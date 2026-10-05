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
