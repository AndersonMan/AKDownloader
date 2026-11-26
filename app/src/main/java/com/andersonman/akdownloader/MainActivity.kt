package com.andersonman.akdownloader

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.andersonman.akdownloader.ui.crash.CrashReporter
import com.andersonman.akdownloader.ui.navigation.AKDownloaderApp
import com.andersonman.akdownloader.ui.theme.AKDownloaderTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            requestNotificationPermissionIfNeeded()
            showCrashReportIfNeeded()
            enableEdgeToEdge()
            setContent {
                AKDownloaderTheme {
                    AKDownloaderApp()
                }
            }
        } catch (t: Throwable) {
            showSurvivalError(t)
        }
    }

    override fun onResume() {
        super.onResume()
        runCatching { CrashReporter.install(this) }
    }

    private fun showSurvivalError(t: Throwable) {
        try {
            val stack = android.util.Log.getStackTraceString(t).take(6000)
            CrashReporter.saveCrash(this, t)
            val messageView = TextView(this).apply {
                text = "AKDownloader n'a pas pu démarrer.\n\n" + stack
                setPadding(40, 40, 40, 40)
                setTextIsSelectable(true)
            }
            setContentView(ScrollView(this).apply { addView(messageView) })
        } catch (e: Throwable) {
            Toast.makeText(this, "Erreur démarrage : " + t.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun showCrashReportIfNeeded() {
        val crash = CrashReporter.readLastCrash(this) ?: return
        runCatching {
            val messageView = TextView(this).apply {
                text = crash.second
                setPadding(48, 24, 48, 24)
                setTextIsSelectable(true)
                movementMethod = android.text.method.ScrollingMovementMethod()
            }
            AlertDialog.Builder(this)
                .setTitle("Rapport de plantage — " + crash.first)
                .setView(messageView)
                .setPositiveButton("Copier") { _, _ ->
                    runCatching {
                        val clipboard = getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("crash", crash.second))
                        Toast.makeText(this, "Rapport copié — collez-le à l'assistant", Toast.LENGTH_LONG).show()
                    }
                    CrashReporter.deleteAllCrashReports(this)
                }
                .setNegativeButton("Fermer") { _, _ ->
                    CrashReporter.deleteAllCrashReports(this)
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1
    }
}
