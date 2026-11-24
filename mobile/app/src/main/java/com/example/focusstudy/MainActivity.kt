package com.example.focusstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val available_minutes: Int
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
    // Використовуємо localhost через прокинутий порт adb reverse tcp:8000 tcp:8000
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
                    FocusStudyScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusStudyScreen() {
    var topic by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("60") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var planResult by remember { mutableStateOf<PlanResponse?>(null) }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FocusStudy AI Планувальник", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                                PlanRequest(topic = trimmedTopic, available_minutes = minutes)
                            )
                            planResult = response
                        } catch (e: Exception) {
                            errorMessage = "Помилка з'єднання із сервером: ${e.localizedMessage ?: "перевірте запуск Backend"}"
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
    }
}