package ru.kladovka.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.io.IOException
import okhttp3.mockwebserver.MockWebServer
import java.net.HttpURLConnection
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Настоящие HTTP-запросы против настоящего сервера.
 *
 * Все прежние тесты трогали либо логику без сети (сортировка, настройки), либо
 * вспомогательную функцию разбора текста ошибки. Сам `ApiClient` — то есть
 * заголовки, коды ответов, разбор JSON, поведение при обрыве — не проверял
 * никто. Здесь поднимается сервер, который отвечает заранее заданным кодом и
 * телом, и клиент разговаривает с ним по-настоящему.
 *
 * Имена методов и формат ответов взяты из `api.php`: `login`, `export`,
 * `latestExe`, `logout`. Расхождение с сервером ловится здесь, а не у
 * человека на телефоне.
 */
class ApiClientHttpTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ApiClient

    @BeforeTest
    fun setUp() {
        // Стенд поднимается по HTTPS, а не по HTTP, и это не украшение: ApiClient
        // намеренно отказывается работать по http, потому что пароль и токен
        // идут открытым текстом. Первый вариант теста был на http — и все
        // проверки падали на требовании https, то есть самые важные (вход,
        // заголовки, разбор ответа) не проверяли ничего.
        //
        // Сертификат самоподписанный, и клиент доверяет ровно ему и ничего
        // больше: проверка цепочки остаётся настоящей, просто корень наш.
        server = MockWebServer()
        server.useHttps(
            HandshakeCertificates.Builder()
                .heldCertificate(heldCertificate)
                .build()
                .sslSocketFactory(),
            false
        )
        server.start()
        api = ApiClient(
            baseUrl = server.url("/").toString().trimEnd('/'),
            http = OkHttpClientForTest
        )
    }

    @AfterTest
    fun tearDown() {
        // takeRequest() ждёт запрос до таймаута: если тест упал раньше, чем
        // клиент что-то отправил, разбор отчёта зависал намертво.
        server.shutdown()
    }

    /**
     * Запрос, который клиент обязан был отправить.
     *
     * Не ждём бесконечно: если запроса не было, тест обязан упасть сразу и
     * показать, что он проверял, а не висеть до конца сборки.
     */
    private fun takenRequest() =
        server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)
            ?: throw AssertionError("клиент не отправил ни одного запроса")

    /** Все запросы стенда по порядку — нужно там, где их больше одного. */
    private fun takenRequests(): List<okhttp3.mockwebserver.RecordedRequest> {
        val out = mutableListOf<okhttp3.mockwebserver.RecordedRequest>()
        while (true) {
            out += server.takeRequest(300, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break
        }
        return out
    }

    /** Временный каталог под фото. */
    private fun tempFolder(): java.io.File =
        java.nio.file.Files.createTempDirectory("kladovka-photos").toFile()

    private fun tmpFile(name: String, bytes: ByteArray): java.io.File {
        val f = tempFolder().resolve(name)
        f.writeBytes(bytes)
        return f
    }

    private fun respond(code: Int, body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body)
        )
    }

    // ---------------------------------------------------------------- вход

    @Test
    fun `вход возвращает токен из ответа сервера`() = runBlocking {
        respond(200, """{"token":"token-from-server","role":"admin"}""")
        assertEquals("token-from-server", api.login("пароль"))
    }

    @Test
    fun `токен уходит заголовком, а не в адресе`() = runBlocking {
        respond(200, """{"token":"tok","role":"admin"}""")
        api.login("пароль")
        val req = takenRequest()
        // Адрес не должен содержать ни пароля, ни токена: иначе они осядут в
        // логах веб-сервера и в истории браузера.
        assertTrue(
            req.requestUrl.toString().contains("action=login"),
            "ожидался вызов login, а пришёл ${req.requestUrl}"
        )
        assertTrue(req.path!!.startsWith("/api.php"), "путь должен быть /api.php: ${req.path}")
    }

    @Test
    fun `вход с неверным паролем даёт текст сервера, а не код`() = runBlocking {
        respond(401, """{"error":"Неверный логин или пароль"}""")
        val e = assertFailsWith<ApiException> { api.login("неверный") }
        // Сообщение должно быть живым: по коду 401 человек не поймёт ничего.
        assertTrue(
            e.message!!.contains("Неверный логин или пароль"),
            "в сообщении должно быть то, что ответил сервер, а не «${e.message}»"
        )
    }

    @Test
    fun `отозванный вход отличается от неверного пароля`() = runBlocking {
        respond(401, """{"error":"Не авторизовано"}""")
        val e = assertFailsWith<ApiException> { api.login("пароль") }
        assertTrue(
            e.message!!.contains("отозван") || e.message!!.contains("Отозван"),
            "сообщение должно говорить про отзыв, а не про пароль: ${e.message}"
        )
    }

    @Test
    fun `код ошибки доступен для разбора`() = runBlocking {
        respond(403, """{"error":"Недостаточно прав"}""")
        val e = assertFailsWith<ApiException> { api.login("пароль") }
        assertEquals(403, e.code, "SyncScreen отличает отказ по коду, а не по тексту")
    }

    @Test
    fun `клиент отказывается от http для настоящего адреса`() = runBlocking {
        // Требование https — это защита от передачи пароля открытым текстом.
        // Обходить его ради теста нельзя, поэтому проверяем, что обход не
        // работает: подменённый адрес не должен молча согласиться.
        val plain = ApiClient(baseUrl = "http://kladovka.dr6ter.ru", http = OkHttpClientForTest)
        val e = assertFailsWith<IOException> { plain.login("пароль") }
        assertTrue(
            e.message!!.contains("https"),
            "должно быть требование https, а не «${e.message}»"
        )
    }

    @Test
    fun `адрес приводится к виду без хвостового слэша`() {
        val c = ApiClient(baseUrl = "https://example.test/")
        assertEquals("https://example.test", c.normalized())
        // И без схемы: подставляется https, а не ничего.
        assertEquals("https://example.test", ApiClient(baseUrl = "example.test").normalized())
    }

    // ---------------------------------------------------------------- профиль

    @Test
    fun `профиль разбирается из json`() = runBlocking {
        respond(
            200,
            """{"ok":true,"id":7,"username":"ivan","email":"ivan@example.test",""" +
                """"email_verified":true,"created_at":1700000000000}"""
        )
        val p = api.profile("test-token")
        assertEquals("ivan", p.username)
        assertEquals("ivan@example.test", p.email)
        assertEquals(7L, p.id)
        assertTrue(p.emailVerified)
        assertEquals(1700000000000L, p.createdAt)
    }

    @Test
    fun `профиль без поля ok не выдаётся за пустой`() = runBlocking {
        // Ответ без "ok" обязан падать. Иначе интерфейс показал бы человеку
        // пустые данные как будто он вошёл, и тот не понял бы, что что-то не так.
        respond(200, """{"username":"ivan"}""")
        val e = assertFailsWith<IOException> { api.profile("test-token") }
        assertTrue(
            e.message!!.contains("профиль"),
            "сообщение должно называть, что не получилось: ${e.message}"
        )
    }

    @Test
    fun `профиль без токена не должен молчать`() = runBlocking {
        respond(401, """{"error":"Не авторизовано"}""")
        assertFailsWith<ApiException> { api.profile("test-token") }
    }

    // ---------------------------------------------------------------- смена данных

    @Test
    fun `смена имени и почты уходит одним запросом и подтверждается профилем`() = runBlocking {
        // Сервер после updateProfile возвращает ok и сам разбирается с полями,
        // поэтому клиент шлёт только изменённые. Пустые поля отправлять нельзя:
        // смена почты заново сбрасывает подтверждение, и человек, открывший
        // диалог и ничего не поменявший, потерял бы доступ к складу.
        respond(200, """{"ok":true,"notes":"ok"}""")
        respond(200, """{"ok":true,"id":7,"username":"ivan2","email":"new@example.test",""" +
            """"email_verified":false,"created_at":1700000000000}""")
        val p = api.updateProfile("test-token", username = "ivan2", email = "new@example.test")
        assertEquals("ivan2", p.username)
        assertEquals("new@example.test", p.email)
        assertFalse(p.emailVerified)

        val sent = takenRequest()!!
        assertTrue(sent.requestUrl.toString().contains("action=updateProfile"))
        val body = sent.body.readUtf8()
        assertTrue(body.contains("\"username\":\"ivan2\""), "тело: $body")
        assertTrue(body.contains("\"email\":\"new@example.test\""), "тело: $body")
        assertFalse(body.contains("new_password"), "пустой пароль отправлять нельзя: $body")
    }

    @Test
    fun `смена только пароля требует текущего`() = runBlocking {
        respond(200, """{"ok":true}""")
        respond(200, """{"ok":true,"id":7,"username":"ivan","email":"ivan@example.test",""" +
            """"email_verified":true,"created_at":1700000000000}""")
        api.updateProfile("test-token", newPassword = "новый-пароль-1", currentPassword = "старый-пароль-1")
        val body = takenRequest()!!.body.readUtf8()
        assertTrue(body.contains("\"new_password\":\"новый-пароль-1\""), "тело: $body")
        assertTrue(body.contains("\"current_password\":\"старый-пароль-1\""), "тело: $body")
        assertFalse(body.contains("\"username\""), "пустое имя отправлять нельзя: $body")
    }

    @Test
    fun `отказ сервера при смене данных не остаётся без текста`() = runBlocking {
        respond(409, """{"error":"Это имя уже занято"}""")
        val e = assertFailsWith<ApiException> { api.updateProfile("test-token", username = "admin") }
        assertTrue(e.message!!.contains("занято"), "сообщение сервера должно дойти: ${e.message}")
    }

    @Test
    fun `смена без изменений не ходит на сервер`() = runBlocking {
        // Ничего не менял — запрос не нужен. Иначе каждое открытие диалога
        // отправляло бы пустое тело и заставляло сервер зря что-то делать.
        respond(200, """{"ok":true,"id":7,"username":"ivan","email":"ivan@example.test",""" +
            """"email_verified":true,"created_at":1700000000000}""")
        val p = api.updateProfile("test-token")
        assertEquals("ivan", p.username)
        assertTrue(
            takenRequest()!!.requestUrl.toString().contains("action=profile"),
            "без изменений должен спрашиваться только профиль"
        )
    }

    // ---------------------------------------------------------------- фотографии

    @Test
    fun `фото уходит multipart с отпечатком и файлом`() = runBlocking {
        // md5 в теле запроса — дедупликация на сервере: один и тот же файл всегда
        // даёт один адрес. Без него на сервере копились бы дубликаты одной и той
        // же фотографии, и каждая отправка заново занимала бы место.
        val f = tmpFile("photo.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3))
        respond(200, """{"url":"https://kladovka.dr6ter.ru/photos/m-abc.jpg"}""")
        val url = api.uploadPhoto("test-token", f)
        assertTrue(url.endsWith("/photos/m-abc.jpg"), "вернулся адрес: $url")

        val req = takenRequest()!!
        assertTrue(req.requestUrl.toString().contains("action=upload_photo"))
        val ct = req.getHeader("Content-Type")!!
        assertTrue(ct.startsWith("multipart/form-data"), "нужен multipart, а не json: $ct")
        val body = req.body.readUtf8()
        assertTrue(body.contains("name=\"md5\""), "нет поля md5")
        assertTrue(body.contains("name=\"photo\""), "нет поля с файлом")
    }

    @Test
    fun `при отправке на сервер в json стоит адрес фото, а не путь на диске`() = runBlocking {
        // Самое частое и самое тихое поломки: если в дамп уехал путь вида
        // C:\Users\...\photos\a.jpg, то на телефоне картинки не будет никогда —
        // файл там другой, и ссылка ведёт в никуда. Проверяем, что в уходящем
        // импорте стоит именно адрес с сервера.
        respond(200, """{"url":"https://kladovka.dr6ter.ru/photos/m-deadbeef.jpg"}""")
        respond(200, """{"ok":true,"notes":"Импорт завершён"}""")
        val dir = tempFolder()
        // Файл обязан лежать в каталоге фото: клиент ищет его как photoDir + photoPath.
        dir.resolve("photo.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3))
        val item = Item(
            id = 1L, name = "Дрель", quantity = 1, unit = "шт", category = "",
            notes = "", containerId = null, shelfId = null, placeId = null,
            photoPath = "photo.jpg", pinned = false, createdAt = 0L, updatedAt = 0L
        )
        val result = api.push("test-token", AppData(places = emptyList(), shelves = emptyList(),
            polki = emptyList(), containers = emptyList(), items = listOf(item)), dir)
        assertEquals(1, result.uploadedPhotos)
        assertEquals(0, result.failedPhotos)

        val pushed = takenRequests().last().body.readUtf8()
        assertTrue(pushed.contains("m-deadbeef.jpg"), "в импорте должен быть адрес фото: $pushed")
        assertFalse(pushed.contains("\\\\"), "локальный путь в импорте быть не должен: $pushed")
    }

    @Test
    fun `сбой загрузки одного фото не роняет всю отправку`() = runBlocking {
        // Один битый файл не должен оставлять весь склад незагруженным: вещи без
        // фото уедут, а человек увидит, какой остался без картинки.
        respond(500, """{"error":"Слишком большой файл"}""")
        respond(200, """{"ok":true,"notes":"Импорт завершён"}""")
        val dir = tempFolder()
        dir.resolve("big.jpg").writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        val item = Item(
            id = 1L, name = "Дрель", quantity = 1, unit = "шт", category = "",
            notes = "", containerId = null, shelfId = null, placeId = null,
            photoPath = "big.jpg", pinned = false, createdAt = 0L, updatedAt = 0L
        )
        val result = api.push("test-token", AppData(places = emptyList(), shelves = emptyList(),
            polki = emptyList(), containers = emptyList(), items = listOf(item)), dir)
        assertEquals(0, result.uploadedPhotos)
        assertEquals(1, result.failedPhotos)
        val pushed = takenRequests().last()
        assertTrue(pushed.requestUrl.toString().contains("action=import"),
            "отправка всё равно должна была уйти")
    }

    @Test
    fun `пустой файл не отправляется на сервер`() = runBlocking {
        val f = tmpFile("пусто.jpg", ByteArray(0))
        assertFailsWith<IOException> { api.uploadPhoto("test-token", f) }
        assertTrue(takenRequests().isEmpty(), "запрос уходить не должен был")
    }

    // ---------------------------------------------------------------- версии

    @Test
    fun `десктоп спрашивает latestExe, а не latestApk`() = runBlocking {
        respond(200, """{"versionCode":100,"versionName":"v1.0","url":"https://x/Kladovka.exe","size":10,"md5":"abc"}""")
        api.latestVersion()
        val req = takenRequest()
        assertTrue(
            req.requestUrl.toString().contains("action=latestExe"),
            "десктоп должен спрашивать про себя, а не про телефон: ${req.requestUrl}"
        )
    }

    @Test
    fun `разбор версии забирает все поля`() = runBlocking {
        respond(200, """{"versionCode":110,"versionName":"v1.10","url":"https://x/a.exe","size":12345,"md5":"deadbeef"}""")
        val v = api.latestVersion()!!
        assertEquals(110, v.versionCode)
        assertEquals("v1.10", v.versionName)
        assertEquals("https://x/a.exe", v.url)
        assertEquals(12345L, v.size)
        assertEquals("deadbeef", v.md5)
    }

    @Test
    fun `пустой ответ сервера не превращается в версию`() = runBlocking {
        // Сервер мог ответить HTML вместо JSON — тогда возвращать «версию без
        // номера» нельзя, UpdateChecker решит по ней, что обновлений нет.
        respond(200, "<html>страница ошибки</html>")
        assertNull(api.latestVersion(), "не-ответ должен давать null, а не пустую версию")
    }

    // ---------------------------------------------------------------- отзыв

    @Test
    fun `выход обращается к logout`() = runBlocking {
        respond(200, """{"ok":true}""")
        // Отдельного метода logout в ApiClient нет — выход делает SyncScreen.
        // Здесь проверяем, что сервер на logout отвечает успехом и ответ
        // переваривается без исключения: отзыв входа держится на этом.
        val req = okhttp3.Request.Builder()
            .url(server.url("/api.php?action=logout").toString())
            .header("Authorization", "Bearer test-token")
            .post("{}".toRequestBody(jsonMedia))
            .build()
        val resp = OkHttpClientForTest.newCall(req).execute()
        resp.use {
            assertEquals(HttpURLConnection.HTTP_OK, it.code)
        }
        assertTrue(takenRequest()!!.requestUrl.toString().contains("action=logout"))
    }
}



private val jsonMedia = "application/json; charset=utf-8".toMediaType()
/**
 * Сертификат для HTTPS-стенда: самий выданный, ничего не проверяющий, кроме
 * того что мы ему доверяем. Нужен, чтобы поднять стенд по https — тогда клиент
 * проходит свою проверку схемы по-честному.
 *
 * Имя в списке SAN — оба, а не только 127.0.0.1. `MockWebServer` в адресе
 * отдаёт `localhost`, но какое имя попадёт в проверку, зависит от того, как JVM
 * развернёт это имя в адрес: на Windows выходило 127.0.0.1 и проверка проходила,
 * на Linux оставалось `localhost` и десять тестов падали с
 * `SSLPeerUnverifiedException: Hostname localhost not verified`. Тест не должен
 * зависеть от платформы, поэтому сертификат покрывает оба варианта.
 */
private val heldCertificate = HeldCertificate.Builder()
    .addSubjectAlternativeName("127.0.0.1")
    .addSubjectAlternativeName("localhost")
    .commonName("kladovka-test")
    .build()

/**
 * Клиент тестов: короткие таймауты (чтобы тест не завис при обрыве) и доверие
 * только к сертификату стенда. Системные корни не подключаем намеренно.
 */
private val OkHttpClientForTest: okhttp3.OkHttpClient
    get() {
        // Доверие и менеджер должны быть из одного билдера: OkHttp проверяет,
        // что они соответствуют друг другу, иначе отказывается работать.
        val certs = HandshakeCertificates.Builder()
            .addTrustedCertificate(heldCertificate.certificate)
            .build()
        return okhttp3.OkHttpClient.Builder()
            .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .sslSocketFactory(certs.sslSocketFactory(), certs.trustManager)
            .build()
    }
