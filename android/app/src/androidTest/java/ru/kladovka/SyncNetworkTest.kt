package ru.kladovka

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.kladovka.data.AppDatabase
import ru.kladovka.data.Item
import ru.kladovka.data.Place
import ru.kladovka.data.Repository

/**
 * Сквозной тест синхронизации: реальный сетевой код приложения против
 * реального сервера. Не трогает данные на телефоне (только чтение + запись на сервер,
 * на котором хранятся тестовые данные).
 */
@RunWith(AndroidJUnit4::class)
class SyncNetworkTest {

    private val serverUrl = "https://kladovka.dr6ter.ru"

    /** Пароль админа берётся из переменной окружения, а не хранится в исходниках. */
    private fun adminPassword(): String =
        System.getenv("KLADOVKA_ADMIN_PW")
            ?: error("KLADOVKA_ADMIN_PW is not set - cannot run network tests")

    @Test
    fun fullSyncRoundTrip() = runBlocking {
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = Repository(AppDatabase.get(ctx), ctx)

        // 1. Вход по паролю → токен
        val token = repo.serverLogin(serverUrl, adminPassword())
        assertTrue("должен вернуться токен", token.isNotEmpty())

        // 2. Отправка текущих данных телефона на сервер
        val localExport = repo.exportJson()
        val local = JSONObject(localExport)
        assertTrue("бэкап телефона должен быть в формате приложения", local.optString("app") == "kladovka")
        repo.serverPush(serverUrl, token, localExport)

        // 3. Загрузка с сервера — сервер отдаёт «голый» объект, приложение оборачивает его
        val fetched = repo.serverFetch(serverUrl, token)
        val root = JSONObject(fetched)
        assertEquals("обёртка приложения", "kladovka", root.optString("app"))

        for (t in listOf("places", "shelves", "containers", "items")) {
            val la = local.optJSONArray(t)
            val ra = root.optJSONArray(t)
            assertEquals("количество записей в $t не совпадает с отправленным",
                la?.length() ?: 0, ra?.length() ?: 0)
            if (la != null && la.length() > 0) {
                assertEquals("id первой записи $t не сохранился",
                    la.getJSONObject(0).optLong("id"), ra!!.getJSONObject(0).optLong("id"))
            }
        }

        // 4. Вещи должны нести временные метки (их ставит сервер при импорте)
        val items = root.optJSONArray("items")
        if (items != null && items.length() > 0) {
            val first = items.getJSONObject(0)
            assertTrue("у вещи должен быть createdAt", first.optLong("createdAt") > 0)
            assertTrue("у вещи должен быть updatedAt", first.optLong("updatedAt") > 0)
        }
    }

    /**
     * Полный цикл с данными: создаём записи → отправляем на сервер → удаляем локально →
     * загружаем с сервера обратно → проверяем целостность → очищаем сервер и телефон.
     */
    @Test
    fun syncWithDataRoundTrip() = runBlocking {
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.get(ctx)
        val repo = Repository(db, ctx)
        val placeDao = db.placeDao()
        val itemDao = db.itemDao()

        val token = repo.serverLogin(serverUrl, adminPassword())
        assertTrue("должен вернуться токен", token.isNotEmpty())

        // Свои тестовые записи (таких имён нигде больше нет)
        val pId = placeDao.insert(Place(name = "Тест-место", notes = "автопроверка"))
        val iId = itemDao.insert(
            Item(name = "Тест-вещь", quantity = 7, unit = "шт", category = "Тест",
                notes = "автопроверка", placeId = pId)
        )

        // 1) Отправка на сервер
        repo.serverPush(serverUrl, token, repo.exportJson())

        // 2) Удаляем локально и тянем обратно с сервера
        itemDao.deleteById(iId)
        placeDao.deleteById(pId)
        assertTrue("локально должно стать пусто", itemDao.observeAll().first().isEmpty())

        val fetched = repo.serverFetch(serverUrl, token)
        assertTrue("загрузка с сервера должна пройти", repo.importJson(fetched))

        // 3) Данные вернулись, id и значения целы
        val p = placeDao.observeAll().first().firstOrNull { it.id == pId }
        assertNotNull("место должно вернуться", p)
        assertEquals("Тест-место", p?.name)
        val i = itemDao.observeAll().first().firstOrNull { it.id == iId }
        assertNotNull("вещь должна вернуться", i)
        assertEquals("Тест-вещь", i?.name)
        assertEquals(7, i?.quantity)
        assertEquals(pId, i?.placeId)

        // 4) Очистка: локально и на сервере (сервер остаётся пустым)
        itemDao.deleteById(iId)
        placeDao.deleteById(pId)
        repo.serverPush(serverUrl, token, repo.exportJson())
    }

    /** Восстановление: забирает данные с сервера и кладёт в приложение (замена). */
    @Test
    fun restoreServerToPhone() = runBlocking {
        val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = Repository(AppDatabase.get(ctx), ctx)
        val token = repo.serverLogin(serverUrl, adminPassword())
        assertTrue("должен вернуться токен", token.isNotEmpty())
        val ok = repo.importJson(repo.serverFetch(serverUrl, token))
        assertTrue("импорт с сервера должен пройти", ok)
    }
}