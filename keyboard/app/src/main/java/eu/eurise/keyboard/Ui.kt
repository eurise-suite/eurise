package eu.eurise.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

/** Colori della barra presi dal tema della tastiera (Perla, Notte o quello scelto), così tutto resta coerente. */
internal class Palette {
    private val colors = Settings.getValues().mColors
    val background = colors.get(ColorType.MAIN_BACKGROUND)
    val surface = colors.get(ColorType.KEY_BACKGROUND)
    val text = colors.get(ColorType.KEY_TEXT)
    val muted = colors.get(ColorType.KEY_HINT_TEXT)
    val accent = colors.get(ColorType.GESTURE_TRAIL)
    val onAccent: Int get() = if (luminance(accent) > 0.5) Color.BLACK else Color.WHITE
    val faint: Int get() = (text and 0x00FFFFFF) or 0x14000000

    private fun luminance(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255
}

internal fun Context.dp(v: Number) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

internal fun rounded(color: Int, radius: Float, ripple: Int? = null): android.graphics.drawable.Drawable {
    val shape = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    return if (ripple == null) shape else RippleDrawable(ColorStateList.valueOf(ripple), shape, null)
}

/** Una pillola: testo (e icona) su fondo arrotondato; [on] la colora d'accento. Alta 40 dp per il dito. */
internal fun Context.pill(label: String, p: Palette, on: Boolean = false, icon: Int? = null, onClick: () -> Unit): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = dp(40)
        setPadding(dp(12), 0, dp(12), 0)
        background = rounded(if (on) p.accent else p.surface, dp(14).toFloat(), p.faint)
        isClickable = true
        contentDescription = label
        setOnClickListener { onClick() }
        if (icon != null) addView(ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, icon)?.mutate()?.apply { setTint(if (on) p.onAccent else p.accent) })
            layoutParams = LinearLayout.LayoutParams(dp(16), dp(16)).apply { marginEnd = if (label.isEmpty()) 0 else dp(6) }
        })
        if (label.isNotEmpty()) addView(TextView(context).apply {
            text = label
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (on) p.onAccent else p.text)
            maxLines = 1
        })
    }

internal fun Context.label(text: CharSequence, p: Palette, size: Float = 13f, bold: Boolean = false, muted: Boolean = false) = TextView(this).apply {
    this.text = text
    textSize = size
    if (bold) typeface = Typeface.DEFAULT_BOLD
    setTextColor(if (muted) p.muted else p.text)
}

internal fun View.lp(w: Int = LinearLayout.LayoutParams.MATCH_PARENT, h: Int = LinearLayout.LayoutParams.WRAP_CONTENT, weight: Float = 0f, top: Int = 0) =
    apply { layoutParams = LinearLayout.LayoutParams(w, h, weight).apply { topMargin = top } }
