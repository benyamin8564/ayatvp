package com.ayat64.airvpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    private lateinit var manager: WireGuardManager
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = WireGuardManager(this)

        setContent {
            var connected by remember { mutableStateOf(manager.isConnected()) }
            var catalog by remember { mutableStateOf<ServerCatalog?>(null) }
            var selected by remember { mutableStateOf<VpnServer?>(null) }
            var busy by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                runCatching { manager.loadBundledCatalog() }.onSuccess {
                    catalog = it
                    selected = it.servers.firstOrNull { s -> s.id == manager.lastServerId() }
                        ?: it.servers.firstOrNull()
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
                        Text("WireGuard واقعی با مدیریت کلید و انتخاب سرور", fontSize = 16.sp)
                        Spacer(Modifier.height(24.dp))

                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    if (connected) "وضعیت: متصل" else "وضعیت: قطع",
                                    fontSize = 20.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    selected?.let { "سرور: ${it.name}" } ?: "سروری پیکربندی نشده",
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            enabled = !busy && selected != null,
                            onClick = {
                                if (connected) {
                                    runCatching { manager.disconnect() }
                                        .onFailure { Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_LONG).show() }
                                        .onSuccess { connected = false }
                                } else {
                                    val permission = VpnService.prepare(this@MainActivity)
                                    if (permission != null) {
                                        startActivityForResult(permission, 100)
                                    } else {
                                        busy = true
                                        val server = selected!!
                                        executor.execute {
                                            val result = runCatching {
                                                manager.connect(server)
                                            }
                                            runOnUiThread {
                                                busy = false
                                                result.onFailure {
                                                    Toast.makeText(this@MainActivity, "خطا: ${it.message}", Toast.LENGTH_LONG).show()
                                                }.onSuccess { connected = true }
                                            }
                                        }
                                    }
                                }
                            }
                        ) {
                            Text(if (busy) "در حال اتصال..." else if (connected) "قطع اتصال" else "اتصال")
                        }

                        Spacer(Modifier.height(14.dp))

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && catalog != null,
                            onClick = {
                                val cat = catalog ?: return@OutlinedButton
                                busy = true
                                executor.execute {
                                    val best = runCatching { manager.chooseBestServer(cat) }
                                    runOnUiThread {
                                        busy = false
                                        best.onSuccess {
                                            selected = it
                                            Toast.makeText(this@MainActivity, "سرور انتخاب شد: ${it.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) { Text("انتخاب خودکار سریع‌ترین سرور") }

                        Spacer(Modifier.height(12.dp))
                        Text("سرورها", fontSize = 19.sp)
                        Spacer(Modifier.height(6.dp))

                        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                            items(catalog?.servers ?: emptyList()) { server ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected?.id == server.id,
                                        onClick = { selected = server },
                                        enabled = !connected
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(server.name)
                                        Text("${server.endpoint}:${server.port}", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Text(
                            "کلید خصوصی فقط روی دستگاه و داخل Android Keystore رمزنگاری می‌شود. " +
                                "برای اتصال واقعی باید public key و endpoint سرور WireGuard خودتان را در servers.json قرار دهید.",
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == Activity.RESULT_OK) {
            val catalog = runCatching { manager.loadBundledCatalog() }.getOrNull()
            val server = catalog?.servers?.firstOrNull { it.id == manager.lastServerId() }
                ?: catalog?.servers?.firstOrNull()
            if (server != null) {
                Thread {
                    runCatching { manager.connect(server) }
                        .onSuccess { runOnUiThread { recreate() } }
                        .onFailure {
                            runOnUiThread {
                                Toast.makeText(this, "خطا: ${it.message}", Toast.LENGTH_LONG).show()
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
