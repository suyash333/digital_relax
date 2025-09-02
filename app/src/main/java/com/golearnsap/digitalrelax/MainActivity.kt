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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.golearnsap.digitalrelax.ui.theme.DigitalRelaxTheme
import kotlinx.coroutines.launch

enum class Screen(val title: String, val icon: ImageVector) {
    Main("Home", Icons.Filled.Home),
    About("About", Icons.Filled.Info)
}

class MainActivity : ComponentActivity() {
    private var countDownTimer: CountDownTimer? = null
    private var soundPool: SoundPool? = null
    private var dingSoundId: Int = 0
    private var isSoundLoaded = false

    private lateinit var prefs: SharedPreferences
    private val PREFS_NAME = "DigitalRelaxPrefs"
    private val KEY_APP_THEME = "app_theme"

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
                    startLockTask()
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
                    stopLockTask()
                }
            } catch (e: Exception) {
                // Log or handle exception
            }
        }
    }


    @OptIn(ExperimentalMaterial3Api::class)
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
            val savedTheme = prefs.getString(KEY_APP_THEME, "system") ?: "system"
            var currentThemeChoice by rememberSaveable { mutableStateOf(savedTheme) }

            val useDarkTheme = if (currentThemeChoice == "dark") {
                true
            } else {
                isSystemInDarkTheme()
            }

            DigitalRelaxTheme(darkTheme = useDarkTheme) {
                var currentScreen by rememberSaveable { mutableStateOf(Screen.Main) }
                var inBreak by rememberSaveable { mutableStateOf(false) }
                var remainingTime by rememberSaveable { mutableLongStateOf(0L) }
                var selectedDurationMinutes by rememberSaveable { mutableStateOf("5") }
                var targetEndTimeMillis by rememberSaveable(key = "targetEndTimeMillis") { mutableLongStateOf(0L) }
                val context = LocalContext.current

                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit, targetEndTimeMillis) {
                    if (targetEndTimeMillis > 0) {
                        val currentTime = System.currentTimeMillis()
                        val newRemainingMillis = targetEndTimeMillis - currentTime
                        if (newRemainingMillis > 0) {
                            inBreak = true
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
                            if (!checkNotificationPolicyAccess(context)) {
                                requestNotificationPolicyAccess(context)
                            } else {
                                updateBreakSystemControls(context, true)
                            }
                        } else {
                            if (inBreak) {
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

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppDrawer(
                            currentScreen = currentScreen,
                            onNavigate = { screen ->
                                currentScreen = screen
                                scope.launch { drawerState.close() }
                            },
                            isDarkModeForced = currentThemeChoice == "dark",
                            onForceDarkModeChanged = { isChecked ->
                                val newTheme = if (isChecked) "dark" else "system"
                                currentThemeChoice = newTheme
                                prefs.edit().putString(KEY_APP_THEME, newTheme).apply()
                            }
                        )
                    }
                ) {
                    if (inBreak) {
                        DigitalBreakScreen(
                            modifier = Modifier.fillMaxSize(),
                            timeRemaining = remainingTime
                        )
                    } else {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            topBar = {
                                TopAppBar(
                                    title = { Text(currentScreen.title) },
                                    navigationIcon = {
                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                            Icon(Icons.Filled.Menu, contentDescription = "Open navigation drawer")
                                        }
                                    }
                                )
                            }
                        ) { innerPadding ->
                            when (currentScreen) {
                                Screen.Main -> {
                                    Column(
                                        modifier = Modifier
                                            .padding(innerPadding)
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(16.dp),
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
                                                    remainingTime = duration / 1000L
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
                                        Spacer(Modifier.weight(1f)) // Pushes content towards top if Column not Centered
                                        // Theme settings UI removed from here
                                    }
                                }
                                Screen.About -> {
                                    AboutAppScreen(
                                        modifier = Modifier
                                            .padding(innerPadding)
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                    )
                                }
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

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                stopLockTask()
            }
        } catch (e: Exception) { /* Silently ignore */ }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted &&
            notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }
}

@Composable
fun AppDrawer(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    isDarkModeForced: Boolean,
    onForceDarkModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier) {
        Spacer(Modifier.height(12.dp))
        Screen.values().forEach { screen ->
            NavigationDrawerItem(
                icon = { Icon(screen.icon, contentDescription = null) },
                label = { Text(screen.title) },
                selected = currentScreen == screen,
                onClick = { onNavigate(screen) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .padding(horizontal = 8.dp), // Adjust padding to align with NavigationDrawerItems
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Always Use Dark Mode",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isDarkModeForced,
                onCheckedChange = onForceDarkModeChanged
            )
        }
    }
}

@Composable
fun AboutAppScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Digital Relax", style = MaterialTheme.typography.headlineSmall)
        Text("Version 1.2", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Text("This app helps you take short breaks to relax.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun DigitalBreakScreen(modifier: Modifier = Modifier, timeRemaining: Long) {
    Box(
        modifier = modifier
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Select break duration:", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { }) { Text("1 min") }
                Button(onClick = { }) { Text("5 min") }
                Button(onClick = { }) { Text("10 min") }
            }
            // Theme settings preview removed from here
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
        AboutAppScreen(Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun AppDrawerPreview() {
    DigitalRelaxTheme {
        AppDrawer(
            currentScreen = Screen.Main,
            onNavigate = {},
            isDarkModeForced = false,
            onForceDarkModeChanged = {}
        )
    }
}

