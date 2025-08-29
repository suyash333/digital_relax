
package com.golearnsap.digitalrelax

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.golearnsap.digitalrelax.ui.theme.DigitalRelaxTheme

enum class Screen {
    Main,
    About
}

class MainActivity : ComponentActivity() {
    private var countDownTimer: CountDownTimer? = null
    private var soundPool: SoundPool? = null
    private var dingSoundId: Int = 0
    private var isSoundLoaded = false

    private lateinit var prefs: SharedPreferences
    private val PREFS_NAME = "DigitalRelaxPrefs"

    // To manage system controls during break
    private fun updateBreakSystemControls(context: Context, isBreakStarting: Boolean) {
        val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            if (isBreakStarting) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            } else {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }

        if (isBreakStarting) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    startLockTask() // Pin the screen
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Screen pinning could not be started.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    stopLockTask() // Unpin the screen
                }
            } catch (e: Exception) {
                // Log or handle exception if stopLockTask fails
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == dingSoundId) {
                isSoundLoaded = true
            }
        }
        dingSoundId = soundPool?.load(this, R.raw.ding, 1) ?: 0

        enableEdgeToEdge()
        setContent {
            DigitalRelaxTheme {
                var currentScreen by rememberSaveable { mutableStateOf(Screen.Main) }
                var inBreak by rememberSaveable { mutableStateOf(false) }
                var remainingTime by rememberSaveable { mutableLongStateOf(0L) }
                var selectedDurationMinutes by rememberSaveable { mutableStateOf("5") }
                var targetEndTimeMillis by rememberSaveable(key = "targetEndTimeMillis") { mutableLongStateOf(0L) }
                val context = LocalContext.current

                LaunchedEffect(Unit, targetEndTimeMillis) {
                    if (targetEndTimeMillis > 0) {
                        val currentTime = System.currentTimeMillis()
                        val newRemainingMillis = targetEndTimeMillis - currentTime
                        if (newRemainingMillis > 0) {
                            inBreak = true // Ensure break screen is shown
                            remainingTime = newRemainingMillis / 1000L
                            startTimer(
                                durationMillis = newRemainingMillis,
                                onTick = { millisUntilFinished -> remainingTime = millisUntilFinished / 1000 },
                                onFinish = {
                                    updateBreakSystemControls(context, false)
                                    playDingSound()
                                    showCompletionToast()
                                    inBreak = false
                                    remainingTime = 0L
                                    targetEndTimeMillis = 0L
                                }
                            )
                            // Re-apply system controls if break is restored
                            if (!checkNotificationPolicyAccess(context)) {
                                requestNotificationPolicyAccess(context)
                            } else {
                                updateBreakSystemControls(context, true)
                            }
                        } else { // Break time has passed while inactive
                            if (inBreak) { // Only if a break was considered active
                                updateBreakSystemControls(context, false)
                                playDingSound()
                                showCompletionToast()
                            }
                            inBreak = false
                            remainingTime = 0L
                            targetEndTimeMillis = 0L
                        }
                    }
                }

                if (inBreak) {
                    DigitalBreakScreen(
                        modifier = Modifier.fillMaxSize(),
                        timeRemaining = remainingTime
                    )
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        when (currentScreen) {
                            Screen.Main -> {
                                Column(
                                    modifier = Modifier
                                        .padding(innerPadding)
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Select break duration:", style = MaterialTheme.typography.titleMedium)
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                                updateBreakSystemControls(context, true)
                                                inBreak = true
                                                targetEndTimeMillis = System.currentTimeMillis() + duration
                                                remainingTime = duration / 1000L // Initial display
                                                startTimer(
                                                    durationMillis = duration,
                                                    onTick = { millisUntilFinished -> remainingTime = millisUntilFinished / 1000 },
                                                    onFinish = {
                                                        updateBreakSystemControls(context, false)
                                                        playDingSound()
                                                        showCompletionToast()
                                                        inBreak = false
                                                        remainingTime = 0L
                                                        targetEndTimeMillis = 0L
                                                    }
                                                )
                                            } else {
                                                requestNotificationPolicyAccess(context)
                                            }
                                        } else {
                                            Toast.makeText(context, "Please enter a valid duration.", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Text("Start Break")
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = { currentScreen = Screen.About }) {
                                        Text("About App")
                                    }
                                }
                            }
                            Screen.About -> {
                                AboutAppScreen(
                                    modifier = Modifier
                                        .padding(innerPadding)
                                        .fillMaxSize() // Ensure AboutAppScreen can fill if needed
                                        .verticalScroll(rememberScrollState()),
                                    onBack = { currentScreen = Screen.Main }
                                )
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
        runOnUiThread {
            Toast.makeText(this, "Hurray! You are successfully digitally relaxed.", Toast.LENGTH_LONG).show()
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

    // setDndMode is now part of updateBreakSystemControls
    // private fun setDndMode(context: Context, enabled: Boolean) { ... }

    private fun startTimer(durationMillis: Long, onTick: (Long) -> Unit, onFinish: () -> Unit) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                onTick(millisUntilFinished)
            }
            override fun onFinish() {
                onFinish()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        soundPool?.release()
        soundPool = null

        // Best effort to clean up system states
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                stopLockTask()
            }
        } catch (e: Exception) { /* Silently ignore */ }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Reset DND if it was on (check current state to avoid unnecessary calls)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted &&
            notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }
}

@Composable
fun AboutAppScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(
        modifier = modifier // Apply the passed-in modifier (includes padding & scroll)
            .padding(16.dp), // Additional content padding
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Digital Relax", style = MaterialTheme.typography.headlineSmall)
        Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text("This app helps you take short breaks to relax.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) {
            Text("Back")
        }
    }
}

@Composable
fun DigitalBreakScreen(modifier: Modifier = Modifier, timeRemaining: Long) {
    Box(
        modifier = modifier // Will be Modifier.fillMaxSize() from the call site
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Select break duration:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { }) { Text("1 min") }
                Button(onClick = { }) { Text("5 min") }
                Button(onClick = { }) { Text("10 min") }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = "5",
                onValueChange = { },
                label = { Text("Custom duration (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { }) { Text("Start Break") }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { }) { Text("About App") }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BreakScreenPreview() {
    DigitalRelaxTheme {
        DigitalBreakScreen(modifier = Modifier.fillMaxSize(), timeRemaining = 299L)
    }
}

@Preview(showBackground = true)
@Composable
fun AboutAppScreenPreview() {
    DigitalRelaxTheme {
        AboutAppScreen(onBack = {})
    }
}
