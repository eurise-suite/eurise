package eu.eurise.desktop.app

import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * Eurise per il computer. Senza argomenti apre la finestra e tiene acceso il Ponte; con un comando fa quello che fa
 * `eurise-ponte` (così Firefox, i tasti rapidi e gli script usano lo stesso programma: `eurise firefox`,
 * `eurise manda nota …`, `eurise installa-firefox`).
 */
fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        eu.eurise.desktop.main(args)
        return
    }
    val ponte = PonteState()
    ponte.start()
    application {
        val state = rememberWindowState(size = DpSize(1360.dp, 860.dp))
        val dark = remember { SystemTheme.dark() }
        Window(
            onCloseRequest = ::exitApplication,
            state = state,
            title = "Eurise",
            icon = painterResource("eurise.png"),
        ) {
            EuriseTheme(dark) { EuriseApp(ponte) }
        }
    }
}
