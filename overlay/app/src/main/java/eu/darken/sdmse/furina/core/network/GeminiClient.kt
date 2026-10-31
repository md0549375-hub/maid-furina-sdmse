package eu.darken.sdmse.furina.core.network

import eu.darken.sdmse.furina.core.data.FurinaSettings
import eu.darken.sdmse.furina.core.data.SecretStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

sealed interface KeyCheck {
    data object Valid : KeyCheck
    data class Invalid(val reason: String) : KeyCheck
    data class Unavailable(val reason: String) : KeyCheck
}

data class FunctionCall(val name: String, val args: JSONObject, val id: String?)

/** [rawContent] must be echoed back verbatim when answering function calls (thought signatures). */
data class ModelTurn(val rawContent: JSONObject, val text: String, val calls: List<FunctionCall>)

class GeminiException(val kind: Kind, message: String, val httpCode: Int? = null) : Exception(message) {
    enum class Kind { NO_KEY, INVALID_KEY, RATE_LIMIT, NETWORK, SERVER, BLOCKED, BAD_REQUEST, BAD_RESPONSE }
}

/**
 * Minimal Gemini REST client (generateContent + function calling) on OkHttp and org.json.
 * The key travels in the x-goog-api-key header, never in the URL, and is never logged.
 */
@Singleton
class GeminiClient @Inject constructor(
    private val secrets: SecretStore,
    private val settings: FurinaSettings,
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(75, TimeUnit.SECONDS)
        .build()

    suspend fun checkKey(apiKey: String): KeyCheck = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext KeyCheck.Invalid("Anahtar boş")
        if (!isHeaderSafe(key)) return@withContext KeyCheck.Invalid("Anahtar geçersiz karakter içeriyor")

        val request = Request.Builder()
            .url("$BASE_URL/models?pageSize=1")
            .header(HEADER_API_KEY, key)
            .get()
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string()
                when {
                    response.isSuccessful -> KeyCheck.Valid
                    response.code == 400 || response.code == 401 || response.code == 403 ->
                        KeyCheck.Invalid(extractErrorMessage(body) ?: "HTTP ${response.code}")
                    response.code == 429 -> KeyCheck.Unavailable("Kota sınırına takıldı, biraz sonra tekrar dene")
                    else -> KeyCheck.Unavailable("Sunucu yanıtı: HTTP ${response.code}")
                }
            }
        } catch (e: IOException) {
            KeyCheck.Unavailable(e.message ?: "Ağ hatası")
        }
    }

    suspend fun generate(systemPrompt: String, contents: JSONArray, tools: JSONArray?): ModelTurn =
        withContext(Dispatchers.IO) {
            val key = secrets.readApiKey()
                ?: throw GeminiException(GeminiException.Kind.NO_KEY, "Gemini API anahtarı girilmemiş")
            val model = settings.model.takeIf { MODEL_REGEX.matches(it) } ?: FurinaSettings.DEFAULT_MODEL

            val payload = JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                put("contents", contents)
                if (tools != null) {
                    put("tools", tools)
                    put("toolConfig", JSONObject().put("functionCallingConfig", JSONObject().put("mode", "AUTO")))
                }
                put("generationConfig", JSONObject().put("temperature", 0.9).put("maxOutputTokens", 1024))
            }

            val request = Request.Builder()
                .url("$BASE_URL/models/$model:generateContent")
                .header(HEADER_API_KEY, key)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            try {
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) throw mapHttpError(response.code, body)
                    parseTurn(body)
                }
            } catch (e: IOException) {
                throw GeminiException(GeminiException.Kind.NETWORK, e.message ?: "Ağ hatası")
            }
        }

    private fun parseTurn(body: String): ModelTurn {
        val root = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw GeminiException(GeminiException.Kind.BAD_RESPONSE, "Çözümlenemeyen yanıt")
        }

        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
        if (candidate == null) {
            val blockReason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw GeminiException(
                if (blockReason.isNotEmpty()) GeminiException.Kind.BLOCKED else GeminiException.Kind.BAD_RESPONSE,
                if (blockReason.isNotEmpty()) "Mesaj güvenlik filtresine takıldı ($blockReason)" else "Boş yanıt",
            )
        }

        val content = candidate.optJSONObject("content")
        if (content == null || content.optJSONArray("parts") == null) {
            val finish = candidate.optString("finishReason")
            throw GeminiException(
                if (finish == "SAFETY" || finish == "PROHIBITED_CONTENT") GeminiException.Kind.BLOCKED
                else GeminiException.Kind.BAD_RESPONSE,
                "Model içerik döndürmedi ($finish)",
            )
        }
        if (!content.has("role")) content.put("role", "model")

        val parts = content.getJSONArray("parts")
        val text = StringBuilder()
        val calls = ArrayList<FunctionCall>()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            part.optJSONObject("functionCall")?.let { fc ->
                calls += FunctionCall(
                    name = fc.optString("name"),
                    args = fc.optJSONObject("args") ?: JSONObject(),
                    id = fc.optString("id").takeIf { it.isNotEmpty() },
                )
            }
            if (part.has("text") && !part.optBoolean("thought", false)) text.append(part.optString("text"))
        }
        return ModelTurn(rawContent = content, text = text.toString().trim(), calls = calls)
    }

    private fun mapHttpError(code: Int, body: String): GeminiException {
        val message = extractErrorMessage(body) ?: "HTTP $code"
        val kind = when {
            code == 401 || code == 403 -> GeminiException.Kind.INVALID_KEY
            code == 400 && message.contains("API key", ignoreCase = true) -> GeminiException.Kind.INVALID_KEY
            code == 429 -> GeminiException.Kind.RATE_LIMIT
            code in 500..599 -> GeminiException.Kind.SERVER
            else -> GeminiException.Kind.BAD_REQUEST
        }
        return GeminiException(kind, message, code)
    }

    private fun extractErrorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }?.take(200)
        } catch (e: JSONException) {
            null
        }
    }

    private fun isHeaderSafe(value: String): Boolean = value.all { it.code in 33..126 }

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val HEADER_API_KEY = "x-goog-api-key"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** The model id ends up in the URL path, so only plain id characters are allowed. */
        private val MODEL_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$")

        fun isValidModelName(name: String): Boolean = MODEL_REGEX.matches(name.trim())
    }
}
