package eu.eurise.core.designsystem.component

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import eu.eurise.core.designsystem.R
import eu.eurise.core.designsystem.icon.EuriseIcons

/*
 * L'occhio dei campi password (regola del 24 novembre 2026): ogni campo che nasconde ciò che si scrive ha accanto l'occhio
 * per vederlo. Si usa così:
 *
 *     var shown by remember { mutableStateOf(false) }
 *     OutlinedTextField(…, visualTransformation = secretText(shown), trailingIcon = { SecretEye(shown) { shown = !shown } })
 */

/** Nasconde il testo finché [shown] è falso. */
fun secretText(shown: Boolean): VisualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation()

/** L'occhio: aperto per mostrare, barrato per nascondere di nuovo. */
@Composable
fun SecretEye(shown: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            if (shown) EuriseIcons.eyeOff() else EuriseIcons.eye(),
            contentDescription = stringResource(if (shown) R.string.ds_secret_hide else R.string.ds_secret_show),
        )
    }
}
