package eu.eurise.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * I simboli delle app della suite nelle icone (tavola IconeStili, approvata il 13 novembre 2026): un disegno per servizio,
 * tratto 2,35, i punti più grossi. Gli stessi tracciati di `scripts/icone.py`, che fa le icone degli APK: qui servono alla
 * Scrivania (sezioni di Eurise e app di sistema con lo stesso servizio). Per le icone dell'interfaccia resta [EuriseIcons].
 */
object SuiteSymbols {
    const val Stroke = 2.35f

    val desk by lazy { symbol("desk", "M4,11 l8,-6.5 l8,6.5 V20 a1,1 0 0 1 -1,1 H5 a1,1 0 0 1 -1,-1 z M9.5,21 v-5.5 h5 V21") }
    val calendar by lazy {
        symbol("calendar", "M5,5 h14 a2,2 0 0 1 2,2 v12 a2,2 0 0 1 -2,2 H5 a2,2 0 0 1 -2,-2 V7 a2,2 0 0 1 2,-2 z M3,10 h18 M8,3 v4 M16,3 v4",
            dots = "M15.5,15.5 h0.01", dotStroke = 3.55f)
    }
    val tasks by lazy { symbol("tasks", "M5,4 h14 a1,1 0 0 1 1,1 v14 a1,1 0 0 1 -1,1 H5 a1,1 0 0 1 -1,-1 V5 a1,1 0 0 1 1,-1 z M8,12.3 l2.8,2.8 l5.4,-5.6") }
    val notes by lazy { symbol("notes", "M14.5,4.5 l5,5 L9.5,19.5 H4.5 v-5 z M12.5,6.5 l5,5 M4,21 h16") }
    val contacts by lazy {
        symbol("contacts", "M6.5,3 H17 a2,2 0 0 1 2,2 V19 a2,2 0 0 1 -2,2 H6.5 Z M4.5,7.5 h3 M4.5,12 h3 M4.5,16.5 h3 " +
            "M10.5,10 a2.3,2.3 0 1 0 4.6,0 a2.3,2.3 0 1 0 -4.6,0 M9.2,17 a3.6,3.6 0 0 1 7.2,0")
    }
    val clock by lazy {
        symbol("clock", "M5,13 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0 M12,9.5 v3.8 l2.5,1.6 M4,6.5 l3,-2.5 M20,6.5 l-3,-2.5 " +
            "M7.5,19.5 l-1.5,2 M16.5,19.5 l1.5,2")
    }
    val files by lazy { symbol("files", "M3,7 a2,2 0 0 1 2,-2 h4 l2,2.5 h8 a2,2 0 0 1 2,2 V17 a2,2 0 0 1 -2,2 H5 a2,2 0 0 1 -2,-2 z M3,10.5 h18") }
    val scanner by lazy {
        symbol("scanner", "M4,8 V5 a1,1 0 0 1 1,-1 h3 M16,4 h3 a1,1 0 0 1 1,1 v3 M20,16 v3 a1,1 0 0 1 -1,1 h-3 " +
            "M8,20 H5 a1,1 0 0 1 -1,-1 v-3 M3,12 h18")
    }
    val read by lazy { symbol("read", "M12,6.5 C10,5 7,4.5 3.5,5 V18.5 C7,18 10,18.5 12,20 C14,18.5 17,18 20.5,18.5 V5 C17,4.5 14,5 12,6.5 Z M12,6.5 V20") }
    val office by lazy { symbol("office", "M6,3 h8 l5,5 v12 a1,1 0 0 1 -1,1 H6 a1,1 0 0 1 -1,-1 V4 a1,1 0 0 1 1,-1 z M14,3 v5 h5 M8.5,12.5 h7 M8.5,16 h7 M8.5,9 h3") }
    val chat by lazy {
        symbol("chat", "M3.5,4 h11 a1.5,1.5 0 0 1 1.5,1.5 v6 a1.5,1.5 0 0 1 -1.5,1.5 H8 l-4.5,3.5 z M19,8.5 h0.5 " +
            "a1.5,1.5 0 0 1 1.5,1.5 v9.5 l-3.5,-2.5 H11 a1.5,1.5 0 0 1 -1.5,-1.5 v-1")
    }
    val messages by lazy {
        symbol("messages", "M4,5 h16 v11 H9 l-5,4 z", dots = "M8.5,10.5 h0.01 M12,10.5 h0.01 M15.5,10.5 h0.01", dotStroke = 3.35f)
    }
    val phone by lazy {
        symbol("phone", "M6.6,3.5 h2.6 l1.5,4 -2,1.3 a11,11 0 0 0 6.5,6.5 l1.3,-2 4,1.5 v2.6 a2,2 0 0 1 -2.2,2 " +
            "A17,17 0 0 1 4.6,5.7 a2,2 0 0 1 2,-2.2 z M14.5,3 a6.5,6.5 0 0 1 6.5,6.5 M14.5,6.5 a3,3 0 0 1 3,3")
    }
    val mail by lazy { symbol("mail", "M4,5.5 h16 a1,1 0 0 1 1,1 v11 a1,1 0 0 1 -1,1 H4 a1,1 0 0 1 -1,-1 v-11 a1,1 0 0 1 1,-1 z M3.5,6.5 l8.5,6.5 l8.5,-6.5") }
    val gallery by lazy {
        symbol("gallery", "M6.2,4.5 H17.8 A3.2,3.2 0 0 1 21,7.7 V16.3 A3.2,3.2 0 0 1 17.8,19.5 H6.2 A3.2,3.2 0 0 1 3,16.3 V7.7 " +
            "A3.2,3.2 0 0 1 6.2,4.5 Z M14.1,9.2 a1.7,1.7 0 1 0 3.4,0 a1.7,1.7 0 1 0 -3.4,0 M3.6,17.2 L8.6,12.3 l4,3.9 l2.6,-2.5 l5.2,4.9")
    }
    val password by lazy {
        symbol("password", "M3,12 a4.5,4.5 0 1 0 9,0 a4.5,4.5 0 1 0 -9,0 M12,12 H21 M18.3,12 v3.2 M15.4,12 v2.4",
            dots = "M7.5,12 h0.01", dotStroke = 2.95f)
    }
    val keyboard by lazy {
        symbol("keyboard", "M5.5,6 H18.5 A3,3 0 0 1 21.5,9 V15.5 A3,3 0 0 1 18.5,18.5 H5.5 A3,3 0 0 1 2.5,15.5 V9 A3,3 0 0 1 5.5,6 Z M9,15.3 h6",
            dots = "M6.6,9.8 h0.01 M10.2,9.8 h0.01 M13.8,9.8 h0.01 M17.4,9.8 h0.01 M6.6,12.8 h0.01 M17.4,12.8 h0.01", dotStroke = 2.95f)
    }
    val maps by lazy { symbol("maps", "M3.5,6.5 l5,-2 l7,2.5 l5,-2 v13 l-5,2 l-7,-2.5 l-5,2 z M8.5,4.5 v13.5 M15.5,7 v13.5") }
    val browser by lazy {
        symbol("browser", "M3.4,12 a8.6,8.6 0 1 0 17.2,0 a8.6,8.6 0 1 0 -17.2,0 M15.6,8.4 l-2.2,5 l-5,2.2 l2.2,-5 z M12,3.4 v1.6 M12,19 v1.6")
    }
    val weather by lazy {
        symbol("weather", "M8,3 v1.5 M3.3,7.8 h1.5 M4.6,4.4 l1,1 M11.4,4.4 l-1,1 M5.3,10.6 a3.2,3.2 0 0 1 5.6,-3.2 " +
            "M8.5,20 h9 a3.5,3.5 0 0 0 0.4,-7 a5,5 0 0 0 -9.5,1.2 a2.9,2.9 0 0 0 0.1,5.8 z")
    }
    val calculator by lazy {
        symbol("calculator", "M6,3 h12 a1,1 0 0 1 1,1 v16 a1,1 0 0 1 -1,1 H6 a1,1 0 0 1 -1,-1 V4 a1,1 0 0 1 1,-1 z M8,6.5 h8 v3 H8 z",
            dots = "M8.6,13.3 h0.01 M12,13.3 h0.01 M15.4,13.3 h0.01 M8.6,17 h0.01 M12,17 h0.01 M15.4,17 h0.01", dotStroke = 3.15f)
    }
    val translator by lazy {
        symbol("translator", "M3.5,12 a8.5,8.5 0 1 0 17,0 a8.5,8.5 0 1 0 -17,0 M3.5,12 h17 M12,3.5 c2.5,2.3 3.6,5.2 3.6,8.5 " +
            "s-1.1,6.2 -3.6,8.5 M12,3.5 c-2.5,2.3 -3.6,5.2 -3.6,8.5 s1.1,6.2 3.6,8.5")
    }
    val alfred by lazy {
        symbol("alfred", "M10 10.4C8.1 8.6 6.2 7.4 4.8 7.6 3.5 7.8 3 9.5 3 12s.5 4.2 1.8 4.4c1.4.2 3.3-1 5.2-2.8 " +
            "M14 10.4c1.9-1.8 3.8-3 5.2-2.8 1.3.2 1.8 1.9 1.8 4.4s-.5 4.2-1.8 4.4c-1.4.2-3.3-1-5.2-2.8 " +
            "M11.7 9.7h.6a1.7 1.7 0 0 1 1.7 1.7v1.2a1.7 1.7 0 0 1-1.7 1.7h-.6a1.7 1.7 0 0 1-1.7-1.7v-1.2a1.7 1.7 0 0 1 1.7-1.7z")
    }

    private fun symbol(name: String, path: String, dots: String? = null, dotStroke: Float = Stroke): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(addPathNodes(path), stroke = SolidColor(Color.Black), strokeLineWidth = Stroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            if (dots != null) addPath(addPathNodes(dots), stroke = SolidColor(Color.Black), strokeLineWidth = dotStroke,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
        }.build()
}
