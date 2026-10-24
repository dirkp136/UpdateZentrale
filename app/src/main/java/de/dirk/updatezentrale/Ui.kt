package de.dirk.updatezentrale

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Colour set for light and dark mode. */
data class Palette(
    val bg: Int, val surface: Int, val onSurface: Int, val muted: Int, val divider: Int,
    val accent: Int, val accentContainer: Int, val onAccentContainer: Int,
    val ok: Int, val okContainer: Int, val danger: Int, val dangerContainer: Int
) {
    companion object {
        fun of(ctx: Context): Palette {
            val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            return if (night) Palette(
                bg = 0xFF0F1418.toInt(), surface = 0xFF1A2128.toInt(), onSurface = 0xFFE4E9ED.toInt(),
                muted = 0xFF98A4AF.toInt(), divider = 0xFF29323A.toInt(),
                accent = 0xFF7CC4DE.toInt(), accentContainer = 0xFF1C3C49.toInt(), onAccentContainer = 0xFFCBEAF5.toInt(),
                ok = 0xFF8BD3AA.toInt(), okContainer = 0xFF1A3828.toInt(),
                danger = 0xFFF2B8B5.toInt(), dangerContainer = 0xFF5A1D18.toInt()
            ) else Palette(
                bg = 0xFFF2F5F7.toInt(), surface = 0xFFFFFFFF.toInt(), onSurface = 0xFF1A1F24.toInt(),
                muted = 0xFF5C6670.toInt(), divider = 0xFFE4E8EC.toInt(),
                accent = 0xFF1C6C8A.toInt(), accentContainer = 0xFFD9EDF4.toInt(), onAccentContainer = 0xFF0A3A4B.toInt(),
                ok = 0xFF1D7A4C.toInt(), okContainer = 0xFFD8F0E2.toInt(),
                danger = 0xFFB3261E.toInt(), dangerContainer = 0xFFF9DEDC.toInt()
            )
        }
    }
}

/** Small view factory so the activity stays readable. */
class Ui(private val ctx: Context, val p: Palette) {

    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun text(s: CharSequence, sp: Float, color: Int = p.onSurface, bold: Boolean = false) =
        TextView(ctx).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            if (bold) typeface = medium
        }

    fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    /** Background with touch ripple for clickable surfaces. */
    fun clickable(view: View, color: Int, radius: Int, onClick: () -> Unit) {
        val shape = rounded(color, radius)
        val mask = rounded(0xFFFFFFFF.toInt(), radius)
        view.background = RippleDrawable(ColorStateList.valueOf((p.accent and 0x00FFFFFF) or 0x33000000), shape, mask)
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener { onClick() }
    }

    /** Round tinted icon badge. */
    fun badge(iconRes: Int, size: Int = 40, bg: Int = p.accentContainer, tint: Int = p.onAccentContainer) =
        FrameLayout(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
            addView(ImageView(ctx).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(tint)
                layoutParams = FrameLayout.LayoutParams(dp(size / 2 + 2), dp(size / 2 + 2), Gravity.CENTER)
            })
        }

    fun chevron(): ImageView = ImageView(ctx).apply {
        setImageResource(R.drawable.ic_chevron)
        imageTintList = ColorStateList.valueOf(p.muted)
        layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
    }

    /** Pill-shaped status label. */
    fun chip(label: String, fg: Int, bg: Int) = text(label, 13f, fg, bold = true).apply {
        background = rounded(bg, 100)
        setPadding(dp(12), dp(6), dp(12), dp(6))
    }

    fun section(label: String) = text(label.uppercase(), 12f, p.muted, bold = true).apply {
        letterSpacing = 0.08f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24); bottomMargin = dp(8); leftMargin = dp(4) }
    }

    fun vertical() = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    fun horizontal() = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun card(): LinearLayout = vertical().apply {
        background = rounded(p.surface, 20)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
    }

    fun divider(insetStart: Int = 0) = View(ctx).apply {
        setBackgroundColor(p.divider)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            .apply { marginStart = dp(insetStart) }
    }

    fun weighted(view: View): View = view.apply {
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }

    fun spacer(w: Int = 0, h: Int = 0) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(w), dp(h))
    }

    fun drawable(res: Int): Drawable? = ctx.getDrawable(res)
}
