package ru.kladovka.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.kladovka.BuildConfig
import ru.kladovka.data.AppDatabase
import ru.kladovka.data.Item
import ru.kladovka.data.Repository
import ru.kladovka.data.Repository.LatestVersion

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(AppDatabase.get(app), app)

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Режим темы: «как в системе» / светлая / тёмная. Сохраняется между запусками. */
    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    /** Полный бэкап всех данных в выбранный файл (SAF). */
    fun exportTo(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val json = repo.exportJson()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val os = getApplication<Application>().contentResolver.openOutputStream(uri) ?: return@withContext false
                    os.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    true
                }.getOrDefault(false)
            }
            onResult(ok)
        }
    }

    /** Восстановление данных из файла бэкапа. Возвращает true при успехе. */
    fun importFrom(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) {
                runCatching {
                    val ins = getApplication<Application>().contentResolver.openInputStream(uri) ?: return@withContext null
                    ins.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }.getOrNull()
            } ?: return@launch onResult(false)
            val ok = repo.importJson(json)
            onResult(ok)
        }
    }

    /** Экспорт всех вещей в CSV (удобно открывать в Excel/таблицах). */
    fun exportCsvTo(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val csv = repo.exportCsv()
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val os = getApplication<Application>().contentResolver.openOutputStream(uri) ?: return@withContext false
                    os.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                    true
                }.getOrDefault(false)
            }
            onResult(ok)
        }
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Активная вкладка главного экрана — живёт в ViewModel, чтобы не сбрасываться при навигации. */
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    /** Была ли уже показана анимация появления списков (чтобы не проигрывать её при каждом возврате). */
    private val _listAnimated = MutableStateFlow(false)
    val listAnimated: StateFlow<Boolean> = _listAnimated.asStateFlow()

    fun markListAnimated() {
        _listAnimated.value = true
    }

    /** Полный набор данных (вещи + контейнеры + стеллажи + полки + места). */
    val data: StateFlow<AppData> =
        combine(repo.items, repo.containers, repo.shelves, repo.polki, repo.places) { items, containers, shelves, polki, places ->
            AppData(items, containers, shelves, polki, places)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppData())

    /** Результаты поиска — полный список или отфильтрованный. */
    val searchResults: StateFlow<List<Item>> =
        _query.flatMapLatest { q -> repo.searchItems(q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) {
        _query.value = q
    }

    /* ---------- Места ---------- */

    fun savePlace(id: Long, name: String, notes: String, latitude: Double?, longitude: Double?) {
        viewModelScope.launch { repo.upsertPlace(id, name, notes, latitude, longitude); scheduleAutoSync() }
    }

    fun deletePlace(id: Long) {
        viewModelScope.launch { repo.deletePlace(id); scheduleAutoSync() }
    }

    /* ---------- Стеллажи ---------- */

    fun saveShelf(id: Long, name: String, notes: String, placeId: Long?) {
        viewModelScope.launch { repo.upsertShelf(id, name, notes, placeId); scheduleAutoSync() }
    }

    fun deleteShelf(id: Long) {
        viewModelScope.launch { repo.deleteShelf(id); scheduleAutoSync() }
    }

    /* ---------- Полки (polki) ---------- */

    fun savePolka(id: Long, name: String, notes: String, shelfId: Long?, placeId: Long?) {
        viewModelScope.launch { repo.upsertPolka(id, name, notes, shelfId, placeId); scheduleAutoSync() }
    }

    fun deletePolka(id: Long) {
        viewModelScope.launch { repo.deletePolka(id); scheduleAutoSync() }
    }

    /* ---------- Контейнеры ---------- */

    fun saveContainer(id: Long, name: String, shelfId: Long?, placeId: Long?) {
        viewModelScope.launch { repo.upsertContainer(id, name, shelfId, placeId); scheduleAutoSync() }
    }

    fun deleteContainer(id: Long) {
        viewModelScope.launch { repo.deleteContainer(id); scheduleAutoSync() }
    }

    /* ---------- Вещи ---------- */

    fun saveItem(
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
        viewModelScope.launch {
            repo.upsertItem(id, name, quantity, unit, category, notes, containerId, shelfId, placeId, photoPath)
            scheduleAutoSync()
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { repo.deleteItem(id); scheduleAutoSync() }
    }

    fun changeItemQuantity(id: Long, delta: Int) {
        viewModelScope.launch { repo.changeItemQuantity(id, delta); scheduleAutoSync() }
    }

    /** Закрепить/открепить вещь (⭐). */
    fun setItemPinned(id: Long, pinned: Boolean) {
        viewModelScope.launch { repo.setItemPinned(id, pinned); scheduleAutoSync() }
    }

    fun persistPhoto(uri: Uri, onResult: (String?) -> Unit) {
        viewModelScope.launch { onResult(repo.savePhoto(uri)) }
    }

    fun loadCategories(onResult: (List<String>) -> Unit) {
        viewModelScope.launch { onResult(repo.categories()) }
    }

    /* ---------- Синхронизация с сервером ---------- */

    private val _syncBusy = MutableStateFlow(false)
    val syncBusy: StateFlow<Boolean> = _syncBusy.asStateFlow()

    /** Токен получен и ещё не сброшен — кнопки «Отправить/Загрузить» активны. */
    private val _synced = MutableStateFlow(false)
    val synced: StateFlow<Boolean> = _synced.asStateFlow()

    /** Последний результат операции: (это ошибка?, текст). null = ещё ничего не было. */
    private val _syncMessage = MutableStateFlow<Pair<Boolean, String>?>(null)
    val syncMessage: StateFlow<Pair<Boolean, String>?> = _syncMessage.asStateFlow()

    /** Что сейчас делает синхронизация («Вход…», «Отправка…»). null = не активна. */
    private val _syncBusyText = MutableStateFlow<String?>(null)
    val syncBusyText: StateFlow<String?> = _syncBusyText.asStateFlow()

    /** Автоматическая отправка изменений на сервер (вкл./выкл.). */
    private val _autoSync = MutableStateFlow(prefs.getBoolean("autoSync", true))
    val autoSync: StateFlow<Boolean> = _autoSync.asStateFlow()

    /** Время последней успешной отправки на сервер (мс). 0 = ещё не отправляли. */
    private val _lastSyncAt = MutableStateFlow(prefs.getLong("lastSyncAt", 0L))
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    private var syncToken: String? = null
    private var autoSyncJob: kotlinx.coroutines.Job? = null

    fun syncServerUrl(): String =
        prefs.getString("serverUrl", "https://kladovka.dr6ter.ru") ?: "https://kladovka.dr6ter.ru"

    fun syncServerUsername(): String = prefs.getString("serverUsername", "") ?: ""

    fun syncServerPassword(): String = prefs.getString("serverPassword", "") ?: ""

    fun saveSyncSettings(url: String, username: String, password: String) {
        prefs.edit()
            .putString("serverUrl", url.trim().ifEmpty { "https://kladovka.dr6ter.ru" })
            .putString("serverUsername", username.trim())
            .putString("serverPassword", password)
            .apply()
        syncToken = null
        _synced.value = false
    }

    fun setAutoSync(on: Boolean) {
        _autoSync.value = on
        prefs.edit().putBoolean("autoSync", on).apply()
        if (!on) {
            autoSyncJob?.cancel()
            autoSyncJob = null
        }
    }

    /** Вход на сервер по сохранённым адресу, имени пользователя и паролю. */
    fun syncLogin(silent: Boolean = false, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _syncBusy.value = true
            _syncBusyText.value = "Соединяемся с сервером…"
            if (!silent) _syncMessage.value = null
            val result = runCatching {
                repo.serverLogin(syncServerUrl(), syncServerUsername(), syncServerPassword())
            }
            _syncBusy.value = false
            _syncBusyText.value = null
            result.onSuccess { token ->
                syncToken = token
                _synced.value = true
                if (!silent) _syncMessage.value = false to "Вход выполнен — данные телефона и сервера связаны"
                refreshProfile()  // подтянуть данные аккаунта для личного кабинета
                onResult(true)
            }.onFailure { e ->
                syncToken = null
                _synced.value = false
                if (!silent) _syncMessage.value = true to (e.message ?: "Ошибка входа")
                onResult(false)
            }
        }
    }

    /** Регистрация нового аккаунта на сервере. */
    fun syncRegister(username: String, password: String, email: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _syncBusy.value = true
            _syncBusyText.value = "Регистрация аккаунта…"
            _syncMessage.value = null
            val result = runCatching {
                repo.serverRegister(syncServerUrl(), username, password, email)
            }
            _syncBusy.value = false
            _syncBusyText.value = null
            result.onSuccess { token ->
                if (token != null) {
                    syncToken = token
                    _synced.value = true
                    _syncMessage.value = false to "Аккаунт создан и подтверждён — данные телефона и сервера связаны"
                    refreshProfile()
                    onResult(true)
                } else {
                    _synced.value = false
                    _syncMessage.value = false to "Аккаунт создан! Перейдите по ссылке из письма на почте, затем нажмите «Подключиться к серверу» для входа."
                    onResult(false)
                }
            }.onFailure { e ->
                syncToken = null
                _synced.value = false
                _syncMessage.value = true to (e.message ?: "Ошибка регистрации")
                onResult(false)
            }
        }
    }

    /* ---------- Личный кабинет ---------- */

    private val _profile = MutableStateFlow<Repository.UserProfile?>(null)
    val profile: StateFlow<Repository.UserProfile?> = _profile.asStateFlow()

    private var profileJob: kotlinx.coroutines.Job? = null

    /** Прочитать данные аккаунта (имя, почта, подтверждение). */
    fun refreshProfile() {
        val token = syncToken ?: return
        profileJob?.cancel()
        profileJob = viewModelScope.launch {
            runCatching { repo.serverProfile(syncServerUrl(), token) }
                .onSuccess { _profile.value = it }
                .onFailure { /* тихо: профиль недоступен, напр. вход администратором */ }
        }
    }

    /** Изменить данные аккаунта. Возвращает (ok, человеческий текст). */
    fun updateProfile(
        username: String?,
        email: String?,
        currentPassword: String?,
        newPassword: String?,
        onResult: (Boolean, String) -> Unit
    ) {
        val token = syncToken
        if (token == null) {
            syncLogin(onResult = { ok ->
                if (ok) updateProfile(username, email, currentPassword, newPassword, onResult)
                else onResult(false, "Сначала войдите на сервер")
            })
            return
        }
        viewModelScope.launch {
            _syncBusy.value = true
            val result = runCatching {
                repo.serverUpdateProfile(syncServerUrl(), token, username, email, currentPassword, newPassword)
            }
            _syncBusy.value = false
            result.onSuccess { (emailSent, verifiedAfter) ->
                // Пароль мог смениться — сохраняем новый, чтобы автовход продолжал работать
                if (!newPassword.isNullOrEmpty()) {
                    prefs.edit().putString("serverPassword", newPassword).apply()
                }
                if (!username.isNullOrBlank()) {
                    prefs.edit().putString("serverUsername", username.trim()).apply()
                }
                refreshProfile()
                onResult(
                    true,
                    when {
                        emailSent && !verifiedAfter -> "Данные сохранены. На новую почту отправлено письмо с подтверждением — перейдите по ссылке из него."
                        emailSent -> "Данные сохранены. Письмо с подтверждением уже в пути."
                        else -> "Данные обновлены."
                    }
                )
            }.onFailure { e ->
                onResult(false, e.message ?: "Не удалось обновить профиль")
            }
        }
    }

    /** Отправка данных телефона на сервер (замена данных на сервере). */
    fun syncPush(onResult: (Boolean) -> Unit = {}) {
        val token = syncToken
        if (token == null) {
            syncLogin { ok -> if (ok) syncPush(onResult) }
            return
        }
        viewModelScope.launch {
            onResult(pushInternal(token, silent = false))
        }
    }

    /* ---------- Совместный учёт (шаринг) ---------- */

    /** Открыть доступ к своему складу другому пользователю. */
    fun shareWith(username: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val token = syncToken
        if (token == null) {
            syncLogin { ok -> if (ok) shareWith(username, onResult) else onResult(false, "Сначала войдите на сервер") }
            return
        }
        viewModelScope.launch {
            _syncBusy.value = true
            val result = runCatching { repo.serverShare(syncServerUrl(), token, username) }
            _syncBusy.value = false
            result.onSuccess { onResult(true, "Доступ открыт для $it") }.onFailure { e ->
                onResult(false, e.message ?: "Ошибка")
            }
        }
    }

    /** Отозвать доступ у пользователя. */
    fun unshareWith(username: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val token = syncToken
        if (token == null) {
            syncLogin { ok -> if (ok) unshareWith(username, onResult) else onResult(false, "Сначала войдите на сервер") }
            return
        }
        viewModelScope.launch {
            _syncBusy.value = true
            val result = runCatching { repo.serverUnshare(syncServerUrl(), token, username) }
            _syncBusy.value = false
            result.onSuccess { onResult(true, "Доступ отозван для $username") }.onFailure { e ->
                onResult(false, e.message ?: "Ошибка")
            }
        }
    }

    /** Кому я дал доступ и кто дал мне. */
    fun syncShares(onResult: (List<String>, List<String>) -> Unit) {
        val token = syncToken
        if (token == null) {
            onResult(emptyList(), emptyList())
            return
        }
        viewModelScope.launch {
            val (given, received) = runCatching { repo.serverShares(syncServerUrl(), token) }.getOrDefault(emptyList<String>() to emptyList())
            onResult(given, received)
        }
    }

    /**
     * Сама отправка. silent=true — для автосинхронизации (не трогает сообщения диалога).
     * Пустой дамп (0 записей) автоматически НЕ отправляется — это защищает сервер
     * от случайного затирания после переустановки/пустой базы; пользователь может
     * намеренно отправить пустоту только ручным push через диалог.
     */
    private suspend fun pushInternal(token: String, silent: Boolean): Boolean {
        if (silent) {
            // Автосинк: если на телефоне пусто, пропускаем отправку целиком.
            val dump = withContext(Dispatchers.IO) { repo.exportJson() }
            val emptyDump = runCatching { JSONObject(dump) }
                .getOrElse { null }
                ?.let { o ->
                    listOf("places","shelves","polki","containers","items")
                        .all { t -> o.optJSONArray(t)?.length() == 0 || o.isNull(t) }
                } ?: false
            if (emptyDump) {
                _syncBusy.value = false
                _syncBusyText.value = null
                _syncMessage.value = false to "Телефон пуст (0 записей) — данные на сервере не тронуты. Ручная отправка доступна в диалоге."
                return false
            }
        }
        _syncBusy.value = true
        _syncBusyText.value = "Отправляем данные на сервер…"
        if (!silent) _syncMessage.value = null
        val result = runCatching {
            // Экспорт «для сервера»: фото вещей загружаются и передаются ссылкой,
            // чтобы они доходили до других устройств (локальный бэкап не затрагивается)
            val json = repo.exportJsonForServer(syncServerUrl(), token)
            repo.serverPush(syncServerUrl(), token, json)
        }
        _syncBusy.value = false
        _syncBusyText.value = null
        result.onSuccess {
            if (!silent) _syncMessage.value = false to "Данные отправлены на сервер"
            _lastSyncAt.value = System.currentTimeMillis()
            prefs.edit().putLong("lastSyncAt", _lastSyncAt.value).apply()
        }.onFailure { e ->
            if (!silent) _syncMessage.value = true to (e.message ?: "Ошибка отправки")
        }
        return result.isSuccess
    }

    /** Загрузка данных с сервера (замена данных на телефоне). */
    fun syncPull(onResult: (Boolean) -> Unit = {}) {
        val token = syncToken
        if (token == null) {
            syncLogin { ok -> if (ok) syncPull(onResult) }
            return
        }
        viewModelScope.launch {
            _syncBusy.value = true
            _syncBusyText.value = "Загружаем данные с сервера…"
            _syncMessage.value = null
            val result = runCatching {
                val json = repo.serverFetch(syncServerUrl(), token)
                repo.importJson(json)
            }
            _syncBusy.value = false
            _syncBusyText.value = null
            result.onSuccess { ok ->
                if (ok) {
                    _syncMessage.value = false to "Данные загружены с сервера"
                    onResult(true)
                } else {
                    _syncMessage.value = true to "Сервер вернул непонятные данные"
                    onResult(false)
                }
            }.onFailure { e ->
                _syncMessage.value = true to (e.message ?: "Ошибка загрузки")
                onResult(false)
            }
        }
    }

    /* ---------- Автосинхронизация ---------- */

    /**
     * Пометить, что данные изменились: через 15 секунд после последнего изменения
     * изменения автоматически улетят на сервер (если автосинхронизация включена).
     */
    private fun scheduleAutoSync() {
        if (!_autoSync.value) return
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            delay(15_000)
            if (syncToken == null) syncLogin() // войдём по сохранённому паролю
            val token = syncToken ?: return@launch
            pushInternal(token, silent = true)
        }
    }

    /** Вызывается при сворачивании приложения: немедленно отправляем изменения. */
    fun onAppBackgrounded() {
        if (!_autoSync.value) return
        autoSyncJob?.cancel()
        autoSyncJob = null
        val token = syncToken
        if (token == null) {
            // ещё не входили — войдём по сохранённому паролю и отправим
            syncLogin { ok -> if (ok) syncPush() }
            return
        }
        viewModelScope.launch { pushInternal(token, silent = true) }
    }

    /* ---------- Проверка обновлений ---------- */

    /** Текст статуса проверки обновления («Доступна версия v1.30» / «Установлена свежая версия» / ошибка). */
    private val _updateInfo = MutableStateFlow<String?>(null)
    val updateInfo: StateFlow<String?> = _updateInfo.asStateFlow()

    /** Ссылка на свежий APK; null = обновление недоступно (уже свежая версия или ошибка проверки). */
    private val _updateUrl = MutableStateFlow<String?>(null)
    val updateUrl: StateFlow<String?> = _updateUrl.asStateFlow()

    /**
     * Спросить сервер о свежей версии приложения (api.php?action=latestApk) и
     * сравнить с установленной. Вызывается при открытии диалога синхронизации
     * и по кнопке «Проверить обновление».
     */
    fun checkForUpdates() {
        viewModelScope.launch {
            runCatching { repo.serverLatest(syncServerUrl()) }
                .onSuccess { latest ->
                    if (latest.versionCode > BuildConfig.VERSION_CODE) {
                        _updateInfo.value = "Доступна новая версия ${latest.versionName}"
                        _updateUrl.value = latest.url
                    } else {
                        _updateInfo.value = "Установлена свежая версия (${BuildConfig.VERSION_NAME})"
                        _updateUrl.value = null
                    }
                }
                .onFailure { e ->
                    _updateInfo.value = "Не удалось проверить обновление: ${e.message}"
                    _updateUrl.value = null
                }
        }
    }

    init {
        // Автовход при старте: логин/пароль уже сохранены после первого входа —
        // соединяемся сразу, чтобы пользователю не приходилось «переподключаться»
        // при каждом запуске приложения. Этот блок стоит ПОСЛЕ всех полей класса:
        // внутри syncLogin() используются StateFlow, объявленные выше, — они
        // обязаны быть инициализированы до вызова.
        if (syncServerPassword().isNotEmpty()) {
            syncLogin(silent = true)
        }
    }
}