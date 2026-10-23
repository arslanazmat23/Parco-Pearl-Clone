/**
 * Primary ordering screen for the BLUE GAS Customer App.
 *
 * Implements the customer order placement journey:
 * - Order type selection: Refill Exchange (handing back an empty cylinder) or New Cylinder (with security deposit)
 * - Approved household LPG cylinder SKU & quantity selection (up to maxQuantityPerOrder)
 * - Optional regulator and reinforced hose accessory add-on priced dynamically via active price list
 * - Fulfilment mode selection (Doorstep Delivery or Depot Pickup) with automated city-level depot resolution
 * - Delivery address selection with zone-serviceability enforcement (forces pickup if outside active zone)
 * - Payment method selection (Cash on Delivery or Online Hosted Gateway)
 * - Real-time total quote generation using [PricingEngine.buildQuote]
 *
 * Reads: [SharedAppState.activeCustomer], [SharedAppState.addresses], [SharedAppState.productVariants],
 *        [SharedAppState.nodes], [SharedAppState.currentPriceList], [SharedAppState.taxConfig], [SharedAppState.settings]
 * Calls: [BlueGasStore.createOrder], [BlueGasStore.confirmPaymentAndAdvance]
 */
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

/**
 * Composable screen enabling customers to configure and place LPG orders.
 *
 * Preserves user selections across navigation tab switches via [rememberSaveable].
 *
 * @param store BlueGasStore application state container.
 * @param onOrderCreated Callback invoked when an order is successfully created in store.
 * @param onNavigateToOrders Callback to switch active tab to Orders view.
 * @param onTrackOrder Callback to navigate to order tracking for a specific order ID.
 * @param onActionMessage Global banner callback receiving (message: String, isError: Boolean).
 * @param prefill Optional reorder prefill object containing past order details.
 * @param onPrefillConsumed Invoked once the prefill values have been applied.
 * @param modifier Optional layout modifier.
 */
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

    // Customer selection state preserved across tab transitions
    var selectedAddressId by rememberSaveable {
        mutableStateOf(
            customerAddresses.firstOrNull { it.isDefault }?.id
                ?: customerAddresses.firstOrNull()?.id
                ?: ""
        )
    }
    var selectedSku by rememberSaveable { mutableStateOf("SKU-LPG-11-8") }
    var orderType by rememberSaveable { mutableStateOf(OrderType.REFILL_EXCHANGE) }
    var selectedMode by rememberSaveable { mutableStateOf(FulfilmentMode.DELIVERY) }
    var hasEmptyCylinderToExchange by rememberSaveable { mutableStateOf(true) }
    var quantity by rememberSaveable { mutableIntStateOf(1) }
    var paymentMethodChoice by rememberSaveable { mutableStateOf(PaymentMethod.ONLINE_HOSTED) }
    var addAccessory by rememberSaveable { mutableStateOf(false) }
    var selectedPickupNodeId by rememberSaveable { mutableStateOf("") }

    // Dialog state for payment execution
    var createdOrderIdForPayment by remember { mutableStateOf<String?>(null) }
    var showPaymentGatewayDialog by remember { mutableStateOf(false) }

    // Track active customer to ensure form resets only on actual customer switches
    var lastCustomerId by rememberSaveable { mutableStateOf(activeCustomer.id) }
    LaunchedEffect(activeCustomer.id) {
        if (lastCustomerId != activeCustomer.id) {
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
            lastCustomerId = activeCustomer.id
        }
    }

    // Apply reorder prefill when triggered from OrdersScreen
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

        // Preserve previous depot selection if valid for the address's city
        val targetAddress = snapshot.addresses.find { it.id == selectedAddressId }
        val depotsInCity = snapshot.nodes.filter {
            it.pickupEnabled && it.type != FulfilmentPointType.PLANT && it.city.equals(targetAddress?.city ?: "", ignoreCase = true)
        }
        if (p.mode == FulfilmentMode.PICKUP && p.pickupPointId != null && depotsInCity.any { it.id == p.pickupPointId }) {
            selectedPickupNodeId = p.pickupPointId
        }
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

    // Depot pickup nodes restricted to the chosen delivery address's city
    val cityPickupNodes = snapshot.nodes.filter {
        it.pickupEnabled && it.type != FulfilmentPointType.PLANT && it.city.equals(activeAddress.city, ignoreCase = true)
    }

    // Enforce depot pickup whenever the address falls outside the serviceable delivery area
    LaunchedEffect(activeAddress.id, activeAddress.isServiceable, cityPickupNodes.size) {
        if (!activeAddress.isServiceable) {
            selectedMode = FulfilmentMode.PICKUP
        }
        if (selectedPickupNodeId.isEmpty() || cityPickupNodes.none { it.id == selectedPickupNodeId }) {
            selectedPickupNodeId = cityPickupNodes.firstOrNull()?.id ?: ""
        }
    }

    // Accessory pricing calculation adhering to price list rounding rules
    val rawAccessoryPaisa = snapshot.currentPriceList.accessoryPaisa["SKU-REG-HOSE"]
    val accessoryPricePaisa = rawAccessoryPaisa?.let {
        PricingEngine.roundToRupees(it, snapshot.currentPriceList.roundingRule)
    }

    // Compute preview price quote with exact item breakdown
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

            Card(
                colors = CardDefaults.cardColors(containerColor = BlueGasDark),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("home_greeting_card")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$greeting, ${activeCustomer.name.split(" ").firstOrNull() ?: activeCustomer.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Licensed LPG doorstep supply in Lahore pilot zone.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BlueGasPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = BlueGasPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Active Order Quick-Track Card
        val inFlightOrders = snapshot.orders.filter {
            it.customerId == activeCustomer.id &&
                (it.status == OrderStatus.ORD_DISP || it.status == OrderStatus.ORD_ATHO || it.status == OrderStatus.ORD_PREP)
        }
        if (inFlightOrders.isNotEmpty()) {
            item {
                val liveOrder = inFlightOrders.first()
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                    modifier = Modifier.fillMaxWidth().testTag("active_order_tracking_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Order ${liveOrder.id} is ${liveOrder.status.customerLabel(liveOrder.fulfilmentMode)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF14532D)
                                )
                                Text(
                                    text = "Tap to track live rider & handover PIN",
                                    fontSize = 10.sp,
                                    color = Color(0xFF166534)
                                )
                            }
                        }
                        Button(
                            onClick = { onTrackOrder(liveOrder.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp).testTag("track_now_home_button")
                        ) {
                            Text("Track", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section 1: Order Requirement
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth().testTag("order_type_selector_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "1. What do you need?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isRefill = orderType == OrderType.REFILL_EXCHANGE
                        FilterChip(
                            selected = isRefill,
                            onClick = { orderType = OrderType.REFILL_EXCHANGE },
                            label = { Text("Refill (empty exchange)", fontSize = 11.sp) },
                            leadingIcon = if (isRefill) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFDBEAFE),
                                selectedLabelColor = BlueGasPrimary
                            ),
                            modifier = Modifier.weight(1f).testTag("order_type_refill_chip")
                        )

                        val isNew = orderType == OrderType.NEW_CYLINDER
                        FilterChip(
                            selected = isNew,
                            onClick = { orderType = OrderType.NEW_CYLINDER },
                            label = { Text("New cylinder", fontSize = 11.sp) },
                            leadingIcon = if (isNew) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFDBEAFE),
                                selectedLabelColor = BlueGasPrimary
                            ),
                            modifier = Modifier.weight(1f).testTag("order_type_new_chip")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    when (orderType) {
                        OrderType.NEW_CYLINDER -> {
                            val modelNote = when (snapshot.settings.newCylinderModel) {
                                NewCylinderModel.SOLD -> "You will own the cylinder"
                                NewCylinderModel.DEPOSIT -> "Held against a security deposit — you don't own it"
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
                                    text = "Hand over an empty BLUE GAS cylinder at delivery",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        // Section 1B: Cylinder Size Selection (Filtered to household cylinders)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth().testTag("cylinder_size_selector_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Select Cylinder Size",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val cylinderVariants = snapshot.productVariants.filter { it.isCylinder && it.isHouseholdApproved }

                    cylinderVariants.forEach { pv ->
                        val isSelected = selectedSku == pv.sku
                        val basePricePaisa = PricingEngine.unitPricePublished(pv.sku, snapshot.currentPriceList)
                        val depositPaisa = if (orderType == OrderType.NEW_CYLINDER) {
                            PricingEngine.roundToRupees(snapshot.currentPriceList.depositPaisa[pv.sku] ?: 0L, snapshot.currentPriceList.roundingRule)
                        } else 0L
                        val lineTotal = basePricePaisa + depositPaisa

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, if (isSelected) BlueGasPrimary else Color(0xFFE2E8F0)),
                            color = if (isSelected) Color(0xFFF0F9FF) else Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedSku = pv.sku }
                                .testTag("sku_option_${pv.sku}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedSku = pv.sku },
                                        colors = RadioButtonDefaults.colors(selectedColor = BlueGasPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = pv.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = pv.description,
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                                Text(
                                    text = formatRupees(lineTotal),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = BlueGasPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    // Quantity Control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quantity (Cylinders)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(
                                onClick = { if (quantity > 1) quantity-- },
                                enabled = quantity > 1,
                                modifier = Modifier.size(34.dp).testTag("decrease_quantity_button")
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = quantity.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 14.dp).testTag("quantity_text")
                            )
                            FilledTonalIconButton(
                                onClick = { if (quantity < snapshot.settings.maxQuantityPerOrder) quantity++ },
                                enabled = quantity < snapshot.settings.maxQuantityPerOrder,
                                modifier = Modifier.size(34.dp).testTag("increase_quantity_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Optional Accessory Add-on (regulator & hose)
        if (accessoryPricePaisa != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (addAccessory) Color(0xFFEFF6FF) else Color.White),
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
                                text = "Add regulator & hose (+${formatRupees(accessoryPricePaisa)})",
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
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isDelivery = selectedMode == FulfilmentMode.DELIVERY
                    FilterChip(
                        selected = isDelivery,
                        onClick = {
                            if (activeAddress.isServiceable) {
                                selectedMode = FulfilmentMode.DELIVERY
                            }
                        },
                        enabled = activeAddress.isServiceable,
                        label = { Text("Doorstep Delivery", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFDBEAFE),
                            selectedLabelColor = BlueGasPrimary
                        ),
                        modifier = Modifier.weight(1f).testTag("mode_delivery_chip")
                    )

                    val isPickup = selectedMode == FulfilmentMode.PICKUP
                    FilterChip(
                        selected = isPickup,
                        onClick = { selectedMode = FulfilmentMode.PICKUP },
                        label = { Text("Depot Pickup", fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFDBEAFE),
                            selectedLabelColor = BlueGasPrimary
                        ),
                        modifier = Modifier.weight(1f).testTag("mode_pickup_chip")
                    )
                }

                if (!activeAddress.isServiceable) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "This address is outside the current delivery area. Depot pickup is available.",
                                fontSize = 10.sp,
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                }
            }
        }

        // Section 2B: Delivery Address / Depot Selection
        item {
            if (selectedMode == FulfilmentMode.DELIVERY) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().testTag("home_delivery_address_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Delivery Address",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        customerAddresses.forEach { addr ->
                            val isChosen = addr.id == selectedAddressId
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isChosen) BlueGasPrimary else Color(0xFFE2E8F0)),
                                color = if (isChosen) Color(0xFFF0F9FF) else Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable { selectedAddressId = addr.id }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isChosen,
                                        onClick = { selectedAddressId = addr.id },
                                        colors = RadioButtonDefaults.colors(selectedColor = BlueGasPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(addr.label, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("${addr.street}, ${addr.area}, ${addr.city}", fontSize = 10.sp, color = Color(0xFF64748B))
                                    }
                                    if (addr.isServiceable) {
                                        Surface(color = Color(0xFFDCFCE7), shape = RoundedCornerShape(4.dp)) {
                                            Text("Active", fontSize = 9.sp, color = Color(0xFF166534), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Depot Pickup choice card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().testTag("home_pickup_depot_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Select Pickup Depot (${activeAddress.city})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            DemoValuePill("Demo value · Q14")
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        if (cityPickupNodes.isEmpty()) {
                            Text(
                                text = "No depot is available in your city yet.",
                                fontSize = 11.sp,
                                color = Color(0xFFDC2626)
                            )
                        } else {
                            cityPickupNodes.forEach { node ->
                                val isChosen = node.id == selectedPickupNodeId
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isChosen) BlueGasPrimary else Color(0xFFE2E8F0)),
                                    color = if (isChosen) Color(0xFFF0F9FF) else Color.White,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable { selectedPickupNodeId = node.id }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isChosen,
                                            onClick = { selectedPickupNodeId = node.id },
                                            colors = RadioButtonDefaults.colors(selectedColor = BlueGasPrimary)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(node.name, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Text(node.address, fontSize = 10.sp, color = Color(0xFF64748B))
                                            Text("Opening hours: Demo value · Q14", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Payment Method
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth().testTag("payment_method_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "3. Payment Method",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isOnline = paymentMethodChoice == PaymentMethod.ONLINE_HOSTED
                        FilterChip(
                            selected = isOnline,
                            onClick = { paymentMethodChoice = PaymentMethod.ONLINE_HOSTED },
                            label = { Text("Online Payment", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFDBEAFE),
                                selectedLabelColor = BlueGasPrimary
                            ),
                            modifier = Modifier.weight(1f).testTag("payment_online_chip")
                        )

                        val isCod = paymentMethodChoice == PaymentMethod.CASH_ON_DELIVERY
                        FilterChip(
                            selected = isCod,
                            onClick = { paymentMethodChoice = PaymentMethod.CASH_ON_DELIVERY },
                            label = { Text("Cash on Delivery", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Money, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFDBEAFE),
                                selectedLabelColor = BlueGasPrimary
                            ),
                            modifier = Modifier.weight(1f).testTag("payment_cod_chip")
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (paymentMethodChoice == PaymentMethod.ONLINE_HOSTED) {
                            "Hold expires in 15 mins if unpaid. Simulated pilot gateway."
                        } else {
                            "Pay in cash directly to rider at doorstep upon delivery."
                        },
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }

        // Section 4: Price Summary & Breakdown
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth().testTag("order_price_summary_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Price Summary",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    QuoteLineRow(
                        label = "LPG Refill Gas (${quantity}x)",
                        value = formatRupees(quote.subtotalPaisa)
                    )

                    if (addAccessory && accessoryPricePaisa != null) {
                        QuoteLineRow(
                            label = "Regulator & Hose Add-on",
                            value = formatRupees(accessoryPricePaisa)
                        )
                    }

                    if (selectedMode == FulfilmentMode.DELIVERY) {
                        QuoteLineRow(
                            label = "Doorstep Delivery Fee",
                            value = formatRupees(quote.deliveryFeePaisa)
                        )
                    }

                    if (quote.platformFeePaisa > 0) {
                        QuoteLineRow(
                            label = "Platform Convenience Fee",
                            value = formatRupees(quote.platformFeePaisa)
                        )
                    }

                    if (quote.taxPaisa > 0) {
                        QuoteLineRow(
                            label = "Sales Tax (GST)",
                            value = formatRupees(quote.taxPaisa)
                        )
                    }

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
                                text = "Security deposit — refund terms follow the BLUE GAS deposit policy (Q03). A deposit does not make you the owner.",
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Payable",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = formatRupees(quote.totalAmountPaisa),
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            color = BlueGasPrimary
                        )
                    }
                }
            }
        }

        // Section 5: Place Order Button with double-tap protection
        item {
            val isPickupValid = selectedMode == FulfilmentMode.PICKUP && cityPickupNodes.isNotEmpty() && selectedPickupNodeId.isNotEmpty()
            val isCheckoutAllowed = (selectedMode == FulfilmentMode.DELIVERY && activeAddress.isServiceable) || isPickupValid

            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
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

                        if (err != null) {
                            onActionMessage(err, true)
                        } else if (newOrder != null) {
                            onOrderCreated(newOrder)
                            onActionMessage("Order ${newOrder.id} placed.", false)

                            if (paymentMethodChoice == PaymentMethod.ONLINE_HOSTED) {
                                createdOrderIdForPayment = newOrder.id
                                showPaymentGatewayDialog = true
                            } else {
                                // COD advances directly to Confirmed status
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
                    enabled = isCheckoutAllowed && !showPaymentGatewayDialog
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
                    val notAllowedText = if (selectedMode == FulfilmentMode.PICKUP && cityPickupNodes.isEmpty()) {
                        "No depot is available in your city yet."
                    } else {
                        "Selected address is outside our delivery area. Switch to depot pickup above to proceed."
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = notAllowedText,
                        fontSize = 10.sp,
                        color = Color(0xFFDC2626)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Shared Demo Payment Dialog
    if (showPaymentGatewayDialog && createdOrderIdForPayment != null) {
        DemoPaymentDialog(
            orderId = createdOrderIdForPayment!!,
            payableLabel = formatRupees(quote.totalAmountPaisa),
            store = store,
            onResult = { msg, isError ->
                onActionMessage(msg, isError)
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

/**
 * Clean key-value row for the order pricing breakdown card.
 */
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
