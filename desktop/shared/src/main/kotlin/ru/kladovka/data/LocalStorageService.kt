package ru.kladovka.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.Charset

/**
 * Локальное хранилище данных для офлайн-синхронизации.
 * Хранит JSON-данные в файле local_storage.json
 */
class LocalStorageService(private val storagePath: Path) {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    init {
        // Инициализация
    }
    
    /**
     * Путь к файлу локального хранилища
     */
    fun storageFilePath(): Path {
        val parent = storagePath.parent ?: Path.of("")
        return Files.createDirectories(parent.resolve(".kladovka_storage"))
            .resolve("local_storage.json")
    }
    
    /**
     * Вспомогательный data class для сериализации произвольных данных
     */
    @Serializable
    data class JsonWrapper(val data: Any)
    
    /**
     * Сохраняет данные в локальное хранилище
     */
    suspend fun save(key: String, data: Any): Boolean {
        val file = storageFilePath()
        // Используем serializer для явного указания типа
        val jsonStr = json.encodeToString(serializer<JsonWrapper>(), JsonWrapper(data))
        Files.write(file, jsonStr.toByteArray(Charset.defaultCharset()))
        return true
    }
    
    /**
     * Загружает данные из локального хранилища
     */
    suspend fun load(key: String): Any? {
        val file = storageFilePath()
        if (!Files.exists(file)) return null
        val jsonStr = Files.readString(file, Charset.defaultCharset())
        return if (jsonStr.isNotEmpty()) {
            val wrapper = json.decodeFromString<JsonWrapper>(jsonStr)
            wrapper.data
        } else null
    }
    
    /**
     * Удаляет данные из локального хранилища
     */
    suspend fun remove(key: String) {
        val file = storageFilePath()
        if (Files.exists(file)) {
            Files.delete(file)
        }
    }
    
    /**
     * Очищает все данные из локального хранилища
     */
    suspend fun clear() {
        val file = storageFilePath()
        if (Files.exists(file)) {
            Files.delete(file)
        }
    }
    
    /**
     * Возвращает список ключей, которые есть в хранилище
     */
    suspend fun listKeys(): List<String> {
        val file = storageFilePath()
        if (!Files.exists(file)) return emptyList()
        return emptyList()
    }
}
