package com.trendpulse.trendpulse.core.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

import com.trendpulse.trendpulse.core.model.Comment

/**
 * Foreground Android service responsible for managing a persistent WebSocket connection
 * to the backend server for real-time comment streaming.
 *
 * This service:
 * - Establishes and maintains a WebSocket connection
 * - Receives and parses streaming JSON messages
 * - Emits structured events via a shared Flow for UI consumption
 * - Implements automatic reconnection using exponential backoff
 *
 * The service operates in the foreground to ensure reliability during long-running
 * operations such as comment scraping and analysis.
 *
 * It leverages Kotlin Coroutines for asynchronous event handling and ensures
 * thread-safe communication between the network layer and the UI layer.
 */
class WebSocketService : Service() {

    companion object {
        const val EXTRA_URL = "postUrl"
        const val CHANNEL_ID = "websocket_channel"
        const val NOTIF_ID = 1

        // ✅ Singleton SharedFlow — ViewModel collects from this
        val eventFlow = MutableSharedFlow<WebSocketEvent>(
            replay = 0,
            extraBufferCapacity = 64
        )
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var webSocket: WebSocket? = null
    private var postUrl: String? = null
    private var reconnectAttempts = 0

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS) // ✅ keep-alive pings
        .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        postUrl = intent?.getStringExtra(EXTRA_URL)
        startForeground(NOTIF_ID, buildNotification())
        postUrl?.let { connect(it) }
        return START_STICKY // ✅ restart if killed by OS
    }

    /**
     * Establishes a WebSocket connection to the backend server.
     *
     * @param url The post URL used as a parameter for the scraping request.
     */
    private fun connect(url: String) {
        val encoded = URLEncoder.encode(url, "UTF-8")
        val wsUrl = "wss://tikgrub.onrender.com/?url=$encoded"

        Log.d("WebSocketService", "🔌 Connecting to $wsUrl")

        val request = Request.Builder().url(wsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocketService", "✅ Connected")
                reconnectAttempts = 0
                emit(WebSocketEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocketService", "📨 $text")
                try {
                    val json = JSONObject(text)
                    when (json.getString("type")) {
                        "SCRAPE_STARTED" -> emit(WebSocketEvent.ScrapeStarted)
                        "COMMENTS" -> {
                            val arr = json.getJSONArray("comments")
                            val comments = (0 until arr.length()).map {
                                val obj = arr.getJSONObject(it)
                                Comment(
                                    text = obj.getString("text"),
                                    user = obj.optString("user", "anon")
                                )
                            }
                            emit(WebSocketEvent.NewComments(
                                comments = comments,
                                progress = json.optInt("progress", 0),
                                total = json.optInt("total", 0)
                            ))
                        }
                        "DONE" -> emit(WebSocketEvent.Done(json.optInt("total", 0)))
                        "ERROR" -> emit(WebSocketEvent.Error(
                            json.optString("message", "Unknown error")
                        ))
                    }
                } catch (e: Exception) {
                    Log.e("WebSocketService", "❌ Parse error: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocketService", "❌ Failed: ${t.message}")
                emit(WebSocketEvent.Disconnected)
                scheduleReconnect(url) // ✅ exponential backoff
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocketService", "🔌 Closed: $reason")
                emit(WebSocketEvent.Disconnected)
            }
        })
    }

    /**
     * Schedules a reconnection attempt using exponential backoff strategy.
     * Backoff is applied to prevent overwhelming the server with reconnect requests.
     *
     * This mechanism improves resilience against transient network failures.
     *
     * the time between attempts increases exponentially, up to a maximum of 30 seconds.
     *
     * @param url The post URL used to reconnect.
     */
    private fun scheduleReconnect(url: String) {
        reconnectAttempts++
        val delayMs = minOf(2000L * (1 shl reconnectAttempts), 30_000L)
        Log.d("WebSocketService", "🔄 Reconnecting in ${delayMs}ms (attempt $reconnectAttempts)")
        Handler(Looper.getMainLooper()).postDelayed({ connect(url) }, delayMs)
    }

    /**
     * Emits a [WebSocketEvent] to the shared event flow.
     *
     * @param event The event to be emitted.
     */
    private fun emit(event: WebSocketEvent) {
        serviceScope.launch { eventFlow.emit(event) }
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TrendPulse Live Updates",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TrendPulse")
            .setContentText("Analyzing comments...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        webSocket?.close(1000, "Service destroyed")
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
