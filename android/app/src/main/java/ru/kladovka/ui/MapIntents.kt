package ru.kladovka.ui

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Открывает внешнее карт-приложение (Google/Яндекс/2ГИС) на точке — просмотр адреса. */
fun openInMapApp(context: Context, lat: Double, lon: Double) {
    openOnMap(context, lat, lon, "Открыть на карте")
}

/**
 * Открывает выбор карт-приложения для выбора точки.
 * Стартуем от последней известной позиции (текущее место), если есть разрешение на геолокацию.
 * Яндекс Карты не слушают geo:-, поэтому добавляем их в системный выбор отдельным intent'ом.
 */
fun openMapForPick(context: Context) {
    val c = lastKnownPosition(context)
    openOnMap(context, c?.first ?: 0.0, c?.second ?: 0.0, "Выбрать точку в картах")
}

/** Поделиться местом текстом: имя + адрес + ссылка на карту. */
fun sharePlace(context: Context, name: String, description: String?, lat: Double?, lon: Double?) {
    val body = buildString {
        append(name)
        if (!description.isNullOrBlank()) append(" — ").append(description)
        if (lat != null && lon != null) {
            append("\n")
            append(String.format(Locale.US, "https://maps.google.com/?q=%f,%f", lat, lon))
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(Intent.createChooser(send, "Поделиться местом"))
}

private fun openOnMap(context: Context, lat: Double, lon: Double, title: String) {
    val geo = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f", lat, lon, lat, lon))
    val chooser = Intent.createChooser(Intent(Intent.ACTION_VIEW, geo), title)
    yandexPickIntent(context, lat, lon)?.let { ya ->
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(ya))
    }
    try {
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(context, "Не найдено приложение карт", Toast.LENGTH_LONG).show()
    }
}

/**
 * Intent для Яндекс Карт: они не обрабатывают geo:-, только yandexmaps:// и https.
 * Возвращает null, если Яндекс Карты не установлены.
 */
private fun yandexPickIntent(context: Context, lat: Double, lon: Double): Intent? {
    val pm = context.packageManager
    val yaPackage = listOf("ru.yandex.yandexmaps", "com.yandex.yandexmaps", "ru.yandex.maps")
        .firstOrNull { pkg -> runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false) }
        ?: return null
    val ll = String.format(Locale.US, "%f,%f", lon, lat) // Яндекс: сначала долгота, потом широта
    val yandexmapsUri = Uri.parse("yandexmaps://maps.yandex.ru/?ll=$ll&z=17&pt=$ll")
    val schemeHandled = pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, yandexmapsUri), 0)
        .any { it.activityInfo.packageName == yaPackage }
    val uri = if (schemeHandled) yandexmapsUri else Uri.parse("https://yandex.ru/maps/?ll=$ll&z=17&pt=$ll")
    return Intent(Intent.ACTION_VIEW, uri).setPackage(yaPackage)
}

/** Последняя известная позиция, если на неё уже есть разрешение (иначе null). */
fun lastKnownPosition(context: Context): Pair<Double, Double>? = runCatching {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    providers.forEach { provider ->
        try {
            lm.getLastKnownLocation(provider)?.let { return it.latitude to it.longitude }
        } catch (_: SecurityException) {
            // нет разрешения — пробуем следующий провайдер
        }
    }
    null
}.getOrNull()

/**
 * Обратное геокодирование: координаты → адрес.
 * Сначала системный Geocoder (есть на большинстве телефонов), затем Nominatim (OSM).
 */
fun reverseGeocode(context: Context, lat: Double, lon: Double): String? {
    try {
        if (Geocoder.isPresent()) {
            val results = Geocoder(context, Locale("ru")).getFromLocation(lat, lon, 1)
            if (!results.isNullOrEmpty()) {
                val r = results[0]
                r.getAddressLine(0)?.takeIf { it.isNotBlank() }?.let { return it }
                val parts = listOf(r.thoroughfare, r.subThoroughfare, r.locality, r.adminArea, r.countryName)
                val joined = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(", ")
                if (joined.isNotBlank()) return joined
            }
        }
    } catch (_: Exception) {
        // падаем на запасной вариант
    }
    return runCatching {
        val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&accept-language=ru"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "KladovkaApp/1.6 (Android)")
            setRequestProperty("Accept-Language", "ru")
        }
        try {
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(text).optString("display_name").takeIf { it.isNotBlank() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

/** Кэш адресов, чтобы не геокодировать одни и те же координаты при каждой перерисовке списка. */
object AddressCache {
    private val cache = mutableMapOf<Pair<Double, Double>, String?>()

    suspend fun address(context: Context, lat: Double, lon: Double): String? {
        val key = lat to lon
        cache[key]?.let { return it }
        val result = withContext(Dispatchers.IO) { reverseGeocode(context, lat, lon) }
        cache[key] = result
        return result
    }
}

/**
 * Разбор координат из geo:-ссылок, ссылок Google/Яндекс Карт и текста «широта, долгота».
 * Используется для возврата точки из внешнего карт-приложения через «Поделиться».
 */
object MapLinkParser {

    fun parseIntent(intent: Intent?): Pair<Double, Double>? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.toString()?.let(::parseText)
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::parseText)
            else -> null
        }
    }

    fun parseText(rawText: String): Pair<Double, Double>? {
        val text = rawText.trim()
        if (text.isEmpty()) return null
        // Ссылка http(s) — ссылки Google/Яндекс Карт
        Regex("""https?://\S+""").find(text)?.value?.let { url ->
            parseUrl(url)?.let { return it }
        }
        // geo: URI
        val geoIdx = text.indexOf("geo:")
        if (geoIdx >= 0) {
            val geoUri = text.substring(geoIdx).takeWhile { !it.isWhitespace() }
            parseGeo(geoUri)?.let { return it }
        }
        // Просто «55.751244, 37.618423»
        return parsePlainPair(text)
    }

    private fun parseUrl(url: String): Pair<Double, Double>? {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        return when {
            host.contains("google") -> parseGoogle(uri)
            host.contains("yandex") -> parseYandex(uri)
            else -> null
        }
    }

    /** Google: q=широта,долгота; ll=широта,долгота; …/@широта,долгота,зум. Порядок: широта, долгота. */
    private fun parseGoogle(uri: Uri): Pair<Double, Double>? {
        listOf("q", "ll").forEach { key ->
            uri.getQueryParameter(key)?.let { param ->
                parsePlainPair(param)?.let { return it }
            }
        }
        val path = uri.path ?: ""
        Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""").find(path)?.let { m ->
            val lat = m.groupValues[1].toDoubleOrNull()
            val lon = m.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null && sane(lat, lon)) return lat to lon
        }
        return null
    }

    /** Яндекс: pt=долгота,широта; ll=долгота,широта. ВНИМАНИЕ: порядок — долгота, широта! */
    private fun parseYandex(uri: Uri): Pair<Double, Double>? {
        listOf("pt", "ll").forEach { key ->
            uri.getQueryParameter(key)?.let { param ->
                val parts = param.split(',')
                if (parts.size >= 2) {
                    val lon = parts[0].trim().toDoubleOrNull()
                    val lat = parts[1].trim().toDoubleOrNull()
                    if (lat != null && lon != null && sane(lat, lon)) return lat to lon
                }
            }
        }
        return null
    }

    /** geo:широта,долгота?q=широта,долгота */
    private fun parseGeo(geoUri: String): Pair<Double, Double>? {
        val uri = Uri.parse(geoUri)
        uri.getQueryParameter("q")?.let { parsePlainPair(it)?.let { return it } }
        val body = geoUri.substringAfter("geo:").substringBefore('?')
        return parsePlainPair(body)
    }

    /** Пара чисел «a, b» → (широта, долгота). Отклоняет нули-заглушки и невалидные диапазоны. */
    private fun parsePlainPair(s: String): Pair<Double, Double>? {
        if (s.isEmpty()) return null
        Regex("""(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)""").matchEntire(s.trim())?.let { m ->
            val a = m.groupValues[1].toDoubleOrNull()
            val b = m.groupValues[2].toDoubleOrNull()
            if (a != null && b != null && sane(a, b) && !(a == 0.0 && b == 0.0)) return a to b
        }
        return null
    }

    private fun sane(lat: Double, lon: Double): Boolean =
        lat in -90.0..90.0 && lon in -180.0..180.0
}