package com.example.bluegas.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.model.*
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAppScreen(store: BlueGasStore) {
    var selectedSku by remember { mutableStateOf("SKU-LPG-11-8") }
    var selectedMode by remember { mutableStateOf(FulfilmentMode.DELIVERY) }
    var includeDeposit by remember { mutableStateOf(true) }
    var quantity by remember { mutableStateOf(1) }
    var selectedAddressId by remember { mutableStateOf(store.customers.first().defaultAddressId) }
    var paymentMethodChoice by remember { mutableStateOf(PaymentMethod.ONLINE_HOSTED) }

    // Dialog states
    var showPaymentGatewayDialog by remember { mutableStateOf(false) }
    var targetOrderIdForPayment by remember { mutableStateOf<String?>(null) }
    var showComplaintDialog by remember { mutableStateOf(false) }
    var targetOrderIdForComplaint by remember { mutableStateOf<String?>(null) }
    var complaintType by remember { mutableStateOf("CYLINDER_DENT") }
    var complaintDetails by remember { mutableStateOf("") }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    val activeCustomer = store.customers.find { it.id == store.activeCustomerId } ?: store.customers.first()
    val activeAddress = store.addresses.find { it.id == selectedAddressId } ?: store.addresses.first()
    val customerOrders = store.orders.filter { it.customerId == activeCustomer.id }

    // Phone frame centered container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE2E8F0)),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Customer Mobile Header
                Surface(
                    color = BlueGasPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("BLUE GAS CONSUMER", fontSize = 11.sp, color = Color(0xFFBFDBFE), fontWeight = FontWeight.Bold)
                                Text(activeCustomer.name, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("CNIC: ${activeCustomer.cnic}", fontSize = 10.sp, color = Color(0xFFE0E7FF))
                            }
                            // Customer switcher
                            Box {
                                var expanded by remember { mutableStateOf(false) }
                                OutlinedButton(
                                    onClick = { expanded = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Switch", fontSize = 11.sp)
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    store.customers.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text("${c.name} (${c.id})") },
                                            onClick = {
                                                store.activeCustomerId = c.id
                                                selectedAddressId = c.defaultAddressId
                                                expanded = false
                                                store.notifyChanged()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Address & Serviceability chip
                        Surface(
                            color = if (activeAddress.isServiceable) Color(0xFF1E3A8A) else Color(0xFF7F1D1D),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (activeAddress.isServiceable) Icons.Default.LocationOn else Icons.Default.LocationOff,
                                    contentDescription = null,
                                    tint = if (activeAddress.isServiceable) Color(0xFF93C5FD) else Color(0xFFFCA5A5),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${activeAddress.label}: ${activeAddress.street}, ${activeAddress.area}, ${activeAddress.city}",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                    Text(
                                        if (activeAddress.isServiceable) "Serviceable Zone (${activeAddress.zoneId})" else "OUT OF SERVICE PILOT AREA",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeAddress.isServiceable) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
                                    )
                                }
                            }
                        }
                    }
                }

                // Action banner notification
                if (actionMessage != null) {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(actionMessage ?: "", fontSize = 11.sp, color = Color(0xFF92400E), modifier = Modifier.weight(1f))
                            IconButton(onClick = { actionMessage = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    // SECTION 1: CATALOGUE
                    item {
                        Text("1. Select LPG Product", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    items(store.productVariants) { pv ->
                        val price = store.priceBookConfig.productPricesPaisa[pv.sku] ?: 0L
                        val isSelected = pv.sku == selectedSku

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                if (isSelected) 2.dp else 1.dp,
                                if (isSelected) BlueGasPrimary else Color(0xFFE2E8F0)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedSku = pv.sku }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedSku = pv.sku }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pv.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(pv.description, fontSize = 10.sp, color = Color.Gray)
                                    Text(
                                        "Refill Price: ${formatPaisaToPkr(price)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = BlueGasPrimary
                                    )
                                }
                            }
                        }
                    }

                    // SECTION 2: ORDER CONFIGURATION
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("2. Fulfilment & Pricing Setup", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))

                        // Mode selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedMode == FulfilmentMode.DELIVERY,
                                onClick = { selectedMode = FulfilmentMode.DELIVERY },
                                label = { Text("Doorstep Delivery (+PKR 300)", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.DeliveryDining, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedMode == FulfilmentMode.PICKUP,
                                onClick = { selectedMode = FulfilmentMode.PICKUP },
                                label = { Text("Self Pickup (Free)", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Deposit toggle
                        val currentProd = store.productVariants.find { it.sku == selectedSku }
                        if (currentProd?.isCylinder == true) {
                            val depPaisa = store.priceBookConfig.depositAmountPaisa[selectedSku] ?: 750000L
                            Surface(
                                color = Color(0xFFFFFBEB),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Include Cylinder Security Deposit?", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(
                                                "Add ${formatPaisaToPkr(depPaisa)} refundable deposit if getting a new cylinder package.",
                                                fontSize = 10.sp,
                                                color = Color(0xFF92400E)
                                            )
                                        }
                                        Switch(
                                            checked = includeDeposit,
                                            onCheckedChange = { includeDeposit = it }
                                        )
                                    }
                                    Text(
                                        "Note: Deposit is a balance sheet liability. It does NOT transfer cylinder ownership to customer.",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFB45309),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }

                        // Payment method choice
                        Text("Payment Method:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = paymentMethodChoice == PaymentMethod.ONLINE_HOSTED,
                                onClick = { paymentMethodChoice = PaymentMethod.ONLINE_HOSTED },
                                label = { Text("Online Hosted", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = paymentMethodChoice == PaymentMethod.CASH_ON_DELIVERY,
                                onClick = { paymentMethodChoice = PaymentMethod.CASH_ON_DELIVERY },
                                label = { Text("Cash on Delivery", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Dynamic Itemized Pricing Table
                        val p = store.priceBookConfig.productPricesPaisa[selectedSku] ?: 300000L
                        val d = if (includeDeposit && (currentProd?.isCylinder == true)) (store.priceBookConfig.depositAmountPaisa[selectedSku] ?: 750000L) else 0L
                        val sub = p * quantity
                        val dep = d * quantity
                        val plat = store.priceBookConfig.platformFeePaisa
                        val del = if (selectedMode == FulfilmentMode.DELIVERY) store.priceBookConfig.deliveryFeePaisa else 0L
                        val tax = ((sub + plat + del) * store.priceBookConfig.taxRateBps) / 10000L
                        val total = sub + plat + del + tax + dep

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Itemized Quotation (in Paisa / PKR)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                PriceLineRow("LPG Refill (${quantity}x)", formatPaisaToPkr(sub))
                                if (dep > 0) {
                                    PriceLineRow("Security Deposit (Liability)", formatPaisaToPkr(dep), isDeposit = true)
                                }
                                PriceLineRow("Digital Platform Fee", formatPaisaToPkr(plat))
                                PriceLineRow("Fulfilment / Delivery Fee", formatPaisaToPkr(del))
                                PriceLineRow("Tax (5%)", formatPaisaToPkr(tax))
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Estimated Total Payable", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(formatPaisaToPkr(total), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BlueGasPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // One-tap place order button
                        Button(
                            onClick = {
                                val (order, err) = store.createOrder(
                                    customerId = activeCustomer.id,
                                    addressId = selectedAddressId,
                                    fulfilmentMode = selectedMode,
                                    sku = selectedSku,
                                    quantity = quantity,
                                    includeDeposit = includeDeposit,
                                    paymentMethod = paymentMethodChoice
                                )
                                if (err != null) {
                                    actionMessage = "Error: $err"
                                } else {
                                    actionMessage = "Order ${order?.id} created! Stock hold placed for ${store.priceBookConfig.holdTtlMinutes}m."
                                    if (paymentMethodChoice == PaymentMethod.ONLINE_HOSTED) {
                                        targetOrderIdForPayment = order?.id
                                        showPaymentGatewayDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                            enabled = activeAddress.isServiceable
                        ) {
                            Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Confirm & Place Order", fontWeight = FontWeight.Bold)
                        }

                        if (!activeAddress.isServiceable) {
                            Text(
                                "Cannot order: Selected address is outside active pilot zones (Lahore & Islamabad only).",
                                color = Color(0xFFDC2626),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("3. Your Active Orders & History (${customerOrders.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    // SECTION 3: ORDERS LIST
                    if (customerOrders.isEmpty()) {
                        item {
                            Text("No orders placed yet.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    } else {
                        items(customerOrders) { ord ->
                            OrderCardCustomerView(
                                order = ord,
                                store = store,
                                onPayOnline = {
                                    targetOrderIdForPayment = ord.id
                                    showPaymentGatewayDialog = true
                                },
                                onReportComplaint = {
                                    targetOrderIdForComplaint = ord.id
                                    showComplaintDialog = true
                                },
                                onSelectForDev = {
                                    store.selectedOrderIdForDrawer = ord.id
                                    store.notifyChanged()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Mocked Hosted Gateway Dialog (Force Success, Decline, Timeout)
    if (showPaymentGatewayDialog && targetOrderIdForPayment != null) {
        val ord = store.orders.find { it.id == targetOrderIdForPayment }
        val pay = store.payments.find { it.orderId == targetOrderIdForPayment }

        AlertDialog(
            onDismissRequest = { showPaymentGatewayDialog = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPaymentGatewayDialog = false }) { Text("Cancel") }
            },
            title = {
                Text("Mocked Hosted Payment Gateway", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            },
            text = {
                Column {
                    Text("Order: ${ord?.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Amount: ${ord?.totalAmountPaisa?.let { formatPaisaToPkr(it) }}", fontSize = 13.sp, color = BlueGasPrimary, fontWeight = FontWeight.Bold)
                    Text("Idempotency Key: ${pay?.idempotencyKey}", fontSize = 10.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Demonstrator Control: Force gateway result to test lifecycle paths:", fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val (success, msg) = store.confirmPaymentAndAdvance(ord?.id ?: "", "SUCCESS")
                            actionMessage = msg
                            showPaymentGatewayDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Force Payment SUCCESS")
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val (success, msg) = store.confirmPaymentAndAdvance(ord?.id ?: "", "DECLINED")
                            actionMessage = msg
                            showPaymentGatewayDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Force Payment DECLINE")
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedButton(
                        onClick = {
                            val (success, msg) = store.confirmPaymentAndAdvance(ord?.id ?: "", "TIMEOUT")
                            actionMessage = msg
                            showPaymentGatewayDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Force Payment TIMEOUT")
                    }
                }
            }
        )
    }

    // Post-completion Complaint Dialog
    if (showComplaintDialog && targetOrderIdForComplaint != null) {
        AlertDialog(
            onDismissRequest = { showComplaintDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val (success, msg) = store.raiseComplaint(
                            orderId = targetOrderIdForComplaint ?: "",
                            issueType = complaintType,
                            description = complaintDetails.ifBlank { "Customer reported issue: $complaintType" }
                        )
                        actionMessage = msg
                        showComplaintDialog = false
                    }
                ) {
                    Text("Submit Dispute Case")
                }
            },
            dismissButton = {
                TextButton(onClick = { showComplaintDialog = false }) { Text("Cancel") }
            },
            title = {
                Text("Report Issue / Safety Dispute", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Order: $targetOrderIdForComplaint", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Note: Completed order stays Completed. A separate SupportCase is opened.", fontSize = 10.sp, color = Color(0xFFB45309))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Issue Type:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    listOf("CYLINDER_DENT", "LEAKAGE_SAFETY", "OVERCHARGING", "DELAYED_DELIVERY").forEach { t ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { complaintType = t }
                        ) {
                            RadioButton(selected = complaintType == t, onClick = { complaintType = t })
                            Text(t.replace("_", " "), fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = complaintDetails,
                        onValueChange = { complaintDetails = it },
                        placeholder = { Text("Describe situation / safety remarks...", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }
        )
    }
}

@Composable
private fun PriceLineRow(label: String, value: String, isDeposit: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isDeposit) Color(0xFFB45309) else Color(0xFF475569),
            fontWeight = if (isDeposit) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = if (isDeposit) Color(0xFFB45309) else Color(0xFF1E293B),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OrderCardCustomerView(
    order: Order,
    store: BlueGasStore,
    onPayOnline: () -> Unit,
    onReportComplaint: () -> Unit,
    onSelectForDev: () -> Unit
) {
    val payment = store.payments.find { it.orderId == order.id }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = onSelectForDev)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(order.id, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                CanonicalStatusChip(order.status)
            }

            Spacer(modifier = Modifier.height(4.dp))
            order.items.forEach { itm ->
                Text("${itm.quantity}x ${itm.productName}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Total: ${formatPaisaToPkr(order.totalAmountPaisa)} • ${order.paymentMethod.name}", fontSize = 11.sp, color = BlueGasPrimary)

            if (order.allocatedSerial != null) {
                Text("Assigned Serial: ${order.allocatedSerial}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
            }

            // Visible Hold TTL countdown
            if (order.status == OrderStatus.ORD_PAYPEND && order.holdExpiresAt != null) {
                val remainingMs = order.holdExpiresAt - System.currentTimeMillis()
                val remainingMins = (remainingMs / 60000).coerceAtLeast(0)
                Surface(
                    color = Color(0xFFFFFBEB),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        "Stock Hold TTL: ~${remainingMins}m remaining before release",
                        fontSize = 10.sp,
                        color = Color(0xFFB45309),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Actions for Customer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (order.status == OrderStatus.ORD_PAYPEND && order.paymentMethod == PaymentMethod.ONLINE_HOSTED) {
                    Button(
                        onClick = onPayOnline,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Pay Online (Gateway)", fontSize = 10.sp)
                    }
                }

                if (order.status == OrderStatus.ORD_COMP) {
                    OutlinedButton(
                        onClick = onReportComplaint,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Report Dispute", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
