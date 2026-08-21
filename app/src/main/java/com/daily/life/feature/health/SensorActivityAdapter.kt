package com.daily.life.feature.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import java.time.Instant

class SensorActivityAdapter(
    private val context: Context,
    private val sensorManagerProvider: () -> SensorManager? = {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    },
    private val reader: suspend (Instant, Instant) -> List<ActivityRecord> = { _, _ -> emptyList() }
) : ActivityDataSource {
    override suspend fun availability(): DataSourceAvailability {
        val hasStepCounter = sensorManagerProvider()?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        return if (hasStepCounter) {
            DataSourceAvailability.available("手机传感器")
        } else {
            DataSourceAvailability.unavailable(
                label = "手机传感器",
                detail = "设备没有步数传感器，请手动记录或授权 Health Connect"
            )
        }
    }

    override suspend fun read(start: Instant, end: Instant): List<ActivityRecord> = reader(start, end)
}
