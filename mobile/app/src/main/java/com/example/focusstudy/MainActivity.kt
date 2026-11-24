package com.example.focusstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.focusstudy.ui.theme.FocusStudyTheme
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// --- Моделі даних (відповідають схемі FastAPI) ---
data class PlanRequest(
    val topic: String,
    val available_minutes: Int,
    val difficulty: String = "medium"
)

data class PomodoroInterval(
    val step_number: Int,
    val title: String,
    val duration_minutes: Int,
    val task_description: String
)

data class PlanResponse(
    val topic: String,
    val total_pomodoros: Int,
    val intervals: List<PomodoroInterval>,
    val break_tip: String
)

// --- API Клієнт ---
interface FocusStudyApi {
    @POST("/api/plan")
    suspend fun generatePlan(@Body request: PlanRequest): PlanResponse
}

object RetrofitClient {
    private const val BASE_URL = "http://localhost:8000/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: FocusStudyApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FocusStudyApi::class.java)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusStudyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FocusStudyApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusStudyApp() {
    val context = LocalContext.current
    val storage = remember { HistoryStorage(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Планувальник, 1 = Історія
    var historyRecords by remember { mutableStateOf(storage.getAllRecords()) }
    var selectedRecordForDetails by remember { mutableStateOf<HistoryRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            selectedRecordForDetails != null -> "Деталі плану"
                            selectedTab == 0 -> "FocusStudy AI Планувальник"
                            else -> "Історія запитів"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (selectedRecordForDetails != null) {
                        TextButton(onClick = { selectedRecordForDetails = null }) {
                            Text("← Назад", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            if (selectedRecordForDetails == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Text("⏱") },
                        label = { Text("Планувальник") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = {
                            historyRecords = storage.getAllRecords()
                            selectedTab = 1
                        },
                        icon = { Text("📋") },
                        label = { Text("Історія (${historyRecords.size})") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                selectedRecordForDetails != null -> {
                    HistoryDetailsScreen(record = selectedRecordForDetails!!)
                }
                selectedTab == 0 -> {
                    PlannerScreen(
                        onPlanSuccess = { topic, minutes, difficulty, plan ->
                            storage.addRecord(topic, minutes, difficulty, plan)
                            historyRecords = storage.getAllRecords()
                        }
                    )
                }
                else -> {
                    HistoryListScreen(
                        records = historyRecords,
                        onOpenDetails = { record ->
                            selectedRecordForDetails = storage.getRecordById(record.id)
                        },
                        onDeleteRecord = { id ->
                            storage.deleteRecord(id)
                            historyRecords = storage.getAllRecords()
                        },
                        onClearAll = {
                            storage.clearAll()
                            historyRecords = storage.getAllRecords()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PlannerScreen(
    onPlanSuccess: (String, Int, String, PlanResponse) -> Unit
) {
    var topic by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("60") }
    var selectedDifficulty by remember { mutableStateOf("medium") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var planResult by remember { mutableStateOf<PlanResponse?>(null) }

    val difficultyOptions = listOf(
        "easy" to "Легкий",
        "medium" to "Середній",
        "hard" to "Інтенсивний"
    )

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text("Навчальна тема (мін. 3 символи)") },
            placeholder = { Text("Напр.: Підготовка до тесту з баз даних") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = minutesText,
            onValueChange = { minutesText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Доступний час у хвилинах (25–300)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(
            text = "Інтенсивність підготовки:",
            style = MaterialTheme.typography.labelLarge
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            difficultyOptions.forEach { (key, label) ->
                FilterChip(
                    selected = selectedDifficulty == key,
                    onClick = { selectedDifficulty = key },
                    label = { Text(label) }
                )
            }
        }

        Button(
            onClick = {
                val trimmedTopic = topic.trim()
                val minutes = minutesText.toIntOrNull()

                if (trimmedTopic.length < 3) {
                    errorMessage = "Введіть тему довжиною мінімум 3 символи"
                    return@Button
                }
                if (minutes == null || minutes !in 25..300) {
                    errorMessage = "Вкажіть час від 25 до 300 хвилин"
                    return@Button
                }

                errorMessage = null
                planResult = null
                isLoading = true

                coroutineScope.launch {
                    try {
                        val response = RetrofitClient.api.generatePlan(
                            PlanRequest(
                                topic = trimmedTopic,
                                available_minutes = minutes,
                                difficulty = selectedDifficulty
                            )
                        )
                        if (response.intervals.isNotEmpty()) {
                            planResult = response
                            onPlanSuccess(trimmedTopic, minutes, selectedDifficulty, response)
                        } else {
                            errorMessage = "Отримано порожній план від сервера"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Помилка з'єднання із сервером: перевірте запуск Backend"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text(if (isLoading) "Генерація плану..." else "Скласти Pomodoro-план")
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        errorMessage?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        planResult?.let { plan ->
            PlanIntervalsView(plan = plan, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun HistoryListScreen(
    records: List<HistoryRecord>,
    onOpenDetails: (HistoryRecord) -> Unit,
    onDeleteRecord: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    var recordIdToDelete by remember { mutableStateOf<Long?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    if (recordIdToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordIdToDelete = null },
            title = { Text("Видалити запис?") },
            text = { Text("Цей план буде видалено з локальної історії пристрою без можливості відновлення.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRecord(recordIdToDelete!!)
                    recordIdToDelete = null
                }) {
                    Text("Видалити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordIdToDelete = null }) {
                    Text("Скасувати")
                }
            }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Очистити всю історію?") },
            text = { Text("Усі збережені навчальні плани будуть видалені з пристрою.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearAllDialog = false
                }) {
                    Text("Очистити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Скасувати")
                }
            }
        )
    }

    if (records.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Історія запитів порожня",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Створіть перший Pomodoro-план на вкладці «Планувальник» — він автоматично збережеться тут.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Збережені плани (${records.size}):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { showClearAllDialog = true }) {
                    Text("Очистити історію", color = MaterialTheme.colorScheme.error)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(records, key = { it.id }) { record ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDetails(record) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = record.created_at,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    AssistChip(
                                        onClick = { onOpenDetails(record) },
                                        label = { Text(record.status) }
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = record.input_summary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = record.result_summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            TextButton(onClick = { recordIdToDelete = record.id }) {
                                Text("Видалити", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryDetailsScreen(record: HistoryRecord) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Збережено: ${record.created_at} • Статус: ${record.status}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Запит: ${record.input_summary}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        PlanIntervalsView(plan = record.plan, modifier = Modifier.weight(1f))
    }
}

@Composable
fun PlanIntervalsView(plan: PlanResponse, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "План: ${plan.topic} (${plan.total_pomodoros} сетів по 25 хв)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Порада на перерву: ${plan.break_tip}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(plan.intervals) { item ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Крок ${item.step_number}: ${item.title} (${item.duration_minutes} хв)",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = item.task_description)
                    }
                }
            }
        }
    }
}