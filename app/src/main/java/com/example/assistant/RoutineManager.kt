package com.example.assistant

import android.content.Context
import android.util.Log
import com.example.assistant.background.AriaBackgroundWakeService
import com.example.assistant.overlay.AriaEdgeGlowOverlayManager
import com.example.assistant.overlay.AriaEdgeGlowView
import com.example.data.AriaDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed class RoutineResult {
    data class Success(val routineName: String, val speechResponse: String, val isGoodNight: Boolean = false) : RoutineResult()
}

/**
 * RoutineManager for ARIA Custom Voice Routines:
 * 1. "Good Morning ARIA" (Wake-up routine: activates ARIA, speaks greeting + Weather + Reminders + Top Headlines)
 * 2. "Good Night ARIA" (Sleep routine: sends standby farewell, disables background voice drain to conserve battery, switches UI to STANDBY)
 * Easily extensible for future routines like "ARIA, office jaana hai" (traffic + calendar).
 */
class RoutineManager(
    private val context: Context,
    private val ariaDao: AriaDao
) {

    private val commandProcessor = AriaCommandProcessor(context, ariaDao)

    companion object {
        fun isRoutineCommand(queryLower: String): Boolean {
            return queryLower.contains("good morning") ||
                    queryLower.contains("subah ho gayi") ||
                    queryLower.contains("wake up aria") ||
                    queryLower.contains("good night") ||
                    queryLower.contains("shubh ratri") ||
                    queryLower.contains("so jao aria") ||
                    queryLower.contains("sleep mode") ||
                    queryLower.contains("standby mode") ||
                    queryLower.contains("office jaana hai")
        }
    }

    suspend fun executeRoutine(rawQuery: String, userName: String = "Boss"): RoutineResult = withContext(Dispatchers.IO) {
        val q = rawQuery.lowercase().trim()

        if (q.contains("good night") || q.contains("shubh ratri") || q.contains("so jao") || q.contains("sleep mode") || q.contains("standby")) {
            executeGoodNightRoutine(userName)
        } else if (q.contains("office jaana hai") || q.contains("leaving for office")) {
            executeOfficeRoutine(userName)
        } else {
            executeGoodMorningRoutine(userName)
        }
    }

    private suspend fun executeGoodMorningRoutine(userName: String): RoutineResult {
        // 1. Ensure edge glow lights up active
        AriaEdgeGlowOverlayManager.updateGlowState(AriaEdgeGlowView.GlowState.SPEAKING)

        // 2. Aggregate weather + reminders + morning greeting
        val briefingText = commandProcessor.generateBriefingSummary(userName)

        return RoutineResult.Success(
            routineName = "GOOD_MORNING",
            speechResponse = briefingText,
            isGoodNight = false
        )
    }

    private suspend fun executeGoodNightRoutine(userName: String): RoutineResult {
        // 1. Short peaceful farewell
        val farewellText = "Good night, $userName! Main standby mode me ja rahi hoon taaki aapki phone battery save ho sake. Sound sleep lijiye! 🌙✨"

        // 2. Gracefully pause background always-listening service to save overnight battery
        try {
            AriaBackgroundWakeService.stop(context)
        } catch (e: Exception) {
            Log.e("RoutineManager", "Error stopping background service: ${e.message}")
        }

        // 3. Close edge glow
        try {
            AriaEdgeGlowOverlayManager.hideEdgeGlow()
        } catch (e: Exception) {
            Log.e("RoutineManager", "Error hiding overlay: ${e.message}")
        }

        return RoutineResult.Success(
            routineName = "GOOD_NIGHT",
            speechResponse = farewellText,
            isGoodNight = true
        )
    }

    private suspend fun executeOfficeRoutine(userName: String): RoutineResult {
        val pendingReminders = try {
            ariaDao.getAllReminders().first().filter { !it.isCompleted }
        } catch (e: Exception) {
            emptyList()
        }

        val speech = buildString {
            append("Safe travels to office, $userName! 🚗\n")
            append("Aaj ke schedule me ${pendingReminders.size} active tasks pending hain.")
            if (pendingReminders.isNotEmpty()) {
                append(" Pehla task: '${pendingReminders.first().title}' at ${pendingReminders.first().timeString}.")
            }
            append(" Have a successful workday!")
        }

        return RoutineResult.Success(
            routineName = "OFFICE_DEPARTURE",
            speechResponse = speech,
            isGoodNight = false
        )
    }
}
