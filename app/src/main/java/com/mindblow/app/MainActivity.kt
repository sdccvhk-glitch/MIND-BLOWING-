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
        window.statusBarColor = Color.rgb(6, 8, 25)
        window.navigationBarColor = Color.rgb(6, 8, 25)
        gameView = MindBlowView(this)
        setContentView(gameView)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (gameView.goBack()) return
        super.onBackPressed()
    }
}

private enum class Screen {
    SPLASH, ONBOARD, HOME, MODES, PUZZLE, MEMORY, FOCUS, RELAX, DAILY, PROFILE
}

private class MindBlowView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs = context.getSharedPreferences("mindblow", Context.MODE_PRIVATE)

    private var screen = if (prefs.getBoolean("seen", false)) Screen.HOME else Screen.SPLASH
    private var splashStart = System.currentTimeMillis()
    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)
    private var level = max(1, score / 500 + 1)

    private var animation = 0f
    private var toastText = ""
    private var toastUntil = 0L

    private var puzzleTarget = Random.nextInt(16)
    private var memoryShownUntil = 0L
    private var memoryValues = MutableList(9) { it }
    private var memoryFirst = -1
    private var memorySecond = -1
    private var focusHits = 0

    init {
        isFocusable = true
        memoryValues.shuffle()
        postInvalidateDelayed(16)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        animation += 0.025f

        drawAnimatedBackground(canvas, w, h)

        when (screen) {
            Screen.SPLASH -> drawSplash(canvas, w, h)
            Screen.ONBOARD -> drawOnboard(canvas, w, h)
            Screen.HOME -> drawHome(canvas, w, h)
            Screen.MODES -> drawModes(canvas, w, h)
            Screen.PUZZLE -> drawPuzzle(canvas, w, h)
            Screen.MEMORY -> drawMemory(canvas, w, h)
            Screen.FOCUS -> drawFocus(canvas, w, h)
            Screen.RELAX -> drawRelax(canvas, w, h)
            Screen.DAILY -> drawDaily(canvas, w, h)
            Screen.PROFILE -> drawProfile(canvas, w, h)
        }

        if (toastUntil > System.currentTimeMillis()) {
            drawToast(canvas, w, h, toastText)
        }

        if (screen == Screen.SPLASH && System.currentTimeMillis() - splashStart > 1500L) {
            screen = Screen.ONBOARD
        }

        postInvalidateDelayed(16)
    }

    private fun drawAnimatedBackground(c: Canvas, w: Float, h: Float) {
        val gradient = LinearGradient(
            0f, 0f, w, h,
            Color.rgb(5, 8, 28),
            Color.rgb(34, 8, 61),
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val x1 = w * 0.16f + sin(animation) * 28f
        val y1 = h * 0.18f + cos(animation * 0.8f) * 24f
        val x2 = w * 0.84f + cos(animation * 0.7f) * 35f
        val y2 = h * 0.34f + sin(animation * 0.9f) * 30f
        val x3 = w * 0.52f + sin(animation * 0.55f) * 42f
        val y3 = h * 0.84f + cos(animation * 0.6f) * 28f

        paint.color = Color.argb(42, 25, 220, 255)
        c.drawCircle(x1, y1, 135f + sin(animation) * 18f, paint)
        paint.color = Color.argb(36, 170, 70, 255)
        c.drawCircle(x2, y2, 155f + cos(animation) * 20f, paint)
        paint.color = Color.argb(25, 0, 255, 190)
        c.drawCircle(x3, y3, 175f, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(35, 100, 230, 255)
        for (i in 0 until 7) {
            val r = 80f + i * 55f + sin(animation + i) * 6f
            c.drawCircle(w * 0.5f, h * 0.43f, r, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun text(
        c: Canvas,
        value: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int = Color.WHITE,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface = if (bold) Typeface.create("sans", Typeface.BOLD)
        else Typeface.create("sans", Typeface.NORMAL)
        c.drawText(value, x, y, paint)
    }

    private fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float = 22f) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(235, 13, 19, 48)
        c.drawRoundRect(l, t, r, b, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(90, 100, 130, 220)
        c.drawRoundRect(l, t, r, b, radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    private fun gradientButton(c: Canvas, label: String, l: Float, t: Float, r: Float, b: Float) {
        val g = LinearGradient(
            l, t, r, b,
            Color.rgb(28, 218, 255),
            Color.rgb(130, 65, 255),
            Shader.TileMode.CLAMP
        )
        paint.shader = g
        c.drawRoundRect(l, t, r, b, 20f, 20f, paint)
        paint.shader = null
        text(c, label, (l + r) / 2f, (t + b) / 2f + 6f, 15f, Color.WHITE, true, Paint.Align.CENTER)
    }

    private fun header(c: Canvas, title: String, subtitle: String = "") {
        text(c, "‹", 25f, 48f, 38f, Color.WHITE, false)
        text(c, title, 58f, 42f, 23f, Color.WHITE, true)
        if (subtitle.isNotEmpty()) text(c, subtitle, 58f, 64f, 12f, 0xff9da8cf.toInt())
    }

    private fun nav(c: Canvas, w: Float, h: Float, selected: Int) {
        val top = h - 78f
        panel(c, 12f, top, w - 12f, h - 10f, 24f)
        val icons = arrayOf("⌂", "◆", "◎", "★", "●")
        val names = arrayOf("Home", "Games", "Focus", "Daily", "Profile")
        for (i in 0..4) {
            val x = w * (i + 0.5f) / 5f
            val col = if (i == selected) 0xff55e6ff.toInt() else 0xff7c86ad.toInt()
            text(c, icons[i], x, top + 28f, 21f, col, true, Paint.Align.CENTER)
            text(c, names[i], x, top + 49f, 9f, col, false, Paint.Align.CENTER)
        }
    }

    private fun drawSplash(c: Canvas, w: Float, h: Float) {
        text(c, "✦", w / 2f, h * .37f, 76f, 0xff61e8ff.toInt(), true, Paint.Align.CENTER)
        text(c, "MindBlow", w / 2f, h * .48f, 42f, Color.WHITE, true, Paint.Align.CENTER)
        text(c, "RELAX  •  FOCUS  •  REFRESH", w / 2f, h * .535f, 14f, 0xffb9c4e7.toInt(), false, Paint.Align.CENTER)
        text(c, "Small games. Big peace.", w / 2f, h * .84f, 14f, 0xff8e99c2.toInt(), false, Paint.Align.CENTER)
    }

    private fun drawOnboard(c: Canvas, w: Float, h: Float) {
        text(c, "MindBlow", w / 2f, 72f, 30f, Color.WHITE, true, Paint.Align.CENTER)
        text(c, "A tiny reset for your mind.", w / 2f, 103f, 15f, 0xffaeb8da.toInt(), false, Paint.Align.CENTER)

        val cy = h * .40f
        paint.color = 0xff182557.toInt()
        c.drawCircle(w / 2f, cy, 110f + sin(animation) * 4f, paint)
        text(c, "✦", w / 2f, cy + 30f, 82f, 0xff65e7ff.toInt(), true, Paint.Align.CENTER)

        text(c, "Play • Relax • Repeat", w / 2f, h * .62f, 25f, Color.WHITE, true, Paint.Align.CENTER)
        text(c, "Pick a quick challenge, clear your head,", w / 2f, h * .67f, 14f, 0xffaeb7d6.toInt(), false, Paint.Align.CENTER)
        text(c, "and come back feeling refreshed.", w / 2f, h * .70f, 14f, 0xffaeb7d6.toInt(), false, Paint.Align.CENTER)
        gradientButton(c, "GET STARTED", w * .16f, h * .77f, w * .84f, h * .85f)
        text(c, "●  ○  ○  ○", w / 2f, h * .91f, 12f, 0xff7b8cff.toInt(), false, Paint.Align.CENTER)
    }

    private fun drawHome(c: Canvas, w: Float, h: Float) {
        text(c, "MindBlow", 22f, 40f, 27f, Color.WHITE, true)
        text(c, "Level $level  •  Keep your mind sharp", 22f, 63f, 12f, 0xff9da8cf.toInt())
        panel(c, w - 112f, 20f, w - 20f, 56f, 16f)
        text(c, "✦ $score", w - 66f, 44f, 14f, 0xffffd66b.toInt(), true, Paint.Align.CENTER)

        panel(c, 18f, 84f, w - 18f, 190f, 24f)
        text(c, "Good to see you ✨", 34f, 115f, 16f, 0xffaeb9df.toInt())
        text(c, "Take a tiny break.", 34f, 143f, 21f, Color.WHITE, true)
        text(c, "Your mind will thank you.", 34f, 164f, 13f, 0xffaab5d6.toInt())
        gradientButton(c, "DAILY CHALLENGE  ›", 34f, 174f, w - 34f, 211f)

        text(c, "Choose your mode", 22f, 246f, 18f, Color.WHITE, true)
        gameCard(c, "✦", "Puzzle", "Think & solve", 18f, 262f, w / 2f - 8f, 352f, 0xff12d4aa)
        gameCard(c, "◉", "Memory", "Remember & grow", w / 2f + 8f, 262f, w - 18f, 352f, 0xff8b5cff)
        gameCard(c, "◎", "Focus", "Stay sharp", 18f, 362f, w / 2f - 8f, 452f, 0xffffad2e)
        gameCard(c, "◈", "Relax", "Just breathe", w / 2f + 8f, 362f, w - 18f, 452f, 0xff16cce8)

        nav(c, w, h, 0)
    }

    private fun gameCard(c: Canvas, icon: String, title: String, sub: String, l: Float, t: Float, r: Float, b: Float, accent: Int) {
        panel(c, l, t, r, b, 20f)
        paint.color = Color.argb(65, Color.red(accent), Color.green(accent), Color.blue(accent))
        c.drawCircle(l + 35f, t + 35f, 25f, paint)
        text(c, icon, l + 35f, t + 43f, 24f, Color.WHITE, true, Paint.Align.CENTER)
        text(c, title, l + 18f, t + 69f, 16f, Color.WHITE, true)
        text(c, sub, l + 18f, t + 87f, 11f, 0xff9da8cc.toInt())
    }

    private fun drawModes(c: Canvas, w: Float, h: Float) {
        header(c, "Select Mode", "Pick your kind of break")
        gameCard(c, "✦", "Puzzle", "Connect • solve", 18f, 98f, w / 2f - 8f, 205f, 0xff12d4aa)
        gameCard(c, "◉", "Memory", "Match • remember", w / 2f + 8f, 98f, w - 18f, 205f, 0xff8b5cff)
        gameCard(c, "◎", "Focus", "Tap • concentrate", 18f, 215f, w / 2f - 8f, 322f, 0xffffad2e)
        gameCard(c, "◈", "Relax", "Breathe • unwind", w / 2f + 8f, 215f, w - 18f, 322f, 0xff16cce8)
        panel(c, 18f, 337f, w - 18f, 430f, 20f)
        text(c, "Daily Challenge", 34f, 370f, 18f, Color.WHITE, true)
        text(c, "A fresh 2–5 minute challenge", 34f, 394f, 13f, 0xffa8b2d2.toInt())
        gradientButton(c, "PLAY TODAY", 34f, 400f, w - 34f, 425f)
        nav(c, w, h, 1)
    }

    private fun drawPuzzle(c: Canvas, w: Float, h: Float) {
        header(c, "Puzzle", "Find the glowing tile")
        val size = min(w * .78f, 310f)
        val left = (w - size) / 2f
        val top = 115f
        val colors = intArrayOf(0xff20dcff.toInt(), 0xffffc34d.toInt(), 0xffe94dff.toInt(), 0xff58e36a.toInt())
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val index = row * 4 + col
                val l = left + col * size / 4f + 4f
                val t = top + row * size / 4f + 4f
                val r = left + (col + 1) * size / 4f - 4f
                val b = top + (row + 1) * size / 4f - 4f
                panel(c, l, t, r, b, 14f)
                if (index == puzzleTarget) {
                    paint.color = colors[index % colors.size]
                    c.drawCircle((l + r) / 2f, (t + b) / 2f, 16f + sin(animation * 3f) * 4f, paint)
                }
            }
        }
        text(c, "Tap the glowing tile", w / 2f, top + size + 30f, 15f, 0xffb5bfdf.toInt(), false, Paint.Align.CENTER)
        text(c, "+25 XP per clear", w / 2f, top + size + 53f, 12f, 0xff7784ad.toInt(), false, Paint.Align.CENTER)
        gradientButton(c, "NEXT LEVEL", w * .20f, top + size + 70f, w * .80f, top + size + 120f)
    }

    private fun drawMemory(c: Canvas, w: Float, h: Float) {
        header(c, "Memory", "Find matching pairs")
        if (memoryShownUntil == 0L) memoryShownUntil = System.currentTimeMillis() + 2500L
        val showing = System.currentTimeMillis() < memoryShownUntil
        if (!showing && memoryFirst == -1 && memorySecond == -1) {
            // normal play state
        }

        val size = min(w * .78f, 315f)
        val left = (w - size) / 2f
        val top = 108f
        val symbols = arrayOf("✿", "★", "☾", "❖", "✦", "●", "◆", "♣", "☀")
        for (i in 0 until 9) {
            val row = i / 3
            val col = i % 3
            val l = left + col * size / 3f + 5f
            val t = top + row * size / 3f + 5f
            val r = left + (col + 1) * size / 3f - 5f
            val b = top + (row + 1) * size / 3f - 5f
            val reveal = showing || i == memoryFirst || i == memorySecond
            panel(c, l, t, r, b, 16f)
            text(c, if (reveal) symbols[memoryValues[i]] else "?", (l + r) / 2f, (t + b) / 2f + 10f, 28f, 0xff67ddff.toInt(), true, Paint.Align.CENTER)
        }
        text(c, if (showing) "Remember the board..." else "Tap two cards", w / 2f, top + size + 30f, 14f, 0xffaeb8d7.toInt(), false, Paint.Align.CENTER)
    }

    private fun drawFocus(c: Canvas, w: Float, h: Float) {
        header(c, "Focus", "Tap the moving glow")
        text(c, "Hits $focusHits", 24f, 92f, 15f, Color.WHITE, true)
        for (i in 0 until 4) {
            val angle = animation * (1.0f + i * .08f) + i * 1.57f
            val x = w / 2f + cos(angle) * (w * .27f)
            val y = 235f + sin(angle * 1.2f) * 105f
            paint.color = if (i == focusHits % 4) 0xff4feaff.toInt() else 0xff895cff.toInt()
            c.drawCircle(x, y, 17f + 7f * sin(animation * 2f + i), paint)
        }
        panel(c, 24f, 430f, w - 24f, 495f, 20f)
        text(c, "Breathe in • focus • tap", w / 2f, 462f, 15f, 0xffbac4e3.toInt(), false, Paint.Align.CENTER)
    }

    private fun drawRelax(c: Canvas, w: Float, h: Float) {
        header(c, "Relax", "Slow down for a moment")
        val cx = w / 2f
        val cy = 255f
        for (i in 0..5) {
            paint.color = Color.argb(34 - i * 4, 50, 210, 255)
            c.drawCircle(cx, cy, 48f + i * 38f + sin(animation + i) * 8f, paint)
        }
        text(c, "◈", cx, cy + 27f, 70f, 0xff8d7cff.toInt(), true, Paint.Align.CENTER)
        text(c, "Slow down", cx, 385f, 28f, Color.WHITE, true, Paint.Align.CENTER)
        text(c, "Tap anywhere for a calming ripple", cx, 414f, 14f, 0xffaeb8d8.toInt(), false, Paint.Align.CENTER)
        gradientButton(c, "I FEEL CALMER", w * .20f, 450f, w * .80f, 505f)
    }

    private fun drawDaily(c: Canvas, w: Float, h: Float) {
        header(c, "Daily Challenge", "One small win today")
        panel(c, 18f, 92f, w - 18f, 275f, 24f)
        val day = ((System.currentTimeMillis() / 86_400_000L) % 30L) + 1L
        text(c, "DAY $day", 38f, 132f, 13f, 0xff61ddff.toInt(), true)
        text(c, "Clear your mind", 38f, 168f, 24f, Color.WHITE, true)
        text(c, "Complete a quick challenge and", 38f, 195f, 13f, 0xffaeb8d7.toInt())
        text(c, "earn a little XP.", 38f, 215f, 13f, 0xffaeb8d7.toInt())
        gradientButton(c, "PLAY NOW", 38f, 225f, w - 38f, 260f)
        text(c, "Current streak", 22f, 315f, 16f, Color.WHITE, true)
        text(c, "🔥 $streak days", 22f, 350f, 28f, 0xffffb52e.toInt(), true)
        gradientButton(c, "CLAIM +50 XP", 22f, 385f, w - 22f, 438f)
        nav(c, w, h, 3)
    }

    private fun drawProfile(c: Canvas, w: Float, h: Float) {
        header(c, "Profile", "Your progress")
        panel(c, 18f, 85f, w - 18f, 215f, 24f)
        text(c, "✦", 62f, 151f, 52f, 0xff68ddff.toInt(), true, Paint.Align.CENTER)
        text(c, "MindExplorer", 105f, 126f, 21f, Color.WHITE, true)
        text(c, "Level $level", 105f, 151f, 13f, 0xffaeb8d8.toInt())
        panel(c, 105f, 170f, w - 32f, 181f, 6f)
        val progress = (score % 500) / 500f
        paint.color = 0xff4fe7ff.toInt()
        c.drawRoundRect(105f, 170f, 105f + (w - 137f) * progress, 181f, 6f, 6f, paint)
        text(c, "$score XP", 105f, 202f, 12f, 0xff8f9bc4.toInt())

        val labels = arrayOf("Games", "XP", "Streak")
        val values = arrayOf((score / 20).toString(), score.toString(), streak.toString())
        for (i in 0..2) {
            val x = 35f + i * (w - 70f) / 2f
            text(c, values[i], x, 267f, 23f, Color.WHITE, true, Paint.Align.CENTER)
            text(c, labels[i], x, 289f, 11f, 0xff8e99c0.toInt(), false, Paint.Align.CENTER)
        }
        panel(c, 18f, 315f, w - 18f, 445f, 22f)
        text(c, "⚙  Settings", 38f, 354f, 16f, Color.WHITE)
        text(c, "♫  Sound & Music", 38f, 394f, 16f, Color.WHITE)
        text(c, "☾  Dark Theme", 38f, 434f, 16f, Color.WHITE)
        nav(c, w, h, 4)
    }

    private fun drawToast(c: Canvas, w: Float, h: Float, value: String) {
        panel(c, 28f, h - 132f, w - 28f, h - 84f, 18f)
        text(c, value, w / 2f, h - 103f, 13f, Color.WHITE, true, Paint.Align.CENTER)
    }

    private fun showToast(value: String) {
        toastText = value
        toastUntil = System.currentTimeMillis() + 1100L
    }

    private fun save() {
        prefs.edit().putInt("score", score).putInt("streak", streak).apply()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val x = event.x
        val y = event.y
        val w = width.toFloat()
        val h = height.toFloat()

        when (screen) {
            Screen.SPLASH -> Unit

            Screen.ONBOARD -> {
                if (y > h * .72f) {
                    prefs.edit().putBoolean("seen", true).apply()
                    screen = Screen.HOME
                }
            }

            Screen.HOME -> {
                when {
                    y > h - 100f && x > w * .80f -> screen = Screen.PROFILE
                    y > h - 100f && x > w * .20f && x < w * .40f -> screen = Screen.MODES
                    y in 175f..225f -> screen = Screen.DAILY
                    y in 262f..352f && x < w / 2f -> screen = Screen.PUZZLE
                    y in 262f..352f && x >= w / 2f -> startMemory()
                    y in 362f..452f && x < w / 2f -> screen = Screen.FOCUS
                    y in 362f..452f && x >= w / 2f -> screen = Screen.RELAX
                }
            }

            Screen.MODES -> {
                when {
                    y < 75f -> screen = Screen.HOME
                    y > h - 100f && x > w * .80f -> screen = Screen.PROFILE
                    y in 98f..205f && x < w / 2f -> screen = Screen.PUZZLE
                    y in 98f..205f && x >= w / 2f -> startMemory()
                    y in 215f..322f && x < w / 2f -> screen = Screen.FOCUS
                    y in 215f..322f && x >= w / 2f -> screen = Screen.RELAX
                    y in 337f..440f -> screen = Screen.DAILY
                }
            }

            Screen.PUZZLE -> {
                if (y < 80f) {
                    screen = Screen.MODES
                } else if (y in 115f..425f) {
                    score += 25
                    level = max(1, score / 500 + 1)
                    puzzleTarget = Random.nextInt(16)
                    save()
                    showToast("Level cleared!  +25 XP")
                }
            }

            Screen.MEMORY -> {
                if (y < 80f) {
                    screen = Screen.MODES
                } else if (memoryShownUntil < System.currentTimeMillis()) {
                    val size = min(w * .78f, 315f)
                    val left = (w - size) / 2f
                    val top = 108f
                    if (y in top..top + size && x in left..left + size) {
                        val col = ((x - left) / (size / 3f)).toInt().coerceIn(0, 2)
                        val row = ((y - top) 
