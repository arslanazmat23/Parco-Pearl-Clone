package com.example.bluegas.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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

@Composable
fun DemoValuePill(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFFE2E8F0),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF475569),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: BlueGasStore,
    onOrderCreated: (Order) -> Unit,
    onNavigateToOrders: () -> Unit,
    onTrackOrder: (String) -> Unit,
    modifier: Modifier = Modifier,
    prefillSku: String? = null,
    prefillQuantity: Int? = null,
    prefillOrderType: OrderType? = null,
    prefillAddressId: String? = null,
    prefillMode: FulfilmentMode? = null,
    prefillPaymentMethod: PaymentMethod? = null
) {
    val snapshot by store.appState.collectAsStateWithLifecycle()
    val activeCustomer = snapshot.activeCustomer ?: return
    val customerAddresses = snapshot.addresses.filter { it.customerId == activeCustomer.id }

    var selectedAddressId by remember(activeCustomer.id) {
        mutableStateOf(
            prefillAddressId
                ?: customerAddresses.firstOrNull { it.isServiceable }?.id
                ?: customerAddresses.firstOrNull()?.id
                ?: snapshot.addresses.firstOrNull()?.id
                ?: ""
        )
    }
    var orderType by remember { mutableStateOf(prefillOrderType ?: OrderType.REFILL_EXCHANGE) }
    var selectedSku by remember { mutableStateOf(prefillSku ?: "SKU-LPG-11-8") }
    var selectedMode by remember { mutableStateOf(prefillMode ?: FulfilmentMode.DELIVERY) }
    var hasEmptyCylinderToExchange by remember { mutableStateOf(true) }
    var quantity by remember { mutableIntStateOf(prefillQuantity ?: 1) }
    var paymentMethodChoice by remember { mutableStateOf(prefillPaymentMethod ?: PaymentMethod.ONLINE_HOSTED) }

    // Nearest pickup point for depot pickup
    val availablePickupNodes = snapshot.nodes.filter { it.pickupEnabled && it.type != FulfilmentPointType.PLANT }
        .ifEmpty { snapshot.nodes.filter { it.pickupEnabled } }
    var selectedPickupNodeId by remember {
        mutableStateOf(availablePickupNodes.firstOrNull()?.id ?: "NODE-LHR-1")
    }

    // Prefill updates when navigating from Reorder
    LaunchedEffect(prefillSku, prefillQuantity, prefillOrderType, prefillAddressId, prefillMode, prefillPaymentMethod) {
        if (prefillSku != null) selectedSku = prefillSku
        if (prefillQuantity != null) quantity = prefillQuantity
        if (prefillOrderType != null) orderType = prefillOrderType
        if (prefillAddressId != null) selectedAddressId = prefillAddressId
        if (prefillMode != null) selectedMode = prefillMode
        if (prefillPaymentMethod != null) paymentMethodChoice = prefillPaymentMethod
    }

    // Dialog state for Mocked Hosted Gateway
    var showPaymentGatewayDialog by remember { mutableStateOf(false) }
    var createdOrderIdForPayment by remember { mutableStateOf<String?>(null) }
    var orderCreationError by remember { mutableStateOf<String?>(null) }
    var feedbackSnackbarMessage by remember { mutableStateOf<String?>(null) }

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
    val selectedPickupNode = snapshot.nodes.find { it.id == selectedPickupNodeId } ?: availablePickupNodes.firstOrNull()

    // Deposit inclusion logic
    val includeDeposit = when (orderType) {
        OrderType.NEW_CYLINDER -> snapshot.settings.newCylinderModel == NewCylinderModel.DEPOSIT
        OrderType.REFILL_EXCHANGE -> !hasEmptyCylinderToExchange
        else -> false
    }

    // Unified pricing quote with PricingEngine
    val quote = remember(
        selectedSku,
        quantity,
        selectedMode,
        orderType,
        includeDeposit,
        snapshot.currentPriceList,
        snapshot.taxConfig,
        snapshot.settings
    ) {
        PricingEngine.buildQuote(
            orderType = orderType,
            sku = selectedSku,
            quantity = quantity,
            accessories = emptyList(),
            mode = selectedMode,
            priceList = snapshot.currentPriceList,
            tax = snapshot.taxConfig,
            settings = snapshot.settings,
            discountPaisa = 0L
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("customer_home_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Feedback Message Banner
        if (feedbackSnackbarMessage != null) {
            item {
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = feedbackSnackbarMessage ?: "",
                            fontSize = 11.sp,
                            color = Color(0xFF166534),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { feedbackSnackbarMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color(0xFF166534), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

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
                    text = "LPG delivered to your doorstep",
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
                        Text(
                            text = "Order LPG in one tap",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Clear pricing, doorstep delivery and live tracking.",
                            fontSize = 11.sp,
                            color = Color(0xFFDBEAFE),
                            lineHeight = 15.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Order Type Selector (Q03 / Q20)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth().testTag("order_type_selector_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "What do you need?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = BlueGasDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val types = listOf(
                            Triple("Refill (exchange)", OrderType.REFILL_EXCHANGE, "order_type_refill"),
                            Triple("New cylinder", OrderType.NEW_CYLINDER, "order_type_new"),
                            Triple("Accessory only", OrderType.ACCESSORY_ONLY, "order_type_accessory")
                        )
                        types.forEachIndexed { index, (label, type, tag) ->
                            SegmentedButton(
                                selected = orderType == type,
                                onClick = { orderType = type },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                                modifier = Modifier.testTag(tag)
                            ) {
                                Text(label, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }

                    // Order Type Context Note
                    Spacer(modifier = Modifier.height(6.dp))
                    when (orderType) {
                        OrderType.NEW_CYLINDER -> {
                            val modelNote = when (snapshot.settings.newCylinderModel) {
                                NewCylinderModel.SOLD -> "You will own the cylinder"
                                NewCylinderModel.DEPOSIT -> "Held against a refundable deposit — you don't own it"
                                NewCylinderModel.LOAN -> "Loaned to you"
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DemoValuePill("Demo value · Q03")
                                Text(
                                    text = modelNote,
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        OrderType.REFILL_EXCHANGE -> {
                            Text(
                                text = "Standard refill: Empty cylinder exchange at handover.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        OrderType.ACCESSORY_ONLY -> {
                            Text(
                                text = "Order regulators, safety hoses and fittings without gas refill.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        else -> {}
                    }
                }
            }
        }

        // Delivery Address Card & Serviceability (S3)
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
                                text = "Delivery Address & Service Area",
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
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
                                    "This area is in the delivery zone."
                                else
                                    "This address is outside the current delivery area.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeAddress.isServiceable) Color(0xFF166534) else Color(0xFF991B1B),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Depot pickup fallback when out of delivery area
                    if (!activeAddress.isServiceable) {
                        Spacer(modifier = Modifier.height(6.dp))
                        if (selectedMode == FulfilmentMode.PICKUP) {
                            Surface(
                                color = Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Icon(Icons.Default.Store, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "Pickup Depot: ${selectedPickupNode?.name ?: "Main Depot"}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = BlueGasDark
                                        )
                                        Text(
                                            text = selectedPickupNode?.address ?: "${selectedPickupNode?.city ?: "Lahore"} Collection Counter",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    selectedMode = FulfilmentMode.PICKUP
                                    if (availablePickupNodes.isNotEmpty()) {
                                        selectedPickupNodeId = availablePickupNodes.first().id
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("switch_to_depot_pickup_button"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = BlueGasPrimary)
                            ) {
                                Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Switch to depot pickup", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // Section 1: Choose Product Variant
        if (orderType != OrderType.ACCESSORY_ONLY) {
            item {
                Column {
                    Text(
                        text = "1. Select LPG Product",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = BlueGasDark
                    )
                    Text(
                        text = "Prices are shown before you confirm.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            items(snapshot.productVariants.filter { it.isHouseholdApproved || !it.isCylinder }) { pv ->
                val pubPrice = PricingEngine.unitPricePublished(pv.sku, snapshot.currentPriceList)
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
                                    text = formatRupees(pubPrice),
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
                                    text = "Cylinder size: ${pv.cylinderSizeKg} kg",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF047857)
                                )
                            }
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
                    val deliveryFeeLabel = formatRupees(snapshot.currentPriceList.deliveryFeePaisa)
                    FilterChip(
                        selected = selectedMode == FulfilmentMode.DELIVERY,
                        onClick = { selectedMode = FulfilmentMode.DELIVERY },
                        label = { Text("Doorstep delivery (+$deliveryFeeLabel)", fontSize = 11.sp) },
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
                        label = { Text("Depot pickup (free)", fontSize = 11.sp) },
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
        if (orderType == OrderType.REFILL_EXCHANGE && selectedProduct?.isCylinder == true) {
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
                                        "I'll hand back an empty BLUE GAS cylinder at delivery."
                                    else
                                        "No empty cylinder to hand back.",
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

                        if (!hasEmptyCylinderToExchange) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = Color(0x33F59E0B),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "Refill requires an empty cylinder exchange. Please select 'New cylinder' above if you need an additional cylinder.",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
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
                    val maxQty = snapshot.settings.maxQuantityPerOrder
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Quantity", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Max $maxQty per order", fontSize = 10.sp, color = Color.Gray)
                        }
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
                                onClick = { if (quantity < maxQty) quantity++ },
                                enabled = quantity < maxQty,
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

        // Section 5: Itemized Price Quotation Breakdown (S2)
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
                    Text(
                        text = "Itemized Quotation Breakdown",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = BlueGasDark
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    // Quote line items
                    quote.lines.forEach { line ->
                        if (line.lineType != "DEPOSIT") {
                            QuoteLineRow("${line.quantity}x ${line.productName}", formatRupees(line.subtotalPaisa))
                        }
                    }

                    // Fulfilment fee if delivery
                    if (selectedMode == FulfilmentMode.DELIVERY) {
                        QuoteLineRow("Fulfilment / Delivery Fee", formatRupees(quote.deliveryFeePaisa))
                    }

                    // Convenience fee
                    QuoteLineRow("Digital Platform Convenience Fee", formatRupees(quote.platformFeePaisa))

                    // Deposit line (if present)
                    if (quote.depositTotalPaisa > 0) {
                        QuoteLineRow(
                            label = "Cylinder Security Deposit",
                            value = formatRupees(quote.depositTotalPaisa),
                            isHighlight = true,
                            highlightColor = Color(0xFFB45309)
                        )
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Security deposit — refundable per BLUE GAS policy (Q03). A deposit does not make you the owner.",
                                fontSize = 9.sp,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(6.dp),
                                lineHeight = 13.sp
                            )
                        }
                    }

                    // Discount (if present)
                    if (quote.discountPaisa > 0) {
                        QuoteLineRow(
                            label = "Promotional Discount",
                            value = "-${formatRupees(quote.discountPaisa)}",
                            isHighlight = true,
                            highlightColor = Color(0xFF16A34A)
                        )
                    }

                    // Taxes
                    val gstPct = snapshot.taxConfig.gstBps / 100.0
                    QuoteLineRow("Sales Tax ($gstPct%)", formatRupees(quote.gstPaisa))
                    if (quote.furtherTaxPaisa > 0) {
                        val fTaxPct = snapshot.taxConfig.furtherTaxBps / 100.0
                        QuoteLineRow("Further Tax ($fTaxPct%)", formatRupees(quote.furtherTaxPaisa))
                    }

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
                            text = formatRupees(quote.totalAmountPaisa),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = BlueGasPrimary,
                            modifier = Modifier.testTag("total_quotation_amount")
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Prices, fees and tax are demo settings pending client decisions (Q03, Q04, Q22, Q24).",
                        fontSize = 9.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 12.sp
                    )
                }
            }
        }

        // Section 6: Place Order CTA
        item {
            val isCheckoutAllowed = selectedMode == FulfilmentMode.PICKUP || activeAddress.isServiceable
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
                            addressId = activeAddress.id,
                            fulfilmentMode = selectedMode,
                            sku = selectedSku,
                            quantity = quantity,
                            includeDeposit = includeDeposit,
                            paymentMethod = paymentMethodChoice,
                            orderType = orderType,
                            pickupPointId = if (selectedMode == FulfilmentMode.PICKUP) selectedPickupNodeId else null
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
                                // COD: confirm payment and advance order
                                store.confirmPaymentAndAdvance(newOrder.id, actor = activeCustomer.id)
                                feedbackSnackbarMessage = "Order placed — pay cash on delivery."
                                store.setCustomerTab("ORDERS")
                                onNavigateToOrders()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_place_order_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                    enabled = isCheckoutAllowed
                ) {
                    Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Confirm & Place Order (${formatRupees(quote.totalAmountPaisa)})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                if (!isCheckoutAllowed) {
                    Text(
                        text = "Selected address is outside our delivery area. Switch to depot pickup above to proceed.",
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

    // Demo Payment Dialog (S4)
    if (showPaymentGatewayDialog && createdOrderIdForPayment != null) {
        val ord = snapshot.orders.find { it.id == createdOrderIdForPayment }

        AlertDialog(
            onDismissRequest = {
                showPaymentGatewayDialog = false
                store.setCustomerTab("ORDERS")
                onNavigateToOrders()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = BlueGasPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pay now (demo)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column {
                    Text("Order: ${ord?.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "Payable: ${ord?.totalAmountPaisa?.let { formatRupees(it) }}",
                        fontSize = 14.sp,
                        color = BlueGasPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This is a demo gateway for the pilot. Choose an outcome:",
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val (_, msg) = store.confirmPaymentAndAdvance(ord?.id ?: "", "SUCCESS")
                            feedbackSnackbarMessage = "Payment received — order confirmed."
                            showPaymentGatewayDialog = false
                            store.setCustomerTab("ORDERS")
                            onNavigateToOrders()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gateway_success_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Payment approved")
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            val (_, msg) = store.confirmPaymentAndAdvance(ord?.id ?: "", "DECLINED")
                            feedbackSnackbarMessage = msg
                            showPaymentGatewayDialog = false
                            store.setCustomerTab("ORDERS")
                            onNavigateToOrders()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gateway_decline_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Payment declined")
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            val (_, msg) = store.confirmPaymentAndAdvance(ord?.id ?: "", "TIMEOUT")
                            feedbackSnackbarMessage = msg
                            showPaymentGatewayDialog = false
                            store.setCustomerTab("ORDERS")
                            onNavigateToOrders()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gateway_timeout_button")
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connection lost")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showPaymentGatewayDialog = false
                        store.setCustomerTab("ORDERS")
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
