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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasPrimary

@Composable
fun StaffCommercialScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit
) {
    var selectedCommercialTab by remember { mutableStateOf("PRICING") }

    val activePriceList = snapshot.priceLists.maxByOrNull { it.version } ?: PriceList(version = 1)
    val draft = snapshot.draftPriceList

    // Draft edit state
    var editRef118PricePkr by remember(draft?.reference118Paisa) {
        mutableStateOf(((draft?.reference118Paisa ?: 445000L) / 100L).toString())
    }

    // Publish dialog state
    var showPublishDialog by remember { mutableStateOf(false) }
    var reviewerNameInput by remember { mutableStateOf("Commercial Manager") }
    var ograRefInput by remember { mutableStateOf("OGRA-LPG-OCT-2026-REVISION-02") }

    // OGRA notice dialog
    var showOgraNoticeDialog by remember { mutableStateOf(false) }
    var ograNoticeRefInput by remember { mutableStateOf("S.R.O. 1421(I)/2026 OGRA LPG Price Notification") }

    // Corporate Account Dialog state
    var showCorporateDialog by remember { mutableStateOf(false) }
    var corpNameInput by remember { mutableStateOf("Pearl Continental Hotels & Resorts") }
    var corpNtnInput by remember { mutableStateOf("2918471-5") }
    var corpCreditLimitInput by remember { mutableStateOf("500000") }
    var corpPaymentTermsInput by remember { mutableStateOf("30") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("commercial_screen")
    ) {
        item {
            AccountableHeader(
                roleName = "Commercial Manager",
                description = "OGRA Price List Publishing, Draft Price Cycles, Trade Margins & Corporate B2B Credit"
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Sub-tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "PRICING" to "Active Prices",
                    "DRAFT" to if (draft != null) "Draft Active (v${draft.version})" else "Draft Cycle",
                    "OGRA" to "OGRA Notices (${snapshot.ograNotices.size})",
                    "CORPORATE" to "Corporate B2B"
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = selectedCommercialTab == id,
                        onClick = { selectedCommercialTab = id },
                        label = { Text(label, fontSize = 9.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        when (selectedCommercialTab) {
            "PRICING" -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Price List v${activePriceList.version} (Active)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "LIVE / PUBLISHED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF166534),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text("OGRA Reference: ${activePriceList.ograNotificationRef ?: "Statutory Baseline"}", fontSize = 11.sp, color = BlueGasPrimary)
                            Text("Published By: ${activePriceList.publishedBy}", fontSize = 10.sp, color = Color(0xFF475569))

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Retail Prices per Cylinder Size:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            activePriceList.perSizePaisa.forEach { (sku, paisa) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(sku, fontSize = 11.sp)
                                    Text(formatPaisaToPkr(paisa), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Deposit Liabilities (Refundable):", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            activePriceList.depositPaisa.forEach { (sku, dep) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(sku, fontSize = 11.sp, color = Color(0xFF64748B))
                                    Text(formatPaisaToPkr(dep), fontSize = 11.sp, color = Color(0xFFD97706))
                                }
                            }
                        }
                    }

                    if (draft == null) {
                        Button(
                            onClick = {
                                val (ok, msg) = store.startPriceDraft()
                                onBanner(msg)
                                selectedCommercialTab = "DRAFT"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start New Price List Draft")
                        }
                    }
                }
            }

            "DRAFT" -> {
                if (draft == null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("No draft price list currently active.", fontSize = 12.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        val (ok, msg) = store.startPriceDraft()
                                        onBanner(msg)
                                    }
                                ) {
                                    Text("Initiate Draft Price Schedule")
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCD34D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Draft Price List v${draft.version}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF92400E))
                                Text("Rule: Modifying draft does NOT affect live or in-flight orders until published with OGRA gazette ref.", fontSize = 10.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = editRef118PricePkr,
                                    onValueChange = { editRef118PricePkr = it },
                                    label = { Text("Reference 11.8kg Household Price (PKR)") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val pkr = editRef118PricePkr.toLongOrNull() ?: 4450L
                                            val new118Paisa = pkr * 100L
                                            val newMap = draft.perSizePaisa.toMutableMap()
                                            newMap["SKU-LPG-11-8"] = new118Paisa
                                            val updated = draft.copy(
                                                reference118Paisa = new118Paisa,
                                                perSizePaisa = newMap
                                            )
                                            val (ok, msg) = store.updateDraftPriceList(updated)
                                            onBanner(msg)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Save Draft Updates", fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val (ok, msg) = store.discardPriceDraft()
                                            onBanner(msg)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Discard Draft", fontSize = 10.sp, color = Color(0xFFDC2626))
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { showPublishDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Publish Live (OGRA Compliant)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            "OGRA" -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("OGRA Price Revision Notifications (${snapshot.ograNotices.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Button(
                            onClick = { showOgraNoticeDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Record Notice", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (snapshot.ograNotices.isEmpty()) {
                    item { Text("No OGRA price notifications on record.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.ograNotices) { noti ->
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
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(noti.id, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Surface(
                                        color = if (noti.reviewed) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (noti.reviewed) "REVIEWED" else "ACTION PENDING",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (noti.reviewed) Color(0xFF166534) else Color(0xFF92400E),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text("Official Reference: ${noti.reference}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            "CORPORATE" -> {
                item {
                    Text("Corporate B2B Credit Accounts:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Commercial Policy Gate:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = "Rule: Household cylinders are strictly cash-on-delivery or prepay. Corporate B2B credit terms are restricted to approved commercial accounts.",
                                fontSize = 10.sp,
                                color = Color(0xFF475569)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showCorporateDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Register Corporate Account")
                            }
                        }
                    }
                }
            }
        }
    }

    // Publish Live Dialog
    if (showPublishDialog) {
        AlertDialog(
            onDismissRequest = { showPublishDialog = false },
            title = { Text("Publish Price List Live", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Publishing this price list will activate new retail prices for all future household and commercial orders.", fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reviewerNameInput,
                        onValueChange = { reviewerNameInput = it },
                        label = { Text("Authorizing Reviewer Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = ograRefInput,
                        onValueChange = { ograRefInput = it },
                        label = { Text("Official OGRA Gazetted Reference") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.publishPriceList(reviewerNameInput, ograRefInput)
                        onBanner(msg)
                        showPublishDialog = false
                        selectedCommercialTab = "PRICING"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Text("Confirm & Publish")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPublishDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // OGRA Notice Dialog
    if (showOgraNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showOgraNoticeDialog = false },
            title = { Text("Log Received OGRA Price Notice", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = ograNoticeRefInput,
                        onValueChange = { ograNoticeRefInput = it },
                        label = { Text("Official Notification Ref / Gazette S.R.O.") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.recordOgraNotice(ograNoticeRefInput)
                        onBanner(msg)
                        showOgraNoticeDialog = false
                    }
                ) {
                    Text("Record Notice")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showOgraNoticeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Corporate Account Dialog
    if (showCorporateDialog) {
        AlertDialog(
            onDismissRequest = { showCorporateDialog = false },
            title = { Text("Create Corporate B2B Account", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = corpNameInput,
                        onValueChange = { corpNameInput = it },
                        label = { Text("Corporate Entity Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = corpNtnInput,
                        onValueChange = { corpNtnInput = it },
                        label = { Text("FBR NTN Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = corpCreditLimitInput,
                        onValueChange = { corpCreditLimitInput = it },
                        label = { Text("Approved Credit Limit (PKR)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = corpPaymentTermsInput,
                        onValueChange = { corpPaymentTermsInput = it },
                        label = { Text("Payment Terms (Net Days)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onBanner("Corporate account registered: $corpNameInput (Terms: Net $corpPaymentTermsInput days)")
                        showCorporateDialog = false
                    }
                ) {
                    Text("Create Account")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCorporateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
