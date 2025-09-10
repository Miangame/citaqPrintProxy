package com.miangame.citaqbridge

import android.annotation.SuppressLint
import android.os.Build

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private lateinit var settings: SettingsRepository

    private var _running by mutableStateOf(false)
    private var _lastPrint by mutableStateOf(0L)
    private var _currentPort by mutableStateOf(9100)

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TcpPrintBridgeService.ACTION_STATE) {
                _running = intent.getBooleanExtra(TcpPrintBridgeService.EXTRA_RUNNING, false)
                _currentPort = intent.getIntExtra(TcpPrintBridgeService.EXTRA_PORT, settings.port)
                _lastPrint = intent.getLongExtra(TcpPrintBridgeService.EXTRA_LASTPRINT, settings.lastPrintAt)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsRepository(this)

        // Estado inicial (por si el service aún no ha emitido broadcast)
        _running = isBridgeRunning()
        _currentPort = settings.port
        _lastPrint = settings.lastPrintAt

        setContent { AppScreen() }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(TcpPrintBridgeService.ACTION_STATE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }
    }

    override fun onStop() {
        unregisterReceiver(stateReceiver)
        super.onStop()
    }

    @Composable
    private fun AppScreen() {
        var port by remember { mutableStateOf(settings.port) }
        var autoStart by remember { mutableStateOf(settings.autoStartOnBoot) }

        // Si el service cambia el puerto, reflejarlo
        LaunchedEffect(_currentPort) {
            port = _currentPort
        }

        Scaffold { padding ->
            Column(
                modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Citaq Print Bridge", style = MaterialTheme.typography.headlineSmall)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Estado: ")
                    Text(
                        if (_running) "ON ($port)" else "OFF",
                        color = if (_running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                OutlinedTextField(
                    value = port.toString(),
                    onValueChange = { v -> port = v.filter { it.isDigit() }.toIntOrNull() ?: port },
                    label = { Text("Puerto TCP") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = autoStart, onCheckedChange = {
                        autoStart = it
                        settings.autoStartOnBoot = it
                    })
                    Spacer(Modifier.width(8.dp))
                    Text("Arrancar al encender (BOOT)")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        settings.port = port
                        val svc = Intent(this@MainActivity, TcpPrintBridgeService::class.java)
                        svc.action = TcpPrintBridgeService.ACTION_START
                        svc.putExtra(TcpPrintBridgeService.EXTRA_PORT, port)
                        startService(svc)
                    }) { Text("Start Bridge") }

                    OutlinedButton(onClick = {
                        val svc = Intent(this@MainActivity, TcpPrintBridgeService::class.java)
                        svc.action = TcpPrintBridgeService.ACTION_STOP
                        startService(svc)
                    }) { Text("Stop Bridge") }

                    OutlinedButton(onClick = {
                        val svc = Intent(this@MainActivity, TcpPrintBridgeService::class.java)
                        svc.action = TcpPrintBridgeService.ACTION_OPEN_DRAWER
                        startService(svc)
                    }) { Text("Abrir cajón") }
                }

                Divider()
                Text("IP del dispositivo: consulta en Ajustes > Wi-Fi", style = MaterialTheme.typography.bodySmall)
                if (_lastPrint > 0L) {
                    Text("Última impresión: $_lastPrint", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
