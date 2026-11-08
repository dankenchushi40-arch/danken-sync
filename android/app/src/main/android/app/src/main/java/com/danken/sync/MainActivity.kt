package com.danken.sync

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class MainActivity : Activity() {

    private val client = OkHttpClient()
    private var socket: WebSocket? = null
    private var player: MediaPlayer? = null
    private var isPrepared = false
    private var isMain = false
    private var pendingAction: String? = null
    private var pendingPosition = 0

    private lateinit var roomInput: EditText
    private lateinit var status: TextView
    private lateinit var songName: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var playButton: Button
    private lateinit var pauseButton: Button

    companion object {
        private const val PICK_AUDIO = 100
        private const val SERVER =
            "wss://danken-sync.dankenchushi40.workers.dev/ws"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 45, 32, 32)

        fun addText(value: String, size: Float = 18f) {
            val text = TextView(this)
            text.text = value
            text.textSize = size
            layout.addView(text)
        }

        addText("DANKEN SYNC", 28f)
        addText("Two-phone music player")

        roomInput = EditText(this)
        roomInput.hint = "Enter 6-character room code"
        roomInput.isSingleLine = true
        layout.addView(roomInput)

        val mainButton = Button(this)
        mainButton.text = "Connect as MAIN"
        mainButton.setOnClickListener { connect(true) }
        layout.addView(mainButton)

        val guestButton = Button(this)
        guestButton.text = "Connect as GUEST"
        guestButton.setOnClickListener { connect(false) }
        layout.addView(guestButton)

        status = TextView(this)
        status.text = "Not connected"
        layout.addView(status)

        val chooseButton = Button(this)
        chooseButton.text = "Choose Music File"
        chooseButton.setOnClickListener { chooseAudio() }
        layout.addView(chooseButton)

        songName = TextView(this)
        songName.text = "No music selected"
        layout.addView(songName)

        seekBar = SeekBar(this)
        layout.addView(seekBar)

        playButton = Button(this)
        playButton.text = "PLAY"
        playButton.setOnClickListener { control("play") }
        layout.addView(playButton)

        pauseButton = Button(this)
        pauseButton.text = "PAUSE"
        pauseButton.setOnClickListener { control("pause") }
        layout.addView(pauseButton)

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    bar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {}

                override fun onStartTrackingTouch(bar: SeekBar?) {}

                override fun onStopTrackingTouch(bar: SeekBar?) {
                    val position = bar?.progress ?: 0
                    player?.seekTo(position)

                    if (isMain) {
                        sendCommand("seek", position)
                    }
                }
            }
        )

        setContentView(layout)
    }

    private fun connect(main: Boolean) {
        val room = roomInput.text.toString().trim().uppercase()

        if (!room.matches(Regex("[A-Z0-9]{6}"))) {
            status.text = "Enter a valid 6-character room code."
            return
        }

        isMain = main
        socket?.close(1000, "Reconnecting")

        val role = if (main) "main" else "guest"
        val request = Request.Builder()
            .url("$SERVER?room=$room&role=$role")
            .build()

        status.text = "Connecting..."

        socket = client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    runOnUiThread {
                        status.text = "Connected as $role"
                        playButton.isEnabled = isMain
                        pauseButton.isEnabled = isMain
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val data = JSONObject(text)

                        if (data.optString("type") == "command" && !isMain) {
                            val action = data.optString("action")
                            val position = data.optInt("position", 0)

                            runOnUiThread {
                                receiveCommand(action, position)
                            }
                        }

                        if (data.optString("type") == "error") {
                            runOnUiThread {
                                status.text = data.optString("message")
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?
                ) {
                    runOnUiThread {
                        status.text = "Connection failed: ${t.message}"
                    }
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    runOnUiThread {
                        status.text = "Disconnected"
                    }
                }
            }
        )
    }

    private fun chooseAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "audio/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        startActivityForResult(intent, PICK_AUDIO)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_AUDIO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return

            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }

            loadMusic(uri)
        }
    }

    private fun loadMusic(uri: Uri) {
        try {
            player?.release()
            isPrepared = false

            val newPlayer = MediaPlayer()
            player = newPlayer

            newPlayer.setDataSource(this, uri)

            newPlayer.setOnPreparedListener {
                isPrepared = true
                seekBar.max = it.duration
                songName.text = "Music ready"
                status.text = "Music loaded successfully"

                pendingAction?.let { action ->
                    receiveCommand(action, pendingPosition)
                    pendingAction = null
                }
            }

            newPlayer.setOnErrorListener { _, _, _ ->
                status.text = "Could not play this audio file."
                true
            }

            newPlayer.prepareAsync()
            songName.text = "Loading music..."
        } catch (e: Exception) {
            status.text = "Could not load music: ${e.message}"
        }
    }

    private fun control(action: String) {
        if (!isMain) return

        val position = player?.currentPosition ?: 0

        if (action == "play" && isPrepared) {
            player?.start()
        } else if (action == "pause" && isPrepared) {
            player?.pause()
        }

        sendCommand(action, position)
    }

    private fun sendCommand(action: String, position: Int) {
        val message = JSONObject()
            .put("action", action)
            .put("position", position)

        socket?.send(message.toString())
    }

    private fun receiveCommand(action: String, position: Int) {
        if (!isPrepared) {
            pendingAction = action
            pendingPosition = position
            status.text = "Waiting for music to load..."
            return
        }

        when (action) {
            "play" -> {
                player?.seekTo(position)
                player?.start()
                status.text = "Following main phone: PLAY"
            }

            "pause" -> {
                player?.seekTo(position)
                player?.pause()
                status.text = "Following main phone: PAUSE"
            }

            "seek" -> {
                player?.seekTo(position)
                status.text = "Playback position synchronised"
            }
        }
    }

    override fun onDestroy() {
        socket?.close(1000, "App closed")
        player?.release()
        client.dispatcher.executorService.shutdown()
        super.onDestroy()
    }
}
