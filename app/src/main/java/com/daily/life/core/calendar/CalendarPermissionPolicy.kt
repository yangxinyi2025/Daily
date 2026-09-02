package com.daily.life.core.calendar

fun requiresCalendarPermission(readGranted: Boolean, writeGranted: Boolean): Boolean =
    !readGranted || !writeGranted
