package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MainAppUi
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AssistantViewModel
import com.example.util.NetworkMonitor

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        val viewModel: AssistantViewModel = viewModel()
        val networkMonitor = remember { NetworkMonitor(this@MainActivity) }
        val isOnline by networkMonitor.isOnline.collectAsState(initial = true)
        
        var showUpdateDialog by remember { mutableStateOf(true) }

        if (showUpdateDialog) {
            AlertDialog(
                onDismissRequest = { /* Must update to continue */ },
                title = { Text("Update Available") },
                text = { Text("A new version of Ava Assistant is available. We've added new admin controls and more features. Please update to continue.") },
                confirmButton = {
                    Button(onClick = { showUpdateDialog = false }) {
                        Text("Update Now")
                    }
                },
                dismissButton = {
                    Button(onClick = { finish() }) {
                        Text("Exit App")
                    }
                }
            )
        }

        if (!isOnline) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Offline") },
                text = { Text("You are currently offline. Please connect to the internet to use the online features.") },
                confirmButton = {
                    Button(onClick = { finish() }) {
                        Text("Exit App")
                    }
                }
            )
        }

        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          MainAppUi(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
        }
      }
    }
  }
}

