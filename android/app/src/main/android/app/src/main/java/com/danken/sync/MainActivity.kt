package com.danken.sync

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }

        val heading = TextView(this).apply {
            text = "DANKEN SYNC"
            textSize = 28f
        }

        status = TextView(this).apply {
            text = "Media access has not been checked."
            textSize = 16f
            setPadding(0, 30, 0, 30)
        }

        val permissionButton = Button(this).apply {
            text = "Enable Media Access"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                )
            }
        }

        val checkButton = Button(this).apply {
            text = "Check Active Music"
            setOnClickListener {
                checkActiveMusic()
            }
        }

        layout.addView(heading)
        layout.addView(status)
        layout.addView(permissionButton)
        layout.addView(checkButton)

        setContentView(layout)
    }

    private fun checkActiveMusic() {
        try {
            val manager = getSystemService(
                MEDIA_SESSION_SERVICE
            ) as MediaSessionManager

            val sessions = manager.getActiveSessions(
                ComponentName(this, MusicListener::class.java)
            )

            if (sessions.isEmpty()) {
                status.text = "No active media sessions found. Open YouTube Music and try again."
                return
            }

            val results = sessions.map { controller ->
                val title = controller.metadata?.getString(
                    "android.media.metadata.TITLE"
                ) ?: "Unknown track"

                "${controller.packageName}: $title"
            }

            status.text = results.joinToString("\n\n")
        } catch (e: SecurityException) {
            status.text = "Media access is not enabled yet. Tap Enable Media Access."
        } catch (e: Exception) {
            status.text = "Could not check media sessions: ${e.message}"
        }
    }
}
