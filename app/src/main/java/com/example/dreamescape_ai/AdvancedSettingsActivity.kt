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

    var jwtText by remember { mutableStateOf(BackendConfig.DEFAULT_JWT_SECRET) }
    var jwtLoaded by remember { mutableStateOf(BackendConfig.DEFAULT_JWT_SECRET) }

    var minioText by remember { mutableStateOf(BackendConfig.DEFAULT_MINIO_BASE_URL) }
    var minioLoaded by remember { mutableStateOf(BackendConfig.DEFAULT_MINIO_BASE_URL) }

    // Seed the fields once with the persisted values.
    LaunchedEffect(Unit) {
        val persistedUrl = BackendConfig.baseUrlFlow(context.applicationContext).first()
        loaded = persistedUrl
        text = persistedUrl
        val persistedSecret = BackendConfig.jwtSecretFlow(context.applicationContext).first()
        jwtLoaded = persistedSecret
        jwtText = persistedSecret
        val persistedMinio = BackendConfig.minioBaseUrlFlow(context.applicationContext).first()
        minioLoaded = persistedMinio
        minioText = persistedMinio
    }

    val normalized = text.trim().trimEnd('/')
    val isValid = isValidBackendUrl(normalized)
    val canSave = isValid && normalized != loaded

    val jwtNormalized = jwtText.trim()
    val jwtValid = jwtNormalized.isNotEmpty()
    val jwtCanSave = jwtValid && jwtNormalized != jwtLoaded

    // Blank is valid (disables the override); otherwise must be a full http(s)://host.
    val minioNormalized = minioText.trim().trimEnd('/')
    val minioValid = minioNormalized.isEmpty() || isValidBackendUrl(minioNormalized)
    val minioCanSave = minioValid && minioNormalized != minioLoaded

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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scripPanel(radius = 20.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "MinIO address",
                color = ScripulyaText,
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = minioText,
                onValueChange = { minioText = it },
                label = { Text("Image storage URL") },
                placeholder = { Text("http://10.66.66.2:9000") },
                singleLine = true,
                isError = minioText.isNotEmpty() && !minioValid,
                supportingText = {
                    Text(
                        if (minioText.isNotEmpty() && !minioValid) {
                            "Enter a full http(s):// address, e.g. http://10.66.66.2:9000"
                        } else {
                            "Where the device can reach MinIO (scheme + host + port). " +
                                "Leave blank to use the URLs the backend returns."
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
                            BackendConfig.setMinioBaseUrl(context.applicationContext, minioNormalized)
                            DreamescapeApplication.applyMinioBaseUrl(minioNormalized)
                            minioText = minioNormalized
                            minioLoaded = minioNormalized
                        }
                    },
                    enabled = minioCanSave,
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
                OutlinedButton(
                    onClick = { minioText = BackendConfig.DEFAULT_MINIO_BASE_URL },
                    enabled = minioNormalized != BackendConfig.DEFAULT_MINIO_BASE_URL,
                    modifier = Modifier.weight(1f)
                ) { Text("Reset to default") }
            }
        }

        Text(
            text = "Image URLs come from the backend pointing at its own MinIO host, " +
                "which may be unreachable from your device (e.g. under a VPN). Setting " +
                "this rewrites only the host of each image URL — the path and any " +
                "presigned signature are preserved. Applies to images immediately, no " +
                "restart needed.",
            color = ScripulyaTextDim,
            style = MaterialTheme.typography.bodySmall
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .scripPanel(radius = 20.dp)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "JWT signing secret",
                color = ScripulyaText,
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = jwtText,
                onValueChange = { jwtText = it },
                label = { Text("Secret key") },
                placeholder = { Text(BackendConfig.DEFAULT_JWT_SECRET) },
                singleLine = true,
                isError = jwtText.isNotEmpty() && !jwtValid,
                supportingText = {
                    Text(
                        if (jwtText.isNotEmpty() && !jwtValid) {
                            "Secret cannot be empty."
                        } else {
                            "Shared HMAC secret used to self-sign each token. Must match the backend's JWT_SECRET_KEY."
                        }
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
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
                            BackendConfig.setJwtSecret(context.applicationContext, jwtNormalized)
                            jwtText = jwtNormalized
                            jwtLoaded = jwtNormalized
                            showRestartDialog = true
                        }
                    },
                    enabled = jwtCanSave,
                    modifier = Modifier.weight(1f)
                ) { Text("Save") }
                OutlinedButton(
                    onClick = { jwtText = BackendConfig.DEFAULT_JWT_SECRET },
                    enabled = jwtNormalized != BackendConfig.DEFAULT_JWT_SECRET,
                    modifier = Modifier.weight(1f)
                ) { Text("Reset to default") }
            }
        }

        Text(
            text = "Every request self-signs a fresh token with this secret. If it " +
                "doesn't match the backend's JWT_SECRET_KEY, requests fail with 401. " +
                "A change takes effect after restart.",
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
                Text("Advanced settings updated. Restart the app so all changes take full effect.")
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
