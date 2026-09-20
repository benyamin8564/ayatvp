package com.ayat64.airvpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED_HANDSHAKE,
        SERVER_NOT_RESPONDING
    }

    private val connectionStatus = mutableStateOf(ConnectionStatus.DISCONNECTED)
    private lateinit var manager: WireGuardManager
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = WireGuardManager(this)
        connectionStatus.value = if (manager.isConnected()) {
            ConnectionStatus.CONNECTED_HANDSHAKE
        } else {
            ConnectionStatus.DISCONNECTED
        }

        val importConf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val text = contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("فایل خوانده نشد")
                val name = uri.lastPathSegment?.substringAfterLast("/")?.ifBlank { "WireGuard.conf" } ?: "WireGuard.conf"
                manager.saveImportedConfig(text, name)
                Toast.makeText(this, "کانفیگ وارد شد؛ حالا اتصال را بزنید.", Toast.LENGTH_SHORT).show()
                recreate()
            }.onFailure {
                Toast.makeText(this, "خطا در فایل WireGuard: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }

        val importZip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("ZIP خوانده نشد")
                manager.importConfigZip(bytes)
            }.onSuccess {
                Toast.makeText(this, "${it.size} سرور WireGuard وارد شد.", Toast.LENGTH_LONG).show()
                recreate()
            }.onFailure {
                Toast.makeText(this, "خطا در ZIP: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }

        setContent {
            var connected by remember { mutableStateOf(manager.isConnected()) }
            var catalog by remember { mutableStateOf<ServerCatalog?>(null) }
            var selected by remember { mutableStateOf<VpnServer?>(null) }
            var imported by remember { mutableStateOf(manager.getImportedConfigs()) }
            var selectedImported by remember {
                mutableStateOf(imported.firstOrNull { it.id == manager.selectedImportedId() })
            }
            var busy by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                runCatching { manager.loadBundledCatalog() }.onSuccess {
                    catalog = it
                    selected = it.servers.firstOrNull { s -> s.id == manager.lastServerId() }
                        ?: it.servers.firstOrNull()
                }
            }

            fun connectNow() {
                connectionStatus.value = ConnectionStatus.CONNECTING
                val importedId = selectedImported?.id
                if (importedId != null) {
                    getPreferences(MODE_PRIVATE).edit()
                        .putString("pending_imported_id", importedId)
                        .apply()
                }
                val permission = VpnService.prepare(this@MainActivity)
                if (permission != null) {
                    startActivityForResult(permission, if (importedId != null) 103 else 100)
                    return
                }
                busy = true
                executor.execute {
                    val result = runCatching {
                        if (importedId != null) manager.connectImportedWithFailover(importedId)
                        else {
                            manager.connect(selected!!)
                            selected!!.name
                        }
                    }
                    runOnUiThread {
                        busy = false
                        result.onSuccess {
                            connected = true
                            connectionStatus.value = ConnectionStatus.CONNECTED_HANDSHAKE
                        }
                            .onFailure {
                                connectionStatus.value = ConnectionStatus.SERVER_NOT_RESPONDING
                                Toast.makeText(
                                    this@MainActivity,
                                    "سرور پاسخ نمی‌دهد: ${it.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                }
            }

            MaterialTheme {
                CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalLayoutDirection provides
                        androidx.compose.ui.unit.LayoutDirection.Rtl
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("AI Iran VPN", fontSize = 30.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("WireGuard واقعی با کانفیگ‌های خصوصی شما", fontSize = 16.sp)
                        Spacer(Modifier.height(20.dp))

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                val statusText = when (connectionStatus.value) {
                                    ConnectionStatus.CONNECTING -> "🟡 در حال اتصال"
                                    ConnectionStatus.CONNECTED_HANDSHAKE -> "🟢 متصل و Handshake برقرار"
                                    ConnectionStatus.SERVER_NOT_RESPONDING -> "🔴 سرور پاسخ نمی‌دهد"
                                    ConnectionStatus.DISCONNECTED -> "وضعیت: قطع"
                                }
                                Text(statusText, fontSize = 20.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    when {
                                        selectedImported != null -> "سرور: ${selectedImported!!.name}"
                                        selected != null -> "سرور: ${selected!!.name}"
                                        else -> "سروری انتخاب نشده"
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Button(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            enabled = !busy && (selected != null || selectedImported != null),
                            onClick = {
                                if (connected) {
                                    runCatching { manager.disconnect() }
                                        .onSuccess {
                                            connected = false
                                            connectionStatus.value = ConnectionStatus.DISCONNECTED
                                        }
                                        .onFailure {
                                            Toast.makeText(
                                                this@MainActivity,
                                                it.message,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                } else connectNow()
                            }
                        ) {
                            Text(
                                if (busy) "در حال اتصال..."
                                else if (connected) "قطع اتصال"
                                else "اتصال به سرور انتخاب‌شده"
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && !connected,
                            onClick = { importZip.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                        ) {
                            Text("وارد کردن ZIP کانفیگ‌ها")
                        }

                        Spacer(Modifier.height(6.dp))

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && !connected,
                            onClick = { importConf.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) }
                        ) {
                            Text("وارد کردن یک فایل WireGuard (.conf)")
                        }

                        Spacer(Modifier.height(14.dp))

                        if (imported.isNotEmpty()) {
                            Text("کانفیگ‌های واردشده: ${imported.size}", fontSize = 19.sp)
                            Spacer(Modifier.height(4.dp))
                            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                                items(imported, key = { it.id }) { item ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selectedImported?.id == item.id,
                                            onClick = {
                                                selectedImported = item
                                                selected = null
                                            },
                                            enabled = !connected
                                        )
                                        Text(item.name, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            Text(
                                "هنوز ZIP خصوصی شما وارد نشده است.",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("سرورهای نمونه", fontSize = 19.sp)
                            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                                items(catalog?.servers ?: emptyList()) { server ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected?.id == server.id,
                                            onClick = {
                                                selected = server
                                                selectedImported = null
                                            },
                                            enabled = !connected
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(server.name)
                                            Text("${server.endpoint}:${server.port}", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            "کانفیگ‌ها داخل ریپوی عمومی قرار نمی‌گیرند. ZIP را روی گوشی وارد کنید؛ کلیدهای خصوصی با Android Keystore رمزنگاری و محلی ذخیره می‌شوند.",
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return

        val id = if (requestCode == 103) {
            getPreferences(MODE_PRIVATE).getString("pending_imported_id", null)
        } else null
        if (requestCode == 103 && id != null) {
            getPreferences(MODE_PRIVATE).edit().remove("pending_imported_id").apply()
            connectionStatus.value = ConnectionStatus.CONNECTING
            Thread {
                runCatching { manager.connectImportedWithFailover(id) }
                    .onSuccess {
                        runOnUiThread {
                            connectionStatus.value = ConnectionStatus.CONNECTED_HANDSHAKE
                            recreate()
                        }
                    }
                    .onFailure {
                        runOnUiThread {
                            connectionStatus.value = ConnectionStatus.SERVER_NOT_RESPONDING
                            Toast.makeText(this, "سرور پاسخ نمی‌دهد: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            }.start()
        } else if (requestCode == 100) {
            connectionStatus.value = ConnectionStatus.CONNECTING
            val catalog = runCatching { manager.loadBundledCatalog() }.getOrNull()
            val server = catalog?.servers?.firstOrNull { it.id == manager.lastServerId() }
                ?: catalog?.servers?.firstOrNull()
            if (server != null) {
                Thread {
                    runCatching { manager.connect(server) }
                        .onSuccess {
                            runOnUiThread {
                                connectionStatus.value = ConnectionStatus.CONNECTED_HANDSHAKE
                                recreate()
                            }
                        }
                        .onFailure {
                            runOnUiThread {
                                connectionStatus.value = ConnectionStatus.SERVER_NOT_RESPONDING
                                Toast.makeText(this, "سرور پاسخ نمی‌دهد: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                }.start()
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
