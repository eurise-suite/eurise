package eu.eurise.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icone a tratto della suite (griglia 24 × 24), disegnate come nei render. Lo spessore del tratto
 * cambia per la voce selezionata: 2,3 di base, 2,7 se attiva.
 */
object EuriseIcons {
    const val Stroke = 2.3f
    const val StrokeSelected = 2.7f

    fun today(stroke: Float = Stroke) = icon("today", stroke,
        circle(12f, 12f, 4f),
        "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")

    fun calendar(stroke: Float = Stroke) = icon("calendar", stroke,
        "M6.5 5h11a3 3 0 0 1 3 3v9.5a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z",
        "M3.5 10h17M8 3v4M16 3v4")

    fun note(stroke: Float = Stroke) = icon("note", stroke,
        "M15 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9z", "M15 3v6h6", "M7 13h7M7 17h4")

    fun task(stroke: Float = Stroke) = icon("task", stroke, circle(12f, 12f, 9f), "M8 12l3 3 5-6")

    fun plus(stroke: Float = 2.4f) = icon("plus", stroke, "M12 5v14M5 12h14")

    fun chevronLeft(stroke: Float = 2.4f) = icon("left", stroke, "M15 6l-6 6 6 6")

    fun chevronRight(stroke: Float = 2.4f) = icon("right", stroke, "M9 6l6 6-6 6")

    fun pen(stroke: Float = Stroke) = icon("pen", stroke, "M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16z")

    fun star(stroke: Float = Stroke) = icon("star", stroke, "M12 3.8l2.5 5.2 5.7.8-4.1 4 1 5.7L12 16.8l-5.1 2.7 1-5.7-4.1-4 5.7-.8z")

    fun chat(stroke: Float = Stroke) = icon("chat", stroke,
        "M5.5 5h13A2.5 2.5 0 0 1 21 7.5v7a2.5 2.5 0 0 1-2.5 2.5H10.5L6 20.5V17h-.5A2.5 2.5 0 0 1 3 14.5v-7A2.5 2.5 0 0 1 5.5 5z")

    fun folder(stroke: Float = Stroke) = icon("folder", stroke,
        "M3.5 7.5a2 2 0 0 1 2-2h4l2 2h7a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z")

    fun tools(stroke: Float = Stroke) = icon("tools", stroke,
        "M14.7 6.3a4 4 0 0 0-5.4 5.4L4 17v3h3l5.3-5.3a4 4 0 0 0 5.4-5.4l-2.4 2.4-2.6-.6-.6-2.6z")

    fun grid(stroke: Float = Stroke) = icon("grid", stroke,
        "M5.8 4h2.9A1.8 1.8 0 0 1 10.5 5.8v2.9a1.8 1.8 0 0 1-1.8 1.8H5.8A1.8 1.8 0 0 1 4 8.7V5.8A1.8 1.8 0 0 1 5.8 4z",
        "M15.3 4h2.9A1.8 1.8 0 0 1 20 5.8v2.9a1.8 1.8 0 0 1-1.8 1.8h-2.9a1.8 1.8 0 0 1-1.8-1.8V5.8A1.8 1.8 0 0 1 15.3 4z",
        "M5.8 13.5h2.9a1.8 1.8 0 0 1 1.8 1.8v2.9A1.8 1.8 0 0 1 8.7 20H5.8A1.8 1.8 0 0 1 4 18.2v-2.9a1.8 1.8 0 0 1 1.8-1.8z",
        "M15.3 13.5h2.9a1.8 1.8 0 0 1 1.8 1.8v2.9a1.8 1.8 0 0 1-1.8 1.8h-2.9a1.8 1.8 0 0 1-1.8-1.8v-2.9a1.8 1.8 0 0 1 1.8-1.8z")

    fun search(stroke: Float = Stroke) = icon("search", stroke, circle(11f, 11f, 6.5f), "M20 20l-4.2-4.2")

    fun cloud(stroke: Float = 2f) = icon("cloud", stroke, "M7 18h10a4 4 0 0 0 .6-7.95A5.8 5.8 0 0 0 6.4 9.6 4.3 4.3 0 0 0 7 18z")

    fun sun(stroke: Float = 2f) = icon("sun", stroke, circle(12f, 12f, 4f),
        "M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4L6 18M18 6l1.4-1.4")

    fun rain(stroke: Float = 2f) = icon("rain", stroke,
        "M7 15h10a4 4 0 0 0 .6-7.95A5.8 5.8 0 0 0 6.4 6.6 4.3 4.3 0 0 0 7 15z", "M8.5 18.5l-1 2M12.5 18.5l-1 2M16.5 18.5l-1 2")

    fun snow(stroke: Float = 2f) = icon("snow", stroke, "M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6-9")

    fun fog(stroke: Float = 2f) = icon("fog", stroke, "M4 9h16M6 13h12M4 17h16")

    fun storm(stroke: Float = 2f) = icon("storm", stroke,
        "M7 14h10a4 4 0 0 0 .6-7.95A5.8 5.8 0 0 0 6.4 5.6 4.3 4.3 0 0 0 7 14z", "M12.5 15l-2 3.5h3l-2 3.5")

    fun pause(stroke: Float = 2.6f) = icon("pause", stroke, "M9 6v12M15 6v12")

    fun play(stroke: Float = 2.4f) = icon("play", stroke, "M8 5.5l10 6.5-10 6.5z")

    fun next(stroke: Float = 2.2f) = icon("next", stroke, "M6 6l8 6-8 6zM18 6v12")

    fun previous(stroke: Float = 2.2f) = icon("previous", stroke, "M18 6l-8 6 8 6zM6 6v12")

    // Simboli delle app di sistema, per le icone a tema della Scrivania.
    fun phone(stroke: Float = Stroke) = icon("phone", stroke,
        "M6.6 3.5h2.6l1.5 4-2 1.3a11 11 0 0 0 6.5 6.5l1.3-2 4 1.5v2.6a2 2 0 0 1-2.2 2A17 17 0 0 1 4.6 5.7a2 2 0 0 1 2-2.2z")

    fun message(stroke: Float = Stroke) = icon("message", stroke, "M4 5h16v11H9l-5 4z")

    /** Tastierino del telefono: tre file di tre punti e lo zero. */
    fun dialpad(stroke: Float = 2.6f) = icon("dialpad", stroke,
        "M6 5h.01M12 5h.01M18 5h.01M6 10.5h.01M12 10.5h.01M18 10.5h.01M6 16h.01M12 16h.01M18 16h.01M12 21h.01")

    /** Chiudi la chiamata: la cornetta abbassata. */
    fun hangup(stroke: Float = Stroke) = icon("hangup", stroke, "M3 14.5c5-4.5 13-4.5 18 0l-2 2.5-3.5-1.2v-2.6a10 10 0 0 0-7 0v2.6L5 17z")

    fun micOff(stroke: Float = Stroke) = icon("micOff", stroke, "M9 3.5h6v11H9z", "M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5v3M4 4l16 16")

    /** Blocca: cerchio barrato (numeri bloccati, disturbatori). */
    fun block(stroke: Float = Stroke) = icon("block", stroke, circle(12f, 12f, 8.5f), "M6 6l12 12")

    /** Chiamata persa: cornetta con la freccia di ritorno. */
    fun callMissed(stroke: Float = Stroke) = icon("callMissed", stroke,
        "M6.6 3.5h2.6l1.5 4-2 1.3a11 11 0 0 0 6.5 6.5l1.3-2 4 1.5v2.6a2 2 0 0 1-2.2 2A17 17 0 0 1 4.6 5.7a2 2 0 0 1 2-2.2z", "M15 3l5 5M20 3v5h-5")

    fun backspace(stroke: Float = Stroke) = icon("backspace", stroke, "M8 5h12v14H8l-5-7z", "M11 9l6 6M17 9l-6 6")

    fun camera(stroke: Float = Stroke) = icon("camera", stroke, "M4 8h3.5L9 5.5h6L16.5 8H20v11H4z", circle(12f, 13f, 3.5f))

    fun image(stroke: Float = Stroke) = icon("image", stroke, "M4 5h16v14H4z", "M4 16l5-5 4 4 2.5-2.5L20 15", circle(15.5f, 9f, 1.5f))

    fun globe(stroke: Float = Stroke) = icon("globe", stroke, circle(12f, 12f, 9f), "M3 12h18", "M12 3c2.8 3 2.8 15 0 18M12 3c-2.8 3-2.8 15 0 18")

    fun mail(stroke: Float = Stroke) = icon("mail", stroke, "M3.5 6h17v12h-17z", "M3.5 7l8.5 6 8.5-6")

    fun gear(stroke: Float = Stroke) = icon("gear", stroke, circle(12f, 12f, 3f),
        "M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1")

    fun person(stroke: Float = Stroke) = icon("person", stroke, circle(12f, 8.5f, 3.5f), "M5 20c1-4 4-5.5 7-5.5s6 1.5 7 5.5")

    fun clock(stroke: Float = Stroke) = icon("clock", stroke, circle(12f, 12f, 9f), "M12 7v5l3 2")

    fun flag(stroke: Float = Stroke) = icon("flag", stroke, "M5 21V4h11l-1.5 4L16 12H5")

    fun repeat(stroke: Float = Stroke) = icon("repeat", stroke, "M17 3l3 3-3 3", "M4 11V9a3 3 0 0 1 3-3h13", "M7 21l-3-3 3-3", "M20 13v2a3 3 0 0 1-3 3H4")

    fun lists(stroke: Float = Stroke) = icon("lists", stroke, "M9 6h11M9 12h11M9 18h11", "M4.5 6h.01M4.5 12h.01M4.5 18h.01")

    fun alarm(stroke: Float = Stroke) = icon("alarm", stroke, circle(12f, 13f, 7.5f), "M12 9.5V13l2 1.5", "M4 5l3-2M20 5l-3-2")

    fun calculator(stroke: Float = Stroke) = icon("calculator", stroke, "M6 3h12v18H6z", "M9 7h6",
        "M9 12h.01M12 12h.01M15 12h.01M9 16h.01M12 16h.01M15 16h.01")

    fun music(stroke: Float = Stroke) = icon("music", stroke, "M9 18V6l10-2v12", circle(6.5f, 18f, 2.5f), circle(16.5f, 16f, 2.5f))

    fun map(stroke: Float = Stroke) = icon("map", stroke, "M9 4l6 2 5-2v14l-5 2-6-2-5 2V6z", "M9 4v14M15 6v14")

    fun shield(stroke: Float = Stroke) = icon("shield", stroke, "M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6z")

    fun bag(stroke: Float = Stroke) = icon("bag", stroke, "M5 8h14l-1 12H6z", "M9 8V6a3 3 0 0 1 6 0v2")

    fun mic(stroke: Float = Stroke) = icon("mic", stroke, "M9 4a3 3 0 0 1 6 0v7a3 3 0 0 1-6 0z", "M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21")

    fun wifi(stroke: Float = Stroke) = icon("wifi", stroke, "M4.5 9.5a11 11 0 0 1 15 0M7.5 12.8a6.8 6.8 0 0 1 9 0M10.5 16a2.5 2.5 0 0 1 3 0M12 19.2h.01")

    fun bluetooth(stroke: Float = Stroke) = icon("bluetooth", stroke, "M7 7l10 10-5 4V3l5 4L7 17")

    fun torch(stroke: Float = Stroke) = icon("torch", stroke, "M8 3h8v4l-2 3v11h-4V10L8 7z", "M8 7h8")

    fun moon(stroke: Float = Stroke) = icon("moon", stroke, "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")

    fun rotate(stroke: Float = Stroke) = icon("rotate", stroke, "M20 11a8 8 0 0 0-14.5-4.5M4 4v3.5h3.5M4 13a8 8 0 0 0 14.5 4.5M20 20v-3.5h-3.5")

    fun chart(stroke: Float = Stroke) = icon("chart", stroke, "M4 17l5-5 4 3 7-8", "M15 7h5v5")

    fun news(stroke: Float = Stroke) = icon("news", stroke, "M5 4h11v16H6a2 2 0 0 1-2-2V5a1 1 0 0 1 1-1z", "M16 8h3v10a2 2 0 0 1-2 2", "M8 8h5M8 12h5M8 16h3")

    fun heart(stroke: Float = Stroke) = icon("heart", stroke, "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z")

    fun home(stroke: Float = Stroke) = icon("home", stroke, "M4 11l8-6.5 8 6.5", "M6 9.5V20h12V9.5", "M10 20v-5h4v5")

    fun pdf(stroke: Float = Stroke) = icon("pdf", stroke, "M7 3.5h7l4 4v13H7z", "M14 3.5v4h4", "M10.5 12v6", "M10.5 12h2.5a1.75 1.75 0 0 1 0 3.5h-2.5")
    fun office(stroke: Float = Stroke) = icon("office", stroke, "M4.5 4.5h15v15h-15z", "M4.5 9.5h15", "M9.5 9.5v10")
    fun scanner(stroke: Float = Stroke) = icon("scanner", stroke, "M4 8V4h4", "M16 4h4v4", "M20 16v4h-4", "M8 20H4v-4", "M4 12h16")
    fun euro(stroke: Float = Stroke) = icon("euro", stroke, "M18 6.8A6.5 6.5 0 0 0 7.5 12a6.5 6.5 0 0 0 10.5 5.2", "M4.5 10.2h9", "M4.5 13.8h8")
    fun compass(stroke: Float = Stroke) = icon("compass", stroke, circle(12f, 12f, 9f), "M15.5 8.5l-2 5-5 2 2-5z")
    fun spark(stroke: Float = Stroke) = icon("spark", stroke, "M11 5l1.9 5.1L18 12l-5.1 1.9L11 19l-1.9-5.1L4 12l5.1-1.9z", "M18.5 3.5v3", "M17 5h3")
    fun browser(stroke: Float = Stroke) = icon("browser", stroke, "M3.5 5.5h17v13h-17z", "M3.5 9.5h17", "M6.5 7.5h.01", "M9.5 7.5h.01")
    fun work(stroke: Float = Stroke) = icon("work", stroke, "M4 8h16v11H4z", "M9 8V6a1.5 1.5 0 0 1 1.5-1.5h3A1.5 1.5 0 0 1 15 6v2", "M4 13h16")

    fun cart(stroke: Float = Stroke) = icon("cart", stroke, "M3 4h2.5l2 11h10l2-8H7", circle(9.5f, 19f, 1.3f), circle(16.5f, 19f, 1.3f))

    fun ball(stroke: Float = Stroke) = icon("ball", stroke, circle(12f, 12f, 8.5f), "M3.5 12h17", "M12 3.5c3 2.5 3 14.5 0 17M12 3.5c-3 2.5-3 14.5 0 17")

    fun plane(stroke: Float = Stroke) = icon("plane", stroke, "M3 13l18-8-6 16-3-7z", "M12 14l9-9")

    fun book(stroke: Float = Stroke) = icon("book", stroke, "M12 6c-2-1.5-5-2-8-1.5V19c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V4.5C17 4 14 4.5 12 6z", "M12 6v14.5")

    fun bank(stroke: Float = Stroke) = icon("bank", stroke, "M3.5 9.5L12 4l8.5 5.5z", "M5.5 10v7M10 10v7M14 10v7M18.5 10v7", "M3.5 20h17")

    fun train(stroke: Float = Stroke) = icon("train", stroke, "M7 3.5h10a2 2 0 0 1 2 2V15a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3V5.5a2 2 0 0 1 2-2z", "M5 11h14", "M8 21l2-3M16 21l-2-3", "M9 14.5h.01M15 14.5h.01")

    // Segni zodiacali, tratto della suite.
    fun zodiac(index: Int, stroke: Float = 2f): ImageVector = when (index) {
        0 -> icon("z0", stroke, "M12 20V8", "M12 8c0-3-2-4.5-4-4.5S4 5 4 7.5 6 11 7 11", "M12 8c0-3 2-4.5 4-4.5s4 1.5 4 4-2 3.5-3 3.5")
        1 -> icon("z1", stroke, circle(12f, 15f, 5f), "M5 4c1.5 3 4 5 7 5s5.5-2 7-5")
        2 -> icon("z2", stroke, "M6 4h12M6 20h12M9 4v16M15 4v16")
        3 -> icon("z3", stroke, circle(7f, 9f, 2.5f), circle(17f, 15f, 2.5f), "M4.5 9C5 5 9 4 13 4s6 1.5 7 3", "M19.5 15c-.5 4-4.5 5-8.5 5S5 18.5 4 17")
        4 -> icon("z4", stroke, circle(7f, 15f, 3f), "M10 15V8a4 4 0 0 1 8 0c0 3-3 4-3 7a3 3 0 0 0 5 2")
        5 -> icon("z5", stroke, "M4 6v12", "M4 9a2.5 2.5 0 0 1 5 0v9", "M9 9a2.5 2.5 0 0 1 5 0v8c0 2 1 3 3 3", "M14 13c2-2 5-1 5 2s-3 5-6 6")
        6 -> icon("z6", stroke, "M4 20h16", "M4 16h5a4.5 4.5 0 1 1 6 0h5")
        7 -> icon("z7", stroke, "M4 6v12", "M4 9a2.5 2.5 0 0 1 5 0v9", "M9 9a2.5 2.5 0 0 1 5 0v9c0 1 1 2 2 2h4", "M18 18l2 2-2 2")
        8 -> icon("z8", stroke, "M5 19L19 5", "M12 5h7v7", "M8 12l4 4")
        9 -> icon("z9", stroke, "M4 6l3 10 3-10v9a4 4 0 1 0 4-4c3 0 5 2 5 5s-2 4-4 4")
        10 -> icon("z10", stroke, "M3 10l3-3 3 3 3-3 3 3 3-3 3 3", "M3 16l3-3 3 3 3-3 3 3 3-3 3 3")
        else -> icon("z11", stroke, "M6 4c4 4 4 12 0 16", "M18 4c-4 4-4 12 0 16", "M6 12h12")
    }

    fun spoon(stroke: Float = Stroke) = icon("spoon", stroke, "M12 13v8", "M9 7.5a3 4.5 0 1 0 6 0a3 4.5 0 1 0-6 0")

    fun minus(stroke: Float = 2.4f) = icon("minus", stroke, "M5 12h14")

    /** Catena: due elementi legati (per esempio schede alte uguali). */
    fun link(stroke: Float = Stroke) = icon("link", stroke, "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1.5 1.5", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1.5-1.5")

    fun reply(stroke: Float = Stroke) = icon("reply", stroke, "M10 7l-5.5 5 5.5 5", "M5 12h8.5a6 6 0 0 1 6 6v1")

    fun trash(stroke: Float = Stroke) = icon("trash", stroke, "M4.5 7h15", "M9.5 7V4.5h5V7", "M6.5 7l1 13h9l1-13")
    fun scan(stroke: Float = Stroke) = icon("scan", stroke, "M4 8V5.5A1.5 1.5 0 0 1 5.5 4H8M16 4h2.5A1.5 1.5 0 0 1 20 5.5V8M20 16v2.5a1.5 1.5 0 0 1-1.5 1.5H16M8 20H5.5A1.5 1.5 0 0 1 4 18.5V16M7 12h10")
    fun download(stroke: Float = Stroke) = icon("download", stroke, "M12 4v11M7.5 10.5 12 15l4.5-4.5M5 20h14")
    fun upload(stroke: Float = Stroke) = icon("upload", stroke, "M12 18V8M7.5 12.5 12 8l4.5 4.5")
    fun sort(stroke: Float = Stroke) = icon("sort", stroke, "M4 7h16M7 12h10M10 17h4")
    fun flash(stroke: Float = Stroke) = icon("flash", stroke, "M13 3 5 13.5h6L10 21l8-10.5h-6z")

    fun read(stroke: Float = Stroke) = icon("read", stroke, "M3.5 12.5l4 4 8-9", "M12.5 16.5l1 0 8-9")

    /** Chiave: codici di accesso. */
    fun key(stroke: Float = Stroke) = icon("key", stroke, "M14.5 9.5a4 4 0 1 0-3.4 3.9L13 15.5h2v2h2v2h3v-3l-5.6-5.6", "M8.5 8.5h.01")

    /** Pacco: consegne in arrivo. */
    fun parcel(stroke: Float = Stroke) = icon("parcel", stroke, "M3.5 7.5 12 3l8.5 4.5v9L12 21l-8.5-4.5z", "M3.5 7.5 12 12l8.5-4.5", "M12 12v9")

    /** Maschera: letto di nascosto, senza far sapere al mittente che lo si è letto. */
    fun mask(stroke: Float = Stroke) = icon(
        "mask", stroke,
        "M2.5 9.5c0-1.6 1.4-2.7 3.2-2.7 2.4 0 4.2 1.4 6.3 1.4s3.9-1.4 6.3-1.4c1.8 0 3.2 1.1 3.2 2.7 0 3.7-2.3 6.7-5.1 6.7-1.9 0-3-1.7-4.4-1.7s-2.5 1.7-4.4 1.7c-2.8 0-5.1-3-5.1-6.7z",
        "M6.5 11.2l2.6.4", "M17.5 11.2l-2.6.4",
    )

    fun send(stroke: Float = Stroke) = icon("send", stroke, "M4 12L20 4l-4 16-4-7z", "M12 13l8-9")

    fun play2(stroke: Float = Stroke) = icon("play2", stroke, "M8 5.5v13l10.5-6.5z")

    // Simboli in più per le categorie della barra.
    fun game(stroke: Float = Stroke) = icon("game", stroke, "M7 8h10a4 4 0 0 1 4 4v1a3.5 3.5 0 0 1-6.3 2.1L14 14h-4l-.7 1.1A3.5 3.5 0 0 1 3 13v-1a4 4 0 0 1 4-4z", "M8 10.5v3M6.5 12h3", "M15.5 11h.01M17.5 13h.01")
    fun car(stroke: Float = Stroke) = icon("car", stroke, "M5 16V12l2-5h10l2 5v4z", "M4 16h16v2.5H4z", "M7.5 13h.01M16.5 13h.01")
    fun paw(stroke: Float = Stroke) = icon("paw", stroke, circle(7f, 9f, 1.6f), circle(11f, 6.5f, 1.6f), circle(15f, 6.5f, 1.6f), circle(18f, 10f, 1.6f), "M12.5 11.5c-3 0-5.5 3.5-5.5 6a2.5 2.5 0 0 0 3.5 2.2l1.5-.6a1.5 1.5 0 0 1 1 0l1.5.6A2.5 2.5 0 0 0 18 17.5c0-2.5-2.5-6-5.5-6z")
    fun gift(stroke: Float = Stroke) = icon("gift", stroke, "M4 9h16v3H4z", "M5.5 12v8h13v-8", "M12 9v11", "M12 9c-1.5-3-5-3.5-5-1.2C7 9 9.5 9 12 9zM12 9c1.5-3 5-3.5 5-1.2C17 9 14.5 9 12 9z")
    fun school(stroke: Float = Stroke) = icon("school", stroke, "M2.5 9.5 12 5l9.5 4.5L12 14z", "M6.5 11.5v4c1.5 1.5 3.5 2.2 5.5 2.2s4-.7 5.5-2.2v-4", "M21.5 9.5v5")
    fun medical(stroke: Float = Stroke) = icon("medical", stroke, "M9.5 4h5v5.5H20v5h-5.5V20h-5v-5.5H4v-5h5.5z")
    fun fitness(stroke: Float = Stroke) = icon("fitness", stroke, "M3 12h18", "M6 8v8M3.5 9.5v5M18 8v8M20.5 9.5v5")
    fun leaf(stroke: Float = Stroke) = icon("leaf", stroke, "M5 19c0-8 5-13 14-14 0 9-5 14-13 14", "M5 19l7-7")
    fun code(stroke: Float = Stroke) = icon("code", stroke, "M8.5 7 3.5 12l5 5", "M15.5 7l5 5-5 5", "M13.5 5l-3 14")
    fun palette(stroke: Float = Stroke) = icon("palette", stroke, "M12 3.5a8.5 8.5 0 0 0 0 17c1.2 0 1.8-.8 1.8-1.7 0-1.2-1-1.5-1-2.6 0-1 .8-1.7 1.8-1.7h2.2A3.7 3.7 0 0 0 20.5 11 7.8 7.8 0 0 0 12 3.5z", "M8 11h.01M10 7.5h.01M14.5 7.5h.01")
    fun lock(stroke: Float = Stroke) = icon("lock", stroke, "M5.5 11h13v9h-13z", "M8 11V8a4 4 0 0 1 8 0v3")
    fun coffee(stroke: Float = Stroke) = icon("coffee", stroke, "M5 9h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5z", "M16 10.5h1.5a2.5 2.5 0 0 1 0 5H16", "M8.5 3.5v2.5M12 3.5v2.5")
    fun baby(stroke: Float = Stroke) = icon("baby", stroke, circle(12f, 13f, 7f), "M9.5 12h.01M14.5 12h.01", "M10 15.5c1.2 1 2.8 1 4 0", "M12 6c-.5-1.5.5-2.5 1.5-2.5")

    fun drag(stroke: Float = 2.4f) = icon("drag", stroke, "M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01")

    // Note
    fun check(stroke: Float = 2.6f) = icon("check", stroke, "M5 12l4 4 10-10")
    fun close(stroke: Float = 2.4f) = icon("close", stroke, "M6 6l12 12M18 6L6 18")
    fun undo(stroke: Float = 2.2f) = icon("undo", stroke, "M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
    fun more(stroke: Float = 2.8f) = icon("more", stroke, "M5 12h.01M12 12h.01M19 12h.01")
    fun checklist(stroke: Float = 2.2f) = icon("checklist", stroke, "M3.5 4h6v6h-6z", "M5 7l1 1 2-2", "M13 7h7M13 17h7", "M3.5 14h6v6h-6z")
    fun bullets(stroke: Float = 2.2f) = icon("bullets", stroke, "M9 6h11M9 12h11M9 18h11", "M4.5 6h.01M4.5 12h.01M4.5 18h.01")
    fun numbers(stroke: Float = 2.2f) = icon("numbers", stroke, "M10 6h10M10 12h10M10 18h10", "M4 5l1.5-1v5", "M3.8 14.2a1.6 1.6 0 0 1 2.7 1.2c0 1-2.7 2.6-2.7 2.6h2.8")
    fun table(stroke: Float = 2.2f) = icon("table", stroke, "M3.5 4.5h17v15h-17z", "M3.5 10h17M3.5 15h17M10 4.5v15")
    fun divider(stroke: Float = 2.2f) = icon("divider", stroke, "M4 12h16", "M8 6h8M8 18h8")
    fun notebook(stroke: Float = Stroke) = icon("notebook", stroke, "M6 3.5h11a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5H6z", "M6 3.5v17M3.5 8H6M3.5 12H6M3.5 16H6M10 8h5")
    fun pin(stroke: Float = Stroke) = icon("pin", stroke, "M12 17v4", "M8 3h8l-1 6 3 3H6l3-3z")
    fun share(stroke: Float = Stroke) = icon("share", stroke, "M12 15V4", "M7.5 8.5L12 4l4.5 4.5", "M5 13v5.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V13")
    fun bell(stroke: Float = Stroke) = icon("bell", stroke, "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0")
    fun heading(stroke: Float = 2.2f) = icon("heading", stroke, "M6 5v14M18 5v14M6 12h12")
    fun redo(stroke: Float = 2.2f) = icon("redo", stroke, "M15 14l5-5-5-5", "M20 9H9.5a5.5 5.5 0 0 0 0 11H13")

    fun crop(stroke: Float = Stroke) = icon("crop", stroke, "M6 3v13a2 2 0 0 0 2 2h13", "M3 6h13a2 2 0 0 1 2 2v13")
    fun sliders(stroke: Float = Stroke) = icon("sliders", stroke, "M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f))
    fun filters(stroke: Float = Stroke) = icon("filters", stroke, circle(9f, 10f, 5f), circle(15f, 10f, 5f), circle(12f, 15f, 5f))
    fun text(stroke: Float = Stroke) = icon("text", stroke, "M5 6V4.5h14V6M12 4.5v15M9 19.5h6")
    fun flip(stroke: Float = Stroke) = icon("flip", stroke, "M12 3v18", "M8 7L3 12l5 5zM16 7l5 5-5 5z")
    fun contactless(stroke: Float = Stroke) = icon("contactless", stroke, "M7 9a4.5 4.5 0 0 1 0 6", "M10.5 6.5a8 8 0 0 1 0 11", "M14 4a11.5 11.5 0 0 1 0 16", "M17.5 2.5a14 14 0 0 1 0 19")
    fun pip(stroke: Float = Stroke) = icon("pip", stroke, "M3.5 5.5h17v13h-17z", "M12 12h6v4.5h-6z")
    fun scissors(stroke: Float = Stroke) = icon("scissors", stroke, circle(6f, 7f, 2.5f), circle(6f, 17f, 2.5f), "M8 8.5L20 16M8 15.5L20 8")
    fun volume(stroke: Float = Stroke) = icon("volume", stroke, "M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M16 9a4 4 0 0 1 0 6M18.5 6.5a7.5 7.5 0 0 1 0 11")
    fun volumeOff(stroke: Float = Stroke) = icon("volumeOff", stroke, "M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M16 9.5l5 5M21 9.5l-5 5")

    /** Apri fuori (in un'altra app): freccia che esce dal riquadro. */
    fun external(stroke: Float = Stroke) = icon("external", stroke, "M14 4h6v6", "M20 4l-9 9", "M19 14v4.5a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 18.5v-11A1.5 1.5 0 0 1 6.5 6H10")
    fun refresh(stroke: Float = Stroke) = icon("refresh", stroke, "M20 12a8 8 0 1 1-2.3-5.6", "M20 4v4h-4")

    // Strumenti di disegno
    fun pencil(stroke: Float = 2.1f) = icon("pencil", stroke, "M15 4l5 5L9 20H4v-5z", "M12.5 6.5l5 5")
    fun marker(stroke: Float = 2.1f) = icon("marker", stroke, "M14 3h6v6L10 19H4v-6z", "M4 21h6")
    fun highlighter(stroke: Float = 2.1f) = icon("highlighter", stroke, "M14 4l6 6-8 8H6v-6z", "M4 21h9")
    fun eraser(stroke: Float = 2.1f) = icon("eraser", stroke, "M8 20l-4.5-4.5a2 2 0 0 1 0-2.8L13 3.2a2 2 0 0 1 2.8 0l5 5a2 2 0 0 1 0 2.8L12 20z", "M8 20h12M9 9l6 6")
    fun ruler(stroke: Float = 2.1f) = icon("ruler", stroke, "M3 16.5L16.5 3 21 7.5 7.5 21z", "M7 12.5l2 2M10 9.5l2 2M13 6.5l2 2")

    fun copy(stroke: Float = 2.2f) = icon("copy", stroke, "M10.5 8h7A2.5 2.5 0 0 1 20 10.5v7a2.5 2.5 0 0 1-2.5 2.5h-7A2.5 2.5 0 0 1 8 17.5v-7A2.5 2.5 0 0 1 10.5 8z", "M16 8V5.5A1.5 1.5 0 0 0 14.5 4h-9A1.5 1.5 0 0 0 4 5.5v9A1.5 1.5 0 0 0 5.5 16H8")
    fun eye(stroke: Float = 2.2f) = icon("eye", stroke, "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", circle(12f, 12f, 2.8f))
    fun eyeOff(stroke: Float = 2.2f) = icon("eyeOff", stroke, "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", "M4 4l16 16")

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

    private val cache = HashMap<String, ImageVector>()

    private fun icon(name: String, stroke: Float, vararg paths: String): ImageVector =
        cache.getOrPut("$name@$stroke") {
            ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
                .apply {
                    paths.forEach { d ->
                        addPath(
                            pathData = addPathNodes(d),
                            stroke = SolidColor(Color.Black),
                            strokeLineWidth = stroke,
                            strokeLineCap = StrokeCap.Round,
                            strokeLineJoin = StrokeJoin.Round,
                        )
                    }
                }
                .build()
        }
}
