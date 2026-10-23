/**
 * Customer profile management, cylinder holdings registry, addresses, and emergency safety support.
 *
 * Implements:
 * - Customer identity display with masked CNIC verification and seamless persona switching ([BlueGasStore.setActiveCustomer])
 * - Cylinder holdings & deposit liabilities card reflecting deposits held without ownership transfer (Q03)
 * - Saved delivery addresses with delivery zone serviceability validation and addition ([BlueGasStore.addAddress])
 * - Past delivery feedback and ratings for completed orders ([RateOrderDialog])
 * - Account support cases and notification history review ([BlueGasStore.markNotificationRead])
 * - Direct support hotline dialer and Safety & Emergencies response card (1122 dispatch + safety concern ticket)
 *
 * Reads: [SharedAppState.activeCustomer], [SharedAppState.addresses], [SharedAppState.customerHoldings],
 *        [SharedAppState.supportCases], [SharedAppState.notifications], [SharedAppState.cylinders]
 * Calls: [BlueGasStore.setActiveCustomer], [BlueGasStore.addAddress], [BlueGasStore.setDefaultAddress],
 *        [BlueGasStore.raiseSafetyConcern], [BlueGasStore.markNotificationRead]
 */
package com.example.bluegas.ui.customer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.data.addAddress
import com.example.bluegas.data.markNotificationRead
import com.example.bluegas.data.raiseSafetyConcern
import com.example.bluegas.data.setActiveCustomer
import com.example.bluegas.data.setDefaultAddress
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

/**
 * Masks the middle digits of a 13-digit Pakistani CNIC to preserve customer privacy in the UI.
 *
 * @param cnic The raw 13-digit CNIC string.
 * @return Formatted CNIC with the middle 5 digits replaced with 'XXXXX'.
 */
fun maskCnic(cnic: String): String {
    if (cnic.length < 13) return cnic
    return cnic.take(7) + "XXXXX" + cnic.takeLast(1)
}

/**
 * Customer profile, address book, deposit holdings, and emergency safety support screen.
 *
 * @param store Application data store reference.
 * @param onActionMessage Customer notification banner callback receiving (message: String, isError: Boolean).
 * @param modifier Optional layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    store: BlueGasStore,
    onActionMessage: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snapshot by store.appState.collectAsStateWithLifecycle()
    val activeCustomer = snapshot.activeCustomer ?: return
    val customerAddresses = snapshot.addresses.filter { it.customerId == activeCustomer.id }
    val customerHoldings = snapshot.customerHoldings.filter { it.customerId == activeCustomer.id }
    val customerCases = snapshot.supportCases.filter { it.customerId == activeCustomer.id }
    val customerOrders = snapshot.orders.filter { it.customerId == activeCustomer.id }.sortedByDescending { it.createdAt }
    val customerOrdersCount = customerOrders.size

    var showCustomerSwitchMenu by remember { mutableStateOf(false) }
    var showSafetyConcernDialog by remember { mutableStateOf(false) }
    var showAddAddressDialog by remember { mutableStateOf(false) }

    val totalDepositPaidPaisa = customerHoldings.sumOf { it.depositPaisa }

    // Dialog state for rating a completed order (using shared RateOrderDialog)
    var ratingOrderId by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("customer_profile_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Customer Header Card with Switcher
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_identity_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(BlueGasPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeCustomer.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = activeCustomer.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "CNIC: ${maskCnic(activeCustomer.cnic)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "Phone: ${activeCustomer.phone}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }

                        // Switch Customer Dropdown Button
                        Box {
                            OutlinedButton(
                                onClick = { showCustomerSwitchMenu = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("switch_customer_profile_button")
                            ) {
                                Text("Switch", fontSize = 11.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = showCustomerSwitchMenu,
                                onDismissRequest = { showCustomerSwitchMenu = false }
                            ) {
                                snapshot.customers.forEach { cust ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(cust.name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("CNIC: ${maskCnic(cust.cnic)} (${cust.id})", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        },
                                        onClick = {
                                            val (ok, msg) = store.setActiveCustomer(cust.id)
                                            onActionMessage(msg, !ok)
                                            showCustomerSwitchMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(customerOrdersCount.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BlueGasPrimary)
                            Text("Orders Placed", fontSize = 10.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(customerHoldings.size.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF047857))
                            Text("Held Cylinders", fontSize = 10.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(customerCases.size.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFD97706))
                            Text("Support Cases", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        // Cylinder Holdings & Deposit Liabilities
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cylinder_holdings_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cylinder Holdings & Deposit Liabilities",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF78350F)
                            )
                        }
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${customerHoldings.size} Units Held",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Security deposits held: ${formatRupees(totalDepositPaidPaisa)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF92400E)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = Color(0x33F59E0B),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "A security deposit does not transfer ownership of the cylinder. Refund terms follow the BLUE GAS deposit policy.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFB45309),
                                lineHeight = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            DemoValuePill(text = "Demo value · Q03")
                        }
                    }

                    if (customerHoldings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        customerHoldings.forEach { holding ->
                            val cyl = snapshot.cylinders.find { it.serialNumber == holding.cylinderSerial }
                            val sizeLabel = cyl?.let { "${it.weightKg} kg" }
                            val ownershipLabel = when (holding.ownership) {
                                CylinderOwnership.OWNED_BY_CUSTOMER -> "Owned"
                                CylinderOwnership.DEPOSIT_HELD -> "Deposit held"
                                CylinderOwnership.LOANED -> "Loaned"
                                else -> holding.ownership.label
                            }

                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, Color(0xFFFDE68A)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFF78350F), modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = holding.cylinderSerial,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (sizeLabel != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = Color(0xFFFEF3C7),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = sizeLabel,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF92400E),
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "Ownership: $ownershipLabel",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    Text(
                                        text = "${formatRupees(holding.depositPaisa)} Deposit",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF78350F)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Delivery Addresses Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_addresses_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PinDrop, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Saved Delivery Addresses (${customerAddresses.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    customerAddresses.forEach { addr ->
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (addr.isServiceable) Icons.Default.LocationOn else Icons.Default.LocationOff,
                                    contentDescription = null,
                                    tint = if (addr.isServiceable) BlueGasPrimary else Color(0xFFDC2626),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(addr.label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        if (addr.isDefault) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(4.dp)) {
                                                Text("DEFAULT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = BlueGasPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    }
                                    Text("${addr.street}, ${addr.area}, ${addr.city}", fontSize = 11.sp, color = Color(0xFF475569))
                                    Text(
                                        text = if (addr.isServiceable) "Zone: ${addr.zoneId} (Serviceable delivery area)" else "Outside current delivery area",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (addr.isServiceable) Color(0xFF166534) else Color(0xFFDC2626)
                                    )
                                }
                                if (!addr.isDefault) {
                                    TextButton(
                                        onClick = {
                                            val (ok, msg) = store.setDefaultAddress(activeCustomer.id, addr.id)
                                            onActionMessage(msg, !ok)
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Set Default", fontSize = 10.sp, color = BlueGasPrimary)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { showAddAddressDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("add_new_address_button"),
                        border = BorderStroke(1.dp, BlueGasPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.AddLocation, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add New Address", fontSize = 12.sp, color = BlueGasPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Order Ratings Section (using shared RateOrderDialog)
        val completedOrders = customerOrders.filter { it.status == OrderStatus.ORD_COMP }
        if (completedOrders.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().testTag("profile_ratings_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFEAB308), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delivery Feedback & Ratings", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlueGasDark)
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        completedOrders.take(3).forEach { ord ->
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Order ${ord.id}", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text(
                                            text = "${ord.items.firstOrNull()?.productName ?: "Cylinder"} • ${formatRupees(ord.totalAmountPaisa)}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                        if (ord.ratingStars != null) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "★".repeat(ord.ratingStars ?: 0) + "☆".repeat(5 - (ord.ratingStars ?: 0)),
                                                    color = Color(0xFFEAB308),
                                                    fontSize = 13.sp
                                                )
                                                if (!ord.ratingComment.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(ord.ratingComment ?: "", fontSize = 10.sp, color = Color(0xFF475569))
                                                }
                                            }
                                        }
                                    }

                                    if (ord.ratingStars == null) {
                                        FilledTonalButton(
                                            onClick = {
                                                ratingOrderId = ord.id
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Rate", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Support Cases History
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_support_cases_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SupportAgent, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Support Cases (${customerCases.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    if (customerCases.isEmpty()) {
                        Text(
                            text = "No open complaints.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        customerCases.forEach { c ->
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val orderLabel = if (c.orderId != null) " • Order: ${c.orderId}" else ""
                                        Text("Case: ${c.id}$orderLabel", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Surface(
                                            color = when (c.status) {
                                                "OPEN" -> Color(0xFFFEE2E2)
                                                "INVESTIGATING" -> Color(0xFFFEF3C7)
                                                else -> Color(0xFFDCFCE7)
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = c.status,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (c.status) {
                                                    "OPEN" -> Color(0xFFDC2626)
                                                    "INVESTIGATING" -> Color(0xFFB45309)
                                                    else -> Color(0xFF166534)
                                                },
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Issue: ${c.issueType.replace("_", " ")}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(c.description, fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Notifications section
        val customerNotifications = snapshot.notifications
            .filter { it.surface == AppSurface.CUSTOMER }
            .sortedByDescending { it.timestamp }

        if (customerNotifications.isNotEmpty()) {
            item {
                Text(
                    text = "Notifications (${customerNotifications.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = BlueGasDark,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }

            items(customerNotifications.take(10)) { notif ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable {
                            store.markNotificationRead(notif.id)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (notif.isRead) Color.White else Color(0xFFEFF6FF)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, if (notif.isRead) Color(0xFFE2E8F0) else BlueGasPrimary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!notif.isRead) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(BlueGasPrimary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(notif.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BlueGasDark)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(formatTimestamp(notif.timestamp), fontSize = 9.sp, color = Color.Gray)
                        }
                        Text(notif.message, fontSize = 11.sp, color = Color(0xFF475569), modifier = Modifier.padding(top = 2.dp))
                        if (notif.orderId != null) {
                            Text("Order: ${notif.orderId}", fontSize = 9.sp, color = BlueGasPrimary, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }

        // Contact Support Card (F6.8)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Contact Support", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlueGasDark)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "BLUE GAS support: ${snapshot.settings.supportPhone}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${snapshot.settings.supportPhone}"))
                                context.startActivity(intent)
                            },
                            label = { Text("Call", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("WhatsApp (Phase 3)", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            }
        }

        // Safety & Emergencies Card (F6.4)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("safety_helpline_card")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Safety & emergencies",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF991B1B)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Follow the approved BLUE GAS safety procedure (to be supplied by the client, Q07). For emergencies call Rescue 1122.",
                        fontSize = 11.sp,
                        color = Color(0xFF7F1D1D),
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1122"))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Call 1122", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { showSafetyConcernDialog = true },
                            border = BorderStroke(1.dp, Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Report a safety concern", fontSize = 11.sp, color = Color(0xFFDC2626))
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Add New Address Dialog (F6.6)
    if (showAddAddressDialog) {
        var labelInput by remember { mutableStateOf("Home") }
        var streetInput by remember { mutableStateOf("") }
        var areaInput by remember { mutableStateOf("") }
        val availableCities = remember(snapshot.serviceZones) {
            snapshot.serviceZones.map { it.city }.distinct().ifEmpty { listOf("Lahore", "Islamabad") }
        }
        var selectedCity by remember { mutableStateOf(availableCities.first()) }
        val cityZones = remember(selectedCity, snapshot.serviceZones) {
            snapshot.serviceZones.filter { it.city.equals(selectedCity, ignoreCase = true) }
                .ifEmpty { snapshot.serviceZones }
        }
        var selectedZoneId by remember(cityZones) { mutableStateOf(cityZones.firstOrNull()?.id ?: "Z-01") }
        var makeDefaultSwitch by remember { mutableStateOf(customerAddresses.isEmpty()) }
        var formError by remember { mutableStateOf<String?>(null) }
        val isServiceableZone = cityZones.find { it.id == selectedZoneId }?.isServiceable ?: false

        var cityDropdownExpanded by remember { mutableStateOf(false) }
        var zoneDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddAddressDialog = false },
            title = {
                Text("Add New Delivery Address", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (formError != null) {
                        Text(formError ?: "", color = Color(0xFFDC2626), fontSize = 11.sp)
                    }

                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("Label (e.g. Home, Office)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = streetInput,
                        onValueChange = { streetInput = it },
                        label = { Text("Street Address / House No") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = areaInput,
                        onValueChange = { areaInput = it },
                        label = { Text("Area / Sector") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // City Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { cityDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("City: $selectedCity", fontSize = 12.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = cityDropdownExpanded,
                            onDismissRequest = { cityDropdownExpanded = false }
                        ) {
                            availableCities.forEach { city ->
                                DropdownMenuItem(
                                    text = { Text(city) },
                                    onClick = {
                                        selectedCity = city
                                        cityDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Zone Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val zoneName = cityZones.find { it.id == selectedZoneId }?.name ?: selectedZoneId
                        OutlinedButton(
                            onClick = { zoneDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Zone: $zoneName", fontSize = 12.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = zoneDropdownExpanded,
                            onDismissRequest = { zoneDropdownExpanded = false }
                        ) {
                            cityZones.forEach { zn ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("${zn.name} (${zn.id})", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Text(if (zn.isServiceable) "Serviceable delivery zone" else "Outside delivery area", fontSize = 9.sp, color = if (zn.isServiceable) Color(0xFF166534) else Color(0xFFDC2626))
                                        }
                                    },
                                    onClick = {
                                        selectedZoneId = zn.id
                                        zoneDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Serviceability preview of selected zone (no duplicate val declaration)
                    Surface(
                        color = if (isServiceableZone) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isServiceableZone) "✓ Serviceable delivery area" else "⚠️ Outside current delivery area (Depot pickup available)",
                            fontSize = 10.sp,
                            color = if (isServiceableZone) Color(0xFF166534) else Color(0xFF991B1B),
                            modifier = Modifier.padding(6.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Set as default address", fontSize = 12.sp)
                        Switch(
                            checked = makeDefaultSwitch,
                            onCheckedChange = { makeDefaultSwitch = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (streetInput.isBlank()) {
                            formError = "Please enter street address."
                            return@Button
                        }
                        val (ok, res) = store.addAddress(
                            customerId = activeCustomer.id,
                            label = labelInput.ifBlank { "Address" },
                            street = streetInput.trim(),
                            area = areaInput.trim(),
                            city = selectedCity,
                            zoneId = selectedZoneId,
                            makeDefault = makeDefaultSwitch
                        )
                        if (ok) {
                            showAddAddressDialog = false
                            val msg = if (isServiceableZone) {
                                "Address added. This area is in the delivery zone."
                            } else {
                                "Address added. This address is outside the current delivery area — depot pickup is available."
                            }
                            onActionMessage(msg, false)
                        } else {
                            formError = res
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary)
                ) {
                    Text("Save Address")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAddressDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Report a Safety Concern Dialog (F6.4)
    if (showSafetyConcernDialog) {
        var selectedConcernOrderId by remember { mutableStateOf<String?>(null) }
        var concernDescription by remember { mutableStateOf("") }
        var concernError by remember { mutableStateOf<String?>(null) }
        var orderDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSafetyConcernDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Report a Safety Concern", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Follow the approved BLUE GAS safety procedure (to be supplied by the client, Q07). For emergencies call Rescue 1122.",
                        fontSize = 11.sp,
                        color = Color(0xFF991B1B)
                    )

                    if (concernError != null) {
                        Text(concernError ?: "", color = Color(0xFFDC2626), fontSize = 11.sp)
                    }

                    Text("Related order (optional):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { orderDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val label = if (selectedConcernOrderId == null) {
                                "Not about a specific order"
                            } else {
                                val ord = customerOrders.find { it.id == selectedConcernOrderId }
                                "Order ${selectedConcernOrderId} (${ord?.status?.displayName ?: ""})"
                            }
                            Text(label, fontSize = 11.sp, maxLines = 1)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = orderDropdownExpanded,
                            onDismissRequest = { orderDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Not about a specific order", fontSize = 12.sp) },
                                onClick = {
                                    selectedConcernOrderId = null
                                    orderDropdownExpanded = false
                                }
                            )
                            customerOrders.forEach { ord ->
                                DropdownMenuItem(
                                    text = { Text("Order ${ord.id} (${ord.status.displayName})", fontSize = 12.sp) },
                                    onClick = {
                                        selectedConcernOrderId = ord.id
                                        orderDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = concernDescription,
                        onValueChange = {
                            concernDescription = it
                            concernError = null
                        },
                        label = { Text("Description of concern *") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (concernDescription.isBlank()) {
                            concernError = "Please describe the safety concern."
                            return@Button
                        }
                        val (ok, msg) = store.raiseSafetyConcern(
                            customerId = activeCustomer.id,
                            description = concernDescription.trim(),
                            orderId = selectedConcernOrderId
                        )
                        onActionMessage(msg, !ok)
                        if (ok) {
                            showSafetyConcernDialog = false
                            concernDescription = ""
                            selectedConcernOrderId = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSafetyConcernDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Shared RateOrderDialog (F6.7 / F1.4)
    if (ratingOrderId != null) {
        RateOrderDialog(
            orderId = ratingOrderId ?: "",
            store = store,
            onResult = { msg, isError ->
                onActionMessage(msg, isError)
            },
            onDismiss = {
                ratingOrderId = null
            }
        )
    }
}
