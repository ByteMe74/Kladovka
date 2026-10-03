package ru.kladovka.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Клиент боевого API (`api.php`).
 *
 * Имена действий сверены с сервером: обмен данными идёт через `import`/`export`
 * (не через `push`/`getLatestData`, которых на сервере нет), авторизация — заголовком
 * `Authorization: Bearer`, URL — только https.
 */
class ApiClient(
    var baseUrl: String = DEFAULT_URL,
    private val http: OkHttpClient = defaultClient()
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /** Приводит адрес к виду `https://host` без хвостового слэша. */
    fun normalized(): String {
        var u = baseUrl.trim()
        if (u.isEmpty()) return ""
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        return u.trimEnd('/')
    }

    // ------------------------------------------------------------------ low level

    private suspend fun apiCall(action: String, body: String?, token: String?): String =
        withContext(Dispatchers.IO) {
            val base = normalized()
            if (base.isEmpty()) throw IOException("Укажите адрес сервера")
            if (!base.startsWith("https://")) {
                throw IOException("Требуется https-адрес сервера (получено: $base)")
            }
            val b = Request.Builder().url("$base/api.php?action=$action")
            if (!token.isNullOrEmpty()) b.addHeader("Authorization", "Bearer $token")
            if (body == null) {
                b.get()
            } else {
                b.post(body.toRequestBody(jsonMedia))
            }
            val resp = b.build().let { http.newCall(it).execute() }
            resp.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful && it.code != 429) {
                    throw IOException("Сервер ответил ${it.code}")
                }
                text
            }
        }

    private fun errorOf(text: String, fallback: String): String = runCatching {
        json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback

    private fun okOf(text: String): Boolean = runCatching {
        json.parseToJsonElement(text).jsonObject["ok"]?.jsonPrimitive?.content == "true"
    }.getOrDefault(false)

    // ------------------------------------------------------------------ auth

    /** Вход по паролю администратора. Возвращает токен. */
    suspend fun login(password: String): String = withContext(Dispatchers.IO) {
        val text = apiCall("login", """{"password":${quote(password)}}""", null)
        json.parseToJsonElement(text).jsonObject["token"]?.jsonPrimitive?.content
            ?.takeIf { it.isNotEmpty() }
            ?: throw IOException(errorOf(text, "Сервер не вернул токен"))
    }

    /** Вход пользователя по имени и паролю. Возвращает пару (токен, подтверждена ли почта). */
    suspend fun loginUser(username: String, password: String): Pair<String, Boolean> {
        val text = apiCall(
            "login",
            """{"username":${quote(username)},"password":${quote(password)}}""",
            null
        )
        val token = json.parseToJsonElement(text).jsonObject["token"]?.jsonPrimitive?.content
            ?.takeIf { it.isNotEmpty() }
            ?: throw IOException(errorOf(text, "Сервер не вернул токен"))
        val o = json.parseToJsonElement(text).jsonObject
        val verified = o["email_verified"]?.jsonPrimitive?.content == "true"
        return token to verified
    }

    // ------------------------------------------------------------------ data exchange

    /** Отправляет локальный бэкап на сервер (полная замена серверных данных). */
    suspend fun push(token: String, backupJson: String) = withContext(Dispatchers.IO) {
        val payload = """{"json":${quote(backupJson)}}"""
        val text = apiCall("import", payload, token)
        if (!okOf(text)) throw IOException(errorOf(text, "Сервер не принял данные"))
    }

    /**
     * Забирает бэкап с сервера. Сервер отдаёт «голый» объект таблиц — оборачиваем
     * его в формат приложения (`app=kladovka`), чтобы его принял [Backup.parse].
     */
    suspend fun pull(token: String): AppData = withContext(Dispatchers.IO) {
        val text = apiCall("export", null, token)
        val server = json.parseToJsonElement(text).jsonObject
        val wrapped = buildString {
            append("""{"app":"kladovka"""")
            for (t in listOf("places", "shelves", "polki", "containers", "items")) {
                val raw = server[t]?.toString() ?: "[]"
                append(""","$t":$raw""")
            }
            append("}")
        }
        Backup.parse(wrapped)
    }

    // ------------------------------------------------------------------ sharing

    suspend fun share(token: String, withUsername: String) = withContext(Dispatchers.IO) {
        val text = apiCall("share", """{"with_username":${quote(withUsername)}}""", token)
        val o = json.parseToJsonElement(text).jsonObject
        if (o.containsKey("error")) throw IOException(o["error"]!!.jsonPrimitive.content)
    }

    suspend fun unshare(token: String, withUsername: String) = withContext(Dispatchers.IO) {
        apiCall("unshare", """{"with_username":${quote(withUsername)}}""", token)
    }

    /** Кому я открыл доступ (given) и кто открыл мне (received). */
    suspend fun shares(token: String): Pair<List<String>, List<String>> = withContext(Dispatchers.IO) {
        val o = json.parseToJsonElement(apiCall("shares", null, token)).jsonObject
        fun list(k: String) = (o[k] as? JsonArray)
            ?.map { it.jsonPrimitive.content } ?: emptyList()
        list("given") to list("received")
    }

    // ------------------------------------------------------------------ profile

    suspend fun profile(token: String): UserProfile = withContext(Dispatchers.IO) {
        val o = json.parseToJsonElement(apiCall("profile", null, token)).jsonObject
        if (o["ok"]?.jsonPrimitive?.content != "true") {
            throw IOException(o["error"]?.jsonPrimitive?.content ?: "Не удалось получить профиль")
        }
        fun p(k: String) = o[k]?.jsonPrimitive?.content
        UserProfile(
            id = p("id")?.toLongOrNull() ?: 0,
            username = p("username").orEmpty(),
            email = p("email").orEmpty(),
            emailVerified = o["email_verified"]?.jsonPrimitive?.content == "true",
            createdAt = p("created_at")?.toLongOrNull() ?: 0
        )
    }

    /** Свежая версия, которую отдаёт сервер (`latestApk`). */
    data class LatestVersion(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val size: Long
    )

    suspend fun latestVersion(): LatestVersion? = withContext(Dispatchers.IO) {
        runCatching {
            val o = json.parseToJsonElement(apiCall("latestApk", null, null)).jsonObject
            fun p(k: String) = o[k]?.jsonPrimitive?.content
            LatestVersion(
                versionCode = p("versionCode")?.toIntOrNull() ?: 0,
                versionName = p("versionName").orEmpty(),
                url = p("url").orEmpty(),
                size = p("size")?.toLongOrNull() ?: 0
            ).takeIf { it.versionName.isNotBlank() }
        }.getOrNull()
    }

    /** Минимальное экранирование строки для вставки в JSON. */
    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
        return sb.append('"').toString()
    }

    companion object {
        const val DEFAULT_URL = "https://kladovka.dr6ter.ru"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
