package com.example.bluegas.ui.customer

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.data.PricingEngine
import com.example.bluegas.data.confirmPaymentAndAdvance
import com.example.bluegas.data.createOrder
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

// F5 HomeScreen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: BlueGasStore,
    onOrderCreated: (Order) -> Unit,
    onNavigateToOrders: () -> Unit,
    onTrackOrder: (String) -> Unit,
    onActionMessage: (String, Boolean) -> Unit,
    prefill: ReorderPrefill? = null,
    onPrefillConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val snapshot by store.appState.collectAsStateWithLifecycle()
    val activeCustomer = snapshot.activeCustomer ?: return
    val customerAddresses = snapshot.addresses.filter { it.customerId == activeCustomer.id }

    // F5.6 Choices held in rememberSaveable across tabs
    var orderType by rememberSaveable { mutableStateOf(OrderType.REFILL_EXCHANGE) }
    var selectedSku by rememberSaveable { mutableStateOf("SKU-LPG-11-8") }
    var selectedAddressId by rememberSaveable {
        mutableStateOf(
            customerAddresses.firstOrNull { it.isServiceable }?.id
                ?: customerAddresses.firstOrNull()?.id
                ?: ""
        )
    }
    var selectedMode by rememberSaveable { mutableStateOf(FulfilmentMode.DELIVERY) }
    var hasEmptyCylinderToExchange by rememberSaveable { mutableStateOf(true) }
    var quantity by rememberSaveable { mutableIntStateOf(1) }
    var paymentMethodChoice by rememberSaveable { mutableStateOf(PaymentMethod.ONLINE_HOSTED) }
    var addAccessory by rememberSaveable { mutableStateOf(false) }
    var selectedPickupNodeId by rememberSaveable { mutableStateOf("") }

    // F5.7 Double-tap guard
    var placing by remember { mutableStateOf(false) }
    var createdOrderIdForPayment by remember { mutableStateOf<String?>(null) }
    var showPaymentGatewayDialog by remember { mutableStateOf(false) }

    // Reset when switching customer
    LaunchedEffect(activeCustomer.id) {
        val defaultAddr = customerAddresses.firstOrNull { it.isDefault }
            ?: customerAddresses.firstOrNull { it.isServiceable }
            ?: customerAddresses.firstOrNull()
        selectedAddressId = defaultAddr?.id ?: ""
        selectedSku = "SKU-LPG-11-8"
        quantity = 1
        orderType = OrderType.REFILL_EXCHANGE
        selectedMode = FulfilmentMode.DELIVERY
        paymentMethodChoice = PaymentMethod.ONLINE_HOSTED
        addAccessory = false
    }

    // F5.5 Apply Reorder Prefill
    LaunchedEffect(prefill?.nonce) {
        val p = prefill ?: return@LaunchedEffect
        if (customerAddresses.any { it.id == p.addressId }) {
            selectedAddressId = p.addressId ?: ""
        } else {
            selectedAddressId = customerAddresses.firstOrNull { it.isDefault }?.id
                ?: customerAddresses.firstOrNull { it.isServiceable }?.id
                ?: customerAddresses.firstOrNull()?.id
                ?: ""
        }
        val approvedSkus = snapshot.productVariants.filter { it.isCylinder && it.isHouseholdApproved }.map { it.sku }
        selectedSku = if (p.sku in approvedSkus) p.sku else (approvedSkus.firstOrNull() ?: "SKU-LPG-11-8")
        quantity = p.quantity.coerceIn(1, snapshot.settings.maxQuantityPerOrder)
        orderType = if (p.orderType == OrderType.NEW_CYLINDER) OrderType.NEW_CYLINDER else OrderType.REFILL_EXCHANGE
        selectedMode = p.mode
        paymentMethodChoice = p.paymentMethod
        addAccessory = p.addAccessory
        onPrefillConsumed()
    }

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

    // F5.3 Pickup depots restricted to address city
    val cityPickupNodes = snapshot.nodes.filter {
        it.pickupEnabled && it.type != FulfilmentPointType.PLANT && it.city.equals(activeAddress.city, ignoreCase = true)
    }

    // Force PICKUP when address is not serviceable, and set valid pickup depot
    LaunchedEffect(activeAddress.id, activeAddress.isServiceable, cityPickupNodes.size) {
        if (!activeAddress.isServiceable) {
            selectedMode = FulfilmentMode.PICKUP
        }
        if (selectedPickupNodeId.isEmpty() || cityPickupNodes.none { it.id == selectedPickupNodeId }) {
            selectedPickupNodeId = cityPickupNodes.firstOrNull()?.id ?: ""
        }
    }

    // F5.1 Accessory price
    val accessoryUnitPricePaisa = snapshot.currentPriceList.accessoryPaisa["SKU-REG-HOSE"] ?: 145000L
    val accessoryPriceRupees = PricingEngine.roundToRupees(accessoryUnitPricePaisa)

    // Unified pricing quote with PricingEngine
    val accessoriesList = if (addAccessory) listOf("SKU-REG-HOSE" to 1) else emptyList()
    val quote = remember(
        selectedSku,
        quantity,
        selectedMode,
        orderType,
        addAccessory,
        snapshot.currentPriceList,
        snapshot.taxConfig,
        snapshot.settings
    ) {
        PricingEngine.buildQuote(
            orderType = orderType,
            sku = selectedSku,
            quantity = quantity,
            accessories = accessoriesList,
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
                modifier = Modifier.fillMaxWidth().testTag("home_hero_card")
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

        // F5.1 Order Type Selector: 2 options only
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                            Triple("Refill (empty exchange)", OrderType.REFILL_EXCHANGE, "order_type_refill"),
                            Triple("New cylinder", OrderType.NEW_CYLINDER, "order_type_new")
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

                    Spacer(modifier = Modifier.height(8.dp))
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DemoValuePill("Demo value · Q20")
                                Text(
                                    text = if (hasEmptyCylinderToExchange)
                                        "Standard refill: I'll hand back an empty BLUE GAS cylinder at delivery."
                                    else
                                        "No empty cylinder to exchange.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        // Delivery Address Card & Serviceability
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth().testTag("address_serviceability_card")
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
                                    "This address is outside the current delivery area. Depot pickup is available.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeAddress.isServiceable) Color(0xFF166534) else Color(0xFF991B1B),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // F5.1 Section 1: Choose Cylinder Size (Cylinders only)
        item {
            Column {
                Text(
                    text = "1. Select Cylinder Size",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BlueGasDark
                )
                Text(
                    text = "Approved household cylinders",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }

        val approvedCylinders = snapshot.productVariants.filter { it.isCylinder && it.isHouseholdApproved }
        items(approvedCylinders) { pv ->
            val pubPrice = PricingEngine.unitPricePublished(pv.sku, snapshot.currentPriceList)
            val isSelected = pv.sku == selectedSku

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFFEFF6FF) else Color.White
                ),
                border = BorderStroke(
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
                                text = "Size: ${pv.cylinderSizeKg} kg",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF047857)
                            )
                        }
                    }
                }
            }
        }

        // F5.1 Optional Add-on Card: Regulator & Hose
        if (accessoryUnitPricePaisa > 0L) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (addAccessory) Color(0xFFEFF6FF) else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (addAccessory) BlueGasPrimary else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().testTag("accessory_addon_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addAccessory = !addAccessory }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = addAccessory,
                            onCheckedChange = { addAccessory = it },
                            colors = CheckboxDefaults.colors(checkedColor = BlueGasPrimary),
                            modifier = Modifier.testTag("accessory_checkbox")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Add regulator & hose (+${formatRupees(accessoryPriceRupees * 100L)})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "Low pressure regulator and reinforced LPG hose.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
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
                    val deliveryFeeLabel = formatRupees(snapshot.currentPriceList.deliveryFeePaisa)
                    FilterChip(
                        selected = selectedMode == FulfilmentMode.DELIVERY,
                        onClick = {
                            if (activeAddress.isServiceable) {
                                selectedMode = FulfilmentMode.DELIVERY
                            }
                        },
                        enabled = activeAddress.isServiceable,
                        label = { Text("Doorstep delivery (+$deliveryFeeLabel)", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.DeliveryDining, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f).testTag("mode_delivery_chip")
                    )
                    FilterChip(
                        selected = selectedMode == FulfilmentMode.PICKUP,
                        onClick = { selectedMode = FulfilmentMode.PICKUP },
                        label = { Text("Depot pickup (free)", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.weight(1f).testTag("mode_pickup_chip")
                    )
                }
            }
        }

        // F5.3 Depot Picker (when PICKUP is selected)
        if (selectedMode == FulfilmentMode.PICKUP) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().testTag("depot_picker_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Select Pickup Depot (${activeAddress.city})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = BlueGasDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (cityPickupNodes.isEmpty()) {
                            Text(
                                text = "No depot is available in your city yet.",
                                fontSize = 12.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            cityPickupNodes.forEach { depot ->
                                val isDepotSelected = depot.id == selectedPickupNodeId
                                Surface(
                                    color = if (isDepotSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isDepotSelected) BlueGasPrimary else Color(0xFFE2E8F0)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable { selectedPickupNodeId = depot.id }
                                        .testTag("depot_option_${depot.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isDepotSelected,
                                            onClick = { selectedPickupNodeId = depot.id },
                                            colors = RadioButtonDefaults.colors(selectedColor = BlueGasPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(depot.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                                            Text(depot.address, fontSize = 11.sp, color = Color(0xFF64748B))
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Opening hours: ", fontSize = 10.sp, color = Color.Gray)
                                                DemoValuePill("Demo value · Q14")
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

        // Section 3: Quantity & Payment Method
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
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

                    Text("Payment Method", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = paymentMethodChoice == PaymentMethod.ONLINE_HOSTED,
                            onClick = { paymentMethodChoice = PaymentMethod.ONLINE_HOSTED },
                            label = { Text("Online payment", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            modifier = Modifier.weight(1f).testTag("payment_online_chip")
                        )
                        FilterChip(
                            selected = paymentMethodChoice == PaymentMethod.CASH_ON_DELIVERY,
                            onClick = { paymentMethodChoice = PaymentMethod.CASH_ON_DELIVERY },
                            label = { Text("Cash on delivery", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            modifier = Modifier.weight(1f).testTag("payment_cod_chip")
                        )
                    }
                }
            }
        }

        // Section 4: Quotation Breakdown
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth().testTag("quotation_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Itemized Quotation Breakdown",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = BlueGasDark
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    quote.lines.forEach { line ->
                        if (line.lineType != "DEPOSIT") {
                            QuoteLineRow("${line.quantity}x ${line.productName}", formatRupees(line.subtotalPaisa))
                        }
                    }

                    if (selectedMode == FulfilmentMode.DELIVERY) {
                        QuoteLineRow("Fulfilment / Delivery Fee", formatRupees(quote.deliveryFeePaisa))
                    }

                    QuoteLineRow("Digital Platform Convenience Fee", formatRupees(quote.platformFeePaisa))

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

                    if (quote.discountPaisa > 0) {
                        QuoteLineRow(
                            label = "Promotional Discount",
                            value = "-${formatRupees(quote.discountPaisa)}",
                            isHighlight = true,
                            highlightColor = Color(0xFF16A34A)
                        )
                    }

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

        // Section 5: Place Order CTA
        item {
            val isPickupValid = selectedMode == FulfilmentMode.PICKUP && cityPickupNodes.isNotEmpty() && selectedPickupNodeId.isNotEmpty()
            val isCheckoutAllowed = (selectedMode == FulfilmentMode.DELIVERY && activeAddress.isServiceable) || isPickupValid

            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (placing) return@Button
                        placing = true

                        val (newOrder, err) = store.createOrder(
                            customerId = activeCustomer.id,
                            addressId = activeAddress.id,
                            fulfilmentMode = selectedMode,
                            sku = selectedSku,
                            quantity = quantity,
                            includeDeposit = false,
                            paymentMethod = paymentMethodChoice,
                            orderType = orderType,
                            pickupPointId = if (selectedMode == FulfilmentMode.PICKUP) selectedPickupNodeId else null,
                            accessories = accessoriesList
                        )
                        placing = false

                        if (err != null) {
                            onActionMessage(err, true)
                        } else if (newOrder != null) {
                            onOrderCreated(newOrder)
                            onActionMessage("Order ${newOrder.id} placed.", false)

                            if (paymentMethodChoice == PaymentMethod.ONLINE_HOSTED) {
                                createdOrderIdForPayment = newOrder.id
                                showPaymentGatewayDialog = true
                            } else {
                                // COD: confirm payment and advance order
                                val (codOk, codMsg) = store.confirmPaymentAndAdvance(newOrder.id, actor = activeCustomer.id)
                                onActionMessage(codMsg, !codOk)
                                onNavigateToOrders()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_place_order_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                    enabled = isCheckoutAllowed && !placing
                ) {
                    if (placing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Confirm & Place Order (${formatRupees(quote.totalAmountPaisa)})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                if (!isCheckoutAllowed) {
                    val notAllowedText = if (selectedMode == FulfilmentMode.PICKUP && cityPickupNodes.isEmpty()) {
                        "No depot is available in your city yet."
                    } else {
                        "Selected address is outside our delivery area. Switch to depot pickup above to proceed."
                    }
                    Text(
                        text = notAllowedText,
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

    // F1.2 Shared Demo Payment Dialog
    if (showPaymentGatewayDialog && createdOrderIdForPayment != null) {
        DemoPaymentDialog(
            orderId = createdOrderIdForPayment!!,
            store = store,
            onResult = { msg, ok ->
                onActionMessage(msg, !ok)
                showPaymentGatewayDialog = false
                onNavigateToOrders()
            },
            onDismiss = {
                showPaymentGatewayDialog = false
                onNavigateToOrders()
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
