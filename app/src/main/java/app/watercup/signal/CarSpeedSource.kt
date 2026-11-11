package app.watercup.signal

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log

/**
 * 차량 속도(m/s). Pleos Vehicle SDK에는 속도 API가 없어
 * Android Automotive 표준 Car API의 PERF_VEHICLE_SPEED를 읽는다.
 */
class CarSpeedSource(private val context: Context) {
    private var car: Car? = null
    private var manager: CarPropertyManager? = null

    @Volatile var speedMps: Float? = null
        private set

    @Volatile var error: String? = null
        private set

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            (value.value as? Float)?.let { speedMps = it }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            error = "속도 수신 오류"
        }
    }

    fun start() {
        if (manager != null) return
        try {
            val created = Car.createCar(context)
            val pm = created.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            pm.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FAST)
            car = created
            manager = pm
            error = null
        } catch (e: Throwable) {
            error = e.message ?: e.javaClass.simpleName
            Log.w("WaterCup", "speed source failed", e)
        }
    }

    fun stop() {
        try {
            manager?.unregisterCallback(callback)
            car?.disconnect()
        } catch (e: Throwable) {
            Log.w("WaterCup", "speed source stop failed", e)
        }
        manager = null
        car = null
    }

    companion object {
        const val PERMISSION = "android.car.permission.CAR_SPEED"
    }
}
