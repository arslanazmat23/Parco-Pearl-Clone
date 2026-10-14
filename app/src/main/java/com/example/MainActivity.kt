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
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.model.AppSurface
import com.example.bluegas.ui.components.AiAdvisorDialog
import com.example.bluegas.ui.components.DevInspectorDialog
import com.example.bluegas.ui.components.NotificationCenterDialog
import com.example.bluegas.ui.components.PersistentTopBar
import com.example.bluegas.ui.customer.CustomerAppScreen
import com.example.bluegas.ui.rider.RiderAppScreen
import com.example.bluegas.ui.staff.StaffPortalScreen
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
fun BlueGasAppRoot() {
    val store = remember { BlueGasStore() }
    val version by store.stateVersion.collectAsStateWithLifecycle()

    var showDevDrawer by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var showAiAdvisor by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            PersistentTopBar(
                store = store,
                onOpenDevDrawer = { showDevDrawer = true },
                onOpenNotifications = { showNotifications = true },
                onOpenAiAdvisor = { showAiAdvisor = true }
            )
        }
    ) { innerPadding ->
        // version triggers recomposition across all surfaces
        key(version) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (store.activeSurface) {
                    AppSurface.CUSTOMER -> CustomerAppScreen(store = store)
                    AppSurface.RIDER -> RiderAppScreen(store = store)
                    AppSurface.STAFF -> StaffPortalScreen(store = store)
                }
            }
        }
    }

    if (showDevDrawer) {
        DevInspectorDialog(store = store, onDismiss = { showDevDrawer = false })
    }

    if (showNotifications) {
        NotificationCenterDialog(store = store, onDismiss = { showNotifications = false })
    }

    if (showAiAdvisor) {
        AiAdvisorDialog(store = store, onDismiss = { showAiAdvisor = false })
    }
}
