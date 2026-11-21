package eu.eurise.feature.launcher.apps

import androidx.compose.ui.graphics.Color

// Generato da scripts/icone_kotlin.py da scripts/icone_disegni.json (tela «Icone della suite», pagina Icone, approvata il
// 16 novembre 2026): non modificare a mano.

/**
 * I disegni nuovi delle app della suite, su una griglia di 48 (la tela li mostra in un riquadro da -7,5 a 55,5).
 * Parti: «t» campitura tenue, «s» tratto sfumato, «S» tratto pieno più spesso, «p» pieno sfumato, «h» tratto e «hf»
 * pieno nel colore del fondo (incavi). Ogni disegno ha i colori delle tre serie: bolle chiare, bolle scure, colorate.
 */
internal object SuiteDrawings {
    class Part(val kind: String, val path: String)

    /** Tratto da [s0] a [s1], pieni da [p0] a [p1], campitura [tint], incavi [hole], spessore del tratto [stroke]. */
    class Palette(val s0: Color, val s1: Color, val p0: Color, val p1: Color, val tint: Color, val hole: Color, val stroke: Float)

    /**
     * [round] è il fondo della serie colorata (tre tappe, posate su [stops]), [glow] la luce in alto a sinistra;
     * [vertical] fa salire il colore dritto dal basso invece che un po' in diagonale.
     */
    class Drawing(
        val parts: List<Part>,
        val light: Palette,
        val dark: Palette,
        val color: Palette,
        val round: List<Color>,
        val glow: Color,
        val vertical: Boolean = false,
        val stops: List<Float> = listOf(0f, 0.45f, 1f),
    )

    val all: Map<String, Drawing> = mapOf(
        "Agenda" to Drawing(
            listOf(
            Part("t", "M15 8 H35 Q39 8 39 12 V36 Q39 40 35 40 H15 Q11 40 11 36 V12 Q11 8 15 8 Z"),
            Part("s", "M15 8 H35 Q39 8 39 12 V36 Q39 40 35 40 H15 Q11 40 11 36 V12 Q11 8 15 8 Z"),
            Part("S", "M7.5 15 H14 M7.5 22 H14 M7.5 29 H14"),
            Part("p", "M30 8 V16 L32.5 13.8 L35 16 V8 Z"),
            Part("p", "M25 15 Q25.96 22.04 33 23 Q25.96 23.96 25 31 Q24.04 23.96 17 23 Q24.04 22.04 25 15 Z"),
            Part("s", "M18.5 35 H31.5"),
            ),
            light = Palette(Color(0xFFEF6658), Color(0xFF9D3258), Color(0xFFF57A6E), Color(0xFFAF3862), Color(0x29F4685A), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFFBC6C0), Color(0xFFE97FA5), Color(0xFFFABBB5), Color(0xFFE66D98), Color(0x2BF8A49C), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFFDE5048), 2.4f),
            round = listOf(Color(0xFFF67C6E), Color(0xFFF4685A), Color(0xFFC93A72)),
            glow = Color(0xFFF89A8E),
        ),
        "Alfred" to Drawing(
            listOf(
            Part("p", "M24 4.5 C31.5 4.5 36 10.5 36 18.5 C36 26 39 32.5 38 38 C37 41.8 31.5 43.5 24 43.5 C16.5 43.5 11 41.8 10 38 C9 32.5 12 26 12 18.5 C12 10.5 16.5 4.5 24 4.5 Z M12.5 24 C9.5 27 7 31 6.5 35 C9.5 33.5 11.5 31 12.5 28.5 Z M35.5 24 C38.5 27 41 31 41.5 35 C38.5 33.5 36.5 31 35.5 28.5 Z"),
            Part("hf", "M24 18 C30.5 18 33.5 24 33.5 30 C33.5 36 30 41 24 41 C18 41 14.5 36 14.5 30 C14.5 24 17.5 18 24 18 Z M20.6 9.9 A1.6 1.6 0 1 0 20.6 13.1 A1.6 1.6 0 1 0 20.6 9.9 Z M27.4 9.9 A1.6 1.6 0 1 0 27.4 13.1 A1.6 1.6 0 1 0 27.4 9.9 Z"),
            Part("hf", "M22.4 14.2 L24 16.8 L25.6 14.2 Z"),
            Part("p", "M23 25.1 C20.6 22.9 18 21.7 16.6 22.1 C15.4 22.5 15 23.9 15 25.5 C15 27.1 15.4 28.5 16.6 28.9 C18 29.3 20.6 28.1 23 25.9 Z M25 25.1 C27.4 22.9 30 21.7 31.4 22.1 C32.6 22.5 33 23.9 33 25.5 C33 27.1 32.6 28.5 31.4 28.9 C30 29.3 27.4 28.1 25 25.9 Z M24 22.7 Q24.34 25.16 26.8 25.5 Q24.34 25.84 24 28.3 Q23.66 25.84 21.2 25.5 Q23.66 25.16 24 22.7 Z"),
            ),
            light = Palette(Color(0xFFA9266D), Color(0xFF402963), Color(0xFFB64180), Color(0xFF472D6F), Color(0x29AC276F), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFDFADC8), Color(0xFF8C75B0), Color(0xFFDA9EBE), Color(0xFF7C61A5), Color(0x2BCD7DA9), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF972262), 2.4f),
            round = listOf(Color(0xFFB43D7D), Color(0xFFAC276F), Color(0xFF50337D)),
            glow = Color(0xFFC76C9D),
        ),
        "Attività" to Drawing(
            listOf(
            Part("t", "M39 5 C29 6 17 14 13 30 C12 33 11.5 36 12 38 C15 36 18 34.5 21 33.5 C31 29.5 38 20 39 5 Z"),
            Part("s", "M39 5 C29 6 17 14 13 30 C12 33 11.5 36 12 38 C15 36 18 34.5 21 33.5 C31 29.5 38 20 39 5 Z"),
            Part("S", "M37 7.5 C29 16 20 27 10 41"),
            Part("p", "M37 33 Q37.6 37.4 42 38 Q37.6 38.6 37 43 Q36.4 38.6 32 38 Q36.4 37.4 37 33 Z"),
            Part("s", "M14 43 C20 40.5 26 44 31 40.5"),
            ),
            light = Palette(Color(0xFF6E9F2C), Color(0xFF297016), Color(0xFF81AD46), Color(0xFF2E7D19), Color(0x2970A22D), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFC9DCAF), Color(0xFF76BC63), Color(0xFFBFD5A0), Color(0xFF62B34D), Color(0x2BA9C781), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF638F28), 2.4f),
            round = listOf(Color(0xFF7EAB42), Color(0xFF70A22D), Color(0xFF348D1C)),
            glow = Color(0xFF9EC070),
        ),
        "Browser" to Drawing(
            listOf(
            Part("p", "M10 22 C10 13 16 8 24 8 C32 8 38 13 38 22 V32 C38 38 32 42 24 42 C16 42 10 38 10 32 Z M12 13 L11 5 L18.5 9.5 Z M36 13 L37 5 L29.5 9.5 Z"),
            Part("hf", "M18 15.6 A5.4 5.4 0 1 0 18 26.4 A5.4 5.4 0 1 0 18 15.6 Z M30 15.6 A5.4 5.4 0 1 0 30 26.4 A5.4 5.4 0 1 0 30 15.6 Z"),
            Part("p", "M18 18.8 A2.2 2.2 0 1 0 18 23.2 A2.2 2.2 0 1 0 18 18.8 Z M30 17.6 Q30.41 20.59 33.4 21 Q30.41 21.41 30 24.4 Q29.59 21.41 26.6 21 Q29.59 20.59 30 17.6 Z"),
            Part("h", "M18 34.5 L20 32.5 L22 34.5 M26 34.5 L28 32.5 L30 34.5"),
            ),
            light = Palette(Color(0xFF1295DB), Color(0xFF002E9E), Color(0xFF2EA4E3), Color(0xFF0033B0), Color(0x291298DF), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFA5D8F3), Color(0xFF4C7BEB), Color(0xFF94D1F1), Color(0xFF3368E8), Color(0x2B71C1EC), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF1086C4), 2.4f),
            round = listOf(Color(0xFF2AA2E2), Color(0xFF1298DF), Color(0xFF003AC7)),
            glow = Color(0xFF5EB9E9),
        ),
        "Calcolatrice" to Drawing(
            listOf(
            Part("t", "M15 6.5 A8.5 8.5 0 1 0 15 23.5 A8.5 8.5 0 1 0 15 6.5 Z M33 6.5 A8.5 8.5 0 1 0 33 23.5 A8.5 8.5 0 1 0 33 6.5 Z M15 24.5 A8.5 8.5 0 1 0 15 41.5 A8.5 8.5 0 1 0 15 24.5 Z M33 24.5 A8.5 8.5 0 1 0 33 41.5 A8.5 8.5 0 1 0 33 24.5 Z"),
            Part("S", "M15 10.5 V19.5 M10.5 15 H19.5 M28.5 15 H37.5 M10.5 33 H19.5"),
            Part("p", "M15 26.900000000000002 A1.7 1.7 0 1 0 15 30.3 A1.7 1.7 0 1 0 15 26.900000000000002 Z M15 35.699999999999996 A1.7 1.7 0 1 0 15 39.1 A1.7 1.7 0 1 0 15 35.699999999999996 Z M33 27.2 Q33.7 32.3 38.8 33 Q33.7 33.7 33 38.8 Q32.3 33.7 27.2 33 Q32.3 32.3 33 27.2 Z"),
            ),
            light = Palette(Color(0xFF465171), Color(0xFF21345E), Color(0xFF5D6884), Color(0xFF253A69), Color(0x29475373), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFB9BECA), Color(0xFF6D80AA), Color(0xFFACB2C0), Color(0xFF596E9E), Color(0x2B9198AB), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF3E4965), 2.4f),
            round = listOf(Color(0xFF596481), Color(0xFF475373), Color(0xFF294176)),
            glow = Color(0xFF828AA0),
        ),
        "Calendario" to Drawing(
            listOf(
            Part("t", "M14 9 H34 Q39 9 39 14 V28 L28 39 H14 Q9 39 9 34 V14 Q9 9 14 9 Z"),
            Part("s", "M14 9 H34 Q39 9 39 14 V28 M28 39 H14 Q9 39 9 34 V14 Q9 9 14 9"),
            Part("p", "M39 28 C32.5 27.5 28 31.5 28 39 C30.5 34.5 34.5 31 39 28 Z"),
            Part("p", "M21 16 Q21.96 23.04 29 24 Q21.96 24.96 21 32 Q20.04 24.96 13 24 Q20.04 23.04 21 16 Z"),
            Part("S", "M17 5.5 V10 M31 5.5 V10"),
            ),
            light = Palette(Color(0xFF16C9B5), Color(0xFF1D4E9D), Color(0xFF32D3C1), Color(0xFF2157AF), Color(0x2916CDB9), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFA6ECE4), Color(0xFF6A9AE9), Color(0xFF96E8E0), Color(0xFF558CE6), Color(0x2B73E1D5), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF13B4A3), 2.4f),
            round = listOf(Color(0xFF2DD2C0), Color(0xFF16CDB9), Color(0xFF2562C5)),
            glow = Color(0xFF61DDCF),
        ),
        "Chat" to Drawing(
            listOf(
            Part("t", "M19 8 H25 Q29 8 29 12 V16 Q29 20 25 20 H24.5 L22 23.2 L19.5 20 H19 Q15 20 15 16 V12 Q15 8 19 8 Z"),
            Part("s", "M19 8 H25 Q29 8 29 12 V16 Q29 20 25 20 H24.5 L22 23.2 L19.5 20 H19 Q15 20 15 16 V12 Q15 8 19 8 Z"),
            Part("p", "M22 10.6 Q22.41 13.59 25.4 14 Q22.41 14.41 22 17.4 Q21.59 14.41 18.6 14 Q21.59 13.59 22 10.6 Z"),
            Part("p", "M10.5 26 H33.5 V29 C33.5 35.5 28.5 40 22 40 C15.5 40 10.5 35.5 10.5 29 Z"),
            Part("s", "M33.5 28 C37.2 28 39 30.1 38.4 32.5 C37.8 34.9 35.4 35.4 32.6 34.5"),
            ),
            light = Palette(Color(0xFF1D98DC), Color(0xFF764620), Color(0xFF39A7E4), Color(0xFF834E24), Color(0x291E9BE0), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFAAD9F3), Color(0xFFC2926D), Color(0xFF9AD2F1), Color(0xFFB98358), Color(0x2B78C3EC), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF5A6286), 2.4f),
            round = listOf(Color(0xFF1E9BE0), Color(0xFF5A6E9A), Color(0xFFA8642E)),
            glow = Color(0xFF5EB9E9),
            vertical = true,
            stops = listOf(0f, 0.38f, 0.78f),
        ),
        "Contatti" to Drawing(
            listOf(
            Part("p", "M16.5 8.0 A5.5 5.5 0 1 0 16.5 19.0 A5.5 5.5 0 1 0 16.5 8.0 Z"),
            Part("p", "M31.5 8.0 A5.5 5.5 0 1 0 31.5 19.0 A5.5 5.5 0 1 0 31.5 8.0 Z"),
            Part("p", "M24 42 C14 36 7.5 30 7.5 24.5 C7.5 21.3 10 19.2 13.2 20 C17 21 20.2 24 24 27.2 C27.8 24 31 21 34.8 20 C38 19.2 40.5 21.3 40.5 24.5 C40.5 30 34 36 24 42 Z"),
            Part("hf", "M24 28.7 Q24.46 32.04 27.8 32.5 Q24.46 32.96 24 36.3 Q23.54 32.96 20.2 32.5 Q23.54 32.04 24 28.7 Z"),
            ),
            light = Palette(Color(0xFFD54F74), Color(0xFF9F3126), Color(0xFFDE6686), Color(0xFFB1372B), Color(0x29D95176), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFF1BDCB), Color(0xFFEB7E73), Color(0xFFEEB1C1), Color(0xFFE96B5F), Color(0x2BE897AD), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFFBF4768), 2.4f),
            round = listOf(Color(0xFFDD6284), Color(0xFFD95176), Color(0xFFC83E30)),
            glow = Color(0xFFE589A2),
        ),
        "Drive" to Drawing(
            listOf(
            Part("t", "M15 26 C12 26 10 23.6 10 21 C10 18 12.5 16 15.5 16.5 C16.5 12 20 9.5 24 9.5 C28.5 9.5 32 12.5 32.5 16.5 C35.5 16.5 38 18.5 38 21.5 C38 24 36 26 33 26 Z"),
            Part("s", "M15 26 C12 26 10 23.6 10 21 C10 18 12.5 16 15.5 16.5 C16.5 12 20 9.5 24 9.5 C28.5 9.5 32 12.5 32.5 16.5 C35.5 16.5 38 18.5 38 21.5 C38 24 36 26 33 26 Z"),
            Part("p", "M24 14.1 Q24.53 17.97 28.4 18.5 Q24.53 19.03 24 22.9 Q23.47 19.03 19.6 18.5 Q23.47 17.97 24 14.1 Z"),
            Part("p", "M11.5 27 H36.5 Q41 27 41 31.5 V36.5 Q41 41 36.5 41 H11.5 Q7 41 7 36.5 V31.5 Q7 27 11.5 27 Z"),
            Part("hf", "M20.6 32.5 H27.4 Q29 32.5 29 34.1 V34.1 Q29 35.7 27.4 35.7 H20.6 Q19 35.7 19 34.1 V34.1 Q19 32.5 20.6 32.5 Z"),
            ),
            light = Palette(Color(0xFFDB6511), Color(0xFF6D2411), Color(0xFFE3792E), Color(0xFF7A2813), Color(0x29DF6711), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFF3C5A5), Color(0xFFBA705D), Color(0xFFF1BB94), Color(0xFFB05C46), Color(0x2BECA470), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFFC45B0F), 2.4f),
            round = listOf(Color(0xFFE27629), Color(0xFFDF6711), Color(0xFF892D15)),
            glow = Color(0xFFE9985D),
        ),
        "Email" to Drawing(
            listOf(
            Part("t", "M21 10.5 A12.5 12.5 0 1 0 21 35.5 A12.5 12.5 0 1 0 21 10.5 Z"),
            Part("s", "M21 10.5 A12.5 12.5 0 1 0 21 35.5 A12.5 12.5 0 1 0 21 10.5 Z"),
            Part("S", "M24.5 26 A3.6 3.6 0 1 1 25 22 V27 C25 29 27.5 29.6 29 28.2 C32.4 24.8 31.4 17 24.6 14.2 C17 11.2 10 17 10.5 24.6 C11 32.4 18.6 36.4 25.4 34"),
            Part("p", "M5.5 39 C5.5 37.4 7.5 36.6 10.5 36.6 H33.5 C36 36.6 37.4 35 37.9 32.2 L39 26 C39.3 24.2 41 23.5 42.3 24.6 C43.5 25.6 43.5 27.5 42.9 29.5 L41 36 C40 39 37.5 40.6 34.5 40.6 H8.5 C6.5 40.6 5.5 40.1 5.5 39 Z"),
            Part("s", "M40.4 24.4 L37.6 17.6 M42.6 24.6 L44 19"),
            Part("p", "M37 12.399999999999999 Q37.38 15.219999999999999 40.2 15.6 Q37.38 15.98 37 18.8 Q36.62 15.98 33.8 15.6 Q36.62 15.219999999999999 37 12.399999999999999 Z"),
            ),
            light = Palette(Color(0xFFE0521A), Color(0xFFA36A05), Color(0xFFE86936), Color(0xFFB67705), Color(0x29E5541B), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFF5BEA8), Color(0xFFF0B751), Color(0xFFF3B298), Color(0xFFEDAD39), Color(0x2BEF9876), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFFCA4A18), 2.4f),
            round = listOf(Color(0xFFE86532), Color(0xFFE5541B), Color(0xFFCD8606)),
            glow = Color(0xFFED8B64),
        ),
        "Galleria" to Drawing(
            listOf(
            Part("t", "M8 30 C13.5 24 19.5 24 25.5 29.5 C30 26 35 25.5 40 27.5 V34 Q40 40 34 40 H14 Q8 40 8 34 Z"),
            Part("p", "M8 35 C14.5 30 21.5 30.5 28 35 C32 33 36 32.8 40 34.5 Q40 40 34 40 H14 Q8 40 8 35 Z"),
            Part("s", "M14 8 H34 Q40 8 40 14 V34 Q40 40 34 40 H14 Q8 40 8 34 V14 Q8 8 14 8 Z"),
            Part("p", "M29.5 10.8 Q30.28 16.52 36.0 17.3 Q30.28 18.080000000000002 29.5 23.8 Q28.72 18.080000000000002 23.0 17.3 Q28.72 16.52 29.5 10.8 Z"),
            ),
            light = Palette(Color(0xFFBD367B), Color(0xFF871C2D), Color(0xFFC84F8D), Color(0xFF971F32), Color(0x29C1377E), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFE7B3CE), Color(0xFFD46879), Color(0xFFE3A5C5), Color(0xFFCD5366), Color(0x2BDA87B2), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFFAA306F), 2.4f),
            round = listOf(Color(0xFFC74B8B), Color(0xFFC1377E), Color(0xFFAA2338)),
            glow = Color(0xFFD577A7),
        ),
        "Leggi" to Drawing(
            listOf(
            Part("t", "M14 17.5 A8.5 8.5 0 1 0 14 34.5 A8.5 8.5 0 1 0 14 17.5 Z M34 17.5 A8.5 8.5 0 1 0 34 34.5 A8.5 8.5 0 1 0 34 17.5 Z"),
            Part("s", "M14 17.5 A8.5 8.5 0 1 0 14 34.5 A8.5 8.5 0 1 0 14 17.5 Z M34 17.5 A8.5 8.5 0 1 0 34 34.5 A8.5 8.5 0 1 0 34 17.5 Z M5.5 25 L3 19.5 M42.5 25 L45 19.5"),
            Part("S", "M22.5 25 C23.2 22.4 24.8 22.4 25.5 25"),
            Part("p", "M16.5 19.6 Q16.91 22.59 19.9 23 Q16.91 23.41 16.5 26.4 Q16.09 23.41 13.1 23 Q16.09 22.59 16.5 19.6 Z"),
            Part("p", "M36.5 20.6 Q36.79 22.71 38.9 23 Q36.79 23.29 36.5 25.4 Q36.21 23.29 34.1 23 Q36.21 22.71 36.5 20.6 Z"),
            ),
            light = Palette(Color(0xFFA91E45), Color(0xFF78150C), Color(0xFFB63A5C), Color(0xFF85170D), Color(0x29AC1F46), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFDFAAB9), Color(0xFFC46258), Color(0xFFDA9AAC), Color(0xFFBC4B41), Color(0x2BCD7990), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF971B3E), 2.4f),
            round = listOf(Color(0xFFB43558), Color(0xFFAC1F46), Color(0xFF961A0F)),
            glow = Color(0xFFC76781),
        ),
        "Mappe" to Drawing(
            listOf(
            Part("t", "M27 19.5 C25.5 23 23 25.5 19.5 26.8 C24 27.2 28.5 25 30.6 20.6 Z"),
            Part("p", "M10 24 C14 19 21 17 28 18 L33 15.5 C35 14.5 37.5 15 38.5 17 L41.5 18 L38.4 19.4 C38 22 36 24 33 25 C29 28 22 29 16 27.5 L8 31 L11 26.5 Z M20 19 C19 12 22 7 28 4.5 C27.5 9.5 28.5 14 31 18 Z"),
            Part("hf", "M35.6 16.700000000000003 A0.9 0.9 0 1 0 35.6 18.5 A0.9 0.9 0 1 0 35.6 16.700000000000003 Z"),
            Part("p", "M9 39.1 A1.4 1.4 0 1 0 9 41.9 A1.4 1.4 0 1 0 9 39.1 Z M13.8 37.0 A1.4 1.4 0 1 0 13.8 39.8 A1.4 1.4 0 1 0 13.8 37.0 Z M18.8 36.0 A1.4 1.4 0 1 0 18.8 38.8 A1.4 1.4 0 1 0 18.8 36.0 Z M23.8 36.2 A1.4 1.4 0 1 0 23.8 39.0 A1.4 1.4 0 1 0 23.8 36.2 Z"),
            Part("p", "M33 44 C33 44 27.5 39 27.5 35 A5.5 5.5 0 0 1 38.5 35 C38.5 39 33 44 33 44 Z"),
            Part("hf", "M33 31.8 Q33.38 34.62 36.2 35 Q33.38 35.38 33 38.2 Q32.62 35.38 29.8 35 Q32.62 34.62 33 31.8 Z"),
            ),
            light = Palette(Color(0xFF1047E1), Color(0xFF0D7242), Color(0xFF2D5EE9), Color(0xFF0E7F49), Color(0x291048E6), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFA4B9F6), Color(0xFF59BF8E), Color(0xFF93ADF4), Color(0xFF41B57E), Color(0x2B7091F0), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF0E3FCA), 2.4f),
            round = listOf(Color(0xFF285AE8), Color(0xFF1048E6), Color(0xFF108F53)),
            glow = Color(0xFF5C83EE),
        ),
        "Messaggi" to Drawing(
            listOf(
            Part("p", "M24 9 C34 9 41 15 41 23 C41 31 34 37 24 37 C21.5 37 19 36.6 17 35.8 L9 40 L11 33 C8.5 30.2 7 26.8 7 23 C7 15 14 9 24 9 Z"),
            Part("h", "M14.5 20 C17.5 17 20.5 23 23.5 20 S29.5 17 33 20 M14.5 27 C17.5 24 20.5 30 23.5 27"),
            Part("hf", "M31 23.4 Q31.43 26.57 34.6 27 Q31.43 27.43 31 30.6 Q30.57 27.43 27.4 27 Q30.57 26.57 31 23.4 Z"),
            ),
            light = Palette(Color(0xFF2752CB), Color(0xFF1D1092), Color(0xFF4269D5), Color(0xFF2012A2), Color(0x292854CF), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFADBEED), Color(0xFF695DDE), Color(0xFF9EB2E9), Color(0xFF5445D9), Color(0x2B7E98E2), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF234AB6), 2.4f),
            round = listOf(Color(0xFF3E65D4), Color(0xFF2854CF), Color(0xFF2414B7)),
            glow = Color(0xFF6D8BDE),
        ),
        "Meteo" to Drawing(
            listOf(
            Part("t", "M31 5 A10 10 0 1 0 31 25 A10 10 0 1 0 31 5 Z"),
            Part("p", "M31 7 Q31.96 14.04 39 15 Q31.96 15.96 31 23 Q30.04 15.96 23 15 Q30.04 14.04 31 7 Z"),
            Part("p", "M14 39 H34 A7 7 0 0 0 35 25 A10 10 0 0 0 16.5 23.5 A8 8 0 0 0 14 39 Z"),
            ),
            light = Palette(Color(0xFF20A7F1), Color(0xFF0637B2), Color(0xFF3CB4F7), Color(0xFF073DC7), Color(0x2921AAF6), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFABDFFC), Color(0xFF5383FF), Color(0xFF9BD9FB), Color(0xFF3A71FF), Color(0x2B7ACCFA), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF1D96D8), 2.4f),
            round = listOf(Color(0xFF37B2F7), Color(0xFF21AAF6), Color(0xFF0845E0)),
            glow = Color(0xFF68C5F9),
        ),
        "Note" to Drawing(
            listOf(
            Part("p", "M12 8 H36 Q40 8 40 12 V30 L30 40 H12 Q8 40 8 36 V12 Q8 8 12 8 Z"),
            Part("t", "M40 30 C34 29.5 30.5 33.5 30 40 Z"),
            Part("s", "M40 30 C34 29.5 30.5 33.5 30 40"),
            Part("h", "M14 17 C18 14 22 20 26 17 S32 15 34 17 M14 24 C18 21 21 27 25 24"),
            Part("hf", "M18 28 Q18.48 31.52 22 32 Q18.48 32.48 18 36 Q17.52 32.48 14 32 Q17.52 31.52 18 28 Z"),
            ),
            light = Palette(Color(0xFFEAA50B), Color(0xFFA76110), Color(0xFFF1B228), Color(0xFFBA6C12), Color(0x29EFA80B), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFF9DEA2), Color(0xFFF3AD5D), Color(0xFFF8D891), Color(0xFFF1A145), Color(0x2BF5CB6D), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFFD2940A), 2.4f),
            round = listOf(Color(0xFFF1B123), Color(0xFFEFA80B), Color(0xFFD17914)),
            glow = Color(0xFFF4C459),
        ),
        "Office" to Drawing(
            listOf(
            Part("t", "M17 9.5 A8.5 8.5 0 1 0 17 26.5 A8.5 8.5 0 1 0 17 9.5 Z"),
            Part("s", "M17 9.5 A8.5 8.5 0 1 0 17 26.5 A8.5 8.5 0 1 0 17 9.5 Z"),
            Part("p", "M27 20 H34.5 Q39 20 39 24.5 V32 Q39 36.5 34.5 36.5 H27 Q22.5 36.5 22.5 32 V24.5 Q22.5 20 27 20 Z"),
            Part("hf", "M30.75 23.85 Q31.28 27.72 35.15 28.25 Q31.28 28.78 30.75 32.65 Q30.22 28.78 26.35 28.25 Q30.22 27.72 30.75 23.85 Z"),
            Part("t", "M12.4 30.6 Q14 28.6 15.6 30.6 L21 38 Q22.2 40.5 19.5 40.5 H8.5 Q5.8 40.5 7 38 Z"),
            Part("s", "M12.4 30.6 Q14 28.6 15.6 30.6 L21 38 Q22.2 40.5 19.5 40.5 H8.5 Q5.8 40.5 7 38 Z"),
            ),
            light = Palette(Color(0xFF771DE1), Color(0xFF881A6F), Color(0xFF8939E9), Color(0xFF971D7B), Color(0x29791EE6), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFCCAAF6), Color(0xFFD466BB), Color(0xFFC39AF4), Color(0xFFCE51B1), Color(0x2BAF78F0), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF6A1ACA), 2.4f),
            round = listOf(Color(0xFF8634E8), Color(0xFF791EE6), Color(0xFFAB218B)),
            glow = Color(0xFFA466EE),
        ),
        "Orologio" to Drawing(
            listOf(
            Part("t", "M24 8.5 A16.5 16.5 0 1 0 24 41.5 A16.5 16.5 0 1 0 24 8.5 Z"),
            Part("s", "M24 8.5 A16.5 16.5 0 1 0 24 41.5 A16.5 16.5 0 1 0 24 8.5 Z"),
            Part("s", "M24 10.6 V12.4 M38.4 25 H36.6 M24 39.4 V37.6 M9.6 25 H11.4"),
            Part("p", "M21.83 12.69 Q25.26 23.20 32.86 23.44 Q25.80 26.26 24.87 29.92 Q22.74 26.80 19.08 25.87 Q22.20 23.74 21.83 12.69 Z"),
            Part("hf", "M24 23.6 A1.4 1.4 0 1 0 24 26.4 A1.4 1.4 0 1 0 24 23.6 Z"),
            Part("S", "M21 5 H27"),
            ),
            light = Palette(Color(0xFFEA770D), Color(0xFFA68B00), Color(0xFFF1892A), Color(0xFFB99A00), Color(0x29EF790D), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFF9CCA3), Color(0xFFF2D74C), Color(0xFFF8C392), Color(0xFFF1D133), Color(0x2BF5AF6E), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFFD26A0B), 2.4f),
            round = listOf(Color(0xFFF18625), Color(0xFFEF790D), Color(0xFFD1AE00)),
            glow = Color(0xFFF4A45A),
        ),
        "Password" to Drawing(
            listOf(
            Part("p", "M24.2 16.3 L20.4 17.3 L20.7 13.5 L17.6 15.8 L16.4 12.1 L14.4 15.4 L12.0 12.4 L11.3 16.2 L7.9 14.3 L8.7 18.1 L4.9 17.6 L7.0 20.8 L3.2 21.8 L6.4 24.0 L3.2 26.2 L7.0 27.2 L4.9 30.4 L8.7 29.9 L7.9 33.7 L11.3 31.8 L12.0 35.6 L14.4 32.6 L16.4 35.9 L17.6 32.2 L20.7 34.5 L20.4 30.7 L24.2 31.7 C29.5 30 31.5 27 31.5 24 C31.5 21 29.5 18 24.2 16.3 Z"),
            Part("hf", "M14 19.4 Q14.55 23.45 18.6 24 Q14.55 24.55 14 28.6 Q13.45 24.55 9.4 24 Q13.45 23.45 14 19.4 Z M26.2 19.9 A1.1 1.1 0 1 0 26.2 22.1 A1.1 1.1 0 1 0 26.2 19.9 Z"),
            Part("S", "M31 24 H42 M36.5 24 V30 M41 24 V28.5"),
            ),
            light = Palette(Color(0xFF7D379D), Color(0xFF6E1E6A), Color(0xFF8F50AB), Color(0xFF7A2276), Color(0x298038A0), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFCFB3DB), Color(0xFFBA6BB6), Color(0xFFC6A5D4), Color(0xFFB155AC), Color(0x2BB388C6), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF71318D), 2.4f),
            round = listOf(Color(0xFF8D4CAA), Color(0xFF8038A0), Color(0xFF8A2685)),
            glow = Color(0xFFA978BE),
        ),
        "Scanner" to Drawing(
            listOf(
            Part("t", "M14 8 H29 L36 15 V37 Q36 40 33 40 H14 Q11 40 11 37 V11 Q11 8 14 8 Z"),
            Part("s", "M14 8 H29 L36 15 V37 Q36 40 33 40 H14 Q11 40 11 37 V11 Q11 8 14 8 Z M29 8 V15 H36"),
            Part("p", "M5 25 C15 19.5 27 30 43 21.5 V26.5 C27 35 15 24.5 5 30 Z"),
            Part("p", "M41 9 Q41.48 12.52 45 13 Q41.48 13.48 41 17 Q40.52 13.48 37 13 Q40.52 12.52 41 9 Z"),
            ),
            light = Palette(Color(0xFF3F87F7), Color(0xFF29429D), Color(0xFF5798FC), Color(0xFF2D4AAF), Color(0x29408AFC), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFB6D3FE), Color(0xFF758FE9), Color(0xFFA9CAFE), Color(0xFF617FE6), Color(0x2B8CB9FD), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF3879DE), 2.4f),
            round = listOf(Color(0xFF5396FC), Color(0xFF408AFC), Color(0xFF3354C5)),
            glow = Color(0xFF7DAFFD),
        ),
        "Tastiera" to Drawing(
            listOf(
            Part("t", "M12 8 H18.5 Q22.5 8 22.5 12 V18.5 Q22.5 22.5 18.5 22.5 H12 Q8 22.5 8 18.5 V12 Q8 8 12 8 Z M29.5 8 H36.0 Q40.0 8 40.0 12 V18.5 Q40.0 22.5 36.0 22.5 H29.5 Q25.5 22.5 25.5 18.5 V12 Q25.5 8 29.5 8 Z M12 25.5 H18.5 Q22.5 25.5 22.5 29.5 V36.0 Q22.5 40.0 18.5 40.0 H12 Q8 40.0 8 36.0 V29.5 Q8 25.5 12 25.5 Z"),
            Part("s", "M12 8 H18.5 Q22.5 8 22.5 12 V18.5 Q22.5 22.5 18.5 22.5 H12 Q8 22.5 8 18.5 V12 Q8 8 12 8 Z M29.5 8 H36.0 Q40.0 8 40.0 12 V18.5 Q40.0 22.5 36.0 22.5 H29.5 Q25.5 22.5 25.5 18.5 V12 Q25.5 8 29.5 8 Z M12 25.5 H18.5 Q22.5 25.5 22.5 29.5 V36.0 Q22.5 40.0 18.5 40.0 H12 Q8 40.0 8 36.0 V29.5 Q8 25.5 12 25.5 Z"),
            Part("h", "M12.5 19.2 L15.2 11.8 L18 19.2 M13.4 16.8 H17"),
            Part("p", "M29.5 25.5 H36.0 Q40.0 25.5 40.0 29.5 V36.0 Q40.0 40.0 36.0 40.0 H29.5 Q25.5 40.0 25.5 36.0 V29.5 Q25.5 25.5 29.5 25.5 Z"),
            Part("hf", "M32.75 28.35 Q33.28 32.22 37.15 32.75 Q33.28 33.28 32.75 37.15 Q32.22 33.28 28.35 32.75 Q32.22 32.22 32.75 28.35 Z"),
            ),
            light = Palette(Color(0xFF7350DB), Color(0xFF7326A4), Color(0xFF8667E3), Color(0xFF802BB7), Color(0x297552DF), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFCBBDF3), Color(0xFFBF73F0), Color(0xFFC1B1F1), Color(0xFFB65FEE), Color(0x2BAC97EC), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF6748C4), 2.4f),
            round = listOf(Color(0xFF8363E2), Color(0xFF7552DF), Color(0xFF9030CE)),
            glow = Color(0xFFA189E9),
        ),
        "Telefono" to Drawing(
            listOf(
            Part("p", "M13 7 C9 7 6.5 10 7 14 C8.5 28 20 39.5 34 41 C38 41.5 41 39 41 35 V32.5 C41 31 40 29.8 38.6 29.4 L33.4 28 C32 27.6 30.6 28.2 30 29.4 L28.6 32 C23 29.5 18.5 25 16 19.4 L18.6 18 C19.8 17.4 20.4 16 20 14.6 L18.6 9.4 C18.2 8 17 7 15.5 7 Z"),
            Part("p", "M38 19.9 Q38.43 23.07 41.6 23.5 Q38.43 23.93 38 27.1 Q37.57 23.93 34.4 23.5 Q37.57 23.07 38 19.9 Z"),
            ),
            light = Palette(Color(0xFF25AD4A), Color(0xFF117B53), Color(0xFF40BA61), Color(0xFF13895D), Color(0x2926B14B), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFADE1BB), Color(0xFF5DC8A0), Color(0xFF9DDCAE), Color(0xFF46C092), Color(0x2B7DD093), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFF219C42), 2.4f),
            round = listOf(Color(0xFF3CB95D), Color(0xFF26B14B), Color(0xFF159B69)),
            glow = Color(0xFF6BCA85),
        ),
        "Traduttore" to Drawing(
            listOf(
            Part("p", "M10 40 C7 30 7 20 12 13 C16 8 24 7 29 10 C32 12 33.5 15 33.5 18 C31 20 29 26 26 30 C25 33 23.5 36 23 40 Z"),
            Part("t", "M30 14.5 C34 14.5 37.5 17.5 37.5 21.5 C37.5 24.5 35.5 26.5 33 26.5 C33.5 24 32.5 21.5 30 21 Z"),
            Part("s", "M30 14.5 C34 14.5 37.5 17.5 37.5 21.5 C37.5 24.5 35.5 26.5 33 26.5 C33.5 24 32.5 21.5 30 21 Z"),
            Part("hf", "M24.5 13.1 A2.4 2.4 0 1 0 24.5 17.9 A2.4 2.4 0 1 0 24.5 13.1 Z"),
            Part("p", "M24.5 14.0 Q24.68 15.32 26.0 15.5 Q24.68 15.68 24.5 17.0 Q24.32 15.68 23.0 15.5 Q24.32 15.32 24.5 14.0 Z"),
            Part("h", "M13 24 C15.5 28 18.5 30 22 30.5"),
            Part("p", "M30 28 H41 Q44.5 28 44.5 31.5 V38.5 Q44.5 42 41 42 H34 L30.5 45 V42 Q27 41.5 27 38.5 V31.5 Q27 28 30.5 28 Z"),
            Part("h", "M30.5 32.2 H41 M35.8 30.4 V32.2 M32 39.6 C35.4 38.6 37.8 36 38.8 32.8 M32.4 34.4 C34 37 36.4 39 39.8 39.8"),
            ),
            light = Palette(Color(0xFFCB2230), Color(0xFF92320D), Color(0xFFD53D4A), Color(0xFFA2370E), Color(0x29CF2331), Color(0xFFF6F5F1), 2.3f),
            dark = Palette(Color(0xFFEDABB1), Color(0xFFDE7E59), Color(0xFFE99CA2), Color(0xFFD96C41), Color(0x2BE27B83), Color(0xFF262A33), 2.3f),
            color = Palette(Color(0xFFFFFDF8), Color(0xFFF4E6CC), Color(0xFFFFFDF8), Color(0xFFEFDDBF), Color(0x33FFFFFF), Color(0xFFB61F2B), 2.4f),
            round = listOf(Color(0xFFD43946), Color(0xFFCF2331), Color(0xFFB73E10)),
            glow = Color(0xFFDE6973),
        ),
    )
}
