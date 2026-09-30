package com.mindblow.app

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var gameView: MindBlowView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 7, 22)
        window.navigationBarColor = Color.rgb(5, 7, 22)
        gameView = MindBlowView(this)
        setContentView(gameView)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!gameView.goBack()) super.onBackPressed()
    }
}

private enum class Screen { SPLASH, WELCOME, HOME, GAMES, PUZZLE, MEMORY, FOCUS, RELAX, DAILY, PROFILE }

private class MindBlowView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs = context.getSharedPreferences("mindblow", Context.MODE_PRIVATE)
    private var screen = if (prefs.getBoolean("welcome_seen", false)) Screen.HOME else Screen.SPLASH
    private var startedAt = System.currentTimeMillis()
    private var t = 0f
    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)
    private var focusHits = 0
    private var puzzleTarget = Random.nextInt(16)
    private var memory = MutableList(9) { it }.apply { shuffle() }
    private var memoryFirst = -1
    private var memorySecond = -1
    private var memoryPreviewUntil = 0L
    private var message = ""
    private var messageUntil = 0L

    init { postInvalidateDelayed(16L) }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w = width.toFloat()
        val h = height.toFloat()
        t += 0.02f
        drawBackground(c, w, h)
        when (screen) {
            Screen.SPLASH -> splash(c, w, h)
            Screen.WELCOME -> welcome(c, w, h)
            Screen.HOME -> home(c, w, h)
            Screen.GAMES -> games(c, w, h)
            Screen.PUZZLE -> puzzle(c, w, h)
            Screen.MEMORY -> memory(c, w, h)
            Screen.FOCUS -> focus(c, w, h)
            Screen.RELAX -> relax(c, w, h)
            Screen.DAILY -> daily(c, w, h)
            Screen.PROFILE -> profile(c, w, h)
        }
        if (messageUntil > System.currentTimeMillis()) toast(c, w, h)
        if (screen == Screen.SPLASH && System.currentTimeMillis() - startedAt > 1200L) screen = Screen.WELCOME
        postInvalidateDelayed(16L)
    }

    private fun drawBackground(c: Canvas, w: Float, h: Float) {
        paint.shader = LinearGradient(0f, 0f, w, h, Color.rgb(5, 8, 28), Color.rgb(35, 7, 58), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        val a = t
        paint.color = Color.argb(38, 35, 220, 255)
        c.drawCircle(w * .18f + sin(a) * 35f, h * .18f + cos(a) * 25f, 135f, paint)
        paint.color = Color.argb(32, 160, 70, 255)
        c.drawCircle(w * .82f + cos(a * .8f) * 40f, h * .38f + sin(a) * 35f, 155f, paint)
        paint.color = Color.argb(24, 20, 255, 180)
        c.drawCircle(w * .48f + sin(a * .6f) * 45f, h * .82f, 170f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(24, 120, 220, 255)
        for (i in 0 until 6) c.drawCircle(w / 2f, h * .43f, 75f + i * 55f + sin(a + i) * 5f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int = Color.WHITE, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        paint.shader = null
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL)
        c.drawText(s, x, y, paint)
    }

    private fun card(c: Canvas, l: Float, top: Float, r: Float, b: Float, radius: Float = 20f) {
        paint.shader = null
        paint.color = Color.argb(225, 13, 19, 46)
        c.drawRoundRect(l, top, r, b, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = Color.argb(70, 110, 145, 220)
        c.drawRoundRect(l, top, r, b, radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun button(c: Canvas, label: String, l: Float, top: Float, r: Float, b: Float) {
        paint.shader = LinearGradient(l, top, r, b, Color.rgb(20, 215, 255), Color.rgb(135, 65, 255), Shader.TileMode.CLAMP)
        c.drawRoundRect(l, top, r, b, 18f, 18f, paint)
        paint.shader = null
        txt(c, label, (l + r) / 2f, (top + b) / 2f + 5f, 14f, Color.WHITE, true, Paint.Align.CENTER)
    }

    private fun header(c: Canvas, title: String, sub: String) {
        txt(c, "‹", 22f, 50f, 38f)
        txt(c, title, 60f, 42f, 23f, Color.WHITE, true)
        txt(c, sub, 60f, 63f, 11f, 0xff9da8cf.toInt())
    }

    private fun nav(c: Canvas, w: Float, h: Float, selected: Int) {
        val top = h - 78f
        card(c, 12f, top, w - 12f, h - 10f, 23f)
        val icons = arrayOf("⌂", "◆", "◎", "★", "●")
        val names = arrayOf("Home", "Games", "Focus", "Daily", "Profile")
        for (i in 0..4) {
            val x = w * (i + .5f) / 5f
            val color = if (i == selected) 0xff55e6ff.toInt() else 0xff7883a8.toInt()
            txt(c, icons[i], x, top + 29f, 20f, color, true, Paint.Align.CENTER)
            txt(c, names[i], x, top + 50f, 9f, color, false, Paint.Align.CENTER)
        }
    }

    private fun splash(c: Canvas, w: Float, h: Float) {
        txt(c, "✦", w / 2f, h * .39f, 75f, 0xff62e8ff.toInt(), true, Paint.Align.CENTER)
        txt(c, "MindBlow", w / 2f, h * .49f, 42f, Color.WHITE, true, Paint.Align.CENTER)
        txt(c, "RELAX  •  FOCUS  •  REFRESH", w / 2f, h * .545f, 13f, 0xffb7c2e5.toInt(), false, Paint.Align.CENTER)
    }

    private fun welcome(c: Canvas, w: Float, h: Float) {
        txt(c, "MindBlow", w / 2f, 75f, 31f, Color.WHITE, true, Paint.Align.CENTER)
        txt(c, "A tiny reset for your mind.", w / 2f, 103f, 14f, 0xffaeb8d9.toInt(), false, Paint.Align.CENTER)
        txt(c, "✦", w / 2f, h * .47f, 95f, 0xff63e8ff.toInt(), true, Paint.Align.CENTER)
        txt(c, "Play • Relax • Repeat", w / 2f, h * .61f, 25f, Color.WHITE, true, Paint.Align.CENTER)
        txt(c, "Quick challenges designed to refresh", w / 2f, h * .66f, 14f, 0xffaeb8d9.toInt(), false, Paint.Align.CENTER)
        txt(c, "your attention without pressure.", w / 2f, h * .695f, 14f, 0xffaeb8d9.toInt(), false, Paint.Align.CENTER)
        button(c, "GET STARTED", w * .16f, h * .77f, w * .84f, h * .85f)
    }

    private fun home(c: Canvas, w: Float, h: Float) {
        txt(c, "MindBlow", 20f, 40f, 27f, Color.WHITE, true)
        txt(c, "Level ${score / 500 + 1}  •  Keep your mind sharp", 20f, 62f, 11f, 0xff9ca8cc.toInt())
        card(c, w - 112f, 18f, w - 18f, 57f, 16f)
        txt(c, "✦ $score", w - 65f, 43f, 14f, 0xffffd66b.toInt(), true, Paint.Align.CENTER)
        card(c, 18f, 82f, w - 18f, 190f, 23f)
        txt(c, "Good to see you ✨", 34f, 114f, 15f, 0xffaeb9df.toInt())
        txt(c, "Take a tiny break.", 34f, 143f, 21f, Color.WHITE, true)
        txt(c, "Your mind will thank you.", 34f, 164f, 12f, 0xffaab5d6.toInt())
        button(c, "DAILY CHALLENGE  ›", 34f, 173f, w - 34f, 215f)
        txt(c, "Choose your mode", 20f, 247f, 18f, Color.WHITE, true)
        gameCard(c, "✦", "Puzzle", "Think & solve", 18f, 262f, w / 2f - 8f, 352f)
        gameCard(c, "◉", "Memory", "Remember & grow", w / 2f + 8f, 262f, w - 18f, 352f)
        gameCard(c, "◎", "Focus", "Stay sharp", 18f, 362f, w / 2f - 8f, 452f)
        gameCard(c, "◈", "Relax", "Just breathe", w / 2f + 8f, 362f, w - 18f, 452f)
        nav(c, w, h, 0)
    }

    private fun gameCard(c: Canvas, icon: String, title: String, sub: String, l: Float, top: Float, r: Float, b: Float) {
        card(c, l, top, r, b, 19f)
        txt(c, icon, l + 33f, top + 42f, 24f, 0xff5ce7ff.toInt(), true, Paint.Align.CENTER)
        txt(c, title, l + 18f, top + 68f, 16f, Color.WHITE, true)
        txt(c, sub, l + 18f, top + 86f, 10f, 0xff9da8cc.toInt())
    }

    private fun games(c: Canvas, w: Float, h: Float) {
        header(c, "Select Mode", "Pick your kind of break")
        gameCard(c, "✦", "Puzzle", "Connect • solve", 18f, 95f, w / 2f - 8f, 205f)
        gameCard(c, "◉", "Memory", "Match • remember", w / 2f + 8f, 95f, w - 18f, 205f)
        gameCard(c, "◎", "Focus", "Tap • concentrate", 18f, 218f, w / 2f - 8f, 328f)
        gameCard(c, "◈", "Relax", "Breathe • unwind", w / 2f + 8f, 218f, w - 18f, 328f)
        card(c, 18f, 342f, w - 18f, 430f, 20f)
        txt(c, "Daily Challenge", 34f, 374f, 18f, Color.WHITE, true)
        txt(c, "A fresh 2–5 minute challenge", 34f, 397f, 12f, 0xffa8b2d2.toInt())
        button(c, "PLAY TODAY", 34f, 405f, w - 34f, 426f)
        nav(c, w, h, 1)
    }

    private fun puzzle(c: Canvas, w: Float, h: Float) {
        header(c, "Puzzle", "Find the glowing tile")
        val size = min(w * .78f, 310f)
        val left = (w - size) / 2f
        val top = 110f
        for (i in 0 until 16) {
            val row = i / 4
            val col = i % 4
            val l = left + col * size / 4f + 4f
            val q = top + row * size / 4f + 4f
            val r = left + (col + 1) * size / 4f - 4f
            val b = top + (row + 1) * size / 4f - 4f
            card(c, l, q, r, b, 13f)
            if (i == puzzleTarget) {
                paint.color = 0xff48e7ff.toInt()
                c.drawCircle((l + r) / 2f, (q + b) / 2f, 16f + sin(t * 3f) * 4f, paint)
            }
        }
        txt(c, "Tap the glowing tile", w / 2f, top + size + 30f, 14f, 0xffb5bfdf.toInt(), false, Paint.Align.CENTER)
        txt(c, "+25 XP per clear", w / 2f, top + size + 52f, 11f, 0xff7784ad.toInt(), false, Paint.Align.CENTER)
    }

    private fun memory(c: Canvas, w: Float, h: Float) {
        header(c, "Memory", "Remember the symbols")
        val size = min(w * .78f, 315f)
        val left = (w - size) / 2f
        val top = 105f
        val symbols = arrayOf("✿", "★", "☾", "❖", "✦", "●", "◆", "♣", "☀")
        val preview = System.currentTimeMillis() < memoryPreviewUntil
        for (i in 0 until 9) {
            val row = i / 3
            val col = i % 3
            val l = left + col * size / 3f + 5f
            val q = top + row * size / 3f + 5f
            val r = left + (col + 1) * size / 3f - 5f
            val b = top + (row + 1) * size / 3f - 5f
            card(c, l, q, r, b, 15f)
            val reveal = preview || i == memoryFirst || i == memorySecond
            txt(c, if (reveal) symbols[memory[i]] else "?", (l + r) / 2f, (q + b) / 2f + 10f, 27f, 0xff67ddff.toInt(), true, Paint.Align.CENTER)
        }
        txt(c, if (preview) "Remember the board..." else "Tap two cards", w / 2f, top + size + 30f, 14f, 0xffaeb8d7.toInt(), false, Paint.Align.CENTER)
    }

    private fun focus(c: Canvas, w: Float, h: Float) {
        header(c, "Focus", "Tap the moving glow")
        txt(c, "Hits $focusHits", 24f, 94f, 15f, Color.WHITE, true)
        for (i in 0 until 4) {
            val angle = t * (1f + i * .08f) + i * 1.57f
            val x = w / 2f + cos(angle.toDouble()).toFloat() * w * .27f
            val y = 240f + sin((angle * 1.2f).toDouble()).toFloat() * 105f
            paint.color = if (i == focusHits % 4) 0xff4feaff.toInt() else 0xff895cff.toInt()
            c.drawCircle(x, y, 17f + 6f * sin(t * 2f + i), paint)
        }
        card(c, 24f, 430f, w - 24f, 495f, 19f)
        txt(c, "Breathe in • focus • tap", w / 2f, 462f, 14f, 0xffbac4e3.toInt(), false, Paint.Align.CENTER)
    }

    private fun relax(c: Canvas, w: Float, h: Float) {
        header(c, "Relax", "Slow down for a moment")
        val cx = w / 2f
        val cy = 245f
        for (i in 0..5) {
            paint.color = Color.argb(34 - i * 4, 50, 210, 255)
            c.drawCircle(cx, cy, 45f + i * 36f + sin(t + i) * 7f, paint)
        }
        txt(c, "◈", cx, cy + 25f, 68f, 0xff8d7cff.toInt(), true, Paint.Align.CENTER)
        txt(c, "Slow down", cx, 380f, 28f, Color.WHITE, true, Paint.Align.CENTER)
        txt(c, "Tap anywhere for a calming ripple", cx, 410f, 14f, 0xffaeb8d8.toInt(), false, Paint.Align.CENTER)
    }

    private fun daily(c: Canvas, w: Float, h: Float) {
        header(c, "Daily Challenge", "One small win today")
        card(c, 18f, 90f, w - 18f, 275f, 24f)
        txt(c, "TODAY", 38f, 128f, 13f, 0xff61ddff.toInt(), true)
        txt(c, "Clear your mind", 38f, 164f, 24f, Color.WHITE, true)
        txt(c, "Complete a quick challenge and", 38f, 192f, 13f, 0xffaeb8d7.toInt())
        txt(c, "earn a little XP.", 38f, 213f, 13f, 0xffaeb8d7.toInt())
        button(c, "PLAY NOW", 38f, 225f, w - 38f, 262f)
        txt(c, "🔥  $streak day streak", 22f, 322f, 19f, 0xffffb52e.toInt(), true)
        button(c, "CLAIM +50 XP", 22f, 350f, w - 22f, 402f)
        nav(c, w, h, 3)
    }

    private fun profile(c: Canvas, w: Float, h: Float) {
        header(c, "Profile", "Your progress")
        card(c, 18f, 85f, w - 18f, 210f, 24f)
        txt(c, "✦", 62f, 153f, 52f, 0xff68ddff.toInt(), true, Paint.Align.CENTER)
        txt(c, "MindExplorer", 105f, 126f, 21f, Color.WHITE, true)
        txt(c, "Level ${score / 500 + 1}", 105f, 151f, 13f, 0xffaeb8d8.toInt())
        txt(c, "$score XP", 105f, 184f, 13f, 0xff8f9bc4.toInt())
        txt(c, "Games  ${score / 25}", 35f, 258f, 16f, Color.WHITE, true)
        txt(c, "Streak  $streak", 35f, 292f, 16f, 0xffffb52e.toInt(), true)
        card(c, 18f, 320f, w - 18f, 430f, 22f)
        txt(c, "⚙  Settings", 38f, 358f, 16f, Color.WHITE)
        txt(c, "♫  Sound & Music", 38f, 398f, 16f, Color.WHITE)
        txt(c, "☾  Dark Theme", 38f, 438f, 16f, Color.WHITE)
        nav(c, w, h, 4)
    }

    private fun toast(c: Canvas, w: Float, h: Float) {
        card(c, 28f, h - 132f, w - 28f, h - 84f, 18f)
        txt(c, message, w / 2f, h - 103f, 13f, Color.WHITE, true, Paint.Align.CENTER)
    }

    private fun showMessage(s: String) {
        message = s
        messageUntil = System.currentTimeMillis() + 1200L
    }

    private fun save() { prefs.edit().putInt("score", score).putInt("streak", streak).apply() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_UP) return true
        val x = e.x
        val y = e.y
        val w = width.toFloat()
        val h = height.toFloat()
        when (screen) {
            Screen.SPLASH -> Unit
            Screen.WELCOME -> if (y > h * .70f) { prefs.edit().putBoolean("welcome_seen", true).apply(); screen = Screen.HOME }
            Screen.HOME -> homeTouch(x, y, w, h)
            Screen.GAMES -> gamesTouch(x, y, w, h)
            Screen.PUZZLE -> puzzleTouch(y, w)
            Screen.MEMORY -> memoryTouch(x, y, w)
            Screen.FOCUS -> { if (y < 80f) screen = Screen.GAMES else { focusHits++; score += 5; save(); showMessage("Great focus! +5 XP") } }
            Screen.RELAX -> { if (y < 80f) screen = Screen.GAMES else showMessage("Breathe in... and out...") }
            Screen.DAILY -> { if (y < 80f) screen = Screen.HOME else { score += 50; streak++; save(); showMessage("Daily complete! +50 XP") } }
            Screen.PROFILE -> if (y < 80f) screen = Screen.HOME
        }
        invalidate()
        return true
    }

    private fun homeTouch(x: Float, y: Float, w: Float, h: Float) {
        if (y > h - 100f && x > w * .80f) { screen = Screen.PROFILE; return }
        if (y > h - 100f && x in w * .20f..w * .40f) { screen = Screen.GAMES; return }
        if (y in 170f..220f) { screen = Screen.DAILY; return }
        if (y in 262f..352f) { if (x < w / 2f) screen = Screen.PUZZLE else startMemory(); return }
        if (y in 362f..452f) { if (x < w / 2f) screen = Screen.FOCUS else screen = Screen.RELAX }
    }

    private fun gamesTouch(x: Float, y: Float, w: Float, h: Float) {
        if (y < 80f) { screen = Screen.HOME; return }
        if (y > h - 100f && x > w * .80f) { screen = Screen.PROFILE; return }
        if (y in 95f..205f) { if (x < w / 2f) screen = Screen.PUZZLE else startMemory(); return }
        if (y in 218f..328f) { if (x < w / 2f) screen = Screen.FOCUS else screen = Screen.RELAX; return }
        if (y in 340f..440f) screen = Screen.DAILY
    }

    private fun puzzleTouch(y: Float, w: Float) {
        if (y < 80f) { screen = Screen.GAMES; return }
        if (y in 105f..425f) { score += 25; puzzleTarget = Random.nextInt(16); save(); showMessage("Level cleared! +25 XP") }
    }

    private fun memoryTouch(x: Float, y: Float, w: Float) {
        if (y < 80f) { screen = Screen.GAMES; return }
        if (System.currentTimeMillis() < memoryPreviewUntil) return
        val size = min(w * .78f, 315f)
        val left = (w - size) / 2f
        val top = 105f
        if (y < top || y > top + size || x < left || x > left + size) return
        val col = ((x - left) / (size / 3f)).toInt().coerceIn(0, 2)
        val row = ((y - top) / (size / 3f)).toInt().coerceIn(0, 2)
        val index = row * 3 + col
        if (memoryFirst == -1) memoryFirst = index
        else if (memorySecond == -1 && index != memoryFirst) {
            memorySecond = index
            if (memory[memoryFirst] == memory[memorySecond]) { score += 20; save(); showMessage("Match! +20 XP") }
            else showMessage("Not a match")
            postDelayed({ memoryFirst = -1; memorySecond = -1; invalidate() }, 450L)
        }
    }

    private fun startMemory() {
        memory = MutableList(9) { it }.apply { shuffle() }
        memoryFirst = -1
        memorySecond = -1
        memoryPreviewUntil = System.currentTimeMillis() + 2200L
        screen = Screen.MEMORY
    }

    fun goBack(): Boolean {
        if (screen == Screen.HOME || screen == Screen.SPLASH) return false
        screen = Screen.HOME
        invalidate()
        return true
    }
}
