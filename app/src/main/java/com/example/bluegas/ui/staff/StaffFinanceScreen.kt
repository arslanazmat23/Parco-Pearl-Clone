package com.example.bluegas.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasPrimary

@Composable
fun StaffFinanceScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit,
    onSelectOrder: (Order) -> Unit
) {
    var selectedFinanceTab by remember { mutableStateOf("REFUNDS") }

    val totalRevenue = snapshot.ledgerEntries.filter { it.entryType == "REVENUE_GAS" || it.entryType == "REVENUE" }.sumOf { it.debitPaisa }
    val totalDepositsHeld = snapshot.ledgerEntries.filter { it.entryType == "DEPOSIT_LIABILITY" }.sumOf { it.creditPaisa }
    val totalRefunds = snapshot.ledgerEntries.filter { it.entryType == "REFUND" }.sumOf { it.creditPaisa }
    val pendingRefundsCount = snapshot.refunds.count { it.state == PaymentState.FULL_REFUND_PENDING || it.state == PaymentState.PARTIAL_REFUND_PENDING }

    // Dispute Handover Dialog state
    var showDisputeDialog by remember { mutableStateOf(false) }
    var selectedHandoverForDispute by remember { mutableStateOf<CashHandover?>(null) }
    var disputeReasonInput by remember { mutableStateOf("Cash deposit discrepancy: Physical bag shortage vs declared PKR") }

    // Gateway Reconciliation Dialog state
    var showGatewayPoutDialog by remember { mutableStateOf(false) }
    var gatewayNameInput by remember { mutableStateOf("EasyPaisa Corporate Gateway") }
    var payoutBatchRefInput by remember { mutableStateOf("BATCH-EP-2026-904") }
    var expectedAmountInput by remember { mutableStateOf("100000") }
    var settledAmountInput by remember { mutableStateOf("97500") }
    var feeAmountInput by remember { mutableStateOf("2500") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("finance_screen")
    ) {
        item {
            AccountableHeader(
                roleName = "Finance Lead",
                description = "Payment Gateway Auditing, Refund Processing, Cash Deposit Reconciliation & FBR Invoicing"
            )
            Spacer(modifier = Modifier.height(10.dp))

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
                        Text("Gross Gas Volume", fontSize = 9.sp, color = Color(0xFF1E40AF))
                        Text(formatPaisaToPkr(totalRevenue), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BlueGasPrimary)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Deposit Liability", fontSize = 9.sp, color = Color(0xFFB45309))
                        Text(formatPaisaToPkr(totalDepositsHeld), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFD97706))
                        Text("Balance Sheet Item", fontSize = 7.sp, color = Color.Gray)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Total Refunds", fontSize = 9.sp, color = Color(0xFF991B1B))
                        Text(formatPaisaToPkr(totalRefunds), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFDC2626))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Sub-tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "REFUNDS" to "Refunds ($pendingRefundsCount)",
                    "CASH_HANDOVERS" to "Cash (${snapshot.cashHandovers.size})",
                    "GATEWAYS" to "Gateway Payouts",
                    "FBR_TAX" to "FBR Invoices",
                    "LEDGER" to "Ledger"
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = selectedFinanceTab == id,
                        onClick = { selectedFinanceTab = id },
                        label = { Text(label, fontSize = 9.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        when (selectedFinanceTab) {
            "REFUNDS" -> {
                item {
                    Text("Refund Processing Queue (${snapshot.refunds.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (snapshot.refunds.isEmpty()) {
                    item { Text("No refunds in queue.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.refunds) { ref ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
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
                                    Text("Refund ${ref.id} (${ref.kind})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                Text("Order: ${ref.orderId} • Amount: ${formatPaisaToPkr(ref.amountPaisa)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("Reason: ${ref.reason}", fontSize = 10.sp, color = Color(0xFF475569))

                                if (ref.state == PaymentState.FULL_REFUND_PENDING || ref.state == PaymentState.PARTIAL_REFUND_PENDING) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.processRefund(ref.id, succeed = true, actor = "Finance Lead")
                                                onBanner(msg)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .height(30.dp)
                                        ) {
                                            Text("Approve & Disburse (Gateway)", fontSize = 9.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val (ok, msg) = store.processRefund(ref.id, succeed = false, actor = "Finance Lead")
                                                onBanner(msg)
                                            },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(30.dp)
                                        ) {
                                            Text("Simulate Gateway Fail", fontSize = 9.sp, color = Color(0xFFDC2626))
                                        }
                                    }
                                } else if (ref.state == PaymentState.FULL_REFUND_FAILED || ref.state == PaymentState.PARTIAL_REFUND_FAILED) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            val (ok, msg) = store.retryRefund(ref.id)
                                            onBanner(msg)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Retry Failed Refund", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "CASH_HANDOVERS" -> {
                item {
                    Text("Cash Handover Submissions (${snapshot.cashHandovers.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (snapshot.cashHandovers.isEmpty()) {
                    item { Text("No cash handovers on record.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.cashHandovers) { csh ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
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
                                    Text("Handover ${csh.id} • ${csh.collectorType} (${csh.collectorId})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Surface(
                                        color = when (csh.status) {
                                            "APPROVED", "RECONCILED" -> Color(0xFFDCFCE7)
                                            "DISPUTED" -> Color(0xFFFEE2E2)
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
                                                else -> Color(0xFF92400E)
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                val varPaisa = csh.differencePaisa ?: (csh.declaredPaisa - csh.expectedPaisa)
                                Text("Declared: ${formatPaisaToPkr(csh.declaredPaisa)} • Variance: ${formatPaisaToPkr(varPaisa)}", fontSize = 11.sp)
                                Text("Collector: ${csh.collectorId} • Recipient: ${csh.recipient}", fontSize = 10.sp, color = Color(0xFF475569))

                                if (csh.status == "SUBMITTED" || csh.status == "DECLARED") {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.approveCashHandover(csh.id, "Verified by Finance Lead", "Finance Lead")
                                                onBanner(msg)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(30.dp)
                                        ) {
                                            Text("Approve & Post", fontSize = 10.sp)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                selectedHandoverForDispute = csh
                                                showDisputeDialog = true
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(30.dp)
                                        ) {
                                            Text("Dispute Handover", fontSize = 10.sp, color = Color(0xFFDC2626))
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
                        Text("Payment Gateway Settlements:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Button(
                            onClick = { showGatewayPoutDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reconcile Payout", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Payment Records Audit (${snapshot.payments.size}):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                items(snapshot.payments) { pay ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${pay.id} (${pay.orderId})", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(pay.state.displayName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BlueGasPrimary)
                            }
                            Text("Method: ${pay.method.name} • Base: ${formatPaisaToPkr(pay.baseAmountPaisa)} • Settled: ${formatPaisaToPkr(pay.settledAmountPaisa)}", fontSize = 10.sp)
                            Text("Idempotency: ${pay.idempotencyKey}", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                        }
                    }
                }
            }

            "FBR_TAX" -> {
                item {
                    Text("FBR Digital Invoicing & Tax Status (${snapshot.fbrPostings.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (snapshot.fbrPostings.isEmpty()) {
                    item { Text("No FBR postings recorded.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.fbrPostings) { fbr ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Order: ${fbr.orderId}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Invoice No: ${fbr.invoiceNo ?: "Pending FBR Assignment"} • Attempts: ${fbr.attempts}", fontSize = 10.sp, color = Color(0xFF475569))
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
                                        onClick = {
                                            val (ok, msg) = store.retryFbr(fbr.orderId)
                                            onBanner(msg)
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("Retry FBR", fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "LEDGER" -> {
                item {
                    Text("Immutable Double-Entry Ledger (${snapshot.ledgerEntries.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                items(snapshot.ledgerEntries.reversed()) { led ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("[${led.entryType}] ${led.description}", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                if (led.orderId != null) {
                                    Text("Order: ${led.orderId}", fontSize = 9.sp, color = Color.Gray)
                                }
                            }
                            Text(
                                text = if (led.debitPaisa > 0) "+${formatPaisaToPkr(led.debitPaisa)}" else "-${formatPaisaToPkr(led.creditPaisa)}",
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

    // Dispute Handover Dialog
    if (showDisputeDialog && selectedHandoverForDispute != null) {
        val csh = selectedHandoverForDispute!!
        AlertDialog(
            onDismissRequest = {
                showDisputeDialog = false
                selectedHandoverForDispute = null
            },
            title = { Text("Dispute Cash Handover", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Handover ${csh.id} (Declared ${formatPaisaToPkr(csh.declaredPaisa)}):", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = disputeReasonInput,
                        onValueChange = { disputeReasonInput = it },
                        label = { Text("Discrepancy Notes / Audit Details") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.disputeCashHandover(csh.id, disputeReasonInput, "Finance Lead")
                        onBanner(msg)
                        showDisputeDialog = false
                        selectedHandoverForDispute = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Confirm Dispute")
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

    // Gateway Payout Dialog
    if (showGatewayPoutDialog) {
        AlertDialog(
            onDismissRequest = { showGatewayPoutDialog = false },
            title = { Text("Reconcile Gateway Settlement Payout", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = gatewayNameInput,
                        onValueChange = { gatewayNameInput = it },
                        label = { Text("Gateway Provider") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = payoutBatchRefInput,
                        onValueChange = { payoutBatchRefInput = it },
                        label = { Text("Bank Settlement Batch Reference") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = expectedAmountInput,
                        onValueChange = { expectedAmountInput = it },
                        label = { Text("Expected Amount (PKR)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = settledAmountInput,
                        onValueChange = { settledAmountInput = it },
                        label = { Text("Settled Net Amount (PKR)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = feeAmountInput,
                        onValueChange = { feeAmountInput = it },
                        label = { Text("Gateway Fee (PKR)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val exp = (expectedAmountInput.toLongOrNull() ?: 1000L) * 100L
                        val set = (settledAmountInput.toLongOrNull() ?: 975L) * 100L
                        val fee = (feeAmountInput.toLongOrNull() ?: 25L) * 100L
                        val (ok, msg) = store.reconcileGatewayPout(
                            gateway = gatewayNameInput,
                            batchRef = payoutBatchRefInput,
                            expectedPaisa = exp,
                            settledPaisa = set,
                            feePaisa = fee,
                            actor = "Finance Lead"
                        )
                        onBanner(msg)
                        showGatewayPoutDialog = false
                    }
                ) {
                    Text("Reconcile Batch")
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
