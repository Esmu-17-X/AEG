package com.example.homeworkbuddy

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** Small helpers that make the screens look good. */
object Ui {
    val PRIMARY = Color.parseColor("#3F51B5")
    val ACCENT = Color.parseColor("#FF9800")
    val GREEN = Color.parseColor("#43A047")
    val RED = Color.parseColor("#E53935")
    val BG = Color.parseColor("#F3F4F9")
    val TEXT = Color.parseColor("#1F2340")
    val MUTED = Color.parseColor("#7A7F9A")
    val CHIP_OFF = Color.parseColor("#E8EAF6")

    private val COLORS = mapOf(
        "English" to "#E91E63", "Math" to "#3F51B5", "Biology" to "#43A047",
        "Physics" to "#00ACC1", "Chemistry" to "#8E24AA", "IT" to "#546E7A",
        "Agriculture" to "#7CB342", "CTE" to "#FB8C00"
    )
    private val EMOJI = mapOf(
        "English" to "📖", "Math" to "➗", "Biology" to "🧬", "Physics" to "⚛️",
        "Chemistry" to "🧪", "IT" to "💻", "Agriculture" to "🌱", "CTE" to "🛠️"
    )

    fun label(s: String) = "${EMOJI[s] ?: "📘"}  $s"
    fun subjectColor(s: String): Int = Color.parseColor(COLORS[s] ?: "#3F51B5")

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun rounded(c: Context, color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = radiusDp * c.resources.displayMetrics.density
        if (strokeColor != null) g.setStroke(dp(c, 2), strokeColor)
        return g
    }

    fun text(c: Context, t: String, sp: Float = 15f, color: Int = TEXT, bold: Boolean = false): TextView {
        val v = TextView(c)
        v.text = t
        v.textSize = sp
        v.setTextColor(color)
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD)
        return v
    }

    fun card(c: Context): LinearLayout {
        val v = LinearLayout(c)
        v.orientation = LinearLayout.VERTICAL
        v.background = rounded(c, Color.WHITE, 20)
        v.elevation = 3f * c.resources.displayMetrics.density
        v.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16))
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, 0, dp(c, 12))
        v.layoutParams = lp
        return v
    }

    fun button(
        c: Context, label: String, bg: Int, fg: Int = Color.WHITE,
        radius: Int = 16, onClick: () -> Unit
    ): TextView {
        val v = TextView(c)
        v.text = label
        v.textSize = 16f
        v.setTextColor(fg)
        v.setTypeface(Typeface.DEFAULT_BOLD)
        v.gravity = Gravity.CENTER
        v.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14))
        v.background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#33000000")),
            rounded(c, bg, radius), rounded(c, Color.BLACK, radius)
        )
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, dp(c, 6), 0, 0)
        v.layoutParams = lp
        v.setOnClickListener { onClick() }
        return v
    }

    /** A tappable settings row:  icon  label  value  › */
    fun row(c: Context, icon: String, label: String, value: String?, onClick: () -> Unit): LinearLayout {
        val r = LinearLayout(c)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, dp(c, 12), 0, dp(c, 12))
        r.background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#22000000")),
            ColorDrawable(Color.TRANSPARENT), ColorDrawable(Color.BLACK)
        )
        val ic = text(c, icon, 20f)
        ic.layoutParams = LinearLayout.LayoutParams(dp(c, 36), ViewGroup.LayoutParams.WRAP_CONTENT)
        val lb = text(c, label, 16f)
        lb.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        r.addView(ic)
        r.addView(lb)
        if (value != null) r.addView(text(c, value, 15f, PRIMARY, true))
        r.addView(text(c, "  ›", 20f, MUTED))
        r.setOnClickListener { onClick() }
        return r
    }

    fun divider(c: Context): View {
        val v = View(c)
        v.setBackgroundColor(Color.parseColor("#EEEEF5"))
        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1))
        return v
    }

    fun styleChip(c: Context, tv: TextView, on: Boolean, color: Int) {
        tv.setTextColor(if (on) Color.WHITE else TEXT)
        tv.background = rounded(c, if (on) color else CHIP_OFF, 22)
    }

    private fun chip(
        c: Context, key: String, on: Boolean, onClick: (TextView) -> Unit
    ): TextView {
        val v = TextView(c)
        v.text = label(key)
        v.tag = key
        v.textSize = 15f
        v.setTypeface(Typeface.DEFAULT_BOLD)
        v.gravity = Gravity.CENTER
        v.setPadding(dp(c, 8), dp(c, 12), dp(c, 8), dp(c, 12))
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp.setMargins(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4))
        v.layoutParams = lp
        styleChip(c, v, on, subjectColor(key))
        v.setOnClickListener { onClick(v) }
        return v
    }

    /** Grid of subject chips, 2 per row. */
    fun chipGrid(
        c: Context, items: List<String>, selected: (String) -> Boolean,
        cols: Int = 2, onClick: (String, TextView) -> Unit
    ): LinearLayout {
        val grid = LinearLayout(c)
        grid.orientation = LinearLayout.VERTICAL
        items.chunked(cols).forEach { rowItems ->
            val r = LinearLayout(c)
            r.orientation = LinearLayout.HORIZONTAL
            rowItems.forEach { s ->
                r.addView(chip(c, s, selected(s)) { tv -> onClick(s, tv) })
            }
            repeat(cols - rowItems.size) {
                val sp = View(c)
                sp.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                r.addView(sp)
            }
            grid.addView(r)
        }
        return grid
    }
}
