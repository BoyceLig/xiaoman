package dev.xiaoman.app.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.xiaoman.app.AppGraph
import dev.xiaoman.app.settings.SettingsStore
import dev.xiaoman.mcp.CustomMcpServer
import dev.xiaoman.mcp.McpSelection
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The 测试连接 button end to end: a dead server must produce an inline error line. Before the
 * try/catch in the click handler, the exception escaped the composition coroutine and killed the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class McpScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a broken server shows an error line instead of crashing`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("mcp-screen-test", Context.MODE_PRIVATE)
        val graph = AppGraph(context, givenSettings = SettingsStore(prefs, prefs))
        graph.mcpSettings.save(
            McpSelection(custom = listOf(CustomMcpServer("测试服务", "http://127.0.0.1:${server.port}/mcp"))),
        )
        compose.setContent { McpScreen(graph) }

        compose.onNodeWithText("测试连接").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("连接失败", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("连接失败", substring = true)[0].assertExists()
    }
}
