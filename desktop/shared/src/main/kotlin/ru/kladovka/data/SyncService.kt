package ru.kladovka.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Служба для синхронизации данных с сервером
 */
class SyncService(
    private val baseUrl: String = "https://kladovka.dr6ter.ru",
    private val localStorage: LocalStorageService? = null
) {
    
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    data class SyncData(
        val items: List<Item> = emptyList(),
        val containers: List<Container> = emptyList(),
        val shelves: List<Shelf> = emptyList(),
        val polki: List<Polka> = emptyList(),
        val places: List<Place> = emptyList()
    )
    
    data class SyncResult(
        val pushed: Boolean = false,
        val pulled: Boolean = false,
        val offlineOnly: Boolean = false,
        val error: String? = null
    )
    
    /**
     * Отправляет данные на сервер с локальным кэшированием
     */
    suspend fun pushData(
        token: String,
        data: SyncData,
        storagePath: Path? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=push")
                .addHeader("Authorization", "Bearer $token")
                .post(json.encodeToString(serializer(), data).toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            if (response.code == 200) {
                val storage = storagePath?.let { LocalStorageService(it) }
                storage?.save("kladovka_places", data.places)
                storage?.save("kladovka_shelves", data.shelves)
                storage?.save("kladovka_polki", data.polki)
                storage?.save("kladovka_containers", data.containers)
                storage?.save("kladovka_items", data.items)
                SyncResult(pushed = true)
            } else {
                SyncResult(error = "Ошибка отправки: ${response.code}")
            }
        } catch (e: Exception) {
            SyncResult(error = e.message)
        }
    }
    
    /**
     * Получает данные с сервера и применяет локальное кэширование
     */
    suspend fun pullData(
        token: String,
        storagePath: Path? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=getLatestData")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            if (response.code == 200) {
                val jsonStr = response.body?.string() ?: ""
                if (jsonStr.isEmpty()) {
                    SyncResult(pulled = true)
                } else {
                    val syncData = json.decodeFromString<SyncData>(jsonStr)
                    val storage = storagePath?.let { LocalStorageService(it) }
                    storage?.save("kladovka_places", syncData.places)
                    storage?.save("kladovka_shelves", syncData.shelves)
                    storage?.save("kladovka_polki", syncData.polki)
                    storage?.save("kladovka_containers", syncData.containers)
                    storage?.save("kladovka_items", syncData.items)
                    SyncResult(pulled = true, error = null)
                }
            } else {
                SyncResult(error = "Ошибка получения: ${response.code}")
            }
        } catch (e: Exception) {
            val storage = storagePath?.let { LocalStorageService(it) }
            val offlineData = StorageData(
                items = storage?.load("kladovka_items") as? List<Item> ?: emptyList(),
                containers = storage?.load("kladovka_containers") as? List<Container> ?: emptyList(),
                shelves = storage?.load("kladovka_shelves") as? List<Shelf> ?: emptyList(),
                polki = storage?.load("kladovka_polki") as? List<Polka> ?: emptyList(),
                places = storage?.load("kladovka_places") as? List<Place> ?: emptyList()
            )
            SyncResult(
                offlineOnly = true,
                pulled = true,
                error = "Оффлайн режим: ${e.message}"
            )
        }
    }
    
    /**
     * Проверяет наличие новой версии приложения
     */
    suspend fun checkNewVersion(token: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=latestApk")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            if (response.code == 200) {
                val jsonStr = response.body?.string() ?: ""
                val data = json.decodeFromString<LatestVersion>(jsonStr)
                data.versionName
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Вспомогательный класс для хранения оффлайн данных
 */
data class StorageData(
    val items: List<Item> = emptyList(),
    val containers: List<Container> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val polki: List<Polka> = emptyList(),
    val places: List<Place> = emptyList()
)

data class LatestVersion(
    val versionName: String
)

object StorageKeys {
    const val PLACES = "kladovka_places"
    const val SHELVES = "kladovka_shelves"
    const val POLKAS = "kladovka_polki"
    const val CONTAINERS = "kladovka_containers"
    const val ITEMS = "kladovka_items"
}
