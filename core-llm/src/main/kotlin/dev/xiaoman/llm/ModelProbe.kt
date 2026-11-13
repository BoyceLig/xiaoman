package dev.xiaoman.llm

import dev.xiaoman.api.ChatMessage
import dev.xiaoman.api.ChatRequest
import dev.xiaoman.api.ChatRole
import dev.xiaoman.api.ContentPart
import dev.xiaoman.api.LlmException
import dev.xiaoman.api.ModelConfig
import kotlinx.coroutines.CancellationException
import java.util.Base64

/**
 * One real round trip to an OpenAI-compatible endpoint, for the 测试连接 buttons in Settings.
 *
 * The chat model only has to answer; the operator model also has to *read* an image, so it is asked
 * about a tiny 64x64 PNG (image input, not image generation). When the endpoint refuses the image the
 * probe sends a text-only request as well, which separates three very different cases:
 * the endpoint is broken, the model cannot read images, or the image itself was rejected.
 *
 * Never throws: every failure becomes a [Report] whose text is safe to show (no API key, provider
 * bodies truncated).
 */
object ModelProbe {
    /**
     * @param ok the endpoint answered as configured.
     * @param detail one line for the settings screen.
     * @param visionRejected the endpoint works, the model refused the image: fine for chat, useless
     *   as the operator model.
     */
    data class Report(
        val ok: Boolean,
        val detail: String,
        val millis: Long = 0,
        val visionRejected: Boolean = false,
    )

    private const val PROMPT = "只回复两个字：连通"
    private const val IMAGE_PROMPT = "这张图是什么颜色？只回一个词"
    private const val TIMEOUT_SECONDS = 35L

    /**
     * 64x64 solid red PNG, 136 bytes.
     *
     * Size matters: Volcano Ark rejects images under 14px with
     * `Image dimensions are too small. Minimum allowed dimension: 14 pixels`, which looks exactly
     * like "this model cannot see" if you only look at the status code. 64 leaves room for other
     * providers that want more.
     */
    internal val probeImageBytes: ByteArray = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAIAAAAlC+aJAAAAT0lEQVR42u3PQQkAAAgEsItz/fMYxgi+hcEKLNO+FgEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQGBywLzk8EPlvGqjQAAAABJRU5ErkJggg==",
    )

    suspend fun run(config: ModelConfig, withImage: Boolean = false): Report {
        if (config.baseUrl.isBlank()) return Report(false, "还没有填 Base URL")
        if (config.model.isBlank()) return Report(false, "还没有填模型名称")

        val started = System.nanoTime()
        return try {
            val reply = client(config).complete(request(withImage))
            val elapsed = elapsed(started)
            val answer = reply.text.trim().replace('\n', ' ').take(40).ifBlank { "<空>" }
            Report(true, "连通 · ${elapsed}ms${if (withImage) " · 已读图" else ""} · 回复：$answer", elapsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LlmException) {
            val elapsed = elapsed(started)
            if (withImage && e.statusCode != null && e.statusCode in 400..499) diagnoseImage(config, e, elapsed) else
                Report(false, explain(e), elapsed)
        } catch (e: Exception) {
            Report(false, "连接失败：${(e.message ?: e.javaClass.simpleName).take(200)}", elapsed(started))
        }
    }

    /** The image was refused. A text-only request then says whether the endpoint works at all. */
    private suspend fun diagnoseImage(config: ModelConfig, imageError: LlmException, elapsed: Long): Report {
        val provider = providerMessage(imageError)
        val textWorks = try {
            client(config).complete(request(withImage = false))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (!textWorks) return Report(false, explain(imageError), elapsed)

        return if (looksLikeNoVision(provider)) {
            Report(
                false,
                "接口和 Key 都没问题，但 ${config.model} 不接受图片输入（HTTP ${imageError.statusCode}：$provider）。" +
                    "操作模型要能看屏幕截图，请换一个支持视觉理解的模型。",
                elapsed,
                visionRejected = true,
            )
        } else {
            // Could be a size/format complaint or a transient provider issue; do not blame the model.
            Report(false, "接口和 Key 都没问题，但图片请求被拒（HTTP ${imageError.statusCode}：$provider）", elapsed)
        }
    }

    private fun client(config: ModelConfig) =
        // No retries: a connectivity test should answer in seconds, not after three backoffs.
        OpenAiCompatibleClient(config.copy(timeoutSeconds = TIMEOUT_SECONDS), maxRetries = 0)

    private fun request(withImage: Boolean): ChatRequest {
        val message = if (!withImage) {
            ChatMessage.user(PROMPT)
        } else {
            ChatMessage(ChatRole.USER, listOf(ContentPart.Text(IMAGE_PROMPT), ContentPart.ImagePng(probeImageBytes)))
        }
        // Reasoning models reject `temperature`, so nothing optional is sent beyond the messages.
        return ChatRequest(messages = listOf(message))
    }

    /** A provider message is only re-used when it actually denies visual input. */
    internal fun looksLikeNoVision(providerMessage: String): Boolean {
        val text = providerMessage.lowercase()
        val mentionsImage = listOf("image", "vision", "图片", "图像", "视觉").any { text.contains(it) }
        val denies = listOf("not support", "unsupported", "does not support", "不支持", "无法识别", "cannot process")
            .any { text.contains(it) }
        return mentionsImage && denies
    }

    /** Turns the client's exception into "what is actually wrong", plus the provider's own words. */
    internal fun explain(e: LlmException): String {
        val status = e.statusCode
        val provider = providerMessage(e)
        val hint = when {
            e.providerCode == "ModelNotOpen" || provider.contains("not activated", ignoreCase = true) ->
                "这个账号还没有开通该模型，请先在方舟控制台开通"
            status == 401 || status == 403 -> "API Key 不对，或这个 Key 没有该模型的权限"
            status == 404 -> "地址或模型名称不对（接口返回 404）"
            status == 429 -> "被限流了，或账户余额/额度不足"
            status != null && status in 500..599 -> "模型服务端出错，稍后再试"
            status != null -> "接口返回 HTTP $status"
            else -> "网络连不上"
        }
        return if (provider.isBlank()) "连接失败：$hint" else "连接失败：$hint（$provider）"
    }

    /** The client prefixes its own `HTTP 404 from <url>: ` / `Request to <url> failed: ` noise. */
    private fun providerMessage(e: LlmException): String =
        e.message.orEmpty().trim()
            .substringAfter(": ", "")
            .replace('\n', ' ')
            .trim()
            .take(200)

    private fun elapsed(startedNanos: Long) = (System.nanoTime() - startedNanos) / 1_000_000
}
