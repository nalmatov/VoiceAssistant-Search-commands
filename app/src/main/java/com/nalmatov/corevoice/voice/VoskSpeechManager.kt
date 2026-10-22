package com.nalmatov.corevoice.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService

class VoskSpeechManager(context: Context) {

    companion object {
        private const val TAG = "VoskSpeech"
        private const val MODEL_ASSET = "vosk-model-small-ru-0.22"
        private const val MODEL_FOLDER = "model"
        private const val SAMPLE_RATE = 16_000
        private const val BUFFER_SIZE = 4096
    }

    private val appContext = context.applicationContext
    private val lock = Any()

    private var model: Model? = null
    private var loading = false
    private var released = false
    private var generation = 0
    private var readyCallbacks = mutableListOf<() -> Unit>()
    private var errorCallbacks = mutableListOf<(String) -> Unit>()

    @Volatile private var listening = false
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var partialCallback: (String) -> Unit = {}
    private var resultCallback: (String) -> Unit = {}

    fun init(onReady: () -> Unit = {}, onError: (String) -> Unit = {}) {
        var alreadyReady = false
        var startLoad = false
        var token = 0
        synchronized(lock) {
            when {
                released -> Unit
                model != null -> alreadyReady = true
                else -> {
                    readyCallbacks.add(onReady)
                    errorCallbacks.add(onError)
                    if (!loading) {
                        loading = true
                        token = generation
                        startLoad = true
                    }
                }
            }
        }
        if (released) {
            onError("VoskSpeechManager уже освобождён")
            return
        }
        if (alreadyReady) {
            onReady()
            return
        }
        if (!startLoad) return

        StorageService.unpack(appContext, MODEL_ASSET, MODEL_FOLDER,
            { loaded ->
                val ready: List<() -> Unit>
                synchronized(lock) {
                    if (released || token != generation) {
                        loaded.close()
                        return@unpack
                    }
                    model = loaded
                    loading = false
                    ready = readyCallbacks.toList()
                    readyCallbacks.clear()
                    errorCallbacks.clear()
                }
                Log.i(TAG, "Модель Vosk загружена")
                ready.forEach { it() }
            },
            { exception ->
                val callbacks: List<(String) -> Unit>
                synchronized(lock) {
                    if (released || token != generation) return@unpack
                    loading = false
                    callbacks = errorCallbacks.toList()
                    readyCallbacks.clear()
                    errorCallbacks.clear()
                }
                val message = "Ошибка загрузки Vosk: ${exception.message}"
                Log.e(TAG, message, exception)
                callbacks.forEach { it(message) }
            }
        )
    }

    fun startListening(
        onPartialResult: (String) -> Unit = {},
        onFinalResult: (String) -> Unit
    ) {
        synchronized(lock) {
            if (listening || audioThread?.isAlive == true) return
            if (released) return
            val currentModel = model ?: run {
                Log.e(TAG, "Модель ещё не загружена")
                return
            }
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Нет разрешения RECORD_AUDIO")
                return
            }

            var record: AudioRecord? = null
            try {
                val minimum = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                require(minimum > 0) { "Недопустимый размер аудиобуфера: $minimum" }

                record = AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minimum * 2, BUFFER_SIZE)
                )
                check(record.state == AudioRecord.STATE_INITIALIZED) {
                    "AudioRecord не инициализирован"
                }
                record.startRecording()
                check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Микрофон не начал запись"
                }

                audioRecord = record
                partialCallback = onPartialResult
                resultCallback = onFinalResult
//                setupEffects(record.audioSessionId)
                listening = true
                val activeRecord = record
                audioThread = Thread({ recognize(currentModel, activeRecord) }, "VoskAudioThread")
                    .also { it.start() }
                Log.i(TAG, "Распознавание запущено")
            } catch (e: Exception) {
                listening = false
                releaseEffects()
                runCatching { record?.stop() }
                runCatching { record?.release() }
                audioRecord = null
                Log.e(TAG, "Не удалось запустить микрофон", e)
            }
        }
    }

    private fun recognize(currentModel: Model, record: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            Recognizer(currentModel, SAMPLE_RATE.toFloat()).use { recognizer ->
                while (listening) {
                    val size = record.read(buffer, 0, buffer.size)
                    if (!listening) break
                    if (size < 0) error("AudioRecord.read() вернул $size")
                    if (size == 0) continue

                    if (recognizer.acceptWaveForm(buffer, size)) {
                        emit(recognizer.result, "text", resultCallback)
                    } else {
                        emit(recognizer.partialResult, "partial", partialCallback)
                    }
                }
                // При обычной остановке не выполняем команды из остатка буфера.
            }
        } catch (e: Exception) {
            if (listening) {
                Log.e(TAG, "Ошибка распознавания", e)
            }
        } finally {
            listening = false
            synchronized(lock) {
                releaseEffects()
                runCatching { record.stop() }
                record.release()
                if (audioRecord === record) audioRecord = null
                audioThread = null
                if (released) {
                    model?.close()
                    model = null
                }
            }
            Log.i(TAG, "Распознавание остановлено")
        }
    }

    private fun emit(json: String?, field: String, callback: (String) -> Unit) {
        val text = runCatching { JSONObject(json ?: "{}").optString(field).trim() }
            .getOrDefault("")
        if (text.isNotEmpty()) {
            Log.d(TAG, "$field: $text")
            runCatching { callback(text) }
                .onFailure { Log.e(TAG, "Ошибка обработчика $field", it) }
        }
    }

    fun stopListening() {
        listening = false
        val record = synchronized(lock) { audioRecord }
        runCatching {
            if (record?.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
        }
        // Ресурсы закрываются только в finally аудиопотока.
    }

    fun isReady(): Boolean = synchronized(lock) { model != null }
    fun isListening(): Boolean = listening

    fun release() {
        stopListening()
        val thread: Thread?
        synchronized(lock) {
            released = true
            generation++
            readyCallbacks.clear()
            errorCallbacks.clear()
            thread = audioThread
        }
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                // Не закрываем модель, пока поток всё ещё может её использовать.
                return
            }
        }
        synchronized(lock) {
            if (audioThread == null) {
                model?.close()
                model = null
            }
        }
    }

    private fun setupEffects(sessionId: Int) {
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = runCatching { NoiseSuppressor.create(sessionId) }.getOrNull()
            runCatching { noiseSuppressor?.enabled = true }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = runCatching { AcousticEchoCanceler.create(sessionId) }.getOrNull()
            runCatching { echoCanceler?.enabled = true }
        }
    }

    private fun releaseEffects() {
        runCatching { noiseSuppressor?.release() }
        runCatching { echoCanceler?.release() }
        noiseSuppressor = null
        echoCanceler = null
    }
}
