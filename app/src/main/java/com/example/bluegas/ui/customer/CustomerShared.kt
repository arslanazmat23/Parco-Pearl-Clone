package com.example.bluegas.ui.customer

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// F1.1
data class BannerMsg(val text: String, val isError: Boolean = false)

// F1.1
@Composable
fun DemoValuePill(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFFE2E8F0),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF475569),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

// F1.1
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

// F1.1
fun formatCountdown(remainingMs: Long): String {
    if (remainingMs <= 0L) return "0:00"
    val totalSeconds = remainingMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

// F3.2
data class ReorderPrefill(
    val sku: String,
    val quantity: Int,
    val orderType: com.example.bluegas.model.OrderType,
    val addressId: String?,
    val mode: com.example.bluegas.model.FulfilmentMode,
    val paymentMethod: com.example.bluegas.model.PaymentMethod,
    val pickupPointId: String?,
    val addAccessory: Boolean,
    val nonce: Long
)
