package com.example.homeworkbuddy

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView

/** Chat screen for the Homework Copilot. */
class CopilotActivity : Activity() {
    companion object {
        // true = message from the user, false = from the Copilot
        val history = mutableListOf<Pair<Boolean, String>>()
    }

    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Ui.PRIMARY
        val ctx = this

        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        // ---- header ----
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(16), dp(14), dp(16), dp(14))
        head.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Ui.PRIMARY, Color.parseColor("#7E57C2"))
        )
        val back = Ui.text(ctx, "←", 26f, Color.WHITE, true)
        back.setPadding(0, 0, dp(16), 0)
        back.setOnClickListener { finish() }
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.addView(Ui.text(ctx, "🤖 Copilot", 20f, Color.WHITE, true))
        col.addView(Ui.text(ctx, "Your homework assistant", 12f, Color.parseColor("#CCFFFFFF")))
        head.addView(back)
        head.addView(col)
        root.addView(head)

        // ---- chat area ----
        scroll = ScrollView(ctx)
        scroll.isVerticalScrollBarEnabled = false
        chat = LinearLayout(ctx)
        chat.orientation = LinearLayout.VERTICAL
        chat.setPadding(dp(12), dp(12), dp(12), dp(12))
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // ---- quick buttons ----
        val hs = HorizontalScrollView(ctx)
        hs.isHorizontalScrollBarEnabled = false
        val chips = LinearLayout(ctx)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.setPadding(dp(8), dp(6), dp(8), dp(2))
        listOf(
            "📊 My report" to "report",
            "📋 What's due?" to "what's due",
            "💡 Tips" to "tips",
            "🎒 Today" to "today subjects",
            "❓ Help" to "help"
        ).forEach { (label, cmd) ->
            val chip = Ui.text(ctx, label, 14f, Ui.PRIMARY, true)
            chip.background = Ui.rounded(ctx, Color.WHITE, 20, Color.parseColor("#C5CAE9"))
            chip.setPadding(dp(14), dp(8), dp(14), dp(8))
            val lp = LinearLayout.LayoutParams(WRAP, WRAP)
            lp.setMargins(dp(4), 0, dp(4), 0)
            chip.layoutParams = lp
            chip.setOnClickListener { send(cmd) }
            chips.addView(chip)
        }
        hs.addView(chips)
        root.addView(hs)

        // ---- input bar ----
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(10), dp(8), dp(10), dp(10))

        input = EditText(ctx)
        input.hint = "Ask me or give a command…"
        input.background = Ui.rounded(ctx, Color.WHITE, 24, Color.parseColor("#C5CAE9"))
        input.setPadding(dp(16), dp(10), dp(16), dp(10))
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.maxLines = 3
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { submit(); true } else false
        }
        bar.addView(input, LinearLayout.LayoutParams(0, WRAP, 1f))

        val sendBtn = Ui.text(ctx, "➤", 20f, Color.WHITE, true)
        sendBtn.gravity = Gravity.CENTER
        sendBtn.background = Ui.rounded(ctx, Ui.PRIMARY, 24)
        val slp = LinearLayout.LayoutParams(dp(48), dp(48))
        slp.setMargins(dp(8), 0, 0, 0)
        sendBtn.layoutParams = slp
        sendBtn.setOnClickListener { submit() }
        bar.addView(sendBtn)
        root.addView(bar)

        setContentView(root)

        if (history.isEmpty()) {
            history.add(
                false to "Hi! I'm your Homework Copilot 🤖\n${Copilot.headline(ctx)}\n\n" +
                    "Tap a button below or type a command. Say \"help\" to see everything I can do."
            )
        }
        history.forEach { addBubble(it.first, it.second) }
    }

    private fun submit() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        send(t)
    }

    private fun send(text: String) {
        history.add(true to text)
        addBubble(true, text)
        Handler(Looper.getMainLooper()).postDelayed({
            val r = Copilot.reply(this, text)
            history.add(false to r)
            addBubble(false, r)
        }, 350)
    }

    private fun addBubble(user: Boolean, text: String) {
        val tv = Ui.text(this, text, 15f, if (user) Color.WHITE else Ui.TEXT)
        tv.maxWidth = (resources.displayMetrics.widthPixels * 0.8f).toInt()
        tv.setPadding(dp(14), dp(10), dp(14), dp(10))
        tv.background = Ui.rounded(this, if (user) Ui.PRIMARY else Color.WHITE, 18)
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.gravity = if (user) Gravity.END else Gravity.START
        lp.setMargins(0, dp(4), 0, dp(4))
        tv.layoutParams = lp
        chat.addView(tv)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
}
