package ru.kladovka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.kladovka.data.AppVersion
import ru.kladovka.data.UpdateState
import java.awt.Desktop
import java.net.URI

/**
 * Окно проверки обновления.
 *
 * Отдельный диалог, а не строка в настройках, потому что обновление — это
 * действие, которое человек ищет осознанно, и результат должен быть виден
 * сразу: либо «вы свежие», либо «есть vX, вот ссылка».
 *
 * Самообновления нет и быть не может: приложение портативное, заменять
 * запущенный exe нельзя. Поэтому кнопка открывает ссылку в браузере, а рядом
 * написано, что делать дальше, — иначе человек скачает файл и не поймёт, куда
 * его деть.
 */
@Composable
fun UpdateDialog(
    state: UpdateState,
    onCheck: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Обновление") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Установленная версия: " +
                        if (AppVersion.known) AppVersion.shortName else "неизвестна",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (state) {
                    is UpdateState.Unknown -> Text(
                        "Нажмите «Проверить», чтобы узнать, есть ли свежая сборка.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    is UpdateState.Checking -> Text(
                        "Спрашиваем сервер…",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    is UpdateState.UpToDate -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Обновлений нет — у вас свежая версия.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Сервер предлагает ${state.serverVersion}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is UpdateState.Available -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Доступна версия ${state.serverVersion}" +
                                if (state.sizeText().isNotEmpty()) " (${state.sizeText()})" else "",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Приложение портативное и само себя не заменит — так оно и должно быть. " +
                                "Нажмите «Скачать», закройте Кладовку и замените файл Kladovka.exe " +
                                "на скачанный: старый можно удалить.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Данные не пострадают: они лежат в папке пользователя, а не внутри exe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { openInBrowser(state.url) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Скачать версию ${state.serverVersion}")
                        }
                        Text(
                            "Ссылка откроется в браузере: " + state.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is UpdateState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Проверить не удалось: ${state.reason}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Это не значит, что обновлений нет — сервер мог быть недоступен.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state !is UpdateState.Checking) {
                    OutlinedButton(onClick = onCheck, modifier = Modifier.padding(end = 4.dp)) {
                        Text("Проверить")
                    }
                }
                TextButton(onClick = onClose) { Text("Закрыть") }
            }
        }
    )
}

/**
 * Открыть ссылку в браузере.
 *
 * Отдельная функция, потому что сбой открытия — обычное дело на сервере без
 * зарегистрированного браузера — не должен валить приложение: об этом честнее
 * сказать в окне, чем закрывать его ошибкой.
 */
private fun openInBrowser(url: String) {
    runCatching {
        val d = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        d?.browse(URI(url))
    }.onFailure {
        // Нечего делать, кроме как оставить ссылку видимой: она напечатана
        // прямо в окне, и человек может скопировать её сам.
    }
}