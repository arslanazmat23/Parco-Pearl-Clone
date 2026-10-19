package com.example.bluegas.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

fun maskCnic(cnic: String): String {
    if (cnic.length < 13) return cnic
    return cnic.take(7) + "XXXXX" + cnic.takeLast(1)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    store: BlueGasStore,
    onCustomerChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot by store.appState.collectAsStateWithLifecycle()
    val activeCustomer = snapshot.activeCustomer ?: return
    val customerAddresses = snapshot.addresses.filter { it.customerId == activeCustomer.id }
    val customerHoldings = snapshot.customerHoldings.filter { it.customerId == activeCustomer.id }
    val customerCases = snapshot.supportCases.filter { it.customerId == activeCustomer.id }
    val customerOrdersCount = snapshot.orders.count { it.customerId == activeCustomer.id }

    var showCustomerSwitchMenu by remember { mutableStateOf(false) }
    var showSafetyHelplineDialog by remember { mutableStateOf(false) }

    val totalDepositPaidPaisa = customerHoldings.sumOf { it.depositPaisa }

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
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = activeCustomer.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "NADRA Verified",
                                        tint = BlueGasPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "CNIC: ${maskCnic(activeCustomer.cnic)} • NADRA Verified",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "Phone: ${activeCustomer.phone}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                                Text(
                                    text = "Member since ${formatTimestamp(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
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
                                                Text("CNIC: ${cust.cnic} (${cust.id})", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        },
                                        onClick = {
                                            store.activeCustomerId = cust.id
                                            store.notifyChanged()
                                            showCustomerSwitchMenu = false
                                            onCustomerChanged()
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

        // Cylinder Holdings & Deposit Liabilities (Critical Business Domain Rule)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
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
                        text = "Total Refundable Security Deposits Held: ${formatPaisaToPkr(totalDepositPaidPaisa)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF92400E)
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = Color(0x33F59E0B),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Balance Sheet Disclaimer: Security deposits represent a refundable liability held on your account. They do NOT transfer ownership of the cylinder asset to the consumer. Full refund is processed upon return of undamaged cylinder.",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(6.dp),
                            lineHeight = 13.sp
                        )
                    }

                    if (customerHoldings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        customerHoldings.forEach { holding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFF78350F), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = holding.cylinderSerial,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${formatPaisaToPkr(holding.depositPaisa)} Deposit",
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

        // Delivery Addresses Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                        if (addr.id == activeCustomer.defaultAddressId) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(4.dp)) {
                                                Text("DEFAULT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = BlueGasPrimary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    }
                                    Text("${addr.street}, ${addr.area}, ${addr.city}", fontSize = 11.sp, color = Color(0xFF475569))
                                    Text(
                                        text = if (addr.isServiceable) "Zone: ${addr.zoneId} (Serviceable Pilot)" else "Outside Active Pilot Area",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (addr.isServiceable) Color(0xFF16A34A) else Color(0xFFDC2626)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { /* TODO: Address form dialog */ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.AddLocation, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add New Address", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                }
            }
        }

        // Support Cases & Dispute History
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
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
                                text = "Support Cases & Safety Disputes (${customerCases.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    if (customerCases.isEmpty()) {
                        Text(
                            text = "No open support disputes. Your safety is guaranteed.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        customerCases.forEach { c ->
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
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
                                        Text("Case: ${c.id} • Order: ${c.orderId}", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
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
                            notif.isRead = true
                            store.notifyChanged()
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

        // Contact Support Card
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
                    Text("Need help with an order or have a question?", fontSize = 11.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { /* TODO: Open phone dialer */ },
                            label = { Text("Call: 0800-BLUEGAS", fontSize = 10.sp) },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                        AssistChip(
                            onClick = { /* TODO: Open WhatsApp */ },
                            label = { Text("WhatsApp", fontSize = 10.sp) },
                            leadingIcon = { Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            }
        }

        // Safety Command Center & 24/7 Helpline Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("safety_helpline_card")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDC2626)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Emergency, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("24/7 Gas Leak Emergency Helpline", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF991B1B))
                        Text("In case of suspected leak, turn off regulator and call immediately.", fontSize = 10.sp, color = Color(0xFF475569))
                    }
                    Button(
                        onClick = { showSafetyHelplineDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("1122", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showSafetyHelplineDialog) {
        AlertDialog(
            onDismissRequest = { showSafetyHelplineDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Emergency, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Emergency LPG Safety Response", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column {
                    Text("1. Immediate Actions:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("• Turn off cylinder valve or regulator knob immediately.\n• Do not ignite matches, lighters, or operate electric switches.\n• Open all windows and doors for rapid ventilation.\n• Evacuate premises if smell persists.", fontSize = 11.sp, color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("2. Emergency Helplines:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("• Blue Gas Emergency Response: 0800-BLUEGAS (Toll Free)\n• Rescue 1122 Pakistan", fontSize = 11.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(onClick = { showSafetyHelplineDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) {
                    Text("Understood")
                }
            }
        )
    }
}
