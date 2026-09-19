package com.ayat64.airvpn

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.crypto.Key
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

class WireGuardManager(private val context: Context) {
    private val backend = GoBackend(context.applicationContext)
    private val store = SecureStore(context.applicationContext)
    private val tunnel = object : Tunnel {
        override fun getName() = "ai-iran-vpn"
        override fun onStateChange(newState: Tunnel.State) {}
    }

    fun getPrivateKey(): String {
        store.get("wg_private_key")?.let { return it }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        raw[0] = (raw[0].toInt() and 248).toByte()
        raw[31] = (raw[31].toInt() and 127).toByte()
        raw[31] = (raw[31].toInt() or 64).toByte()
        val key = Key.fromBytes(raw).toBase64()
        store.put("wg_private_key", key)
        return key
    }

    fun connect(server: VpnServer) {
        val privateKey = getPrivateKey()
        val configText = buildConfig(privateKey, server)
        connectConfig(configText)
        store.put("last_server_id", server.id)
    }

    fun saveImportedConfig(configText: String) {
        validateConfig(configText)
        store.put("imported_config", configText)
    }

    fun connectImportedConfig() {
        val configText = store.get("imported_config")
            ?: throw IllegalStateException("No imported WireGuard configuration")
        connectConfig(configText)
    }

    fun connectConfig(configText: String) {
        validateConfig(configText)
        val config = Config.parse(ByteArrayInputStream(configText.toByteArray(StandardCharsets.UTF_8)))
        backend.setState(tunnel, Tunnel.State.UP, config)
        store.put("imported_config", configText)
    }

    private fun validateConfig(configText: String) {
        require(configText.contains("[Interface]", ignoreCase = true)) {
            "Invalid WireGuard configuration"
        }
        require(configText.contains("[Peer]", ignoreCase = true)) {
            "No WireGuard peer found"
        }
    }

    fun hasImportedConfig(): Boolean = store.get("imported_config") != null

    fun disconnect() {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
    }

    fun isConnected(): Boolean = backend.getState(tunnel) == Tunnel.State.UP

    fun lastServerId(): String? = store.get("last_server_id")

    private fun buildConfig(privateKey: String, s: VpnServer): String = """
        [Interface]
        PrivateKey = $privateKey
        Address = ${s.address}
        DNS = ${s.dns}
        MTU = 1280

        [Peer]
        PublicKey = ${s.publicKey}
        AllowedIPs = ${s.allowedIps}
        Endpoint = ${s.endpoint}:${s.port}
        PersistentKeepalive = ${s.keepalive}
    """.trimIndent()

    fun chooseBestServer(catalog: ServerCatalog, timeoutMs: Int = 2500): VpnServer {
        val candidates = catalog.servers
        var best: VpnServer? = null
        var bestMs = Long.MAX_VALUE
        for (server in candidates) {
            val url = server.healthUrl ?: continue
            val started = System.nanoTime()
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    instanceFollowRedirects = false
                }
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..399) {
                    val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
                    if (ms < bestMs) {
                        bestMs = ms
                        best = server
                    }
                }
            } catch (_: Exception) {}
        }
        return best ?: candidates.first()
    }

    fun updateCatalogSecurely(urlString: String, pinnedSha256: String? = null): ServerCatalog {
        require(urlString.startsWith("https://", ignoreCase = true)) {
            "Only HTTPS config URLs are allowed"
        }
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            instanceFollowRedirects = false
        }
        val body = conn.inputStream.use { it.readBytes() }
        conn.disconnect()
        if (pinnedSha256 != null) {
            val actual = MessageDigest.getInstance("SHA-256").digest(body)
                .joinToString("") { "%02x".format(it) }
            require(actual.equals(pinnedSha256.removePrefix("sha256:"), true)) {
                "Remote configuration checksum mismatch"
            }
        }
        val text = body.toString(StandardCharsets.UTF_8)
        val catalog = ServerCatalog.parse(text)
        store.put("server_catalog", text)
        return catalog
    }

    fun loadBundledCatalog(): ServerCatalog {
        val cached = store.get("server_catalog")
        if (cached != null) return ServerCatalog.parse(cached)
        val text = context.assets.open("servers.json").use {
            it.readBytes().toString(StandardCharsets.UTF_8)
        }
        return ServerCatalog.parse(text)
    }
}
