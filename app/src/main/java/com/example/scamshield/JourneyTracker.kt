package com.example.scamshield

import android.content.Context
import java.security.MessageDigest

object JourneyTracker {

    private const val PREFS = "scamshield_journey"

    // 10 nimisham. Final demo-ku 24 hours pannalaam: 24 * 60 * 60 * 1000L
    private const val WINDOW_MILLIS = 10 * 60 * 1000L

    // Privacy: sender number-a save pannaama, hash mattum save pannurom
    private fun hashSender(sender: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(sender.trim().lowercase().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    // Munnadi vandha SMS-oda stages-a serthu, journey bonus-oda puthu result tharum
    fun apply(context: Context, sender: String, result: ScamResult): ScamResult {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val key = hashSender(sender)

        // Time window kadandha palaya data-va azhikkirom
        val cleaner = prefs.edit()
        for ((k, v) in prefs.all) {
            if (k.startsWith("time_") && v is Long && now - v > WINDOW_MILLIS) {
                cleaner.remove(k)
                cleaner.remove("mask_" + k.removePrefix("time_"))
            }
        }
        cleaner.apply()

        // Ippo vandha SMS-la scam stage illana, journey-a thodaadhu. Safe SMS safe-a irukkum.
        if (result.stages.isEmpty()) return result

        // Ippo vandha stages-a palaya stages-oda serkkirom
        var mask = prefs.getInt("mask_$key", 0)
        for (stage in result.stages) {
            mask = mask or stage.bit
        }
        prefs.edit()
            .putInt("mask_$key", mask)
            .putLong("time_$key", now)
            .apply()

        val seen = ScamStage.values().filter { (mask and it.bit) != 0 }
        val count = seen.size

        // 2 stages-ku keezha journey illa
        if (count < 2) return result

        val bonus = when {
            count >= 4 -> 45
            count == 3 -> 30
            else -> 15
        }
        val newScore = minOf(result.score + bonus, 100)

        val progress = ScamStage.values().joinToString(" → ") { stage ->
            if ((mask and stage.bit) != 0) "✅ ${stage.label}" else "⬜ ${stage.label}"
        }

        val newReasons = result.reasons +
                "Scam journey: $count of 5 stages seen from this sender (+$bonus)" +
                "Journey: $progress"

        return result.copy(
            score = newScore,
            level = ScamDetector.levelFor(newScore),
            reasons = newReasons
        )
    }
}