package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bluegas.data.*
import com.example.bluegas.model.AppSurface
import com.example.bluegas.ui.components.AiAdvisorDialog
import com.example.bluegas.ui.components.DevInspectorDialog
import com.example.bluegas.ui.components.NotificationCenterDialog
import com.example.bluegas.ui.components.PersistentTopBar
import com.example.bluegas.ui.customer.CustomerAppScreen
import com.example.bluegas.ui.rider.RiderAppScreen
import com.example.bluegas.ui.staff.StaffPortalScreen
import com.example.bluegas.viewmodel.BlueGasViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                BlueGasAppRoot()
            }
        }
    }
}

@Composable
fun BlueGasAppRoot(viewModel: BlueGasViewModel = viewModel()) {
    // Single source of truth StateFlow for the entire app
    val appState by viewModel.appState.collectAsStateWithLifecycle()

    var showDevDrawer by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showAiAdvisor by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5_000)
            viewModel.store.processTimers()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            PersistentTopBar(
                store = viewModel.store,
                activeSurface = appState.activeSurface,
                isRiderOffline = appState.isRiderOffline,
                onOpenDevDrawer = { showDevDrawer = true },
                onOpenNotifications = { showNotifications = true },
                onOpenAiAdvisor = { showAiAdvisor = true }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (appState.activeSurface) {
                AppSurface.CUSTOMER -> CustomerAppScreen(store = viewModel.store)
                AppSurface.RIDER -> RiderAppScreen(store = viewModel.store)
                AppSurface.STAFF -> StaffPortalScreen(store = viewModel.store)
            }
        }
    }

    if (showDevDrawer) {
        DevInspectorDialog(store = viewModel.store, onDismiss = { showDevDrawer = false })
    }

    if (showNotifications) {
        NotificationCenterDialog(store = viewModel.store, onDismiss = { showNotifications = false })
    }

    if (showAiAdvisor) {
        AiAdvisorDialog(store = viewModel.store, onDismiss = { showAiAdvisor = false })
    }
}
