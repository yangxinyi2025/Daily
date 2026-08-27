package com.daily.life.feature.health

internal fun weightChangeFromLatest(weights: List<WeightRecord>): Double? {
    val latest = weights.sortedByDescending { it.recordedAt }.take(2)
    if (latest.size < 2) return null
    return latest[0].weightJin - latest[1].weightJin
}
