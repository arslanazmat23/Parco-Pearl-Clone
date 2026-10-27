/**
 * Proof of Delivery (POD) multi-step capture flow for riders at customer doorstep.
 *
 * Implements R5.1 sequential delivery verification steps:
 * 1. Handover code (PIN) verification with 3-attempt safety unlock
 * 2. Non-technical installation checklist driven by platform configuration
 * 3. Empty cylinder exchange (conditional on REFILL_EXCHANGE orders)
 * 4. Cash on Delivery collection, live change calculation, and difference reasons
 * 5. Optional photo capture with explicit customer consent
 * 6. Customer signature canvas or inability reason capture
 * 7. Review and final delivery submission
 *
 * Reads: [SharedAppState.settings], [SharedAppState.customers]
 * Calls: [BlueGasStore.verifyHandoverPin], [BlueGasStore.completeHandover]
 */
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

/**
 * Logical steps in the proof-of-delivery capture wizard.
 *
 * @property title User-facing step title displayed in the header.
 */
enum class PodStep(val title: String) {
    PIN("Handover Code"),
    INSTALLATION("Installation"),
    EMPTY_CYLINDER("Empty Cylinder"),
    PAYMENT("Payment"),
    PHOTO("Photo"),
    SIGNATURE("Signature"),
    REVIEW("Review & Complete")
}

/**
 * Modal dialog for capturing doorstep delivery verification evidence before completing an order.
 *
 * @param order The active order being delivered.
 * @param store Application data store reference.
 * @param onDismiss Invoked when the dialog is cancelled or closed.
 * @param onCompleted Callback receiving success status and feedback message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodCaptureDialog(
    order: Order,
    store: BlueGasStore,
    onDismiss: () -> Unit,
    onCompleted: (Boolean, String) -> Unit
) {
    val snapshot by store.appState.collectAsStateWithLifecycle()
    val settings = snapshot.settings
    val isCod = order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY
    val isRefillExchange = order.orderType == OrderType.REFILL_EXCHANGE

    // Compute active steps dynamically based on order type and payment method
    val activeSteps = remember(order) {
        buildList {
            add(PodStep.PIN)
            add(PodStep.INSTALLATION)
            if (isRefillExchange) add(PodStep.EMPTY_CYLINDER)
            if (isCod) add(PodStep.PAYMENT)
            add(PodStep.PHOTO)
            add(PodStep.SIGNATURE)
            add(PodStep.REVIEW)
        }
    }

    var currentStepIndex by remember { mutableIntStateOf(0) }
    val currentStep = activeSteps[currentStepIndex]
    var showDiscardAlert by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Step 1: Handover Code (PIN) States
    var enteredPin by remember { mutableStateOf("") }
    var pinVerified by remember { mutableStateOf(false) }
    var pinFailedAttempts by remember { mutableIntStateOf(0) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var codeNotAvailableSelected by remember { mutableStateOf(false) }
    var pinNotVerifiedReason by remember { mutableStateOf("") }

    // Step 2: Installation States
    var installationResult by remember { mutableStateOf(InstallationResult.INSTALLED) }
    var notInstalledReason by remember { mutableStateOf("") }
    val checklistSelections = remember(settings.installationChecklistItems) {
        mutableStateMapOf<String, Boolean>().apply {
            settings.installationChecklistItems.forEach { put(it, false) }
        }
    }

    // Step 3: Empty Cylinder States (Refill only)
    var emptyCollected by remember { mutableStateOf(false) }
    var selectedBrand by remember(settings.approvedEmptyBrands) {
        mutableStateOf(settings.approvedEmptyBrands.firstOrNull() ?: "BLUE GAS")
    }
    var emptySerialInput by remember { mutableStateOf("") }

    // Step 4: Payment States (COD only)
    var cashReceivedRupees by remember(order.totalAmountPaisa) {
        mutableStateOf((order.totalAmountPaisa / 100).toString())
    }
    var cashReceivedConfirmed by remember { mutableStateOf(false) }
    var cashDifferenceReason by remember { mutableStateOf("") }

    val recPaisa = remember(cashReceivedRupees) {
        (cashReceivedRupees.trim().toLongOrNull() ?: 0L) * 100L
    }
    val cashDifference = recPaisa - order.totalAmountPaisa

    // Step 5: Photo States
    var photoConsent by remember { mutableStateOf(false) }
    var photoCaptured by remember { mutableStateOf(false) }
    var skipPhoto by remember { mutableStateOf(false) }
    var photoSkipReason by remember { mutableStateOf("") }

    // Step 6: Signature States
    val signaturePoints = remember { mutableStateListOf<Offset>() }
    var cannotSignSelected by remember { mutableStateOf(false) }
    var signatureUnavailableReason by remember { mutableStateOf("") }
    val customer = snapshot.customers.find { it.id == order.customerId }
    var signerName by remember(customer?.name) { mutableStateOf(customer?.name ?: "Customer") }

    // Step Validation Logic
    val isStepValid: Boolean = when (currentStep) {
        PodStep.PIN -> {
            pinVerified || (codeNotAvailableSelected && pinNotVerifiedReason.trim().isNotBlank())
        }
        PodStep.INSTALLATION -> {
            when (installationResult) {
                InstallationResult.INSTALLED -> {
                    settings.installationChecklistItems.isEmpty() ||
                            settings.installationChecklistItems.all { checklistSelections[it] == true }
                }
                InstallationResult.NOT_INSTALLED -> notInstalledReason.trim().isNotBlank()
                InstallationResult.NOT_APPLICABLE -> true
            }
        }
        PodStep.EMPTY_CYLINDER -> {
            // Optional to collect; if collected, brand is chosen from approved list
            true
        }
        PodStep.PAYMENT -> {
            if (!isCod) true
            else {
                cashReceivedConfirmed && (cashDifference == 0L || cashDifferenceReason.trim().isNotBlank())
            }
        }
        PodStep.PHOTO -> {
            (photoCaptured && photoConsent) || (skipPhoto && photoSkipReason.trim().isNotBlank())
        }
        PodStep.SIGNATURE -> {
            if (cannotSignSelected) {
                signatureUnavailableReason.trim().isNotBlank()
            } else {
                signaturePoints.isNotEmpty() && signerName.trim().isNotBlank()
            }
        }
        PodStep.REVIEW -> true
    }

    Dialog(
        onDismissRequest = { showDiscardAlert = true },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .testTag("pod_capture_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header (Pinned)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Proof of Delivery",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlueGasDark
                        )
                        Text(
                            text = "Step ${currentStepIndex + 1} of ${activeSteps.size}: ${currentStep.title}",
                            fontSize = 12.sp,
                            color = BlueGasPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    IconButton(
                        onClick = { showDiscardAlert = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close dialog")
                    }
                }

                // Step Progress Indicator
                LinearProgressIndicator(
                    progress = { (currentStepIndex + 1).toFloat() / activeSteps.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = BlueGasPrimary,
                    trackColor = Color(0xFFE2E8F0)
                )

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    when (currentStep) {
                        PodStep.PIN -> {
                            Text(
                                text = "Ask the customer for their 4-digit handover code.",
                                fontSize = 13.sp,
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = enteredPin,
                                onValueChange = {
                                    if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                        enteredPin = it
                                        pinError = null
                                    }
                                },
                                label = { Text("4-digit Handover Code") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                enabled = !pinVerified && !codeNotAvailableSelected,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 4.sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("handover_pin_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            if (pinError != null) {
                                Text(
                                    text = pinError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            if (!pinVerified && !codeNotAvailableSelected) {
                                Button(
                                    onClick = {
                                        val ok = store.verifyHandoverPin(order.id, enteredPin)
                                        if (ok) {
                                            pinVerified = true
                                            pinError = null
                                        } else {
                                            pinFailedAttempts++
                                            if (pinFailedAttempts >= 3) {
                                                pinError = "Code not accepted after 3 attempts."
                                            } else {
                                                pinError = "Incorrect code. ${3 - pinFailedAttempts} attempt(s) remaining."
                                            }
                                        }
                                    },
                                    enabled = enteredPin.length == 4,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("verify_pin_button")
                                ) {
                                    Text("Verify Code")
                                }
                            }

                            if (pinVerified) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF166534))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Code verified successfully",
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF166534),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }

                            if (pinFailedAttempts >= 3 || codeNotAvailableSelected) {
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        codeNotAvailableSelected = !codeNotAvailableSelected
                                        if (codeNotAvailableSelected) pinVerified = false
                                    }
                                ) {
                                    Checkbox(
                                        checked = codeNotAvailableSelected,
                                        onCheckedChange = {
                                            codeNotAvailableSelected = it
                                            if (it) pinVerified = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Code not available",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }

                                if (codeNotAvailableSelected) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Please provide the reason why the code could not be verified:",
                                        fontSize = 12.sp,
                                        color = Color(0xFF475569)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    val reasons = listOf("Customer has no phone", "Customer unreachable", "SMS not received", "Other")
                                    reasons.forEach { r ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { pinNotVerifiedReason = r }
                                        ) {
                                            RadioButton(
                                                selected = pinNotVerifiedReason == r || (r == "Other" && pinNotVerifiedReason !in reasons),
                                                onClick = { pinNotVerifiedReason = r }
                                            )
                                            Text(r, fontSize = 12.sp)
                                        }
                                    }

                                    OutlinedTextField(
                                        value = pinNotVerifiedReason,
                                        onValueChange = { pinNotVerifiedReason = it },
                                        label = { Text("Reason Details") },
                                        placeholder = { Text("Explain why code was not checked") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        PodStep.INSTALLATION -> {
                            Text(
                                text = "Installation",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueGasDark
                            )
                            Text(
                                text = "Complete the steps your company has approved.",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            listOf(
                                InstallationResult.INSTALLED to "Installed",
                                InstallationResult.NOT_INSTALLED to "Not installed",
                                InstallationResult.NOT_APPLICABLE to "Not applicable"
                            ).forEach { (res, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { installationResult = res }
                                        .padding(vertical = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = installationResult == res,
                                        onClick = { installationResult = res }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            when (installationResult) {
                                InstallationResult.INSTALLED -> {
                                    Text(
                                        text = "Safety Checklist (all items required):",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = BlueGasDark
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    if (settings.installationChecklistItems.isEmpty()) {
                                        Text(
                                            "No checklist items configured in settings.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    } else {
                                        settings.installationChecklistItems.forEach { item ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        val current = checklistSelections[item] ?: false
                                                        checklistSelections[item] = !current
                                                    }
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Checkbox(
                                                    checked = checklistSelections[item] == true,
                                                    onCheckedChange = { checklistSelections[item] = it }
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(item, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                                InstallationResult.NOT_INSTALLED -> {
                                    OutlinedTextField(
                                        value = notInstalledReason,
                                        onValueChange = { notInstalledReason = it },
                                        label = { Text("Reason installation was not done") },
                                        placeholder = { Text("e.g. Customer declined installation") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = false,
                                        minLines = 2
                                    )
                                }
                                InstallationResult.NOT_APPLICABLE -> {
                                    Text(
                                        "Installation is marked Not applicable for this order.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        PodStep.EMPTY_CYLINDER -> {
                            Text(
                                text = "Empty Cylinder Exchange",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueGasDark
                            )
                            Text(
                                text = "Verify any empty cylinder returned for refill exchange.",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { emptyCollected = !emptyCollected }
                            ) {
                                Checkbox(
                                    checked = emptyCollected,
                                    onCheckedChange = { emptyCollected = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Empty cylinder collected", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }

                            if (emptyCollected) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Select Cylinder Brand:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))

                                val brands = settings.approvedEmptyBrands + "Other brand"
                                brands.forEach { b ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedBrand = b }
                                    ) {
                                        RadioButton(selected = selectedBrand == b, onClick = { selectedBrand = b })
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(b, fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = emptySerialInput,
                                    onValueChange = { emptySerialInput = it },
                                    label = { Text("Empty Cylinder Serial (Optional)") },
                                    placeholder = { Text("Scan or enter serial number") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        PodStep.PAYMENT -> {
                            Text(
                                text = "Cash on Delivery",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueGasDark
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Amount Due:", fontSize = 13.sp, color = Color(0xFF475569))
                                        Text(
                                            formatRupees(order.totalAmountPaisa),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BlueGasDark
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = cashReceivedRupees,
                                onValueChange = {
                                    if (it.all { c -> c.isDigit() }) {
                                        cashReceivedRupees = it
                                    }
                                },
                                label = { Text("Cash Received (Rupees)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            when {
                                cashDifference > 0 -> {
                                    Text(
                                        "Change to return: ${formatRupees(cashDifference)}",
                                        color = Color(0xFF16A34A),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                                cashDifference < 0 -> {
                                    Text(
                                        "Short by: ${formatRupees(-cashDifference)}",
                                        color = Color(0xFFDC2626),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                                else -> {
                                    Text(
                                        "Exact amount received",
                                        color = Color(0xFF16A34A),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            if (cashDifference != 0L) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = cashDifferenceReason,
                                    onValueChange = { cashDifferenceReason = it },
                                    label = { Text("Reason for cash difference *") },
                                    placeholder = { Text("e.g. Customer short of exact change") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { cashReceivedConfirmed = !cashReceivedConfirmed }
                            ) {
                                Checkbox(
                                    checked = cashReceivedConfirmed,
                                    onCheckedChange = { cashReceivedConfirmed = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "I have received this cash",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        PodStep.PHOTO -> {
                            Text(
                                text = "Delivery Photo",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueGasDark
                            )
                            Text(
                                text = "Photo of the delivered cylinder at the door.",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        photoConsent = !photoConsent
                                        if (!photoConsent) photoCaptured = false
                                    }
                            ) {
                                Checkbox(
                                    checked = photoConsent,
                                    onCheckedChange = {
                                        photoConsent = it
                                        if (!it) photoCaptured = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "The customer agrees to a delivery photo",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (!photoCaptured && !skipPhoto) {
                                Button(
                                    onClick = { photoCaptured = true },
                                    enabled = photoConsent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Take delivery photo")
                                }
                            }

                            if (photoCaptured) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = BlueGasPrimary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Photo captured", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        }
                                        TextButton(onClick = { photoCaptured = false }) {
                                            Text("Retake")
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        skipPhoto = !skipPhoto
                                        if (skipPhoto) photoCaptured = false
                                    }
                            ) {
                                Checkbox(
                                    checked = skipPhoto,
                                    onCheckedChange = {
                                        skipPhoto = it
                                        if (it) photoCaptured = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Skip photo", fontSize = 13.sp)
                            }

                            if (skipPhoto) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = photoSkipReason,
                                    onValueChange = { photoSkipReason = it },
                                    label = { Text("Reason for skipping photo *") },
                                    placeholder = { Text("e.g. Customer declined photo") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }

                        PodStep.SIGNATURE -> {
                            Text(
                                text = "Customer Signature",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueGasDark
                            )
                            Text(
                                text = "Customer delivery acknowledgment.",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = signerName,
                                onValueChange = { signerName = it },
                                label = { Text("Signer Name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        cannotSignSelected = !cannotSignSelected
                                        if (cannotSignSelected) signaturePoints.clear()
                                    }
                            ) {
                                Checkbox(
                                    checked = cannotSignSelected,
                                    onCheckedChange = {
                                        cannotSignSelected = it
                                        if (it) signaturePoints.clear()
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Customer cannot sign", fontSize = 13.sp)
                            }

                            if (cannotSignSelected) {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = signatureUnavailableReason,
                                    onValueChange = { signatureUnavailableReason = it },
                                    label = { Text("Reason signature unavailable *") },
                                    placeholder = { Text("e.g. Physical disability / authorized recipient") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Draw signature in the box below:", fontSize = 12.sp, color = Color(0xFF64748B))
                                Spacer(modifier = Modifier.height(6.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF8FAFC))
                                        .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, _ ->
                                                change.consume()
                                                signaturePoints.add(change.position)
                                            }
                                        }
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        if (signaturePoints.size > 1) {
                                            val path = Path()
                                            path.moveTo(signaturePoints.first().x, signaturePoints.first().y)
                                            for (i in 1 until signaturePoints.size) {
                                                path.lineTo(signaturePoints[i].x, signaturePoints[i].y)
                                            }
                                            drawPath(
                                                path = path,
                                                color = Color(0xFF0F172A),
                                                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                            )
                                        }
                                    }

                                    if (signaturePoints.isEmpty()) {
                                        Text(
                                            "Sign here",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 14.sp,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }

                                if (signaturePoints.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { signaturePoints.clear() }) {
                                            Text("Clear Signature")
                                        }
                                    }
                                }
                            }
                        }

                        PodStep.REVIEW -> {
                            Text(
                                text = "Delivery Summary",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueGasDark
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    ReviewSummaryRow(
                                        label = "PIN Verification",
                                        value = if (pinVerified) "Verified" else "Not verified: $pinNotVerifiedReason"
                                    )
                                    ReviewSummaryRow(
                                        label = "Installation",
                                        value = installationResult.displayName
                                    )
                                    if (isRefillExchange) {
                                        ReviewSummaryRow(
                                            label = "Empty Cylinder",
                                            value = if (emptyCollected) "Collected ($selectedBrand)" else "Not collected"
                                        )
                                    }
                                    ReviewSummaryRow(
                                        label = "Payment",
                                        value = if (isCod) "Cash received: ${formatRupees(recPaisa)}" + (if (cashDifference != 0L) " (${if (cashDifference > 0) "Change: " else "Short: "}${formatRupees(kotlin.math.abs(cashDifference))})" else "") else "Paid online"
                                    )
                                    ReviewSummaryRow(
                                        label = "Photo Evidence",
                                        value = if (photoCaptured) "Photo captured" else "Skipped: $photoSkipReason"
                                    )
                                    ReviewSummaryRow(
                                        label = "Signature",
                                        value = if (!cannotSignSelected && signaturePoints.isNotEmpty()) "Signed by $signerName" else "Unavailable: $signatureUnavailableReason"
                                    )
                                }
                            }
                        }
                    }
                }

                // Pinned Bottom Action Bar
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStepIndex > 0) {
                            OutlinedButton(
                                onClick = { currentStepIndex-- },
                                enabled = !isSubmitting,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("pod_back_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back")
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        if (currentStepIndex < activeSteps.size - 1) {
                            Button(
                                onClick = { currentStepIndex++ },
                                enabled = isStepValid && !isSubmitting,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("pod_next_button")
                            ) {
                                Text("Next")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        } else {
                            Button(
                                onClick = {
                                    isSubmitting = true
                                    val (ok, msg) = store.completeHandover(
                                        orderId = order.id,
                                        checklistAnswers = checklistSelections.toMap(),
                                        installationResult = installationResult,
                                        notInstalledReason = notInstalledReason.ifBlank { null },
                                        pinVerified = pinVerified,
                                        pinNotVerifiedReason = pinNotVerifiedReason.ifBlank { null },
                                        photoCaptured = photoCaptured,
                                        signatureCaptured = !cannotSignSelected && signaturePoints.isNotEmpty(),
                                        signatureUnavailableReason = signatureUnavailableReason.ifBlank { null },
                                        evidenceConsent = photoConsent,
                                        signerName = signerName.ifBlank { "Customer" },
                                        amountReceivedPaisa = if (isCod) recPaisa else null,
                                        changeReturnedPaisa = if (isCod && cashDifference > 0) cashDifference else null,
                                        cashDifferenceReason = cashDifferenceReason.ifBlank { null },
                                        emptyCollected = emptyCollected,
                                        emptyBrand = if (emptyCollected) selectedBrand else null,
                                        emptySerial = if (emptyCollected) emptySerialInput.ifBlank { null } else null
                                    )
                                    isSubmitting = false
                                    onCompleted(ok, msg)
                                },
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("pod_complete_button")
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Complete delivery")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDiscardAlert) {
        AlertDialog(
            onDismissRequest = { showDiscardAlert = false },
            title = { Text("Discard progress?") },
            text = { Text("Any details entered in this proof of delivery flow will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardAlert = false
                        onDismiss()
                    }
                ) {
                    Text("Discard", color = Color(0xFFDC2626))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardAlert = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}

@Composable
private fun ReviewSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF64748B))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF0F172A),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
