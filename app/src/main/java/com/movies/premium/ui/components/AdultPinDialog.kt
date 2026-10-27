package com.movies.premium.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.movies.premium.ui.tv.focusRing

/**
 * The two PIN dialogs of the settings screen.
 *
 * The original shows two variants of the same dialog
 * (`com.app.mlounge.ui.components.e0.p`):
 *
 * - [Create] — "Set Adult PIN" / "Create a 4-digit PIN to protect adult
 *   content" — asks for the PIN twice, the second time as a password.
 * - [Verify] — "Enter PIN" / "Enter your PIN to enable adult content" — asks
 *   for the existing PIN once, as a password.
 *
 * The validation is the original's (`com.app.mlounge.ui.components.x`): the PIN
 * has to be exactly four characters long ("PIN must be 4 digits") and, in the
 * create variant, the two fields have to be equal ("PINs do not match"). The
 * original counts the characters, it does not check that they are digits.
 */
enum class AdultPinMode {
    /** The user sets the PIN for the first time. */
    Create,

    /** The user proves that they know the PIN. */
    Verify,
}

/**
 * The validation of the PIN.
 *
 * The original's check (`com.app.mlounge.ui.components.x`) is: the PIN has to
 * be exactly four characters long and — when the PIN is created — the two
 * fields have to be equal. The original counts the characters, it does not
 * check that they are digits.
 */
object AdultPinValidator {
    /** The number of characters a PIN has. */
    const val LENGTH = 4

    /** The error the original shows when the PIN is too short or too long. */
    const val ERROR_LENGTH = "PIN must be 4 digits"

    /** The error the original shows when the two fields differ. */
    const val ERROR_MISMATCH = "PINs do not match"

    /**
     * Checks the PIN.
     *
     * @param pin the PIN the user typed.
     * @param confirm the second field; null in the verify variant.
     * @return the error message, or null when the PIN is acceptable.
     */
    fun validate(pin: String, confirm: String? = null): String? = when {
        pin.length != LENGTH -> ERROR_LENGTH
        confirm != null && pin != confirm -> ERROR_MISMATCH
        else -> null
    }
}

/**
 * The PIN dialog.
 *
 * @param mode which of the original's two variants to show.
 * @param onDismiss called when the user cancels the dialog.
 * @param onConfirm called with the PIN the user typed. The caller decides what
 *   to do with it — the original stores it and switches the adult section on.
 */
@Composable
fun AdultPinDialog(
    mode: AdultPinMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val title = if (mode == AdultPinMode.Create) "Set Adult PIN" else "Enter PIN"
    val subtitle = if (mode == AdultPinMode.Create) {
        "Create a 4-digit PIN to protect adult content"
    } else {
        "Enter your PIN to enable adult content"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.focusRing(),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it; error = null },
                    singleLine = true,
                    label = { Text(if (mode == AdultPinMode.Create) "PIN" else "Enter PIN") },
                    visualTransformation = if (mode == AdultPinMode.Create) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mode == AdultPinMode.Create) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it; error = null },
                        singleLine = true,
                        label = { Text("Confirm PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // The original's own check: four characters, and — when the
                    // PIN is created — the two fields have to be equal.
                    val problem = AdultPinValidator.validate(
                        pin = pin,
                        confirm = if (mode == AdultPinMode.Create) confirm else null,
                    )
                    if (problem != null) {
                        error = problem
                        return@TextButton
                    }
                    onConfirm(pin)
                },
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
