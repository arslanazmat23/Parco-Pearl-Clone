package com.example.bluegas.ui.rider

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.*
import com.example.bluegas.model.PartnerApplication
import com.example.bluegas.ui.customer.DemoValuePill
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

private val JOIN_CNIC_REGEX = Regex("^\\d{5}-\\d{7}-\\d$")

/**
 * Self-service onboarding form and application status view allowing prospective riders
 * to join BLUE GAS and track approval and checklist training readiness (To-Be P8).
 *
 * @param store Shared in-memory store instance.
 * @param appState Immutable state snapshot for rendering reactive status updates.
 * @param onBack Callback returning to the active Rider Console.
 * @param onActionMessage Banner callback for submission confirmation or error feedback.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RiderJoinScreen(
    store: BlueGasStore,
    appState: SharedAppState,
    onBack: () -> Unit,
    onActionMessage: (String, Boolean) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var cnicInput by remember { mutableStateOf("") }
    var cityInput by remember { mutableStateOf("Lahore") }
    var vehicleInput by remember { mutableStateOf("") }
    var cnicCopyChecked by remember { mutableStateOf(false) }
    var licenceChecked by remember { mutableStateOf(false) }
    var regChecked by remember { mutableStateOf(false) }
    var localBanner by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    val lastApp: PartnerApplication? = remember(appState.version, store.lastRiderApplicationId) {
        val targetId = store.lastRiderApplicationId
        if (targetId != null) {
            appState.partnerApplications.find { it.id == targetId }
        } else {
            null
        }
    }

    val linkedRider = remember(appState.version, lastApp?.applicantName, lastApp?.cnic) {
        if (lastApp == null) null
        else appState.riders.find { it.cnic == lastApp.cnic || it.name == lastApp.applicantName }
    }

    val isCnicValid = JOIN_CNIC_REGEX.matches(cnicInput.trim())
    val canSubmit = nameInput.isNotBlank() &&
        phoneInput.isNotBlank() &&
        isCnicValid &&
        vehicleInput.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9)),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("rider_join_screen")
            ) {
                Surface(color = BlueGasDark, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("rider_join_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Rider Console",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Join as a BLUE GAS rider",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Apply for delivery & collection runs",
                                fontSize = 11.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val activeBanner = localBanner
                    if (activeBanner != null) {
                        Surface(
                            color = if (activeBanner.second) Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (activeBanner.second) Color(0xFFFCA5A5) else Color(0xFF86EFAC)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = activeBanner.first,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeBanner.second) Color(0xFF991B1B) else Color(0xFF166534),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (lastApp != null) {
                        val (bg, fg, statusLabel) = when {
                            lastApp.status == "PENDING_APPROVAL" ->
                                Triple(Color(0xFFDBEAFE), Color(0xFF1E40AF), "Under review")
                            lastApp.status == "RETURNED_INCOMPLETE" ->
                                Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "Returned — documents needed")
                            lastApp.status == "APPROVED" && linkedRider?.installationTrained == true ->
                                Triple(Color(0xFFDCFCE7), Color(0xFF166534), "Ready for jobs")
                            lastApp.status == "APPROVED" ->
                                Triple(Color(0xFFFEF3C7), Color(0xFF92400E), "Approved — training pending")
                            lastApp.status == "REJECTED" ->
                                Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), "Not approved")
                            else -> Triple(Color(0xFFF1F5F9), Color(0xFF334155), lastApp.status)
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, fg.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("rider_join_status_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Application status (${lastApp.id})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = statusLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = fg,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                                Text(
                                    text = "${lastApp.applicantName} · ${lastApp.city} · ${lastApp.vehicleOrShopDetails}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569)
                                )
                                if ((lastApp.status == "RETURNED_INCOMPLETE" || lastApp.status == "REJECTED") &&
                                    !lastApp.decisionReason.isNullOrBlank()
                                ) {
                                    Text(
                                        text = "Reason: ${lastApp.decisionReason}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = fg
                                    )
                                }
                                if (lastApp.status == "APPROVED" && linkedRider?.installationTrained == false) {
                                    Text(
                                        text = "Operations has approved your documents. Visit your hub to complete installation checklist training.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF92400E)
                                    )
                                }
                                if (lastApp.status == "APPROVED" && linkedRider?.installationTrained == true) {
                                    Button(
                                        onClick = {
                                            store.switchRider(linkedRider.id)
                                            onBack()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("rider_join_open_console_btn")
                                    ) {
                                        Text("Switch to ${linkedRider.name}", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Rider application details",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F172A)
                            )

                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                label = { Text("Full name *") },
                                placeholder = { Text("e.g. Tariq Mahmood", color = Color.Gray) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rider_join_name")
                            )

                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                label = { Text("Phone *") },
                                placeholder = { Text("e.g. +92 300 1234567", color = Color.Gray) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rider_join_phone")
                            )

                            OutlinedTextField(
                                value = cnicInput,
                                onValueChange = { cnicInput = it },
                                label = { Text("CNIC *") },
                                placeholder = { Text("#####-#######-#", color = Color.Gray) },
                                isError = cnicInput.isNotBlank() && !isCnicValid,
                                supportingText = {
                                    Text("Format: #####-#######-# (13 digits with dashes)", fontSize = 10.sp)
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rider_join_cnic")
                            )

                            Text("City *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Lahore", "Islamabad").forEach { cityOption ->
                                    FilterChip(
                                        selected = cityInput == cityOption,
                                        onClick = { cityInput = cityOption },
                                        label = { Text(cityOption, fontSize = 12.sp) },
                                        modifier = Modifier.testTag("rider_join_city_${cityOption.lowercase()}")
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = vehicleInput,
                                onValueChange = { vehicleInput = it },
                                label = { Text("Vehicle number *") },
                                placeholder = { Text("e.g. LEB-4521 · Honda 125", color = Color.Gray) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rider_join_vehicle")
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Required documents",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                DemoValuePill("Demo value · Q18/Q31")
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                listOf(
                                    Triple("CNIC copy", cnicCopyChecked) { v: Boolean -> cnicCopyChecked = v },
                                    Triple("Driving licence", licenceChecked) { v: Boolean -> licenceChecked = v },
                                    Triple("Vehicle registration", regChecked) { v: Boolean -> regChecked = v }
                                ).forEach { (label, checked, onToggle) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onToggle(!checked) }
                                    ) {
                                        Checkbox(checked = checked, onCheckedChange = onToggle)
                                        Text(label, fontSize = 12.sp)
                                    }
                                }
                            }

                            Button(
                                enabled = canSubmit,
                                onClick = {
                                    val res = store.submitRiderApplication(
                                        name = nameInput,
                                        phone = phoneInput,
                                        cnic = cnicInput,
                                        city = cityInput,
                                        vehicleNumber = vehicleInput,
                                        cnicCopy = cnicCopyChecked,
                                        drivingLicence = licenceChecked,
                                        vehicleRegistration = regChecked
                                    )
                                    onActionMessage(res.second, !res.first)
                                    localBanner = res.second to !res.first
                                    if (res.first) {
                                        nameInput = ""
                                        phoneInput = ""
                                        cnicInput = ""
                                        vehicleInput = ""
                                        cnicCopyChecked = false
                                        licenceChecked = false
                                        regChecked = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("rider_join_submit_btn")
                            ) {
                                Text("Submit application", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
