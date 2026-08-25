package dev.soloistdev.studenttracker.security

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Raised when a P2P payload cannot be authenticated: wrong pairing code, or tampered bytes. */
class SyncPairingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * End-to-end protection for the LAN peer-to-peer roster transfer.
 *
 * The receiver generates a single-use 6-digit pairing code and reads it aloud; the sender types
 * it in. Both sides derive the same AES-256-GCM key from that code via PBKDF2, so a device that
 * does not know the code can neither push a payload into the receiver's database nor read a
 * payload it intercepts on the network.
 *
 * Wire format: MAGIC(4) | VERSION(1) | SALT(16) | IV(12) | AES-GCM ciphertext + 16-byte tag.
 *
 * Note on strength: a 6-digit code carries only ~20 bits of entropy, so the iteration count below
 * is what makes an offline guessing attack against a captured payload expensive. The code is
 * single-use and lives only for the duration of one sync session, which bounds the exposure.
 */
object SyncCrypto {

    /** Upper bound on a decrypted roster payload, matching the file-import safety threshold. */
    const val MAX_PAYLOAD_BYTES = 10 * 1024 * 1024

    private val MAGIC = byteArrayOf(0x53, 0x54, 0x53, 0x59) // "STSY"
    private const val VERSION: Byte = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 210_000

    private const val HEADER_BYTES = 4 + 1 + SALT_BYTES + IV_BYTES

    /** Generates a single-use 6-digit pairing code from a cryptographically secure source. */
    fun generatePairingCode(): String =
        SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')

    fun encrypt(plaintext: ByteArray, pairingCode: String): ByteArray {
        if (plaintext.size > MAX_PAYLOAD_BYTES) {
            throw IOException("Payload exceeds the ${MAX_PAYLOAD_BYTES / (1024 * 1024)}MB safety threshold.")
        }

        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pairingCode, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        return ByteArrayOutputStream(HEADER_BYTES + ciphertext.size).apply {
            write(MAGIC)
            write(byteArrayOf(VERSION))
            write(salt)
            write(iv)
            write(ciphertext)
        }.toByteArray()
    }

    /** Verifies and decrypts a payload. Throws [SyncPairingException] if the code does not match. */
    fun decrypt(payload: ByteArray, pairingCode: String): ByteArray {
        if (payload.size <= HEADER_BYTES) {
            throw SyncPairingException("Payload is too short to be a valid sync transfer.")
        }
        if (!payload.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw SyncPairingException("Unrecognised payload. The sender is not a Student Tracker peer.")
        }
        if (payload[MAGIC.size] != VERSION) {
            throw SyncPairingException("Unsupported sync protocol version. Update both devices.")
        }

        var offset = MAGIC.size + 1
        val salt = payload.copyOfRange(offset, offset + SALT_BYTES)
        offset += SALT_BYTES
        val iv = payload.copyOfRange(offset, offset + IV_BYTES)
        offset += IV_BYTES
        val ciphertext = payload.copyOfRange(offset, payload.size)

        val plaintext = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(pairingCode, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            // An authentication-tag failure is indistinguishable from a wrong code, by design.
            throw SyncPairingException("Incorrect pairing code, or the payload was altered in transit.", e)
        }

        if (plaintext.size > MAX_PAYLOAD_BYTES) {
            throw SyncPairingException("Decrypted payload exceeds the safety threshold.")
        }
        return plaintext
    }

    /**
     * Reads at most [limit] bytes, aborting rather than letting an unbounded peer stream fill
     * the cache. Used on the receiving socket, where no content-length is trustworthy.
     */
    fun readBounded(input: InputStream, limit: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            total += read
            if (total > limit) {
                throw IOException("Incoming payload exceeds the ${limit / (1024 * 1024)}MB safety threshold.")
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun deriveKey(pairingCode: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pairingCode.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }
}
