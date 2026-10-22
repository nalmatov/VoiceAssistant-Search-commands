package com.nalmatov.corevoice.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyConfig
import android.car.hardware.property.CarPropertyManager
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import java.util.concurrent.Executors

object CarManager {

    private const val TAG = "CarManagerNalmatov"

    private var mCar: Car? = null
    private var mPropertyManager: CarPropertyManager? = null

    private val executor = Executors.newSingleThreadExecutor()

    fun init(
        context: Context,
        onConnected: (Boolean) -> Unit
    ) {
        if (mCar?.isConnected == true && mPropertyManager != null) {
            onConnected(true)
            return
        }

        mCar = Car.createCar(
            context.applicationContext,
            object : ServiceConnection {

                override fun onServiceConnected(
                    name: ComponentName?,
                    service: IBinder?
                ) {
                    Log.i(TAG, "Car Service connected: $name")

                    try {

                        Log.i(
                            TAG,
                            "Получаем PROPERTY_SERVICE, package=${context.packageName}"
                        )

                        mPropertyManager =
                            mCar?.getCarManager(Car.PROPERTY_SERVICE)
                                    as? CarPropertyManager

                        if (mPropertyManager == null) {
                            Log.e(TAG, "CarPropertyManager == null")
                            onConnected(false)
                            return
                        }

                        Log.i(TAG, "CarPropertyManager получен успешно")

                        onConnected(true)

                    } catch (e: SecurityException) {

                        Log.e(
                            TAG,
                            """
                                CarService отклонил приложение.
                    
                                package=${context.packageName}
                                reason=${e.message}
                    
                                Вероятно приложение отсутствует в vendor CarService whitelist.
                                """.trimIndent(),
                            e
                        )

                        mPropertyManager = null
                        onConnected(false)

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Ошибка получения CarPropertyManager",
                            e
                        )

                        mPropertyManager = null
                        onConnected(false)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    mPropertyManager = null
                    Log.w(TAG, "Соединение с Car Service потеряно")
                }
            }
        )

        mCar?.connect()
    }

    fun isInitialized(): Boolean {
        return mCar?.isConnected == true && mPropertyManager != null
    }

    /**
     * Выводит все свойства, доступные текущему приложению.
     *
     * Метод только читает конфигурацию и значения.
     * Никаких setProperty() здесь нет.
     */
    private fun formatValue(value: Any?): String {
        return when (value) {
            null -> "null"

            is ByteArray -> value.contentToString()
            is IntArray -> value.contentToString()
            is LongArray -> value.contentToString()
            is FloatArray -> value.contentToString()
            is DoubleArray -> value.contentToString()
            is BooleanArray -> value.contentToString()
            is Array<*> -> value.contentDeepToString()

            else -> value.toString()
        }
    }

    fun setPropertyGeneric(
        propertyId: Int,
        areaId: Int,
        value: Int
    ) {
        try {
            val method = mPropertyManager!!::class.java.getMethod(
                "setProperty",
                Class::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Any::class.java
            )

            method.invoke(
                mPropertyManager,
                Integer::class.java,
                propertyId,
                areaId,
                value
            )

            Log.i(TAG, "Generic setProperty OK")
        } catch (e: Exception) {
            Log.e(TAG, "Generic setProperty FAILED", e)
        }
    }

    fun setProperty(
        propertyId: Int,
        areaId: Int,
        value: Int
    ): Boolean {
        val manager = mPropertyManager

        if (manager == null) {
            Log.e(TAG, "Error: manager not initialized")
            return false
        }

        return try {
            manager.setProperty(
                Integer::class.java,
                propertyId,
                areaId,
                value as Integer
            )

            Log.i(
                TAG,
                "Установлено: propertyId=$propertyId, " +
                        "areaId=$areaId, value=$value"
            )

            true
        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "SecurityException: propertyId=$propertyId, " +
                        "areaId=$areaId, value=$value, ${e.message}",
                e
            )
            false
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка setProperty: ${e.message}", e)
            false
        }
    }

    fun setFloatProperty(propertyId: Int, areaId: Int, value: Float): Boolean {
        val manager = mPropertyManager

        if (manager == null) {
            Log.e(TAG, "Error: manager not initialized")
            return false
        }

        return try {
            manager.setFloatProperty(
                propertyId,
                areaId,
                value
            )

            Log.i(
                "CarManagerNalmatov",
                "Установлено FLOAT: propertyId=$propertyId, areaId=$areaId, value=$value"
            )
            true
        } catch (e: Exception) {
            Log.e(
                "CarManagerNalmatov",
                "Ошибка FLOAT: propertyId=$propertyId, areaId=$areaId, value=$value",
                e
            )
            false
        }
    }

    fun setByteArrayProperty(
        propertyId: Int,
        areaId: Int,
        value: ByteArray
    ): Boolean {
        return try {
            val manager = mPropertyManager ?: return false

            manager.setProperty(
                ByteArray::class.java,
                propertyId,
                areaId,
                value
            )

            Log.i(
                "CarManagerNalmatov",
                "BYTE ARRAY: propertyId=$propertyId, " +
                        "areaId=$areaId, " +
                        "value=${value.joinToString { (it.toInt() and 0xFF).toString() }}"
            )
            true
        } catch (e: Exception) {
            Log.e(
                "CarManagerNalmatov",
                "Ошибка записи ByteArray: propertyId=$propertyId",
                e
            )
            false
        }
    }

    fun getProperty(propertyId: Int, areaId: Int): Any? {
        val tag = "PROPERTY_GET"

        try {
            val manager = mPropertyManager ?: run {
                Log.e(tag, "CarPropertyManager не инициализирован")
                return null
            }

            val config = manager.getCarPropertyConfig(propertyId) ?: run {
                Log.e(tag, "Property $propertyId не поддерживается")
                return null
            }

            // Проверяем, поддерживается ли areaId
            if (areaId !in config.areaIds) {
                Log.e(
                    tag,
                    "Неверный areaId=$areaId. Доступные: ${config.areaIds.contentToString()}"
                )
                return null
            }

            val value: Any? = when (config.propertyType) {
                java.lang.Integer::class.java ->
                    manager.getIntProperty(propertyId, areaId)

                java.lang.Boolean::class.java ->
                    manager.getBooleanProperty(propertyId, areaId)

                java.lang.Float::class.java ->
                    manager.getFloatProperty(propertyId, areaId)

                java.lang.String::class.java ->
                    manager.getProperty(
                        String::class.java,
                        propertyId,
                        areaId
                    )?.value

                else -> {
                    Log.w(tag, "Неизвестный тип: ${config.propertyType}")
                    null
                }
            }

            Log.i(tag, "========== PROPERTY VALUE ==========")
            Log.i(tag, "Property ID: $propertyId")
            Log.i(tag, "Area ID: $areaId")
            Log.i(tag, "Type: ${config.propertyType}")
            Log.i(tag, "Value: $value")
            Log.i(tag, "====================================")

            return value

        } catch (e: Exception) {
            Log.e(
                tag,
                "Ошибка чтения propertyId=$propertyId areaId=$areaId",
                e
            )
            return null
        }
    }

    fun inspectProperty(propertyId: Int) {
        val tag = "PROPERTY_DEBUG"

        try {
            val manager = mPropertyManager

            if (manager == null) {
                Log.e(tag, "CarPropertyManager не подключён")
                return
            }

            val config = manager.getCarPropertyConfig(propertyId)

            if (config == null) {
                Log.e(tag, "Property $propertyId не поддерживается")
                return
            }

            Log.i(tag, "========== PROPERTY INFO ==========")
            Log.i(tag, "Property ID: $propertyId")
            Log.i(tag, "Hex: 0x${propertyId.toUInt().toString(16)}")
            Log.i(tag, "Type: ${config.propertyType}")
            Log.i(tag, "Access: ${config.access}")
            Log.i(tag, "Change mode: ${config.changeMode}")
            Log.i(tag, "Config array: ${config.configArray}")

            for (areaId in config.areaIds) {
                Log.i(tag, "---------- AREA ----------")
                Log.i(tag, "Area ID: $areaId")
                Log.i(tag, "Min value: ${config.getMinValue(areaId)}")
                Log.i(tag, "Max value: ${config.getMaxValue(areaId)}")
            }

            Log.i(tag, "===================================")

        } catch (e: Exception) {
            Log.e(tag, "Ошибка чтения Property $propertyId", e)
        }
    }

    fun setPropertyBoolean(
        propertyId: Int,
        areaId: Int,
        value: Boolean
    ): Boolean {
        val manager = mPropertyManager

        if (manager == null) {
            Log.e(TAG, "Error: manager not initialized")
            return false
        }

        return try {
            manager.setProperty(
                Boolean::class.java,
                propertyId,
                areaId,
                value as Boolean
            )

            Log.i(
                TAG,
                "Установлено: propertyId=$propertyId, " +
                        "areaId=$areaId, value=$value"
            )

            true
        } catch (e: SecurityException) {
            Log.e(
                TAG,
                "SecurityException: propertyId=$propertyId, " +
                        "areaId=$areaId, value=$value, ${e.message}",
                e
            )
            false
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка setProperty: ${e.message}", e)
            false
        }
    }

    fun disconnect() {
        mCar?.disconnect()
        mCar = null
        mPropertyManager = null
    }
}