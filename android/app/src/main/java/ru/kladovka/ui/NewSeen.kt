package ru.kladovka.ui

import android.content.Context

/**
 * Отметка «новое»: вещь считается новой, если создана после последнего открытия приложения.
 * Логика повторяет веб-кабинет (kladovka-seen в localStorage):
 * lastSeen = прошлое сохранённое значение (или сейчас при первом запуске),
 * затем сразу сохраняется текущий момент. Вызывать [init] один раз при старте UI.
 */
object NewSeen {

    @Volatile
    private var lastSeen: Long = 0L

    private const val PREFS = "app_settings"
    private const val KEY = "lastSeen"

    fun init(context: Context) {
        if (lastSeen != 0L) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getLong(KEY, 0L)
        val now = System.currentTimeMillis()
        lastSeen = if (saved <= 0L) now else saved
        prefs.edit().putLong(KEY, now).apply()
    }

    /** Новая ли запись по времени создания (миллисекунды эпохи). */
    fun isNew(createdAt: Long): Boolean = createdAt > 0L && createdAt > lastSeen
}