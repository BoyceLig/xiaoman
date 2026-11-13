package dev.xiaoman.llm

import dev.xiaoman.api.LlmException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelCatalogTest {
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

    private fun baseUrl() = server.url("/api/v3/").toString()

    @Test fun `lists ids from the openai data array with the key in the header`() = runTest {
        server.enqueue(MockResponse().setBody("""{"object":"list","data":[{"id":"doubao-pro"},{"id":"doubao-lite"}]}"""))
        val catalog = ModelCatalog.fetch(baseUrl(), apiKey, LlmClients.sharedHttp, timeoutSeconds = 5)
        assertEquals(listOf("doubao-lite", "doubao-pro"), catalog.ids)
        val request = server.takeRequest()
        assertEquals("/api/v3/models", request.path)
        assertEquals("Bearer $apiKey", request.getHeader("Authorization"))
    }

    @Test fun `reads ollama style models array and a bare list`() {
        assertEquals(listOf("qwen2"), ModelCatalog.parse("""{"models":[{"name":"qwen2","size":1}]}""").ids)
        assertEquals(listOf("a", "b"), ModelCatalog.parse("""["b","a","a"]""").ids)
    }

    @Test fun `newest model comes first and duplicates disappear`() {
        val body = """
            {"data":[
              {"id":"old","created":100,"api_supported":{"chat/completions":true}},
              {"id":"new","created":300,"api_supported":{"chat/completions":true}},
              {"id":"mid","created":200,"api_supported":{"chat/completions":true}},
              {"id":"new","created":300,"api_supported":{"chat/completions":true}}
            ]}
        """
        assertEquals(listOf("new", "mid", "old"), ModelCatalog.parse(body).ids)
    }

    /** The shape Volcano Ark really returns, taken from a live `/models` call. */
    @Test fun `ark retired and non-chat models are hidden and counted`() {
        val body = """
            {"data":[
              {"id":"doubao-seed-2-0-lite-260428","status":"Published","created":1792808954,
               "api_supported":{"chat/completions":true,"responses":true},
               "modalities":{"input_modalities":["text","image","video","audio"],"output_modalities":["text"]}},
              {"id":"doubao-seed-2-0-pro-260215","status":"Retiring","created":1700000000,
               "api_supported":{"chat/completions":true},
               "modalities":{"input_modalities":["text","image"],"output_modalities":["text"]}},
              {"id":"doubao-seedream-5-5-261028","status":"Published","created":1790000000,
               "api_supported":{"images/generations":true},
               "modalities":{"input_modalities":["text","image"],"output_modalities":["image"]}},
              {"id":"doubao-embedding-large-text-240915","status":"Retiring","created":1600000000,
               "api_supported":{"embeddings":true},"modalities":null}
            ]}
        """
        val catalog = ModelCatalog.parse(body)
        assertEquals(listOf("doubao-seed-2-0-lite-260428"), catalog.ids)
        assertEquals(2, catalog.retired)
        assertEquals(1, catalog.unusable)
    }

    @Test fun `the operator picker keeps only models with image input`() {
        val body = """
            {"data":[
              {"id":"vision","status":"Published","api_supported":{"chat/completions":true},
               "modalities":{"input_modalities":["text","image"]}},
              {"id":"text-only","status":"Published","api_supported":{"chat/completions":true},
               "modalities":{"input_modalities":["text"]}}
            ]}
        """
        assertEquals(listOf("vision"), ModelCatalog.parse(body, needImage = true).ids)
        assertEquals(listOf("text-only", "vision"), ModelCatalog.parse(body).ids)
        assertEquals(1, ModelCatalog.parse(body, needImage = true).unusable)
    }

    @Test fun `openrouter architecture says which models read images`() {
        val body = """
            {"data":[
              {"id":"acme/vision","architecture":{"input_modalities":["text","image"]}},
              {"id":"acme/text","architecture":{"input_modalities":["text"]}}
            ]}
        """
        assertEquals(listOf("acme/vision"), ModelCatalog.parse(body, needImage = true).ids)
    }

    @Test fun `a model without metadata is kept, not guessed away`() {
        val catalog = ModelCatalog.parse("""{"data":[{"id":"mystery"}]}""")
        assertEquals(listOf("mystery"), catalog.ids)
        assertEquals(0, catalog.retired)
        assertEquals(0, catalog.unusable)
        assertEquals(listOf("mystery"), ModelCatalog.parse("""{"data":[{"id":"mystery"}]}""", needImage = true).ids)
    }

    /** Ark returns a few of these with no `api_supported`, no `modalities` and no `status`. */
    @Test fun `undescribed generators are recognised by name, but stated capabilities win`() {
        val body = """
            {"data":[
              {"id":"hitem3d-2-0-251223"},
              {"id":"doubao-seedream-4-0-20260415"},
              {"id":"doubao-seed-translation-250915"},
              {"id":"acme-chat"},
              {"id":"acme-embedding-but-stated","api_supported":{"chat/completions":true}}
            ]}
        """
        val catalog = ModelCatalog.parse(body)
        assertEquals(listOf("acme-chat", "acme-embedding-but-stated"), catalog.ids)
        assertEquals(3, catalog.unusable)
    }

    @Test fun `no api key means no authorization header`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"m"}]}"""))
        ModelCatalog.fetch(baseUrl(), "", LlmClients.sharedHttp, timeoutSeconds = 5)
        assertEquals(null, server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `http error carries the status and never the key`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key $apiKey"}}"""))
        val e = assertFailsWith<LlmException> { ModelCatalog.fetch(baseUrl(), apiKey, LlmClients.sharedHttp, timeoutSeconds = 5) }
        assertEquals(401, e.statusCode)
        assertTrue(e.message!!.contains("401"))
        assertFalse(e.message!!.contains(apiKey))
    }

    @Test fun `error body without a model list is a failure, not an empty result`() {
        val e = assertFailsWith<LlmException> { ModelCatalog.parse("""{"error":{"message":"nope"}}""") }
        assertEquals("nope", e.message)
        assertFailsWith<LlmException> { ModelCatalog.parse("<html>gateway</html>") }
    }

    @Test fun `blank base url fails before any request`() = runTest {
        assertFailsWith<LlmException> { ModelCatalog.list("   ", apiKey) }
        assertEquals(0, server.requestCount)
    }
}
