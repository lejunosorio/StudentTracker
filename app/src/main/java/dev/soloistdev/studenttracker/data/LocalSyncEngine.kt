package dev.soloistdev.studenttracker.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import dev.soloistdev.studenttracker.security.SyncCrypto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class LocalSyncEngine(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var serverSocket: ServerSocket? = null

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var serverJob: Job? = null

    private val _discoveredPeers = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val discoveredPeers: StateFlow<List<NsdServiceInfo>> = _discoveredPeers

    private val _syncState = MutableStateFlow("Idle")
    val syncState: StateFlow<String> = _syncState

    // Single-use code the receiver displays and the sender must enter to derive the transfer key
    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode

    private var onBackupReceivedCallback: ((File) -> Unit)? = null

    // Starts a secure localized TCP Socket Server on an anonymous free port [1]
    fun startLocalServer(onBackupReceived: (File) -> Unit) {
        stopActiveSession()
        _pairingCode.value = SyncCrypto.generatePairingCode()
        _syncState.value = "Listening"
        onBackupReceivedCallback = onBackupReceived

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(0) // Dynamic port allocation
                val localPort = serverSocket!!.localPort

                registerMdnsService(localPort)

                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    handleIncomingPayload(clientSocket)
                }
            } catch (e: Exception) {
                _syncState.value = "Error: ${e.message}"
            }
        }
    }

    // Scans for other active local peer servers using mDNS Network Service Discovery [1]
    fun startScanningPeers() {
        stopActiveSession()
        _syncState.value = "Scanning"

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _syncState.value = "Error: Discovery failed"
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _syncState.value = "Error: Discovery stop failed"
            }

            override fun onDiscoveryStarted(serviceType: String?) {}

            override fun onDiscoveryStopped(serviceType: String?) {
                _discoveredPeers.value = emptyList()
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("_studenttracker")) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val currentList = _discoveredPeers.value.toMutableList()
                            if (currentList.none { it.serviceName == resolvedInfo.serviceName }) {
                                currentList.add(resolvedInfo)
                                _discoveredPeers.value = currentList
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val currentList = _discoveredPeers.value.toMutableList()
                currentList.removeAll { it.serviceName == serviceInfo.serviceName }
                _discoveredPeers.value = currentList
            }
        }
        nsdManager.discoverServices("_studenttracker._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    // Seals the JSON database payload under the pairing code of the peer, then transmits it [1]
    fun transmitBackupToPeer(
        peer: NsdServiceInfo,
        backupFile: File,
        pairingCode: String,
        onComplete: (Boolean) -> Unit
    ) {
        _syncState.value = "Connecting"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sealedPayload = SyncCrypto.encrypt(backupFile.readBytes(), pairingCode)

                val clientSocket = Socket(peer.host, peer.port)
                _syncState.value = "Syncing"

                clientSocket.getOutputStream().use { output ->
                    output.write(sealedPayload)
                    output.flush()
                }
                clientSocket.close()
                _syncState.value = "Success"
                withContext(Dispatchers.Main) { onComplete(true) }
            } catch (e: Exception) {
                _syncState.value = "Error: ${e.message}"
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    // Gracefully unregisters listeners and releases active socket connections [1]
    fun stopActiveSession() {
        _syncState.value = "Idle"
        _pairingCode.value = null
        serverJob?.cancel()
        serverJob = null

        try {
            serverSocket?.close()
            serverSocket = null
        } catch (_: Exception) {}

        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
            registrationListener = null
        } catch (_: Exception) {}

        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
            discoveryListener = null
        } catch (_: Exception) {}

        _discoveredPeers.value = emptyList()
        onBackupReceivedCallback = null
    }

    private fun registerMdnsService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "StudentTracker_${Build.MODEL}_${UUID.randomUUID().toString().take(5)}"
            serviceType = "_studenttracker._tcp."
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                _syncState.value = "Error: NSD Registration failed"
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}

            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                _syncState.value = "Listening"
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {}
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    private fun handleIncomingPayload(socket: Socket) {
        _syncState.value = "Syncing"
        try {
            val activeCode = _pairingCode.value
                ?: throw IllegalStateException("No active pairing session.")

            // Bounded read: an unpaired peer cannot stream an unlimited write into the cache.
            // Allow headroom over the plaintext cap for the header and the GCM tag.
            val sealedPayload = SyncCrypto.readBounded(
                socket.getInputStream(),
                SyncCrypto.MAX_PAYLOAD_BYTES + 1024
            )

            // Authenticates the sender before a single byte reaches the database.
            val plaintext = SyncCrypto.decrypt(sealedPayload, activeCode)

            val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
            // Resolved: Saved as a .json file to skip hardware KeyStore decryption [1]
            val receivedFile = File(cacheDir, "temp_p2p_import.json")
            if (receivedFile.exists()) receivedFile.delete()

            FileOutputStream(receivedFile).use { fos ->
                fos.write(plaintext)
                fos.flush()
            }
            _syncState.value = "Success"
            onBackupReceivedCallback?.invoke(receivedFile)
        } catch (e: Exception) {
            _syncState.value = "Error: ${e.message}"
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}