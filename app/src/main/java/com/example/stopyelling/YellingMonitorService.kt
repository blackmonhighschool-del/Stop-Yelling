package com.example.stopyelling

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.math.abs

class YellingMonitorService : Service(), TextToSpeech.OnInitListener {

    private val binder = LocalBinder()
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var tts: TextToSpeech? = null
    var isSpeaking = false
        private set
    var currentVolume = 0
        private set
    var isMonitoring = false
        private set

    private val CHANNEL_ID = "YellingMonitorChannel"
    private val NOTIFICATION_ID = 1

    var onUpdate: (() -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): YellingMonitorService = this@YellingMonitorService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                onUpdate?.invoke()
            }
            override fun onDone(utteranceId: String?) {
                if (utteranceId == "FinalRepeat") {
                    isSpeaking = false
                    onUpdate?.invoke()
                }
            }
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                onUpdate?.invoke()
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
        return START_STICKY
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        onUpdate?.invoke()

        job = scope.launch {
            val threshold = 18000
            val bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val internalBufferSize = (bufferSize * 4).coerceAtLeast(8820)
            
            if (ContextCompat.checkSelfPermission(this@YellingMonitorService, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                stopSelf()
                return@launch
            }

            val audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, internalBufferSize)

            if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord.startRecording()
                val buffer = ShortArray(bufferSize)
                var accumulatedLoudTime = 0L
                var quietTime = 0L

                while (isMonitoring) {
                    if (!isSpeaking) {
                        val readCount = audioRecord.read(buffer, 0, bufferSize)
                        if (readCount > 0) {
                            var max = 0
                            for (i in 0 until readCount) {
                                val absVal = abs(buffer[i].toInt())
                                if (absVal > max) max = absVal
                            }
                            currentVolume = max
                            onUpdate?.invoke()

                            val audioDurationMs = (readCount.toLong() * 1000) / 44100
                            if (max > threshold) {
                                accumulatedLoudTime += audioDurationMs
                                quietTime = 0L
                                if (accumulatedLoudTime >= 4000) {
                                    triggerAlerts()
                                    accumulatedLoudTime = 0
                                }
                            } else {
                                quietTime += audioDurationMs
                                if (quietTime > 3000) {
                                    accumulatedLoudTime = 0
                                }
                            }
                        }
                    } else {
                        currentVolume = 0
                        onUpdate?.invoke()
                        delay(100)
                    }
                    delay(50)
                }
                audioRecord.stop()
                audioRecord.release()
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        job?.cancel()
        currentVolume = 0
        onUpdate?.invoke()
    }

    private fun triggerAlerts() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
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

        speakThreeTimes()
    }

    private fun speakThreeTimes() {
        tts?.speak("Who is yelling?", TextToSpeech.QUEUE_FLUSH, null, "Repeat1")
        tts?.speak("Who is yelling?", TextToSpeech.QUEUE_ADD, null, "Repeat2")
        tts?.speak("Who is yelling?", TextToSpeech.QUEUE_ADD, null, "FinalRepeat")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Yelling Monitor Service Channel", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, YellingMonitorService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Yelling Monitor Active")
            .setContentText("Monitoring for loud noises...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        stopMonitoring()
        tts?.stop()
        tts?.shutdown()
        scope.cancel()
        super.onDestroy()
    }
}
