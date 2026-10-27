package com.movies.premium.ui.screens.services

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movies.premium.data.repository.ServiceId
import com.movies.premium.ui.viewmodel.ServiceUiState
import com.movies.premium.ui.viewmodel.ServicesViewModel
import com.movies.premium.ui.tv.focusRing

/**
 * The linked-accounts screen.
 *
 * The original's screen is `com.app.mlounge.ui.screens.services.p`; it lists the
 * eight services it can talk to and shows each one's account details once the user
 * has signed in. The same eight services, the same labels and the same sign-in
 * flow are reproduced here.
 */
@Composable
fun ServicesScreen(
    modifier: Modifier = Modifier,
    viewModel: ServicesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val simklClientId by viewModel.simklClientId.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<ServiceId?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Services",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        items(state.services, key = { it.service.name }) { service ->
            if (service.service == ServiceId.EASYNEWS) {
                EasyNewsCard(
                    state = service,
                    onSignIn = viewModel::connectEasynews,
                    onLogout = { viewModel.disconnect(ServiceId.EASYNEWS) },
                )
            } else {
                ServiceCard(
                    state = service,
                    onConnect = { pending = service.service },
                    onDisconnect = { viewModel.disconnect(service.service) },
                    simklClientId = if (service.service == ServiceId.SIMKL) simklClientId else null,
                    onSimklClientIdChange = { viewModel.setSimklClientId(it) },
                )
            }
        }
    }

    pending?.let { service ->
        CredentialDialog(
            service = service,
            onDismiss = { pending = null },
            onSubmit = { token ->
                viewModel.connect(service, token)
                pending = null
            },
        )
    }
}

/**
 * The EasyNews card.
 *
 * EasyNews is the one service the original signs in to with a **user name and
 * a password** rather than with a token (`com.app.mlounge.ui.screens.services.h`):
 *
 * - signed out: a "Username" field, a "Password" field with a visibility
 *   toggle, a "Sign In" button and a "Don't have an account? Sign up" link
 *   that opens `https://easynews.com/`,
 * - signed in: a "Username: \<name\>" row and a "Logout" button.
 *
 * The original never asks easynews.com whether the account works — the
 * "Connected" / "Not Connected" line is derived from the stored user name.
 */
@Composable
private fun EasyNewsCard(
    state: ServiceUiState,
    onSignIn: (String, String) -> Unit,
    onLogout: () -> Unit,
) {
    val service = state.service
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = service.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.connected) "Connected" else "Not Connected",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.connected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Text(
                text = service.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.connected) {
                state.account?.fields?.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = value, style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = onLogout) { Text("Logout") }
            } else {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = if (passwordVisible) {
                                    "Hide password"
                                } else {
                                    "Show password"
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onSignIn(username, password) },
                    enabled = username.isNotBlank(),
                ) {
                    Text("Sign In")
                }
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(service.website))
                        runCatching { context.startActivity(intent) }
                    },
                ) {
                    Text("Don't have an account? Sign up")
                }
            }
        }
    }
}

@Composable
private fun ServiceCard(
    state: ServiceUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    simklClientId: String? = null,
    onSimklClientIdChange: (String?) -> Unit = {},
) {
    val service = state.service
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = service.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.connected) {
                    Text(
                        text = "Connected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = service.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = service.website,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.account?.fields?.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = value, style = MaterialTheme.typography.bodySmall)
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (state.connected) {
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect") }
            } else {
                Button(onClick = onConnect, enabled = !state.validating) {
                    if (state.validating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text("Validating...")
                    } else {
                        Text(
                            if (service.usesApiKey) {
                                "${service.displayName} API Key"
                            } else {
                                "Sign In with ${service.displayName}"
                            },
                        )
                    }
                }
            }

            // SIMKL needs an application id of the user's own before the device-code
            // login can start. The original hard-codes its own; the replica does not
            // ship it, so the user registers one and pastes it here.
            if (service == ServiceId.SIMKL) {
                OutlinedTextField(
                    value = simklClientId.orEmpty(),
                    onValueChange = onSimklClientIdChange,
                    singleLine = true,
                    label = { Text("SIMKL client id") },
                    placeholder = { Text("Register at simkl.com/account/apiclients") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CredentialDialog(
    service: ServiceId,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.focusRing(),
        title = {
            Text(
                if (service.usesApiKey) {
                    "${service.displayName} API Key"
                } else {
                    "Sign In with ${service.displayName}"
                },
            )
        },
        text = {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                singleLine = true,
                label = { Text(if (service.usesApiKey) "API key" else "Token") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(token) },
                enabled = token.isNotBlank(),
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
