package com.example.stopyelling

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isSpeakingState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeakingState.value = true
            }
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "FinalRepeat") {
                    isSpeakingState.value = false
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeakingState.value = false
            }
        })

        enableEdgeToEdge()
        setContent {
            StopYellingTheme {
                StopYellingApp(
                    isSpeaking = isSpeakingState.value,
                    onTriggerAlert = { speakThreeTimes() }
                )
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    private fun speakThreeTimes() {
        tts?.speak("Who is yelling?", TextToSpeech.QUEUE_FLUSH, null, "Repeat1")
        tts?.speak("Who is yelling?", TextToSpeech.QUEUE_ADD, null, "Repeat2")
        tts?.speak("Who is yelling?", TextToSpeech.QUEUE_ADD, null, "FinalRepeat")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun StopYellingApp(isSpeaking: Boolean, onTriggerAlert: () -> Unit) {
    val context = LocalContext.current
    var isMonitoring by remember { mutableStateOf(false) }
    var currentVolume by remember { mutableIntStateOf(0) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val threshold = 18000 // Threshold for yelling detection

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Audio recording permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFFFF176)) // Yellow background
        ) {
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
                    text = "Stop Yelling Monitor",
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
                            isMonitoring = !isMonitoring
                        } else {
                            launcher.launch(Manifest.permission.RECORD_AUDIO)
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

    LaunchedEffect(isMonitoring, isSpeaking) {
        if (isMonitoring && hasPermission && !isSpeaking) {
            scope.launch(Dispatchers.IO) {
                val bufferSize = AudioRecord.getMinBufferSize(
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val internalBufferSize = (bufferSize * 4).coerceAtLeast(8820)
                val audioRecord = try {
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        44100,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        internalBufferSize
                    )
                } catch (e: SecurityException) {
                    null
                }

                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.startRecording()
                    val buffer = ShortArray(bufferSize)
                    var accumulatedLoudTime = 0L
                    var quietTime = 0L

                    while (isMonitoring && !isSpeaking) {
                        val readCount = audioRecord.read(buffer, 0, bufferSize)
                        if (readCount > 0) {
                            var max = 0
                            for (i in 0 until readCount) {
                                val absVal = abs(buffer[i].toInt())
                                if (absVal > max) max = absVal
                            }
                            currentVolume = max

                            val audioDurationMs = (readCount.toLong() * 1000) / 44100
                            if (max > threshold) {
                                accumulatedLoudTime += audioDurationMs
                                quietTime = 0L
                                if (accumulatedLoudTime >= 4000) {
                                    triggerPhysicalAlert(context)
                                    onTriggerAlert()
                                    accumulatedLoudTime = 0
                                }
                            } else {
                                quietTime += audioDurationMs
                                if (quietTime > 3000) {
                                    accumulatedLoudTime = 0
                                }
                            }
                        }
                        delay(50)
                    }
                    audioRecord.stop()
                    audioRecord.release()
                }
            }
        } else {
            currentVolume = 0
        }
    }
}

@Composable
fun AngryStickFigures(isLoud: Boolean, volumeFactor: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val strokeWidth = 10f

        // Dynamic shaking and coloring
        val shakeAmount = if (isLoud) volumeFactor * 15f else 0f
        val randomX = (Math.random().toFloat() - 0.5f) * shakeAmount
        val randomY = (Math.random().toFloat() - 0.5f) * shakeAmount
        val figureColor = if (isLoud) Color(0xFFD32F2F) else Color.Black

        // Adult (Left) - Leaning in
        val adultX = width * 0.28f + randomX
        val adultY = height * 0.65f + randomY
        
        // Head
        drawCircle(figureColor, 55f, Offset(adultX, adultY - 220f), style = Stroke(strokeWidth))
        // Eyebrows (Less arched, more focused anger)
        drawLine(figureColor, Offset(adultX - 35f, adultY - 240f), Offset(adultX - 10f, adultY - 232f), strokeWidth + 2)
        drawLine(figureColor, Offset(adultX + 10f, adultY - 232f), Offset(adultX + 35f, adultY - 240f), strokeWidth + 2)
        // Yelling Mouth
        drawCircle(figureColor, 18f, Offset(adultX, adultY - 185f), style = Stroke(strokeWidth))
        
        // Body (Leaning forward)
        drawLine(figureColor, Offset(adultX, adultY - 165f), Offset(adultX + 20f, adultY + 50f), strokeWidth)
        // Arms (Pointing/Gesturing)
        drawLine(figureColor, Offset(adultX + 10f, adultY - 110f), Offset(adultX + 90f, adultY - 170f), strokeWidth)
        drawLine(figureColor, Offset(adultX + 10f, adultY - 110f), Offset(adultX - 70f, adultY - 60f), strokeWidth)
        // Legs
        drawLine(figureColor, Offset(adultX + 20f, adultY + 50f), Offset(adultX - 30f, adultY + 180f), strokeWidth)
        drawLine(figureColor, Offset(adultX + 20f, adultY + 50f), Offset(adultX + 70f, adultY + 180f), strokeWidth)

        // Child (Right) - Defensive yelling
        val childX = width * 0.72f - randomX
        val childY = height * 0.72f - randomY
        
        // Head
        drawCircle(figureColor, 40f, Offset(childX, childY - 150f), style = Stroke(strokeWidth))
        // Eyebrows (Slanted but flatter)
        drawLine(figureColor, Offset(childX - 25f, childY - 168f), Offset(childX - 8f, childY - 162f), strokeWidth)
        drawLine(figureColor, Offset(childX + 8f, childY - 162f), Offset(childX + 25f, childY - 168f), strokeWidth)
        // Yelling Mouth
        drawCircle(figureColor, 12f, Offset(childX, childY - 130f), style = Stroke(strokeWidth))
        
        // Body
        drawLine(figureColor, Offset(childX, childY - 110f), Offset(childX - 15f, childY + 30f), strokeWidth)
        // Arms (Hands up)
        drawLine(figureColor, Offset(childX - 5f, childY - 70f), Offset(childX - 70f, childY - 120f), strokeWidth)
        drawLine(figureColor, Offset(childX - 5f, childY - 70f), Offset(childX + 60f, childY - 100f), strokeWidth)
        // Legs
        drawLine(figureColor, Offset(childX - 15f, childY + 30f), Offset(childX - 50f, childY + 120f), strokeWidth)
        drawLine(figureColor, Offset(childX - 15f, childY + 30f), Offset(childX + 20f, childY + 120f), strokeWidth)

        // Visual Yelling Waves
        if (isLoud) {
            val waveAlpha = (volumeFactor * 0.8f).coerceIn(0.2f, 0.8f)
            for (i in 1..3) {
                val offset = i * 30f * volumeFactor
                drawArc(
                    color = figureColor.copy(alpha = waveAlpha / i),
                    startAngle = -45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(adultX + 60f + offset, adultY - 230f),
                    size = androidx.compose.ui.geometry.Size(80f, 80f),
                    style = Stroke(4f)
                )
                drawArc(
                    color = figureColor.copy(alpha = waveAlpha / i),
                    startAngle = 135f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(childX - 140f - offset, childY - 180f),
                    size = androidx.compose.ui.geometry.Size(60f, 60f),
                    style = Stroke(3f)
                )
            }
        }
    }
}

private fun triggerPhysicalAlert(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (vibrator.hasVibrator()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(2000, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(2000)
        }
    }
    
    try {
        val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
    } catch (e: Exception) {}
}
