package com.example.scamshield

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class SmsReceiver : BroadcastReceiver() {

    companion object {
        // Testing-ku true. Final demo-la false pannalaam.
        private const val SHOW_SAFE_NOTIFICATIONS = true

        private const val CHANNEL_ALERT = "scamshield_alerts"
        private const val CHANNEL_SAFE = "scamshield_safe"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: "Unknown"
        val body = messages.joinToString("") { it.messageBody ?: "" }

        // Phone-kulla mattum analyse pannurom. SMS text-a save / log pannala.
        val result = JourneyTracker.apply(context, sender, ScamDetector.analyze(body))

        Log.d(
            "ScamShield",
            "Checked SMS. length=${body.length}, score=${result.score}, level=${result.level}"
        )

        // Main screen-ku result save pannurom (score, level, reasons mattum)
        ResultStore.save(context, result)

        showResultNotification(context, sender, result)
    }

    private fun showResultNotification(context: Context, sender: String, result: ScamResult) {
        val isLow = result.level == RiskLevel.LOW
        if (isLow && !SHOW_SAFE_NOTIFICATIONS) return

        // Notification permission illana notify pannaadhu
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT, "ScamShield Warnings", NotificationManager.IMPORTANCE_HIGH
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SAFE, "ScamShield Safe Results", NotificationManager.IMPORTANCE_LOW
            )
        )

        val title = when (result.level) {
            RiskLevel.HIGH -> "🔴 HIGH RISK: possible scam"
            RiskLevel.MEDIUM -> "🟠 MEDIUM RISK: suspicious message"
            RiskLevel.LOW -> "🟢 LOW RISK: looks normal"
        }

        val advice = when (result.level) {
            RiskLevel.HIGH -> "Do not click the link, do not share OTP / PIN, do not pay. " +
                    "Verify through the official app or website."
            RiskLevel.MEDIUM -> "Be careful. Do not click unknown links. " +
                    "Verify through an official channel."
            RiskLevel.LOW -> "No obvious scam patterns found. Stay careful anyway."
        }

        val reasonsText = if (result.reasons.isEmpty()) {
            "No scam patterns found."
        } else {
            result.reasons.joinToString("\n") { "• $it" }
        }

        val bigText = "From: $sender\n" +
                "Risk score: ${result.score}/100\n\n" +
                "Why:\n$reasonsText\n\n" +
                "Safe action: $advice"

        // Notification tap panna ScamShield main screen open aagum
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context, if (isLow) CHANNEL_SAFE else CHANNEL_ALERT
        )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("Risk score ${result.score}/100. Pull down to see why.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(
                if (isLow) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}