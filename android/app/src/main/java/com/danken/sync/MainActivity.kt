package com.danken.sync

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 60, 40, 40)

        val title = TextView(this)
        title.text = "DANKEN SYNC"
        title.textSize = 28f

        val message = TextView(this)
        message.text = "Your two-phone music sync project."
        message.textSize = 18f

        val button = Button(this)
        button.text = "Enable Media Access"

        button.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            )
        }

        layout.addView(title)
        layout.addView(message)
        layout.addView(button)

        setContentView(layout)
    }
}
