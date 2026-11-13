package dev.xiaoman.llm

import dev.xiaoman.api.LlmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Lists the models an OpenAI-compatible endpoint advertises (`GET {baseUrl}/models`), for the
 * refresh button next to 模型名称 in Settings.
 *
 * Endpoints that describe their models are filtered to the ones that can actually be used. Volcano
 * Ark reports `status`, `api_supported` and `modalities`, so retired models (`Retiring`) and
 * non-chat models (image/video generation, embeddings) are dropped instead of filling the picker
 * with 54 mostly useless ids; for the operator picker only models that accept images are offered.
 * Endpoints that describe nothing (Ollama, vLLM, a bare `["m1","m2"]`) pass through untouched.
 *
 * Error messages never contain the API key.
 */
object ModelCatalog {
    /**
     * One advertised model. [chat] and [image] stay null when the endpoint does not say, which means
     * "unknown, keep it": guessing wrong would hide a model the user can actually use.
     */
    data class Entry(
        val id: String,
        val status: String? = null,
        val chat: Boolean? = null,
        val image: Boolean? = null,
        val created: Long = 0,
    ) {
        /** A model the provider is winding down: it still answers, but it is not what you want to pick. */
        val retired: Boolean get() = status?.lowercase()?.let { s -> RETIRED.any { s.contains(it) } } == true

        /** Unknown counts as usable for chat; an explicit "no chat API" does not. */
        val usableForChat: Boolean get() = chat != false

        /**
         * Last resort for entries that describe nothing at all: a name from the picture/video/3D/
         * embedding/translation families is not a chat model. Ignored whenever the endpoint stated
         * anything about capability, so a real chat model is never hidden by its name.
         */
        val looksLikeNonChat: Boolean
            get() = chat == null && NON_CHAT_HINTS.any { id.lowercase().contains(it) }
    }

    /** Ready models plus how many were hidden, so the picker can say so instead of looking wrong. */
    data class Catalog(
        val entries: List<Entry> = emptyList(),
        /** Hidden because the provider marks them retiring/deprecated. */
        val retired: Int = 0,
        /** Hidden because they cannot do the job: not a chat model, or no image input. */
        val unusable: Int = 0,
    ) {
        val ids: List<String> get() = entries.map { it.id }
        val size: Int get() = entries.size
    }

    private val RETIRED = listOf("retir", "deprecat", "offline", "archiv", "eol", "sunset", "disable")

    private val NON_CHAT_HINTS = listOf(
        "embed", "rerank", "seedream", "seedance", "seed3d", "hitem3d", "hyper3d",
        "tts", "asr", "whisper", "translation", "ocr",
    )

    /** Models served by [baseUrl], newest first. [needImage] keeps only models that accept images. */
    suspend fun list(baseUrl: String, apiKey: String, needImage: Boolean = false): Catalog =
        fetch(baseUrl, apiKey, LlmClients.sharedHttp, needImage = needImage)

    internal suspend fun fetch(
        baseUrl: String,
        apiKey: String,
        http: OkHttpClient,
        timeoutSeconds: Long = 20,
        needImage: Boolean = false,
    ): Catalog {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) throw LlmException("Base URL 为空")
        val url = "$base/models"
        val client = http.newBuilder()
            .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build()
        val builder = Request.Builder().url(url).get()
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${apiKey.trim()}")

        val (code, body) = try {
            execute(client, builder.build())
        } catch (e: IOException) {
            throw LlmException(redact("无法连接 $url：${e.message ?: e.javaClass.simpleName}", apiKey), retryable = true, cause = e)
        }
        if (code !in 200..299) {
            throw LlmException(redact("HTTP $code：${errorDetail(body)}", apiKey), code, code == 429 || code in 500..599)
        }
        return parse(body, redact = { redact(it, apiKey) }, needImage = needImage)
    }

    /** Parses a `/models` response body. [redact] scrubs a key that a proxy may echo back. */
    internal fun parse(body: String, redact: (String) -> String = { it }, needImage: Boolean = false): Catalog {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull()
            ?: throw LlmException(redact("返回的不是 JSON：${body.take(120)}"))
        val elements = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["data"] as? JsonArray)
                ?: (root["models"] as? JsonArray)
                ?: throw LlmException(
                    redact((root["error"] as? JsonObject)?.get("message")?.let { (it as? JsonPrimitive)?.contentOrNull }
                        ?: "返回里没有模型列表：${body.take(120)}"),
                )
            else -> throw LlmException(redact("返回里没有模型列表：${body.take(120)}"))
        }

        var retired = 0
        var unusable = 0
        val seen = HashSet<String>()
        val parsed = ArrayList<Entry>(elements.size)
        for (element in elements) {
            val entry = toEntry(element) ?: continue
            if (seen.add(entry.id)) parsed.add(entry)
        }
        // An endpoint that describes modalities for at least one model is expected to describe them
        // for all: entries that stay silent there are old side-products, not models that read images.
        // An endpoint that describes nothing (vLLM, Ollama) must not lose its whole list to this.
        val describesVision = parsed.any { it.image != null }

        val kept = ArrayList<Entry>(parsed.size)
        for (entry in parsed) {
            val readsImages = if (describesVision) entry.image == true else entry.image != false
            when {
                entry.retired -> retired++
                !entry.usableForChat || entry.looksLikeNonChat -> unusable++
                needImage && !readsImages -> unusable++
                else -> kept.add(entry)
            }
        }
        // Newest first is what the ids encode anyway (`-260428`); endpoints without dates fall back
        // to alphabetical order because every created is 0.
        kept.sortWith(compareByDescending<Entry> { it.created }.thenBy { it.id })
        return Catalog(kept.map { it.copy(id = redact(it.id)) }, retired, unusable)
    }

    private fun toEntry(element: kotlinx.serialization.json.JsonElement): Entry? {
        val obj = element as? JsonObject
        val id = when {
            element is JsonPrimitive -> element.contentOrNull
            obj != null -> listOf("id", "name", "model").firstNotNullOfOrNull { key ->
                (obj[key] as? JsonPrimitive)?.contentOrNull
            }
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (obj == null) return Entry(id)

        // Ark lists capabilities as flags; a model with `images/generations` and no `chat/completions`
        // is a picture generator, not a chat model. When `api_supported` is missing, "outputs text"
        // still separates a chat model from an image/video/3D generator. No information at all stays
        // null, which must mean "unknown" rather than "not usable".
        val apiSupported = obj["api_supported"] as? JsonObject
        val chatFlag = (apiSupported?.get("chat/completions") as? JsonPrimitive)?.booleanOrNull
        val modalities = obj["modalities"] as? JsonObject ?: obj["architecture"] as? JsonObject
        val inputs = (modalities?.get("input_modalities") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val outputs = (modalities?.get("output_modalities") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val chat = when {
            apiSupported != null -> chatFlag == true
            outputs != null -> outputs.any { it.equals("text", ignoreCase = true) }
            else -> null
        }
        return Entry(
            id = id,
            status = (obj["status"] as? JsonPrimitive)?.contentOrNull,
            chat = chat,
            image = inputs?.let { list -> list.any { it.equals("image", ignoreCase = true) } },
            created = (obj["created"] as? JsonPrimitive)?.longOrNull ?: 0L,
        )
    }

    private suspend fun execute(http: OkHttpClient, request: Request): Pair<Int, String> = withContext(Dispatchers.IO) {
        val call = http.newCall(request)
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            try {
                val result = call.execute().use { it.code to it.body?.string().orEmpty() }
                cont.resume(result)
            } catch (e: IOException) {
                cont.resumeWithException(e)
            }
        }
    }

    private fun errorDetail(body: String): String {
        val message = runCatching {
            val root = Json.parseToJsonElement(body) as JsonObject
            when (val error = root["error"]) {
                is JsonObject -> (error["message"] as? JsonPrimitive)?.contentOrNull
                is JsonPrimitive -> error.contentOrNull
                else -> (root["message"] as? JsonPrimitive)?.contentOrNull
            }
        }.getOrNull()
        return (message ?: body).trim().take(200).ifBlank { "<空响应>" }
    }

    private fun redact(text: String, apiKey: String): String =
        if (apiKey.isBlank()) text else text.replace(apiKey.trim(), "***")
}
