/**
 * Shared time, countdown, and date formatting utilities for AI Studio surfaces.
 *
 * Provides lifecycle-aware millisecond clock producers, countdown string formatters,
 * and standard date/time formatting without hardcoded relative timestamps or invented facts.
 */
package com.example.bluegas.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Coroutine-backed state producer providing the current epoch millisecond timestamp.
 *
 * Ticks periodically only while [enabled] is true. When disabled, the ticker coroutine halts.
 *
 * @param tickMs Interval between clock ticks in milliseconds.
 * @param enabled Controls whether the ticking coroutine is active.
 * @return The latest epoch timestamp in milliseconds.
 */
@Composable
fun rememberNowMillis(tickMs: Long = 1000L, enabled: Boolean = true): Long {
    return produceState(initialValue = System.currentTimeMillis(), key1 = enabled, key2 = tickMs) {
        if (!enabled) return@produceState
        while (isActive) {
            value = System.currentTimeMillis()
            delay(tickMs)
        }
    }.value
}

/**
 * Formats a remaining time duration in milliseconds into a concise "m:ss" countdown string.
 *
 * @param remainingMs Duration remaining until expiry in milliseconds.
 * @return Formatted countdown string, clamping at "0:00" if remaining time is non-positive.
 */
fun formatCountdown(remainingMs: Long): String {
    if (remainingMs <= 0L) return "0:00"
    val totalSeconds = remainingMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

/**
 * Formats an epoch timestamp into time only ("HH:mm").
 *
 * @param timestamp Epoch milliseconds.
 * @return Formatted time string.
 */
fun formatTimeOnly(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(timestamp)
}

/**
 * Formats an epoch timestamp into human date and time ("dd MMM, HH:mm").
 *
 * @param timestamp Epoch milliseconds.
 * @return Formatted date and time string.
 */
fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    return sdf.format(timestamp)
}

/**
 * Checks whether the given timestamp falls on today's calendar date in the device's default time zone.
 *
 * @param timestamp Epoch milliseconds.
 * @return True if [timestamp] occurred on the current day, false otherwise.
 */
fun isToday(timestamp: Long): Boolean {
    val calEvent = Calendar.getInstance().apply { timeInMillis = timestamp }
    val calToday = Calendar.getInstance()
    return calEvent.get(Calendar.YEAR) == calToday.get(Calendar.YEAR) &&
            calEvent.get(Calendar.DAY_OF_YEAR) == calToday.get(Calendar.DAY_OF_YEAR)
}
