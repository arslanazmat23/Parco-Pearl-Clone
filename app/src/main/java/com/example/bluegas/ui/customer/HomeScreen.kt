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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.data.DeliveryWindowOption
import com.example.bluegas.data.PricingEngine
import com.example.bluegas.data.availableDeliveryWindows
import com.example.bluegas.data.captureCnic
import com.example.bluegas.data.confirmPaymentAndAdvance
import com.example.bluegas.data.createOrder
import com.example.bluegas.data.dismissRefillReminder
import com.example.bluegas.data.lastReorderableOrder
import com.example.bluegas.data.needsCnicForDeposit
import com.example.bluegas.data.refillReminderDue
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var selectedWindowLabel by rememberSaveable { mutableStateOf("") }
    var selectedServiceLevel by rememberSaveable { mutableStateOf(ServiceLevel.STANDARD) }

    // Dialog state for payment execution, CNIC deposit capture, deposit info sheet, and quick reorder sheet
    var createdOrderIdForPayment by remember { mutableStateOf<String?>(null) }
    var showPaymentGatewayDialog by remember { mutableStateOf(false) }
    var showDepositInfoSheet by remember { mutableStateOf(false) }
    var showQuickReorderSheet by remember { mutableStateOf(false) }
    var showCnicCaptureDialog by remember { mutableStateOf(false) }
    var cnicDigitsInput by remember { mutableStateOf("") }
    var cnicConsentChecked by remember { mutableStateOf(false) }
    var cnicModalError by remember { mutableStateOf<String?>(null) }

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
            selectedWindowLabel = ""
            selectedServiceLevel = ServiceLevel.STANDARD
            showQuickReorderSheet = false
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
        selectedServiceLevel = if (p.mode == FulfilmentMode.DELIVERY && snapshot.settings.expressEnabled) {
            p.serviceLevel
        } else {
            ServiceLevel.STANDARD
        }

        // Preserve previous depot selection if valid for the address's city
        val targetAddress = snapshot.addresses.find { it.id == selectedAddressId }
        val depotsInCity = snapshot.nodes.filter {
            it.pickupEnabled && it.type != FulfilmentPointType.PLANT && it.city.equals(targetAddress?.city ?: "", ignoreCase = true)
        }
        if (p.mode == FulfilmentMode.PICKUP && p.pickupPointId != null && depotsInCity.any { it.id == p.pickupPointId }) {
            selectedPickupNodeId = p.pickupPointId
        }
        // Reorder prefill handling: consumes order parameters passed from the Orders screen and populates
        // SKU, quantity, accessories, and pickup depot choices for rapid one-tap repurchases.
        onPrefillConsumed()
    }

    val activeAddress = customerAddresses.find { it.id == selectedAddressId }
        ?: customerAddresses.firstOrNull()
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

    // Depot pickup nodes restricted to the chosen delivery address's city.
    // Automated depot resolution: queries nodes for pickup-enabled facilities located within the city of the active address.
    // When an address is outside the serviceable delivery area, this ensures customers have valid local pickup options.
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

    // Delivery time windows (filtered for current Karachi time) & service level normalization
    val deliveryWindowOptions = remember(snapshot.version, snapshot.settings) {
        store.availableDeliveryWindows()
    }
    LaunchedEffect(deliveryWindowOptions) {
        if (deliveryWindowOptions.isNotEmpty() && deliveryWindowOptions.none { it.label == selectedWindowLabel }) {
            selectedWindowLabel = deliveryWindowOptions.first().label
        } else if (deliveryWindowOptions.isEmpty()) {
            selectedWindowLabel = ""
        }
    }
    val selectedWindowOption: DeliveryWindowOption? = deliveryWindowOptions.find { it.label == selectedWindowLabel }
        ?: deliveryWindowOptions.firstOrNull()

    LaunchedEffect(selectedMode, snapshot.settings.expressEnabled) {
        if (selectedMode == FulfilmentMode.PICKUP || !snapshot.settings.expressEnabled) {
            selectedServiceLevel = ServiceLevel.STANDARD
        }
    }
    val effectiveServiceLevel = if (selectedMode == FulfilmentMode.DELIVERY && snapshot.settings.expressEnabled) {
        selectedServiceLevel
    } else {
        ServiceLevel.STANDARD
    }

    // Helper for Home, CNIC dialog, and One-Tap Reorder to execute order placement without duplicating payment handling
    fun submitOrderRequest(
        reqAddressId: String,
        reqMode: FulfilmentMode,
        reqSku: String,
        reqQty: Int,
        reqPaymentMethod: PaymentMethod,
        reqOrderType: OrderType,
        reqPickupPointId: String?,
        reqAccessories: List<Pair<String, Int>>,
        reqWindow: DeliveryWindowOption?,
        reqServiceLevel: ServiceLevel
    ): Boolean {
        val (newOrder, err) = store.createOrder(
            customerId = activeCustomer.id,
            addressId = reqAddressId,
            fulfilmentMode = reqMode,
            sku = reqSku,
            quantity = reqQty,
            includeDeposit = false,
            paymentMethod = reqPaymentMethod,
            orderType = reqOrderType,
            pickupPointId = if (reqMode == FulfilmentMode.PICKUP) reqPickupPointId else null,
            accessories = reqAccessories,
            deliveryWindowLabel = if (reqMode == FulfilmentMode.DELIVERY) reqWindow?.label else null,
            windowStartAt = if (reqMode == FulfilmentMode.DELIVERY) reqWindow?.startAt else null,
            windowEndAt = if (reqMode == FulfilmentMode.DELIVERY) reqWindow?.endAt else null,
            serviceLevel = reqServiceLevel
        )
        if (err != null) {
            onActionMessage(err, true)
            return false
        }
        if (newOrder != null) {
            onOrderCreated(newOrder)
            onActionMessage("Order ${newOrder.id} placed.", false)
            if (reqPaymentMethod == PaymentMethod.ONLINE_HOSTED) {
                createdOrderIdForPayment = newOrder.id
                showPaymentGatewayDialog = true
            } else {
                val (codOk, codMsg) = store.confirmPaymentAndAdvance(newOrder.id, actor = activeCustomer.id)
                onActionMessage(codMsg, !codOk)
                onNavigateToOrders()
            }
            return true
        }
        return false
    }

    // Accessory pricing calculation adhering to price list rounding rules
    val rawAccessoryPaisa = snapshot.currentPriceList.accessoryPaisa["SKU-REG-HOSE"]
    val accessoryPricePaisa = rawAccessoryPaisa?.let {
        PricingEngine.roundToRupees(it, snapshot.currentPriceList.roundingRule)
    }

    // Refill reminder & one-tap reorder candidates
    val refillReminder = remember(snapshot.version, activeCustomer.id) {
        store.refillReminderDue(activeCustomer.id)
    }
    val lastReorderOrder = remember(snapshot.version, activeCustomer.id) {
        store.lastReorderableOrder(activeCustomer.id)
    }

    // Pure deterministic pricing quote calculation adhering to company pricing tiers and rounding rules.
    val accessoriesList = if (addAccessory) listOf("SKU-REG-HOSE" to 1) else emptyList()
    val quote = remember(
        selectedSku,
        quantity,
        selectedMode,
        orderType,
        addAccessory,
        effectiveServiceLevel,
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
            discountPaisa = 0L,
            serviceLevel = effectiveServiceLevel
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
                            text = "Order LPG for doorstep delivery or hub pickup.",
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

        // Household Refill Reminder Card (G-20)
        if (refillReminder != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                    modifier = Modifier.fillMaxWidth().testTag("home_refill_reminder_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RecommendedPill()
                        Text(
                            text = "Your ${refillReminder.sizeLabel} cylinder may be running low. Reorder?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF78350F)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    selectedSku = refillReminder.sku
                                    orderType = OrderType.REFILL_EXCHANGE
                                    if (lastReorderOrder != null) {
                                        showQuickReorderSheet = true
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("refill_reminder_reorder_btn")
                            ) {
                                Text("Reorder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { store.dismissRefillReminder(activeCustomer.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("refill_reminder_dismiss_btn")
                            ) {
                                Text("Not now", fontSize = 12.sp, color = Color(0xFF78350F))
                            }
                        }
                    }
                }
            }
        }

        // One-Tap Reorder Card (G-19)
        if (lastReorderOrder != null) {
            item {
                val gasLine = lastReorderOrder.items.firstOrNull { it.lineType == "GAS" }
                val variant = snapshot.productVariants.find { it.sku == gasLine?.sku }
                val sizeLabel = variant?.cylinderSizeKg?.let { "$it kg" } ?: (variant?.name ?: "LPG")
                val addrLabel = if (lastReorderOrder.fulfilmentMode == FulfilmentMode.PICKUP) {
                    "Hub pickup"
                } else {
                    snapshot.addresses.find { it.id == lastReorderOrder.addressId }?.label ?: "Saved address"
                }
                val payLabel = when (lastReorderOrder.paymentMethod) {
                    PaymentMethod.ONLINE_HOSTED -> "Online payment"
                    PaymentMethod.CASH_ON_DELIVERY -> "Cash on delivery"
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showQuickReorderSheet = true }
                        .testTag("home_one_tap_reorder_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RecommendedPill()
                            Text(
                                text = "One-tap reorder",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueGasPrimary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Reorder: $sizeLabel refill · $addrLabel · $payLabel",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = BlueGasDark
                                )
                                Text(
                                    text = "Tap to review fresh live price & delivery time",
                                    fontSize = 10.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                            Button(
                                onClick = { showQuickReorderSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("home_one_tap_reorder_button")
                            ) {
                                Text("Reorder", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Active Order Quick-Track Card
        // Surfaces ongoing deliveries on the home landing page to prevent duplicate order placements.
        // Provides immediate 1-tap navigation directly to live route tracking and rider status.
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
        // Distinguishes between standard empty cylinder refill exchange and new cylinder acquisition.
        // Refill exchange requires handing over an approved empty cylinder at doorstep or depot.
        // New cylinder acquisition adds a security deposit liability line item without transferring asset ownership.
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
                            val modelNote = "Held against a refundable security deposit — you don't own it."
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
        // Presents residential-approved LPG cylinder sizes with real-time base gas price and deposit liability.
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

                    // Household SKU gating: customers can only select cylinders approved for residential use.
                    // Commercial cylinders (e.g. 45.4 kg bulk) require commercial trade accounts and are hidden from retail.
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedSku = pv.sku },
                                        colors = RadioButtonDefaults.colors(selectedColor = BlueGasPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
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
                                        if (orderType == OrderType.NEW_CYLINDER && depositPaisa > 0L) {
                                            Text(
                                                text = "Gas ${formatRupees(basePricePaisa)} + Refundable deposit ${formatRupees(depositPaisa)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFF92400E)
                                            )
                                        }
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

        // Accessory add-on: low-pressure safety regulator and reinforced LPG hose bundle.
        // Rendered only when active price list specifies valid pricing for SKU-REG-HOSE.
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
        // Doorstep Delivery requires the active address to reside within an active delivery zone.
        // Addresses outside delivery areas automatically force depot pickup to ensure reliable fulfillment.
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
                        label = { Text("Hub Pickup", fontSize = 11.sp) },
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
                                text = "This address is outside the current delivery area. Hub pickup is available.",
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
                // Depot pickup choice: restricts selectable depot locations to those situated within the
                // same municipality/city as the selected delivery address to prevent cross-city fulfillment errors.
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().testTag("home_pickup_depot_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Select Pickup Hub (${activeAddress.city})",
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
                                text = "No BLUE GAS hub is available in your city yet.",
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

        // Section 2C: Delivery Time Windows & Service Speed (Delivery Mode Only; Pickup ignores windows & Express)
        if (selectedMode == FulfilmentMode.DELIVERY) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().testTag("delivery_window_selector_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "When should we deliver?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                        RecommendedPill()

                        if (deliveryWindowOptions.isEmpty()) {
                            Text(
                                text = "No delivery time windows are available right now. Switch to Hub pickup above to order.",
                                fontSize = 11.sp,
                                color = Color(0xFFDC2626)
                            )
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                deliveryWindowOptions.forEachIndexed { idx, opt ->
                                    val isChosen = selectedWindowOption?.label == opt.label
                                    FilterChip(
                                        selected = isChosen,
                                        onClick = { selectedWindowLabel = opt.label },
                                        label = { Text(opt.label, fontSize = 11.sp) },
                                        leadingIcon = if (isChosen) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFDBEAFE),
                                            selectedLabelColor = BlueGasPrimary
                                        ),
                                        modifier = Modifier.testTag("delivery_window_chip_$idx")
                                    )
                                }
                            }
                        }

                        if (snapshot.settings.expressEnabled) {
                            HorizontalDivider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 2.dp))
                            Text(
                                text = "Delivery speed",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF0F172A)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                RecommendedPill()
                                DemoValuePill("Demo value · delivery promise")
                            }

                            val expressRoundedPaisa = PricingEngine.roundToRupees(
                                snapshot.settings.expressFeePaisa,
                                snapshot.currentPriceList.roundingRule
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isStandard = effectiveServiceLevel == ServiceLevel.STANDARD
                                FilterChip(
                                    selected = isStandard,
                                    onClick = { selectedServiceLevel = ServiceLevel.STANDARD },
                                    label = { Text(ServiceLevel.STANDARD.label, fontSize = 11.sp) },
                                    leadingIcon = if (isStandard) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFDBEAFE),
                                        selectedLabelColor = BlueGasPrimary
                                    ),
                                    modifier = Modifier.weight(1f).testTag("service_level_standard_chip")
                                )

                                val isExpress = effectiveServiceLevel == ServiceLevel.EXPRESS
                                FilterChip(
                                    selected = isExpress,
                                    onClick = { selectedServiceLevel = ServiceLevel.EXPRESS },
                                    label = {
                                        Text(
                                            text = "${ServiceLevel.EXPRESS.label} · ${snapshot.settings.expressHours}h (+${formatRupees(expressRoundedPaisa)})",
                                            fontSize = 11.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFFFEDD5),
                                        selectedLabelColor = Color(0xFFC2410C)
                                    ),
                                    modifier = Modifier.weight(1.3f).testTag("service_level_express_chip")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Payment Method
        // Cash on Delivery confirms the order immediately with payment collected at doorstep handover.
        // Online Hosted Gateway places order in ORD_PAYPEND with a 15-minute stock reservation hold window.
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
                            "Hold expires in ${snapshot.settings.holdTtlMinutes} mins if unpaid. Simulated pilot gateway."
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
        // Aggregates published unit prices, optional accessories, delivery logistics fees,
        // statutory sales tax (GST), and security deposits following strict arithmetic footing rules.
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

                    // Footing rule: item rows + fees + tax + deposit − discount = total
                    quote.lines.filter { it.lineType != "DEPOSIT" }.forEach { line ->
                        QuoteLineRow(
                            label = "${line.quantity}x ${line.productName}",
                            value = formatRupees(line.subtotalPaisa)
                        )
                    }

                    if (selectedMode == FulfilmentMode.DELIVERY) {
                        val summaryWindowText = if (effectiveServiceLevel == ServiceLevel.EXPRESS) {
                            "${ServiceLevel.EXPRESS.label} · within ${snapshot.settings.expressHours} hours"
                        } else {
                            selectedWindowOption?.label ?: "As soon as possible"
                        }
                        QuoteLineRow(
                            label = "Delivery time",
                            value = summaryWindowText
                        )
                        QuoteLineRow(
                            label = "Doorstep Delivery Fee",
                            value = formatRupees(quote.deliveryFeePaisa)
                        )
                    }

                    if (quote.expressFeePaisa > 0) {
                        QuoteLineRow(
                            label = "Express delivery fee",
                            value = formatRupees(quote.expressFeePaisa)
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
                            label = "Refundable security deposit",
                            value = formatRupees(quote.depositTotalPaisa),
                            isHighlight = true,
                            highlightColor = Color(0xFFB45309)
                        )
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text(
                                    text = "Security deposit — refund terms follow the BLUE GAS deposit policy (Q03). A deposit does not make you the owner.",
                                    fontSize = 10.sp,
                                    color = Color(0xFF92400E),
                                    lineHeight = 14.sp
                                )
                                TextButton(
                                    onClick = { showDepositInfoSheet = true },
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .heightIn(min = 32.dp)
                                        .testTag("how_deposit_refunds_work_button")
                                ) {
                                    Text(
                                        text = "How deposit refunds work",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BlueGasPrimary
                                    )
                                }
                            }
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
        // Validates checkout eligibility based on delivery zone serviceability, open delivery window, or hub selection.
        // Disables the place order action while payment gateway modal is active to prevent duplicate orders.
        item {
            val isPickupValid = selectedMode == FulfilmentMode.PICKUP && cityPickupNodes.isNotEmpty() && selectedPickupNodeId.isNotEmpty()
            val hasDeliveryWindow = deliveryWindowOptions.isNotEmpty() && selectedWindowOption != null
            val isCheckoutAllowed = (selectedMode == FulfilmentMode.DELIVERY && activeAddress.isServiceable && hasDeliveryWindow) || isPickupValid

            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (store.needsCnicForDeposit(activeCustomer.id, orderType)) {
                            cnicDigitsInput = ""
                            cnicConsentChecked = false
                            cnicModalError = null
                            showCnicCaptureDialog = true
                        } else {
                            submitOrderRequest(
                                reqAddressId = activeAddress.id,
                                reqMode = selectedMode,
                                reqSku = selectedSku,
                                reqQty = quantity,
                                reqPaymentMethod = paymentMethodChoice,
                                reqOrderType = orderType,
                                reqPickupPointId = selectedPickupNodeId,
                                reqAccessories = accessoriesList,
                                reqWindow = selectedWindowOption,
                                reqServiceLevel = effectiveServiceLevel
                            )
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
                    val notAllowedText = when {
                        selectedMode == FulfilmentMode.PICKUP && cityPickupNodes.isEmpty() ->
                            "No BLUE GAS hub is available in your city yet."
                        selectedMode == FulfilmentMode.DELIVERY && !activeAddress.isServiceable ->
                            "Selected address is outside our delivery area. Switch to hub pickup above to proceed."
                        else ->
                            "No delivery time windows are open right now. Switch to hub pickup above to proceed."
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

    // Refundable deposit explanation bottom sheet
    if (showDepositInfoSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDepositInfoSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("deposit_info_bottom_sheet"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "How deposit refunds work",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                    DemoValuePill("Demo value · Q03")
                }
                Text(
                    text = "1. Cylinder custody: Your security deposit lets you hold a BLUE GAS cylinder. The cylinder itself remains BLUE GAS property.",
                    fontSize = 12.sp,
                    color = Color(0xFF334155),
                    lineHeight = 17.sp
                )
                Text(
                    text = "2. Refill exchanges: When ordering a refill, you hand over your empty cylinder in exchange for a filled one — your deposit automatically carries over.",
                    fontSize = 12.sp,
                    color = Color(0xFF334155),
                    lineHeight = 17.sp
                )
                Text(
                    text = "3. Returning a cylinder: Open Profile -> My cylinders and tap Return my cylinder. After our rider collects it and our hub checks its condition, your refundable deposit is refunded to you.",
                    fontSize = 12.sp,
                    color = Color(0xFF334155),
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { showDepositInfoSheet = false },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Got it")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // CNIC capture modal for first-time deposit orders (Q18)
    if (showCnicCaptureDialog) {
        val formattedPreview = formatCnicDigits(cnicDigitsInput)
        val isCnicValid = cnicDigitsInput.length == 13
        AlertDialog(
            onDismissRequest = { showCnicCaptureDialog = false },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "CNIC required for cylinder deposit",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    DemoValuePill("Demo value · Q18")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Please provide your 13-digit CNIC before placing your first deposit order.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 16.sp
                    )
                    OutlinedTextField(
                        value = cnicDigitsInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(13)
                            cnicDigitsInput = digits
                            cnicModalError = null
                        },
                        label = { Text("CNIC (12345-1234567-1)") },
                        placeholder = { Text("35201-1234567-1") },
                        supportingText = {
                            Text(
                                text = if (cnicDigitsInput.isNotEmpty()) {
                                    "CNIC: $formattedPreview (${cnicDigitsInput.length}/13 digits)"
                                } else {
                                    "Enter 13 digits — dashes are added automatically"
                                },
                                fontSize = 10.sp
                            )
                        },
                        singleLine = true,
                        visualTransformation = CnicVisualTransformation,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cnic_capture_input")
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { cnicConsentChecked = !cnicConsentChecked }
                    ) {
                        Checkbox(
                            checked = cnicConsentChecked,
                            onCheckedChange = { cnicConsentChecked = it },
                            modifier = Modifier.testTag("cnic_capture_consent_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "I agree BLUE GAS may store my CNIC for this refundable cylinder deposit",
                            fontSize = 11.sp,
                            color = Color(0xFF1E293B),
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!cnicModalError.isNullOrBlank()) {
                        Text(
                            text = cnicModalError ?: "",
                            fontSize = 11.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.captureCnic(activeCustomer.id, formattedPreview)
                        if (!ok) {
                            cnicModalError = msg
                        } else {
                            showCnicCaptureDialog = false
                            submitOrderRequest(
                                reqAddressId = activeAddress.id,
                                reqMode = selectedMode,
                                reqSku = selectedSku,
                                reqQty = quantity,
                                reqPaymentMethod = paymentMethodChoice,
                                reqOrderType = orderType,
                                reqPickupPointId = selectedPickupNodeId,
                                reqAccessories = accessoriesList,
                                reqWindow = selectedWindowOption,
                                reqServiceLevel = effectiveServiceLevel
                            )
                        }
                    },
                    enabled = isCnicValid && cnicConsentChecked,
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                    modifier = Modifier.testTag("cnic_capture_confirm_button")
                ) {
                    Text("Save & Place Order")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCnicCaptureDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // One-Tap Reorder Bottom Sheet (G-19)
    if (showQuickReorderSheet && lastReorderOrder != null) {
        val prevOrder = lastReorderOrder
        val gasLine = prevOrder.items.firstOrNull { it.lineType == "GAS" }
        val reorderSku = gasLine?.sku?.takeIf { sku ->
            snapshot.productVariants.any { it.sku == sku && it.isCylinder && it.isHouseholdApproved }
        } ?: "SKU-LPG-11-8"
        val reorderQty = (gasLine?.quantity ?: 1).coerceIn(1, snapshot.settings.maxQuantityPerOrder)
        val reorderVariant = snapshot.productVariants.find { it.sku == reorderSku }
        val reorderSizeLabel = reorderVariant?.cylinderSizeKg?.let { "$it kg" } ?: (reorderVariant?.name ?: "LPG")

        val savedAddress = customerAddresses.find { it.id == prevOrder.addressId }
            ?: customerAddresses.firstOrNull { it.isDefault }
            ?: customerAddresses.firstOrNull()
            ?: activeAddress

        val wasDelivery = prevOrder.fulfilmentMode == FulfilmentMode.DELIVERY
        val addressUnserviceableFallback = wasDelivery && !savedAddress.isServiceable
        val reorderMode = if (addressUnserviceableFallback) FulfilmentMode.PICKUP else prevOrder.fulfilmentMode
        val reorderCityHubs = snapshot.nodes.filter {
            it.pickupEnabled && it.type != FulfilmentPointType.PLANT && it.city.equals(savedAddress.city, ignoreCase = true)
        }
        val reorderPickupHubId = prevOrder.pickupPointId?.takeIf { pid -> reorderCityHubs.any { it.id == pid } }
            ?: reorderCityHubs.firstOrNull()?.id
            ?: ""

        var sheetWindowLabel by remember(prevOrder.id, deliveryWindowOptions) {
            mutableStateOf(deliveryWindowOptions.firstOrNull()?.label ?: "")
        }
        var sheetServiceLevel by remember(prevOrder.id, snapshot.settings.expressEnabled, reorderMode) {
            mutableStateOf(
                if (reorderMode == FulfilmentMode.DELIVERY && snapshot.settings.expressEnabled && prevOrder.serviceLevel == ServiceLevel.EXPRESS) {
                    ServiceLevel.EXPRESS
                } else {
                    ServiceLevel.STANDARD
                }
            )
        }
        val effectiveSheetServiceLevel = if (reorderMode == FulfilmentMode.DELIVERY && snapshot.settings.expressEnabled) {
            sheetServiceLevel
        } else {
            ServiceLevel.STANDARD
        }
        val selectedSheetWindow = deliveryWindowOptions.find { it.label == sheetWindowLabel }
            ?: deliveryWindowOptions.firstOrNull()

        val reorderQuote = remember(
            reorderSku,
            reorderQty,
            reorderMode,
            effectiveSheetServiceLevel,
            snapshot.currentPriceList,
            snapshot.taxConfig,
            snapshot.settings
        ) {
            PricingEngine.buildQuote(
                orderType = OrderType.REFILL_EXCHANGE,
                sku = reorderSku,
                quantity = reorderQty,
                accessories = emptyList(),
                mode = reorderMode,
                priceList = snapshot.currentPriceList,
                tax = snapshot.taxConfig,
                settings = snapshot.settings,
                discountPaisa = 0L,
                serviceLevel = effectiveSheetServiceLevel
            )
        }

        val reorderSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQuickReorderSheet = false },
            sheetState = reorderSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("quick_reorder_bottom_sheet"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Confirm Reorder ($reorderQty× $reorderSizeLabel refill)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = BlueGasDark
                    )
                }
                RecommendedPill()

                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val destText = if (reorderMode == FulfilmentMode.PICKUP) {
                            val hubName = reorderCityHubs.find { it.id == reorderPickupHubId }?.name ?: "Hub pickup"
                            "Hub pickup · $hubName"
                        } else {
                            "${savedAddress.label} · ${savedAddress.street}, ${savedAddress.area}"
                        }
                        Text(text = "Destination: $destText", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                        val methodWords = if (prevOrder.paymentMethod == PaymentMethod.ONLINE_HOSTED) "Online payment" else "Cash on delivery"
                        Text(text = "Payment: $methodWords", fontSize = 11.sp, color = Color(0xFF475569))
                    }
                }

                if (addressUnserviceableFallback) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Your saved address is outside the delivery area — switched to Hub pickup.",
                            fontSize = 11.sp,
                            color = Color(0xFF991B1B),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (reorderMode == FulfilmentMode.DELIVERY) {
                    Text("When should we deliver?", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                    if (deliveryWindowOptions.isEmpty()) {
                        Text(
                            text = "No delivery time windows are open right now.",
                            fontSize = 11.sp,
                            color = Color(0xFFDC2626)
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            deliveryWindowOptions.forEach { opt ->
                                val isChosen = selectedSheetWindow?.label == opt.label
                                FilterChip(
                                    selected = isChosen,
                                    onClick = { sheetWindowLabel = opt.label },
                                    label = { Text(opt.label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    if (snapshot.settings.expressEnabled) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            RecommendedPill()
                            DemoValuePill("Demo value · delivery promise")
                        }
                        val expressRoundedPaisa = PricingEngine.roundToRupees(
                            snapshot.settings.expressFeePaisa,
                            snapshot.currentPriceList.roundingRule
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = effectiveSheetServiceLevel == ServiceLevel.STANDARD,
                                onClick = { sheetServiceLevel = ServiceLevel.STANDARD },
                                label = { Text(ServiceLevel.STANDARD.label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = effectiveSheetServiceLevel == ServiceLevel.EXPRESS,
                                onClick = { sheetServiceLevel = ServiceLevel.EXPRESS },
                                label = { Text("${ServiceLevel.EXPRESS.label} (+${formatRupees(expressRoundedPaisa)})", fontSize = 11.sp) },
                                modifier = Modifier.weight(1.2f)
                            )
                        }
                    }
                }

                HorizontalDivider()
                reorderQuote.lines.filter { it.lineType != "DEPOSIT" }.forEach { line ->
                    QuoteLineRow(label = "${line.quantity}x ${line.productName}", value = formatRupees(line.subtotalPaisa))
                }
                if (reorderMode == FulfilmentMode.DELIVERY) {
                    QuoteLineRow(label = "Doorstep Delivery Fee", value = formatRupees(reorderQuote.deliveryFeePaisa))
                }
                if (reorderQuote.expressFeePaisa > 0L) {
                    QuoteLineRow(label = "Express delivery fee", value = formatRupees(reorderQuote.expressFeePaisa))
                }
                if (reorderQuote.taxPaisa > 0L) {
                    QuoteLineRow(label = "Sales Tax (GST)", value = formatRupees(reorderQuote.taxPaisa))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Live Total", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(formatRupees(reorderQuote.totalAmountPaisa), fontWeight = FontWeight.Black, fontSize = 16.sp, color = BlueGasPrimary)
                }

                val canConfirmReorder = if (reorderMode == FulfilmentMode.DELIVERY) {
                    savedAddress.isServiceable && deliveryWindowOptions.isNotEmpty() && selectedSheetWindow != null
                } else {
                    reorderPickupHubId.isNotEmpty()
                }
                Button(
                    onClick = {
                        val submitted = submitOrderRequest(
                            reqAddressId = savedAddress.id,
                            reqMode = reorderMode,
                            reqSku = reorderSku,
                            reqQty = reorderQty,
                            reqPaymentMethod = prevOrder.paymentMethod,
                            reqOrderType = OrderType.REFILL_EXCHANGE,
                            reqPickupPointId = reorderPickupHubId,
                            reqAccessories = emptyList(),
                            reqWindow = selectedSheetWindow,
                            reqServiceLevel = effectiveSheetServiceLevel
                        )
                        if (submitted) {
                            showQuickReorderSheet = false
                        }
                    },
                    enabled = canConfirmReorder && !showPaymentGatewayDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_quick_reorder_button")
                ) {
                    Text("Place Reorder (${formatRupees(reorderQuote.totalAmountPaisa)})", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    // Shared Demo Payment Dialog
    if (showPaymentGatewayDialog && createdOrderIdForPayment != null) {
        DemoPaymentDialog(
            orderId = createdOrderIdForPayment!!,
            payableLabel = formatRupees(snapshot.orders.find { it.id == createdOrderIdForPayment }?.totalAmountPaisa ?: quote.totalAmountPaisa),
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

private fun formatCnicDigits(digits: String): String {
    val clean = digits.filter { it.isDigit() }.take(13)
    return buildString {
        clean.forEachIndexed { idx, ch ->
            if (idx == 5 || idx == 12) append('-')
            append(ch)
        }
    }
}

private object CnicVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text.take(13)
        val formatted = formatCnicDigits(raw)
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, raw.length)
                return when {
                    o <= 5 -> o
                    o <= 12 -> o + 1
                    else -> o + 2
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                val f = offset.coerceIn(0, formatted.length)
                return when {
                    f <= 5 -> f
                    f <= 13 -> (f - 1).coerceAtLeast(5)
                    else -> (f - 2).coerceAtLeast(12)
                }.coerceAtMost(raw.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
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
