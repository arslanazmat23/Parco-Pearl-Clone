package com.example.bluegas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.ai.GeminiAdvisorService
import com.example.bluegas.data.BlueGasStore
import kotlinx.coroutines.launch

@Composable
fun AiAdvisorDialog(store: BlueGasStore, onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    var inputQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var responseText by remember {
        mutableStateOf(
            "Welcome to Blue Gas Intelligence Advisor.\n" +
            "Powered by gemini-4-argon with High Thinking level.\n" +
            "Ask any question about LPG safety procedures (leakage, OGRA compliance), operational exceptions recovery targets, or security deposit ownership vs custody."
        )
    }

    val quickQueries = listOf(
        "What to do if customer smells gas leak?",
        "How to recover from ORD_EXC (Fulfilment Exception)?",
        "Does security deposit give cylinder ownership?",
        "What happens if two offline riders scan same serial?"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF0284C7)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Blue Gas Intelligence Advisor", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Model: gemini-4-argon • Thinking: HIGH", fontSize = 11.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.SemiBold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                // Quick suggested prompts
                Text("Quick Inquiries:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickQueries.take(2).forEach { q ->
                        AssistChip(
                            onClick = {
                                inputQuery = q
                            },
                            label = { Text(q.take(28) + "...", fontSize = 9.sp) }
                        )
                    }
                }

                // Response window
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    item {
                        if (isLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 16.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Engaging High Thinking reasoning with gemini-4-argon...", fontSize = 12.sp, color = Color(0xFF0284C7))
                            }
                        } else {
                            Text(
                                text = responseText,
                                fontSize = 12.sp,
                                color = Color(0xFF0F172A),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Input box
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        placeholder = { Text("Ask safety or operational query...", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (inputQuery.isNotBlank() && !isLoading) {
                                val queryToSend = inputQuery
                                inputQuery = ""
                                isLoading = true
                                coroutineScope.launch {
                                    val currentOrder = store.orders.firstOrNull()
                                    val context = "Total Orders: ${store.orders.size}, Active Zone: Lahore Gulberg, Sample Order Status: ${currentOrder?.status?.canonicalLabel}"
                                    val result = GeminiAdvisorService.consultAdvisor(queryToSend, context)
                                    responseText = result
                                    isLoading = false
                                }
                            }
                        },
                        enabled = inputQuery.isNotBlank() && !isLoading
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color(0xFF0052CC))
                    }
                }
            }
        }
    )
}
