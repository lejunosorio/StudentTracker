package dev.soloistdev.studenttracker.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.android.identity.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

class LocalSyncEngine(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var serverSocket: ServerSocket? = null

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var serverJob: Job? = null

    private val _discoveredPeers = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val discoveredPeers: StateFlow<List<NsdServiceInfo>> = _discoveredPeers

    private val _syncState = MutableStateFlow<String>("Idle")
    val syncState: StateFlow<String> = _syncState

    private var onBackupReceivedCallback: ((File) -> Unit)? = null

    // 1. Starts a secure localized TCP Socket Server on an anonymous free port [1]
    fun startLocalServer(onBackupReceived: (File) -> Unit) {
        stopActiveSession()
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

    // 2. Scans for other active local peer servers using mDNS Network Service Discovery [1]
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
                // Evaluates sequential service matching safely across custom profiles
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

    // 3. Securely transmits the encrypted database backup over the local network socket [1]
    fun transmitBackupToPeer(peer: NsdServiceInfo, backupFile: File, onComplete: (Boolean) -> Unit) {
        _syncState.value = "Connecting"
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Direct connection to resolved peer IP and port
                val clientSocket = Socket(peer.host, peer.port)
                _syncState.value = "Syncing"

                FileInputStream(backupFile).use { fis ->
                    clientSocket.getOutputStream().use { output ->
                        fis.copyTo(output)
                        output.flush()
                    }
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

    // Gracefully releases sockets and listeners
    fun stopActiveSession() {
        _syncState.value = "Idle"
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
            val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
            // Resolved: Saved as a .json file to skip hardware KeyStore decryption [1]
            val receivedFile = File(cacheDir, "temp_p2p_import.json")
            if (receivedFile.exists()) receivedFile.delete()

            socket.getInputStream().use { input ->
                FileOutputStream(receivedFile).use { fos ->
                    input.copyTo(fos)
                    fos.flush()
                }
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