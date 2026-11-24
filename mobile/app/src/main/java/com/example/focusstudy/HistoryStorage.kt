package com.example.focusstudy

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Модель локального запису згідно з вимогами Лабораторної роботи 4
data class HistoryRecord(
    val id: Long,
    val created_at: String,
    val input_summary: String,
    val result_summary: String,
    val status: String = "готово",
    val plan: PlanResponse
)

// Окремий шар роботи з локальним сховищем (ізольований від UI)
class HistoryStorage(context: Context) {
    private val prefs = context.getSharedPreferences("focus_study_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val storageKey = "records_list"

    // Отримати всі записи у зворотному хронологічному порядку (новіші зверху)
    fun getAllRecords(): List<HistoryRecord> {
        val json = prefs.getString(storageKey, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<HistoryRecord>>() {}.type
            val list: List<HistoryRecord> = gson.fromJson(json, type) ?: emptyList()
            list.sortedByDescending { it.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Отримати один запис за локальним ID
    fun getRecordById(id: Long): HistoryRecord? {
        return getAllRecords().find { it.id == id }
    }

    // Зберегти тільки успішний результат
    fun addRecord(topic: String, minutes: Int, difficulty: String, plan: PlanResponse): HistoryRecord {
        val currentList = getAllRecords().toMutableList()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val difficultyLabel = when (difficulty) {
            "easy" -> "Легкий"
            "hard" -> "Інтенсивний"
            else -> "Середній"
        }

        val newRecord = HistoryRecord(
            id = System.currentTimeMillis(),
            created_at = now,
            input_summary = "$topic • $minutes хв • $difficultyLabel",
            result_summary = "${plan.total_pomodoros} Pomodoro-сетів по 25 хв",
            status = "готово",
            plan = plan
        )

        currentList.add(0, newRecord)
        saveList(currentList)
        return newRecord
    }

    // Видалити один запис за ID
    fun deleteRecord(id: Long) {
        val updatedList = getAllRecords().filterNot { it.id == id }
        saveList(updatedList)
    }

    // Очистити всю історію
    fun clearAll() {
        prefs.edit().remove(storageKey).apply()
    }

    private fun saveList(list: List<HistoryRecord>) {
        prefs.edit().putString(storageKey, gson.toJson(list)).apply()
    }
}