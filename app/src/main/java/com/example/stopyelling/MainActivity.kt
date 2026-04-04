package com.example.stopyelling

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.stopyelling.ui.theme.StopYellingTheme

class MainActivity : ComponentActivity() {
    private var yellingService by mutableStateOf<YellingMonitorService?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as YellingMonitorService.LocalBinder
            yellingService = binder.getService()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            yellingService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Intent(this, YellingMonitorService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        enableEdgeToEdge()
        setContent {
            StopYellingTheme {
                StopYellingApp(yellingService)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }
}

@Composable
fun StopYellingApp(service: YellingMonitorService?) {
    val context = LocalContext.current
    var isMonitoring by remember { mutableStateOf(false) }
    var currentVolume by remember { mutableIntStateOf(0) }
    var isSpeaking by remember { mutableStateOf(false) }

    // Synchronize UI state with Service state
    LaunchedEffect(service) {
        service?.let { s ->
            s.onUpdate = {
                currentVolume = s.currentVolume
                isSpeaking = s.isSpeaking
                isMonitoring = s.isMonitoring
            }
            // Initial sync when service connects
            currentVolume = s.currentVolume
            isSpeaking = s.isSpeaking
            isMonitoring = s.isMonitoring
        }
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
        if (!hasPermission) {
            Toast.makeText(context, "Permissions are required for monitoring", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFFFF176)) // Yellow background
        ) {
            val threshold = 18000
            val isLoud = isMonitoring && currentVolume > threshold
            val volumeFactor = (currentVolume.toFloat() / 32768f).coerceIn(0f, 1f)

            AngryStickFigures(
                isLoud = isLoud,
                volumeFactor = volumeFactor,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isLoud) Color.Red.copy(alpha = 0.3f) else Color.Transparent),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "What the Yell?",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                Text(
                    text = when {
                        isSpeaking -> "Speaking..."
                        isMonitoring -> "Monitoring Sound..."
                        else -> "Not Monitoring"
                    },
                    fontSize = 18.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isMonitoring && !isSpeaking) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .padding(horizontal = 48.dp)
                            .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(volumeFactor)
                                .fillMaxHeight()
                                .background(
                                    if (isLoud) Color.Red else Color.Green,
                                    RoundedCornerShape(20.dp)
                                )
                        )
                    }

                    if (isLoud) {
                        Text(
                            text = "LOUD CONTENT DETECTED",
                            color = Color.Red,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Button(
                    onClick = {
                        if (hasPermission) {
                            val intent = Intent(context, YellingMonitorService::class.java)
                            if (!isMonitoring) {
                                ContextCompat.startForegroundService(context, intent)
                                isMonitoring = true
                            } else {
                                context.stopService(intent)
                                isMonitoring = false
                                currentVolume = 0
                            }
                        } else {
                            val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            launcher.launch(permissions.toTypedArray())
                        }
                    },
                    modifier = Modifier.size(120.dp),
                    shape = RoundedCornerShape(60.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMonitoring) Color.DarkGray else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isMonitoring) "Stop" else "Start")
                }
            }
        }
    }
}

@Composable
fun AngryStickFigures(isLoud: Boolean, volumeFactor: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val strokeWidth = 10f

        val shakeAmount = if (isLoud) volumeFactor * 15f else 0f
        val randomX = (Math.random().toFloat() - 0.5f) * shakeAmount
        val randomY = (Math.random().toFloat() - 0.5f) * shakeAmount
        val figureColor = if (isLoud) Color(0xFFD32F2F) else Color.Black

        // Adult (Left)
        val adultX = width * 0.28f + randomX
        val adultY = height * 0.65f + randomY
        
        drawCircle(figureColor, 55f, Offset(adultX, adultY - 220f), style = Stroke(strokeWidth))
        // Eyebrows
        drawLine(figureColor, Offset(adultX - 35f, adultY - 240f), Offset(adultX - 10f, adultY - 232f), strokeWidth + 2)
        drawLine(figureColor, Offset(adultX + 10f, adultY - 232f), Offset(adultX + 35f, adultY - 240f), strokeWidth + 2)
        // Yelling Mouth
        drawCircle(figureColor, 18f, Offset(adultX, adultY - 185f), style = Stroke(strokeWidth))
        // Body
        drawLine(figureColor, Offset(adultX, adultY - 165f), Offset(adultX + 20f, adultY + 50f), strokeWidth)
        // Arms
        drawLine(figureColor, Offset(adultX + 10f, adultY - 110f), Offset(adultX + 90f, adultY - 170f), strokeWidth)
        drawLine(figureColor, Offset(adultX + 10f, adultY - 110f), Offset(adultX - 70f, adultY - 60f), strokeWidth)
        // Legs
        drawLine(figureColor, Offset(adultX + 20f, adultY + 50f), Offset(adultX - 30f, adultY + 180f), strokeWidth)
        drawLine(figureColor, Offset(adultX + 20f, adultY + 50f), Offset(adultX + 70f, adultY + 180f), strokeWidth)

        // Child (Right)
        val childX = width * 0.72f - randomX
        val childY = height * 0.72f - randomY
        
        drawCircle(figureColor, 40f, Offset(childX, childY - 150f), style = Stroke(strokeWidth))
        // Eyebrows
        drawLine(figureColor, Offset(childX - 25f, childY - 168f), Offset(childX - 8f, childY - 162f), strokeWidth)
        drawLine(figureColor, Offset(childX + 8f, childY - 162f), Offset(childX + 25f, childY - 168f), strokeWidth)
        // Yelling Mouth
        drawCircle(figureColor, 12f, Offset(childX, childY - 130f), style = Stroke(strokeWidth))
        // Body
        drawLine(figureColor, Offset(childX, childY - 110f), Offset(childX - 15f, childY + 30f), strokeWidth)
        // Arms
        drawLine(figureColor, Offset(childX - 5f, childY - 70f), Offset(childX - 70f, childY - 120f), strokeWidth)
        drawLine(figureColor, Offset(childX - 5f, childY - 70f), Offset(childX + 60f, childY - 100f), strokeWidth)
        // Legs
        drawLine(figureColor, Offset(childX - 15f, childY + 30f), Offset(childX - 50f, childY + 120f), strokeWidth)
        drawLine(figureColor, Offset(childX - 15f, childY + 30f), Offset(childX + 20f, childY + 120f), strokeWidth)

        // Yelling Waves
        if (isLoud) {
            val waveAlpha = (volumeFactor * 0.8f).coerceIn(0.2f, 0.8f)
            for (i in 1..3) {
                val offset = i * 30f * volumeFactor
                drawArc(figureColor.copy(alpha = waveAlpha / i), -45f, 90f, false, Offset(adultX + 60f + offset, adultY - 230f), size = androidx.compose.ui.geometry.Size(80f, 80f), style = Stroke(4f))
                drawArc(figureColor.copy(alpha = waveAlpha / i), 135f, 90f, false, Offset(childX - 140f - offset, childY - 180f), size = androidx.compose.ui.geometry.Size(60f, 60f), style = Stroke(3f))
            }
        }
    }
}
