package com.example.bluegas.ui.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.data.riderCollectCylinder
import com.example.bluegas.model.ReturnRefundCase
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

/**
 * Masks a cylinder serial number to expose only its final 3 characters (e.g. `••••-731`).
 *
 * @param serial Full cylinder serial identifier on the return request.
 * @return Masked hint safe to display before rider verification.
 */
fun maskCylinderIdHint(serial: String?): String {
    val clean = serial?.trim().orEmpty()
    if (clean.isEmpty()) return "••••"
    return "••••-${clean.takeLast(3)}"
}

/**
 * Modal dialog for riders to verify the returned cylinder ID and capture a required condition photo
 * before confirming pickup from the customer's doorstep.
 *
 * Keeps the dialog open and surfaces store amber errors (e.g. serial mismatch or offline guard)
 * until collection succeeds or the rider cancels.
 *
 * @param returnCase Active cylinder return request assigned to the rider.
 * @param expectedSizeLabel Human-readable cylinder size label (e.g. `"11.8 kg"`).
 * @param riderId Active rider identifier performing the collection.
 * @param store Application data store used to commit [BlueGasStore.riderCollectCylinder].
 * @param onDismiss Invoked when the rider cancels or closes the dialog.
 * @param onCollected Invoked with confirmation message when collection succeeds.
 */
@Composable
fun CollectReturnDialog(
    returnCase: ReturnRefundCase,
    expectedSizeLabel: String,
    riderId: String,
    store: BlueGasStore,
    onDismiss: () -> Unit,
    onCollected: (String) -> Unit
) {
    var enteredId by remember(returnCase.id) { mutableStateOf("") }
    var photoCaptured by remember(returnCase.id) { mutableStateOf(false) }
    var errorMessage by remember(returnCase.id) { mutableStateOf<String?>(null) }

    val maskedHint = remember(returnCase.cylinderSerial) {
        maskCylinderIdHint(returnCase.cylinderSerial)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("collect_return_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF7E22CE))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Collect Cylinder",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = BlueGasDark
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = Color(0xFFFAF5FF),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Expected size: $expectedSizeLabel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BlueGasDark
                        )
                        Text(
                            text = "Expected cylinder ID ends with: $maskedHint",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B21A8)
                        )
                    }
                }

                OutlinedTextField(
                    value = enteredId,
                    onValueChange = { raw ->
                        enteredId = raw.filterNot { it.isWhitespace() }.uppercase()
                        errorMessage = null
                    },
                    label = { Text("Cylinder ID") },
                    placeholder = { Text("Scan or type ID") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("collect_return_id_input")
                )

                Surface(
                    onClick = {
                        enteredId = returnCase.cylinderSerial.orEmpty().trim().uppercase()
                        errorMessage = null
                    },
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("collect_return_demo_id_chip")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Demo: use expected ID",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569)
                        )
                    }
                }

                if (!photoCaptured) {
                    OutlinedButton(
                        onClick = { photoCaptured = true },
                        border = BorderStroke(1.dp, BlueGasPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("collect_return_photo_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Take condition photo", fontSize = 12.sp)
                    }
                } else {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF166534),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Photo captured ✓",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF166534)
                                )
                            }
                            TextButton(
                                onClick = { photoCaptured = false },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Retake", fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = Color(0xFF92400E),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = errorMessage ?: "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (ok, msg) = store.riderCollectCylinder(
                        caseId = returnCase.id,
                        riderId = riderId,
                        scannedSerial = enteredId.trim(),
                        photoCaptured = photoCaptured
                    )
                    if (ok) {
                        onCollected(msg)
                    } else {
                        errorMessage = msg
                    }
                },
                enabled = enteredId.isNotBlank() && photoCaptured,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("collect_return_confirm_button")
            ) {
                Text("Confirm collection", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel", fontSize = 12.sp)
            }
        }
    )
}
