package dev.xiaoman.llm

import dev.xiaoman.api.ModelConfig
import dev.xiaoman.api.ModelRole
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.nio.ByteBuffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelProbeTest {
    private val apiKey = "sk-secret-123456"
    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun config(role: ModelRole = ModelRole.CHAT, model: String = "doubao-pro") = ModelConfig(
        role = role,
        baseUrl = server.url("/api/v3/").toString(),
        model = model,
        apiKey = apiKey,
    )

    private fun reply(text: String) = MockResponse().setBody(
        """{"choices":[{"message":{"role":"assistant","content":"$text"},"finish_reason":"stop"}]}""",
    )

    @Test fun `a working endpoint is reported as connected with its reply`() = runTest {
        server.enqueue(reply("连通"))
        val report = ModelProbe.run(config())
        assertTrue(report.ok, report.detail)
        assertContains(report.detail, "回复：连通")
        val request = server.takeRequest()
        assertEquals("/api/v3/chat/completions", request.path)
        assertEquals("Bearer $apiKey", request.getHeader("Authorization"))
    }

    @Test fun `the operator probe really sends an image`() = runTest {
        server.enqueue(reply("红"))
        val report = ModelProbe.run(config(ModelRole.OPERATOR), withImage = true)
        assertTrue(report.ok, report.detail)
        assertContains(report.detail, "已读图")
        val body = server.takeRequest().body.readUtf8()
        assertContains(body, "\"image_url\"")
        assertContains(body, "data:image/png;base64,")
    }

    /**
     * Volcano Ark answers `Image dimensions are too small. Minimum allowed dimension: 14 pixels` for
     * a small probe, which is a 400 that has nothing to do with the model being able to see.
     */
    @Test fun `the probe image clears the smallest minimum dimension providers enforce`() {
        val header = ByteBuffer.wrap(ModelProbe.probeImageBytes, 16, 8)
        val width = header.int
        val height = header.int
        assertTrue(width >= 14 && height >= 14, "probe image is ${width}x$height")
    }

    @Test fun `a model that refuses images is told apart from a broken endpoint`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":{"message":"invalid content: image_url is not supported by this model"}}"""),
        )
        server.enqueue(reply("连通")) // the text-only fallback
        val report = ModelProbe.run(config(ModelRole.OPERATOR), withImage = true)
        assertFalse(report.ok)
        assertTrue(report.visionRejected, report.detail)
        assertContains(report.detail, "不接受图片")
        assertEquals(2, server.requestCount)
    }

    @Test fun `a rejected image is not blamed on the model`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error":{"code":"InvalidParameter","message":"image data 0 failed: Image dimensions are too small. Minimum allowed dimension: 14 pixels."}}""",
            ),
        )
        server.enqueue(reply("连通"))
        val report = ModelProbe.run(config(ModelRole.OPERATOR), withImage = true)
        assertFalse(report.ok)
        assertFalse(report.visionRejected, report.detail)
        assertContains(report.detail, "图片请求被拒")
        assertContains(report.detail, "too small")
    }

    @Test fun `a bad key is named as such and the key never leaks`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"message":"bad key $apiKey"}}"""),
        )
        val report = ModelProbe.run(config())
        assertFalse(report.ok)
        assertContains(report.detail, "API Key 不对")
        assertFalse(report.detail.contains(apiKey), report.detail)
    }

    @Test fun `a wrong model name points at the model name`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"message":"model not found"}}"""))
        val report = ModelProbe.run(config(model = "no-such-model"))
        assertFalse(report.ok)
        assertContains(report.detail, "模型名称不对")
    }

    @Test fun `a model the account never activated says to activate it`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"error":{"code":"ModelNotOpen","message":"Your account 1 has not activated the model deepseek-v4-1-flash-260910."}}""",
            ),
        )
        val report = ModelProbe.run(config(model = "deepseek-v4-1-flash-260910"))
        assertFalse(report.ok)
        assertContains(report.detail, "还没有开通")
    }

    @Test fun `an unreachable host says the network is down`() = runTest {
        val report = ModelProbe.run(config().copy(baseUrl = "http://127.0.0.1:1/api/v3"))
        assertFalse(report.ok)
        assertContains(report.detail, "网络连不上")
    }

    @Test fun `blank fields fail without any request`() = runTest {
        assertFalse(ModelProbe.run(config().copy(baseUrl = " ")).ok)
        assertFalse(ModelProbe.run(config(model = "")).ok)
        assertEquals(0, server.requestCount)
    }
}
