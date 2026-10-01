package com.mindblow.app

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var mindView: MindBlowView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(7, 8, 25)
        window.navigationBarColor = Color.rgb(7, 8, 25)

        mindView = MindBlowView(this)
        setContentView(mindView)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!mindView.goBack()) {
            super.onBackPressed()
        }
    }
}

private enum class Screen {
    WELCOME,
    HOME,
    GAMES,
    GLOW,
    MEMORY,
    NUMBER,
    COLOR,
    REACTION,
    BREATHE,
    PROFILE
}

private class MindBlowView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handler = Handler(Looper.getMainLooper())

    private val prefs =
        context.getSharedPreferences("mindblow_data", Context.MODE_PRIVATE)

    private var screen =
        if (prefs.getBoolean("started", false)) {
            Screen.HOME
        } else {
            Screen.WELCOME
        }

    private var time = 0f

    private var xp = prefs.getInt("xp", 0)
    private var streak = prefs.getInt("streak", 0)

    private var message = ""
    private var messageUntil = 0L

    // Glow Tap
    private var glowX = 0f
    private var glowY = 0f
    private var glowHits = 0

    // Memory
    private val memorySymbols =
        arrayOf("★", "●", "◆", "☀", "✦", "☾", "✿", "❖")

    private var memoryCards = mutableListOf<Int>()
    private var memoryOpen1 = -1
    private var memoryOpen2 = -1
    private var memoryLocked = false

    // Number Recall
    private var numberSequence = ""
    private var numberInput = ""
    private var numberRunning = false
    private var numberLevel = 1

    // Color Focus
    private var colorTarget = 0
    private var colorRound = 0

    // Reaction
    private var reactionReady = false
    private var reactionStart = 0L
    private var reactionHits = 0

    // Breathing
    private var breathingStart = 0L

    init {
        memoryCards = createMemoryBoard()

        postInvalidateDelayed(16L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        time += 0.025f

        drawBackground(canvas, w, h)

        when (screen) {
            Screen.WELCOME -> drawWelcome(canvas, w, h)
            Screen.HOME -> drawHome(canvas, w, h)
            Screen.GAMES -> drawGames(canvas, w, h)
            Screen.GLOW -> drawGlow(canvas, w, h)
            Screen.MEMORY -> drawMemory(canvas, w, h)
            Screen.NUMBER -> drawNumber(canvas, w, h)
            Screen.COLOR -> drawColor(canvas, w, h)
            Screen.REACTION -> drawReaction(canvas, w, h)
            Screen.BREATHE -> drawBreathe(canvas, w, h)
            Screen.PROFILE -> drawProfile(canvas, w, h)
        }

        if (messageUntil > System.currentTimeMillis()) {
            drawToast(canvas, w, h)
        }

        postInvalidateDelayed(16L)
    }

    // =========================================================
    // BACKGROUND
    // =========================================================

    private fun drawBackground(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        paint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            Color.rgb(5, 8, 27),
            Color.rgb(35, 8, 58),
            Shader.TileMode.CLAMP
        )

        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val x1 = w * 0.18f + sin(time) * 45f
        val y1 = h * 0.18f + cos(time * 0.8f) * 35f

        paint.color = Color.argb(40, 0, 220, 255)
        c.drawCircle(x1, y1, 145f, paint)

        val x2 = w * 0.82f + cos(time * 0.7f) * 50f
        val y2 = h * 0.35f + sin(time) * 40f

        paint.color = Color.argb(38, 150, 60, 255)
        c.drawCircle(x2, y2, 160f, paint)

        paint.color = Color.argb(25, 20, 255, 180)
        c.drawCircle(
            w * 0.5f + sin(time * 0.5f) * 50f,
            h * 0.82f,
            180f,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(22, 100, 220, 255)

        for (i in 0 until 7) {
            c.drawCircle(
                w / 2f,
                h * 0.43f,
                70f + i * 50f + sin(time + i) * 5f,
                paint
            )
        }

        paint.style = Paint.Style.FILL
    }

    // =========================================================
    // BASIC DRAWING
    // =========================================================

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
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface = Typeface.create(
            "sans-serif",
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )

        c.drawText(value, x, y, paint)
    }

    private fun card(
        c: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        radius: Float = 22f
    ) {
        paint.shader = null
        paint.color = Color.argb(225, 14, 19, 45)
        c.drawRoundRect(l, t, r, b, radius, radius, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = Color.argb(65, 100, 190, 240)
        c.drawRoundRect(l, t, r, b, radius, radius, paint)

        paint.style = Paint.Style.FILL
    }

    private fun button(
        c: Canvas,
        label: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {
        paint.shader = LinearGradient(
            l,
            t,
            r,
            b,
            Color.rgb(20, 215, 255),
            Color.rgb(125, 65, 255),
            Shader.TileMode.CLAMP
        )

        c.drawRoundRect(l, t, r, b, 20f, 20f, paint)

        paint.shader = null

        text(
            c,
            label,
            (l + r) / 2f,
            (t + b) / 2f + 6f,
            14f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    private fun header(
        c: Canvas,
        title: String,
        subtitle: String
    ) {
        text(c, "‹", 20f, 50f, 38f)
        text(c, title, 58f, 41f, 23f, Color.WHITE, true)
        text(c, subtitle, 58f, 63f, 11f, 0xff9ca8c9.toInt())
    }

    private fun nav(
        c: Canvas,
        w: Float,
        h: Float,
        selected: Int
    ) {
        val top = h - 78f

        card(c, 10f, top, w - 10f, h - 8f, 24f)

        val icons = arrayOf("⌂", "◆", "◎", "★", "●")
        val names = arrayOf(
            "Home",
            "Games",
            "Focus",
            "Daily",
            "Profile"
        )

        for (i in 0..4) {
            val x = w * (i + 0.5f) / 5f

            val color =
                if (i == selected) {
                    0xff55e6ff.toInt()
                } else {
                    0xff7883a8.toInt()
                }

            text(
                c,
                icons[i],
                x,
                top + 29f,
                21f,
                color,
                true,
                Paint.Align.CENTER
            )

            text(
                c,
                names[i],
                x,
                top + 51f,
                9f,
                color,
                false,
                Paint.Align.CENTER
            )
        }
    }

    // =========================================================
    // WELCOME
    // =========================================================

    private fun drawWelcome(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            c,
            "MINDBLOW",
            w / 2f,
            78f,
            31f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "RESET • REFRESH • REFOCUS",
            w / 2f,
            105f,
            11f,
            0xff9ea9d0.toInt(),
            false,
            Paint.Align.CENTER
        )

        val pulse = 85f + sin(time * 2f) * 8f

        paint.color = Color.argb(35, 50, 230, 255)
        c.drawCircle(w / 2f, h * 0.40f, pulse + 45f, paint)

        paint.color = Color.argb(60, 50, 230, 255)
        c.drawCircle(w / 2f, h * 0.40f, pulse, paint)

        text(
            c,
            "✦",
            w / 2f,
            h * 0.43f,
            85f,
            0xff63e8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Give your brain",
            w / 2f,
            h * 0.57f,
            27f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "a better 5-minute break.",
            w / 2f,
            h * 0.615f,
            27f,
            0xff63e8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Mini games designed for focus, memory",
            w / 2f,
            h * 0.69f,
            13f,
            0xffaeb8d5.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            c,
            "reaction and relaxation.",
            w / 2f,
            h * 0.72f,
            13f,
            0xffaeb8d5.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            c,
            "START MINDBLOW",
            w * 0.12f,
            h * 0.80f,
            w * 0.88f,
            h * 0.88f
        )
    }

    // =========================================================
    // HOME
    // =========================================================

    private fun drawHome(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            c,
            "MindBlow",
            20f,
            42f,
            28f,
            Color.WHITE,
            true
        )

        text(
            c,
            "Your 5-minute brain reset",
            20f,
            64f,
            11f,
            0xff9ca8ca.toInt()
        )

        card(
            c,
            w - 120f,
            18f,
            w - 18f,
            62f,
            17f
        )

        text(
            c,
            "✦ $xp XP",
            w - 69f,
            46f,
            14f,
            0xffffd76a.toInt(),
            true,
            Paint.Align.CENTER
        )

        card(
            c,
            18f,
            86f,
            w - 18f,
            190f,
            24f
        )

        text(
            c,
            "READY?",
            36f,
            116f,
            11f,
            0xff55e6ff.toInt(),
            true
        )

        text(
            c,
            "Refresh your mind.",
            36f,
            148f,
            22f,
            Color.WHITE,
            true
        )

        text(
            c,
            "Pick a quick challenge below.",
            36f,
            172f,
            12f,
            0xffaab5d5.toInt()
        )

        button(
            c,
            "EXPLORE GAMES  ›",
            36f,
            205f,
            w - 36f,
            258f
        )

        text(
            c,
            "Quick Mind Games",
            20f,
            300f,
            19f,
            Color.WHITE,
            true
        )

        miniGame(
            c,
            "✦",
            "Glow Tap",
            "Speed",
            18f,
            315f,
            w / 2f - 8f,
            400f
        )

        miniGame(
            c,
            "◆",
            "Memory",
            "Recall",
            w / 2f + 8f,
            315f,
            w - 18f,
            400f
        )

        miniGame(
            c,
            "123",
            "Numbers",
            "Memory",
            18f,
            410f,
            w / 2f - 8f,
            495f
        )

        miniGame(
            c,
            "●",
            "Reaction",
            "Reflex",
            w / 2f + 8f,
            410f,
            w - 18f,
            495f
        )

        nav(c, w, h, 0)
    }

    private fun miniGame(
        c: Canvas,
        icon: String,
        title: String,
        subtitle: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {
        card(c, l, t, r, b, 20f)

        text(
            c,
            icon,
            l + 31f,
            t + 35f,
            21f,
            0xff5ce7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            title,
            l + 17f,
            t + 62f,
            15f,
            Color.WHITE,
            true
        )

        text(
            c,
            subtitle,
            l + 17f,
            t + 79f,
            10f,
            0xff9ca8c8.toInt()
        )
    }

    // =========================================================
    // GAMES MENU
    // =========================================================

    private fun drawGames(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Mind Games",
            "Choose your challenge"
        )

        gameButton(
            c,
            "✦",
            "Glow Tap",
            "Find the moving light",
            18f,
            95f,
            w - 18f,
            155f
        )

        gameButton(
            c,
            "◆",
            "Memory Match",
            "Train visual memory",
            18f,
            168f,
            w - 18f,
            228f
        )

        gameButton(
            c,
            "123",
            "Number Recall",
            "Remember the sequence",
            18f,
            241f,
            w - 18f,
            301f
        )

        gameButton(
            c,
            "●",
            "Color Focus",
            "Ignore distractions",
            18f,
            314f,
            w - 18f,
            374f
        )

        gameButton(
            c,
            "⚡",
            "Reaction Rush",
            "Test your reflexes",
            18f,
            387f,
            w - 18f,
            447f
        )

        gameButton(
            c,
            "◉",
            "Breathing Space",
            "Slow down and relax",
            18f,
            460f,
            w - 18f,
            520f
        )

        nav(c, w, h, 1)
    }

    private fun gameButton(
        c: Canvas,
        icon: String,
        title: String,
        subtitle: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {
        card(c, l, t, r, b, 19f)

        text(
            c,
            icon,
            l + 35f,
            t + 37f,
            23f,
            0xff5ee8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            title,
            l + 65f,
            t + 27f,
            16f,
            Color.WHITE,
            true
        )

        text(
            c,
            subtitle,
            l + 65f,
            t + 46f,
            10f,
            0xff9ca8c9.toInt()
        )

        text(
            c,
            "›",
            r - 25f,
            t + 37f,
            26f,
            0xff7c8bb8.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // GAME 1 - GLOW TAP
    // =========================================================

    private fun drawGlow(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Glow Tap",
            "Hit the moving light"
        )

        text(
            c,
            "SCORE  $glowHits",
            w / 2f,
            105f,
            16f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )

        if (glowX == 0f) {
            glowX = w / 2f
            glowY = h / 2f
        }

        paint.color = Color.argb(35, 40, 230, 255)
        c.drawCircle(
            glowX,
            glowY,
            55f + sin(time * 4f) * 8f,
            paint
        )

        paint.color = 0xff51e8ff.toInt()

        c.drawCircle(
            glowX,
            glowY,
            25f + sin(time * 4f) * 4f,
            paint
        )

        text(
            c,
            "TAP THE GLOW",
            w / 2f,
            h - 125f,
            13f,
            0xffaeb9d9.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // GAME 2 - MEMORY
    // =========================================================

    private fun drawMemory(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Memory Match",
            "Find matching symbols"
        )

        val size = min(w * 0.86f, 340f)
        val left = (w - size) / 2f
        val top = 100f
        val cell = size / 4f

        for (i in 0 until 16) {

            val row = i / 4
            val col = i % 4

            val l = left + col * cell + 5f
            val t = top + row * cell + 5f
            val r = left + (col + 1) * cell - 5f
            val b = top + (row + 1) * cell - 5f

            card(c, l, t, r, b, 14f)

            val open =
                i == memoryOpen1 ||
                i == memoryOpen2

            if (open) {
                val symbol =
                    memorySymbols[
                        memoryCards[i]
                    ]

                text(
                    c,
                    symbol,
                    (l + r) / 2f,
                    (t + b) / 2f + 11f,
                    28f,
                    0xff60e7ff.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            } else {
                text(
                    c,
                    "?",
                    (l + r) / 2f,
                    (t + b) / 2f + 10f,
                    24f,
                    0xff64739d.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            }
        }

        text(
            c,
            "Match two cards",
            w / 2f,
            top + size + 32f,
            14f,
            0xffaeb9d8.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // GAME 3 - NUMBER RECALL
    // =========================================================

    private fun drawNumber(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Number Recall",
            "Train your working memory"
        )

        text(
            c,
            "LEVEL $numberLevel",
            w / 2f,
            110f,
            14f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )

        card(
            c,
            28f,
            140f,
            w - 28f,
            230f,
            24f
        )

        if (numberRunning) {
            text(
                c,
                numberSequence,
                w / 2f,
                205f,
                35f,
                0xff60e8ff.toInt(),
                true,
                Paint.Align.CENTER
            )
        } else {
            text(
                c,
                if (numberInput.isEmpty())
                    "Enter sequence"
                else
                    numberInput,
                w / 2f,
                205f,
                25f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        button(
            c,
            if (numberRunning) "MEMORIZE" else "CHECK",
            w * 0.18f,
            270f,
            w * 0.82f,
            325f
        )

        button(
            c,
            "NEW SEQUENCE",
            w * 0.18f,
            345f,
            w * 0.82f,
            400f
        )

        text(
            c,
            "Tap NEW SEQUENCE to begin",
            w / 2f,
            440f,
            12f,
            0xff9da8c8.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // GAME 4 - COLOR FOCUS
    // =========================================================

    private fun drawColor(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Color Focus",
            "Tap the correct color"
        )

        text(
            c,
            "ROUND $colorRound",
            w / 2f,
            105f,
            14f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )

        val names = arrayOf(
            "CYAN",
            "PURPLE",
            "GREEN",
            "ORANGE"
        )

        val colors = intArrayOf(
            0xff31ddff.toInt(),
            0xffa15cff.toInt(),
            0xff42e58b.toInt(),
            0xffffa63d.toInt()
        )

        text(
            c,
            names[colorTarget],
            w / 2f,
            165f,
            30f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        for (i in 0 until 4) {

            val col = i % 2
            val row = i / 2

            val l = 30f + col * (w / 2f - 35f)
            val t = 220f + row * 100f
            val r = w / 2f - 10f + col * (w / 2f - 35f)
            val b = t + 75f

            paint.color = colors[i]

            c.drawRoundRect(
                l,
                t,
                r,
                b,
                22f,
                22f,
                paint
            )

            text(
                c,
                names[i],
                (l + r) / 2f,
                t + 45f,
                12f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }
    }

    // =========================================================
    // GAME 5 - REACTION
    // =========================================================

    private fun drawReaction(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Reaction Rush",
            "Wait for green"
        )

        if (!reactionReady) {

            text(
                c,
                "GET READY",
                w / 2f,
                210f,
                31f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )

            button(
                c,
                "START",
                w * 0.20f,
                300f,
                w * 0.80f,
                360f
            )

        } else {

            paint.color = 0xff42e58b.toInt()

            c.drawCircle(
                w / 2f,
                260f,
                95f + sin(time * 3f) * 8f,
                paint
            )

            text(
                c,
                "TAP NOW!",
                w / 2f,
                270f,
                25f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        text(
            c,
            "Hits: $reactionHits",
            w / 2f,
            430f,
            15f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // GAME 6 - BREATHING
    // =========================================================

    private fun drawBreathe(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Breathing Space",
            "Slow your rhythm"
        )

        val elapsed =
            System.currentTimeMillis() - breathingStart

        val cycle = (elapsed % 8000L).toFloat() / 8000f

        val radius =
            if (cycle < 0.5f) {
                60f + cycle * 2f * 90f
            } else {
                150f - (cycle - 0.5f) * 2f * 90f
            }

        paint.color = Color.argb(45, 60, 225, 255)

        c.drawCircle(
            w / 2f,
            260f,
            radius + 30f,
            paint
        )

        paint.color = 0xff55e5ff.toInt()

        c.drawCircle(
            w / 2f,
            260f,
            radius,
            paint
        )

        val instruction =
            if (cycle < 0.5f)
                "BREATHE IN"
            else
                "BREATHE OUT"

        text(
            c,
            instruction,
            w / 2f,
            268f,
            21f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Follow the circle",
            w / 2f,
            420f,
            14f,
            0xffaeb9d8.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // PROFILE
    // =========================================================

    private fun drawProfile(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Profile",
            "Your MindBlow progress"
        )

        card(
            c,
            18f,
            90f,
            w - 18f,
            220f,
            25f
        )

        text(
            c,
            "✦",
            68f,
            170f,
            55f,
            0xff5ce7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Mind Explorer",
            112f,
            135f,
            20f,
            Color.WHITE,
            true
        )

        text(
            c,
            "LEVEL ${xp / 500 + 1}",
            112f,
            163f,
            12f,
            0xff61e7ff.toInt(),
            true
        )

        text(
            c,
            "$xp XP",
            112f,
            190f,
            14f,
            0xffffd66b.toInt(),
            true
        )

        text(
            c,
            "🔥 $streak day streak",
            30f,
            270f,
            17f,
            0xffffa63d.toInt(),
            true
        )

        text(
            c,
            "Completed games: ${xp / 25}",
            30f,
            302f,
            14f,
            0xffaeb9d8.toInt()
        )

        card(
            c,
            18f,
            340f,
            w - 18f,
            470f,
            22f
        )

        text(
            c,
            "♫  Mind Refreshing Music",
            38f,
            385f,
            16f,
            Color.WHITE,
            true
        )

        text(
            c,
            "Music can be added through res/raw",
            38f,
            410f,
            11f,
            0xff9ca8c8.toInt()
        )

        text(
            c,
            "✦  More experiences coming",
            38f,
            450f,
            14f,
            0xff62e8ff.toInt()
        )

        nav(c, w, h, 4)
    }

    // =========================================================
    // TOAST
    // =========================================================

    private fun toast(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        card(
            c,
            25f,
            h - 130f,
            w - 25f,
            h - 82f,
            18f
        )

        text(
            c,
            message,
            w / 2f,
            h - 101f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    private fun showMessage(value: String) {
        message = value
        messageUntil =
            System.currentTimeMillis() + 1400L
    }

    private fun addXp(amount: Int) {
        xp += amount

        if (xp % 100 == 0) {
            streak++
        }

        prefs.edit()
            .putInt("xp", xp)
            .putInt("streak", streak)
            .apply()
    }

    // =========================================================
    // MEMORY SETUP
    // =========================================================

    private fun createMemoryBoard(): MutableList<Int> {
        val list = mutableListOf<Int>()

        for (i in 0 until 8) {
            list.add(i)
            list.add(i)
        }

        list.shuffle()

        return list
    }

    // =========================================================
    // TOUCH
    // =========================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {

        if (event.action != MotionEvent.ACTION_UP) {
            return true
        }

        val x = event.x
        val y = event.y
        val w = width.toFloat()
        val h = height.toFloat()

        when (screen) {

            Screen.WELCOME -> {
                if (y > h * 0.74f) {
                    prefs.edit()
                        .putBoolean("started", true)
                        .apply()

                    screen = Screen.HOME
                }
            }

            Screen.HOME -> {
                homeTouch(x, y, w, h)
            }

            Screen.GAMES -> {
                gamesTouch(y, w, h)
            }

            Screen.GLOW -> {
                glowTouch(x, y, w, h)
            }

            Screen.MEMORY -> {
                memoryTouch(x, y, w)
            }

            Screen.NUMBER -> {
                numberTouch(x, y, w)
            }

            Screen.COLOR -> {
                colorTouch(x, y, w)
            }

            Screen.REACTION -> {
                reactionTouch(x, y, w)
            }

            Screen.BREATHE -> {
                if (y < 80f) {
                    screen = Screen.GAMES
                }
            }

            Screen.PROFILE -> {
                if (y < 80f) {
                    screen = Screen.HOME
                }
            }
        }

        invalidate()

        return true
    }

    // =========================================================
    // HOME TOUCH
    // =========================================================

    private fun homeTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y > h - 100f) {

            if (x > w * 0.80f) {
                screen = Screen.PROFILE
                return
            }

            if (x in w * 0.20f..w * 0.40f) {
                screen = Screen.GAMES
                return
            }
        }

        if (y in 195f..270f) {
            screen = Screen.GAMES
            return
        }

        if (y in 315f..400f) {

            if (x < w / 2f) {
                startGlow()
            } else {
                startMemory()
            }

            return
        }

        if (y in 410f..500f) {

            if (x < w / 2f) {
                startNumber()
            } else {
                startReaction()
            }
        }
    }

    // =========================================================
    // GAMES TOUCH
    // =========================================================

    private fun gamesTouch(
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 80f) {
            screen = Screen.HOME
            return
        }

        if (y > h - 100f) {
            return
        }

        when {
            y in 95f..155f -> startGlow()
            y in 168f..228f -> startMemory()
            y in 241f..301f -> startNumber()
            y in 314f..374f -> startColor()
            y in 387f..447f -> startReaction()
            y in 460f..540f -> startBreathe()
        }
    }

    // =========================================================
    // GLOW GAME
    // =========================================================

    private fun startGlow() {
        screen = Screen.GLOW
        glowHits = 0
        glowX = width * 0.5f
        glowY = height * 0.45f
    }

    private fun glowTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        val distance =
            kotlin.math.sqrt(
                ((x - glowX) * (x - glowX)) +
                    ((y - glowY) * (y - glowY))
            )

        if (distance < 60f) {

            glowHits++

            addXp(10)

            glowX =
                Random.nextFloat() *
                    (w - 100f) + 50f

            glowY =
                Random.nextFloat() *
                    (h - 220f) + 130f

            showMessage("+10 XP  Great!")
        }
    }

    // =========================================================
    // MEMORY GAME
    // =========================================================

    private fun startMemory() {
        screen = Screen.MEMORY

        memoryCards = createMemoryBoard()
        memoryOpen1 = -1
        memoryOpen2 = -1
        memoryLocked = false
    }

    private fun memoryTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        if (memoryLocked) return

        val size = min(w * 0.86f, 340f)
        val left = (w - size) / 2f
        val top = 100f
        val cell = size / 4f

        if (
            x < left ||
            x > left + size ||
            y < top ||
            y > top + size
        ) {
            return
        }

        val col =
            ((x - left) / cell)
                .toInt()
                .coerceIn(0, 3)

        val row =
            ((y - top) / cell)
                .toInt()
                .coerceIn(0, 3)

        val index = row * 4 + col

        if (index == memoryOpen1) return

        if (memoryOpen1 == -1) {
            memoryOpen1 = index
            return
        }

        if (memoryOpen2 == -1) {

            memoryOpen2 = index

            if (
                memoryCards[memoryOpen1] ==
                memoryCards[memoryOpen2]
            ) {

                addXp(20)

                showMessage("+20 XP  Match!")

                handler.postDelayed({

                    memoryOpen1 = -1
                    memoryOpen2 = -1

                    if (memoryCards.isEmpty()) {
                        memoryCards = createMemoryBoard()
                    }

                    invalidate()

                }, 450L)

            } else {

                memoryLocked = true

                showMessage("Try again")

                handler.postDelayed({

                    memoryOpen1 = -1
                    memoryOpen2 = -1
                    memoryLocked = false

                    invalidate()

                }, 700L)
            }
        }
    }

    // =========================================================
    // NUMBER GAME
    // =========================================================

    private fun startNumber() {
        screen = Screen.NUMBER
        numberLevel = 1
        numberInput = ""
        numberRunning = false
        newNumberSequence()
    }

    private fun newNumberSequence() {

        val length =
            (3 + numberLevel)
                .coerceAtMost(8)

        numberSequence =
            buildString {
                repeat(length) {
                    append(Random.nextInt(0, 10))
                }
            }

        numberInput = ""
        numberRunning = true

        handler.postDelayed({

            numberRunning = false
            invalidate()

        }, 1800L)

        invalidate()
    }

    private fun numberTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        if (y in 345f..410f) {
            newNumberSequence()
            return
        }

        if (y in 270f..335f) {

            if (numberRunning) return

            if (numberInput == numberSequence) {

                addXp(25)

                numberLevel++

                showMessage("+25 XP  Perfect!")

                newNumberSequence()

            } else {

                showMessage("Sequence was $numberSequence")
                numberInput = ""
            }

            return
        }

        if (!numberRunning && y in 140f..230f) {

            if (numberInput.length < 8) {

                val digit =
                    Random.nextInt(0, 10)

                numberInput += digit.toString()

                invalidate()
            }
        }
    }

    // =========================================================
    // COLOR GAME
    // =========================================================

    private fun startColor() {
        screen = Screen.COLOR
        colorRound = 1
        colorTarget = Random.nextInt(4)
    }

    private fun colorTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        if (y < 220f) return

        val col =
            if (x < w / 2f) 0 else 1

        val row =
            if (y < 395f) 0 else 1

        val selected =
            row * 2 + col

        if (selected == colorTarget) {

            addXp(15)

            colorRound++

            colorTarget =
                Random.nextInt(4)

            showMessage("+15 XP  Nice focus!")

        } else {
            showMessage("Focus on the target")
        }
    }

    // =========================================================
    // REACTION GAME
    // =========================================================

    private fun startReaction() {

        screen = Screen.REACTION
        reactionReady = false
        reactionHits = 0
    }

    private fun reactionTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        if (!reactionReady) {

            if (y in 280f..380f) {

                reactionReady = true

                handler.postDelayed({

                    reactionStart =
                        System.currentTimeMillis()

                    invalidate()

                }, Random.nextLong(1000L, 3000L))
            }

        } else {

            val reactionTime =
                System.currentTimeMillis() -
                    reactionStart

            if (reactionStart == 0L) {
                return
            }

            reactionReady = false
            reactionStart = 0L

            reactionHits++

            val gained =
                when {
                    reactionTime < 300L -> 35
                    reactionTime < 500L -> 25
                    else -> 15
                }

            addXp(gained)

            showMessage(
                "${reactionTime}ms  +$gained XP"
            )
        }
    }

    // =========================================================
    // BREATHING
    // =========================================================

    private fun startBreathe() {
        screen = Screen.BREATHE
        breathingStart =
            System.currentTimeMillis()
    }

    // =========================================================
    // BACK
    // =========================================================

    fun goBack(): Boolean {

        if (screen == Screen.HOME) {
            return false
        }

        screen = Screen.HOME

        invalidate()

        return true
    }
}
