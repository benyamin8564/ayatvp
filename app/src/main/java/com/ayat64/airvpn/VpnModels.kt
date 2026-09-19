package com.ayat64.airvpn

import org.json.JSONArray
import org.json.JSONObject

data class VpnServer(
    val id: String,
    val name: String,
    val endpoint: String,
    val port: Int,
    val publicKey: String,
    val address: String,
    val dns: String = "1.1.1.1",
    val healthUrl: String? = null,
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val keepalive: Int = 25
)

data class ServerCatalog(
    val version: Int,
    val servers: List<VpnServer>
) {
    companion object {
        fun parse(json: String): ServerCatalog {
            val root = JSONObject(json)
            val arr = root.getJSONArray("servers")
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(VpnServer(
                        id = o.getString("id"),
                        name = o.optString("name", o.getString("id")),
                        endpoint = o.getString("host"),
                        port = o.optInt("port", 51820),
                        publicKey = o.getString("publicKey"),
                        address = o.getString("address"),
                        dns = o.optString("dns", "1.1.1.1"),
                        healthUrl = o.optString("healthUrl").takeIf { it.isNotBlank() },
                        allowedIps = o.optString("allowedIps", "0.0.0.0/0, ::/0"),
                        keepalive = o.optInt("persistentKeepalive", 25)
                    ))
                }
            }
            require(list.isNotEmpty()) { "No VPN servers configured" }
            return ServerCatalog(root.optInt("version", 1), list)
        }
    }
}

data class ImportedVpnConfig(
    val id: String,
    val name: String,
    val configText: String
)

data class ImportedConfigIndex(
    val id: String,
    val name: String
)
