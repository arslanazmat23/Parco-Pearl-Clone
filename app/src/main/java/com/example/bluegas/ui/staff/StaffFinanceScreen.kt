package com.example.bluegas.ui.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasPrimary

/**
 * Staff Finance workspace covering customer deposit liability & return decisions, refund queue
 * (including automatic cancel refunds and EXC-27 retry), two-step cash reconciliation, gateway
 * payouts, FBR tax invoices, and the double-entry ledger.
 *
 * @param store Shared in-memory store instance.
 * @param snapshot Immutable state snapshot for rendering.
 * @param onBanner Banner callback reporting success or error feedback.
 * @param onSelectOrder Callback to open the order detail dialog.
 */
@Composable
fun StaffFinanceScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner,
    onSelectOrder: (Order) -> Unit
) {
    var selectedFinanceTab by remember { mutableStateOf("DEPOSITS") }

    val totalRevenue = remember(snapshot.version) {
        snapshot.ledgerEntries.filter { it.entryType == "REVENUE_GAS" || it.entryType == "REVENUE" }.sumOf { it.debitPaisa }
    }
    val totalDepositsHeld = remember(snapshot.version) {
        snapshot.depositAccounts.filter { it.status == DepositStatus.HELD && !it.noDepositOnRecord }.sumOf { it.amountPaisa }
    }
    val totalRefunds = remember(snapshot.version) {
        snapshot.ledgerEntries.filter { it.entryType == "REFUND" || it.entryType == "DEPOSIT_REFUND" }.sumOf { it.debitPaisa + it.creditPaisa }
    }
    val pendingRefundsCount = remember(snapshot.version) {
        snapshot.refunds.count {
            it.state == PaymentState.FULL_REFUND_PENDING ||
                it.state == PaymentState.PARTIAL_REFUND_PENDING ||
                it.state == PaymentState.FULL_REFUND_FAILED ||
                it.state == PaymentState.PARTIAL_REFUND_FAILED
        }
    }

    // Deposits summary & queue
    val heldDepositCount = remember(snapshot.version) {
        snapshot.depositAccounts.count { it.status == DepositStatus.HELD }
    }
    val inProgressDepositCount = remember(snapshot.version) {
        snapshot.depositAccounts.count { it.status == DepositStatus.REFUND_IN_PROGRESS }
    }
    val noDepositOnRecordCount = remember(snapshot.version) {
        snapshot.depositAccounts.count { it.noDepositOnRecord }
    }
    val returnedAwaitingDecision = remember(snapshot.version) {
        snapshot.returnCases.filter { it.type == "CYLINDER_RETURN" && it.status == L6Status.ITEM_RETURNED }
    }
    var lastDepositCheck by remember { mutableStateOf<ReconLine?>(null) }

    // Reconcile Cash Handover Dialog state (blank inputs)
    var handoverToReconcile by remember { mutableStateOf<CashHandover?>(null) }
    var reconcileCountedInput by remember { mutableStateOf("") }
    var reconcileReasonInput by remember { mutableStateOf("") }

    // Dispute Handover Dialog state (blank input)
    var showDisputeDialog by remember { mutableStateOf(false) }
    var selectedHandoverForDispute by remember { mutableStateOf<CashHandover?>(null) }
    var disputeReasonInput by remember { mutableStateOf("") }

    // Gateway Reconciliation Dialog state (blank inputs)
    var showGatewayPoutDialog by remember { mutableStateOf(false) }
    var gatewayNameInput by remember { mutableStateOf("") }
    var payoutBatchRefInput by remember { mutableStateOf("") }
    var expectedAmountInput by remember { mutableStateOf("") }
    var settledAmountInput by remember { mutableStateOf("") }
    var feeAmountInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("finance_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            AccountableHeader(
                roleName = AccountableRole.FINANCE.displayName,
                description = "Deposits, Refund Queue, Two-Step Cash Reconciliation, Gateway Payouts & FBR Invoicing"
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Financial Summary KPI Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Gross gas volume", fontSize = 9.sp, color = Color(0xFF1E40AF))
                        Text(formatRupees(totalRevenue), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BlueGasPrimary)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Deposit liability", fontSize = 9.sp, color = Color(0xFFB45309))
                        Text(formatRupees(totalDepositsHeld), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFD97706))
                        Text("Balance sheet item", fontSize = 7.sp, color = Color.Gray)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Total refunds", fontSize = 9.sp, color = Color(0xFF991B1B))
                        Text(formatRupees(totalRefunds), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFDC2626))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Sub-tabs
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    listOf(
                        "DEPOSITS" to "Deposits (${returnedAwaitingDecision.size})",
                        "REFUNDS" to "Refunds ($pendingRefundsCount)",
                        "CASH_HANDOVERS" to "Cash (${snapshot.cashHandovers.size})",
                        "GATEWAYS" to "Gateway Payouts",
                        "FBR_TAX" to "FBR Invoices",
                        "LEDGER" to "Ledger"
                    )
                ) { (id, label) ->
                    FilterChip(
                        selected = selectedFinanceTab == id,
                        onClick = { selectedFinanceTab = id },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.testTag("finance_tab_${id.lowercase()}")
                    )
                }
            }
        }

        when (selectedFinanceTab) {
            "DEPOSITS" -> {
                item {
                    AccountableHeader(
                        roleName = "${AccountableRole.FINANCE.displayName} (RACI-36)",
                        description = "Customer security deposits held (liability), daily deposit checks (EXC-28), and returned cylinder refund decisions."
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 4 summary cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DepositKpiCard(
                            title = "Deposits owed to customers",
                            value = formatRupees(totalDepositsHeld),
                            modifier = Modifier.weight(1f)
                        )
                        DepositKpiCard(
                            title = "Cylinders on deposit",
                            value = heldDepositCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DepositKpiCard(
                            title = "Refunds in progress",
                            value = inProgressDepositCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                        DepositKpiCard(
                            title = "No deposit on record",
                            value = noDepositOnRecordCount.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val line = store.runDepositCheck()
                            lastDepositCheck = line
                            val matched = line.difference == "Rs 0" || line.difference == "0"
                            if (matched) {
                                onBanner("Deposit check matched (${line.expected}).", false)
                            } else {
                                onBanner("Deposit check mismatch (${line.difference}) — EXC-28 logged.", true)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("finance_run_deposit_check_btn")
                    ) {
                        Text("Run deposit check", fontSize = 12.sp)
                    }

                    lastDepositCheck?.let { chk ->
                        val matched = chk.difference == "Rs 0" || chk.difference == "0"
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = if (matched) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (matched) Color(0xFF22C55E) else Color(0xFFF59E0B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${chk.area}: ${chk.note ?: ""}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (matched) Color(0xFF166534) else Color(0xFF92400E)
                                )
                                Text(
                                    text = "Accounts: ${chk.expected} · Holdings: ${chk.actual} · Diff: ${chk.difference}",
                                    fontSize = 11.sp,
                                    color = if (matched) Color(0xFF166534) else Color(0xFF92400E)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Returned cylinders waiting for a refund decision (${returnedAwaitingDecision.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                if (returnedAwaitingDecision.isEmpty()) {
                    item {
                        Text("No returned cylinders waiting for a refund decision.", fontSize = 11.sp, color = Color.Gray)
                    }
                } else {
                    items(returnedAwaitingDecision, key = { it.id }) { rc ->
                        FinanceDepositDecisionCard(
                            rc = rc,
                            store = store,
                            snapshot = snapshot,
                            onBanner = onBanner
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Deposit accounts (${snapshot.depositAccounts.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                }

                items(snapshot.depositAccounts, key = { it.id }) { dep ->
                    val statusBg = when (dep.status) {
                        DepositStatus.HELD -> Color(0xFFDBEAFE)
                        DepositStatus.REFUND_IN_PROGRESS -> Color(0xFFFEF3C7)
                        DepositStatus.REFUNDED -> Color(0xFFDCFCE7)
                        DepositStatus.REDUCED_OR_FORFEITED -> Color(0xFFFEE2E2)
                    }
                    val statusFg = when (dep.status) {
                        DepositStatus.HELD -> Color(0xFF1E40AF)
                        DepositStatus.REFUND_IN_PROGRESS -> Color(0xFF92400E)
                        DepositStatus.REFUNDED -> Color(0xFF166534)
                        DepositStatus.REDUCED_OR_FORFEITED -> Color(0xFF991B1B)
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${snapshot.customerName(dep.customerId)} · ${snapshot.skuLabel(dep.sku)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(color = statusBg, shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        text = dep.status.label,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusFg,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Cylinder: ${dep.cylinderSerial ?: "None"} · Amount: ${formatRupees(dep.amountPaisa)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF0F172A)
                                )
                                if (dep.noDepositOnRecord) {
                                    Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = "No deposit on record",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF92400E),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${snapshot.orderLabel(dep.orderId)} · Paid ${formatTimestamp(dep.paidAt)}",
                                fontSize = 10.sp,
                                color = Color(0xFF475569)
                            )
                            val linkedText = listOfNotNull(
                                dep.returnCaseId?.let { "Case $it" },
                                dep.refundId?.let { "Refund $it" }
                            ).joinToString(" · ")
                            if (linkedText.isNotEmpty()) {
                                Text(linkedText, fontSize = 10.sp, color = BlueGasPrimary)
                            }
                        }
                    }
                }
            }

            "REFUNDS" -> {
                item {
                    Text("Refund processing queue (${snapshot.refunds.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (snapshot.refunds.isEmpty()) {
                    item { Text("No refunds in queue.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.refunds, key = { it.id }) { ref ->
                        val isAutomatic = ref.executedBy == "BLUE GAS platform (system)"
                        val isDepositRefund = ref.kind == "DEPOSIT"
                        val isFailed = ref.state == PaymentState.FULL_REFUND_FAILED || ref.state == PaymentState.PARTIAL_REFUND_FAILED
                        val isPending = ref.state == PaymentState.FULL_REFUND_PENDING || ref.state == PaymentState.PARTIAL_REFUND_PENDING

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("finance_refund_card_${ref.id}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Refund ${ref.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Surface(
                                        color = when (ref.state) {
                                            PaymentState.FULLY_REFUNDED, PaymentState.PARTIAL_REFUND_COMPLETED -> Color(0xFFDCFCE7)
                                            PaymentState.FULL_REFUND_FAILED, PaymentState.PARTIAL_REFUND_FAILED -> Color(0xFFFEE2E2)
                                            else -> Color(0xFFFEF3C7)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = ref.state.displayName,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (ref.state) {
                                                PaymentState.FULLY_REFUNDED, PaymentState.PARTIAL_REFUND_COMPLETED -> Color(0xFF166534)
                                                PaymentState.FULL_REFUND_FAILED, PaymentState.PARTIAL_REFUND_FAILED -> Color(0xFF991B1B)
                                                else -> Color(0xFF92400E)
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (isDepositRefund) {
                                        Surface(color = Color(0xFFE0E7FF), shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                text = "Deposit refund",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF3730A3),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (isAutomatic) {
                                        Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                text = "Automatic",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF166534),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (isFailed) {
                                        Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                text = "Payment failed (EXC-27)",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF991B1B),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "${if (ref.orderId.isNotBlank()) snapshot.orderLabel(ref.orderId) else "Deposit refund"} · Amount: ${formatRupees(ref.amountPaisa)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("Reason: ${ref.reason}", fontSize = 10.sp, color = Color(0xFF475569))

                                if (isPending) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = {
                                                onBanner.report(
                                                    store.processRefund(
                                                        ref.id,
                                                        succeed = true,
                                                        actor = AccountableRole.FINANCE.displayName
                                                    )
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("finance_approve_refund_${ref.id}")
                                        ) {
                                            Text("Approve & disburse", fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onBanner.report(
                                                    store.processRefund(
                                                        ref.id,
                                                        succeed = false,
                                                        actor = AccountableRole.FINANCE.displayName
                                                    )
                                                )
                                            },
                                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("finance_fail_refund_${ref.id}")
                                        ) {
                                            Text("Demo: simulate a failed payout", fontSize = 10.sp, color = Color(0xFF64748B))
                                        }
                                    }
                                } else if (isFailed) {
                                    Button(
                                        onClick = {
                                            onBanner.report(store.retryRefund(ref.id))
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("finance_retry_refund_${ref.id}")
                                    ) {
                                        Text("Retry", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "CASH_HANDOVERS" -> {
                item {
                    Text("Cash hand-over submissions (${snapshot.cashHandovers.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (snapshot.cashHandovers.isEmpty()) {
                    item { Text("No cash hand-overs on record.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.cashHandovers, key = { it.id }) { csh ->
                        val collectorName = if (csh.collectorType == "RIDER") {
                            snapshot.riderName(csh.collectorId)
                        } else {
                            snapshot.pointName(csh.collectorId)
                        }
                        val waitingForHubCashier = csh.recipient == "HUB" && csh.hubReceivedAt == null
                        val canReconcile = (csh.status == "RECEIVED_BY_HUB") ||
                            (csh.recipient == "FINANCE" && (csh.status == "SUBMITTED" || csh.status == "DECLARED"))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("finance_cash_card_${csh.id}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${csh.id} · $collectorName", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Surface(
                                        color = when (csh.status) {
                                            "APPROVED", "RECONCILED" -> Color(0xFFDCFCE7)
                                            "DISPUTED" -> Color(0xFFFEE2E2)
                                            "RECEIVED_BY_HUB" -> Color(0xFFDBEAFE)
                                            else -> Color(0xFFFEF3C7)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = csh.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (csh.status) {
                                                "APPROVED", "RECONCILED" -> Color(0xFF166534)
                                                "DISPUTED" -> Color(0xFF991B1B)
                                                "RECEIVED_BY_HUB" -> Color(0xFF1E40AF)
                                                else -> Color(0xFF92400E)
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Declared: ${formatRupees(csh.declaredPaisa)} (including deposits) · Expected: ${formatRupees(csh.expectedPaisa)} (including deposits)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Recipient: ${csh.recipient} · Orders: ${csh.orderIds.size}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF475569)
                                )
                                csh.hubReceivedPaisa?.let { rec ->
                                    Text(
                                        text = "Hub received: ${formatRupees(rec)}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF1E40AF),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (waitingForHubCashier && csh.status == "SUBMITTED") {
                                    Text(
                                        text = "Waiting for the hub cashier",
                                        fontSize = 11.sp,
                                        color = Color(0xFFB45309),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("finance_reconcile_btn_${csh.id}")
                                    ) {
                                        Text("Reconcile", fontSize = 11.sp)
                                    }
                                } else if (canReconcile) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                handoverToReconcile = csh
                                                reconcileCountedInput = ""
                                                reconcileReasonInput = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("finance_reconcile_btn_${csh.id}")
                                        ) {
                                            Text("Reconcile", fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                selectedHandoverForDispute = csh
                                                disputeReasonInput = ""
                                                showDisputeDialog = true
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                        ) {
                                            Text("Dispute", fontSize = 11.sp, color = Color(0xFFDC2626))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "GATEWAYS" -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Payment gateway settlements", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Button(
                            onClick = {
                                gatewayNameInput = ""
                                payoutBatchRefInput = ""
                                expectedAmountInput = ""
                                settledAmountInput = ""
                                feeAmountInput = ""
                                showGatewayPoutDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reconcile payout", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Payment records (${snapshot.payments.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }

                items(snapshot.payments, key = { it.id }) { pay ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${pay.id} (${snapshot.orderLabel(pay.orderId)})", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(pay.state.displayName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BlueGasPrimary)
                            }
                            Text(
                                text = "Method: ${pay.method.name} · Base: ${formatRupees(pay.baseAmountPaisa)} · Settled: ${formatRupees(pay.settledAmountPaisa)}",
                                fontSize = 10.sp
                            )
                            Text("Idempotency: ${pay.idempotencyKey}", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                        }
                    }
                }
            }

            "FBR_TAX" -> {
                item {
                    Text("FBR digital invoicing & tax status (${snapshot.fbrPostings.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (snapshot.fbrPostings.isEmpty()) {
                    item { Text("No FBR postings recorded.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.fbrPostings, key = { it.orderId }) { fbr ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(snapshot.orderLabel(fbr.orderId), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Invoice no: ${fbr.invoiceNo ?: "Pending FBR assignment"} · Attempts: ${fbr.attempts}", fontSize = 10.sp, color = Color(0xFF475569))
                                }
                                Surface(
                                    color = if (fbr.status == "POSTED") Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = fbr.status,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (fbr.status == "POSTED") Color(0xFF166534) else Color(0xFF92400E),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (fbr.status != "POSTED") {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = { onBanner.report(store.retryFbr(fbr.orderId)) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Retry FBR", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "LEDGER" -> {
                item {
                    Text("Immutable double-entry ledger (${snapshot.ledgerEntries.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                items(snapshot.ledgerEntries.reversed(), key = { it.id }) { led ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val entryTypeLabel = when (led.entryType) {
                                    "REVENUE_GAS" -> "Gas revenue"
                                    "DELIVERY_FEE" -> "Delivery fee"
                                    "EXPRESS_FEE" -> "Express delivery fee"
                                    "PLATFORM_FEE" -> "Platform fee"
                                    "TAX_PAYABLE" -> "Tax payable"
                                    "DEPOSIT_LIABILITY" -> "Security deposit"
                                    "REFUND" -> "Refund"
                                    "SETTLEMENT" -> "Settlement"
                                    "CASH_SHORT_OVER" -> "Cash short / over"
                                    else -> led.entryType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
                                }
                                Text("$entryTypeLabel · ${led.description}", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                if (led.orderId != null) {
                                    Text(snapshot.orderLabel(led.orderId), fontSize = 9.sp, color = Color.Gray)
                                }
                            }
                            Text(
                                text = if (led.debitPaisa > 0) "+${formatRupees(led.debitPaisa)}" else "-${formatRupees(led.creditPaisa)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (led.debitPaisa > 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }
        }
    }

    // Reconcile Cash Handover Dialog (blank inputs)
    val targetReconcile = handoverToReconcile
    if (targetReconcile != null) {
        val countedRs = reconcileCountedInput.trim().toLongOrNull()
        val countedPaisa = countedRs?.times(100L)
        val differs = countedPaisa != null && countedPaisa != targetReconcile.expectedPaisa

        AlertDialog(
            onDismissRequest = { handoverToReconcile = null },
            title = { Text("Reconcile cash hand-over ${targetReconcile.id}", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Expected: ${formatRupees(targetReconcile.expectedPaisa)} (including deposits)",
                        fontSize = 12.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Declared: ${formatRupees(targetReconcile.declaredPaisa)} (including deposits)",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = reconcileCountedInput,
                        onValueChange = { reconcileCountedInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Counted cash (Rs)") },
                        placeholder = { Text("Enter cash amount counted", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reconcileReasonInput,
                        onValueChange = { reconcileReasonInput = it },
                        label = { Text(if (differs) "Difference reason (required)" else "Note (optional)") },
                        placeholder = { Text("Explain any shortage or overage", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (countedPaisa == null) {
                            onBanner("Enter the counted cash amount in rupees.", true)
                            return@Button
                        }
                        val res = store.reconcileCashHandover(
                            handoverId = targetReconcile.id,
                            countedPaisa = countedPaisa,
                            reason = reconcileReasonInput.trim().takeIf { it.isNotEmpty() },
                            actor = AccountableRole.FINANCE.displayName
                        )
                        onBanner.report(res)
                        if (res.first) handoverToReconcile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Text("Confirm reconciliation")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { handoverToReconcile = null }) { Text("Cancel") }
            }
        )
    }

    // Dispute Handover Dialog (blank input)
    if (showDisputeDialog && selectedHandoverForDispute != null) {
        val csh = selectedHandoverForDispute!!
        AlertDialog(
            onDismissRequest = {
                showDisputeDialog = false
                selectedHandoverForDispute = null
            },
            title = { Text("Dispute cash hand-over", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hand-over ${csh.id} (Declared ${formatRupees(csh.declaredPaisa)} including deposits):", fontSize = 12.sp)
                    OutlinedTextField(
                        value = disputeReasonInput,
                        onValueChange = { disputeReasonInput = it },
                        label = { Text("Discrepancy details") },
                        placeholder = { Text("Enter why this hand-over is disputed", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (disputeReasonInput.isBlank()) {
                            onBanner("Enter a reason before disputing.", true)
                            return@Button
                        }
                        onBanner.report(
                            store.disputeCashHandover(
                                csh.id,
                                disputeReasonInput.trim(),
                                AccountableRole.FINANCE.displayName
                            )
                        )
                        showDisputeDialog = false
                        selectedHandoverForDispute = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Confirm dispute")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showDisputeDialog = false
                    selectedHandoverForDispute = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Gateway Payout Dialog (blank inputs)
    if (showGatewayPoutDialog) {
        AlertDialog(
            onDismissRequest = { showGatewayPoutDialog = false },
            title = { Text("Reconcile gateway settlement payout", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = gatewayNameInput,
                        onValueChange = { gatewayNameInput = it },
                        label = { Text("Gateway provider") },
                        placeholder = { Text("e.g. EasyPaisa", color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = payoutBatchRefInput,
                        onValueChange = { payoutBatchRefInput = it },
                        label = { Text("Settlement batch reference") },
                        placeholder = { Text("Enter batch reference", color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = expectedAmountInput,
                        onValueChange = { expectedAmountInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Expected amount (Rs)") },
                        placeholder = { Text("0", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settledAmountInput,
                        onValueChange = { settledAmountInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Settled net amount (Rs)") },
                        placeholder = { Text("0", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = feeAmountInput,
                        onValueChange = { feeAmountInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Gateway fee (Rs)") },
                        placeholder = { Text("0", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val exp = expectedAmountInput.trim().toLongOrNull()
                        val set = settledAmountInput.trim().toLongOrNull()
                        val fee = feeAmountInput.trim().toLongOrNull() ?: 0L
                        if (gatewayNameInput.isBlank() || payoutBatchRefInput.isBlank() || exp == null || set == null) {
                            onBanner("Fill in gateway, batch reference, expected and settled amounts.", true)
                            return@Button
                        }
                        val res = store.reconcileGatewayPout(
                            gateway = gatewayNameInput.trim(),
                            batchRef = payoutBatchRefInput.trim(),
                            expectedPaisa = exp * 100L,
                            settledPaisa = set * 100L,
                            feePaisa = fee * 100L,
                            actor = AccountableRole.FINANCE.displayName
                        )
                        onBanner.report(res)
                        if (res.first) showGatewayPoutDialog = false
                    }
                ) {
                    Text("Reconcile batch")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showGatewayPoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Compact KPI card used in the Finance Deposits summary strip.
 */
@Composable
private fun DepositKpiCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontSize = 10.sp, color = Color(0xFF475569))
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
        }
    }
}

/**
 * Card letting Finance decide any deposit deduction for a returned cylinder in ITEM_RETURNED state
 * and submit the deposit refund request via [requestDepositRefund].
 */
@Composable
private fun FinanceDepositDecisionCard(
    rc: ReturnRefundCase,
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner
) {
    val deposit = remember(snapshot.version, rc.depositId, rc.cylinderSerial, rc.customerId) {
        snapshot.depositAccounts.find { it.id == rc.depositId }
            ?: snapshot.depositAccounts.find { it.customerId == rc.customerId && it.cylinderSerial == rc.cylinderSerial }
    }
    val cyl = remember(snapshot.version, rc.cylinderSerial) {
        snapshot.cylinders.find { it.serialNumber == rc.cylinderSerial }
    }
    val sizeLabel = remember(snapshot.version, cyl?.sku, deposit?.sku) {
        val sku = cyl?.sku ?: deposit?.sku
        if (sku != null) snapshot.skuLabel(sku) else "11.8 kg"
    }
    val originalPayment = remember(snapshot.version, deposit?.paymentId, deposit?.orderId) {
        deposit?.let { d ->
            snapshot.payments.find { it.id == d.paymentId }
                ?: snapshot.payments.find { it.orderId == d.orderId }
        }
    }
    val depositPaisa = deposit?.amountPaisa ?: 0L
    var deductionRsInput by remember(rc.id) { mutableStateOf("") }
    var reasonInput by remember(rc.id) { mutableStateOf("") }

    val deductionPaisa = (deductionRsInput.trim().toLongOrNull() ?: 0L) * 100L
    val refundPaisa = (depositPaisa - deductionPaisa).coerceAtLeast(0L)
    val needsReason = deductionPaisa > 0L || rc.conditionResult != ReturnCondition.GOOD

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("finance_deposit_decision_${rc.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${snapshot.customerName(rc.customerId)} · ${rc.cylinderSerial ?: "No serial"} ($sizeLabel)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (depositPaisa > 0L && deposit?.noDepositOnRecord != true) {
                        formatRupees(depositPaisa)
                    } else {
                        "No deposit on record"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = BlueGasPrimary
                )
            }

            Text(
                text = "Hub condition: ${rc.conditionResult?.label ?: "Checked"}${rc.conditionNote?.let { " · $it" } ?: ""}",
                fontSize = 11.sp,
                color = Color(0xFF475569)
            )

            if (originalPayment != null && originalPayment.state != PaymentState.SETTLED) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Original payment is ${originalPayment.state.displayName} — reconcile cash before issuing this deposit refund (UAT-37).",
                        fontSize = 10.sp,
                        color = Color(0xFF92400E),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            OutlinedTextField(
                value = deductionRsInput,
                onValueChange = { deductionRsInput = it.filter { ch -> ch.isDigit() } },
                label = { Text("Deduction (Rs)", fontSize = 11.sp) },
                placeholder = { Text("0", color = Color.Gray, fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("finance_deduction_input_${rc.id}")
            )
            OutlinedTextField(
                value = reasonInput,
                onValueChange = { reasonInput = it },
                label = { Text(if (needsReason) "Reason (required)" else "Reason (optional)", fontSize = 11.sp) },
                placeholder = { Text("Enter reason if deducting or damaged", color = Color.Gray, fontSize = 11.sp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("finance_deduction_reason_${rc.id}")
            )

            Text(
                text = "Refund = ${formatRupees(refundPaisa)}",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF166534)
            )

            Button(
                onClick = {
                    val res = store.requestDepositRefund(
                        caseId = rc.id,
                        deductionPaisa = deductionPaisa,
                        deductionReason = reasonInput.trim().takeIf { it.isNotEmpty() },
                        actor = AccountableRole.FINANCE.displayName
                    )
                    onBanner.report(res)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("finance_request_refund_btn_${rc.id}")
            ) {
                Text("Request refund", fontSize = 12.sp)
            }
        }
    }
}
