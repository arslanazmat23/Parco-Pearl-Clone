/**
 * Customer profile management, cylinder holdings registry, addresses, and emergency safety support.
 *
 * Implements:
 * - Customer identity display with masked CNIC verification and seamless persona switching ([BlueGasStore.setActiveCustomer])
 * - Cylinder holdings & deposit liabilities card reflecting deposits held without ownership transfer (Q03)
 * - Saved delivery addresses with delivery zone serviceability validation and addition ([BlueGasStore.addAddress])
 * - Past delivery feedback and ratings for completed orders ([RateOrderDialog])
 * - Account support cases and notification history review ([BlueGasStore.markNotificationRead])
 * - Direct support hotline dialer and Safety & Emergencies response card (1122 dispatch + safety concern ticket)
 *
 * Reads: [SharedAppState.activeCustomer], [SharedAppState.addresses], [SharedAppState.customerHoldings],
 *        [SharedAppState.supportCases], [SharedAppState.notifications], [SharedAppState.cylinders]
 * Calls: [BlueGasStore.setActiveCustomer], [BlueGasStore.addAddress], [BlueGasStore.setDefaultAddress],
 *        [BlueGasStore.raiseSafetyConcern], [BlueGasStore.markNotificationRead]
 */
package com.example.bluegas.ui.customer

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.data.addAddress
import com.example.bluegas.data.markNotificationRead
import com.example.bluegas.data.raiseSafetyConcern
import com.example.bluegas.data.requestCylinderReturn
import com.example.bluegas.data.setActiveCustomer
import com.example.bluegas.data.setDefaultAddress
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary
import kotlinx.coroutines.launch

/**
 * Privacy masking rule for Pakistani National Identity Cards (CNIC).
 * Strips non-digits; if fewer than 13 digits, returns input unchanged.
 * Formats as first 5 digits, then -XXXXXXX-, then final checksum digit (e.g. 37405-XXXXXXX-1).
 *
 * @param cnic The raw or formatted CNIC string.
 * @return Masked CNIC string in format 37405-XXXXXXX-1.
 */
fun maskCnic(cnic: String): String {
    val digits = cnic.filter { it.isDigit() }
    if (digits.length < 13) return cnic
    return "${digits.take(5)}-XXXXXXX-${digits.takeLast(1)}"
}

/**
 * Customer profile, address book, deposit holdings, and emergency safety support screen.
 *
 * @param store Application data store reference.
 * @param onActionMessage Customer notification banner callback receiving (message: String, isError: Boolean).
 * @param modifier Optional layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    store: BlueGasStore,
    onActionMessage: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snapshot by store.appState.collectAsStateWithLifecycle()
    val activeCustomer = snapshot.activeCustomer ?: return
    val customerAddresses = snapshot.addresses.filter { it.customerId == activeCustomer.id }
    val customerHoldings = snapshot.customerHoldings.filter { it.customerId == activeCustomer.id }
    val activeHoldings = customerHoldings.filter { it.status == "ACTIVE" && it.ownership != CylinderOwnership.RETURNED }
    val returnedHoldings = customerHoldings.filter { it.status != "ACTIVE" || it.ownership == CylinderOwnership.RETURNED }
    val customerReturnCases = snapshot.returnCases.filter {
        it.customerId == activeCustomer.id && it.type == "CYLINDER_RETURN"
    }.sortedByDescending { it.openedAt }
    val customerCases = snapshot.supportCases.filter { it.customerId == activeCustomer.id }
    val customerOrders = snapshot.orders.filter { it.customerId == activeCustomer.id }.sortedByDescending { it.createdAt }
    val customerOrdersCount = customerOrders.size

    var showCustomerSwitchMenu by remember { mutableStateOf(false) }
    var showSafetyConcernDialog by remember { mutableStateOf(false) }
    var showAddAddressDialog by remember { mutableStateOf(false) }
    var returnedExpanded by remember { mutableStateOf(false) }
    var returnDialogSerial by remember { mutableStateOf<String?>(null) }
    var returnReasonInput by remember { mutableStateOf("") }
    var returnPreferredWindow by remember { mutableStateOf<String?>(null) }

    val totalDepositPaidPaisa = activeHoldings
        .filter { it.ownership == CylinderOwnership.DEPOSIT_HELD && !it.noDepositOnRecord }
        .sumOf { it.depositPaisa }

    // Dialog state for rating a completed order (using shared RateOrderDialog)
    var ratingOrderId by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("customer_profile_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Customer Header Card with Switcher
        // Displays active verified persona, masked CNIC, and overall account metrics.
        // Account switcher is provided for pilot testing and administrative role evaluation,
        // simulating real-world customer profiles across various zones and cylinder holdings.
        // Switching customer profiles updates the active session without restarting the app.
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_identity_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(BlueGasPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeCustomer.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = activeCustomer.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "CNIC: ${maskCnic(activeCustomer.cnic)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "Phone: ${activeCustomer.phone}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }

                        // Switch Customer Dropdown Button
                        Box {
                            OutlinedButton(
                                onClick = { showCustomerSwitchMenu = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("switch_customer_profile_button")
                            ) {
                                Text("Switch", fontSize = 11.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            // Persona switching: in pilot and testing mode, persona switching is executed through
                            // store.setActiveCustomer to allow evaluating customer-specific addresses, holdings, and
                            // active orders without requiring a complete app restart or mock session resets.
                            DropdownMenu(
                                expanded = showCustomerSwitchMenu,
                                onDismissRequest = { showCustomerSwitchMenu = false }
                            ) {
                                snapshot.customers.forEach { cust ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(cust.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("CNIC: ${maskCnic(cust.cnic)} (${cust.id})", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        },
                                        onClick = {
                                            val (ok, msg) = store.setActiveCustomer(cust.id)
                                            onActionMessage(msg, !ok)
                                            showCustomerSwitchMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(customerOrdersCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BlueGasPrimary)
                            Text("Orders Placed", fontSize = 10.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(activeHoldings.size.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF047857))
                            Text("Cylinders Held", fontSize = 10.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(customerCases.size.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFD97706))
                            Text("Support Cases", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        // My cylinders card: tracks physical custody and refundable security deposits
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cylinder_holdings_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "My cylinders",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF78350F)
                            )
                        }
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${activeHoldings.size} ${if (activeHoldings.size == 1) "cylinder" else "cylinders"}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${activeHoldings.size} ${if (activeHoldings.size == 1) "cylinder" else "cylinders"} · Refundable deposits held: ${formatRupees(totalDepositPaidPaisa)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF92400E)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = Color(0x33F59E0B),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "A security deposit lets you hold a BLUE GAS cylinder — the cylinder stays BLUE GAS property. Refund terms follow the BLUE GAS deposit policy.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFB45309),
                                lineHeight = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            DemoValuePill(text = "Demo value · Q03")
                        }
                    }

                    if (activeHoldings.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You don't hold any BLUE GAS cylinders right now.",
                            fontSize = 11.sp,
                            color = Color(0xFF78350F)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        activeHoldings.forEach { holding ->
                            val cyl = snapshot.cylinders.find { it.serialNumber == holding.cylinderSerial }
                            val sizeLabel = cyl?.let { "${it.weightKg} kg" }
                            val depAccount = snapshot.depositAccounts.firstOrNull {
                                it.customerId == activeCustomer.id && it.cylinderSerial == holding.cylinderSerial
                            }
                            val depStatus = depAccount?.status ?: holding.depositStatus
                            val hasOpenReturn = customerReturnCases.any {
                                it.cylinderSerial == holding.cylinderSerial &&
                                    it.status != L6Status.CASE_CLOSED &&
                                    it.status != L6Status.RETURN_REJECTED &&
                                    it.status != L6Status.REFUND_CONFIRMED
                            }
                            val custodyLine = when {
                                holding.noDepositOnRecord || holding.ownership == CylinderOwnership.LOANED ->
                                    "Dealer cylinder — no deposit on record"
                                holding.ownership == CylinderOwnership.DEPOSIT_HELD ->
                                    "Deposit ${formatRupees(holding.depositPaisa)} · ${customerDepositStatusLabel(depStatus)}"
                                else -> "Surrendered — pending check"
                            }

                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(0.5.dp, Color(0xFFFDE68A)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFF78350F), modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = holding.cylinderSerial,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (sizeLabel != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = Color(0xFFFEF3C7),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = sizeLabel,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF92400E),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        if (!holding.noDepositOnRecord && holding.depositPaisa > 0L) {
                                            Text(
                                                text = formatRupees(holding.depositPaisa),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF78350F)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = custodyLine,
                                        fontSize = 11.sp,
                                        color = Color(0xFF475569)
                                    )
                                    if (holding.ownership == CylinderOwnership.DEPOSIT_HELD || holding.ownership == CylinderOwnership.LOANED) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedButton(
                                            onClick = {
                                                returnReasonInput = ""
                                                returnPreferredWindow = customerReturnWindowOptions(snapshot.settings).firstOrNull()
                                                returnDialogSerial = holding.cylinderSerial
                                            },
                                            enabled = !hasOpenReturn,
                                            border = BorderStroke(1.dp, if (hasOpenReturn) Color(0xFFCBD5E1) else Color(0xFFB45309)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("return_cylinder_btn_${holding.cylinderSerial}")
                                        ) {
                                            Text(
                                                text = if (hasOpenReturn) "Return requested" else "Return my cylinder",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (hasOpenReturn) Color(0xFF64748B) else Color(0xFF92400E)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (returnedHoldings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable { returnedExpanded = !returnedExpanded }
                                .testTag("returned_cylinders_toggle"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Returned cylinders (${returnedHoldings.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF78350F)
                            )
                            Icon(
                                imageVector = if (returnedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (returnedExpanded) "Collapse returned cylinders" else "Expand returned cylinders",
                                tint = Color(0xFF78350F)
                            )
                        }
                        AnimatedVisibility(visible = returnedExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                returnedHoldings.forEach { rh ->
                                    val cyl = snapshot.cylinders.find { it.serialNumber == rh.cylinderSerial }
                                    val sizeLabel = cyl?.let { " · ${it.weightKg} kg" } ?: ""
                                    val depStatus = snapshot.depositAccounts.firstOrNull {
                                        it.customerId == activeCustomer.id && it.cylinderSerial == rh.cylinderSerial
                                    }?.status ?: rh.depositStatus
                                    Surface(
                                        color = Color(0xFFFEFCE8),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(0.5.dp, Color(0xFFFDE68A)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${rh.cylinderSerial}$sizeLabel",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF475569)
                                            )
                                            Text(
                                                text = "Returned · ${customerDepositStatusLabel(depStatus)}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF166534)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Plain-language Return progress stepper for active and recent cylinder returns
        if (customerReturnCases.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_return_progress_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AssignmentReturn, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Return progress",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = BlueGasDark
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        customerReturnCases.forEach { rc ->
                            val linkedRefund = snapshot.refunds.firstOrNull { it.id == rc.linkedRefundId || it.linkedCaseId == rc.id }
                            val progress = mapCustomerReturnProgress(rc, linkedRefund)
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Cylinder ${rc.cylinderSerial ?: ""}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF0F172A)
                                        )
                                        if (rc.requestedAmountPaisa > 0L) {
                                            Text(
                                                text = "Deposit ${formatRupees(rc.requestedAmountPaisa)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF166534)
                                            )
                                        }
                                    }
                                    if (!rc.preferredCollectionWindow.isNullOrBlank()) {
                                        Text(
                                            text = "Preferred time: ${rc.preferredCollectionWindow}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (progress.isRejected) {
                                        Surface(
                                            color = Color(0xFFFEF2F2),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = "Request not accepted",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF991B1B)
                                                )
                                                val rejDetail = progress.statusDetail.removePrefix("Request not accepted").removePrefix(":").trim()
                                                if (rejDetail.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = rejDetail,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF7F1D1D)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = progress.statusDetail,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (rc.deductionPaisa > 0L && (rc.status == L6Status.REFUND_CONFIRMED || rc.status == L6Status.CASE_CLOSED)) {
                                                Color(0xFFB45309)
                                            } else {
                                                BlueGasPrimary
                                            }
                                        )
                                        if (rc.deductionPaisa > 0L && rc.status != L6Status.REFUND_CONFIRMED && rc.status != L6Status.CASE_CLOSED) {
                                            Text(
                                                text = "${formatRupees(rc.deductionPaisa)} was kept: ${rc.deductionReason ?: "Condition adjustment"}",
                                                fontSize = 10.sp,
                                                color = Color(0xFFB45309)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        progress.stepTitles.forEachIndexed { idx, stepLabel ->
                                            val isDone = idx < progress.currentStepIndex ||
                                                (idx == progress.stepTitles.lastIndex && (rc.status == L6Status.REFUND_CONFIRMED || rc.status == L6Status.CASE_CLOSED))
                                            val isCurrent = !isDone && idx == progress.currentStepIndex
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                when {
                                                                    isDone -> Color(0xFF16A34A)
                                                                    isCurrent -> BlueGasPrimary
                                                                    else -> Color(0xFFE2E8F0)
                                                                }
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isDone) {
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                                        } else {
                                                            Text(
                                                                text = (idx + 1).toString(),
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isCurrent) Color.White else Color(0xFF64748B)
                                                            )
                                                        }
                                                    }
                                                    if (idx < progress.stepTitles.lastIndex) {
                                                        Box(
                                                            modifier = Modifier
                                                                .width(2.dp)
                                                                .height(12.dp)
                                                                .background(if (isDone) Color(0xFF16A34A) else Color(0xFFE2E8F0))
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stepLabel,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isCurrent || isDone) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = if (isCurrent) BlueGasPrimary else if (isDone) Color(0xFF0F172A) else Color(0xFF94A3B8)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delivery Addresses Card
        // Saved delivery addresses: maps customer premises to geographical delivery zones.
        // Each address maintains a serviceability status reflecting whether it falls inside the active pilot delivery zone.
        // Customers can save multiple residential, commercial, or depot delivery locations with custom labels.
        // Selecting a default address sets the default destination for quotation pricing and order dispatch.
        // Addresses outside the coverage zone automatically guide customers toward depot pickup options.
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_addresses_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PinDrop, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Saved Delivery Addresses (${customerAddresses.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    customerAddresses.forEach { addr ->
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (addr.isServiceable) Icons.Default.LocationOn else Icons.Default.LocationOff,
                                    contentDescription = null,
                                    tint = if (addr.isServiceable) BlueGasPrimary else Color(0xFFDC2626),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(addr.label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        if (addr.isDefault) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(4.dp)) {
                                                Text("DEFAULT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = BlueGasPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    }
                                    Text("${addr.street}, ${addr.area}, ${addr.city}", fontSize = 11.sp, color = Color(0xFF475569))
                                    Text(
                                        text = if (addr.isServiceable) "Zone: ${addr.zoneId} (Serviceable delivery area)" else "Outside current delivery area",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (addr.isServiceable) Color(0xFF166534) else Color(0xFFDC2626)
                                    )
                                }
                                if (!addr.isDefault) {
                                    TextButton(
                                        onClick = {
                                            val (ok, msg) = store.setDefaultAddress(activeCustomer.id, addr.id)
                                            onActionMessage(msg, !ok)
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Set Default", fontSize = 10.sp, color = BlueGasPrimary)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { showAddAddressDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("add_new_address_button"),
                        border = BorderStroke(1.dp, BlueGasPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.AddLocation, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add New Address", fontSize = 12.sp, color = BlueGasPrimary, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = Color(0xFFF0F9FF),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Moving home? Return any empty BLUE GAS cylinder before you move so your refundable security deposit can be released.",
                                fontSize = 10.sp,
                                color = Color(0xFF0369A1),
                                lineHeight = 14.sp
                            )
                            DemoValuePill("Demo value · Q03")
                        }
                    }
                }
            }
        }

        // Order Ratings Section (using shared RateOrderDialog)
        // Lists recently fulfilled orders and lets customers submit star ratings
        // and delivery feedback comments. Each order is restricted to a single submission.
        // Ratings provide closed-loop quality assurance on rider conduct, delivery promptness, and cylinder condition.
        // Customer ratings and comments are permanently bound to the order record in the operations store.
        val completedOrders = customerOrders.filter { it.status == OrderStatus.ORD_COMP }
        if (completedOrders.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().testTag("profile_ratings_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFEAB308), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delivery Feedback & Ratings", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlueGasDark)
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        completedOrders.take(3).forEach { ord ->
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Order ${ord.id}", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text(
                                            text = "${ord.items.firstOrNull()?.productName ?: "Cylinder"} • ${formatRupees(ord.totalAmountPaisa)}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        if (ord.ratingStars != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "★".repeat(ord.ratingStars ?: 0) + "☆".repeat(5 - (ord.ratingStars ?: 0)),
                                                    color = Color(0xFFEAB308),
                                                    fontSize = 13.sp
                                                )
                                                if (!ord.ratingComment.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(ord.ratingComment ?: "", fontSize = 10.sp, color = Color(0xFF475569))
                                                }
                                            }
                                        }
                                    }

                                    if (ord.ratingStars == null) {
                                        FilledTonalButton(
                                            onClick = {
                                                ratingOrderId = ord.id
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Rate", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Support Cases History
        // Support cases & operational notifications: tracks customer-initiated disputes and automated alerts.
        // Notifications can be acknowledged and marked as read directly by the customer.
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_support_cases_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Support Cases (${customerCases.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    if (customerCases.isEmpty()) {
                        Text(
                            text = "No open complaints.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        customerCases.forEach { c ->
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val orderLabel = if (c.orderId != null) " • Order: ${c.orderId}" else ""
                                        Text("Case: ${c.id}$orderLabel", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Surface(
                                            color = when (c.status) {
                                                "OPEN" -> Color(0xFFFEE2E2)
                                                "INVESTIGATING" -> Color(0xFFFEF3C7)
                                                else -> Color(0xFFDCFCE7)
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = c.status,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (c.status) {
                                                    "OPEN" -> Color(0xFFDC2626)
                                                    "INVESTIGATING" -> Color(0xFFB45309)
                                                    else -> Color(0xFF166534)
                                                },
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Issue: ${c.issueType.replace("_", " ")}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(c.description, fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Notifications section
        // Presents real-time customer-facing milestone alerts (payment receipts, dispatch notices, handover PINs).
        // Tapping any notification automatically marks it as acknowledged in the store state.
        // Surface filtering restricts display strictly to customer-directed alerts (AppSurface.CUSTOMER),
        // preventing internal courier dispatch or plant operational status updates from leaking to the customer view.
        val customerNotifications = snapshot.notifications
            .filter { it.surface == AppSurface.CUSTOMER }
            .sortedByDescending { it.timestamp }

        if (customerNotifications.isNotEmpty()) {
            item {
                Text(
                    text = "Notifications (${customerNotifications.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BlueGasDark,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            items(customerNotifications.take(10)) { notif ->
                val notifIcon = when (notif.code) {
                    "NTF-19" -> Icons.Default.Payments
                    "NTF-20" -> Icons.Default.Inventory2
                    "NTF-21" -> Icons.Default.VerifiedUser
                    else -> Icons.Default.Notifications
                }
                val isCylinderNotif = notif.code in setOf("NTF-19", "NTF-20", "NTF-21")
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable {
                            store.markNotificationRead(notif.id)
                            if (isCylinderNotif) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(1)
                                }
                            }
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (notif.isRead) Color.White else Color(0xFFEFF6FF)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, if (notif.isRead) Color(0xFFE2E8F0) else BlueGasPrimary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!notif.isRead) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(BlueGasPrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Icon(
                                imageVector = notifIcon,
                                contentDescription = null,
                                tint = BlueGasPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(notif.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BlueGasDark, modifier = Modifier.weight(1f))
                            Text(formatTimestamp(notif.timestamp), fontSize = 9.sp, color = Color.Gray)
                        }
                        Text(notif.message, fontSize = 11.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 2.dp))
                        if (notif.orderId != null) {
                            Text("Order: ${notif.orderId}", fontSize = 9.sp, color = BlueGasPrimary, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }

        // Customer care support hotline and assistance card
        // Provides direct telephone contact with support operations while WhatsApp remains designated for Phase 3.
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Contact Support", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlueGasDark)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "BLUE GAS support: ${snapshot.settings.supportPhone}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${snapshot.settings.supportPhone}"))
                                context.startActivity(intent)
                            },
                            label = { Text("Call", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("WhatsApp (Phase 3)", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            }
        }

        // Emergency response and safety reporting card
        // Direct integration with Rescue 1122 emergency services and BLUE GAS internal safety reporting.
        // Safety concerns reported by customers trigger immediate dual escalation:
        // 1. A customer support ticket is opened to keep the customer informed of status and resolution.
        // 2. An HSE safety incident is filed in the operations ledger to alert depot safety supervisors.
        // If linked to an order, custody records identify the cylinder serial number and driver for immediate field triage.
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("safety_helpline_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Safety & emergencies",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF991B1B)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Follow the approved BLUE GAS safety procedure (to be supplied by the client, Q07). For emergencies call Rescue 1122.",
                        fontSize = 11.sp,
                        color = Color(0xFF7F1D1D),
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1122"))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call 1122", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { showSafetyConcernDialog = true },
                            border = BorderStroke(1.dp, Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Report a safety concern", fontSize = 11.sp, color = Color(0xFFDC2626))
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Add new delivery address entry dialog
    // Address serviceability: the chosen geographical zone directly controls whether doorstep delivery is allowed.
    // Customers outside delivery zones are guided to depot pickup fulfillment.
    // Address entry validation: validates street address, area name, city selection, and serviceability zone.
    // Also manages the toggle for setting this newly created address as the primary account default.
    if (showAddAddressDialog) {
        var labelInput by remember { mutableStateOf("Home") }
        var streetInput by remember { mutableStateOf("") }
        var areaInput by remember { mutableStateOf("") }
        val availableCities = remember(snapshot.serviceZones) {
            snapshot.serviceZones.map { it.city }.distinct().ifEmpty { listOf("Lahore", "Islamabad") }
        }
        var selectedCity by remember { mutableStateOf(availableCities.first()) }
        val cityZones = remember(selectedCity, snapshot.serviceZones) {
            snapshot.serviceZones.filter { it.city.equals(selectedCity, ignoreCase = true) }
                .ifEmpty { snapshot.serviceZones }
        }
        var selectedZoneId by remember(cityZones) { mutableStateOf(cityZones.firstOrNull()?.id ?: "Z-01") }
        var makeDefaultSwitch by remember { mutableStateOf(customerAddresses.isEmpty()) }
        var formError by remember { mutableStateOf<String?>(null) }
        val isServiceableZone = cityZones.find { it.id == selectedZoneId }?.isServiceable ?: false

        var cityDropdownExpanded by remember { mutableStateOf(false) }
        var zoneDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddAddressDialog = false },
            title = {
                Text("Add New Delivery Address", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (formError != null) {
                        Text(formError ?: "", color = Color(0xFFDC2626), fontSize = 11.sp)
                    }

                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("Label (e.g. Home, Office)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = streetInput,
                        onValueChange = { streetInput = it },
                        label = { Text("Street Address / House No") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = areaInput,
                        onValueChange = { areaInput = it },
                        label = { Text("Area / Sector") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // City Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { cityDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("City: $selectedCity", fontSize = 12.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = cityDropdownExpanded,
                            onDismissRequest = { cityDropdownExpanded = false }
                        ) {
                            availableCities.forEach { city ->
                                DropdownMenuItem(
                                    text = { Text(city) },
                                    onClick = {
                                        selectedCity = city
                                        cityDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Zone Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val zoneName = cityZones.find { it.id == selectedZoneId }?.name ?: selectedZoneId
                        OutlinedButton(
                            onClick = { zoneDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Zone: $zoneName", fontSize = 12.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = zoneDropdownExpanded,
                            onDismissRequest = { zoneDropdownExpanded = false }
                        ) {
                            cityZones.forEach { zn ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("${zn.name} (${zn.id})", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Text(if (zn.isServiceable) "Serviceable delivery zone" else "Outside delivery area", fontSize = 9.sp, color = if (zn.isServiceable) Color(0xFF166534) else Color(0xFFDC2626))
                                        }
                                    },
                                    onClick = {
                                        selectedZoneId = zn.id
                                        zoneDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Serviceability preview of selected zone (no duplicate val declaration)
                    Surface(
                        color = if (isServiceableZone) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isServiceableZone) "✓ Serviceable delivery area" else "⚠️ Outside current delivery area (Hub pickup available)",
                            fontSize = 10.sp,
                            color = if (isServiceableZone) Color(0xFF166534) else Color(0xFF991B1B),
                            modifier = Modifier.padding(6.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set as default address", fontSize = 12.sp)
                        Switch(
                            checked = makeDefaultSwitch,
                            onCheckedChange = { makeDefaultSwitch = it }
                        )
                    }
                }
            },
            confirmButton = {
                // Address persistence: commits the new customer premises record to the shared store.
                // Flags whether the selected zone qualifies for active doorstep courier delivery or requires hub self-pickup.
                // If set as default, becomes the immediate destination for all future quotation sessions.
                Button(
                    onClick = {
                        if (streetInput.isBlank()) {
                            formError = "Please enter street address."
                            return@Button
                        }
                        val (ok, res) = store.addAddress(
                            customerId = activeCustomer.id,
                            label = labelInput.ifBlank { "Address" },
                            street = streetInput.trim(),
                            area = areaInput.trim(),
                            city = selectedCity,
                            zoneId = selectedZoneId,
                            makeDefault = makeDefaultSwitch
                        )
                        if (ok) {
                            showAddAddressDialog = false
                            val msg = if (isServiceableZone) {
                                "Address added. This area is in the delivery zone."
                            } else {
                                "Address added. This address is outside the current delivery area — hub pickup is available."
                            }
                            onActionMessage(msg, false)
                        } else {
                            formError = res
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary)
                ) {
                    Text("Save Address")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAddressDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Emergency safety hazard reporting dialog
    // Dual escalation pipeline: reporting a safety concern logs a customer-facing support case ticket
    // and simultaneously activates a Level 7 HSE emergency incident for internal safety triage.
    // Safety concern submission: validates description text and optionally associates the concern with
    // an existing order to link physical cylinder custody records and incident tracking.
    if (showSafetyConcernDialog) {
        var selectedConcernOrderId by remember { mutableStateOf<String?>(null) }
        var concernDescription by remember { mutableStateOf("") }
        var concernError by remember { mutableStateOf<String?>(null) }
        var orderDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSafetyConcernDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Report a Safety Concern", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Follow the approved BLUE GAS safety procedure (to be supplied by the client, Q07). For emergencies call Rescue 1122.",
                        fontSize = 11.sp,
                        color = Color(0xFF991B1B)
                    )

                    if (concernError != null) {
                        Text(concernError ?: "", color = Color(0xFFDC2626), fontSize = 11.sp)
                    }

                    Text("Related order (optional):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { orderDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val label = if (selectedConcernOrderId == null) {
                                "Not about a specific order"
                            } else {
                                val ord = customerOrders.find { it.id == selectedConcernOrderId }
                                "Order ${selectedConcernOrderId} (${ord?.status?.displayName ?: ""})"
                            }
                            Text(label, fontSize = 11.sp, maxLines = 1)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = orderDropdownExpanded,
                            onDismissRequest = { orderDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Not about a specific order", fontSize = 12.sp) },
                                onClick = {
                                    selectedConcernOrderId = null
                                    orderDropdownExpanded = false
                                }
                            )
                            customerOrders.forEach { ord ->
                                DropdownMenuItem(
                                    text = { Text("Order ${ord.id} (${ord.status.displayName})", fontSize = 12.sp) },
                                    onClick = {
                                        selectedConcernOrderId = ord.id
                                        orderDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = concernDescription,
                        onValueChange = {
                            concernDescription = it
                            concernError = null
                        },
                        label = { Text("Description of concern *") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                // Safety incident escalation: dispatches safety report to customer support tickets and HSE triage simultaneously.
                // Links order custody and serial numbers when provided to isolate compromised equipment in the field.
                Button(
                    onClick = {
                        if (concernDescription.isBlank()) {
                            concernError = "Please describe the safety concern."
                            return@Button
                        }
                        val (ok, msg) = store.raiseSafetyConcern(
                            customerId = activeCustomer.id,
                            description = concernDescription.trim(),
                            orderId = selectedConcernOrderId
                        )
                        onActionMessage(msg, !ok)
                        if (ok) {
                            showSafetyConcernDialog = false
                            concernDescription = ""
                            selectedConcernOrderId = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSafetyConcernDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delivery feedback and rating modal dialog
    if (ratingOrderId != null) {
        RateOrderDialog(
            orderId = ratingOrderId ?: "",
            store = store,
            onResult = { msg, isError ->
                onActionMessage(msg, isError)
            },
            onDismiss = {
                ratingOrderId = null
            }
        )
    }

    // Return my cylinder dialog with reason (200 char cap) and preferred collection time chips
    val serialToReturn = returnDialogSerial
    if (serialToReturn != null) {
        val windowOptions = remember(snapshot.settings.deliveryWindowsHours, snapshot.settings.tomorrowWindowsEnabled) {
            customerReturnWindowOptions(snapshot.settings)
        }
        val targetHolding = customerHoldings.find { it.cylinderSerial == serialToReturn }

        AlertDialog(
            onDismissRequest = { returnDialogSerial = null },
            title = {
                Text(
                    text = "Return my cylinder",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Cylinder ID: $serialToReturn",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF0F172A)
                    )
                    if (targetHolding != null && targetHolding.noDepositOnRecord) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "This is a dealer cylinder with no deposit on record. You can return it, and our team will review its status.",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = returnReasonInput,
                        onValueChange = { if (it.length <= 200) returnReasonInput = it },
                        label = { Text("Reason for returning *") },
                        placeholder = { Text("e.g. Moving home, no longer needed") },
                        supportingText = {
                            Text("${returnReasonInput.length}/200", fontSize = 10.sp)
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("return_reason_input")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Preferred collection time",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                        Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = "Recommended — awaiting approval",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1D4ED8),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            windowOptions.forEach { win ->
                                val selected = returnPreferredWindow == win
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        returnPreferredWindow = if (selected) null else win
                                    },
                                    label = { Text(win, fontSize = 10.sp) },
                                    modifier = Modifier.heightIn(min = 36.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, resMsg) = store.requestCylinderReturn(
                            customerId = activeCustomer.id,
                            cylinderSerial = serialToReturn,
                            reason = returnReasonInput.trim(),
                            actor = activeCustomer.name,
                            preferredWindow = returnPreferredWindow
                        )
                        if (ok) {
                            returnDialogSerial = null
                            onActionMessage("Return request submitted ($resMsg).", false)
                        } else {
                            onActionMessage(resMsg, true)
                        }
                    },
                    enabled = returnReasonInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                    modifier = Modifier.testTag("confirm_return_cylinder_button")
                ) {
                    Text("Submit Request")
                }
            },
            dismissButton = {
                TextButton(onClick = { returnDialogSerial = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
