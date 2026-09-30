package com.mindblow.app

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
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

        window.statusBarColor = Color.rgb(5, 7, 22)
        window.navigationBarColor = Color.rgb(5, 7, 22)

        mindView = MindBlowView(this)
        setContentView(mindView)
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (!mindView.goBack()) {
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

    private var screen: Screen

    private var animation = 0f

    private var score: Int
    private var streak: Int

    private var puzzleTarget = Random.nextInt(16)

    private var memoryValues = MutableList(9) { it }.apply {
        shuffle()
    }

    private var memoryFirst = -1
    private var memorySecond = -1
    private var memoryPreviewUntil = 0L

    private var focusTarget = 0
    private var focusHits = 0

    private var message = ""
    private var messageUntil = 0L

    private var splashStarted = System.currentTimeMillis()

    init {
        val seen = prefs.getBoolean("welcome_seen", false)

        screen = if (seen) {
            Screen.SPLASH
        } else {
            Screen.SPLASH
        }

        score = prefs.getInt("score", 0)
        streak = prefs.getInt("streak", 0)

        postInvalidateDelayed(16L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        animation += 0.018f

        drawAnimatedBackground(canvas, width, height)

        when (screen) {

            Screen.SPLASH -> drawSplash(canvas, width, height)

            Screen.WELCOME -> drawWelcome(canvas, width, height)

            Screen.HOME -> drawHome(canvas, width, height)

            Screen.GAMES -> drawGames(canvas, width, height)

            Screen.PUZZLE -> drawPuzzle(canvas, width, height)

            Screen.MEMORY -> drawMemory(canvas, width, height)

            Screen.FOCUS -> drawFocus(canvas, width, height)

            Screen.RELAX -> drawRelax(canvas, width, height)

            Screen.DAILY -> drawDaily(canvas, width, height)

            Screen.PROFILE -> drawProfile(canvas, width, height)
        }

        if (messageUntil > System.currentTimeMillis()) {
            drawToast(canvas, width, height)
        }

        if (
            screen == Screen.SPLASH &&
            System.currentTimeMillis() - splashStarted > 1500L
        ) {
            screen = if (prefs.getBoolean("welcome_seen", false)) {
                Screen.HOME
            } else {
                Screen.WELCOME
            }
        }

        postInvalidateDelayed(16L)
    }

    // =========================================================
    // BACKGROUND
    // =========================================================

    private fun drawAnimatedBackground(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        paint.shader = LinearGradient(
            0f,
            0f,
            width,
            height,
            Color.rgb(4, 8, 28),
            Color.rgb(30, 8, 55),
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(
            0f,
            0f,
            width,
            height,
            paint
        )

        paint.shader = null

        val wave = animation

        paint.color = Color.argb(30, 0, 220, 255)

        canvas.drawCircle(
            width * 0.18f + sin(wave) * 45f,
            height * 0.18f + cos(wave) * 25f,
            145f,
            paint
        )

        paint.color = Color.argb(28, 160, 70, 255)

        canvas.drawCircle(
            width * 0.82f + cos(wave * 0.7f) * 50f,
            height * 0.35f + sin(wave) * 35f,
            165f,
            paint
        )

        paint.color = Color.argb(20, 0, 255, 180)

        canvas.drawCircle(
            width * 0.5f + sin(wave * 0.5f) * 50f,
            height * 0.82f,
            180f,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(25, 100, 220, 255)

        for (i in 0 until 7) {
            canvas.drawCircle(
                width / 2f,
                height * 0.45f,
                70f + i * 55f + sin(wave + i) * 6f,
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

        canvas.drawText(
            value,
            x,
            y,
            paint
        )
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

        paint.color = Color.argb(
            225,
            12,
            18,
            45
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
            70,
            100,
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
    // SPLASH
    // =========================================================

    private fun drawSplash(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        text(
            canvas,
            "✦",
            width / 2f,
            height * 0.40f,
            78f,
            Color.rgb(90, 230, 255),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "MindBlow",
            width / 2f,
            height * 0.50f,
            42f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "RELAX  •  FOCUS  •  REFRESH",
            width / 2f,
            height * 0.56f,
            13f,
            Color.rgb(180, 195, 225),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // WELCOME
    // =========================================================

    private fun drawWelcome(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        text(
            canvas,
            "MindBlow",
            width / 2f,
            75f,
            32f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "A tiny reset for your mind.",
            width / 2f,
            105f,
            14f,
            Color.rgb(175, 188, 220),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "✦",
            width / 2f,
            height * 0.47f,
            96f,
            Color.rgb(95, 230, 255),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Play • Relax • Repeat",
            width / 2f,
            height * 0.62f,
            25f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Quick challenges designed to refresh",
            width / 2f,
            height * 0.67f,
            14f,
            Color.rgb(175, 188, 220),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "your attention without pressure.",
            width / 2f,
            height * 0.705f,
            14f,
            Color.rgb(175, 188, 220),
            false,
            Paint.Align.CENTER
        )

        drawButton(
            canvas,
            "GET STARTED",
            width * 0.16f,
            height * 0.78f,
            width * 0.84f,
            height * 0.86f
        )
    }

    // =========================================================
    // HOME
    // =========================================================

    private fun drawHome(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        text(
            canvas,
            "MindBlow",
            20f,
            40f,
            28f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Level ${score / 500 + 1}  •  Keep your mind sharp",
            20f,
            63f,
            11f,
            Color.rgb(155, 170, 205)
        )

        drawCard(
            canvas,
            width - 115f,
            18f,
            width - 18f,
            58f,
            16f
        )

        text(
            canvas,
            "✦ $score",
            width - 66f,
            44f,
            14f,
            Color.rgb(255, 215, 100),
            true,
            Paint.Align.CENTER
        )

        drawCard(
            canvas,
            18f,
            84f,
            width - 18f,
            190f,
            23f
        )

        text(
            canvas,
            "Good to see you ✨",
            34f,
            115f,
            15f,
            Color.rgb(175, 190, 225)
        )

        text(
            canvas,
            "Take a tiny break.",
            34f,
            145f,
            21f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Your mind will thank you.",
            34f,
            166f,
            12f,
            Color.rgb(170, 185, 215)
        )

        drawButton(
            canvas,
            "DAILY CHALLENGE  ›",
            34f,
            180f,
            width - 34f,
            220f
        )

        text(
            canvas,
            "Choose your mode",
            20f,
            250f,
            18f,
            Color.WHITE,
            true
        )

        gameCard(
            canvas,
            "✦",
            "Puzzle",
            "Think & solve",
            18f,
            265f,
            width / 2f - 8f,
            355f
        )

        gameCard(
            canvas,
            "◉",
            "Memory",
            "Remember & grow",
            width / 2f + 8f,
            265f,
            width - 18f,
            355f
        )

        gameCard(
            canvas,
            "◎",
            "Focus",
            "Stay sharp",
            18f,
            365f,
            width / 2f - 8f,
            455f
        )

        gameCard(
            canvas,
            "◈",
            "Relax",
            "Just breathe",
            width / 2f + 8f,
            365f,
            width - 18f,
            455f
        )

        drawNavigation(
            canvas,
            width,
            height,
            0
        )
    }

    // =========================================================
    // GAME CARD
    // =========================================================

    private fun gameCard(
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
            left + 34f,
            top + 42f,
            25f,
            Color.rgb(90, 230, 255),
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
            Color.rgb(155, 170, 205)
        )
    }

    // =========================================================
    // NAVIGATION
    // =========================================================

    private fun drawNavigation(
        canvas: Canvas,
        width: Float,
        height: Float,
        selected: Int
    ) {
        val top = height - 78f

        drawCard(
            canvas,
            12f,
            top,
            width - 12f,
            height - 10f,
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

            val x = width * (i + 0.5f) / 5f

            val color =
                if (i == selected) {
                    Color.rgb(85, 230, 255)
                } else {
                    Color.rgb(120, 130, 165)
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
    // HEADER
    // =========================================================

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
            Color.rgb(155, 170, 205)
        )
    }

    // =========================================================
    // GAMES
    // =========================================================

    private fun drawGames(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        drawHeader(
            canvas,
            "Select Mode",
            "Pick your kind of break"
        )

        gameCard(
            canvas,
            "✦",
            "Puzzle",
            "Connect • solve",
            18f,
            95f,
            width / 2f - 8f,
            205f
        )

        gameCard(
            canvas,
            "◉",
            "Memory",
            "Match • remember",
            width / 2f + 8f,
            95f,
            width - 18f,
            205f
        )

        gameCard(
            canvas,
            "◎",
            "Focus",
            "Tap • concentrate",
            18f,
            218f,
            width / 2f - 8f,
            328f
        )

        gameCard(
            canvas,
            "◈",
            "Relax",
            "Breathe • unwind",
            width / 2f + 8f,
            218f,
            width - 18f,
            328f
        )

        drawCard(
            canvas,
            18f,
            342f,
            width - 18f,
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
            Color.rgb(165, 180, 215)
        )

        drawButton(
            canvas,
            "PLAY TODAY",
            34f,
            405f,
            width - 34f,
            426f
        )

        drawNavigation(
            canvas,
            width,
            height,
            1
        )
    }

    // =========================================================
    // PUZZLE
    // =========================================================

    private fun drawPuzzle(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        drawHeader(
            canvas,
            "Puzzle",
            "Find the glowing tile"
        )

        val size = min(
            width * 0.78f,
            310f
        )

        val left = (width - size) / 2f
        val top = 110f

        for (i in 0 until 16) {

            val row = i / 4
            val column = i % 4

            val tileLeft =
                left + column * size / 4f + 4f

            val tileTop =
                top + row * size / 4f + 4f

            val tileRight =
                left + (column + 1) * size / 4f - 4f

            val tileBottom =
                top + (row + 1) * size / 4f - 4f

            drawCard(
                canvas,
                tileLeft,
                tileTop,
                tileRight,
                tileBottom,
                13f
            )

            if (i == puzzleTarget) {

                paint.color = Color.rgb(
                    70,
                    230,
                    255
                )

                canvas.drawCircle(
                    (tileLeft + tileRight) / 2f,
                    (tileTop + tileBottom) / 2f,
                    17f + sin(animation * 3f) * 4f,
                    paint
                )
            }
        }

        text(
            canvas,
            "Tap the glowing tile",
            width / 2f,
            top + size + 32f,
            14f,
            Color.rgb(180, 195, 225),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "+25 XP per clear",
            width / 2f,
            top + size + 54f,
            11f,
            Color.rgb(120, 135, 175),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // MEMORY
    // =========================================================

    private fun drawMemory(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        drawHeader(
            canvas,
            "Memory",
            "Remember the symbols"
        )

        val size = min(
            width * 0.78f,
            315f
        )

        val left = (width - size) / 2f
        val top = 105f

        val symbols = arrayOf(
            "✿",
            "★",
            "☾",
            "❖",
            "✦",
            "●",
            "◆",
            "♣",
            "☀"
        )

        val preview =
            System.currentTimeMillis() < memoryPreviewUntil

        for (i in 0 until 9) {

            val row = i / 3
            val column = i % 3

            val tileLeft =
                left + column * size / 3f + 5f

            val tileTop =
                top + row * size / 3f + 5f

            val tileRight =
                left + (column + 1) * size / 3f - 5f

            val tileBottom =
                top + (row + 1) * size / 3f - 5f

            drawCard(
                canvas,
                tileLeft,
                tileTop,
                tileRight,
                tileBottom,
                15f
            )

            val revealed =
                preview ||
                        i == memoryFirst ||
                        i == memorySecond

            val value =
                if (revealed) {
                    symbols[memoryValues[i]]
                } else {
                    "?"
                }

            text(
                canvas,
                value,
                (tileLeft + tileRight) / 2f,
                (tileTop + tileBottom) / 2f + 10f,
                27f,
                Color.rgb(100, 220, 255),
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
            width / 2f,
            top + size + 32f,
            14f,
            Color.rgb(175, 190, 220),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // FOCUS
    // =========================================================

    private fun drawFocus(
        canvas: Canvas,
        width: Float,
        height: Float
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
                animation * (1f + i * 0.08f) +
                        i * 1.57f

            val x =
                width / 2f +
                        cos(angle.toDouble()).toFloat() *
                        width * 0.27f

            val y =
                240f +
                        sin((angle * 1.2f).toDouble()).toFloat() *
                        105f

            paint.color =
                if (i == focusTarget) {
                    Color.rgb(75, 235, 255)
                } else {
                    Color.rgb(135, 85, 255)
                }

            canvas.drawCircle(
                x,
                y,
                18f + 5f * sin(animation * 2f + i),
                paint
            )
        }

        drawCard(
            canvas,
            24f,
            430f,
            width - 24f,
            495f,
            19f
        )

        text(
            canvas,
            "Tap the cyan glow",
            width / 2f,
            462f,
            14f,
            Color.rgb(190, 205, 230),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // RELAX
    // =========================================================

    private fun drawRelax(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        drawHeader(
            canvas,
            "Relax",
            "Slow down for a moment"
        )

        val centerX = width / 2f
        val centerY = 245f

        for (i in 0..6) {

            paint.color = Color.argb(
                38 - i * 4,
                50,
                210,
                255
            )

            canvas.drawCircle(
                centerX,
                centerY,
                45f +
                        i * 36f +
                        sin(animation + i) * 7f,
                paint
            )
        }

        text(
            canvas,
            "◈",
            centerX,
            centerY + 25f,
            68f,
            Color.rgb(145, 125, 255),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Slow down",
            centerX,
            380f,
            28f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Tap anywhere for a calming ripple",
            centerX,
            410f,
            14f,
            Color.rgb(175, 190, 220),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // DAILY
    // =========================================================

    private fun drawDaily(
        canvas: Canvas,
        width: Float,
        height: Float
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
            width - 18f,
            275f,
            24f
        )

        text(
            canvas,
            "TODAY",
            38f,
            128f,
            13f,
            Color.rgb(95, 220, 255),
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
            Color.rgb(175, 190, 220)
        )

        text(
            canvas,
            "earn a little XP.",
            38f,
            213f,
            13f,
            Color.rgb(175, 190, 220)
        )

        drawButton(
            canvas,
            "PLAY NOW",
            38f,
            225f,
            width - 38f,
            262f
        )

        text(
            canvas,
            "🔥  $streak day streak",
            22f,
            322f,
            19f,
            Color.rgb(255, 180, 45),
            true
        )

        drawButton(
            canvas,
            "CLAIM +50 XP",
            22f,
            350f,
            width - 22f,
            402f
        )

        drawNavigation(
            canvas,
            width,
            height,
            3
        )
    }

    // =========================================================
    // PROFILE
    // =========================================================

    private fun drawProfile(
        canvas: Canvas,
        width: Float,
        height: Float
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
            width - 18f,
            210f,
            24f
        )

        text(
            canvas,
            "✦",
            62f,
            153f,
            52f,
            Color.rgb(105, 220, 255),
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
            Color.rgb(175, 190, 220)
        )

        text(
            canvas,
            "$score XP",
            105f,
            184f,
            13f,
            Color.rgb(145, 160, 195)
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
            Color.rgb(255, 180, 45),
            true
        )

        drawCard(
            canvas,
            18f,
            320f,
            width - 18f,
            440f,
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
            width,
            height,
            4
        )
    }

    // =========================================================
    // TOAST
    // =========================================================

    private fun drawToast(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        drawCard(
            canvas,
            28f,
            height - 132f,
            width - 28f,
            height - 84f,
            18f
        )

        text(
            canvas,
            message,
            width / 2f,
            height - 103f,
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

    private fun saveData() {
        prefs.edit()
            .putInt("score", score)
            .putInt("streak", streak)
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

        val width = width.toFloat()
        val height = height.toFloat()

        when (screen) {

            Screen.SPLASH -> {
                // Splash automatically continues.
            }

            Screen.WELCOME -> {
                if (y > height * 0.70f) {
                    prefs.edit()
                        .putBoolean("welcome_seen", true)
                        .apply()

                    screen = Screen.HOME
                }
            }

            Screen.HOME -> {
                handleHomeTouch(
                    x,
                    y,
                    width,
                    height
                )
            }

            Screen.GAMES -> {
                handleGamesTouch(
                    x,
                    y,
                    width,
                    height
                )
            }

            Screen.PUZZLE -> {
                handlePuzzleTouch(
                    x,
                    y,
                    width
                )
            }

            Screen.MEMORY -> {
                handleMemoryTouch(
                    x,
                    y,
                    width
                )
            }

            Screen.FOCUS -> {
                handleFocusTouch(
                    x,
                    y,
                    width,
                    height
                )
            }

            Screen.RELAX -> {
                handleRelaxTouch(
                    y
                )
            }

            Screen.DAILY -> {
                handleDailyTouch(
                    y,
                    width
                )
            }

            Screen.PROFILE -> {
                handleProfileTouch(
                    y
                )
            }
        }

        invalidate()

        return true
    }

    // =========================================================
    // HOME TOUCH
    // =========================================================

    private fun handleHomeTouch(
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {

        if (y > height - 95f) {

            if (x < width * 0.20f) {
                screen = Screen.HOME
                return
            }

            if (x >= width * 0.20f &&
                x < width * 0.40f
            ) {
                screen = Screen.GAMES
                return
            }

            if (x >= width * 0.80f) {
                screen = Screen.PROFILE
                return
            }
        }

        if (y in 165f..225f) {
            screen = Screen.DAILY
            return
        }

        if (y in 260f..355f) {

            if (x < width / 2f) {
                screen = Screen.PUZZLE
            } else {
                startMemoryGame()
            }

            return
        }

        if (y in 360f..465f) {

            if (x < width / 2f) {
                screen = Screen.FOCUS
                focusHits = 0
            } else {
                screen = Screen.RELAX
            }
        }
    }

    // =========================================================
    // GAMES TOUCH
    // =========================================================

    private fun handleGamesTouch(
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {

        if (y < 80f) {
            screen = Screen.HOME
            return
        }

        if (y > height - 95f) {

            if (x >= width * 0.80f) {
                screen = Screen.PROFILE
                return
            }

            if (x >= width * 0.20f &&
                x < width * 0.40f
            ) {
                return
            }
        }

        if (y in 90f..210f) {

            if (x < width / 2f) {
                screen = Screen.PUZZLE
            } else {
                startMemoryGame()
            }

            return
        }

        if (y in 215f..335f) {

            if (x < width / 2f) {
                screen = Screen.FOCUS
                focusHits = 0
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

    private fun handlePuzzleTouch(
        x: Float,
        y: Float,
        width: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        val size = min(
            width * 0.78f,
            310f
        )

        val left = (width - size) / 2f
        val top = 110f

        if (
            x >= left &&
            x <= left + size &&
            y >= top &&
            y <= top + size
        ) {

            val column =
                ((x - left) / (size / 4f))
                    .toInt()
                    .coerceIn(0, 3)

            val row =
                ((y - top) / (size / 4f))
                    .toInt()
                    .coerceIn(0, 3)

            val selected =
                row * 4 + column

            if (selected == puzzleTarget) {

                score += 25

                saveData()

                puzzleTarget = Random.nextInt(16)

                showMessage(
                    "Level cleared! +25 XP"
                )
            } else {
                showMessage(
                    "Try the glowing tile!"
                )
            }
        }
    }

    // =========================================================
    // MEMORY
    // =========================================================

    private fun startMemoryGame() {

        memoryValues =
            MutableList(9) { it }.apply {
                shuffle()
            }

        memoryFirst = -1
        memorySecond = -1

        memoryPreviewUntil =
            System.currentTimeMillis() + 2200L

        screen = Screen.MEMORY
    }

    private fun handleMemoryTouch(
        x: Float,
        y: Float,
        width: Float
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
            width * 0.78f,
            315f
        )

        val left = (width - size) / 2f
        val top = 105f

        if (
            x < left ||
            x > left + size ||
            y < top ||
            y > top + size
        ) {
            return
        }

        val column =
            ((x - left) / (size / 3f))
                .toInt()
                .coerceIn(0, 2)

        val row =
            ((y - top) / (size / 3f))
                .toInt()
                .coerceIn(0, 2)

        val index = row * 3 + column

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
                memoryValues[memoryFirst] ==
                memoryValues[memorySecond]
            ) {

                score += 20

                saveData()

                showMessage(
                    "Match! +20 XP"
                )

            } else {

                showMessage(
                    "Not a match"
                )
            }

            postDelayed(
                {
                    memoryFirst = -1
                    memorySecond = -1
                    invalidate()
                },
                600L
            )
        }
    }

    // =========================================================
    // FOCUS TOUCH
    // =========================================================

    private fun handleFocusTouch(
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        if (
            y < 100f ||
            y > 420f
        ) {
            return
        }

        val angle =
            animation *
                    (1f + focusTarget * 0.08f) +
                    focusTarget * 1.57f

        val targetX =
            width / 2f +
                    cos(angle.toDouble()).toFloat() *
                    width * 0.27f

        val targetY =
            240f +
                    sin((angle * 1.2f).toDouble()).toFloat() *
                    105f

        val dx = x - targetX
        val dy = y - targetY

        val distanceSquared =
            dx * dx + dy * dy

        if (distanceSquared < 55f * 55f) {

            focusHits++

            score += 5

            saveData()

            focusTarget =
                (focusTarget + 1) % 4

            showMessage(
                "Great focus! +5 XP"
            )
        } else {

            showMessage(
                "Follow the cyan glow"
            )
        }
    }

    // =========================================================
    // RELAX TOUCH
    // =========================================================

    private fun handleRelaxTouch(
        y: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        showMessage(
            "Breathe in... and out..."
        )
    }

    // =========================================================
    // DAILY TOUCH
    // =========================================================

    private fun handleDailyTouch(
        y: Float,
        width: Float
    ) {

        if (y < 80f) {
            screen = Screen.HOME
            return
        }

        if (y in 210f..430f) {

            score += 50
            streak += 1

            saveData()

            showMessage(
                "Daily complete! +50 XP"
            )
        }
    }

    // =========================================================
    // PROFILE TOUCH
    // =========================================================

    private fun handleProfileTouch(
        y: Float
    ) {

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

            Screen.WELCOME -> false

            Screen.HOME -> false

            Screen.GAMES -> {
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

            Screen.DAILY -> {
                screen = Screen.HOME
                invalidate()
                true
            }

            Screen.PROFILE -> {
                screen = Screen.HOME
                invalidate()
                true
            }
        }
    }
}
