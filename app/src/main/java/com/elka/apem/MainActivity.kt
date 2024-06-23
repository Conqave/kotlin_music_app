package com.elka.apem

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elka.apem.ui.theme.ApemTheme
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val mp3Files = mutableListOf<Pair<Int, String>>(
        Pair(R.raw.s1, "Sample 1"),
        Pair(R.raw.s2, "Sample 2")
    )
    private val uriFiles = mutableStateListOf<Pair<Uri, String>>()

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val title = cursor.getString(cursor.getColumnIndexOrThrow("_display_name"))
                        uriFiles.add(Pair(uri, title))
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ApemTheme {
                val coroutineScope = rememberCoroutineScope()
                val position = remember { mutableStateOf(0) }
                val duration = remember { mutableStateOf(0) }
                val isPlaying = remember { mutableStateOf(false) }
                var selectedFile by remember { mutableStateOf(mp3Files.firstOrNull()?.first) }
                var selectedUri by remember { mutableStateOf<Uri?>(null) }

                LaunchedEffect(selectedFile, selectedUri) {
                    mediaPlayer?.release()
                    if (selectedFile != null) {
                        mediaPlayer = MediaPlayer.create(this@MainActivity, selectedFile!!).apply {
                            setOnPreparedListener { player ->
                                duration.value = player.duration
                                player.start()
                                isPlaying.value = true
                            }
                            setOnCompletionListener { player ->
                                player.seekTo(0)
                                isPlaying.value = false
                            }
                        }
                    } else if (selectedUri != null) {
                        mediaPlayer = MediaPlayer().apply {
                            try {
                                setDataSource(this@MainActivity, selectedUri!!)
                                prepare()
                                setOnPreparedListener { player ->
                                    duration.value = player.duration
                                    player.start()
                                    isPlaying.value = true
                                }
                                setOnCompletionListener { player ->
                                    player.seekTo(0)
                                    isPlaying.value = false
                                }
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                    }

                    handler.postDelayed(object : Runnable {
                        override fun run() {
                            mediaPlayer?.let { player ->
                                if (player.isPlaying) {
                                    position.value = player.currentPosition
                                }
                            }
                            handler.postDelayed(this, 1000)
                        }
                    }, 1000)
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Greeting(
                            name = "Android",
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "audio/*"
                                }
                                openDocumentLauncher.launch(intent)
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(text = "Load MP3 from device", fontSize = 16.sp)
                        }
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (mp3Files.isEmpty() && uriFiles.isEmpty()) {
                                item {
                                    Text(
                                        text = "BRAK",
                                        fontSize = 16.sp,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    )
                                }
                            } else {
                                items(mp3Files) { file ->
                                    FileItem(
                                        fileName = file.second,
                                        onClick = {
                                            selectedFile = file.first
                                            selectedUri = null
                                        }
                                    )
                                }
                                items(uriFiles) { file ->
                                    FileItem(
                                        fileName = file.second,
                                        onClick = {
                                            selectedUri = file.first
                                            selectedFile = null
                                        },
                                        onRemove = {
                                            uriFiles.remove(file)
                                        }
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(onClick = { playPauseMp3(isPlaying) }) {
                                Text(text = if (isPlaying.value) "Pause" else "Play")
                            }
                            Button(onClick = { rewind10Seconds() }) {
                                Text(text = "Back 10s")
                            }
                            Button(onClick = { forward10Seconds() }) {
                                Text(text = "Forward 10s")
                            }
                        }
                        Text(
                            text = "${formatTime(position.value)} / ${formatTime(duration.value)}",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Slider(
                            value = position.value.toFloat(),
                            onValueChange = { newPosition ->
                                mediaPlayer?.seekTo(newPosition.toInt())
                                position.value = newPosition.toInt()
                            },
                            valueRange = 0f..duration.value.toFloat(),
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        )
                    }
                }
            }
        }
    }

    private fun playPauseMp3(isPlaying: MutableState<Boolean>) {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying.value = false
            } else {
                it.start()
                isPlaying.value = true
            }
        }
    }

    private fun forward10Seconds() {
        mediaPlayer?.let {
            val newPosition = it.currentPosition + 10000
            if (newPosition < it.duration) {
                it.seekTo(newPosition)
            } else {
                it.seekTo(it.duration)
            }
        }
    }

    private fun rewind10Seconds() {
        mediaPlayer?.let {
            val newPosition = it.currentPosition - 10000
            if (newPosition > 0) {
                it.seekTo(newPosition)
            } else {
                it.seekTo(0)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        handler.removeCallbacksAndMessages(null)
    }

    private fun formatTime(milliseconds: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.toLong()) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
fun FileItem(fileName: String, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Text(text = fileName, fontSize = 16.sp)
        }
        onRemove?.let {
            Button(
                onClick = onRemove,
                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error),
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(text = "Remove", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ApemTheme {
        Greeting("Android")
    }
}
