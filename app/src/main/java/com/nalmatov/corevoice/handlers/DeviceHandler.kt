package com.nalmatov.corevoice.handlers

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import kotlin.math.roundToInt

class DeviceHandler(context: Context) {

    companion object {
        private const val TAG = "DeviceHandler"
        private const val BRIGHTNESS_STEP = 10
        private const val VOLUME_STEP = 10
    }

    private val context = context.applicationContext

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ───────── ЯРКОСТЬ ─────────

    fun increaseBrightness() = changeBrightness(BRIGHTNESS_STEP)

    fun decreaseBrightness() = changeBrightness(-BRIGHTNESS_STEP)

    fun setBrightness(percent: Int): Boolean {
        if (!Settings.System.canWrite(context)) {
            Log.e(TAG, "Нет разрешения WRITE_SETTINGS")
            return false
        }

        return try {
            val value = (percent.coerceIn(0, 100) * 255 / 100f)
                .roundToInt()

            // Отключаем автоматическую яркость
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )

            val success = Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value
            )

            Log.i(TAG, "Яркость: $percent%")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка изменения яркости", e)
            false
        }
    }

    private fun changeBrightness(delta: Int): Boolean {
        val current = Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            128
        )

        val percent = (current * 100f / 255).roundToInt()
        return setBrightness(percent + delta)
    }

    // ───────── ГРОМКОСТЬ ─────────

    fun increaseVolume() = changeVolume(VOLUME_STEP)

    fun decreaseVolume() = changeVolume(-VOLUME_STEP)

    fun setVolume(percent: Int) {
        val stream = AudioManager.STREAM_MUSIC
        val max = audioManager.getStreamMaxVolume(stream)

        val value = (percent.coerceIn(0, 100) * max / 100f)
            .roundToInt()

        audioManager.setStreamVolume(
            stream,
            value,
            0
        )

        Log.i(TAG, "Громкость: $percent%")
    }

    private fun changeVolume(delta: Int) {
        val stream = AudioManager.STREAM_MUSIC

        val current = audioManager.getStreamVolume(stream)
        val max = audioManager.getStreamMaxVolume(stream)

        if (max <= 0) return

        val percent = (current * 100f / max).roundToInt()
        setVolume(percent + delta)
    }

    // ───────── ПРИЛОЖЕНИЯ ─────────

    fun openApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager
                .getLaunchIntentForPackage(packageName)
                ?: return false

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            Log.i(TAG, "Открыто приложение: $packageName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка открытия: $packageName", e)
            false
        }
    }
}