package com.mindblow.app

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!gameView.goBack()) {
            super.onBackPressed()
        }
    }
}

private enum class Screen {
    SPLASH,
    WELCOME,
    HOME,
    GAMES,
    PUZZLE,
    MEMORY,
    FOCUS,
    RELAX,
    DAILY,
    PROFILE
}

private class MindBlowView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs =
        context.getSharedPreferences("mindblow_data", Context.MODE_PRIVATE)

    private var screen =
        if (prefs.getBoolean("welcome_seen", false)) {
            Screen.HOME
        } else {
            Screen.SPLASH
        }

    private var splashStarted = System.currentTimeMillis()

    private var animationTime = 0f

    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)

    // Puzzle
    private var puzzleTarget = Random.nextInt(16)

    // Memory
    private val symbols = arrayOf(
        "★", "◆", "●",
        "✦", "☀", "☾",
        "✿", "❖", "♣"
    )

    private var memoryBoard =
        MutableList(9) { it }.apply { shuffle() }

    private var memoryFirst = -1
    private var memorySecond = -1
    private var memoryPreviewUntil = 0L

    // Focus
    private var focusHits = 0
    private var focusTarget = 0

    // Relax
    private var relaxTaps = 0

    private var message = ""
    private var messageUntil = 0L

    init {
        isFocusable = true
        postInvalidateDelayed(16L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        animationTime += 0.025f

        drawBackground(canvas, w, h)

        when (screen) {
            Screen.SPLASH -> drawSplash(canvas, w, h)
            Screen.WELCOME -> drawWelcome(canvas, w, h)
            Screen.HOME -> drawHome(canvas, w, h)
            Screen.GAMES -> drawGames(canvas, w, h)
            Screen.PUZZLE -> drawPuzzle(canvas, w, h)
            Screen.MEMORY -> drawMemory(canvas, w, h)
            Screen.FOCUS -> drawFocus(canvas, w, h)
            Screen.RELAX -> drawRelax(canvas, w, h)
            Screen.DAILY -> drawDaily(canvas, w, h)
            Screen.PROFILE -> drawProfile(canvas, w, h)
        }

        if (messageUntil > System.currentTimeMillis()) {
            drawToast(canvas, w, h)
        }

        if (
            screen == Screen.SPLASH &&
            System.currentTimeMillis() - splashStarted > 1500L
        ) {
            screen = Screen.WELCOME
        }

        postInvalidateDelayed(16L)
    }

    // ---------------------------------------------------------
    // BACKGROUND
    // ---------------------------------------------------------

    private fun drawBackground(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        paint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            Color.rgb(4, 7, 25),
            Color.rgb(39, 7, 61),
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(
            0f,
            0f,
            w,
            h,
            paint
        )

        paint.shader = null

        val t = animationTime

        paint.color = Color.argb(
            42,
            20,
            220,
            255
        )

        canvas.drawCircle(
            w * 0.18f + sin(t) * 35f,
            h * 0.20f + cos(t) * 28f,
            145f,
            paint
        )

        paint.color = Color.argb(
            35,
            155,
            70,
            255
        )

        canvas.drawCircle(
            w * 0.82f + cos(t * 0.8f) * 40f,
            h * 0.38f + sin(t) * 35f,
            165f,
            paint
        )

        paint.color = Color.argb(
            25,
            30,
            255,
            180
        )

        canvas.drawCircle(
            w * 0.50f + sin(t * 0.6f) * 45f,
            h * 0.84f,
            180f,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(
            22,
            100,
            220,
            255
        )

        for (i in 0 until 6) {
            canvas.drawCircle(
                w / 2f,
                h * 0.43f,
                70f + i * 55f + sin(t + i) * 5f,
                paint
            )
        }

        paint.style = Paint.Style.FILL
    }

    // ---------------------------------------------------------
    // TEXT
    // ---------------------------------------------------------

    private fun text(
        canvas: Canvas,
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

        canvas.drawText(
            value,
            x,
            y,
            paint
        )
    }

    // ---------------------------------------------------------
    // CARD
    // ---------------------------------------------------------

    private fun drawCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float = 20f
    ) {
        paint.shader = null
        paint.color = Color.argb(
            225,
            13,
            19,
            46
        )

        canvas.drawRoundRect(
            left,
            top,
            right,
            bottom,
            radius,
            radius,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = Color.argb(
            65,
            110,
            160,
            230
        )

        canvas.drawRoundRect(
            left,
            top,
            right,
            bottom,
            radius,
            radius,
            paint
        )

        paint.style = Paint.Style.FILL
    }

    // ---------------------------------------------------------
    // BUTTON
    // ---------------------------------------------------------

    private fun drawButton(
        canvas: Canvas,
        label: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        paint.shader = LinearGradient(
            left,
            top,
            right,
            bottom,
            Color.rgb(20, 215, 255),
            Color.rgb(135, 65, 255),
            Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(
            left,
            top,
            right,
            bottom,
            18f,
            18f,
            paint
        )

        paint.shader = null

        text(
            canvas,
            label,
            (left + right) / 2f,
            (top + bottom) / 2f + 5f,
            14f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // HEADER
    // ---------------------------------------------------------

    private fun drawHeader(
        canvas: Canvas,
        title: String,
        subtitle: String
    ) {
        text(
            canvas,
            "‹",
            22f,
            50f,
            38f,
            Color.WHITE,
            false
        )

        text(
            canvas,
            title,
            60f,
            42f,
            23f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            60f,
            63f,
            11f,
            0xff9da8cf.toInt()
        )
    }

    // ---------------------------------------------------------
    // BOTTOM NAV
    // ---------------------------------------------------------

    private fun drawNavigation(
        canvas: Canvas,
        w: Float,
        h: Float,
        selected: Int
    ) {
        val top = h - 82f

        drawCard(
            canvas,
            10f,
            top,
            w - 10f,
            h - 8f,
            23f
        )

        val icons = arrayOf(
            "⌂",
            "◆",
            "◎",
            "★",
            "●"
        )

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
                canvas,
                icons[i],
                x,
                top + 29f,
                20f,
                color,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
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

    // ---------------------------------------------------------
    // SPLASH
    // ---------------------------------------------------------

    private fun drawSplash(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "✦",
            w / 2f,
            h * 0.39f,
            78f,
            0xff62e8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "MindBlow",
            w / 2f,
            h * 0.49f,
            42f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "RELAX  •  FOCUS  •  REFRESH",
            w / 2f,
            h * 0.545f,
            13f,
            0xffb7c2e5.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // WELCOME
    // ---------------------------------------------------------

    private fun drawWelcome(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "MindBlow",
            w / 2f,
            75f,
            31f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "A tiny reset for your mind.",
            w / 2f,
            103f,
            14f,
            0xffaeb8d9.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "✦",
            w / 2f,
            h * 0.47f,
            95f,
            0xff63e8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Play • Relax • Repeat",
            w / 2f,
            h * 0.61f,
            25f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Quick challenges designed to refresh",
            w / 2f,
            h * 0.66f,
            14f,
            0xffaeb8d9.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "your attention without pressure.",
            w / 2f,
            h * 0.695f,
            14f,
            0xffaeb8d9.toInt(),
            false,
            Paint.Align.CENTER
        )

        drawButton(
            canvas,
            "GET STARTED",
            w * 0.16f,
            h * 0.77f,
            w * 0.84f,
            h * 0.85f
        )
    }

    // ---------------------------------------------------------
    // HOME
    // ---------------------------------------------------------

    private fun drawHome(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "MindBlow",
            20f,
            40f,
            27f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Level ${score / 500 + 1}  •  Keep your mind sharp",
            20f,
            62f,
            11f,
            0xff9ca8cc.toInt()
        )

        drawCard(
            canvas,
            w - 112f,
            18f,
            w - 18f,
            57f,
            16f
        )

        text(
            canvas,
            "✦ $score",
            w - 65f,
            43f,
            14f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )

        drawCard(
            canvas,
            18f,
            82f,
            w - 18f,
            190f,
            23f
        )

        text(
            canvas,
            "Good to see you ✨",
            34f,
            114f,
            15f,
            0xffaeb9df.toInt()
        )

        text(
            canvas,
            "Take a tiny break.",
            34f,
            143f,
            21f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Your mind will thank you.",
            34f,
            164f,
            12f,
            0xffaab5d6.toInt()
        )

        drawButton(
            canvas,
            "DAILY CHALLENGE  ›",
            34f,
            174f,
            w - 34f,
            215f
        )

        text(
            canvas,
            "Choose your mode",
            20f,
            247f,
            18f,
            Color.WHITE,
            true
        )

        drawGameCard(
            canvas,
            "✦",
            "Puzzle",
            "Think & solve",
            18f,
            262f,
            w / 2f - 8f,
            352f
        )

        drawGameCard(
            canvas,
            "◉",
            "Memory",
            "Remember & grow",
            w / 2f + 8f,
            262f,
            w - 18f,
            352f
        )

        drawGameCard(
            canvas,
            "◎",
            "Focus",
            "Stay sharp",
            18f,
            362f,
            w / 2f - 8f,
            452f
        )

        drawGameCard(
            canvas,
            "◈",
            "Relax",
            "Just breathe",
            w / 2f + 8f,
            362f,
            w - 18f,
            452f
        )

        drawNavigation(
            canvas,
            w,
            h,
            0
        )
    }

    // ---------------------------------------------------------
    // GAME CARD
    // ---------------------------------------------------------

    private fun drawGameCard(
        canvas: Canvas,
        icon: String,
        title: String,
        subtitle: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        drawCard(
            canvas,
            left,
            top,
            right,
            bottom,
            19f
        )

        text(
            canvas,
            icon,
            left + 33f,
            top + 42f,
            24f,
            0xff5ce7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            title,
            left + 18f,
            top + 68f,
            16f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            left + 18f,
            top + 86f,
            10f,
            0xff9da8cc.toInt()
        )
    }

    // ---------------------------------------------------------
    // GAMES
    // ---------------------------------------------------------

    private fun drawGames(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        drawHeader(
            canvas,
            "Select Mode",
            "Pick your kind of break"
        )

        drawGameCard(
            canvas,
            "✦",
            "Puzzle",
            "Connect • solve",
            18f,
            95f,
            w / 2f - 8f,
            205f
        )

        drawGameCard(
            canvas,
            "◉",
            "Memory",
            "Match • remember",
            w / 2f + 8f,
            95f,
            w - 18f,
            205f
        )

        drawGameCard(
            canvas,
            "◎",
            "Focus",
            "Tap • concentrate",
            18f,
            218f,
            w / 2f - 8f,
            328f
        )

        drawGameCard(
            canvas,
            "◈",
            "Relax",
            "Breathe • unwind",
            w / 2f + 8f,
            218f,
            w - 18f,
            328f
        )

        drawCard(
            canvas,
            18f,
            342f,
            w - 18f,
            430f,
            20f
        )

        text(
            canvas,
            "Daily Challenge",
            34f,
            374f,
            18f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "A fresh 2–5 minute challenge",
            34f,
            397f,
            12f,
            0xffa8b2d2.toInt()
        )

        drawButton(
            canvas,
            "PLAY TODAY",
            34f,
            405f,
            w - 34f,
            426f
        )

        drawNavigation(
            canvas,
            w,
            h,
            1
        )
    }

    // ---------------------------------------------------------
    // PUZZLE
    // ---------------------------------------------------------

    private fun drawPuzzle(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        drawHeader(
            canvas,
            "Puzzle",
            "Find the glowing tile"
        )

        val size = min(
            w * 0.78f,
            310f
        )

        val left = (w - size) / 2f
        val top = 105f
        val cell = size / 4f

        for (i in 0 until 16) {

            val row = i / 4
            val col = i % 4

            val l = left + col * cell + 4f
            val t = top + row * cell + 4f
            val r = left + (col + 1) * cell - 4f
            val b = top + (row + 1) * cell - 4f

            drawCard(
                canvas,
                l,
                t,
                r,
                b,
                13f
            )

            if (i == puzzleTarget) {

                paint.color = 0xff48e7ff.toInt()

                canvas.drawCircle(
                    (l + r) / 2f,
                    (t + b) / 2f,
                    16f + sin(animationTime * 3f) * 4f,
                    paint
                )
            }
        }

        text(
            canvas,
            "Tap the glowing tile",
            w / 2f,
            top + size + 30f,
            14f,
            0xffb5bfdf.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "+25 XP per clear",
            w / 2f,
            top + size + 52f,
            11f,
            0xff7784ad.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // MEMORY
    // ---------------------------------------------------------

    private fun drawMemory(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        drawHeader(
            canvas,
            "Memory",
            "Remember the symbols"
        )

        val size = min(
            w * 0.78f,
            315f
        )

        val left = (w - size) / 2f
        val top = 105f
        val cell = size / 3f

        val preview =
            System.currentTimeMillis() < memoryPreviewUntil

        for (i in 0 until 9) {

            val row = i / 3
            val col = i % 3

            val l = left + col * cell + 5f
            val t = top + row * cell + 5f
            val r = left + (col + 1) * cell - 5f
            val b = top + (row + 1) * cell - 5f

            drawCard(
                canvas,
                l,
                t,
                r,
                b,
                15f
            )

            val reveal =
                preview ||
                i == memoryFirst ||
                i == memorySecond

            val value =
                if (reveal) {
                    symbols[memoryBoard[i]]
                } else {
                    "?"
                }

            text(
                canvas,
                value,
                (l + r) / 2f,
                (t + b) / 2f + 10f,
                27f,
                0xff67ddff.toInt(),
                true,
                Paint.Align.CENTER
            )
        }

        text(
            canvas,
            if (preview) {
                "Remember the board..."
            } else {
                "Tap two cards"
            },
            w / 2f,
            top + size + 30f,
            14f,
            0xffaeb8d7.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // FOCUS
    // ---------------------------------------------------------

    private fun drawFocus(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        drawHeader(
            canvas,
            "Focus",
            "Tap the moving glow"
        )

        text(
            canvas,
            "Hits $focusHits",
            24f,
            94f,
            15f,
            Color.WHITE,
            true
        )

        for (i in 0 until 4) {

            val angle =
                animationTime * (1f + i * 0.08f) +
                i * 1.57f

            val x =
                w / 2f +
                cos(angle.toDouble()).toFloat() *
                w * 0.27f

            val y =
                245f +
                sin((angle * 1.2f).toDouble()).toFloat() *
                105f

            val active =
                i == focusTarget

            paint.color =
                if (active) {
                    0xff4feaff.toInt()
                } else {
                    0xff7655dc.toInt()
                }

            canvas.drawCircle(
                x,
                y,
                if (active) {
                    23f + 5f * sin(animationTime * 3f)
                } else {
                    15f
                },
                paint
            )
        }

        drawCard(
            canvas,
            24f,
            430f,
            w - 24f,
            495f,
            19f
        )

        text(
            canvas,
            "Tap the bright circle",
            w / 2f,
            462f,
            14f,
            0xffbac4e3.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // RELAX
    // ---------------------------------------------------------

    private fun drawRelax(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        drawHeader(
            canvas,
            "Relax",
            "Slow down for a moment"
        )

        val cx = w / 2f
        val cy = 245f

        for (i in 0..5) {

            paint.color = Color.argb(
                34 - i * 4,
                50,
                210,
                255
            )

            canvas.drawCircle(
                cx,
                cy,
                45f +
                    i * 36f +
                    sin(animationTime + i) * 7f,
                paint
            )
        }

        text(
            canvas,
            "◈",
            cx,
            cy + 25f,
            68f,
            0xff8d7cff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Slow down",
            cx,
            380f,
            28f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Tap anywhere for a calming ripple",
            cx,
            410f,
            14f,
            0xffaeb8d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Relax taps: $relaxTaps",
            cx,
            450f,
            12f,
            0xff6bdfff.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // DAILY
    // ---------------------------------------------------------

    private fun drawDaily(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        drawHeader(
            canvas,
            "Daily Challenge",
            "One small win today"
        )

        drawCard(
            canvas,
            18f,
            90f,
            w - 18f,
            275f,
            24f
        )

        text(
            canvas,
            "TODAY",
            38f,
            128f,
            13f,
            0xff61ddff.toInt(),
            true
        )

        text(
            canvas,
            "Clear your mind",
            38f,
            164f,
            24f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Complete a quick challenge and",
            38f,
            192f,
            13f,
            0xffaeb8d7.toInt()
        )

        text(
            canvas,
            "earn a little XP.",
            38f,
            213f,
            13f,
            0xffaeb8d7.toInt()
        )

        drawButton(
            canvas,
            "PLAY NOW",
            38f,
            225f,
            w - 38f,
            262f
        )

        text(
            canvas,
            "🔥  $streak day streak",
            22f,
            322f,
            19f,
            0xffffb52e.toInt(),
            true
        )

        drawButton(
            canvas,
            "CLAIM +50 XP",
            22f,
            350f,
            w - 22f,
            402f
        )

        drawNavigation(
            canvas,
            w,
            h,
            3
        )
    }

    // ---------------------------------------------------------
    // PROFILE
    // ---------------------------------------------------------

    private fun drawProfile(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        drawHeader(
            canvas,
            "Profile",
            "Your progress"
        )

        drawCard(
            canvas,
            18f,
            85f,
            w - 18f,
            210f,
            24f
        )

        text(
            canvas,
            "✦",
            62f,
            153f,
            52f,
            0xff68ddff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "MindExplorer",
            105f,
            126f,
            21f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Level ${score / 500 + 1}",
            105f,
            151f,
            13f,
            0xffaeb8d8.toInt()
        )

        text(
            canvas,
            "$score XP",
            105f,
            184f,
            13f,
            0xff8f9bc4.toInt()
        )

        text(
            canvas,
            "Games  ${score / 25}",
            35f,
            258f,
            16f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Streak  $streak",
            35f,
            292f,
            16f,
            0xffffb52e.toInt(),
            true
        )

        drawCard(
            canvas,
            18f,
            320f,
            w - 18f,
            430f,
            22f
        )

        text(
            canvas,
            "⚙  Settings",
            38f,
            358f,
            16f,
            Color.WHITE
        )

        text(
            canvas,
            "♫  Sound & Music",
            38f,
            398f,
            16f,
            Color.WHITE
        )

        text(
            canvas,
            "☾  Dark Theme",
            38f,
            438f,
            16f,
            Color.WHITE
        )

        drawNavigation(
            canvas,
            w,
            h,
            4
        )
    }

    // ---------------------------------------------------------
    // TOAST
    // ---------------------------------------------------------

    private fun drawToast(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        drawCard(
            canvas,
            28f,
            h - 132f,
            w - 28f,
            h - 84f,
            18f
        )

        text(
            canvas,
            message,
            w / 2f,
            h - 103f,
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

    private fun saveProgress() {
        prefs.edit()
            .putInt("score", score)
            .putInt("streak", streak)
            .apply()
    }

    // ---------------------------------------------------------
    // TOUCH
    // ---------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {

        if (event.action != MotionEvent.ACTION_UP) {
            return true
        }

        val x = event.x
        val y = event.y
        val w = width.toFloat()
        val h = height.toFloat()

        when (screen) {

            Screen.SPLASH -> {
                // Splash automatically advances.
            }

            Screen.WELCOME -> {
                if (y > h * 0.70f) {
                    prefs.edit()
                        .putBoolean("welcome_seen", true)
                        .apply()

                    screen = Screen.HOME
                }
            }

            Screen.HOME -> {
                handleHomeTouch(x, y, w, h)
            }

            Screen.GAMES -> {
                handleGamesTouch(x, y, w, h)
            }

            Screen.PUZZLE -> {
                handlePuzzleTouch(x, y, w)
            }

            Screen.MEMORY -> {
                handleMemoryTouch(x, y, w)
            }

            Screen.FOCUS -> {
                handleFocusTouch(x, y, w)
            }

            Screen.RELAX -> {
                handleRelaxTouch(y)
            }

            Screen.DAILY -> {
                handleDailyTouch(x, y, w, h)
            }

            Screen.PROFILE -> {
                handleProfileTouch(y)
            }
        }

        invalidate()
        return true
    }

    // ---------------------------------------------------------
    // HOME TOUCH
    // ---------------------------------------------------------

    private fun handleHomeTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y > h - 100f) {

            when {
                x < w * 0.20f -> {
                    screen = Screen.HOME
                }

                x < w * 0.40f -> {
                    screen = Screen.GAMES
                }

                x < w * 0.60f -> {
                    screen = Screen.FOCUS
                }

                x < w * 0.80f -> {
                    screen = Screen.DAILY
                }

                else -> {
                    screen = Screen.PROFILE
                }
            }

            return
        }

        if (y in 165f..225f) {
            screen = Screen.DAILY
            return
        }

        if (y in 255f..355f) {

            if (x < w / 2f) {
                screen = Screen.PUZZLE
            } else {
                startMemory()
            }

            return
        }

        if (y in 355f..465f) {

            if (x < w / 2f) {
                screen = Screen.FOCUS
            } else {
                screen = Screen.RELAX
            }
        }
    }

    // ---------------------------------------------------------
    // GAMES TOUCH
    // ---------------------------------------------------------

    private fun handleGamesTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 80f) {
            screen = Screen.HOME
            return
        }

        if (y > h - 100f) {

            when {
                x < w * 0.20f -> screen = Screen.HOME
                x < w * 0.40f -> screen = Screen.GAMES
                x < w * 0.60f -> screen = Screen.FOCUS
                x < w * 0.80f -> screen = Screen.DAILY
                else -> screen = Screen.PROFILE
            }

            return
        }

        if (y in 90f..210f) {

            if (x < w / 2f) {
                screen = Screen.PUZZLE
            } else {
                startMemory()
            }

            return
        }

        if (y in 215f..335f) {

            if (x < w / 2f) {
                screen = Screen.FOCUS
            } else {
                screen = Screen.RELAX
            }

            return
        }

        if (y in 340f..440f) {
            screen = Screen.DAILY
        }
    }

    // ---------------------------------------------------------
    // PUZZLE TOUCH
    // ---------------------------------------------------------

    private fun handlePuzzleTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        val size = min(
            w * 0.78f,
            310f
        )

        val left = (w - size) / 2f
        val top = 105f
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

        if (index == puzzleTarget) {

            score += 25

            puzzleTarget = Random.nextInt(16)

            saveProgress()

            showMessage(
                "Correct! +25 XP"
            )

        } else {

            showMessage(
                "Try the glowing tile"
            )
        }
    }

    // ---------------------------------------------------------
    // MEMORY TOUCH
    // ---------------------------------------------------------

    private fun handleMemoryTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        if (
            System.currentTimeMillis() <
            memoryPreviewUntil
        ) {
            return
        }

        val size = min(
            w * 0.78f,
            315f
        )

        val left = (w - size) / 2f
        val top = 105f
        val cell = size / 3f

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
                .coerceIn(0, 2)

        val row =
            ((y - top) / cell)
                .toInt()
                .coerceIn(0, 2)

        val index = row * 3 + col

        if (memoryFirst == -1) {

            memoryFirst = index
            return
        }

        if (
            memorySecond == -1 &&
            index != memoryFirst
        ) {

            memorySecond = index

            if (
                memoryBoard[memoryFirst] ==
                memoryBoard[memorySecond]
            ) {

                score += 20

                saveProgress()

                showMessage(
                    "Match! +20 XP"
                )

                postDelayed({

                    memoryFirst = -1
                    memorySecond = -1

                    memoryBoard =
                        MutableList(9) { it }
                            .apply { shuffle() }

                    memoryPreviewUntil =
                        System.currentTimeMillis() + 1200L

                    invalidate()

                }, 500L)

            } else {

                showMessage(
                    "Not a match"
                )

                postDelayed({

                    memoryFirst = -1
                    memorySecond = -1

                    invalidate()

                }, 700L)
            }
        }
    }

    private fun startMemory() {

        memoryBoard =
            MutableList(9) { it }
                .apply { shuffle() }

        memoryFirst = -1
        memorySecond = -1

        memoryPreviewUntil =
            System.currentTimeMillis() + 2200L

        screen = Screen.MEMORY
    }

    // ---------------------------------------------------------
    // FOCUS TOUCH
    // ---------------------------------------------------------

    private fun handleFocusTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        val angle =
            animationTime *
            (1f + focusTarget * 0.08f) +
            focusTarget * 1.57f

        val targetX =
            w / 2f +
            cos(angle.toDouble()).toFloat() *
            w * 0.27f

        val targetY =
            245f +
            sin((angle * 1.2f).toDouble()).toFloat() *
            105f

        val dx = x - targetX
        val dy = y - targetY

        val distanceSquared =
            dx * dx + dy * dy

        if (distanceSquared < 45f * 45f) {

            focusHits++
            score += 5

            focusTarget =
                Random.nextInt(4)

            saveProgress()

            showMessage(
                "Great focus! +5 XP"
            )

        } else {

            showMessage(
                "Follow the bright glow"
            )
        }
    }

    // ---------------------------------------------------------
    // RELAX TOUCH
    // ---------------------------------------------------------

    private fun handleRelaxTouch(y: Float) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        relaxTaps++

        if (relaxTaps % 5 == 0) {

            score += 5

            saveProgress()

            showMessage(
                "Nice and calm! +5 XP"
            )

        } else {

            showMessage(
                "Breathe in... and out..."
            )
        }
    }

    // ---------------------------------------------------------
    // DAILY TOUCH
    // ---------------------------------------------------------

    private fun handleDailyTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 80f) {
            screen = Screen.HOME
            return
        }

        if (y > h - 100f) {

            when {
                x < w * 0.20f -> screen = Screen.HOME
                x < w * 0.40f -> screen = Screen.GAMES
                x < w * 0.60f -> screen = Screen.FOCUS
                x < w * 0.80f -> screen = Screen.DAILY
                else -> screen = Screen.PROFILE
            }

            return
        }

        if (y in 215f..275f) {

            score += 50
            streak++

            saveProgress()

            showMessage(
                "Daily complete! +50 XP"
            )
        }

        if (y in 340f..420f) {

            score += 50
            streak++

            saveProgress()

            showMessage(
                "Reward claimed! +50 XP"
            )
        }
    }

    // ---------------------------------------------------------
    // PROFILE TOUCH
    // ---------------------------------------------------------

    private fun handleProfileTouch(y: Float) {

        if (y < 80f) {
            screen = Screen.HOME
        }
    }

    // ---------------------------------------------------------
    // BACK
    // ---------------------------------------------------------

    fun goBack(): Boolean {

        return when (screen) {

            Screen.SPLASH -> false

            Screen.WELCOME -> {
                screen = Screen.SPLASH
                invalidate()
                true
            }

            Screen.HOME -> false

            Screen.GAMES,
            Screen.DAILY,
            Screen.PROFILE -> {
                screen = Screen.HOME
                invalidate()
                true
            }

            Screen.PUZZLE,
            Screen.MEMORY,
            Screen.FOCUS,
            Screen.RELAX -> {
                screen = Screen.GAMES
                invalidate()
                true
            }
        }
    }
}
