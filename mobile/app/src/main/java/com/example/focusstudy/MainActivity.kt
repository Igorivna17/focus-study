package com.example.focusstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// --- Моделі запиту та відповіді API (v2.1 — з підтримкою контексту та перевірки придатності) ---

data class PlanRequest(
    val topic: String,
    val available_minutes: Int,
    val difficulty: String = "medium",
    val study_context: String? = null
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
    val break_tip: String,
    val is_suitable: Boolean = true,
    val rejection_reason: String? = null,
    val context_applied: Boolean = false
)

// --- Retrofit інтерфейс ---

interface FocusStudyApi {
    @POST("api/plan")
    suspend fun generatePlan(@Body request: PlanRequest): PlanResponse
}

object RetrofitClient {
    private const val BASE_URL = "http://127.0.0.1:8000/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
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
        setContent {
            MaterialTheme {
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
    val historyStorage = remember { HistoryStorage(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedRecordId by remember { mutableStateOf<Long?>(null) }
    var historyList by remember { mutableStateOf(historyStorage.getAllRecords()) }

    val refreshHistory: () -> Unit = {
        historyList = historyStorage.getAllRecords()
    }

    if (selectedRecordId != null) {
        val record = remember(selectedRecordId, historyList) {
            historyStorage.getRecordById(selectedRecordId!!)
        }
        if (record != null) {
            HistoryDetailScreen(
                record = record,
                onBack = { selectedRecordId = null },
                onDelete = {
                    historyStorage.deleteRecord(record.id)
                    refreshHistory()
                    selectedRecordId = null
                }
            )
            return
        } else {
            selectedRecordId = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FocusStudy AI Планувальник") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
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
                        refreshHistory()
                        selectedTab = 1
                    },
                    icon = { Text("📋") },
                    label = { Text("Історія (${historyList.size})") }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> FocusStudyScreen(
                    onPlanSaved = { topic, minutes, difficulty, plan ->
                        historyStorage.addRecord(topic, minutes, difficulty, plan)
                        refreshHistory()
                    }
                )
                1 -> HistoryScreen(
                    records = historyList,
                    onRecordClick = { id -> selectedRecordId = id },
                    onDeleteRecord = { id ->
                        historyStorage.deleteRecord(id)
                        refreshHistory()
                    },
                    onClearAll = {
                        historyStorage.clearAll()
                        refreshHistory()
                    }
                )
            }
        }
    }
}

@Composable
fun FocusStudyScreen(
    onPlanSaved: (String, Int, String, PlanResponse) -> Unit
) {
    var topic by remember { mutableStateOf("") }
    var minutesInput by remember { mutableStateOf("60") }
    var studyContext by remember { mutableStateOf("") }
    var selectedDifficulty by remember { mutableStateOf("medium") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var unsuitableReason by remember { mutableStateOf<String?>(null) }
    var planResult by remember { mutableStateOf<PlanResponse?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val difficultyOptions = listOf(
        "easy" to "Легкий",
        "medium" to "Середній",
        "hard" to "Інтенсивний"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text("Навчальна тема (мін. 3 символи)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = minutesInput,
            onValueChange = { minutesInput = it.filter { ch -> ch.isDigit() } },
            label = { Text("Доступний час у хвилинах (25–300)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = studyContext,
            onValueChange = { if (it.length <= 500) studyContext = it },
            label = { Text("Контекст / власний конспект (опціонально)") },
            placeholder = { Text("Напр.: фокус на протоколах TCP/UDP та сокетах") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
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
                val minutes = minutesInput.toIntOrNull()

                if (trimmedTopic.length < 3) {
                    errorMessage = "Введіть тему довжиною мінімум 3 символи"
                    unsuitableReason = null
                    return@Button
                }
                if (minutes == null || minutes < 25 || minutes > 300) {
                    errorMessage = "Час має бути в межах від 25 до 300 хвилин"
                    unsuitableReason = null
                    return@Button
                }

                errorMessage = null
                unsuitableReason = null
                isLoading = true

                val trimmedContext = studyContext.trim().ifEmpty { null }

                coroutineScope.launch {
                    try {
                        val response = RetrofitClient.api.generatePlan(
                            PlanRequest(
                                topic = trimmedTopic,
                                available_minutes = minutes,
                                difficulty = selectedDifficulty,
                                study_context = trimmedContext
                            )
                        )
                        if (!response.is_suitable) {
                            planResult = null
                            unsuitableReason = response.rejection_reason
                                ?: "Запит не підходить для складання навчального Pomodoro-плану."
                        } else {
                            planResult = response
                            unsuitableReason = null
                            onPlanSaved(trimmedTopic, minutes, selectedDifficulty, response)
                        }
                    } catch (e: Exception) {
                        errorMessage = "Помилка з'єднання з сервером: ${e.localizedMessage ?: "Невідома помилка"}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Генерація плану..." else "Скласти Pomodoro-план")
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        errorMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = msg,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        unsuitableReason?.let { reason ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Запит не підходить для навчання",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reason,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            topic = ""
                            unsuitableReason = null
                        }
                    ) {
                        Text("Ввести іншу тему")
                    }
                }
            }
        }

        planResult?.let { plan ->
            PlanIntervalsView(plan = plan, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun HistoryScreen(
    records: List<HistoryRecord>,
    onRecordClick: (Long) -> Unit,
    onDeleteRecord: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистити всю історію?") },
            text = { Text("Усі збережені навчальні плани буде видалено з пристрою без можливості відновлення.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearAll()
                    showClearDialog = false
                }) {
                    Text("Очистити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Скасувати")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Збережені плани (${records.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (records.isNotEmpty()) {
                TextButton(onClick = { showClearDialog = true }) {
                    Text("Очистити все", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (records.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Історія поки порожня.\nСтворіть перший навчальний план на вкладці «Планувальник»!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(records, key = { it.id }) { record ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRecordClick(record.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = record.input_summary,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${record.result_summary} • ${record.status}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Створено: ${record.created_at}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { onDeleteRecord(record.id) }) {
                                Text("Видалити", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    record: HistoryRecord,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Деталі плану") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Назад")
                    }
                },
                actions = {
                    TextButton(onClick = onDelete) {
                        Text("Видалити", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = record.input_summary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Дата створення: ${record.created_at}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            PlanIntervalsView(plan = record.plan, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun PlanIntervalsView(plan: PlanResponse, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (plan.context_applied) {
            AssistChip(
                onClick = {},
                label = { Text("Персоналізовано за вашим контекстом") }
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "План для теми: ${plan.topic}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(text = "Кількість Pomodoro-сетів по 25 хв: ${plan.total_pomodoros}")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Порада для перерви: ${plan.break_tip}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
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