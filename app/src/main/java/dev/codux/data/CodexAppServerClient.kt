package dev.codux.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RpcException(val code: Int, override val message: String) : Exception(message)

class CodexAppServerClient(
    private val endpoint: String,
    private val bearerToken: String,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) : Closeable {
    private val ids = AtomicInteger(1)
    private val pending = mutableMapOf<String, CompletableDeferred<JSONObject>>()
    private val lock = Any()
    private val opened = CompletableDeferred<Unit>()
    private val eventChannel = Channel<AppServerEvent>(Channel.UNLIMITED)
    val events: Flow<AppServerEvent> = eventChannel.receiveAsFlow()
    @Volatile private var socket: WebSocket? = null
    @Volatile private var isClosed = false

    suspend fun connect() {
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $bearerToken")
            .build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                dispatch(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isClosed = true
                if (!opened.isCompleted) opened.completeExceptionally(t)
                failAll(t)
                eventChannel.trySend(AppServerEvent("transport/error", JSONObject().put("message", t.message ?: "Conexão perdida")))
                eventChannel.close()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isClosed = true
                failAll(IllegalStateException("App Server fechou a ligação ($code): $reason"))
                eventChannel.trySend(AppServerEvent("transport/closed", JSONObject().put("reason", reason)))
                eventChannel.close()
            }
        })
        withTimeout(15_000) { opened.await() }
        request("initialize", JSONObject()
            .put("clientInfo", JSONObject()
                .put("name", "codux_android")
                .put("title", "Codux")
                .put("version", "0.1.0"))
            .put("capabilities", JSONObject().put("experimentalApi", true)))
        notify("initialized", JSONObject())
    }

    suspend fun request(method: String, params: JSONObject = JSONObject()): JSONObject = withContext(Dispatchers.IO) {
        check(!isClosed) { "Ligação ao Codex encerrada." }
        val id = ids.getAndIncrement()
        val waiter = CompletableDeferred<JSONObject>()
        synchronized(lock) { pending[id.toString()] = waiter }
        val sent = socket?.send(JSONObject().put("id", id).put("method", method).put("params", params).toString()) == true
        if (!sent) {
            synchronized(lock) { pending.remove(id.toString()) }
            throw IllegalStateException("Não foi possível enviar o pedido ao Codex.")
        }
        try {
            withTimeout(30_000) { waiter.await() }
        } finally {
            synchronized(lock) { pending.remove(id.toString()) }
        }
    }

    fun notify(method: String, params: JSONObject = JSONObject()) {
        socket?.send(JSONObject().put("method", method).put("params", params).toString())
    }

    fun respond(id: Any, result: JSONObject) {
        val message = JSONObject().put("id", id).put("result", result)
        socket?.send(message.toString())
    }

    private fun dispatch(raw: String) {
        val message = try { JSONObject(raw) } catch (_: Exception) { return }
        if (message.has("id") && (message.has("result") || message.has("error"))) {
            val id = message.opt("id")?.toString() ?: return
            val waiter = synchronized(lock) { pending[id] } ?: return
            if (message.has("error")) {
                val error = message.optJSONObject("error") ?: JSONObject()
                waiter.completeExceptionally(RpcException(error.optInt("code", -1), error.optString("message", "Erro RPC")))
            } else {
                waiter.complete(message.optJSONObject("result") ?: JSONObject())
            }
            return
        }
        val method = message.optString("method")
        if (method.isEmpty()) return
        val id = if (message.has("id")) message.opt("id") else null
        eventChannel.trySend(AppServerEvent(method, message.optJSONObject("params") ?: JSONObject(), id))
    }

    private fun failAll(error: Throwable) {
        synchronized(lock) {
            pending.values.forEach { it.completeExceptionally(error) }
            pending.clear()
        }
    }

    override fun close() {
        isClosed = true
        socket?.close(1000, "Codux disconnect")
        failAll(IllegalStateException("Ligação encerrada."))
        eventChannel.close()
    }
}
