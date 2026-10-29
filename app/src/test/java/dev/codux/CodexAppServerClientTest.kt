package dev.codux

import dev.codux.data.CodexAppServerClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CodexAppServerClientTest {
    @Test fun authenticatesInitializesAndRoutesRpcAndNotifications() = runBlocking {
        val server = MockWebServer()
        val serverClosed = CountDownLatch(1)
        val listener = object : WebSocketListener() {
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                serverClosed.countDown()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = JSONObject(text)
                when (message.optString("method")) {
                    "initialize" -> webSocket.send(
                        JSONObject().put("id", message.opt("id")).put("result", JSONObject().put("userAgent", "codex-test")).toString()
                    )
                    "initialized" -> webSocket.send(
                        JSONObject().put("method", "warning").put("params", JSONObject().put("message", "initialized")).toString()
                    )
                    "test/echo" -> {
                        webSocket.send(
                            JSONObject().put("id", message.opt("id"))
                                .put("result", JSONObject().put("value", message.optJSONObject("params")?.optString("value")))
                                .toString()
                        )
                        webSocket.close(1000, "test complete")
                    }
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(listener))
        server.start()
        val client = CodexAppServerClient(server.url("/app-server").toString().replaceFirst("http", "ws"), "unit-test-token")
        try {
            client.connect()
            val handshake = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals("Bearer unit-test-token", handshake.getHeader("Authorization"))

            val response = client.request("test/echo", JSONObject().put("value", "hello"))
            assertEquals("hello", response.optString("value"))
            val event = withTimeout(2_000) { client.events.first() }
            assertEquals("warning", event.method)
            assertEquals("initialized", event.params.optString("message"))
        } finally {
            client.close()
            serverClosed.await(2, TimeUnit.SECONDS)
            server.shutdown()
        }
    }
}
