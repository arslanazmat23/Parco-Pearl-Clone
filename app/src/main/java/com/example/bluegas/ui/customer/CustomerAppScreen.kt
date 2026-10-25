/**
 * Main application shell and top-level navigation container for the BLUE GAS Customer App.
 *
 * Coordinates navigation across the four primary customer tabs (Home, Orders, Tracking, Profile),
 * hosts the unified auto-dismissing banner system, manages cross-tab state preservation using
 * [rememberSaveableStateHolder], and isolates reorder prefill intents per customer identity.
 *
 * Reads: [SharedAppState.customerTab], [SharedAppState.activeCustomer], [SharedAppState.addresses], [SharedAppState.orders]
 * Writes: [BlueGasStore.setCustomerTab]
 */
package com.example.bluegas.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.example.bluegas.model.AppSurface
import com.example.bluegas.model.OrderStatus
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary
import kotlinx.coroutines.delay

/**
 * Top-level navigation destinations available within the customer experience.
 *
 * @property label User-facing display text in bottom navigation.
 * @property icon Navigation item vector icon.
 * @property testTag Automated UI test identifier for selection.
 */
enum class CustomerTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val testTag: String) {
    HOME("Home", Icons.Default.Home, "customer_tab_home"),
    ORDERS("Orders", Icons.AutoMirrored.Filled.ReceiptLong, "customer_tab_orders"),
    TRACKING("Tracking", Icons.Default.LocalShipping, "customer_tab_tracking"),
    PROFILE("Profile", Icons.Default.Person, "customer_tab_profile")
}

/**
 * Root composable for the BLUE GAS Customer App.
 *
 * Hosts top bar branding and active delivery zone indicator, displays global feedback messages,
 * renders bottom navigation with active order count badges, and switches between feature panes.
 * Uses [rememberSaveableStateHolder] to preserve form inputs across tab transitions.
 *
 * @param store The single source of truth application store.
 * @param modifier Optional layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAppScreen(store: BlueGasStore, modifier: Modifier = Modifier) {
    val snapshot by store.appState.collectAsStateWithLifecycle()
    val currentTab = when (snapshot.customerTab.uppercase()) {
        "ORDERS" -> CustomerTab.ORDERS
        "TRACKING" -> CustomerTab.TRACKING
        "PROFILE" -> CustomerTab.PROFILE
        else -> CustomerTab.HOME
    }
    var selectedTrackingOrderId by remember { mutableStateOf<String?>(null) }

    // Unified customer notification banner with auto-dismiss.
    // Automatically clears transient action confirmations and errors after 5 seconds to preserve viewport space.
    // Any subsequent message emission cancels the previous timer and resets the 5-second window.
    var banner by remember { mutableStateOf<BannerMsg?>(null) }
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(5000L)
            banner = null
        }
    }
    val onActionMessage: (String, Boolean) -> Unit = { text, isError ->
        banner = BannerMsg(text, isError)
    }

    // Reorder prefill state holder; cleared when customer identity changes
    var reorderPrefill by remember { mutableStateOf<ReorderPrefill?>(null) }
    val activeCustomer = snapshot.activeCustomer

    LaunchedEffect(activeCustomer?.id) {
        reorderPrefill = null
    }

    // Address resolution: prioritizes customer's default address, then any serviceable address in the zone.
    // When no serviceable address is found, header indicator informs the customer of depot pickup availability.
    val customerAddresses = snapshot.addresses.filter { it.customerId == activeCustomer?.id }
    val activeAddress = customerAddresses.find { it.isDefault }
        ?: customerAddresses.find { it.isServiceable }
        ?: customerAddresses.firstOrNull()

    // Active order counter drives notification badges on bottom navigation destinations.
    // Excludes terminal and suspended lifecycle states (Completed, Cancelled, Closed, or Suspended).
    val customerOrders = snapshot.orders.filter { it.customerId == activeCustomer?.id }
    val activeOrdersCount = customerOrders.count {
        it.status != OrderStatus.ORD_COMP &&
            it.status != OrderStatus.ORD_CANC &&
            it.status != OrderStatus.ORD_FCLO &&
            it.status != OrderStatus.ORD_SUPS
    }

    // Retain form states when navigating away from tabs and returning
    val saveableHolder = rememberSaveableStateHolder()

    Surface(
        modifier = modifier.fillMaxSize().testTag("customer_app_root"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    Surface(
                        color = BlueGasDark,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth().testTag("customer_top_bar")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(BlueGasPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.LocalFireDepartment,
                                            contentDescription = "Blue Gas Logo",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "BLUE GAS",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = activeCustomer?.name ?: "Customer",
                                            fontSize = 15.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Surface(
                                    color = if (activeAddress?.isServiceable == true) Color(0xFF166534) else Color(0xFF991B1B),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (activeAddress?.isServiceable == true) Color(0xFF4ADE80) else Color(0xFFF87171))
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = if (activeAddress?.isServiceable == true) "Lahore Pilot" else "Pickup only",
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            if (activeAddress != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = Color(0x22FFFFFF),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = BlueGasPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${activeAddress.label}: ${activeAddress.street}, ${activeAddress.area}, ${activeAddress.city}",
                                            fontSize = 11.sp,
                                            color = Color(0xFFE2E8F0),
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (activeAddress.isServiceable) {
                                            Text(
                                                text = "Active Zone",
                                                fontSize = 9.sp,
                                                color = Color(0xFF86EFAC),
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            Text(
                                                text = "Outside delivery area",
                                                fontSize = 9.sp,
                                                color = Color(0xFFFCA5A5),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.White,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("customer_bottom_navigation")
                    ) {
                        CustomerTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { store.setCustomerTab(tab.name) },
                                modifier = Modifier.testTag(tab.testTag),
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            when (tab) {
                                                CustomerTab.ORDERS -> {
                                                    if (activeOrdersCount > 0) {
                                                        Badge(
                                                            containerColor = BlueGasPrimary,
                                                            contentColor = Color.White
                                                        ) {
                                                            Text(activeOrdersCount.toString())
                                                        }
                                                    }
                                                }
                                                CustomerTab.TRACKING -> {
                                                    val hasLiveOrder = customerOrders.any {
                                                        it.status == OrderStatus.ORD_DISP ||
                                                            it.status == OrderStatus.ORD_ATHO ||
                                                            it.status == OrderStatus.ORD_PREP
                                                    }
                                                    if (hasLiveOrder) {
                                                        Badge(
                                                            containerColor = Color(0xFF16A34A),
                                                            contentColor = Color.White
                                                        ) {
                                                            Text("•")
                                                        }
                                                    }
                                                }
                                                CustomerTab.PROFILE -> {
                                                    val unreadCount = snapshot.notifications.count {
                                                        it.surface == AppSurface.CUSTOMER && !it.isRead
                                                    }
                                                    if (unreadCount > 0) {
                                                        Badge(
                                                            containerColor = Color(0xFFDC2626),
                                                            contentColor = Color.White
                                                        ) {
                                                            Text(unreadCount.toString())
                                                        }
                                                    }
                                                }
                                                else -> {}
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label
                                        )
                                    }
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = BlueGasPrimary,
                                    selectedTextColor = BlueGasPrimary,
                                    indicatorColor = Color(0xFFDBEAFE),
                                    unselectedIconColor = Color(0xFF64748B),
                                    unselectedTextColor = Color(0xFF64748B)
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Global action / notification banner
                    AnimatedVisibility(
                        visible = banner != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        banner?.let { b ->
                            Surface(
                                color = if (b.isError) Color(0xFFFEF3C7) else Color(0xFFDCFCE7),
                                modifier = Modifier.fillMaxWidth().testTag("customer_global_banner")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (b.isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (b.isError) Color(0xFFB45309) else Color(0xFF16A34A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = b.text,
                                        fontSize = 11.sp,
                                        color = if (b.isError) Color(0xFF78350F) else Color(0xFF166534),
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { banner = null },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = if (b.isError) Color(0xFF78350F) else Color(0xFF166534),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Main screen body router with SaveableStateProvider preserving screen form state
                    Box(modifier = Modifier.fillMaxSize()) {
                        saveableHolder.SaveableStateProvider(currentTab.name) {
                            when (currentTab) {
                                CustomerTab.HOME -> {
                                    HomeScreen(
                                        store = store,
                                        onOrderCreated = { newOrder ->
                                            selectedTrackingOrderId = newOrder.id
                                        },
                                        onNavigateToOrders = {
                                            store.setCustomerTab("ORDERS")
                                        },
                                        onTrackOrder = { orderId ->
                                            selectedTrackingOrderId = orderId
                                            store.setCustomerTab("TRACKING")
                                        },
                                        onActionMessage = onActionMessage,
                                        prefill = reorderPrefill,
                                        onPrefillConsumed = { reorderPrefill = null }
                                    )
                                }
                                CustomerTab.ORDERS -> {
                                    OrdersScreen(
                                        store = store,
                                        onTrackOrder = { orderId ->
                                            selectedTrackingOrderId = orderId
                                            store.setCustomerTab("TRACKING")
                                        },
                                        onActionMessage = onActionMessage,
                                        onReorder = { order ->
                                            val gasItem = order.items.firstOrNull { it.lineType == "GAS" } ?: order.items.firstOrNull()
                                            val hasAccessory = order.items.any { it.lineType == "ACCESSORY" }
                                            reorderPrefill = ReorderPrefill(
                                                sku = gasItem?.sku ?: "SKU-LPG-11-8",
                                                quantity = gasItem?.quantity ?: 1,
                                                orderType = order.orderType,
                                                addressId = order.addressId,
                                                mode = order.fulfilmentMode,
                                                paymentMethod = order.paymentMethod,
                                                pickupPointId = order.pickupPointId ?: order.fulfilmentPointId,
                                                addAccessory = hasAccessory,
                                                nonce = System.currentTimeMillis()
                                            )
                                            store.setCustomerTab("HOME")
                                        },
                                        onNavigateToHome = {
                                            store.setCustomerTab("HOME")
                                        }
                                    )
                                }
                                CustomerTab.TRACKING -> {
                                    TrackingScreen(
                                        store = store,
                                        initialOrderId = selectedTrackingOrderId,
                                        onSelectOrderToTrack = { orderId ->
                                            selectedTrackingOrderId = orderId
                                        },
                                        onBackToOrders = {
                                            store.setCustomerTab("ORDERS")
                                        },
                                        onActionMessage = onActionMessage
                                    )
                                }
                                CustomerTab.PROFILE -> {
                                    ProfileScreen(
                                        store = store,
                                        onActionMessage = onActionMessage
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
