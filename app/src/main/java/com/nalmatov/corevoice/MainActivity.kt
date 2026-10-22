package com.nalmatov.corevoice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.content.Intent
import android.widget.EditText
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nalmatov.corevoice.car.CarManager
import com.nalmatov.corevoice.ui.theme.CoreVoiceTheme
import com.nalmatov.corevoice.voice.VoskSpeechManager
import com.nalmatov.corevoice.voice.VoiceAssistantService
import com.nalmatov.corevoice.handlers.DeviceHandler
import androidx.compose.runtime.remember
import android.graphics.Color

class MainActivity : ComponentActivity() {

    private lateinit var voskManager: VoskSpeechManager

    private var recognizedText by mutableStateOf("")
    private var voskReady by mutableStateOf(false)
    private var listening by mutableStateOf(false)

    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                initVosk()
            } else {
                Log.e(
                    "MainActivity",
                    "Разрешение RECORD_AUDIO не выдано"
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CarManager.init(this) {
            Log.i("CarManager", "Голосовой ассистент проинициализирован!")
        }
        var deviceHandler = DeviceHandler(applicationContext)

        voskManager = VoskSpeechManager(
            applicationContext
        )

        checkMicrophonePermission()

        enableEdgeToEdge()

        setContent {
            CoreVoiceTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Вызываем наш главный экран, куда корректно инкапсулировано состояние
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        voskReady = voskReady,
                        listening = listening,
                        recognizedText = recognizedText,
                        onToggleListening = { toggleListening() },
                        onStartAssistant = { startVoiceAssistant() },
                        onStopAssistant = { stopVoiceAssistant() }
                    )
                }
            }
        }
    }

    private fun startVoiceAssistant() {
        val intent = Intent(
            this,
            VoiceAssistantService::class.java
        ).apply {
            action = VoiceAssistantService.ACTION_START
        }

        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopVoiceAssistant() {
        val intent = Intent(
            this,
            VoiceAssistantService::class.java
        ).apply {
            action = VoiceAssistantService.ACTION_STOP
        }

        startService(intent)
    }

    private fun checkMicrophonePermission() {
        val permission =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            )

        if (permission == PackageManager.PERMISSION_GRANTED) {
            initVosk()
        } else {
            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    private fun initVosk() {
        voskManager.init(
            onReady = {
                runOnUiThread {
                    voskReady = true
                }

                Log.i(
                    "MainActivity",
                    "Vosk готов"
                )
            },

            onError = { error ->
                Log.e(
                    "MainActivity",
                    "Vosk error: $error"
                )
            }
        )
    }

    private fun openApp(packageName: String) {
        Log.i("MainActivity", packageName)
        try {
            val intent = this.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                this.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Ошибка открытия: ${e.message}")
        }
    }

    private fun toggleListening() {
        if (!voskReady) {
            return
        }

        if (voskManager.isListening()) {

            voskManager.stopListening()

            listening = false

            return
        }

        recognizedText = ""
        listening = true

        voskManager.startListening(

            onPartialResult = { text ->

                runOnUiThread {
                    recognizedText = text
                }

                Log.d(
                    "MainActivity",
                    "Partial: $text"
                )
            },

            onFinalResult = { text ->

                runOnUiThread {
                    recognizedText = text
                }
                // power 356517136, 15, close/open any num
                // a/c 356517125, 15, 1 close, 2 open
                // fan speed 624957704, 15, 1-8
                // trunk 692121656, 0, 1 close, 2 open
                // max a/c 356517126, 15, 1 close 2 open
                // recirc 356517128 15, 0 inside, 1 outside
                // temperature 358614275, 1, 22.0-32.0 float
                // mirror 557855943, 1 close, 2 open
                // rgb brightness 557845026, 0, 0-6
                //

                if (text == "включи кондиционер" || text == "включить кондиционер") {
                    CarManager.setPropertyBoolean(356517125, 240, true)
                } else if (text == "выключи кондиционер") {
                    CarManager.setPropertyBoolean(356517125, 15, false)
                } else if (text == "открой ютуб") {
                    Log.i("CarManager", "yotube shoude be open")
                    openApp("app.youtube.revanced")
                } else if ("открой окно пассажир" in text) {
                    CarManager.setProperty(322964416, 64, 100)
                } else if ("закрой окно пассажира" in text) {
                    CarManager.setProperty(322964416, 64, 0)
                }  else if ("открой окно водителя" in text || "открой окно" in text) {
                    CarManager.setProperty(322964416, 16, 100)
                } else if ("закрой окно" in text || "закрой окно" in text) {
                    CarManager.setProperty(322964416, 16, 0)
                }else if ("открой шторку" in text || "открой водительское люк" in text) {
                    CarManager.setProperty(557903927, 0, 203)
                } else if ("закрой шторку" in text || "закрой водительское люк" in text) {
                    CarManager.setProperty(557903927, 0, 103)
                } else if ("закрой люк" in text || "закрой водительское люк" in text) {
                    CarManager.setProperty(557903927, 0, 1)
                } else if ("открой люк" in text || "закрой водительское люк" in text) {
                    CarManager.setProperty(557903927, 0, 101)
                } else if ("сложи зеркала" in text || "сложи зеркало" in text) {
                    CarManager.setProperty(557855943, 0, 2)
                } else if ("разложи зеркала" in text || "разложить зеркала" in text) {
                    CarManager.setProperty(557855943, 0, 1)
                } else if ("включи массаж" in text || "включить массаж" in text) {
                    CarManager.setProperty(557855893, 0, 1)
                } else if ("выключи массаж" in text || "выключить массаж" in text) {
                    CarManager.setProperty(557855893, 0, 9)
                } else if ("открой багажник" in text) {
                    CarManager.setProperty(692073902, 4, 2)
                }

                Log.i(
                    "MainActivity",
                    "Final: $text"
                )
            }
        )
    }

    override fun onDestroy() {
        voskManager.stopListening()
        voskManager.release()
        super.onDestroy()
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    voskReady: Boolean,
    listening: Boolean,
    recognizedText: String,
    onToggleListening: () -> Unit,
    onStartAssistant: () -> Unit,
    onStopAssistant: () -> Unit
) {
    // Состояния для полей ввода теперь живут внутри правильного уровня рекомпозиции
    var propertyId by remember { mutableStateOf("") }
    var areaId by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CoreVoice",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            modifier = Modifier.padding(top = 24.dp),
            text = when {
                !voskReady -> "Model loading..."
                listening -> "Listening..."
                else -> "Ready"
            }
        )

        Text(
            modifier = Modifier.padding(top = 24.dp),
            text = if (recognizedText.isBlank()) "..." else recognizedText
        )

        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp),
            enabled = voskReady,
            onClick = onToggleListening
        ) {
            Text(if (listening) "Остановить" else "Начать слушать")
        }

        AndroidView(
            factory = { context ->
                EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER

                    setTextColor(Color.WHITE) // Белый текст
                    setHintTextColor(Color.GRAY) // Цвет подсказки, если понадобится

                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?, start: Int, count: Int, after: Int
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?, start: Int, before: Int, count: Int
                        ) {
                            propertyId = s?.toString().orEmpty()
                        }

                        override fun afterTextChanged(s: Editable?) {}
                    })
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        AndroidView(
            factory = { context ->
                EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER

                    setTextColor(Color.WHITE) // Белый текст
                    setHintTextColor(Color.GRAY) // Цвет подсказки, если понадобится

                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?, start: Int, count: Int, after: Int
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?, start: Int, before: Int, count: Int
                        ) {
                            areaId = s?.toString().orEmpty()
                        }

                        override fun afterTextChanged(s: Editable?) {}
                    })
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        AndroidView(
            factory = { context ->
                EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER

                    setTextColor(Color.WHITE) // Белый текст
                    setHintTextColor(Color.GRAY) // Цвет подсказки, если понадобится

                    addTextChangedListener(object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?, start: Int, count: Int, after: Int
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?, start: Int, before: Int, count: Int
                        ) {
                            value = s?.toString().orEmpty()
                        }

                        override fun afterTextChanged(s: Editable?) {}
                    })
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            modifier = Modifier.padding(top = 8.dp),
            onClick = {
                CarManager.getProperty(557845027, 0)
                CarManager.getProperty(557855752, 0)

                val property = propertyId.toIntOrNull()
                val area = areaId.toIntOrNull()
                val intValue = value.toIntOrNull()

                if (property != null) {
                    CarManager.inspectProperty(property)

                    if (area != null) {
                        CarManager.getProperty(property, area)

                        if (intValue != null) {
                            CarManager.setProperty(property, area, intValue)
                        }
                    }
                }

            }
        ) {
            Text("Проверка")
        }

//                        Button(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .padding(top = 32.dp),
//                            onClick = {
//                                CarManager.setProperty(557855893, 0, 0)
////                                public static final int ECO = 1;
////                                public static final int NORMAL = 0;
////                                public static final int SNOW = 3;
////                                public static final int SPORT = 2;
////                                public static final int SPORT_PLUS = 4;
////                                Log.i("MainActivity", "yotube shoude be open")
////                                openApp("app.revanced.android.youtube")
//                            }
//                        ) {
//                            Text("Проверка")
//                        }

        Button(
            modifier = Modifier.padding(top = 8.dp),
            onClick = onStartAssistant
        ) {
            Text("Запустить ассистента")
        }

        Button(
            modifier = Modifier.padding(top = 8.dp),
            onClick = onStopAssistant
        ) {
            Text("Остановить ассистента")
        }
    }
}