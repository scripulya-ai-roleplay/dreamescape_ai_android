package com.example.dreamescape_ai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.dreamescape_ai.data.BackendConfig
import com.example.dreamescape_ai.ui.components.scripPanel
import com.example.dreamescape_ai.ui.theme.Dreamescape_aiTheme
import com.example.dreamescape_ai.ui.theme.ScripulyaText
import com.example.dreamescape_ai.ui.theme.ScripulyaTextDim
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.system.exitProcess

/**
 * Full-screen "Advanced settings" flow reachable from the Profile tab's Settings
 * gear. Currently exposes a single control: the backend base URL.
 *
 * Mirrors [ChatSettingsActivity]'s structure (ComponentActivity + Scaffold with a
 * back-arrow TopAppBar). The URL is persisted via [BackendConfig] and pushed live
 * via [DreamescapeApplication.applyBaseUrl]; because the generated API clients
 * cache the URL lazily per class, saving prompts a process restart.
 */
class AdvancedSettingsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Dreamescape_aiTheme {
                Scaffold(
                    modifier = Modifier.fillMaxWidth(),
                    topBar = {
                        TopAppBar(
                            title = { Text("Advanced settings") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    AdvancedSettingsScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf(BackendConfig.DEFAULT_BACKEND_BASE_URL) }
    var loaded by remember { mutableStateOf(BackendConfig.DEFAULT_BACKEND_BASE_URL) }
    var showRestartDialog by remember { mutableStateOf(false) }

    // Seed the field once with the persisted URL.
    LaunchedEffect(Unit) {
        val persisted = BackendConfig.baseUrlFlow(context.applicationContext).first()
        loaded = persisted
        text = persisted
    }

    val normalized = text.trim().trimEnd('/')
    val isValid = isValidBackendUrl(normalized)
    val canSave = isValid && normalized != loaded

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scripPanel(radius = 20.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Backend address",
                color = ScripulyaText,
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Base URL") },
                placeholder = { Text("http://10.66.66.2:8000") },
                singleLine = true,
                isError = text.isNotEmpty() && !isValid,
                supportingText = {
                    Text(
                        if (text.isNotEmpty() && !isValid) {
                            "Enter a full http(s):// address, e.g. http://10.66.66.2:8000"
                        } else {
                            "Full address including scheme and port, e.g. http://10.66.66.2:8000"
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            BackendConfig.setBaseUrl(context.applicationContext, normalized)
                            DreamescapeApplication.applyBaseUrl(normalized)
                            text = normalized
                            loaded = normalized
                            showRestartDialog = true
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
                OutlinedButton(
                    onClick = { text = BackendConfig.DEFAULT_BACKEND_BASE_URL },
                    enabled = normalized != BackendConfig.DEFAULT_BACKEND_BASE_URL,
                    modifier = Modifier.weight(1f)
                ) { Text("Reset to default") }
            }
        }

        Text(
            text = "Changing this re-points every API request at the new backend. " +
                "Because already-loaded clients keep the old address cached, the app " +
                "must restart for the change to take full effect.",
            color = ScripulyaTextDim,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(8.dp))
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestartDialog = false
                finishHost(context)
            },
            title = { Text("Restart to apply") },
            text = {
                Text("Backend address updated. Restart the app so all requests use it.")
            },
            confirmButton = {
                TextButton(onClick = { restartApp(context) }) {
                    Text("Restart now")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    finishHost(context)
                }) { Text("Later") }
            }
        )
    }
}

/** True iff [input] is a usable backend base URL (http(s) scheme + host). */
private fun isValidBackendUrl(input: String): Boolean {
    val parsed = input.toHttpUrlOrNull() ?: return false
    return parsed.scheme == "http" || parsed.scheme == "https"
}

/** Returns to the previous screen without restarting. */
private fun finishHost(context: Context) {
    (context as? Activity)?.finish()
}

/**
 * Relaunches the app's launcher activity and kills the current process so the
 * generated API clients drop their lazily-cached base URL and re-read it on the
 * next launch. Mirrors the ProcessPhoenix trick: schedule the relaunch first,
 * then exit so the cached clients are released.
 */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    (context as? Activity)?.finishAffinity()
    exitProcess(0)
}
