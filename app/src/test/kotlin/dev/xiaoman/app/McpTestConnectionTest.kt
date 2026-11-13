package dev.xiaoman.app

import dev.xiaoman.mcp.CustomMcpServer
import dev.xiaoman.mcp.McpSelection
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The 测试连接 button path. Whatever the server does, [AppGraph.testMcp] must come back with a
 * [dev.xiaoman.mcp.ServerStatus]; an escaping exception takes the whole app down because the button
 * runs it in a plain `rememberCoroutineScope` launch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpTestConnectionTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun graph() = AppGraph(RuntimeEnvironment.getApplication())

    private fun selection() =
        McpSelection(custom = listOf(CustomMcpServer("测试服务", "http://127.0.0.1:${server.port}/mcp", emptyMap())))

    private fun reply(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    @Test
    fun `an http 500 server is reported, not thrown`() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))
            val status = graph().testMcp(selection())
            assertEquals(1, status.size)
            assertFalse(status.single().connected)
            assertNotNull(status.single().error)
        }
    }

    @Test
    fun `a json-rpc error during initialize is reported, not thrown`() {
        runBlocking {
            server.enqueue(reply("""{"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"no"}}"""))
            val status = graph().testMcp(selection())
            assertFalse(status.single().connected)
            assertNotNull(status.single().error)
        }
    }

    @Test
    fun `a non json body is reported, not thrown`() {
        runBlocking {
            server.enqueue(reply("<html>gateway</html>"))
            val status = graph().testMcp(selection())
            assertFalse(status.single().connected)
            assertNotNull(status.single().error)
        }
    }

    @Test
    fun `a working server reports its tools`() {
        runBlocking {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body.readUtf8()
                    return when {
                        body.contains("initialize") -> reply(
                            """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18",""" +
                                """"capabilities":{},"serverInfo":{"name":"t","version":"1"}}}""",
                        )
                        body.contains("tools/list") -> reply(
                            """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"ping","inputSchema":{"type":"object"}}]}}""",
                        )
                        else -> MockResponse().setResponseCode(202)
                    }
                }
            }
            val status = graph().testMcp(selection())
            assertTrue(status.single().connected, "error: ${status.single().error}")
            assertEquals(1, status.single().toolCount)
        }
    }
}
