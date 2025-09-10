package com.miangame.citaqbridge

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class TcpPrintBridgeService : Service() {

    companion object {
        const val ACTION_START = "dev.tuusuario.citaqbridge.action.START"
        const val ACTION_STOP = "dev.tuusuario.citaqbridge.action.STOP"
        const val ACTION_OPEN_DRAWER = "dev.tuusuario.citaqbridge.action.OPEN_DRAWER"

        // Broadcast de estado para la Activity
        const val ACTION_STATE = "dev.tuusuario.citaqbridge.action.STATE"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_PORT = "port"
        const val EXTRA_LASTPRINT = "last_print"

        private const val CHANNEL_ID = "print_bridge_channel"
        private const val NOTIF_ID = 1001
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private lateinit var printer: PrinterAdapter
    private lateinit var settings: SettingsRepository
    private var port: Int = 9100

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        printer = CitaqPrinter()
        createNotificationChannel()
        // No llamamos a startForeground aquí; solo cuando el bridge esté ON
        notifyState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startBridge(intent.getIntExtra(EXTRA_PORT, settings.port))
            ACTION_STOP -> stopBridge()
            ACTION_OPEN_DRAWER -> {
                val ok = printer.openDrawer()
                Logger.log(this, "OpenDrawer ${if (ok) "OK" else "ERR"}")
                // no cambia el estado ON/OFF, pero avisamos por si la UI muestra última acción
                notifyState()
            }
            else -> { /* noop */ }
        }
        return START_STICKY
    }

    private fun startBridge(requestedPort: Int) {
        if (running.get()) { updateNotificationText("Bridge ON ($port)"); notifyState(); return }
        port = requestedPort
        Logger.log(this, "Starting server on port $port")
        running.set(true)

        // Ahora sí: foreground con notificación visible
        startForeground(NOTIF_ID, buildNotification("Bridge ON ($port)"))

        scope.launch { runServer() }
        notifyState()
    }

    private fun stopBridge() {
        Logger.log(this, "Stopping server")
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        // Quitamos la notificación y paramos el service
        stopForeground(true)
        stopSelf()
        notifyState()
    }

    private suspend fun runServer() {
        try {
            val bindAddr = InetSocketAddress(InetAddress.getByName("0.0.0.0"), port)
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(bindAddr)
            }
            Logger.log(this, "Listening on $port")
            while (running.get()) {
                val client = withContext(Dispatchers.IO) { serverSocket?.accept() } ?: break
                scope.launch { handleClient(client) }
            }
        } catch (e: Exception) {
            Logger.log(this, "Server error: ${e.message}")
            // Si hubo fallo grave, apagamos el bridge (esto además quita la notificación)
            stopBridge()
        }
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                socket.soTimeout = 3000
                val input = BufferedInputStream(socket.getInputStream())
                val output = socket.getOutputStream()

                val buffer = ByteArray(4096)
                val received = ArrayList<Byte>()
                while (true) {
                    val n = try { input.read(buffer) } catch (_: java.net.SocketTimeoutException) { -1 }
                    if (n == -1) break
                    for (i in 0 until n) received.add(buffer[i])
                }
                val bytes = received.toByteArray()
                val ok = printer.printRaw(bytes)
                if (ok) {
                    settings.lastPrintAt = System.currentTimeMillis()
                    notifyState() // actualiza “Última impresión” en la UI
                }
                output.write(if (ok) "OK\n".toByteArray() else "ERR\n".toByteArray())
                output.flush()
                Logger.log(this@TcpPrintBridgeService, "Job ${if (ok) "OK" else "ERR"} (${bytes.size} bytes)")
            } catch (e: Exception) {
                Logger.log(this@TcpPrintBridgeService, "Client error: ${e.message}")
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        // Asegura apagado limpio
        try { serverSocket?.close() } catch (_: Exception) {}
        running.set(false)
        scope.cancel()
        notifyState()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- Notificación ----------
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_ID, "Citaq Print Bridge", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val mainPI = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val stopIntent = Intent(this, TcpPrintBridgeService::class.java).apply { action = ACTION_STOP }
        val stopPI = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Citaq Print Bridge")
            .setContentText(text)
            .setContentIntent(mainPI)
            .addAction(0, "Stop", stopPI)
            .setOngoing(true)
            .build()
    }

    private fun updateNotificationText(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    // ---------- Broadcast de estado ----------
    private fun notifyState() {
        val intent = Intent(ACTION_STATE).apply {
            setPackage(packageName)               // 👈 solo tu app
            putExtra(EXTRA_RUNNING, running.get())
            putExtra(EXTRA_PORT, port)
            putExtra(EXTRA_LASTPRINT, settings.lastPrintAt)
        }
        sendBroadcast(intent)
    }
}
