package com.example.bluegas.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.model.OrderStatus
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

enum class CustomerTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val testTag: String) {
    HOME("Home", Icons.Default.Home, "customer_tab_home"),
    ORDERS("Orders", Icons.AutoMirrored.Filled.ReceiptLong, "customer_tab_orders"),
    TRACKING("Tracking", Icons.Default.LocalShipping, "customer_tab_tracking"),
    PROFILE("Profile", Icons.Default.Person, "customer_tab_profile")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAppScreen(store: BlueGasStore, modifier: Modifier = Modifier) {
    var currentTab by remember { mutableStateOf(CustomerTab.HOME) }
    var selectedTrackingOrderId by remember { mutableStateOf<String?>(null) }
    var notificationBannerMessage by remember { mutableStateOf<String?>(null) }

    val activeCustomer = store.customers.find { it.id == store.activeCustomerId } ?: store.customers.firstOrNull()
    val activeAddress = store.addresses.find { it.customerId == activeCustomer?.id && it.isServiceable }
        ?: store.addresses.find { it.customerId == activeCustomer?.id }
        ?: store.addresses.firstOrNull()

    val customerOrders = store.orders.filter { it.customerId == activeCustomer?.id }
    val activeOrdersCount = customerOrders.count { !it.status.isTerminal }
    val inTransitOrder = customerOrders.find {
        it.status == OrderStatus.ORD_DISP || it.status == OrderStatus.ORD_ATHO || it.status == OrderStatus.ORD_PREP
    }

    // Default tracking order to the latest active order or most recent order
    LaunchedEffect(customerOrders.size, currentTab) {
        if (selectedTrackingOrderId == null || store.orders.none { it.id == selectedTrackingOrderId }) {
            selectedTrackingOrderId = inTransitOrder?.id
                ?: customerOrders.firstOrNull { !it.status.isTerminal }?.id
                ?: customerOrders.firstOrNull()?.id
        }
    }

    // Phone canvas mockup layout
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE2E8F0)),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxHeight()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color(0xFFF8FAFC),
                topBar = {
                    Surface(
                        color = BlueGasDark,
                        shadowElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(BlueGasPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocalFireDepartment,
                                            contentDescription = "BlueGas Logo",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "BLUE GAS CONSUMER",
                                            fontSize = 10.sp,
                                            color = Color(0xFF93C5FD),
                                            fontWeight = FontWeight.Bold,
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

                                // Active Pilot Zone Badge
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
                                                .background(if (activeAddress?.isServiceable == true) Color(0xFF86EFAC) else Color(0xFFFCA5A5))
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = activeAddress?.city ?: "Pilot Area",
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Subheader delivery address strip
                            if (activeAddress != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = Color(0xFF1E293B),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            tint = if (activeAddress.isServiceable) Color(0xFF60A5FA) else Color(0xFFF87171),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Deliver to: ${activeAddress.label} • ${activeAddress.area}, ${activeAddress.city}",
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
                                                text = "Non-Serviceable",
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
                        CustomerTab.values().forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
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
                                                    if (inTransitOrder != null) {
                                                        Badge(
                                                            containerColor = Color(0xFF16A34A),
                                                            contentColor = Color.White
                                                        ) {
                                                            Text("LIVE")
                                                        }
                                                    }
                                                }
                                                else -> {}
                                            }
                                        }
                                    ) {
                                        Icon(imageVector = tab.icon, contentDescription = tab.label)
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
                        visible = notificationBannerMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = notificationBannerMessage ?: "",
                                    fontSize = 11.sp,
                                    color = Color(0xFF78350F),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { notificationBannerMessage = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color(0xFF78350F),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Main screen body router
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentTab) {
                            CustomerTab.HOME -> {
                                HomeScreen(
                                    store = store,
                                    onOrderCreated = { newOrder ->
                                        selectedTrackingOrderId = newOrder.id
                                        notificationBannerMessage = "Order ${newOrder.id} placed! Stock held for ${store.priceBookConfig.holdTtlMinutes} min."
                                    },
                                    onNavigateToOrders = {
                                        currentTab = CustomerTab.ORDERS
                                    },
                                    onTrackOrder = { orderId ->
                                        selectedTrackingOrderId = orderId
                                        currentTab = CustomerTab.TRACKING
                                    }
                                )
                            }
                            CustomerTab.ORDERS -> {
                                OrdersScreen(
                                    store = store,
                                    onTrackOrder = { orderId ->
                                        selectedTrackingOrderId = orderId
                                        currentTab = CustomerTab.TRACKING
                                    },
                                    onActionMessage = { msg ->
                                        notificationBannerMessage = msg
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
                                        currentTab = CustomerTab.ORDERS
                                    }
                                )
                            }
                            CustomerTab.PROFILE -> {
                                ProfileScreen(
                                    store = store,
                                    onCustomerChanged = {
                                        notificationBannerMessage = "Switched customer profile"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
