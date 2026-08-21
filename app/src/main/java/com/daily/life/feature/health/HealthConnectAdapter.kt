package com.daily.life.feature.health

import android.content.Context
import java.time.Instant

class HealthConnectAdapter(
    private val context: Context,
    private val reader: suspend (Instant, Instant) -> List<ActivityRecord> = { _, _ -> emptyList() },
    private val permissionRequester: suspend () -> DataSourceAvailability = {
        DataSourceAvailability.permissionRequired(
            label = "Health Connect",
            detail = "请在设备的 Health Connect 设置中授予步数和运动权限"
        )
    }
) : PermissionAwareActivityDataSource {
    override suspend fun availability(): DataSourceAvailability {
        val providerInstalled = runCatching {
            context.packageManager.getPackageInfo(HEALTH_CONNECT_PACKAGE, 0)
        }.isSuccess
        return if (providerInstalled) {
            DataSourceAvailability.permissionRequired(
                label = "Health Connect",
                detail = "进入健康页后授权，授权请求不会在启动时执行"
            )
        } else {
            DataSourceAvailability.unavailable(
                label = "Health Connect",
                detail = "设备未安装 Health Connect，可使用手动记录或手机传感器"
            )
        }
    }

    override suspend fun read(start: Instant, end: Instant): List<ActivityRecord> = reader(start, end)

    override suspend fun requestPermission(): DataSourceAvailability = permissionRequester()

    private companion object {
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    }
}
