package com.example.bluegas.ui.rider

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.PathEffect
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
    SAFETY_CHECKLIST("Safety Check", "OGRA Installation Standards"),
    PAYMENT_CONFIRMATION("Payment", "Verify & Collect"),
    PHOTO_EVIDENCE("Photo Evidence", "Capture Geotagged Proof"),
    SIGNATURE_CAPTURE("Signature", "Customer Acknowledgment")
}

@Composable
fun PodCaptureDialog(
    order: Order,
    store: BlueGasStore,
    onDismiss: () -> Unit,
    onCompleted: (Boolean, String) -> Unit
) {
    var currentStep by remember { mutableStateOf(PodStep.SAFETY_CHECKLIST) }

    // Step 1: Safety Checklist States
    var leakCheckPassed by remember { mutableStateOf(false) }
    var valveIntact by remember { mutableStateOf(false) }
    var ventilatedArea by remember { mutableStateOf(false) }
    var hoseConnectionSecured by remember { mutableStateOf(false) }
    var emptyCylinderCollected by remember { mutableStateOf(false) }
    var emptyCylinderSerial by remember { mutableStateOf("BG-LHR-OLD-812") }

    // Step 2: Payment States
    val isCod = order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY
    var tenderedPaisa by remember(order.totalAmountPaisa) {
        mutableLongStateOf(if (isCod) order.totalAmountPaisa else 0L)
    }
    var cashCollectedConfirmed by remember { mutableStateOf(!isCod) }

    // Step 3: Photo Evidence States
    var photoCaptured by remember { mutableStateOf(false) }
    var photoType by remember { mutableStateOf("DOORSTEP") } // DOORSTEP or VALVE_SEAL
    val photoCaptureTimestamp = remember { System.currentTimeMillis() }

    // Step 4: Signature Canvas States
    val signaturePoints = remember { mutableStateListOf<Offset>() }
    var quickSignAdopted by remember { mutableStateOf(false) }
    val customer = store.customers.find { it.id == order.customerId }
    var signerName by remember(customer?.name) { mutableStateOf(customer?.name ?: "Customer") }
    var signerRelation by remember { mutableStateOf("Self") }
    var riderNotes by remember { mutableStateOf("") }

    val activeRider = store.getActiveRider() ?: store.riders.first()
    val customerAddress = store.addresses.find { it.id == order.addressId }
    val zoneLabel = customerAddress?.let { "${it.city} (${it.zoneId})" } ?: activeRider.zoneName

    // Step Validations
    val isChecklistValid = leakCheckPassed && valveIntact && ventilatedArea && hoseConnectionSecured
    val isPaymentValid = !isCod || (cashCollectedConfirmed && tenderedPaisa >= order.totalAmountPaisa)
    val isPhotoValid = photoCaptured
    val isSignatureValid = signaturePoints.size > 8 || quickSignAdopted

    val isCurrentStepValid = when (currentStep) {
        PodStep.SAFETY_CHECKLIST -> isChecklistValid
        PodStep.PAYMENT_CONFIRMATION -> isPaymentValid
        PodStep.PHOTO_EVIDENCE -> isPhotoValid
        PodStep.SIGNATURE_CAPTURE -> isSignatureValid
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .testTag("pod_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = BlueGasPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Proof of Delivery (POD)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = BlueGasDark
                            )
                        }
                        Text(
                            text = "Order: ${order.id} • ${order.allocatedSerial ?: "Serial Bound"}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF64748B)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stepper Indicator
                PodStepperIndicator(currentStep = currentStep)

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Body Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (currentStep) {
                        PodStep.SAFETY_CHECKLIST -> {
                            PodSafetyChecklistStep(
                                leakCheckPassed = leakCheckPassed,
                                onLeakCheckChange = { leakCheckPassed = it },
                                valveIntact = valveIntact,
                                onValveIntactChange = { valveIntact = it },
                                ventilatedArea = ventilatedArea,
                                onVentilatedAreaChange = { ventilatedArea = it },
                                hoseConnectionSecured = hoseConnectionSecured,
                                onHoseConnectionChange = { hoseConnectionSecured = it },
                                emptyCylinderCollected = emptyCylinderCollected,
                                onEmptyCylinderToggle = { emptyCylinderCollected = it },
                                emptyCylinderSerial = emptyCylinderSerial,
                                onEmptyCylinderSerialChange = { emptyCylinderSerial = it }
                            )
                        }
                        PodStep.PAYMENT_CONFIRMATION -> {
                            PodPaymentConfirmationStep(
                                order = order,
                                isCod = isCod,
                                tenderedPaisa = tenderedPaisa,
                                onTenderedPaisaChange = { tenderedPaisa = it },
                                cashCollectedConfirmed = cashCollectedConfirmed,
                                onCashCollectedConfirmedChange = { cashCollectedConfirmed = it }
                            )
                        }
                        PodStep.PHOTO_EVIDENCE -> {
                            PodPhotoEvidenceStep(
                                order = order,
                                zoneLabel = zoneLabel,
                                photoType = photoType,
                                onPhotoTypeChange = { photoType = it },
                                photoCaptured = photoCaptured,
                                onPhotoCapture = { photoCaptured = true },
                                onPhotoRetake = { photoCaptured = false },
                                timestamp = photoCaptureTimestamp
                            )
                        }
                        PodStep.SIGNATURE_CAPTURE -> {
                            PodSignatureCaptureStep(
                                signaturePoints = signaturePoints,
                                onClearSignature = {
                                    signaturePoints.clear()
                                    quickSignAdopted = false
                                },
                                quickSignAdopted = quickSignAdopted,
                                onAdoptQuickSign = { quickSignAdopted = true },
                                signerName = signerName,
                                onSignerNameChange = { signerName = it },
                                signerRelation = signerRelation,
                                onSignerRelationChange = { signerRelation = it },
                                riderNotes = riderNotes,
                                onRiderNotesChange = { riderNotes = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer Buttons
                PodFooterButtons(
                    currentStep = currentStep,
                    isCurrentStepValid = isCurrentStepValid,
                    onBack = {
                        val allSteps = PodStep.entries
                        val prevIndex = allSteps.indexOf(currentStep) - 1
                        if (prevIndex >= 0) {
                            currentStep = allSteps[prevIndex]
                        }
                    },
                    onNext = {
                        val allSteps = PodStep.entries
                        val nextIndex = allSteps.indexOf(currentStep) + 1
                        if (nextIndex < allSteps.size) {
                            currentStep = allSteps[nextIndex]
                        } else {
                            // Step 4 Complete: Trigger completeHandover
                            val changePaisa = if (isCod) (tenderedPaisa - order.totalAmountPaisa).coerceAtLeast(0L) else null
                            val podNote = buildString {
                                append("SAFETY: Leak=${leakCheckPassed}, Valve=${valveIntact}, Vent=${ventilatedArea}, Hose=${hoseConnectionSecured}")
                                append(" | PAYMENT: ${if (isCod) "COD Collected" else "Prepaid"}")
                                append(" | PHOTO: ${if (photoCaptured) photoType else "Not Captured"}")
                                append(" | SIGNED_BY: $signerName ($signerRelation)")
                                if (riderNotes.isNotBlank()) append(" | NOTES: $riderNotes")
                            }

                            val result = store.completeHandover(
                                orderId = order.id,
                                nonTechInstallationPassed = isChecklistValid,
                                proofOfDeliveryNote = podNote,
                                collectedCashPaisa = if (isCod) order.totalAmountPaisa else null,
                                actor = activeRider.name,
                                emptyCylinderCollected = emptyCylinderCollected,
                                emptyCylinderSerial = if (emptyCylinderCollected) emptyCylinderSerial else null,
                                customerSignature = if (quickSignAdopted) "Adopted: $signerName" else "Touch Verified",
                                photoEvidenceCaptured = photoCaptured,
                                signerName = signerName,
                                changeReturnedPaisa = changePaisa
                            )
                            onCompleted(result.first, result.second)
                        }
                    },
                    onCancel = onDismiss
                )
            }
        }
    }
}

@Composable
fun PodStepperIndicator(currentStep: PodStep) {
    val allSteps = PodStep.entries
    val currentIndex = allSteps.indexOf(currentStep)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        allSteps.forEachIndexed { index, step ->
            val isPassed = index < currentIndex
            val isCurrent = index == currentIndex

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isPassed -> Color(0xFF16A34A)
                                isCurrent -> BlueGasPrimary
                                else -> Color(0xFFE2E8F0)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPassed) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = (index + 1).toString(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) Color.White else Color(0xFF64748B)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = step.title,
                    fontSize = 10.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) BlueGasPrimary else Color(0xFF64748B),
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }

            if (index < allSteps.size - 1) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .weight(0.6f)
                        .background(if (index < currentIndex) Color(0xFF16A34A) else Color(0xFFE2E8F0))
                )
            }
        }
    }
}

@Composable
private fun PodSafetyChecklistStep(
    leakCheckPassed: Boolean,
    onLeakCheckChange: (Boolean) -> Unit,
    valveIntact: Boolean,
    onValveIntactChange: (Boolean) -> Unit,
    ventilatedArea: Boolean,
    onVentilatedAreaChange: (Boolean) -> Unit,
    hoseConnectionSecured: Boolean,
    onHoseConnectionChange: (Boolean) -> Unit,
    emptyCylinderCollected: Boolean,
    onEmptyCylinderToggle: (Boolean) -> Unit,
    emptyCylinderSerial: String,
    onEmptyCylinderSerialChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("pod_step_safety"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // OGRA Compliance Banner
        Surface(
            color = Color(0xFFEFF6FF),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = BlueGasPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "OGRA Non-Technical Installation Standards",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF1E3A8A)
                    )
                    Text(
                        text = "All 4 safety checks are legally mandated before handing over certified LPG.",
                        fontSize = 10.sp,
                        color = Color(0xFF3B82F6),
                        lineHeight = 14.sp
                    )
                }
            }
        }

        Text(
            text = "Mandatory Delivery Checklist",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = BlueGasDark
        )

        SafetyCheckItem(
            title = "1. Bubble Leak Test Passed",
            subtitle = "Verified zero bubbling at cylinder valve and regulator joint with soapy water.",
            checked = leakCheckPassed,
            onCheckedChange = onLeakCheckChange,
            testTag = "check_bubble_leak"
        )

        SafetyCheckItem(
            title = "2. Protective Seal & Valve Intact",
            subtitle = "Safety tamper seal verified, valve handwheel and collar are undamaged.",
            checked = valveIntact,
            onCheckedChange = onValveIntactChange,
            testTag = "check_valve_intact"
        )

        SafetyCheckItem(
            title = "3. Upright & Ventilated Placement",
            subtitle = "Cylinder placed strictly upright on level floor in a ventilated area.",
            checked = ventilatedArea,
            onCheckedChange = onVentilatedAreaChange,
            testTag = "check_ventilated"
        )

        SafetyCheckItem(
            title = "4. Hose Pipe Securely Clamped",
            subtitle = "Metallic jubilee clips tightened securely on regulator and stove joints without kinks.",
            checked = hoseConnectionSecured,
            onCheckedChange = onHoseConnectionChange,
            testTag = "check_hose_clamped"
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Empty Cylinder Collection Section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Collect Customer Empty Cylinder?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Record return tare unit for depot refurbishment.",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Switch(
                        checked = emptyCylinderCollected,
                        onCheckedChange = onEmptyCylinderToggle,
                        modifier = Modifier.testTag("empty_cylinder_toggle")
                    )
                }

                if (emptyCylinderCollected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emptyCylinderSerial,
                        onValueChange = onEmptyCylinderSerialChange,
                        label = { Text("Old Cylinder Serial Number", fontSize = 11.sp) },
                        placeholder = { Text("e.g. BG-LHR-OLD-812", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("empty_serial_input")
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetyCheckItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        color = if (checked) Color(0xFFF0FDF4) else Color.White,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (checked) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = Color(0xFF16A34A))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (checked) Color(0xFF166534) else Color(0xFF0F172A)
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

@Composable
private fun PodPaymentConfirmationStep(
    order: Order,
    isCod: Boolean,
    tenderedPaisa: Long,
    onTenderedPaisaChange: (Long) -> Unit,
    cashCollectedConfirmed: Boolean,
    onCashCollectedConfirmedChange: (Boolean) -> Unit
) {
    val totalAmountPaisa = order.totalAmountPaisa
    val changePaisa = (tenderedPaisa - totalAmountPaisa).coerceAtLeast(0L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("pod_step_payment"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isCod) {
            // Prepaid / Online Hosted Confirmation
            Surface(
                color = Color(0xFFF0FDF4),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Payment Received Online",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF166534)
                    )
                    Text(
                        text = "Total amount of ${formatPaisaToPkr(totalAmountPaisa)} was captured via Online Hosted Gateway.",
                        fontSize = 11.sp,
                        color = Color(0xFF15803D),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "NO CASH COLLECTION NEEDED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF166534),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        } else {
            // Cash on Delivery (COD) Collection
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Amount to Collect (COD)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF78350F)
                            )
                            Text(
                                text = "Inclusive of all taxes & cylinder charges",
                                fontSize = 9.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                        Text(
                            text = formatPaisaToPkr(totalAmountPaisa),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color(0xFFB45309),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color(0xFFFDE68A))
                    Spacer(modifier = Modifier.height(8.dp))

                    order.items.forEach { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${line.quantity}x ${line.productName}", fontSize = 11.sp, color = Color(0xFF451A03))
                            Text(formatPaisaToPkr(line.subtotalPaisa), fontSize = 11.sp, color = Color(0xFF451A03))
                        }
                    }
                    if (order.depositTotalPaisa > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Security Deposit", fontSize = 11.sp, color = Color(0xFF92400E))
                            Text(formatPaisaToPkr(order.depositTotalPaisa), fontSize = 11.sp, color = Color(0xFF92400E))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Delivery & Platform Fees", fontSize = 11.sp, color = Color(0xFF451A03))
                        Text(formatPaisaToPkr(order.deliveryFeePaisa + order.platformFeePaisa), fontSize = 11.sp, color = Color(0xFF451A03))
                    }
                }
            }

            // Cash Tendered Input & Calculator
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Cash Tendered by Customer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = BlueGasDark
                )
                Spacer(modifier = Modifier.height(4.dp))

                var tenderInputText by remember { mutableStateOf((tenderedPaisa / 100).toString()) }

                OutlinedTextField(
                    value = tenderInputText,
                    onValueChange = { input ->
                        tenderInputText = input.filter { it.isDigit() }
                        val num = tenderInputText.toLongOrNull() ?: 0L
                        onTenderedPaisaChange(num * 100L)
                    },
                    prefix = { Text("PKR ", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tender_amount_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick denomination chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val exactPkr = totalAmountPaisa / 100
                    AssistChip(
                        onClick = {
                            tenderInputText = exactPkr.toString()
                            onTenderedPaisaChange(totalAmountPaisa)
                        },
                        label = { Text("Exact", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {
                            tenderInputText = "4000"
                            onTenderedPaisaChange(400000L)
                        },
                        label = { Text("Rs. 4,000", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = {
                            tenderInputText = "5000"
                            onTenderedPaisaChange(500000L)
                        },
                        label = { Text("Rs. 5,000", fontSize = 10.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (changePaisa > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Change to Return:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF166534)
                            )
                            Text(
                                text = formatPaisaToPkr(changePaisa),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color(0xFF166534)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Confirmation Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCashCollectedConfirmedChange(!cashCollectedConfirmed) }
                ) {
                    Checkbox(
                        checked = cashCollectedConfirmed,
                        onCheckedChange = onCashCollectedConfirmedChange,
                        modifier = Modifier.testTag("cash_confirmed_checkbox")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I confirm I have physically collected ${formatPaisaToPkr(totalAmountPaisa)} in cash.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F172A)
                    )
                }
            }
        }
    }
}

@Composable
private fun PodPhotoEvidenceStep(
    order: Order,
    zoneLabel: String,
    photoType: String,
    onPhotoTypeChange: (String) -> Unit,
    photoCaptured: Boolean,
    onPhotoCapture: () -> Unit,
    onPhotoRetake: () -> Unit,
    timestamp: Long
) {
    val timeFormatted = remember(timestamp) {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.US)
        sdf.format(Date(timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("pod_step_photo"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Geotagged Photo Verification",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = BlueGasDark
        )

        // Photo Type Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = photoType == "DOORSTEP",
                onClick = { onPhotoTypeChange("DOORSTEP") },
                label = { Text("Doorstep Handover", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(14.dp)) },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = photoType == "VALVE_SEAL",
                onClick = { onPhotoTypeChange("VALVE_SEAL") },
                label = { Text("Valve Safety Seal", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(14.dp)) },
                modifier = Modifier.weight(1f)
            )
        }

        // Camera Viewfinder Canvas Mockup
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .border(2.dp, if (photoCaptured) Color(0xFF22C55E) else Color(0xFF334155), RoundedCornerShape(12.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 4f
                val cornerLen = 30f
                val cornerColor = if (photoCaptured) Color(0xFF22C55E) else Color.White

                // Top-Left corner
                drawLine(cornerColor, Offset(16f, 16f), Offset(16f + cornerLen, 16f), strokeW)
                drawLine(cornerColor, Offset(16f, 16f), Offset(16f, 16f + cornerLen), strokeW)

                // Top-Right corner
                drawLine(cornerColor, Offset(size.width - 16f, 16f), Offset(size.width - 16f - cornerLen, 16f), strokeW)
                drawLine(cornerColor, Offset(size.width - 16f, 16f), Offset(size.width - 16f, 16f + cornerLen), strokeW)

                // Bottom-Left corner
                drawLine(cornerColor, Offset(16f, size.height - 16f), Offset(16f + cornerLen, size.height - 16f), strokeW)
                drawLine(cornerColor, Offset(16f, size.height - 16f), Offset(16f, size.height - 16f - cornerLen), strokeW)

                // Bottom-Right corner
                drawLine(cornerColor, Offset(size.width - 16f, size.height - 16f), Offset(size.width - 16f - cornerLen, size.height - 16f), strokeW)
                drawLine(cornerColor, Offset(size.width - 16f, size.height - 16f), Offset(size.width - 16f, size.height - 16f - cornerLen), strokeW)

                // Center crosshair
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawLine(Color(0x66FFFFFF), Offset(cx - 15f, cy), Offset(cx + 15f, cy), 2f)
                drawLine(Color(0x66FFFFFF), Offset(cx, cy - 15f), Offset(cx, cy + 15f), 2f)
            }

            // Watermark Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = Color(0x99000000),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (photoCaptured) "✓ PHOTO EVIDENCE CAPTURED" else "ALIGN CYLINDER IN FRAME",
                        color = if (photoCaptured) Color(0xFF86EFAC) else Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Watermark telemetry
                Surface(
                    color = Color(0xB3000000),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "📍 33.6844°N, 73.0479°E • $zoneLabel",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "⏰ $timeFormatted",
                            color = Color(0xFF93C5FD),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Order: ${order.id} | Serial: ${order.allocatedSerial ?: "PENDING"}",
                            color = Color(0xFF86EFAC),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "OGRA COMPLIANT POD WATERMARK",
                            color = Color(0xFFFDE68A),
                            fontSize = 8.sp,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Action Trigger
        if (!photoCaptured) {
            Button(
                onClick = onPhotoCapture,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("capture_photo_button"),
                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Capture Proof Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photo Locked & Timestamped", color = Color(0xFF166534), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedButton(
                    onClick = onPhotoRetake,
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Retake", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun PodSignatureCaptureStep(
    signaturePoints: MutableList<Offset>,
    onClearSignature: () -> Unit,
    quickSignAdopted: Boolean,
    onAdoptQuickSign: () -> Unit,
    signerName: String,
    onSignerNameChange: (String) -> Unit,
    signerRelation: String,
    onSignerRelationChange: (String) -> Unit,
    riderNotes: String,
    onRiderNotesChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("pod_step_signature"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Customer Digital Signature",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = BlueGasDark
        )
        Text(
            text = "Customer or authorized household recipient must sign on the canvas below.",
            fontSize = 10.sp,
            color = Color(0xFF64748B)
        )

        // Touch Signature Box
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (signaturePoints.size > 8 || quickSignAdopted) Color(0xFF22C55E) else Color(0xFF94A3B8)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                signaturePoints.add(change.position)
                            }
                        }
                ) {
                    // Dashed guide baseline at 70% height
                    val baselineY = size.height * 0.72f
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = Offset(20f, baselineY),
                        end = Offset(size.width - 20f, baselineY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )

                    // Draw actual captured touch path
                    if (signaturePoints.size > 1) {
                        val path = Path().apply {
                            moveTo(signaturePoints.first().x, signaturePoints.first().y)
                            for (i in 1 until signaturePoints.size) {
                                lineTo(signaturePoints[i].x, signaturePoints[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = BlueGasPrimary,
                            style = Stroke(
                                width = 4f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                if (quickSignAdopted) {
                    Surface(
                        color = Color(0xE6DCFCE7),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "✍ $signerName",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534),
                                fontFamily = FontFamily.Cursive
                            )
                            Text(
                                text = "Adopted Digital Name Signature",
                                fontSize = 10.sp,
                                color = Color(0xFF15803D)
                            )
                        }
                    }
                } else if (signaturePoints.isEmpty()) {
                    Text(
                        text = "Sign here with finger / stylus",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier
                            .align(Alignment.Center)
                    )
                }

                // Clear button in top-right
                if (signaturePoints.isNotEmpty() || quickSignAdopted) {
                    TextButton(
                        onClick = onClearSignature,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Text("Clear", fontSize = 11.sp, color = Color(0xFFDC2626))
                    }
                }
            }
        }

        // Quick Sign Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(" OR ", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 8.dp))
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        OutlinedButton(
            onClick = onAdoptQuickSign,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .testTag("adopt_quick_sign_button")
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Use Name as Quick Sign: '$signerName'", fontSize = 11.sp)
        }

        // Signer Details Form
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = signerName,
                onValueChange = onSignerNameChange,
                label = { Text("Signer Name", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("signer_name_input")
            )

            // Relationship Picker
            var relExpanded by remember { mutableStateOf(false) }
            val relations = listOf("Self", "Family Member", "Security Guard", "Household Staff")

            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = signerRelation,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Relation", fontSize = 11.sp) },
                    trailingIcon = {
                        IconButton(onClick = { relExpanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = relExpanded, onDismissRequest = { relExpanded = false }) {
                    relations.forEach { r ->
                        DropdownMenuItem(
                            text = { Text(r, fontSize = 12.sp) },
                            onClick = {
                                onSignerRelationChange(r)
                                relExpanded = false
                            }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = riderNotes,
            onValueChange = onRiderNotesChange,
            label = { Text("Rider Handover Notes (Optional)", fontSize = 11.sp) },
            placeholder = { Text("e.g. Delivered to kitchen ground floor; leak test verified.", fontSize = 10.sp) },
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PodFooterButtons(
    currentStep: PodStep,
    isCurrentStepValid: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    val isFirstStep = currentStep == PodStep.SAFETY_CHECKLIST
    val isLastStep = currentStep == PodStep.SIGNATURE_CAPTURE

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFirstStep) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("pod_cancel_button")
            ) {
                Text("Cancel", fontSize = 12.sp)
            }
        } else {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("pod_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back", fontSize = 12.sp)
            }
        }

        Button(
            onClick = onNext,
            enabled = isCurrentStepValid,
            modifier = Modifier
                .weight(1.4f)
                .height(44.dp)
                .testTag(if (isLastStep) "pod_complete_button" else "pod_next_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isLastStep) Color(0xFF16A34A) else BlueGasPrimary
            )
        ) {
            if (isLastStep) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Complete Delivery", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("Next Step", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
            }
        }
    }
}
