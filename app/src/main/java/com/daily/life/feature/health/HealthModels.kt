package com.daily.life.feature.health

import java.time.Instant
import java.time.LocalDate

data class WeightRecord(
    val id: Long = 0L,
    val recordedAt: Instant,
    val weightJin: Double,
    val source: String = "MANUAL",
    val notes: String? = null
)

data class WeightPoint(
    val date: LocalDate,
    val weightJin: Double
)
