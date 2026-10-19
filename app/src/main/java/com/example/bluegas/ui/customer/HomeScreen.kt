package com.example.bluegas.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: BlueGasStore,
    onOrderCreated: (Order) -> Unit,
    onNavigateToOrders: () -> Unit,
    onTrackOrder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot by store.appState.collectAsStateWithLifecycle()
    val activeCustomer = snapshot.activeCustomer ?: return
    val customerAddresses = snapshot.addresses.filter { it.customerId == activeCustomer.id }

    var selectedAddressId by remember(activeCustomer.id) {
        mutableStateOf(customerAddresses.firstOrNull { it.isServiceable }?.id ?: customerAddresses.firstOrNull()?.id ?: snapshot.addresses.firstOrNull()?.id ?: "")
    }
    var selectedSku by remember { mutableStateOf("SKU-LPG-11-8") }
    var selectedMode by remember { mutableStateOf(FulfilmentMode.DELIVERY) }
    var hasEmptyCylinderToExchange by remember { mutableStateOf(true) }
    var quantity by remember { mutableStateOf(1) }
    var paymentMethodChoice by remember { mutableStateOf(PaymentMethod.ONLINE_HOSTED) }

    // Dialog state for Mocked Hosted Gateway
    var showPaymentGatewayDialog by remember { mutableStateOf(false) }
    var createdOrderIdForPayment by remember { mutableStateOf<String?>(null) }
    var orderCreationError by remember { mutableStateOf<String?>(null) }

    val activeAddress = snapshot.addresses.find { it.id == selectedAddressId }
        ?: customerAddresses.firstOrNull()
        ?: snapshot.addresses.firstOrNull()
        ?: Address(
            id = "",
            customerId = activeCustomer.id,
            label = "No Address",
            street = "No address on file",
            area = "N/A",
            city = "Lahore",
            zoneId = "Z-01",
            isServiceable = false
        )
    val selectedProduct = snapshot.productVariants.find { it.sku == selectedSku }

    val price = snapshot.priceBookConfig.productPricesPaisa[selectedSku] ?: 325000L
    val depositPerUnit = if (!hasEmptyCylinderToExchange && (selectedProduct?.isCylinder == true)) {
        snapshot.priceBookConfig.depositAmountPaisa[selectedSku] ?: 750000L
    } else 0L

    val subtotalPaisa = price * quantity
    val depositTotalPaisa = depositPerUnit * quantity
    val platformFeePaisa = snapshot.priceBookConfig.platformFeePaisa
    val deliveryFeePaisa = if (selectedMode == FulfilmentMode.DELIVERY) snapshot.priceBookConfig.deliveryFeePaisa else 0L
    val taxableBase = subtotalPaisa + platformFeePaisa + deliveryFeePaisa
    val taxPaisa = (taxableBase * snapshot.priceBookConfig.taxRateBps) / 10000L
    val estimatedTotalPaisa = taxableBase + taxPaisa + depositTotalPaisa

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("customer_home_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Greeting Banner
        item {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val greeting = when {
                hour < 12 -> "Good Morning"
                hour < 17 -> "Good Afternoon"
                else -> "Good Evening"
            }

            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                Text(
                    text = "$greeting, ${activeCustomer.name.split(" ").first()} 👋",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueGasDark
                )
                Text(
                    text = "Order safe, certified LPG delivered to your doorstep",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        // Hero Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BlueGasPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_hero_card")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = Color(0x33FFFFFF),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "OGRA & PESSI COMPLIANT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Order Certified Safe LPG",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Hydro-tested cylinders with tamper-evident digital seal delivered in 45 mins.",
                            fontSize = 11.sp,
                            color = Color(0xFFDBEAFE),
                            lineHeight = 15.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // Delivery Address Card & Serviceability
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("address_serviceability_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (activeAddress.isServiceable) BlueGasPrimary else Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Delivery Address & Service Zone",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        // Address Switcher if multiple
                        if (customerAddresses.size > 1) {
                            var showAddressMenu by remember { mutableStateOf(false) }
                            Box {
                                TextButton(
                                    onClick = { showAddressMenu = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Change", fontSize = 11.sp, color = BlueGasPrimary)
                                }
                                DropdownMenu(
                                    expanded = showAddressMenu,
                                    onDismissRequest = { showAddressMenu = false }
                                ) {
                                    customerAddresses.forEach { addr ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text("${addr.label} (${addr.city})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text(addr.street, fontSize = 10.sp, color = Color.Gray)
                                                }
                                            },
                                            onClick = {
                                                selectedAddressId = addr.id
                                                showAddressMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${activeAddress.street}, ${activeAddress.area}, ${activeAddress.city}",
                        fontSize = 12.sp,
                        color = Color(0xFF334155),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = if (activeAddress.isServiceable) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = if (activeAddress.isServiceable) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (activeAddress.isServiceable) Color(0xFF166534) else Color(0xFF991B1B),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (activeAddress.isServiceable)
                                    "✓ Zone ${activeAddress.zoneId} is fully serviceable in Lahore/Islamabad Pilot"
                                else
                                    "⚠️ Out-of-area: Orders cannot be dispatched outside Pilot boundary",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeAddress.isServiceable) Color(0xFF166534) else Color(0xFF991B1B)
                            )
                        }
                    }
                }
            }
        }

        // Section 1: Choose Product Variant
        item {
            Column {
                Text(
                    text = "1. Select LPG Product",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BlueGasDark
                )
                Text(
                    text = "All cylinders filled strictly according to calibrated weights & safety seals.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        items(snapshot.productVariants.filter { it.isHouseholdApproved || !it.isCylinder }) { pv ->
            val pvPrice = snapshot.priceBookConfig.productPricesPaisa[pv.sku] ?: 0L
            val isSelected = pv.sku == selectedSku

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFFEFF6FF) else Color.White
                ),
                border = androidx.compose.foundation.BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) BlueGasPrimary else Color(0xFFE2E8F0)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedSku = pv.sku }
                    .testTag("product_item_${pv.sku}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedSku = pv.sku },
                        colors = RadioButtonDefaults.colors(selectedColor = BlueGasPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = pv.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = formatPaisaToPkr(pvPrice),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = BlueGasPrimary
                            )
                        }
                        Text(
                            text = pv.description,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                        if (pv.cylinderSizeKg != null) {
                            Text(
                                text = "Certified Net Weight: ${pv.cylinderSizeKg} kg LPG",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF047857)
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Fulfilment Mode
        item {
            Column {
                Text(
                    text = "2. Fulfilment Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BlueGasDark
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMode == FulfilmentMode.DELIVERY,
                        onClick = { selectedMode = FulfilmentMode.DELIVERY },
                        label = { Text("Doorstep Delivery (+PKR 300)", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.DeliveryDining, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_delivery_chip")
                    )
                    FilterChip(
                        selected = selectedMode == FulfilmentMode.PICKUP,
                        onClick = { selectedMode = FulfilmentMode.PICKUP },
                        label = { Text("Self Pickup (Free)", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_pickup_chip")
                    )
                }
            }
        }

        // Section 3: Cylinder Exchange vs New Security Deposit
        if (selectedProduct?.isCylinder == true) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cylinder_deposit_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Empty Cylinder Exchange",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF78350F)
                                )
                                Text(
                                    text = if (hasEmptyCylinderToExchange)
                                        "✓ You will exchange an empty cylinder. No security deposit charged."
                                    else
                                        "⚠️ Adding ${formatPaisaToPkr(snapshot.priceBookConfig.depositAmountPaisa[selectedSku] ?: 750000L)} refundable security deposit.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF92400E)
                                )
                            }
                            Switch(
                                checked = hasEmptyCylinderToExchange,
                                onCheckedChange = { hasEmptyCylinderToExchange = it },
                                modifier = Modifier.testTag("empty_cylinder_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = Color(0x33F59E0B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Regulatory Note: Security deposit is a balance sheet liability held on your behalf. Cylinder ownership remains strictly with Blue Gas Pakistan.",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Section 4: Quantity & Payment Method
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Quantity Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Quantity", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalIconButton(
                                onClick = { if (quantity > 1) quantity-- },
                                enabled = quantity > 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = quantity.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.testTag("order_quantity_text")
                            )
                            FilledTonalIconButton(
                                onClick = { if (quantity < 5) quantity++ },
                                enabled = quantity < 5,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // Payment Method selector
                    Text("Payment Method", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = paymentMethodChoice == PaymentMethod.ONLINE_HOSTED,
                            onClick = { paymentMethodChoice = PaymentMethod.ONLINE_HOSTED },
                            label = { Text("Online Hosted", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("payment_online_chip")
                        )
                        FilterChip(
                            selected = paymentMethodChoice == PaymentMethod.CASH_ON_DELIVERY,
                            onClick = { paymentMethodChoice = PaymentMethod.CASH_ON_DELIVERY },
                            label = { Text("Cash on Delivery", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("payment_cod_chip")
                        )
                    }
                }
            }
        }

        // Section 5: Itemized Price Quotation Breakdown
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quotation_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Itemized Quotation Breakdown",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = BlueGasDark
                        )
                        Surface(
                            color = Color(0xFFDBEAFE),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Paisa Precision",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueGasPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    QuoteLineRow("LPG Refill (${quantity}x ${selectedProduct?.name ?: ""})", formatPaisaToPkr(subtotalPaisa))

                    if (depositTotalPaisa > 0) {
                        QuoteLineRow(
                            label = "Cylinder Security Deposit (Refundable Liability)",
                            value = formatPaisaToPkr(depositTotalPaisa),
                            isHighlight = true,
                            highlightColor = Color(0xFFB45309)
                        )
                    }

                    QuoteLineRow("Digital Platform Convenience Fee", formatPaisaToPkr(platformFeePaisa))
                    QuoteLineRow("Fulfilment / Delivery Fee", formatPaisaToPkr(deliveryFeePaisa))
                    QuoteLineRow("Sales Tax (${snapshot.priceBookConfig.taxRateBps / 100.0}%)", formatPaisaToPkr(taxPaisa))

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Payable Amount", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Inclusive of taxes and fees", fontSize = 10.sp, color = Color.Gray)
                        }
                        Text(
                            text = formatPaisaToPkr(estimatedTotalPaisa),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = BlueGasPrimary,
                            modifier = Modifier.testTag("total_quotation_amount")
                        )
                    }
                }
            }
        }

        // Section 6: Place Order CTA
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (orderCreationError != null) {
                    Surface(
                        color = Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = orderCreationError ?: "",
                            color = Color(0xFFDC2626),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        val (newOrder, err) = store.createOrder(
                            customerId = activeCustomer.id,
                            addressId = selectedAddressId,
                            fulfilmentMode = selectedMode,
                            sku = selectedSku,
                            quantity = quantity,
                            includeDeposit = !hasEmptyCylinderToExchange,
                            paymentMethod = paymentMethodChoice
                        )

                        if (err != null) {
                            orderCreationError = err
                        } else if (newOrder != null) {
                            orderCreationError = null
                            onOrderCreated(newOrder)

                            if (paymentMethodChoice == PaymentMethod.ONLINE_HOSTED) {
                                createdOrderIdForPayment = newOrder.id
                                showPaymentGatewayDialog = true
                            } else {
                                // COD: immediately confirm payment to advance order from PAYPEND → CONF
                                store.confirmPaymentAndAdvance(newOrder.id, actor = activeCustomer.id)
                                onNavigateToOrders()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_place_order_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                    enabled = activeAddress.isServiceable
                ) {
                    Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirm & Place Order (${formatPaisaToPkr(estimatedTotalPaisa)})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                if (!activeAddress.isServiceable) {
                    Text(
                        text = "Selected address is outside our Lahore & Islamabad pilot boundary.",
                        fontSize = 11.sp,
                        color = Color(0xFFDC2626),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Hosted Payment Gateway Dialog
    if (showPaymentGatewayDialog && createdOrderIdForPayment != null) {
        val ord = snapshot.orders.find { it.id == createdOrderIdForPayment }
        val pay = snapshot.payments.find { it.orderId == createdOrderIdForPayment }

        AlertDialog(
            onDismissRequest = {
                showPaymentGatewayDialog = false
                onNavigateToOrders()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = BlueGasPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hosted Payment Gateway", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column {
                    Text("Order: ${ord?.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "Payable: ${ord?.totalAmountPaisa?.let { formatPaisaToPkr(it) }}",
                        fontSize = 14.sp,
                        color = BlueGasPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Idempotency: ${pay?.idempotencyKey?.take(16)}...", fontSize = 10.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Demonstrator Control: Simulate gateway authorization outcome to verify order state machine transitions:",
                            fontSize = 10.sp,
                            color = Color(0xFF1E40AF),
                            modifier = Modifier.padding(6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            store.confirmPaymentAndAdvance(ord?.id ?: "", "SUCCESS")
                            showPaymentGatewayDialog = false
                            onNavigateToOrders()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gateway_success_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate Payment SUCCESS")
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            store.confirmPaymentAndAdvance(ord?.id ?: "", "DECLINED")
                            showPaymentGatewayDialog = false
                            onNavigateToOrders()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gateway_decline_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate Payment DECLINED")
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            store.confirmPaymentAndAdvance(ord?.id ?: "", "TIMEOUT")
                            showPaymentGatewayDialog = false
                            onNavigateToOrders()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gateway_timeout_button")
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simulate Payment TIMEOUT")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showPaymentGatewayDialog = false
                        onNavigateToOrders()
                    }
                ) {
                    Text("Skip for Now")
                }
            }
        )
    }
}

@Composable
private fun QuoteLineRow(
    label: String,
    value: String,
    isHighlight: Boolean = false,
    highlightColor: Color = Color(0xFF1E293B)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isHighlight) highlightColor else Color(0xFF475569),
            fontWeight = if (isHighlight) FontWeight.SemiBold else FontWeight.Normal
        )
        Text(
            text = value,
            fontSize = 11.sp,
            color = if (isHighlight) highlightColor else Color(0xFF0F172A),
            fontWeight = FontWeight.SemiBold
        )
    }
}
