package app.wallglass.ambient

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

private const val TAG = "AmbientLight"

/** Room light level from the panel's light sensor; empty when the device has none. */
fun ambientLux(context: Context): Flow<Float> {
    val manager = context.getSystemService(SensorManager::class.java) ?: return emptyFlow()
    val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT) ?: return emptyFlow<Float>().also {
        Log.i(TAG, "brak czujnika światła")
    }
    return callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val lux = event.values.firstOrNull() ?: return
                trySend(lux)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            Log.w(TAG, "nie udało się włączyć czujnika światła")
        }
        awaitClose { manager.unregisterListener(listener) }
    }
}
