package com.saintseiya.coach.capture

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bolha flutuante discreta (como gravadores de tela): mostra o progresso e dá acesso rápido a Parar / Coach.
 * Só aparece com a leitura ligada e com permissão de sobreposição; arrastável; não toca no jogo.
 */
class Bubble(private val ctx: Context, private val onStop: () -> Unit, private val onOpen: () -> Unit) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var label: TextView? = null
    private var panel: LinearLayout? = null
    private lateinit var lp: WindowManager.LayoutParams

    companion object { fun canShow(ctx: Context) = Settings.canDrawOverlays(ctx) }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun btn(text: String, color: Int, click: () -> Unit) = TextView(ctx).apply {
        this.text = text; setTextColor(color); textSize = 13f; setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { click() }
    }

    fun show() {
        if (root != null || !canShow(ctx)) return
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(6); y = dp(90) }
        val gold = Color.parseColor("#E8B84B")
        val r = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.parseColor("#E60B1020")); cornerRadius = dp(18).toFloat(); setStroke(dp(1), gold) }
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        val l = TextView(ctx).apply { text = "● 0"; setTextColor(gold); textSize = 12f; setPadding(dp(10), dp(6), dp(10), dp(6)) }
        val p = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            addView(btn("■ Parar", Color.parseColor("#FF6B6B")) { onStop() })
            addView(btn("↗ Coach", Color.parseColor("#5CC8FF")) { onOpen() })
        }
        r.addView(l); r.addView(p)
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        l.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (Math.abs(dx) > 8 || Math.abs(dy) > 8) moved = true
                    if (moved) { lp.x = sx + dx; lp.y = sy + dy; runCatching { wm.updateViewLayout(r, lp) } }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) p.visibility = if (p.visibility == View.GONE) View.VISIBLE else View.GONE; true }
                else -> false
            }
        }
        runCatching { wm.addView(r, lp); root = r; label = l; panel = p }
    }

    fun update(text: String) { label?.text = text }
    fun hide() { root?.let { runCatching { wm.removeView(it) } }; root = null; label = null; panel = null }
}
