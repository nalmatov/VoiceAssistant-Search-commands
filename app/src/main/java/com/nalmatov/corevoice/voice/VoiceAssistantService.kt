package com.nalmatov.corevoice.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nalmatov.corevoice.MainActivity
import com.nalmatov.corevoice.car.CarManager

class VoiceAssistantService : Service() {

    companion object {
        private const val TAG = "CoreVoiceService"
        private const val CHANNEL_ID = "corevoice_assistant"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START =
            "com.nalmatov.corevoice.START"

        const val ACTION_STOP =
            "com.nalmatov.corevoice.STOP"
    }

    private lateinit var voskManager: VoskSpeechManager

    private var started = false

    override fun onCreate() {
        super.onCreate()

        voskManager = VoskSpeechManager(applicationContext)

        createNotificationChannel()

        Log.i(TAG, "VoiceAssistantService создан")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {
            ACTION_STOP -> {
                stopAssistant()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                startForegroundNotification()
                startAssistant()
            }

            else -> {
                // Не запускаем микрофон без явной команды.
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startAssistant() {
        if (started) return

        started = true

        Log.i(TAG, "Загрузка Vosk...")

        voskManager.init(
            onReady = {
                if (started) {
                    Log.i(TAG, "Vosk готов. Запуск прослушивания")
                    startListening()
                }
            },

            onError = { error ->
                Log.e(TAG, "Ошибка Vosk: $error")
                stopAssistant()
            }
        )
    }

    private fun startListening() {
        if (!started || voskManager.isListening()) return

        voskManager.startListening(
            onPartialResult = { text ->
                Log.d(TAG, "Partial: $text")
            },

            onFinalResult = { text ->
                Log.i(TAG, "Recognized: $text")

//                if (text == "включи кондиционер" || text == "включить кондиционер") {
//                    CarManager.setPropertyBoolean(356517125, 240, true)
//                } else if (text == "выключи кондиционер") {
//                    CarManager.setPropertyBoolean(356517125, 15, false)
//                } else if (text == "открой ютуб") {
//                    Log.i("CarManager", "yotube shoude be open")
////                    openApp("app.youtube.revanced")
//                } else if ("открой окно пассажир" in text) {
//                    CarManager.setProperty(322964416, 64, 100)
//                } else if ("закрой окно пассажира" in text) {
//                    CarManager.setProperty(322964416, 64, 0)
//                }  else if ("открой окно водителя" in text || "открой окно" in text) {
//                    CarManager.setProperty(322964416, 16, 100)
//                } else if ("закрой окно" in text || "закрой окно" in text) {
//                    CarManager.setProperty(322964416, 16, 0)
//                }else if ("открой шторку" in text || "открой водительское люк" in text) {
//                    CarManager.setProperty(557903927, 0, 203)
//                } else if ("закрой шторку" in text || "закрой водительское люк" in text) {
//                    CarManager.setProperty(557903927, 0, 103)
//                } else if ("закрой люк" in text || "закрой водительское люк" in text) {
//                    CarManager.setProperty(557903927, 0, 1)
//                } else if ("открой люк" in text || "закрой водительское люк" in text) {
//                    CarManager.setProperty(557903927, 0, 101)
//                } else if ("открой багажник" in text) {
//                    CarManager.setProperty(692073902, 4, 2)
//                }

                // Позже:
                // wakeWordDetector.process(text)
                // voiceCommandParser.parse(text)
            }
        )
    }

    private fun stopAssistant() {
        started = false

        if (::voskManager.isInitialized) {
            voskManager.stopListening()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CoreVoice Assistant",
                NotificationManager.IMPORTANCE_LOW
            )

            channel.description =
                "Фоновое распознавание голосовых команд"

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CoreVoice")
            .setContentText("Голосовой ассистент активен")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        started = false

        if (::voskManager.isInitialized) {
            voskManager.stopListening()
        }

        Log.i(TAG, "VoiceAssistantService остановлен")

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}