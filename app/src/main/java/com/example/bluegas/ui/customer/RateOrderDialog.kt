package com.example.bluegas.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.data.rateOrder
import com.example.ui.theme.BlueGasPrimary

// F1.4
@Composable
fun RateOrderDialog(
    orderId: String,
    store: BlueGasStore,
    onResult: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var ratingStars by remember(orderId) { mutableIntStateOf(5) }
    var ratingComment by remember(orderId) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFEAB308))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Rate Delivery", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Order: $orderId",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    (1..5).forEach { star ->
                        IconButton(
                            onClick = { ratingStars = star },
                            modifier = Modifier.testTag("rate_star_$star")
                        ) {
                            Icon(
                                imageVector = if (star <= ratingStars) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star stars",
                                tint = Color(0xFFEAB308),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = ratingComment,
                    onValueChange = { ratingComment = it },
                    label = { Text("Comment (optional)") },
                    modifier = Modifier.fillMaxWidth().testTag("rate_comment_input"),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (ok, msg) = store.rateOrder(orderId, ratingStars, ratingComment)
                    onResult(msg, ok)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                modifier = Modifier.testTag("rate_order_submit_button")
            ) {
                Text("Submit rating")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
