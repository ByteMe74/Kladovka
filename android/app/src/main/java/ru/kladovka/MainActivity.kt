package ru.kladovka

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.kladovka.ui.AppViewModel
import ru.kladovka.ui.ContainerEditScreen
import ru.kladovka.ui.ItemEditScreen
import ru.kladovka.ui.KladovkaTheme
import ru.kladovka.ui.MainScreen
import ru.kladovka.ui.MapLinkParser
import ru.kladovka.ui.PlaceEditScreen
import ru.kladovka.ui.PolkaEditScreen
import ru.kladovka.ui.ShelfEditScreen
import ru.kladovka.ui.ThemeMode

class MainActivity : ComponentActivity() {

    /** Координаты, присланные из внешних карт через «Поделиться» / geo:-ссылку. */
    var pendingPick by mutableStateOf<Pair<Double, Double>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Обязательная настройка osmdroid (карта): агент пользователя
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = packageName
        }
        pendingPick = MapLinkParser.parseIntent(intent)
        if (isMapIntent(intent) && pendingPick == null) {
            Toast.makeText(this, "Координаты не найдены в переданной ссылке", Toast.LENGTH_LONG).show()
        }
        enableEdgeToEdge()
        setContent {
            KladovkaApp(
                pendingPick = pendingPick,
                onPendingConsumed = { pendingPick = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingPick = MapLinkParser.parseIntent(intent)
        if (isMapIntent(intent) && pendingPick == null) {
            Toast.makeText(this, "Координаты не найдены в переданной ссылке", Toast.LENGTH_LONG).show()
        }
    }

    /** Пришло ли намерение именно «с карты» (geo: или Поделиться), а не обычный запуск. */
    private fun isMapIntent(intent: Intent): Boolean {
        return (intent.action == Intent.ACTION_VIEW && intent.data?.scheme?.equals("geo", true) == true) ||
                (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true)
    }
}

@Composable
private fun KladovkaApp(
    vm: AppViewModel = viewModel(),
    pendingPick: Pair<Double, Double>? = null,
    onPendingConsumed: () -> Unit = {}
) {
    var screenIndex by rememberSaveable { mutableStateOf(0) }
    var screenArg by rememberSaveable { mutableStateOf(0L) }

    fun navigate(newIndex: Int, arg: Long = 0L) {
        screenIndex = newIndex
        screenArg = arg
    }

    // Режим темы (сохраняется в настройках): null = как в системе
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    KladovkaTheme(
        darkTheme = when (themeMode) {
            ThemeMode.SYSTEM -> null
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    ) {
        // Пришли координаты из внешних карт: открываем экран места (если его ещё нет),
        // а сам экран подставит координаты в поля.
        LaunchedEffect(pendingPick) {
            val p = pendingPick ?: return@LaunchedEffect
            if (screenIndex != 4) navigate(4, 0L)
        }

        // Лёгкий фейд вместо полного кроссфейда: анимируется только новый экран,
        // старый мгновенно уходит из композиции — нет двойной отрисовки, переходы не тормозят.
        when (screenIndex) {
        1 -> ScreenFade {
            BackHandler { navigate(0) }
            ItemEditScreen(itemId = screenArg, vm = vm, onDone = { navigate(0) })
        }
        2 -> ScreenFade {
            BackHandler { navigate(0) }
            ShelfEditScreen(shelfId = screenArg, vm = vm, onDone = { navigate(0) })
        }
        3 -> ScreenFade {
            BackHandler { navigate(0) }
            ContainerEditScreen(containerId = screenArg, vm = vm, onDone = { navigate(0) })
        }
        5 -> ScreenFade {
            BackHandler { navigate(0) }
            PolkaEditScreen(polkaId = screenArg, vm = vm, onDone = { navigate(0) })
        }
        4 -> ScreenFade {
            BackHandler { navigate(0) }
            PlaceEditScreen(
                placeId = screenArg,
                vm = vm,
                onDone = { navigate(0) },
                pendingLat = pendingPick?.first,
                pendingLon = pendingPick?.second,
                onPendingConsumed = onPendingConsumed
            )
        }
        else -> ScreenFade {
            MainScreen(
                vm = vm,
                onOpenItem = { navigate(1, it) },
                onAddItem = { navigate(1, 0L) },
                onEditShelf = { navigate(2, it) },
                onAddShelf = { navigate(2, 0L) },
                onEditPolka = { navigate(5, it) },
                onAddPolka = { navigate(5, 0L) },
                onEditContainer = { navigate(3, it) },
                onAddContainer = { navigate(3, 0L) },
                onEditPlace = { navigate(4, it) },
                onAddPlace = { navigate(4, 0L) }
            )
        }
        }
    }
}

/** Быстрое появление экрана (160 мс) — дёшево для слабых устройств. */
@Composable
private fun ScreenFade(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(visible = visible, enter = fadeIn(animationSpec = tween(160))) {
        content()
    }
}