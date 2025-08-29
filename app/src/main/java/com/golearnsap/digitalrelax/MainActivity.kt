
package com.golearnsap.digitalrelax

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import com.golearnsap.digitalrelax.ui.theme.DigitalRelaxTheme

class MainActivity : ComponentActivity() {
    private var countDownTimer: CountDownTimer? = null

    private var soundPool: SoundPool? = null
    private var dingSoundId: Int = 0
    private var isSoundLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == dingSoundId) { // 0 indicates success
                isSoundLoaded = true
            }
        }
        // Replace R.raw.ding if your sound file has a different name
        dingSoundId = soundPool?.load(this, R.raw.ding, 1) ?: 0

        enableEdgeToEdge()
        setContent {
            DigitalRelaxTheme {
                var inBreak by remember { mutableStateOf(false) }
                var remainingTime by remember { mutableLongStateOf(0L) }
                var selectedDurationMinutes by rememberSaveable { mutableStateOf("5") }
                val context = LocalContext.current

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (inBreak) {
                        DigitalBreakScreen(timeRemaining = remainingTime)
                    } else {
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Select break duration:", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(onClick = { selectedDurationMinutes = "1" }) { Text("1 min") }
                                Button(onClick = { selectedDurationMinutes = "5" }) { Text("5 min") }
                                Button(onClick = { selectedDurationMinutes = "10" }) { Text("10 min") }
                            }
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = selectedDurationMinutes,
                                onValueChange = { selectedDurationMinutes = it },
                                label = { Text("Custom duration (minutes)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = {
                                val duration = selectedDurationMinutes.toLongOrNull()?.times(60000L)
                                if (duration != null && duration > 0) {
                                    if (checkNotificationPolicyAccess(context)) {
                                        setDndMode(context, true)
                                        inBreak = true
                                        startTimer(
                                            durationMillis = duration,
                                            onTick = { millisUntilFinished -> remainingTime = millisUntilFinished / 1000 },
                                            onFinish = {
                                                setDndMode(context, false)
                                                inBreak = false
                                                remainingTime = 0
                                                // Play sound and show toast on timer finish
                                                playDingSound()
                                                showCompletionToast()
                                            }
                                        )
                                    } else {
                                        requestNotificationPolicyAccess(context)
                                    }
                                }
                            }) {
                                Text("Start Break")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun playDingSound() {
        if (isSoundLoaded && dingSoundId != 0) {
            soundPool?.play(dingSoundId, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }

    private fun showCompletionToast() {
        runOnUiThread { // Ensure Toast is shown on the main thread
            Toast.makeText(this, "Hurray you are sucessfully digitally relaxed", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkNotificationPolicyAccess(context: Context): Boolean {
        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return notificationManager.isNotificationPolicyAccessGranted
    }

    private fun requestNotificationPolicyAccess(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        context.startActivity(intent)
    }

    private fun setDndMode(context: Context, enabled: Boolean) {
        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            if (enabled) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            } else {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }
    }

    private fun startTimer(durationMillis: Long, onTick: (Long) -> Unit, onFinish: () -> Unit) {
        countDownTimer?.cancel() // Cancel any existing timer
        countDownTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                onTick(millisUntilFinished)
            }

            override fun onFinish() {
                onFinish() // This will execute the lambda passed from the Composable
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        soundPool?.release()
        soundPool = null
    }
}

@Composable
fun DigitalBreakScreen(timeRemaining: Long) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Time remaining: ${timeRemaining}s",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    DigitalRelaxTheme {
        var selectedDurationMinutes by rememberSaveable { mutableStateOf("5") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Select break duration:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { selectedDurationMinutes = "1" }) { Text("1 min") }
                Button(onClick = { selectedDurationMinutes = "5" }) { Text("5 min") }
                Button(onClick = { selectedDurationMinutes = "10" }) { Text("10 min") }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = selectedDurationMinutes,
                onValueChange = { selectedDurationMinutes = it },
                label = { Text("Custom duration (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { /* Preview doesn't handle DND or timers */ }) {
                Text("Start Break")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BreakScreenPreview() {
    DigitalRelaxTheme {
        DigitalBreakScreen(timeRemaining = 299L)
    }
}
