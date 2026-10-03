package ru.kladovka.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import androidx.room.withTransaction

/**
 * Ошибка API с кодом ответа.
 *
 * Тип нужен, чтобы отличить «сессия отозвана» (401) от любой другой ошибки.
 * Раньше на любой не-2xx приходило «Сервер ответил HTTP 401» — после того, как
 * выход на сайте стал настоящим отзывом, это означало, что приложение молча
 * перестаёт синхронизироваться и не говорит человеку почему.
 */
class ApiException(val code: Int, message: String) : Exception(message)

class Repository(private val db: AppDatabase, private val appContext: Context) {

    val places: Flow<List<Place>> = db.placeDao().observeAll()
    val shelves: Flow<List<Shelf>> = db.shelfDao().observeAll()
    val polki: Flow<List<Polka>> = db.polkaDao().observeAll()
    val containers: Flow<List<Container>> = db.containerDao().observeAll()
    val items: Flow<List<Item>> = db.itemDao().observeAll()

    fun searchItems(q: String): Flow<List<Item>> =
        if (q.isBlank()) db.itemDao().observeAll() else db.itemDao().search(q.trim())

    suspend fun itemById(id: Long): Item? = db.itemDao().getById(id)

    suspend fun categories(): List<String> = db.itemDao().categories()

    /* ---------- Места ---------- */

    suspend fun upsertPlace(id: Long, name: String, notes: String, latitude: Double?, longitude: Double?) {
        val n = name.trim()
        if (n.isEmpty()) return
        val lat = latitude?.takeIf { it in -90.0..90.0 }
        val lon = longitude?.takeIf { it in -180.0..180.0 }
        if (id == 0L) {
            db.placeDao().insert(Place(name = n, notes = notes.trim(), latitude = lat, longitude = lon))
        } else {
            val old = db.placeDao().getById(id) ?: return
            db.placeDao().update(old.copy(name = n, notes = notes.trim(), latitude = lat, longitude = lon))
        }
    }

    suspend fun deletePlace(id: Long) {
        db.placeDao().detachShelves(id)
        db.placeDao().detachPolki(id)
        db.placeDao().detachContainers(id)
        db.placeDao().detachItems(id)
        db.placeDao().deleteById(id)
    }

    /* ---------- Полки (polki) ---------- */

    suspend fun upsertPolka(id: Long, name: String, notes: String, shelfId: Long?, placeId: Long?) {
        val n = name.trim()
        if (n.isEmpty()) return
        if (id == 0L) {
            db.polkaDao().insert(Polka(name = n, notes = notes.trim(), shelfId = shelfId, placeId = placeId))
        } else {
            val old = db.polkaDao().getById(id) ?: return
            db.polkaDao().update(old.copy(name = n, notes = notes.trim(), shelfId = shelfId, placeId = placeId))
        }
    }

    suspend fun deletePolka(id: Long) {
        db.polkaDao().deleteById(id)
    }

    /* ---------- Стеллажи ---------- */

    suspend fun upsertShelf(id: Long, name: String, notes: String, placeId: Long?) {
        val n = name.trim()
        if (n.isEmpty()) return
        if (id == 0L) {
            db.shelfDao().insert(Shelf(name = n, notes = notes.trim(), placeId = placeId))
        } else {
            val old = db.shelfDao().getById(id) ?: return
            db.shelfDao().update(old.copy(name = n, notes = notes.trim(), placeId = placeId))
        }
    }

    suspend fun deleteShelf(id: Long) {
        db.shelfDao().detachPolki(id)
        db.shelfDao().detachContainers(id)
        db.shelfDao().detachItems(id)
        db.shelfDao().deleteById(id)
    }

    /* ---------- Контейнеры ---------- */

    suspend fun upsertContainer(id: Long, name: String, shelfId: Long?, placeId: Long?) {
        val n = name.trim()
        if (n.isEmpty()) return
        if (id == 0L) {
            db.containerDao().insert(Container(name = n, shelfId = shelfId, placeId = placeId))
        } else {
            val old = db.containerDao().getById(id) ?: return
            db.containerDao().update(old.copy(name = n, shelfId = shelfId, placeId = placeId))
        }
    }

    suspend fun deleteContainer(id: Long) {
        db.containerDao().detachItems(id)
        db.containerDao().deleteById(id)
    }

    /* ---------- Вещи ---------- */

    suspend fun upsertItem(
        id: Long,
        name: String,
        quantity: Int,
        unit: String,
        category: String,
        notes: String,
        containerId: Long?,
        shelfId: Long?,
        placeId: Long?,
        photoPath: String?
    ) {
        val n = name.trim()
        if (n.isEmpty()) return
        val now = System.currentTimeMillis()
        if (id == 0L) {
            db.itemDao().insert(
                Item(
                    name = n,
                    quantity = quantity,
                    unit = unit.trim(),
                    category = category.trim(),
                    notes = notes.trim(),
                    containerId = containerId,
                    shelfId = shelfId,
                    placeId = placeId,
                    photoPath = photoPath,
                    createdAt = now,
                    updatedAt = now
                )
            )
        } else {
            val old = db.itemDao().getById(id) ?: return
            db.itemDao().update(
                old.copy(
                    name = n,
                    quantity = quantity,
                    unit = unit.trim(),
                    category = category.trim(),
                    notes = notes.trim(),
                    containerId = containerId,
                    shelfId = shelfId,
                    placeId = placeId,
                    photoPath = photoPath,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun deleteItem(id: Long) {
        val old = db.itemDao().getById(id)
        db.itemDao().deleteById(id)
        old?.photoPath?.let { deletePhotoFile(it) }
    }

    /** Закрепить/открепить вещь (⭐ — всегда в начале списка). */
    suspend fun setItemPinned(id: Long, pinned: Boolean) {
        val old = db.itemDao().getById(id) ?: return
        db.itemDao().update(
            old.copy(pinned = pinned, updatedAt = System.currentTimeMillis())
        )
    }

    /** Быстрое изменение количества из списка (+1/−1, не ниже 1). Атомарно — UPDATE в одной
     *  SQL-операции (WHERE id), чтобы два устройства в один момент не потеряли «+1» из-за гонки. */
    suspend fun changeItemQuantity(id: Long, delta: Int) {
        db.withTransaction {
            db.itemDao().atomicallyAdjustQuantity(id, delta, System.currentTimeMillis())
        }
    }

    /* ---------- Фото ---------- */

    /** Сохраняет фото (из галереи или после съёмки) во внутреннее хранилище, возвращает путь или null. */
    suspend fun savePhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(appContext.filesDir, "photos").apply { mkdirs() }
            val out = File(dir, UUID.randomUUID().toString() + ".jpg")

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sample = 1
            while (bounds.outWidth / sample > 3200 || bounds.outHeight / sample > 3200) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap: Bitmap = appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@runCatching null

            FileOutputStream(out).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 87, fos)
            }
            out.absolutePath
        }.getOrNull()
    }

    fun deletePhotoFile(path: String) {
        runCatching { File(path).delete() }
    }

    /* ---------- Бэкап (JSON) ---------- */

    /** Полный экспорт всех данных в JSON-строку. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("app", "kladovka")
            put("exportedAt", System.currentTimeMillis())

            put("places", JSONArray().apply {
                db.placeDao().observeAll().first().forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("latitude", p.latitude)
                        put("longitude", p.longitude)
                        put("notes", p.notes)
                    })
                }
            })
            put("shelves", JSONArray().apply {
                db.shelfDao().observeAll().first().forEach { s ->
                    put(JSONObject().apply {
                        put("id", s.id)
                        put("name", s.name)
                        put("notes", s.notes)
                        put("placeId", s.placeId)
                        put("location", s.location)
                    })
                }
            })
            put("polki", JSONArray().apply {
                db.polkaDao().observeAll().first().forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("notes", p.notes)
                        put("shelfId", p.shelfId)
                        put("placeId", p.placeId)
                    })
                }
            })
            put("containers", JSONArray().apply {
                db.containerDao().observeAll().first().forEach { c ->
                    put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("shelfId", c.shelfId)
                        put("placeId", c.placeId)
                        put("location", c.location)
                    })
                }
            })
            put("items", JSONArray().apply {
                db.itemDao().observeAll().first().forEach { i ->
                    put(JSONObject().apply {
                        put("id", i.id)
                        put("name", i.name)
                        put("quantity", i.quantity)
                        put("unit", i.unit)
                        put("category", i.category)
                        put("notes", i.notes)
                        put("containerId", i.containerId)
                        put("shelfId", i.shelfId)
                        put("placeId", i.placeId)
                        put("photoPath", i.photoPath)
                        put("pinned", if (i.pinned) 1 else 0)
                        put("createdAt", i.createdAt)
                        put("updatedAt", i.updatedAt)
                    })
                }
            })
        }
        root.toString(2)
    }

    /**
     * Импорт бэкапа: полностью заменяет содержимое базы данными из JSON.
     * Возвращает true при успехе. Фото не переносятся — если файла по пути нет, карточка будет без фото.
     */
    suspend fun importJson(json: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(json)
            if (root.optString("app") != "kladovka") return@runCatching false

            // Сначала полностью очищаем базу, затем вставляем «родители → дети». Вся замена —
            // ОДНА транзакция: если вставка упадёт, SQLite откатится, и на телефоне останется
            // прежняя база, а не полуочищенная (защита от потери данных при битом дампе).
            db.withTransaction {
                db.clearAllTables()

            val places = root.optJSONArray("places") ?: JSONArray()
            for (i in 0 until places.length()) {
                val o = places.getJSONObject(i)
                db.placeDao().insert(
                    Place(
                        id = o.optLong("id"),
                        name = o.optString("name"),
                        latitude = if (o.isNull("latitude")) null else o.optDouble("latitude"),
                        longitude = if (o.isNull("longitude")) null else o.optDouble("longitude"),
                        notes = o.optString("notes")
                    )
                )
            }

            val shelves = root.optJSONArray("shelves") ?: JSONArray()
            for (i in 0 until shelves.length()) {
                val o = shelves.getJSONObject(i)
                db.shelfDao().insert(
                    Shelf(
                        id = o.optLong("id"),
                        name = o.optString("name"),
                        notes = o.optString("notes"),
                        placeId = if (o.isNull("placeId")) null else o.optLong("placeId"),
                        location = o.optString("location")
                    )
                )
            }

            val polki = root.optJSONArray("polki") ?: JSONArray()
            for (i in 0 until polki.length()) {
                val o = polki.getJSONObject(i)
                db.polkaDao().insert(
                    Polka(
                        id = o.optLong("id"),
                        name = o.optString("name"),
                        notes = o.optString("notes"),
                        shelfId = if (o.isNull("shelfId")) null else o.optLong("shelfId"),
                        placeId = if (o.isNull("placeId")) null else o.optLong("placeId")
                    )
                )
            }

            val containers = root.optJSONArray("containers") ?: JSONArray()
            for (i in 0 until containers.length()) {
                val o = containers.getJSONObject(i)
                db.containerDao().insert(
                    Container(
                        id = o.optLong("id"),
                        name = o.optString("name"),
                        shelfId = if (o.isNull("shelfId")) null else o.optLong("shelfId"),
                        placeId = if (o.isNull("placeId")) null else o.optLong("placeId"),
                        location = o.optString("location")
                    )
                )
            }

            val items = root.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                db.itemDao().insert(
                    Item(
                        id = o.optLong("id"),
                        name = o.optString("name"),
                        quantity = o.optInt("quantity", 1),
                        unit = o.optString("unit"),
                        category = o.optString("category"),
                        notes = o.optString("notes"),
                        containerId = if (o.isNull("containerId")) null else o.optLong("containerId"),
                        shelfId = if (o.isNull("shelfId")) null else o.optLong("shelfId"),
                        placeId = if (o.isNull("placeId")) null else o.optLong("placeId"),
                        photoPath = if (o.isNull("photoPath")) null else o.optString("photoPath"),
                        pinned = o.optInt("pinned", 0) != 0,
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            true
            } // db.withTransaction
        }.getOrDefault(false)
    }

    /* ---------- Экспорт в CSV (для Excel/таблиц) ---------- */

    /**
     * Экспорт всех вещей в CSV с разделителем «;» и BOM (UTF-8), чтобы
     * Excel корректно открыл файл сразу. Колонки: название, кол-во, ед.,
     * категория, место (полный путь), заметки.
     */
    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        val places = db.placeDao().observeAll().first().associateBy { it.id }
        val shelves = db.shelfDao().observeAll().first().associateBy { it.id }
        val containers = db.containerDao().observeAll().first().associateBy { it.id }
        val items = db.itemDao().observeAll().first()

        fun esc(s: String): String =
            "\"" + s.replace("\"", "\"\"") + "\""

        fun itemLocation(i: Item): String {
            val c = i.containerId?.let(containers::get)
            val s = c?.shelfId?.let(shelves::get) ?: i.shelfId?.let(shelves::get)
            val p = i.placeId?.let(places::get) ?: c?.placeId?.let(places::get) ?: s?.placeId?.let(places::get)
            return listOfNotNull(
                p?.name,
                c?.name,
                s?.name
            ).joinToString(" · ").ifEmpty { "Без места" }
        }

        buildString {
            append('\uFEFF') // BOM для Excel
            append("Название;Кол-во;Ед.;Категория;Место;Заметки\r\n")
            items.sortedBy { it.name.lowercase() }.forEach { i ->
                append(esc(i.name)).append(';')
                append(i.quantity).append(';')
                append(esc(i.unit)).append(';')
                append(esc(i.category)).append(';')
                append(esc(itemLocation(i))).append(';')
                append(esc(i.notes)).append("\r\n")
            }
        }
    }

    /* ---------- Синхронизация с сервером (kladovka.dr6ter.ru) ---------- */

    /** Доводит адрес сервера до вида https://host (без хвостового слэша). */
    private fun normalizeServerUrl(raw: String): String {
        val url = raw.trim().trimEnd('/')
        if (url.isEmpty()) return ""
        return if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    }

    /** Один запрос к API. Бросает RuntimeException с понятным текстом при ошибке. */
    private fun apiCall(fullUrl: String, bodyJson: String?, token: String?): String {
        val conn = URL(fullUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.requestMethod = if (bodyJson != null) "POST" else "GET"
            conn.setRequestProperty("Accept", "application/json")
            if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
            if (bodyJson != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) {
                val serverError = runCatching { JSONObject(body).optString("error") }.getOrDefault("")
                throw ApiException(code, httpMessage(code, serverError))
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Текст ошибки для показа человеку.
     *
     * «Не авторизовано» на 401 — это отозванный или сброшенный вход (например,
     * пользователь вышел из аккаунта на сайте), а не неверный пароль: для того
     * сервер отдаёт «Неверный логин или пароль», и его текст мы не трогаем.
     */
    private fun httpMessage(code: Int, serverError: String): String = when {
        code == 401 && serverError.contains("Не авторизовано", ignoreCase = true) ->
            "Сессия отозвана на сервере — например, вы вышли из аккаунта на сайте. Нажмите «Подключиться к серверу», чтобы войти снова."
        serverError.isNotEmpty() -> serverError
        code == 403 -> "Недостаточно прав для этого действия"
        else -> "Сервер ответил HTTP $code"
    }

    /** Вход: для аккаунта — имя пользователя + пароль; пустое имя = вход администратора (только пароль). */
    suspend fun serverLogin(rawUrl: String, username: String, password: String): String = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawUrl)
        if (base.isEmpty()) throw RuntimeException("Укажите адрес сервера")
        if (password.isEmpty()) throw RuntimeException("Введите пароль")
        val payload = JSONObject()
        if (username.isNotBlank()) payload.put("username", username.trim())
        payload.put("password", password)
        val body = apiCall("$base/api.php?action=login", payload.toString(), null)
        val parsed = JSONObject(body)
        val token = parsed.optString("token")
        if (token.isEmpty()) throw RuntimeException(parsed.optString("error", "Сервер не принял пароль"))
        // Для аккаунтов: без подтверждённой почты сервер разрешит вход, но не отдаст данные —
        // сразу сообщаем пользователю, что нужно подтвердить почту
        if (parsed.optString("role") == "user" && parsed.optBoolean("email_verified") != true) {
            throw RuntimeException("Аккаунт не подтверждён: перейдите по ссылке из письма, затем войдите снова")
        }
        token
    }

    /**
     * Выход: сервер отзывает вход пользователя, поэтому токен перестаёт работать
     * и после повторного входа нужно получить новый. Раньше такой функции не было
     * вовсе — из приложения выйти было нельзя, только закрыть его.
     *
     * Ошибку сюда не бросаем намеренно: локальные учётные данные стираются в любом
     * случае, а сервер их всё равно отозвал (токен статистичный, он был один).
     */
    suspend fun serverLogout(rawUrl: String, token: String) = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawUrl)
        if (base.isEmpty() || token.isEmpty()) return@withContext
        runCatching { apiCall("$base/api.php?action=logout", "{}", token) }
    }

    /** Регистрация нового аккаунта: логин, пароль, email. Возвращает токен если email уже подтверждён, иначе null. */
    suspend fun serverRegister(rawUrl: String, username: String, password: String, email: String): String? = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawUrl)
        if (base.isEmpty()) throw RuntimeException("Укажите адрес сервера")
        if (username.isBlank()) throw RuntimeException("Введите имя пользователя")
        if (password.isBlank()) throw RuntimeException("Введите пароль")
        if (email.isBlank()) throw RuntimeException("Введите email")
        val payload = JSONObject().apply {
            put("username", username.trim())
            put("password", password)
            put("email", email.trim().lowercase())
        }
        val body = apiCall("$base/api.php?action=register", payload.toString(), null)
        val parsed = JSONObject(body)
        if (!parsed.optBoolean("ok")) throw RuntimeException(parsed.optString("error", "Не удалось зарегистрироваться"))
        // Если email подтверждён — вернёт токен, иначе null
        parsed.optString("token").ifEmpty { null }
    }

    /** Отправка своего бэкапа: данные на сервере заменяются данными телефона. */
    suspend fun serverPush(rawUrl: String, token: String, json: String) = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawUrl)
        if (base.isEmpty()) throw RuntimeException("Укажите адрес сервера")
        val payload = JSONObject().put("json", json).toString()
        val resp = apiCall("$base/api.php?action=import", payload, token)
        if (JSONObject(resp).optBoolean("ok") != true) throw RuntimeException("Сервер не принял данные")
    }

    /**
     * Загрузка бэкапа с сервера. Сервер отдаёт «голый» объект таблиц —
     * оборачиваем его в формат приложения (app=kladovka), чтобы importJson его принял.
     */
    suspend fun serverFetch(rawUrl: String, token: String): String = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawUrl)
        if (base.isEmpty()) throw RuntimeException("Укажите адрес сервера")
        val body = apiCall("$base/api.php?action=export", null, token)
        val server = JSONObject(body)
        val root = JSONObject().put("app", "kladovka")
        for (t in listOf("places", "shelves", "polki", "containers", "items")) {
            root.put(t, server.optJSONArray(t) ?: JSONArray())
        }
        root.toString()
    }

    /** Открыть доступ к своему складу другому пользователю (совместный учёт). */
    suspend fun serverShare(rawUrl: String, token: String, withUsername: String): String = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawUrl)
        if (base.isEmpty()) throw RuntimeException("Укажите адрес сервера")
        val payload = JSONObject().put("with_username", withUsername.trim()).toString()
        val j = JSONObject(apiCall("$base/api.php?action=share", payload, token))
        val err = j.optString("error", "")
        if (err.isNotEmpty()) throw RuntimeException(err)
        j.optString("shared_with", withUsername.trim())
    }

    /** Отозвать доступ у пользователя. */
    suspend fun serverUnshare(rawUrl: String, token: String, withUsername: String) = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawUrl)
        if (base.isEmpty()) throw RuntimeException("Укажите адрес сервера")
        val payload = JSONObject().put("with_username", withUsername.trim()).toString()
        apiCall("$base/api.php?action=unshare", payload, token)
    }

    /** Списки: кому я дал доступ и кто дал мне. */
    suspend fun serverShares(rawUrl: String, token: String): Pair<List<String>, List<String>> = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawUrl)
        if (base.isEmpty()) throw RuntimeException("Укажите адрес сервера")
        val j = JSONObject(apiCall("$base/api.php?action=shares", null, token))
        fun arr(k: String): List<String> {
            val a = j.optJSONArray(k) ?: return emptyList()
            return (0 until a.length()).map { a.getString(it) }
        }
        arr("given") to arr("received")
    }

    /* ---------- Личный кабинет ---------- */

    /** Данные аккаунта: имя пользователя, почта, статус подтверждения. */
    data class UserProfile(
        val id: Long,
        val username: String,
        val email: String,
        val emailVerified: Boolean,
        val createdAt: Long
    )

    /** Запросить данные своего аккаунта (api.php?action=profile). */
    suspend fun serverProfile(rawUrl: String, token: String): UserProfile = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawUrl)
        if (base.isEmpty()) throw RuntimeException("Укажите адрес сервера")
        val j = JSONObject(apiCall("$base/api.php?action=profile", null, token))
        if (j.optBoolean("ok") != true) throw RuntimeException(j.optString("error", "Не удалось получить профиль"))
        UserProfile(
            id = j.optLong("id", 0),
            username = j.optString("username", ""),
            email = j.optString("email", ""),
            emailVerified = j.optBoolean("email_verified", false),
            createdAt = j.optLong("created_at", 0)
        )
    }

    /**
     * Изменить данные аккаунта: имя пользователя и/или почту (подтверждение письмом)
     * и/или пароль. Возвращает (email_sent, email_verified_after).
     */
    suspend fun serverUpdateProfile(
        rawUrl: String,
        token: String,
        username: String?,
        email: String?,
        currentPassword: String?,
        newPassword: String?
    ): Pair<Boolean, Boolean> = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawUrl)
        if (base.isEmpty()) throw RuntimeException("Укажите адрес сервера")
        val payload = JSONObject()
        if (!username.isNullOrBlank()) payload.put("username", username.trim())
        if (!email.isNullOrBlank()) payload.put("email", email.trim())
        if (!currentPassword.isNullOrEmpty()) payload.put("current_password", currentPassword)
        if (!newPassword.isNullOrEmpty()) payload.put("new_password", newPassword)
        val j = JSONObject(apiCall("$base/api.php?action=updateProfile", payload.toString(), token))
        if (j.optBoolean("ok") != true) throw RuntimeException(j.optString("error", "Не удалось обновить профиль"))
        (j.optBoolean("email_sent", false)) to (j.optBoolean("email_verified", false))
    }

    /* ---------- Проверка обновлений ---------- */

    /** Свежая версия, которую отдаёт сервер (api.php?action=latestApk). */
    data class LatestVersion(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val md5: String,
        val size: Long
    )

    /** Запросить у сервера информацию о самой свежей версии приложения. */
    suspend fun serverLatest(rawUrl: String): LatestVersion = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawUrl)
        if (base.isEmpty()) throw RuntimeException("Укажите адрес сервера")
        val j = JSONObject(apiCall("$base/api.php?action=latestApk", null, null))
        LatestVersion(
            versionCode = j.optInt("versionCode", 0),
            versionName = j.optString("versionName", ""),
            url = j.optString("url", ""),
            md5 = j.optString("md5", ""),
            size = j.optLong("size", 0)
        )
    }

    /* ---------- Фото на сервере (синхронизация между устройствами) ---------- */

    private fun md5File(file: File): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { String.format("%02x", it) }
    }

    /**
     * Загрузить фото вещи на сервер (multipart, с MD5 для дедупликации).
     * Возвращает абсолютный URL фото (например, https://…/photos/m-<md5>.jpg).
     * Сервер не плодит копии: тот же файл → тот же адрес.
     */
    private suspend fun uploadPhoto(rawUrl: String, token: String, file: File): String = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawUrl)
        if (base.isEmpty()) throw RuntimeException("Укажите адрес сервера")
        val md5 = md5File(file)
        val conn = URL("$base/api.php?action=upload_photo").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $token")
            val boundary = "----Kladovka" + System.currentTimeMillis()
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.outputStream.use { out ->
                fun w(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
                w("--$boundary\r\n")
                w("Content-Disposition: form-data; name=\"md5\"\r\n\r\n$md5\r\n")
                w("--$boundary\r\n")
                w("Content-Disposition: form-data; name=\"photo\"; filename=\"${file.name}\"\r\n")
                w("Content-Type: application/octet-stream\r\n\r\n")
                file.inputStream().use { it.copyTo(out) }
                w("\r\n--$boundary--\r\n")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (code !in 200..299) throw RuntimeException("Сервер не принял фото (HTTP $code)")
            val url = JSONObject(body).optString("url")
            if (url.isEmpty()) throw RuntimeException("Сервер не вернул адрес фото")
            url
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Фото уже находится на сервере (URL), а не локальный файл.
     * Внимание: локальные пути в Android тоже начинаются с «/», поэтому
     * признаком серверного адреса считаем только схему http(s):// или «//».
     */
    private fun photoIsRemote(p: String): Boolean =
        p.startsWith("http://") || p.startsWith("https://") || p.startsWith("//")

    /**
     * Экспорт для ОТПРАВКИ на сервер (в отличие от exportJson для локального бэкапа):
     * фото вещей загружаются на сервер, в данные подставляется их URL.
     * Повторная отправка того же фото не создаёт копию (дедупликация по MD5).
     */
    suspend fun exportJsonForServer(rawUrl: String, token: String): String = withContext(Dispatchers.IO) {
        val base = normalizeServerUrl(rawUrl)
        val root = JSONObject().put("app", "kladovka")
        root.put("places", JSONArray().apply {
            db.placeDao().observeAll().first().forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("notes", p.notes)
                    put("latitude", p.latitude)
                    put("longitude", p.longitude)
                })
            }
        })
        root.put("shelves", JSONArray().apply {
            db.shelfDao().observeAll().first().forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("notes", s.notes)
                    put("placeId", s.placeId)
                    put("location", s.location)
                })
            }
        })
        root.put("polki", JSONArray().apply {
            db.polkaDao().observeAll().first().forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("notes", p.notes)
                    put("shelfId", p.shelfId)
                    put("placeId", p.placeId)
                })
            }
        })
        root.put("containers", JSONArray().apply {
            db.containerDao().observeAll().first().forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("shelfId", c.shelfId)
                    put("placeId", c.placeId)
                    put("location", c.location)
                })
            }
        })
        root.put("items", JSONArray().apply {
            db.itemDao().observeAll().first().forEach { i ->
                put(JSONObject().apply {
                    put("id", i.id)
                    put("name", i.name)
                    put("quantity", i.quantity)
                    put("unit", i.unit)
                    put("category", i.category)
                    put("notes", i.notes)
                    put("containerId", i.containerId)
                    put("shelfId", i.shelfId)
                    put("placeId", i.placeId)
                    val p = i.photoPath
                    put("photoPath", when {
                        p == null || p.isEmpty() -> null
                        photoIsRemote(p) -> p
                        else -> {
                            val f = File(p)
                            if (f.exists() && f.length() > 0L) {
                                // Фото не доехало до сервера — загружаем. Сбой одного фото
                                // не должен ронять всю синхронизацию: оставляем локальный путь.
                                try { uploadPhoto(base, token, f) }
                                catch (_: Exception) { p }
                            } else p
                        }
                    })
                    put("pinned", if (i.pinned) 1 else 0)
                    put("createdAt", i.createdAt)
                    put("updatedAt", i.updatedAt)
                })
            }
        })
        root.toString(2)
    }
}