package com.miangame.citaqbridge

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.miangame.citaqbridge.MainActivity
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.net.InetAddress
import java.net.InetSocketAddress

class TcpPrintBridgeService : Service() {

    companion object {
        private const val CHANNEL_ID = "print_bridge_channel"
        private const val NOTIF_ID = 1001
        private const val PORT = 9100
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)

    private lateinit var printer: PrinterAdapter

    private val DEBUG_TOASTS = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Starting…"))
        printer = CitaqPrinter() // 👈 usar nuestro adaptador directo
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running.compareAndSet(false, true)) {
            scope.launch { runServer() }
            updateNotification("Starting…")
        }
        return START_STICKY
    }

    private suspend fun runServer() {
        try {
            // Bind explícito a 0.0.0.0 para escuchar en todas las interfaces
            val bindAddr = java.net.InetSocketAddress(java.net.InetAddress.getByName("0.0.0.0"), PORT)
            serverSocket = ServerSocket()
            serverSocket!!.reuseAddress = true
            serverSocket!!.bind(bindAddr)  // <-- si falla, mostramos el error abajo
            updateNotification("Listening on TCP $PORT")

            while (running.get()) {
                val client = withContext(Dispatchers.IO) { serverSocket?.accept() } ?: break
                scope.launch { handleClient(client) }
            }
        } catch (e: Exception) {
            // Muestra el error en notificación y en un Toast visible
            updateNotification("Server error: ${e.message}")
            if (DEBUG_TOASTS) {
                showToastMainThread("Server error: ${e.message}")
            }

            running.set(false)
            stopSelf()
        }
    }

    private fun handleClient(socket: Socket) {
        scope.launch {
            try {
                // 1–2 s suele ir bien; sube a 3000 si notas tickets muy largos
                socket.soTimeout = 2000

                val input = BufferedInputStream(socket.getInputStream())
                val output = socket.getOutputStream()

                if (DEBUG_TOASTS) {
                    showToastMainThread("Cliente ${socket.inetAddress.hostAddress} conectado")
                }


                val buffer = ByteArray(4096)
                val received = ArrayList<Byte>()

                while (true) {
                    val n = try { input.read(buffer) }
                    catch (e: java.net.SocketTimeoutException) { -1 } // inactividad = fin de trabajo
                    if (n == -1) break   // EOF o timeout → terminamos de leer el ticket
                    for (i in 0 until n) received.add(buffer[i])
                    // ⚠️ NO usar input.available() como criterio de fin; puede ser 0 entre ráfagas
                }

                val bytes = received.toByteArray()

                if (DEBUG_TOASTS) {
                    showToastMainThread("Recibidos ${bytes.size} bytes")
                }

                // Enviar tal cual al puerto serie (SIN init, SIN saltos, SIN corte)
                val ok = printer.printRaw(bytes)

                // Respuesta para clientes de prueba
                output.write(if (ok) "OK\n".toByteArray() else "ERR\n".toByteArray())
                output.flush()
            } catch (e: Exception) {
                if (DEBUG_TOASTS) {
                    showToastMainThread("Error cliente: ${e.message}")
                }
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }



    override fun onDestroy() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // -------- Notificación foreground --------
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID, "Citaq Print Bridge",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Citaq Print Bridge")
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    // -------- Toast helper --------
    private fun showToastMainThread(msg: String) {
        android.os.Handler(mainLooper).post {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
