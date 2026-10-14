package com.example.bluegas.ui.rider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.model.Order
import com.example.bluegas.model.PaymentMethod
import com.example.bluegas.model.formatPaisaToPkr
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary
import java.text.SimpleDateFormat
import java.util.*

enum class PodStep(val title: String, val subtitle: String) {
    SAFETY_CHECKLIST("Safety Checklist", "Non-technical inspection"),
    PAYMENT_CONFIRMATION("Payment & Cash", "COD or Online settlement"),
    PHOTO_EVIDENCE("Photo Evidence", "Geotagged cylinder proof"),
    SIGNATURE_CAPTURE("Customer Signature", "POD verification")
}

@Composable
fun PodCaptureDialog(
    order: Order,
    store: BlueGasStore,
    onDismiss: () -> Unit,
    onCompleted: (Boolean, String) -> Unit
) {
    var currentStep by remember { mutableStateOf(PodStep.SAFETY_CHECKLIST) }

    // Step 1: Safety checklist states
    var leakCheckPassed by remember { mutableStateOf(true) }
    var valveIntact by remember { mutableStateOf(true) }
    var ventilatedArea by remember { mutableStateOf(true) }
    var hoseConnectionSecured by remember { mutableStateOf(true) }
    var emptyCylinderCollected by remember { mutableStateOf(false) }
    var emptyCylinderSerial by remember { mutableStateOf("BG-LHR-OLD-812") }

    // Step 2: Payment states
    val isCod = order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY
    var cashCollectedConfirmed by remember { mutableStateOf(!isCod) }
    var tenderedAmountText by remember {
        val pkr = order.totalAmountPaisa / 100
        mutableStateOf(pkr.toString())
    }

    // Step 3: Photo evidence states
    var photoCaptured by remember { mutableStateOf(false) }
    var photoCaptureTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var photoType by remember { mutableStateOf("DOORSTEP") } // DOORSTEP or VALVE_SEAL
    var isShutterTriggered by remember { mutableStateOf(false) }

    // Step 4: Signature canvas states
    val signaturePoints = remember { mutableStateListOf<Offset>() }
    val customer = store.customers.find { it.id == order.customerId }
    var signerName by remember { mutableStateOf(customer?.name ?: "Customer") }
    var signerRelation by remember { mutableStateOf("Customer Himself") }
    var podCustomerNotes by remember { mutableStateOf("") }
    var quickSignAdopted by remember { mutableStateOf(false) }

    val customerAddress = store.addresses.find { it.id == order.addressId }
    val zoneLabel = customerAddress?.let { "${it.city} (${it.zoneId})" } ?: "Lahore Central"

    val isChecklistValid = leakCheckPassed && valveIntact && ventilatedArea && hoseConnectionSecured
    val tenderedPaisa = (tenderedAmountText.toLongOrNull() ?: 0L) * 100L
    val changePaisa = (tenderedPaisa - order.totalAmountPaisa).coerceAtLeast(0L)
    val isPaymentValid = !isCod || (cashCollectedConfirmed && tenderedPaisa >= order.totalAmountPaisa)
    val isPhotoValid = photoCaptured
    val isSignatureValid = signaturePoints.size > 8 || quickSignAdopted

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .testTag("pod_multi_step_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with Order & Progress Stepper
                PodHeader(
                    order = order,
                    currentStep = currentStep,
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Stepper Indicator
                PodStepperIndicator(currentStep = currentStep)

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content Area based on currentStep
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (currentStep) {
                        PodStep.SAFETY_CHECKLIST -> {
                            SafetyChecklistStep(
                                order = order,
                                leakCheckPassed = leakCheckPassed,
                                onLeakCheckChange = { leakCheckPassed = it },
                                valveIntact = valveIntact,
                                onValveIntactChange = { valveIntact = it },
                                ventilatedArea = ventilatedArea,
                                onVentilatedAreaChange = { ventilatedArea = it },
                                hoseConnectionSecured = hoseConnectionSecured,
                                onHoseSecuredChange = { hoseConnectionSecured = it },
                                emptyCylinderCollected = emptyCylinderCollected,
                                onEmptyCylinderChange = { emptyCylinderCollected = it },
                                emptyCylinderSerial = emptyCylinderSerial,
                                onEmptySerialChange = { emptyCylinderSerial = it }
                            )
                        }

                        PodStep.PAYMENT_CONFIRMATION -> {
                            PaymentConfirmationStep(
                                order = order,
                                isCod = isCod,
                                cashCollectedConfirmed = cashCollectedConfirmed,
                                onCashConfirmedChange = { cashCollectedConfirmed = it },
                                tenderedAmountText = tenderedAmountText,
                                onTenderedAmountChange = { tenderedAmountText = it },
                                changePaisa = changePaisa
                            )
                        }

                        PodStep.PHOTO_EVIDENCE -> {
                            PhotoEvidenceStep(
                                order = order,
                                zoneLabel = zoneLabel,
                                photoCaptured = photoCaptured,
                                photoCaptureTimestamp = photoCaptureTimestamp,
                                photoType = photoType,
                                isShutterTriggered = isShutterTriggered,
                                onPhotoTypeChange = { photoType = it },
                                onCaptureClick = {
                                    isShutterTriggered = true
                                    photoCaptureTimestamp = System.currentTimeMillis()
                                    photoCaptured = true
                                },
                                onRetakeClick = {
                                    photoCaptured = false
                                }
                            )
                        }

                        PodStep.SIGNATURE_CAPTURE -> {
                            SignatureCaptureStep(
                                signaturePoints = signaturePoints,
                                signerName = signerName,
                                onSignerNameChange = { signerName = it },
                                signerRelation = signerRelation,
                                onSignerRelationChange = { signerRelation = it },
                                podCustomerNotes = podCustomerNotes,
                                onCustomerNotesChange = { podCustomerNotes = it },
                                quickSignAdopted = quickSignAdopted,
                                onAdoptQuickSign = { quickSignAdopted = true },
                                onClearSignature = {
                                    signaturePoints.clear()
                                    quickSignAdopted = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer Navigation Buttons
                PodFooterButtons(
                    currentStep = currentStep,
                    isNextEnabled = when (currentStep) {
                        PodStep.SAFETY_CHECKLIST -> isChecklistValid
                        PodStep.PAYMENT_CONFIRMATION -> isPaymentValid
                        PodStep.PHOTO_EVIDENCE -> isPhotoValid
                        PodStep.SIGNATURE_CAPTURE -> isSignatureValid
                    },
                    onBack = {
                        val prev = when (currentStep) {
                            PodStep.SAFETY_CHECKLIST -> null
                            PodStep.PAYMENT_CONFIRMATION -> PodStep.SAFETY_CHECKLIST
                            PodStep.PHOTO_EVIDENCE -> PodStep.PAYMENT_CONFIRMATION
                            PodStep.SIGNATURE_CAPTURE -> PodStep.PHOTO_EVIDENCE
                        }
                        if (prev != null) currentStep = prev else onDismiss()
                    },
                    onNext = {
                        when (currentStep) {
                            PodStep.SAFETY_CHECKLIST -> currentStep = PodStep.PAYMENT_CONFIRMATION
                            PodStep.PAYMENT_CONFIRMATION -> currentStep = PodStep.PHOTO_EVIDENCE
                            PodStep.PHOTO_EVIDENCE -> currentStep = PodStep.SIGNATURE_CAPTURE
                            PodStep.SIGNATURE_CAPTURE -> {
                                // Complete Handover
                                val podNote = buildString {
                                    append("Safety Checklist passed (Leak=$leakCheckPassed, Valve=$valveIntact, Vent=$ventilatedArea). ")
                                    if (emptyCylinderCollected) append("Empty Cylinder Collected ($emptyCylinderSerial). ")
                                    if (isCod) append("COD Cash Tendered Rs. ${tenderedAmountText} (Change: ${formatPaisaToPkr(changePaisa)}). ")
                                    append("Photo evidence captured with OGRA watermark. ")
                                    append("Signed by $signerName ($signerRelation). ")
                                    if (podCustomerNotes.isNotBlank()) append("Notes: $podCustomerNotes")
                                }

                                val (ok, msg) = store.completeHandover(
                                    orderId = order.id,
                                    nonTechInstallationPassed = isChecklistValid,
                                    proofOfDeliveryNote = podNote,
                                    collectedCashPaisa = if (isCod) order.totalAmountPaisa else null,
                                    emptyCylinderCollected = emptyCylinderCollected,
                                    emptyCylinderSerial = if (emptyCylinderCollected) emptyCylinderSerial else null,
                                    customerSignature = "Digitally Signed by $signerName ($signerRelation)",
                                    photoEvidenceCaptured = photoCaptured,
                                    signerName = signerName,
                                    changeReturnedPaisa = if (isCod) changePaisa else null
                                )
                                onCompleted(ok, msg)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PodHeader(
    order: Order,
    currentStep: PodStep,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = BlueGasPrimary.copy(alpha = 0.12f),
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = "POD Flow",
                        tint = BlueGasPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Proof of Delivery (POD)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = BlueGasDark
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Order #${order.id}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = order.allocatedSerial ?: "SERIAL-OK",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("pod_close_button")
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
        }
    }
}

@Composable
private fun PodStepperIndicator(currentStep: PodStep) {
    val steps = PodStep.values()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val isCurrent = step == currentStep
            val isPassed = step.ordinal < currentStep.ordinal

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .background(if (isPassed || isCurrent) BlueGasPrimary else Color(0xFFE2E8F0))
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Surface(
                        shape = CircleShape,
                        color = when {
                            isPassed -> Color(0xFF16A34A)
                            isCurrent -> BlueGasPrimary
                            else -> Color(0xFFE2E8F0)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isPassed) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) Color.White else Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    if (index < steps.size - 1) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .background(if (isPassed) Color(0xFF16A34A) else Color(0xFFE2E8F0))
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = when (step) {
                        PodStep.SAFETY_CHECKLIST -> "Safety"
                        PodStep.PAYMENT_CONFIRMATION -> "Payment"
                        PodStep.PHOTO_EVIDENCE -> "Photo"
                        PodStep.SIGNATURE_CAPTURE -> "Sign"
                    },
                    fontSize = 10.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) BlueGasPrimary else Color(0xFF64748B)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 1: SAFETY & INSTALLATION CHECKLIST
// -------------------------------------------------------------
@Composable
private fun SafetyChecklistStep(
    order: Order,
    leakCheckPassed: Boolean,
    onLeakCheckChange: (Boolean) -> Unit,
    valveIntact: Boolean,
    onValveIntactChange: (Boolean) -> Unit,
    ventilatedArea: Boolean,
    onVentilatedAreaChange: (Boolean) -> Unit,
    hoseConnectionSecured: Boolean,
    onHoseSecuredChange: (Boolean) -> Unit,
    emptyCylinderCollected: Boolean,
    onEmptyCylinderChange: (Boolean) -> Unit,
    emptyCylinderSerial: String,
    onEmptySerialChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Surface(
            color = Color(0xFFEFF6FF),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "OGRA Non-Technical Installation Standard: Perform mandatory soapy bubble test and verify tamper seal before customer sign-off.",
                    fontSize = 11.sp,
                    color = Color(0xFF0369A1),
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("Mandatory Safety Checklist:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlueGasDark)
        Spacer(modifier = Modifier.height(6.dp))

        SafetyCheckItem(
            checked = leakCheckPassed,
            onCheckedChange = onLeakCheckChange,
            title = "Bubble Leak Test Passed",
            subtitle = "Soapy solution applied to regulator joint; zero bubbles observed.",
            testTag = "checklist_leak_test"
        )

        SafetyCheckItem(
            checked = valveIntact,
            onCheckedChange = onValveIntactChange,
            title = "Protective Seal & Valve Intact",
            subtitle = "Blue tamper-evident seal broken in front of customer. Threading undamaged.",
            testTag = "checklist_valve_intact"
        )

        SafetyCheckItem(
            checked = ventilatedArea,
            onCheckedChange = onVentilatedAreaChange,
            title = "Upright & Ventilated Placement",
            subtitle = "Cylinder kept standing vertically in an airy room, away from open sparks.",
            testTag = "checklist_ventilated_area"
        )

        SafetyCheckItem(
            checked = hoseConnectionSecured,
            onCheckedChange = onHoseSecuredChange,
            title = "Hose Pipe Clamped Firmly",
            subtitle = "Rubber tubing free of cracks/kinks; metal hose clamp locked firmly.",
            testTag = "checklist_hose_secured"
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = Color(0xFFE2E8F0))
        Spacer(modifier = Modifier.height(10.dp))

        // Empty Cylinder Section
        Text("Empty Cylinder Exchange (Optional):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlueGasDark)
        Spacer(modifier = Modifier.height(4.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = if (emptyCylinderCollected) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (emptyCylinderCollected) Color(0xFF86EFAC) else Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = emptyCylinderCollected,
                        onCheckedChange = onEmptyCylinderChange,
                        modifier = Modifier.testTag("checklist_empty_cylinder_toggle")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Customer Returned Empty Cylinder", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Eligible for security deposit waiver/rebate", fontSize = 10.sp, color = Color.Gray)
                    }
                }

                if (emptyCylinderCollected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emptyCylinderSerial,
                        onValueChange = onEmptySerialChange,
                        label = { Text("Returned Empty Serial Number", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = BlueGasPrimary) },
                        trailingIcon = {
                            TextButton(onClick = { onEmptySerialChange("BG-LHR-OLD-${(100..999).random()}") }) {
                                Text("Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("empty_serial_input"),
                        singleLine = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyCheckItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onCheckedChange(!checked) }
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) Color(0xFFF0FDF4) else Color(0xFFFFFFFF)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (checked) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = if (checked) Color(0xFF15803D) else BlueGasDark
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 14.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 2: PAYMENT & CASH COLLECTION CONFIRMATION
// -------------------------------------------------------------
@Composable
private fun PaymentConfirmationStep(
    order: Order,
    isCod: Boolean,
    cashCollectedConfirmed: Boolean,
    onCashConfirmedChange: (Boolean) -> Unit,
    tenderedAmountText: String,
    onTenderedAmountChange: (String) -> Unit,
    changePaisa: Long
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (!isCod) {
            // Prepaid / Online Hosted Card
            Surface(
                color = Color(0xFFF0FDF4),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Prepaid Online Order",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF15803D)
                    )
                    Text(
                        "No cash collection required at doorstep.",
                        fontSize = 12.sp,
                        color = Color(0xFF166534)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Paid Online:", fontSize = 11.sp, color = Color.Gray)
                            Text(formatPaisaToPkr(order.totalAmountPaisa), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BlueGasPrimary)
                        }
                    }
                }
            }
        } else {
            // Cash on Delivery (COD) Flow
            Surface(
                color = Color(0xFFFFFBEB),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Cash On Delivery (COD)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF92400E))
                            Text("Exact collection mandatory before handover", fontSize = 10.sp, color = Color(0xFFB45309))
                        }
                        Surface(
                            color = Color(0xFFF59E0B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "COD DUE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Financial breakdown
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cylinder SKU Refill:", fontSize = 11.sp, color = Color.Gray)
                            Text(formatPaisaToPkr(order.subtotalPaisa), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        if (order.depositTotalPaisa > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Security Deposit:", fontSize = 11.sp, color = Color.Gray)
                                Text(formatPaisaToPkr(order.depositTotalPaisa), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Delivery & Safety Fee:", fontSize = 11.sp, color = Color.Gray)
                            Text(formatPaisaToPkr(order.deliveryFeePaisa + order.platformFeePaisa), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFFE2E8F0))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Cash To Collect:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlueGasDark)
                            Text(formatPaisaToPkr(order.totalAmountPaisa), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF15803D))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cash Tender Calculator
            Text("Cash Tendered by Customer (PKR):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BlueGasDark)
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = tenderedAmountText,
                onValueChange = onTenderedAmountChange,
                leadingIcon = { Text("Rs.", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BlueGasPrimary, modifier = Modifier.padding(start = 10.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cash_tendered_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Quick denomination helper chips
            val exactPkr = order.totalAmountPaisa / 100
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = { onTenderedAmountChange(exactPkr.toString()) },
                    label = { Text("Exact (Rs. $exactPkr)", fontSize = 10.sp) }
                )
                AssistChip(
                    onClick = { onTenderedAmountChange("4000") },
                    label = { Text("Rs. 4,000", fontSize = 10.sp) }
                )
                AssistChip(
                    onClick = { onTenderedAmountChange("5000") },
                    label = { Text("Rs. 5,000", fontSize = 10.sp) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Change to return banner
            Surface(
                color = if (changePaisa > 0) Color(0xFFEFF6FF) else Color(0xFFF1F5F9),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Change to Return to Customer:", fontSize = 11.sp, color = Color.Gray)
                        Text(formatPaisaToPkr(changePaisa), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (changePaisa > 0) Color(0xFF0284C7) else Color(0xFF64748B))
                    }
                    if (changePaisa > 0) {
                        Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(4.dp)) {
                            Text("Handover Change", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1D4ED8), modifier = Modifier.padding(6.dp, 2.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Explicit Confirmation Checkbox
            Card(
                colors = CardDefaults.cardColors(containerColor = if (cashCollectedConfirmed) Color(0xFFF0FDF4) else Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (cashCollectedConfirmed) Color(0xFF86EFAC) else Color(0xFFCBD5E1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCashConfirmedChange(!cashCollectedConfirmed) }
                    .testTag("cash_confirm_card")
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = cashCollectedConfirmed,
                        onCheckedChange = onCashConfirmedChange,
                        modifier = Modifier.testTag("cash_confirm_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("I confirm full cash received & counted", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (cashCollectedConfirmed) Color(0xFF15803D) else BlueGasDark)
                        Text("Amount: ${formatPaisaToPkr(order.totalAmountPaisa)} will be reconciled in rider wallet.", fontSize = 10.sp, color = Color(0xFF64748B))
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 3: PHOTO EVIDENCE CAPTURE
// -------------------------------------------------------------
@Composable
private fun PhotoEvidenceStep(
    order: Order,
    zoneLabel: String,
    photoCaptured: Boolean,
    photoCaptureTimestamp: Long,
    photoType: String,
    isShutterTriggered: Boolean,
    onPhotoTypeChange: (String) -> Unit,
    onCaptureClick: () -> Unit,
    onRetakeClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()) }
    val timeFormatted = remember(photoCaptureTimestamp) { dateFormat.format(Date(photoCaptureTimestamp)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Photo Proof of Delivery:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlueGasDark)
            // Toggle angle
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (photoType == "DOORSTEP") BlueGasPrimary else Color(0xFFF1F5F9),
                    modifier = Modifier.clickable { onPhotoTypeChange("DOORSTEP") }
                ) {
                    Text(
                        "Doorstep",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (photoType == "DOORSTEP") Color.White else Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (photoType == "VALVE_SEAL") BlueGasPrimary else Color(0xFFF1F5F9),
                    modifier = Modifier.clickable { onPhotoTypeChange("VALVE_SEAL") }
                ) {
                    Text(
                        "Seal Check",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (photoType == "VALVE_SEAL") Color.White else Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Camera Viewfinder Box / Photo Evidence Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, if (photoCaptured) Color(0xFF22C55E) else Color(0xFF334155), RoundedCornerShape(12.dp))
                .testTag("camera_viewfinder_container"),
            contentAlignment = Alignment.Center
        ) {
            if (!photoCaptured) {
                // Live Viewfinder Mockup
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Camera Viewfinder",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Align Cylinder at Doorstep",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        "Make sure the Blue Gas seal & QR barcode are clearly visible in the frame",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                // Viewfinder Crosshairs / Corners
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    val len = 20.dp.toPx()
                    val color = Color(0xFF38BDF8)

                    // Top Left
                    drawLine(color, Offset(16.dp.toPx(), 16.dp.toPx()), Offset(16.dp.toPx() + len, 16.dp.toPx()), stroke)
                    drawLine(color, Offset(16.dp.toPx(), 16.dp.toPx()), Offset(16.dp.toPx(), 16.dp.toPx() + len), stroke)

                    // Top Right
                    drawLine(color, Offset(size.width - 16.dp.toPx(), 16.dp.toPx()), Offset(size.width - 16.dp.toPx() - len, 16.dp.toPx()), stroke)
                    drawLine(color, Offset(size.width - 16.dp.toPx(), 16.dp.toPx()), Offset(size.width - 16.dp.toPx(), 16.dp.toPx() + len), stroke)

                    // Bottom Left
                    drawLine(color, Offset(16.dp.toPx(), size.height - 16.dp.toPx()), Offset(16.dp.toPx() + len, size.height - 16.dp.toPx()), stroke)
                    drawLine(color, Offset(16.dp.toPx(), size.height - 16.dp.toPx()), Offset(16.dp.toPx(), size.height - 16.dp.toPx() - len), stroke)

                    // Bottom Right
                    drawLine(color, Offset(size.width - 16.dp.toPx(), size.height - 16.dp.toPx()), Offset(size.width - 16.dp.toPx() - len, size.height - 16.dp.toPx()), stroke)
                    drawLine(color, Offset(size.width - 16.dp.toPx(), size.height - 16.dp.toPx()), Offset(size.width - 16.dp.toPx(), size.height - 16.dp.toPx() - len), stroke)
                }
            } else {
                // Captured Photo Simulation
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Surface(
                            color = Color(0xFF0284C7).copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Doorstep Cylinder Verification Captured",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            "Tamper Seal #${order.allocatedSerial ?: "BG-LHR-002"} • Tamper Lock OK",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Real-time Geotag Watermark Overlay (Bottom of Viewfinder)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "31.5204° N, 74.3587° E • $zoneLabel",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = timeFormatted,
                        color = Color(0xFFCBD5E1),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ORDER: ${order.id} | CYL: ${order.allocatedSerial ?: "SERIAL-OK"}",
                        color = Color(0xFF38BDF8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "OGRA COMPLIANT POD",
                        color = Color(0xFF22C55E),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Camera Action Button Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!photoCaptured) {
                Button(
                    onClick = onCaptureClick,
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("capture_photo_button")
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Capture Evidence Photo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Evidence Photo Verified", color = Color(0xFF15803D), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onRetakeClick,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("retake_photo_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retake", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// STEP 4: SIGNATURE CAPTURE CANVAS
// -------------------------------------------------------------
@Composable
private fun SignatureCaptureStep(
    signaturePoints: MutableList<Offset>,
    signerName: String,
    onSignerNameChange: (String) -> Unit,
    signerRelation: String,
    onSignerRelationChange: (String) -> Unit,
    podCustomerNotes: String,
    onCustomerNotesChange: (String) -> Unit,
    quickSignAdopted: Boolean,
    onAdoptQuickSign: () -> Unit,
    onClearSignature: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Customer Digital Signature:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlueGasDark)
        Text("Customer draws signature on canvas to confirm acceptance & inspection.", fontSize = 11.sp, color = Color(0xFF64748B))

        Spacer(modifier = Modifier.height(8.dp))

        // Touch Signature Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF8FAFC))
                .border(
                    width = 1.5.dp,
                    color = if (signaturePoints.isNotEmpty() || quickSignAdopted) Color(0xFF0284C7) else Color(0xFFCBD5E1),
                    shape = RoundedCornerShape(10.dp)
                )
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            signaturePoints.add(offset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            signaturePoints.add(change.position)
                        }
                    )
                }
                .testTag("signature_canvas")
        ) {
            // Baseline and watermarked prompt
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Baseline guide line
                val baselineY = size.height * 0.78f
                drawLine(
                    color = Color(0xFFE2E8F0),
                    start = Offset(24.dp.toPx(), baselineY),
                    end = Offset(size.width - 24.dp.toPx(), baselineY),
                    strokeWidth = 1.5.dp.toPx()
                )

                if (signaturePoints.size > 1) {
                    val path = Path().apply {
                        moveTo(signaturePoints.first().x, signaturePoints.first().y)
                        for (i in 1 until signaturePoints.size) {
                            val pt = signaturePoints[i]
                            lineTo(pt.x, pt.y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF0F172A),
                        style = Stroke(
                            width = 3.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }

            if (signaturePoints.isEmpty() && !quickSignAdopted) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Draw, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Sign here with finger",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text("X ___________________________________", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                }
            } else if (quickSignAdopted && signaturePoints.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Digitally Signed & Verified",
                        color = Color(0xFF0284C7),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Cursive
                    )
                    Text(
                        signerName,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }
            }

            // Canvas action chips (Clear / Quick Sign)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (signaturePoints.isNotEmpty() || quickSignAdopted) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier
                            .clickable { onClearSignature() }
                            .testTag("clear_signature_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color(0xFFDC2626), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Clear", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        }
                    }
                }

                if (signaturePoints.isEmpty() && !quickSignAdopted) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE0E7FF),
                        modifier = Modifier
                            .clickable { onAdoptQuickSign() }
                            .testTag("quick_sign_button")
                    ) {
                        Text(
                            "Use Name as Sign",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4338CA),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Signer Metadata Form
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = signerName,
                onValueChange = onSignerNameChange,
                label = { Text("Signer Name", fontSize = 11.sp) },
                modifier = Modifier
                    .weight(1.2f)
                    .height(52.dp)
                    .testTag("signer_name_input"),
                singleLine = true
            )

            // Relationship dropdown / chips
            Column(modifier = Modifier.weight(1f)) {
                Text("Relationship:", fontSize = 10.sp, color = Color.Gray)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val relations = listOf("Self", "Family", "Guard")
                    relations.forEach { rel ->
                        val isSel = (rel == "Self" && signerRelation.contains("Himself")) ||
                                (rel == "Family" && signerRelation.contains("Family")) ||
                                (rel == "Guard" && signerRelation.contains("Guard"))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSel) BlueGasPrimary else Color(0xFFF1F5F9),
                            modifier = Modifier
                                .clickable {
                                    onSignerRelationChange(
                                        when (rel) {
                                            "Self" -> "Customer Himself"
                                            "Family" -> "Family Member"
                                            else -> "Security Guard / Staff"
                                        }
                                    )
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                rel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSel) Color.White else Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = podCustomerNotes,
            onValueChange = onCustomerNotesChange,
            label = { Text("Rider Notes / Comments (Optional)", fontSize = 11.sp) },
            placeholder = { Text("e.g. Delivered to 1st floor kitchen, seal tested with soapy water.", fontSize = 10.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag("pod_notes_input"),
            maxLines = 2
        )
    }
}

// -------------------------------------------------------------
// FOOTER ACTION BUTTONS
// -------------------------------------------------------------
@Composable
private fun PodFooterButtons(
    currentStep: PodStep,
    isNextEnabled: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .height(44.dp)
                .testTag("pod_back_button")
        ) {
            Text(if (currentStep == PodStep.SAFETY_CHECKLIST) "Cancel" else "Back")
        }

        Button(
            onClick = onNext,
            enabled = isNextEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (currentStep == PodStep.SIGNATURE_CAPTURE) Color(0xFF16A34A) else BlueGasPrimary
            ),
            modifier = Modifier
                .height(44.dp)
                .testTag("pod_next_button")
        ) {
            if (currentStep == PodStep.SIGNATURE_CAPTURE) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Complete Delivery & Seal POD", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            } else {
                Text(
                    text = when (currentStep) {
                        PodStep.SAFETY_CHECKLIST -> "Next: Payment & COD"
                        PodStep.PAYMENT_CONFIRMATION -> "Next: Photo Evidence"
                        PodStep.PHOTO_EVIDENCE -> "Next: Customer Signature"
                        else -> "Next"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}
