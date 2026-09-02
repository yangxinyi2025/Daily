package com.daily.life.feature.health

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

internal fun weightRecordInstantFor(date: LocalDate, zoneId: ZoneId): Instant =
    date.atStartOfDay(zoneId).toInstant()

internal fun weightRecordInstantFor(recordedAt: LocalDateTime, zoneId: ZoneId): Instant =
    recordedAt.atZone(zoneId).toInstant()
