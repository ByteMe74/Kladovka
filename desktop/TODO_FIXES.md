# TODO Fixes - Список задач для завершения

## 1. Заполнение TextField значений в Edit диалогах

### EditShelfScreen.kt
**Текущий код:**
```kotlin
OutlinedTextField(
    value = "",
    onValueChange = { /* TODO: capture value */ },
    label = { Text("Название") },
    modifier = Modifier.fillMaxWidth()
)
```

**Нужно:**
```kotlin
// Перед открытием диалога получить сущность
val shelf = repo.shelf(id)
// В диалоге
OutlinedTextField(
    value = shelf?.name ?: "",
    onValueChange = { newValue ->
        shelf?.let { repo.updateShelf(it.copy(name = newValue)) }
    },
    label = { Text("Название") },
    modifier = Modifier.fillMaxWidth()
)
```

### Аналогично для всех Edit экранов:
- `EditPolkaScreen.kt`
- `EditContainerScreen.kt`
- `EditPlaceScreen.kt`
- `ItemEditScreen.kt`

## 2. Связывание onValueChange с Repository.save()

**Шаги:**
1. Создать переменную для хранения значения
2. Обновлять её в onValueChange
3. Сохранять в Repository при нажатии "Сохранить"

**Пример для Shelf:**
```kotlin
@Composable
fun EditShelfScreen(repo: Repository, onBack: () -> Unit, onSave: (Shelf) -> Unit) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var placeId by remember { mutableStateOf(0L) }
    
    if (showShelfDialog) {
        AlertDialog(
            title = { Text("Новый стеллаж") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Заметки") }
                )
                Card(
                    onClick = { showShelfPlaceDialog = true }
                ) {
                    Text("Место: ${repo.places.value.firstOrNull()?.name ?: "Выберите" }")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    repo.addShelf(Shelf(name = name, notes = notes, placeId = placeId))
                    showShelfDialog = false
                }) {
                    Text("Создать")
                }
            }
        )
    }
}
```

## 3. ID Handling для Place и Item

### EditPlaceScreen.kt
**Проблема:** ID placeholder `0L` не работает

**Решение:**
```kotlin
@Composable
fun EditPlaceScreen(repo: Repository, onBack: () -> Unit, onSave: (Place) -> Unit) {
    // Получаем существующее место или создаем новое
    val placeId = /* из навигационных параметров или 0L для нового */
    val place = repo.place(placeId)
    
    var name by remember { mutableStateOf(place?.name ?: "") }
    var latitude by remember { mutableStateOf(place?.latitude?.toString() ?: "") }
    var longitude by remember { mutableStateOf(place?.longitude?.toString() ?: "") }
    var notes by remember { mutableStateOf(place?.notes ?: "") }
    
    // ... остальной код
}
```

### AddItemScreen.kt
**Проблема:** Item требует ShelfId, PolkaId, ContainerId, PlaceId

**Решение:**
```kotlin
@Composable
fun AddItemScreen(repo: Repository, onBack: () -> Unit, onSave: (Item) -> Unit) {
    // Для теста можно задать фиксированные ID
    val shelfId = 1L  // или получить из выбора
    val polkaId = 2L  // или получить из выбора
    val containerId = 3L  // или получить из выбора
    val placeId = 1L  // или получить из выбора
    
    var name by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    
    // ... остальной код
}
```

## 4. Wire Delete Operations

**В App.kt:**
```kotlin
// Уже реализовано, но нужно проверить:
val onDeleteShelf = remember {
    { shelfId: Long ->
        scope.launch {
            repo.shelf(shelfId)?.let { shelf ->
                repo.deleteShelf(shelfId)
            }
        }
    }
}

// В MainScreen.kt нужно передать этот callback:
MainScreen(
    onDeleteShelf = { shelfId ->
        AlertDialog(
            title = { Text("Удалить стеллаж?") },
            text = { Text("Этот шаг нельзя отменить") },
            confirmButton = {
                TextButton(onClick = { onDeleteShelf(shelfId) }) {
                    Text("Удалить", color = Color.Red)
                }
            }
        )
    }
)
```

## 5. Тестирование

### Проверить все сценарии:

1. **Стеллажи:**
   - Добавить стеллаж (выбрать место)
   - Редактировать стеллаж
   - Удалить стеллаж

2. **Полки:**
   - Добавить полку (выбрать стеллаж)
   - Редактировать полку
   - Удалить полку

3. **Контейнеры:**
   - Добавить контейнер (выбрать место)
   - Редактировать контейнер
   - Удалить контейнер

4. **Места:**
   - Добавить место (широта, долгота)
   - Редактировать место
   - Удалить место

5. **Вещи:**
   - Добавить вещь (выбрать shelf→polka→container→place)
   - Редактировать вещь
   - Удалить вещь

6. **Синхронизация:**
   - Проверить статус
   - Запустить синхронизацию

## 6. Ошибки компиляции

**Если возникнут ошибки:**
- Проверьте импорты в каждом файле
- Убедитесь, что все зависимости в build.gradle.kts
- Проверьте версии библиотек

**Частые ошибки:**
- `Unresolved reference: Repository` - добавить `import ru.kladovka.data.Repository`
- `Unresolved reference: Shelf` - добавить `import ru.kladovka.data.Shelf`
- `Unresolved reference: AlertDialog` - добавить `import androidx.compose.material3.AlertDialog`

## 7. Сборка

```bash
cd kladovka-desktop
gradle build
```

**Если Gradle не в PATH:**
- Скачать Gradle 8.10
- Распаковать в `gradle-8.10`
- Добавить `gradle-8.10\bin` в PATH

**Запуск:**
```bash
gradle run
```
