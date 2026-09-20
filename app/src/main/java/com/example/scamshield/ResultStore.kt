package com.example.scamshield

import android.content.Context

data class SavedResult(
    val score: Int,
    val level: RiskLevel,
    val reasons: List<String>,
    val timeMillis: Long
)

object ResultStore {

    private const val PREFS = "scamshield_last_result"

    // Privacy: SMS text-um sender number-um save pannala. Score, level, reasons mattum.
    fun save(context: Context, result: ScamResult) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt("score", result.score)
            .putString("level", result.level.name)
            .putString("reasons", result.reasons.joinToString("\n"))
            .putLong("time", System.currentTimeMillis())
            .apply()
    }

    // Save pannina result illana null tharum
    fun load(context: Context): SavedResult? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val levelName = prefs.getString("level", null) ?: return null

        val level = try {
            RiskLevel.valueOf(levelName)
        } catch (e: IllegalArgumentException) {
            return null
        }

        val reasonsRaw = prefs.getString("reasons", "") ?: ""
        val reasons = if (reasonsRaw.isBlank()) emptyList() else reasonsRaw.split("\n")

        return SavedResult(
            score = prefs.getInt("score", 0),
            level = level,
            reasons = reasons,
            timeMillis = prefs.getLong("time", 0L)
        )
    }
}