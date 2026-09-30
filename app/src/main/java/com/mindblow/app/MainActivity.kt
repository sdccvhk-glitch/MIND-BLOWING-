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
    private var puzzleRound = 1

    // Memory
    private val symbols = arrayOf(
        "★", "◆", "●",
        "✦", "☀", "☾",
        "✿", "❖", "♣"
    )

    private var memoryBoard = MutableList(9) { it }.apply { shuffle() }
    private var memoryFirst = -1
    private var memorySecond = -1
    private var memoryPreviewUntil = 0L
    private var memoryMatches = 0

    // Focus
    private var focusHits = 0
    private var focusTargetX = 0f
    private var focusTargetY = 0f
    private var focusTargetRadius = 28f
    private var focusStarted = false

    // Relax
    private var relaxTaps = 0

    // Daily
    private var dailyCompleted = prefs.getBoolean("daily_completed", false)

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
            System.currentTimeMillis() - splashStarted > 1400L
        ) {
            screen = Screen.WELCOME
        }

        postInvalidateDelayed(16L)
    }

    // =========================================================
    // BACKGROUND
    // =========================================================

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

        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val t = animationTime

        paint.color = Color.argb(45, 20, 220, 255)
        canvas.drawCircle(
            w * 0.16f + sin(t) * 35f,
            h * 0.18f + cos(t) * 25f,
            145f,
            paint
        )

        paint.color = Color.argb(36, 155, 70, 255)
        canvas.drawCircle(
            w * 0.84f + cos(t * 0.8f) * 40f,
            h * 0.38f + sin(t) * 35f,
            165f,
            paint
        )

        paint.color = Color.argb(26, 30, 255, 180)
        canvas.drawCircle(
            w * 0.50f + sin(t * 0.6f) * 45f,
            h * 0.84f,
            180f,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(22, 100, 220, 255)

        for (i in 0 until 7) {
            canvas.drawCircle(
                w / 2f,
                h * 0.43f,
                70f + i * 55f + sin(t + i) * 5f,
                paint
            )
        }

        paint.style = Paint.Style.FILL
    }

    // =========================================================
    // TEXT
    // =========================================================

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

        canvas.drawText(value, x, y, paint)
    }

    // =========================================================
    // CARD
    // =========================================================

    private fun drawCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float = 20f
    ) {
        paint.shader = null
        paint.color = Color.argb(225, 13, 19, 46)

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
        paint.color = Color.argb(65, 110, 160, 230)

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

    // =========================================================
    // BUTTON
    // =========================================================

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

    // =========================================================
    // HEADER
    // =========================================================

    private fun drawHeader(
        canvas: Canvas,
        title: String,
        subtitle: String
    ) {
        text(canvas, "‹", 22f, 51f, 38f, Color.WHITE)

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

    // =========================================================
    // NAVIGATION
    // =========================================================

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

    // =========================================================
    // SPLASH
    // =========================================================

    private fun drawSplash(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        val pulse = 1f + sin(animationTime * 3f) * 0.08f

        text(
            canvas,
            "✦",
            w / 2f,
            h * 0.39f,
            78f * pulse,
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

    // =========================================================
    // WELCOME
    // =========================================================

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

    // =========================================================
    // HOME
    // =========================================================

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
            "DAILY CHALLENGE ›",
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

        drawNavigation(canvas, w, h, 0)
    }

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

    // =========================================================
    // GAMES
    // =========================================================

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

        drawNavigation(canvas, w, h, 1)
    }

    // =========================================================
    // PUZZLE
    // =========================================================

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

        text(
            canvas,
            "ROUND $puzzleRound",
            w / 2f,
            91f,
            12f,
            0xff6be7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        val size = min(w * 0.78f, 310f)
        val left = (w - size) / 2f
        val top = 105f
        val cell = size / 4f

        for (i in 0 until 16) {
            val row = i / 4
            val col = i % 4

            val l = left + col * cell + 4f
            val tt = top + row * cell + 4f
            val r = left + (col + 1) * cell - 4f
            val b = top + (row + 1) * cell - 4f

            drawCard(canvas, l, tt, r, b, 13f)

            if (i == puzzleTarget) {
                paint.color = 0xff48e7ff.toInt()

                canvas.drawCircle(
                    (l + r) / 2f,
                    (tt + b) / 2f,
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

    // =========================================================
    // MEMORY
    // =========================================================

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

        text(
            canvas,
            "MATCHES $memoryMatches / 4",
            w / 2f,
            91f,
            12f,
            0xff6be7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        val size = min(w * 0.82f, 320f)
        val left = (w - size) / 2f
        val top = 105f
        val cell = size / 3f

        val preview =
            System.currentTimeMillis() < memoryPreviewUntil

        for (i in 0 until 9) {
            val row = i / 3
            val col = i % 3

            val l = left + col * cell + 5f
            val tt = top + row * cell + 5f
            val r = left + (col + 1) * cell - 5f
            val b = top + (row + 1) * cell - 5f

            drawCard(canvas, l, tt, r, b, 15f)

            val revealed =
                preview ||
                i == memoryFirst ||
                i == memorySecond

            if (revealed) {
                text(
                    canvas,
                    symbols[memoryBoard[i]],
                    (l + r) / 2f,
                    (tt + b) / 2f + 10f,
                    28f,
                    0xff67ddff.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            } else {
                text(
                    canvas,
                    "?",
                    (l + r) / 2f,
                    (tt + b) / 2f + 10f,
                    27f,
                    0xff59658f.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            }
        }

        text(
            canvas,
            if (preview) {
                "Remember the board..."
            } else {
                "Tap two cards"
            },
            w / 2f,
            top + size + 32f,
            14f,
            0xffaeb8d7.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // FOCUS
    // =========================================================

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
            "HITS  $focusHits",
            24f,
            94f,
            15f,
            Color.WHITE,
            true
        )

        if (!focusStarted) {
            drawCard(
                canvas,
                30f,
                150f,
                w - 30f,
                350f,
                24f
            )

            text(
                canvas,
                "FOCUS TEST",
                w / 2f,
                205f,
                14f,
                0xff62e8ff.toInt(),
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                "Tap the moving target",
                w / 2f,
                245f,
                22f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                "Stay alert and follow the glow.",
                w / 2f,
                275f,
                13f,
                0xffaeb8d8.toInt(),
                false,
                Paint.Align.CENTER
            )

            drawButton(
                canvas,
                "START",
                55f,
                300f,
                w - 55f,
                350f
            )
        } else {
            paint.color = 0xff55e8ff.toInt()

            canvas.drawCircle(
                focusTargetX,
                focusTargetY,
                focusTargetRadius +
                    sin(animationTime * 5f) * 5f,
                paint
            )

            paint.color = 0xff9b6cff.toInt()

            canvas.drawCircle(
                focusTargetX,
                focusTargetY,
                focusTargetRadius * 0.45f,
                paint
            )

            text(
                canvas,
                "FOLLOW THE GLOW",
                w / 2f,
                h - 130f,
                13f,
                0xffaeb8d8.toInt(),
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                "+5 XP each hit",
                w / 2f,
                h - 105f,
                11f,
                0xff7884ad.toInt(),
                false,
                Paint.Align.CENTER
            )
        }
    }

    // =========================================================
    // RELAX
    // =========================================================

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
        val cy = h * 0.43f

        val pulse =
            1f + (sin(animationTime * 1.6f) + 1f) * 0.16f

        for (i in 0..6) {
            paint.color =
                Color.argb(
                    maxAlpha(38 - i * 5),
                    50,
                    210,
                    255
                )

            canvas.drawCircle(
                cx,
                cy,
                (45f + i * 35f) * pulse,
                paint
            )
        }

        text(
            canvas,
            "◈",
            cx,
            cy + 24f,
            68f,
            0xff8d7cff.toInt(),
            true,
            Paint.Align.CENTER
        )

        val phase =
            if ((animationTime % 8f) < 4f) {
                "BREATHE IN"
            } else {
                "BREATHE OUT"
            }

        text(
            canvas,
            phase,
            cx,
            cy + 110f,
            19f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Tap anywhere to create a calming ripple",
            cx,
            cy + 145f,
            13f,
            0xffaeb8d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Relax taps: $relaxTaps",
            cx,
            cy + 180f,
            12f,
            0xff7884ad.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    private fun maxAlpha(value: Int): Int {
        return value.coerceIn(0, 255)
    }

    // =========================================================
    // DAILY
    // =========================================================

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
            300f,
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
            if (dailyCompleted) {
                "Challenge complete!"
            } else {
                "Clear your mind"
            },
            38f,
            165f,
            24f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            if (dailyCompleted) {
                "Come back tomorrow for another challenge."
            } else {
                "Complete this quick challenge and"
            },
            38f,
            195f,
            13f,
            0xffaeb8d7.toInt()
        )

        if (!dailyCompleted) {
            text(
                canvas,
                "earn +50 XP.",
                38f,
                217f,
                13f,
                0xffaeb8d7.toInt()
            )

            drawButton(
                canvas,
                "COMPLETE CHALLENGE",
                38f,
                235f,
                w - 38f,
                278f
            )
        } else {
            text(
                canvas,
                "✓ +50 XP earned",
                38f,
                252f,
                16f,
                0xff5de7b2.toInt(),
                true
            )
        }

        text(
            canvas,
            "🔥  $streak day streak",
            22f,
            345f,
            19f,
            0xffffb52e.toInt(),
            true
        )

        text(
            canvas,
            "Total XP: $score",
            22f,
            378f,
            14f,
            0xffaeb8d8.toInt(),
            false
        )

        drawNavigation(canvas, w, h, 3)
    }

    // =========================================================
    // PROFILE
    // =========================================================

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
            220f,
            24f
        )

        text(
            canvas,
            "✦",
            65f,
            155f,
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
            180f,
            13f,
            0xff8f9bc4.toInt()
        )

        text(
            canvas,
            "Games completed: ${score / 25}",
            35f,
            260f,
            16f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Streak: $streak days",
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
            455f,
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
            400f,
            16f,
            Color.WHITE
        )

        text(
            canvas,
            "☾  Dark Theme",
            38f,
            442f,
            16f,
            Color.WHITE
        )

        drawNavigation(canvas, w, h, 4)
    }

    // =========================================================
    // TOAST
    // =========================================================

    private fun drawToast(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        drawCard(
            canvas,
            25f,
            h - 135f,
            w - 25f,
            h - 83f,
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
        messageUntil = System.currentTimeMillis() + 1400L
    }

    private fun save() {
        prefs.edit()
            .putInt("score", score)
            .putInt("streak", streak)
            .putBoolean("daily_completed", dailyCompleted)
            .apply()
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

            Screen.SPLASH -> {
                screen = Screen.WELCOME
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
                homeTouch(x, y, w, h)
            }

            Screen.GAMES -> {
                gamesTouch(x, y, w, h)
            }

            Screen.PUZZLE -> {
                puzzleTouch(x, y, w, h)
            }

            Screen.MEMORY -> {
                memoryTouch(x, y, w, h)
            }

            Screen.FOCUS -> {
                focusTouch(x, y, w, h)
            }

            Screen.RELAX -> {
                relaxTouch(y)
            }

            Screen.DAILY -> {
                dailyTouch(y, w)
            }

            Screen.PROFILE -> {
                profileTouch(y)
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
        if (y > h - 95f) {

            when {
                x < w * 0.20f -> {
                    screen = Screen.HOME
                }

                x < w * 0.40f -> {
                    screen = Screen.GAMES
                }

                x < w * 0.60f -> {
                    focusStarted = false
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

        if (y in 250f..355f) {
            if (x < w / 2f) {
                puzzleRound = 1
                puzzleTarget = Random.nextInt(16)
                screen = Screen.PUZZLE
            } else {
                startMemory()
            }

            return
        }

        if (y in 355f..465f) {
            if (x < w / 2f) {
                focusStarted = false
                screen = Screen.FOCUS
            } else {
                screen = Screen.RELAX
            }
        }
    }

    // =========================================================
    // GAMES TOUCH
    // =========================================================

    private fun gamesTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (y < 80f) {
            screen = Screen.HOME
            return
        }

        if (y > h - 95f) {

            when {
                x < w * 0.20f -> screen = Screen.HOME
                x < w * 0.40f -> screen = Screen.GAMES
                x < w * 0.60f -> {
                    focusStarted = false
                    screen = Screen.FOCUS
                }
                x < w * 0.80f -> screen = Screen.DAILY
                else -> screen = Screen.PROFILE
            }

            return
        }

        if (y in 90f..210f) {

            if (x < w / 2f) {
                puzzleRound = 1
                puzzleTarget = Random.nextInt(16)
                screen = Screen.PUZZLE
            } else {
                startMemory()
            }

            return
        }

        if (y in 210f..335f) {

            if (x < w / 2f) {
                focusStarted = false
                screen = Screen.FOCUS
            } else {
                screen = Screen.RELAX
            }

            return
        }

        if (y in 335f..445f) {
            screen = Screen.DAILY
        }
    }

    // =========================================================
    // PUZZLE TOUCH
    // =========================================================

    private fun puzzleTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        val size = min(w * 0.78f, 310f)
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
            puzzleRound++

            puzzleTarget = Random.nextInt(16)

            save()

            showMessage(
                "Correct! +25 XP"
            )

        } else {

            showMessage(
                "Try the glowing tile!"
            )
        }
    }

    // =========================================================
    // MEMORY
    // =========================================================

    private fun startMemory() {

        memoryBoard =
            MutableList(9) { it }.apply {
                shuffle()
            }

        memoryFirst = -1
        memorySecond = -1
        memoryMatches = 0

        memoryPreviewUntil =
            System.currentTimeMillis() + 2200L

        screen = Screen.MEMORY
    }

    private fun memoryTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
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

        val size = min(w * 0.82f, 320f)
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

        if (index == memoryFirst) {
            return
        }

        if (memoryFirst == -1) {

            memoryFirst = index

        } else if (memorySecond == -1) {

            memorySecond = index

            if (
                memoryBoard[memoryFirst] ==
                memoryBoard[memorySecond]
            ) {

                memoryMatches++
                score += 20

                save()

                showMessage(
                    "Match! +20 XP"
                )

                postDelayed(
                    {
                        memoryFirst = -1
                        memorySecond = -1

                        if (memoryMatches >= 4) {
                            showMessage(
                                "Memory cleared! +50 XP"
                            )
                            score += 50
                            save()
                        }

                        invalidate()
                    },
                    500L
                )

            } else {

                showMessage("Not a match")

                postDelayed(
                    {
                        memoryFirst = -1
                        memorySecond = -1
                        invalidate()
                    },
                    650L
                )
            }
        }
    }

    // =========================================================
    // FOCUS
    // =========================================================

    private fun startFocus(w: Float, h: Float) {

        focusStarted = true

        focusTargetX =
            Random.nextFloat() *
                (w - 100f) + 50f

        focusTargetY =
            Random.nextFloat() *
                (h - 330f) + 150f

        focusTargetRadius =
            Random.nextInt(22, 34).toFloat()
    }

    private fun focusTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        if (!focusStarted) {

            startFocus(w, h)
            return
        }

        val dx = x - focusTargetX
        val dy = y - focusTargetY

        val distance =
            kotlin.math.sqrt(
                dx * dx + dy * dy
            )

        if (distance <= focusTargetRadius + 20f) {

            focusHits++
            score += 5

            save()

            showMessage(
                "Great focus! +5 XP"
            )

            startFocus(w, h)
        }
    }

    // =========================================================
    // RELAX TOUCH
    // =========================================================

    private fun relaxTouch(y: Float) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        relaxTaps++

        if (relaxTaps % 5 == 0) {
            score += 5
            save()

            showMessage(
                "Calm moment +5 XP"
            )
        } else {
            showMessage(
                "Breathe in... breathe out..."
            )
        }
    }

    // =========================================================
    // DAILY TOUCH
    // =========================================================

    private fun dailyTouch(
        y: Float,
        w: Float
    ) {
        if (y < 80f) {
            screen = Screen.HOME
            return
        }

        if (y > hMinusNavigation()) {
            return
        }

        if (!dailyCompleted && y in 220f..300f) {

            dailyCompleted = true
            score += 50
            streak++

            save()

            showMessage(
                "Daily complete! +50 XP"
            )
        }
    }

    private fun hMinusNavigation(): Float {
        return height.toFloat() - 95f
    }

    // =========================================================
    // PROFILE TOUCH
    // =========================================================

    private fun profileTouch(y: Float) {

        if (y < 80f) {
            screen = Screen.HOME
        }
    }

    // =========================================================
    // BACK
    // =========================================================

    fun goBack(): Boolean {

        return when (screen) {

            Screen.SPLASH -> false

            Screen.WELCOME -> {
                false
            }

            Screen.HOME -> {
                false
            }

            Screen.GAMES,
            Screen.PROFILE,
            Screen.DAILY -> {
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
