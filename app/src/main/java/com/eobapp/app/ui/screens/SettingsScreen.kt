package com.eobapp.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.eobapp.app.BuildConfig
import com.eobapp.app.MainViewModel
import com.eobapp.app.TestResult
import com.eobapp.app.ui.theme.LocalEobColors

/**
 * Where the scheduler is and how to get past Cloudflare Access. Shown on first launch, and from
 * the gear on Home afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val c = LocalEobColors.current
    val test by viewModel.test.collectAsState()

    var host by remember { mutableStateOf(viewModel.settings.getHost()) }
    var clientId by remember { mutableStateOf(viewModel.settings.getClientId()) }
    var clientSecret by remember { mutableStateOf(viewModel.settings.getClientSecret()) }
    var showSecret by remember { mutableStateOf(false) }

    // A result from an earlier visit would be misleading next time the screen opens.
    DisposableEffect(Unit) { onDispose { viewModel.clearTest() } }

    Scaffold(
        containerColor = c.ground,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.ground),
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "The address of your eob_scheduler dashboard, and the Cloudflare Access " +
                    "service token that lets the app through the login.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.mute
            )

            OutlinedTextField(
                value = host,
                onValueChange = { host = it; viewModel.clearTest() },
                label = { Text("Heater address") },
                placeholder = { Text("heater.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                supportingText = { Text("Without https:// — the app always uses HTTPS") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it; viewModel.clearTest() },
                label = { Text("Service token Client ID") },
                placeholder = { Text("xxxxxxxx.access") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = clientSecret,
                onValueChange = { clientSecret = it; viewModel.clearTest() },
                label = { Text("Service token Client Secret") },
                singleLine = true,
                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = {
                    TextButton(onClick = { showSecret = !showSecret }) {
                        Text(if (showSecret) "Hide" else "Show")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { viewModel.testConnection(host, clientId, clientSecret) },
                    enabled = host.isNotBlank() && test !is TestResult.Running
                ) { Text("Test connection") }
                Button(
                    onClick = {
                        viewModel.saveSettings(host, clientId, clientSecret)
                        onSaved()
                    },
                    enabled = host.isNotBlank()
                ) { Text("Save") }
            }

            when (val t = test) {
                is TestResult.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
                }
                is TestResult.Ok -> Text(t.summary, color = c.good, style = MaterialTheme.typography.bodyMedium)
                is TestResult.Failed -> Text(t.message, color = c.bad, style = MaterialTheme.typography.bodyMedium)
                null -> {}
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = c.card,
                border = BorderStroke(1.dp, c.line)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Getting a service token", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "1. Cloudflare Zero Trust → Access controls → Service credentials → " +
                            "Service Tokens → Create. Copy the Client ID and Secret (the secret " +
                            "is shown only once).\n" +
                            "2. Open your heater Access application → Policies → add a policy " +
                            "with Action \"Service Auth\" and Include → Service Token → that token.\n" +
                            "3. Paste both here and tap Test connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.mute
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "EOBApp ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = c.mute
            )
        }
    }
}
