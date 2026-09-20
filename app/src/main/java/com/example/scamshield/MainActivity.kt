package com.example.scamshield

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var tvProtectionStatus: TextView
    private lateinit var tvRiskLevel: TextView
    private lateinit var tvRiskScore: TextView
    private lateinit var tvWarningReason: TextView
    private lateinit var tvAdvice: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updateProtectionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvProtectionStatus = findViewById(R.id.tvProtectionStatus)
        tvRiskLevel = findViewById(R.id.tvRiskLevel)
        tvRiskScore = findViewById(R.id.tvRiskScore)
        tvWarningReason = findViewById(R.id.tvWarningReason)
        tvAdvice = findViewById(R.id.tvAdvice)

        requestNeededPermissions()
        updateProtectionStatus()
        showLatestResult()
    }

    override fun onResume() {
        super.onResume()
        updateProtectionStatus()
        showLatestResult()
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()

        if (!isGranted(Manifest.permission.RECEIVE_SMS)) {
            needed.add(Manifest.permission.RECEIVE_SMS)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            !isGranted(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun updateProtectionStatus() {
        if (isGranted(Manifest.permission.RECEIVE_SMS)) {
            tvProtectionStatus.text = "Protection ON ✅"
            tvProtectionStatus.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            tvProtectionStatus.text = "SMS permission needed ⚠️"
            tvProtectionStatus.setTextColor(Color.parseColor("#EF6C00"))
        }
    }

    private fun showLatestResult() {
        val saved = ResultStore.load(this)

        if (saved == null) {
            tvRiskLevel.text = "No SMS checked yet"
            tvRiskLevel.setTextColor(Color.parseColor("#546E7A"))
            tvRiskScore.text = "Waiting for a new SMS..."
            tvWarningReason.text = "No suspicious message detected yet."
            return
        }

        val (levelText, colorHex, advice) = when (saved.level) {
            RiskLevel.HIGH -> Triple(
                "🔴 HIGH RISK", "#C62828",
                "Do not click the link, do not share OTP / PIN, do not pay. " +
                        "Verify through the official app or website."
            )
            RiskLevel.MEDIUM -> Triple(
                "🟠 MEDIUM RISK", "#EF6C00",
                "Be careful. Do not click unknown links. Verify through an official channel."
            )
            RiskLevel.LOW -> Triple(
                "🟢 LOW RISK", "#2E7D32",
                "No obvious scam patterns found. Stay careful anyway."
            )
        }

        val time = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            .format(Date(saved.timeMillis))

        tvRiskLevel.text = levelText
        tvRiskLevel.setTextColor(Color.parseColor(colorHex))
        tvRiskScore.text = "Risk score: ${saved.score} / 100  |  $time"

        tvWarningReason.text = if (saved.reasons.isEmpty()) {
            "No scam patterns found."
        } else {
            saved.reasons.joinToString("\n") { "• $it" }
        }
        tvAdvice.text = advice
    }
}