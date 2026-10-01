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

    private lateinit var mindView: MindBlowView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(6, 8, 25)
        window.navigationBarColor = Color.rgb(6, 8, 25)

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
    SPLASH,
    WELCOME,
    HOME,
    GAMES,
    GAME,
    PROFILE
}

private enum class GameType {
    NEURO_TAP,
    COLOR_SHIFT,
    REACTION_RUSH,
    NUMBER_FLOW,
    PATTERN_PULSE,
    CALM_FLOW
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

    private var selectedGame = GameType.NEURO_TAP

    private var startedAt = System.currentTimeMillis()
    private var animation = 0f

    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)

    private var level: Int
        get() = score / 500 + 1
        set(value) {}

    private var message = ""
    private var messageUntil = 0L

    // ---------------------------------------------------------
    // GAME STATE
    // ---------------------------------------------------------

    private var gameStarted = false
    private var gameOver = false
    private var gameScore = 0
    private var gameRound = 1

    // Neuro Tap
    private var neuroX = 0f
    private var neuroY = 0f
    private var neuroRadius = 34f
    private var neuroExpires = 0L
    private var neuroHits = 0

    // Color Shift
    private var colorWord = ""
    private var colorIndex = 0
    private var colorOptions = intArrayOf(0, 1, 2, 3)
    private var colorRunning = false

    private val colorNames = arrayOf(
        "CYAN",
        "PINK",
        "YELLOW",
        "GREEN"
    )

    private val gameColors = intArrayOf(
        Color.rgb(65, 225, 255),
        Color.rgb(255, 85, 180),
        Color.rgb(255, 215, 70),
        Color.rgb(80, 235, 145)
    )

    // Reaction Rush
    private var reactionState = 0
    // 0 = idle, 1 = waiting, 2 = GO, 3 = result
    private var reactionStarted = 0L
    private var reactionDelay = 0L
    private var reactionTime = 0L
    private var reactionBest = Long.MAX_VALUE

    // Number Flow
    private var numberSequence = ""
    private var numberInput = ""
    private var numberShowing = false
    private var numberShowUntil = 0L

    // Pattern Pulse
    private var pattern = BooleanArray(9)
    private var patternSelected = BooleanArray(9)
    private var patternShowing = false
    private var patternShowUntil = 0L

    // Calm Flow
    private var calmCycles = 0
    private var calmPhase = 0
    private var calmPhaseStarted = 0L

    init {
        isFocusable = true
        postInvalidateDelayed(16L)
    }

    // =========================================================
    // DRAW
    // =========================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        animation += 0.018f

        drawAnimatedBackground(canvas, w, h)

        when (screen) {
            Screen.SPLASH -> drawSplash(canvas, w, h)
            Screen.WELCOME -> drawWelcome(canvas, w, h)
            Screen.HOME -> drawHome(canvas, w, h)
            Screen.GAMES -> drawGames(canvas, w, h)
            Screen.GAME -> drawGame(canvas, w, h)
            Screen.PROFILE -> drawProfile(canvas, w, h)
        }

        if (messageUntil > System.currentTimeMillis()) {
            drawToast(canvas, w, h)
        }

        if (
            screen == Screen.SPLASH &&
            System.currentTimeMillis() - startedAt > 1300L
        ) {
            screen = Screen.WELCOME
        }

        updateGames()

        postInvalidateDelayed(16L)
    }

    // =========================================================
    // ANIMATED BACKGROUND
    // =========================================================

    private fun drawAnimatedBackground(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        paint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            Color.rgb(4, 7, 26),
            Color.rgb(27, 5, 50),
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = null

        val t = animation

        // Moving cyan glow
        paint.color = Color.argb(38, 20, 220, 255)

        canvas.drawCircle(
            w * 0.15f + sin(t * 0.7f) * w * 0.10f,
            h * 0.20f + cos(t * 0.6f) * 40f,
            min(w * 0.28f, 145f),
            paint
        )

        // Moving purple glow
        paint.color = Color.argb(34, 155, 65, 255)

        canvas.drawCircle(
            w * 0.88f + cos(t * 0.5f) * 45f,
            h * 0.40f + sin(t * 0.8f) * 55f,
            min(w * 0.30f, 165f),
            paint
        )

        // Bottom glow
        paint.color = Color.argb(28, 30, 255, 175)

        canvas.drawCircle(
            w * 0.50f + sin(t * 0.4f) * 55f,
            h * 0.86f,
            min(w * 0.32f, 180f),
            paint
        )

        // Floating particles
        for (i in 0 until 22) {
            val px =
                ((i * 83f + sin(t * 0.4f + i) * 35f) % w + w) % w

            val py =
                ((i * 137f + cos(t * 0.3f + i) * 45f) % h + h) % h

            val alpha =
                (20 + 18 * ((sin(t + i) + 1f) / 2f)).toInt()

            paint.color = Color.argb(
                alpha,
                100,
                220,
                255
            )

            canvas.drawCircle(
                px,
                py,
                if (i % 3 == 0) 2.2f else 1.2f,
                paint
            )
        }

        // Radar rings
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(22, 110, 210, 255)

        val centerX = w / 2f
        val centerY = h * 0.53f

        for (i in 0 until 7) {
            val radius =
                45f + i * 48f + sin(t + i) * 4f

            canvas.drawCircle(
                centerX,
                centerY,
                radius,
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

    private fun card(
        canvas: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        radius: Float = 20f
    ) {
        paint.shader = null

        paint.color = Color.argb(
            228,
            10,
            17,
            43
        )

        canvas.drawRoundRect(
            l,
            t,
            r,
            b,
            radius,
            radius,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f

        paint.color = Color.argb(
            70,
            80,
            180,
            255
        )

        canvas.drawRoundRect(
            l,
            t,
            r,
            b,
            radius,
            radius,
            paint
        )

        paint.style = Paint.Style.FILL
    }

    // =========================================================
    // GRADIENT BUTTON
    // =========================================================

    private fun button(
        canvas: Canvas,
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
            Color.rgb(30, 220, 255),
            Color.rgb(125, 65, 255),
            Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(
            l,
            t,
            r,
            b,
            18f,
            18f,
            paint
        )

        paint.shader = null

        text(
            canvas,
            label,
            (l + r) / 2f,
            (t + b) / 2f + 5f,
            14f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // HEADER
    // =========================================================

    private fun header(
        canvas: Canvas,
        title: String,
        subtitle: String
    ) {
        text(
            canvas,
            "‹",
            22f,
            47f,
            38f,
            Color.WHITE,
            false
        )

        text(
            canvas,
            title,
            58f,
            39f,
            23f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            58f,
            59f,
            11f,
            0xff9ca9d2.toInt()
        )
    }

    // =========================================================
    // BOTTOM NAVIGATION
    // =========================================================

    private fun navigation(
        canvas: Canvas,
        w: Float,
        h: Float,
        selected: Int
    ) {
        val top = h - 76f

        card(
            canvas,
            10f,
            top,
            w - 10f,
            h - 8f,
            22f
        )

        val names = arrayOf(
            "Home",
            "Games",
            "Focus",
            "Daily",
            "Profile"
        )

        val icons = arrayOf(
            "⌂",
            "◆",
            "◎",
            "★",
            "●"
        )

        for (i in 0..4) {
            val x = w * (i + 0.5f) / 5f

            val c =
                if (i == selected) {
                    0xff5ce9ff.toInt()
                } else {
                    0xff7784aa.toInt()
                }

            text(
                canvas,
                icons[i],
                x,
                top + 27f,
                19f,
                c,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                names[i],
                x,
                top + 48f,
                8.5f,
                c,
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
        val pulse =
            1f + sin(animation * 2f) * 0.08f

        text(
            canvas,
            "M",
            w / 2f,
            h * 0.43f,
            80f * pulse,
            0xff61e9ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "MINDBLOW",
            w / 2f,
            h * 0.52f,
            34f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "RESET YOUR MIND",
            w / 2f,
            h * 0.565f,
            12f,
            0xffaab8df.toInt(),
            true,
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
            "Welcome to",
            w / 2f,
            h * 0.15f,
            15f,
            0xffaab7dc.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "MindBlow",
            w / 2f,
            h * 0.21f,
            38f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        // Animated orb
        val cx = w / 2f
        val cy = h * 0.42f

        for (i in 0 until 5) {
            paint.color = Color.argb(
                24 - i * 3,
                60,
                220,
                255
            )

            canvas.drawCircle(
                cx,
                cy,
                45f + i * 30f + sin(animation + i) * 7f,
                paint
            )
        }

        text(
            canvas,
            "BRAIN",
            cx,
            cy - 5f,
            16f,
            0xff5ee9ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "PLAY",
            cx,
            cy + 25f,
            27f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Short games for focus, memory, reaction",
            w / 2f,
            h * 0.59f,
            14f,
            0xffaab6d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "and a calmer mind.",
            w / 2f,
            h * 0.625f,
            14f,
            0xffaab6d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            canvas,
            "START MY RESET",
            w * 0.12f,
            h * 0.72f,
            w * 0.88f,
            h * 0.80f
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
            "MINDBLOW",
            20f,
            42f,
            25f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Your daily mental reset",
            20f,
            63f,
            11f,
            0xff98a7d0.toInt()
        )

        card(
            canvas,
            w - 108f,
            18f,
            w - 18f,
            61f,
            17f
        )

        text(
            canvas,
            "$score XP",
            w - 63f,
            45f,
            13f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )

        // Hero card
        val heroTop = h * 0.105f
        val heroBottom = h * 0.275f

        card(
            canvas,
            16f,
            heroTop,
            w - 16f,
            heroBottom,
            24f
        )

        text(
            canvas,
            "LEVEL $level",
            31f,
            heroTop + 34f,
            11f,
            0xff5de7ff.toInt(),
            true
        )

        text(
            canvas,
            "Ready for a quick reset?",
            31f,
            heroTop + 65f,
            22f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Play for 2–5 minutes.",
            31f,
            heroTop + 89f,
            12f,
            0xffaab6d8.toInt()
        )

        val progress =
            (score % 500) / 500f

        paint.color = Color.argb(
            55,
            100,
            130,
            180
        )

        canvas.drawRoundRect(
            31f,
            heroBottom - 27f,
            w - 31f,
            heroBottom - 20f,
            5f,
            5f,
            paint
        )

        paint.shader = LinearGradient(
            31f,
            0f,
            w - 31f,
            0f,
            0xff43e5ff.toInt(),
            0xff965bff.toInt(),
            Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(
            31f,
            heroBottom - 27f,
            31f + (w - 62f) * progress,
            heroBottom - 20f,
            5f,
            5f,
            paint
        )

        paint.shader = null

        // Quick play
        text(
            canvas,
            "QUICK PLAY",
            18f,
            heroBottom + 43f,
            17f,
            Color.WHITE,
            true
        )

        val gap = 10f
        val cardW = (w - 36f - gap) / 2f
        val row1 = heroBottom + 58f
        val row2 = row1 + 102f

        miniGameCard(
            canvas,
            "NEURO",
            "Fast targets",
            18f,
            row1,
            18f + cardW,
            row1 + 92f,
            0xff4de7ff.toInt()
        )

        miniGameCard(
            canvas,
            "COLOR",
            "Train attention",
            28f + cardW,
            row1,
            w - 18f,
            row1 + 92f,
            0xffff5ab7.toInt()
        )

        miniGameCard(
            canvas,
            "REACTION",
            "Test reflexes",
            18f,
            row2,
            18f + cardW,
            row2 + 92f,
            0xffffd84d.toInt()
        )

        miniGameCard(
            canvas,
            "MEMORY",
            "Remember patterns",
            28f + cardW,
            row2,
            w - 18f,
            row2 + 92f,
            0xff8f7cff.toInt()
        )

        val lower = row2 + 108f

        card(
            canvas,
            18f,
            lower,
            w - 18f,
            lower + 72f,
            19f
        )

        text(
            canvas,
            "🔥",
            40f,
            lower + 43f,
            22f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "$streak DAY STREAK",
            76f,
            lower + 31f,
            13f,
            0xffffc64d.toInt(),
            true
        )

        text(
            canvas,
            "Keep your reset going",
            76f,
            lower + 51f,
            11f,
            0xff9da9ce.toInt()
        )

        navigation(
            canvas,
            w,
            h,
            0
        )
    }

    private fun miniGameCard(
        canvas: Canvas,
        title: String,
        subtitle: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        accent: Int
    ) {
        card(canvas, l, t, r, b, 19f)

        paint.color = Color.argb(
            45,
            Color.red(accent),
            Color.green(accent),
            Color.blue(accent)
        )

        canvas.drawCircle(
            l + 27f,
            t + 30f,
            13f,
            paint
        )

        text(
            canvas,
            "●",
            l + 27f,
            t + 35f,
            12f,
            accent,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            title,
            l + 18f,
            t + 62f,
            14f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            l + 18f,
            t + 80f,
            9f,
            0xff98a6cc.toInt()
        )
    }

    // =========================================================
    // GAMES MENU
    // =========================================================

    private fun drawGames(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Mind Games",
            "Choose your challenge"
        )

        val top = 82f
        val gap = 10f
        val cw = (w - 36f - gap) / 2f

        gameMenuCard(
            canvas,
            "01",
            "Neuro Tap",
            "Train reaction",
            18f,
            top,
            18f + cw,
            top + 105f,
            0xff4de7ff.toInt()
        )

        gameMenuCard(
            canvas,
            "02",
            "Color Shift",
            "Train attention",
            28f + cw,
            top,
            w - 18f,
            top + 105f,
            0xffff5ab7.toInt()
        )

        gameMenuCard(
            canvas,
            "03",
            "Reaction Rush",
            "Test reflex",
            18f,
            top + 115f,
            18f + cw,
            top + 220f,
            0xffffd84d.toInt()
        )

        gameMenuCard(
            canvas,
            "04",
            "Number Flow",
            "Train memory",
            28f + cw,
            top + 115f,
            w - 18f,
            top + 220f,
            0xff7c8cff.toInt()
        )

        gameMenuCard(
            canvas,
            "05",
            "Pattern Pulse",
            "Visual memory",
            18f,
            top + 230f,
            18f + cw,
            top + 335f,
            0xffa568ff.toInt()
        )

        gameMenuCard(
            canvas,
            "06",
            "Calm Flow",
            "Reset your pace",
            28f + cw,
            top + 230f,
            w - 18f,
            top + 335f,
            0xff55e5a2.toInt()
        )

        card(
            canvas,
            18f,
            top + 355f,
            w - 18f,
            top + 425f,
            20f
        )

        text(
            canvas,
            "TIP",
            34f,
            top + 383f,
            10f,
            0xff5ce7ff.toInt(),
            true
        )

        text(
            canvas,
            "Short sessions work best. Pick one game and reset.",
            34f,
            top + 407f,
            11f,
            0xffaab6d8.toInt()
        )

        navigation(
            canvas,
            w,
            h,
            1
        )
    }

    private fun gameMenuCard(
        canvas: Canvas,
        number: String,
        title: String,
        subtitle: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        accent: Int
    ) {
        card(canvas, l, t, r, b, 19f)

        paint.color = Color.argb(
            45,
            Color.red(accent),
            Color.green(accent),
            Color.blue(accent)
        )

        canvas.drawCircle(
            l + 28f,
            t + 30f,
            17f,
            paint
        )

        text(
            canvas,
            number,
            l + 28f,
            t + 34f,
            9f,
            accent,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            title,
            l + 18f,
            t + 66f,
            14f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            l + 18f,
            t + 85f,
            9f,
            0xff9da9cf.toInt()
        )
    }

    // =========================================================
    // GAME SCREEN
    // =========================================================

    private fun drawGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        when (selectedGame) {
            GameType.NEURO_TAP ->
                drawNeuroTap(canvas, w, h)

            GameType.COLOR_SHIFT ->
                drawColorShift(canvas, w, h)

            GameType.REACTION_RUSH ->
                drawReactionRush(canvas, w, h)

            GameType.NUMBER_FLOW ->
                drawNumberFlow(canvas, w, h)

            GameType.PATTERN_PULSE ->
                drawPatternPulse(canvas, w, h)

            GameType.CALM_FLOW ->
                drawCalmFlow(canvas, w, h)
        }
    }

    // =========================================================
    // 1. NEURO TAP
    // =========================================================

    private fun drawNeuroTap(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        gameHeader(
            canvas,
            "NEURO TAP",
            "Hit the glowing target"
        )

        drawGameStats(
            canvas,
            w,
            "SCORE $gameScore",
            "ROUND $gameRound"
        )

        if (!gameStarted) {
            gameIntro(
                canvas,
                w,
                h,
                "NEURO TAP",
                "Targets appear in different places.",
                "Tap them before they vanish.",
                "START GAME"
            )
            return
        }

        if (gameOver) {
            gameResult(
                canvas,
                w,
                h,
                "SESSION COMPLETE",
                "$gameScore TARGETS",
                "XP EARNED  +${gameScore * 8}",
                "PLAY AGAIN"
            )
            return
        }

        val now = System.currentTimeMillis()

        if (now > neuroExpires) {
            gameOver = true
            saveScore(gameScore * 8)
            return
        }

        // target glow
        for (i in 3 downTo 1) {
            paint.color = Color.argb(
                18,
                70,
                230,
                255
            )

            canvas.drawCircle(
                neuroX,
                neuroY,
                neuroRadius + i * 15f +
                    sin(animation * 4f) * 4f,
                paint
            )
        }

        paint.color = 0xff53eaff.toInt()

        canvas.drawCircle(
            neuroX,
            neuroY,
            neuroRadius,
            paint
        )

        paint.color = Color.WHITE

        canvas.drawCircle(
            neuroX,
            neuroY,
            7f,
            paint
        )

        text(
            canvas,
            "TAP!",
            neuroX,
            neuroY + 5f,
            10f,
            0xff04101e.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Find the glow",
            w / 2f,
            h * 0.83f,
            14f,
            0xffaab8dc.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // 2. COLOR SHIFT
    // =========================================================

    private fun drawColorShift(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        gameHeader(
            canvas,
            "COLOR SHIFT",
            "Tap the color, not the word"
        )

        drawGameStats(
            canvas,
            w,
            "SCORE $gameScore",
            "ROUND $gameRound"
        )

        if (!colorRunning) {
            gameIntro(
                canvas,
                w,
                h,
                "COLOR SHIFT",
                "The word and its color can disagree.",
                "Choose the actual color of the text.",
                "START GAME"
            )
            return
        }

        text(
            canvas,
            "WHAT COLOR IS THIS?",
            w / 2f,
            h * 0.25f,
            12f,
            0xffa7b5d9.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            colorWord,
            w / 2f,
            h * 0.39f,
            42f,
            gameColors[colorIndex],
            true,
            Paint.Align.CENTER
        )

        val bw = (w - 52f) / 2f
        val startY = h * 0.52f

        for (i in 0 until 4) {
            val row = i / 2
            val col = i % 2

            val l = 18f + col * (bw + 16f)
            val t = startY + row * 75f
            val r = l + bw
            val b = t + 58f

            paint.color = Color.argb(
                40,
                Color.red(gameColors[colorOptions[i]]),
                Color.green(gameColors[colorOptions[i]]),
                Color.blue(gameColors[colorOptions[i]])
            )

            canvas.drawRoundRect(
                l,
                t,
                r,
                b,
                17f,
                17f,
                paint
            )

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = gameColors[colorOptions[i]]

            canvas.drawRoundRect(
                l,
                t,
                r,
                b,
                17f,
                17f,
                paint
            )

            paint.style = Paint.Style.FILL

            text(
                canvas,
                colorNames[colorOptions[i]],
                (l + r) / 2f,
                t + 36f,
                13f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }
    }

    // =========================================================
    // 3. REACTION RUSH
    // =========================================================

    private fun drawReactionRush(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        gameHeader(
            canvas,
            "REACTION RUSH",
            "Wait for green"
        )

        if (reactionState == 0) {
            gameIntro(
                canvas,
                w,
                h,
                "REACTION RUSH",
                "The screen changes unexpectedly.",
                "Tap only when it turns green.",
                "START"
            )
            return
        }

        val cx = w / 2f
        val cy = h * 0.43f

        when (reactionState) {
            1 -> {
                for (i in 0..3) {
                    paint.color = Color.argb(
                        20 - i * 3,
                        255,
                        100,
                        80
                    )

                    canvas.drawCircle(
                        cx,
                        cy,
                        80f + i * 35f,
                        paint
                    )
                }

                paint.color = 0xffff5c69.toInt()

                canvas.drawCircle(
                    cx,
                    cy,
                    75f,
                    paint
                )

                text(
                    canvas,
                    "WAIT",
                    cx,
                    cy + 9f,
                    19f,
                    Color.WHITE,
                    true,
                    Paint.Align.CENTER
                )

                text(
                    canvas,
                    "Don't tap yet",
                    cx,
                    cy + 125f,
                    14f,
                    0xffaab7d8.toInt(),
                    false,
                    Paint.Align.CENTER
                )
            }

            2 -> {
                for (i in 0..4) {
                    paint.color = Color.argb(
                        24 - i * 3,
                        65,
                        255,
                        150
                    )

                    canvas.drawCircle(
                        cx,
                        cy,
                        80f + i * 40f +
                            sin(animation * 4f) * 6f,
                        paint
                    )
                }

                paint.color = 0xff50ef9b.toInt()

                canvas.drawCircle(
                    cx,
                    cy,
                    78f,
                    paint
                )

                text(
                    canvas,
                    "GO!",
                    cx,
                    cy + 10f,
                    25f,
                    Color.WHITE,
                    true,
                    Paint.Align.CENTER
                )
            }

            3 -> {
                text(
                    canvas,
                    "${reactionTime}ms",
                    cx,
                    cy,
                    45f,
                    0xff58e8ff.toInt(),
                    true,
                    Paint.Align.CENTER
                )

                text(
                    canvas,
                    "REACTION TIME",
                    cx,
                    cy + 35f,
                    12f,
                    0xffaab7d8.toInt(),
                    true,
                    Paint.Align.CENTER
                )

                button(
                    canvas,
                    "TRY AGAIN",
                    w * 0.18f,
                    h * 0.63f,
                    w * 0.82f,
                    h * 0.71f
                )
            }
        }
    }

    // =========================================================
    // 4. NUMBER FLOW
    // =========================================================

    private fun drawNumberFlow(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        gameHeader(
            canvas,
            "NUMBER FLOW",
            "Remember the sequence"
        )

        drawGameStats(
            canvas,
            w,
            "LEVEL $gameRound",
            "SCORE $gameScore"
        )

        if (!gameStarted) {
            gameIntro(
                canvas,
                w,
                h,
                "NUMBER FLOW",
                "A number sequence appears briefly.",
                "Remember it and enter it correctly.",
                "START"
            )
            return
        }

        if (numberShowing) {
            text(
                canvas,
                "MEMORIZE",
                w / 2f,
                h * 0.25f,
                12f,
                0xff5de7ff.toInt(),
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                numberSequence,
                w / 2f,
                h * 0.39f,
                42f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                "Watch carefully...",
                w / 2f,
                h * 0.46f,
                13f,
                0xff9eacd2.toInt(),
                false,
                Paint.Align.CENTER
            )

            return
        }

        card(
            canvas,
            24f,
            h * 0.20f,
            w - 24f,
            h * 0.30f,
            20f
        )

        text(
            canvas,
            if (numberInput.isEmpty()) "ENTER SEQUENCE" else numberInput,
            w / 2f,
            h * 0.265f,
            25f,
            if (numberInput.isEmpty()) {
                0xff7786ad.toInt()
            } else {
                Color.WHITE
            },
            true,
            Paint.Align.CENTER
        )

        val gridTop = h * 0.36f
        val cell = (w - 70f) / 3f

        for (i in 1..9) {
            val n = i - 1
            val row = n / 3
            val col = n % 3

            val l = 20f + col * (cell + 15f)
            val t = gridTop + row * (cell + 15f)
            val r = l + cell
            val b = t + cell

            card(
                canvas,
                l,
                t,
                r,
                b,
                17f
            )

            text(
                canvas,
                i.toString(),
                (l + r) / 2f,
                (t + b) / 2f + 8f,
                23f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        button(
            canvas,
            "CLEAR",
            22f,
            h * 0.82f,
            w * 0.46f,
            h * 0.89f
        )

        button(
            canvas,
            "CHECK",
            w * 0.54f,
            h * 0.82f,
            w - 22f,
            h * 0.89f
        )
    }

    // =========================================================
    // 5. PATTERN PULSE
    // =========================================================

    private fun drawPatternPulse(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        gameHeader(
            canvas,
            "PATTERN PULSE",
            "Remember the glowing pattern"
        )

        if (!gameStarted) {
            gameIntro(
                canvas,
                w,
                h,
                "PATTERN PULSE",
                "A pattern flashes on the board.",
                "Recreate it after it disappears.",
                "START"
            )
            return
        }

        if (gameOver) {
            gameResult(
                canvas,
                w,
                h,
                "PATTERN COMPLETE",
                "$gameScore POINTS",
                "XP EARNED  +${gameScore * 10}",
                "PLAY AGAIN"
            )
            return
        }

        text(
            canvas,
            if (patternShowing) "MEMORIZE THE PATTERN" else "RECREATE IT",
            w / 2f,
            h * 0.19f,
            12f,
            0xffaab8dc.toInt(),
            true,
            Paint.Align.CENTER
        )

        val size = min(w * 0.76f, 320f)
        val left = (w - size) / 2f
        val top = h * 0.26f
        val cell = size / 3f

        for (i in 0 until 9) {
            val row = i / 3
            val col = i % 3

            val l = left + col * cell + 5f
            val t = top + row * cell + 5f
            val r = left + (col + 1) * cell - 5f
            val b = top + (row + 1) * cell - 5f

            val lit =
                if (patternShowing) {
                    pattern[i]
                } else {
                    patternSelected[i]
                }

            paint.color =
                if (lit) {
                    0xff51e7ff.toInt()
                } else {
                    Color.argb(210, 11, 20, 48)
                }

            canvas.drawRoundRect(
                l,
                t,
                r,
                b,
                15f,
                15f,
                paint
            )

            if (lit) {
                paint.color = Color.WHITE

                canvas.drawCircle(
                    (l + r) / 2f,
                    (t + b) / 2f,
                    6f,
                    paint
                )
            }
        }

        if (!patternShowing) {
            button(
                canvas,
                "CHECK PATTERN",
                w * 0.16f,
                h * 0.78f,
                w * 0.84f,
                h * 0.86f
            )
        }
    }

    // =========================================================
    // 6. CALM FLOW
    // =========================================================

    private fun drawCalmFlow(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        gameHeader(
            canvas,
            "CALM FLOW",
            "Follow the breathing circle"
        )

        val cx = w / 2f
        val cy = h * 0.42f

        if (!gameStarted) {
            gameIntro(
                canvas,
                w,
                h,
                "CALM FLOW",
                "Follow the expanding and shrinking glow.",
                "Complete three slow breathing cycles.",
                "BEGIN"
            )
            return
        }

        val elapsed =
            System.currentTimeMillis() - calmPhaseStarted

        if (elapsed > 4000L) {
            calmPhase =
                (calmPhase + 1) % 2

            calmPhaseStarted =
                System.currentTimeMillis()

            if (calmPhase == 0) {
                calmCycles++
            }

            if (calmCycles >= 3) {
                gameOver = true
                saveScore(60)
            }
        }

        if (gameOver) {
            gameResult(
                canvas,
                w,
                h,
                "MIND RESET COMPLETE",
                "3 BREATH CYCLES",
                "XP EARNED  +60",
                "FLOW AGAIN"
            )
            return
        }

        val progress =
            (elapsed.coerceIn(0L, 4000L) / 4000f)

        val amount =
            if (calmPhase == 0) {
                progress
            } else {
                1f - progress
            }

        val radius =
            55f + amount * 90f

        for (i in 5 downTo 1) {
            paint.color = Color.argb(
                18,
                70,
                225,
                255
            )

            canvas.drawCircle(
                cx,
                cy,
                radius + i * 18f,
                paint
            )
        }

        paint.color = 0xff55e8ff.toInt()

        canvas.drawCircle(
            cx,
            cy,
            radius,
            paint
        )

        text(
            canvas,
            if (calmPhase == 0) "INHALE" else "EXHALE",
            cx,
            cy + 7f,
            18f,
            0xff07111e.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "CYCLE ${min(calmCycles + 1, 3)} / 3",
            cx,
            h * 0.64f,
            13f,
            0xffaebbdc.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Slow and steady",
            cx,
            h * 0.69f,
            14f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // GAME HEADER
    // =========================================================

    private fun gameHeader(
        canvas: Canvas,
        title: String,
        subtitle: String
    ) {
        text(
            canvas,
            "‹",
            20f,
            47f,
            38f,
            Color.WHITE,
            false
        )

        text(
            canvas,
            title,
            57f,
            38f,
            21f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            57f,
            58f,
            10f,
            0xff9da9cf.toInt()
        )
    }

    private fun drawGameStats(
        canvas: Canvas,
        w: Float,
        leftText: String,
        rightText: String
    ) {
        card(
            canvas,
            18f,
            75f,
            w - 18f,
            112f,
            17f
        )

        text(
            canvas,
            leftText,
            32f,
            99f,
            11f,
            0xff59e6ff.toInt(),
            true
        )

        text(
            canvas,
            rightText,
            w - 32f,
            99f,
            11f,
            0xffffcf57.toInt(),
            true,
            Paint.Align.RIGHT
        )
    }

    // =========================================================
    // GAME INTRO
    // =========================================================

    private fun gameIntro(
        canvas: Canvas,
        w: Float,
        h: Float,
        title: String,
        line1: String,
        line2: String,
        buttonText: String
    ) {
        val cy = h * 0.37f

        for (i in 0 until 5) {
            paint.color = Color.argb(
                18,
                70,
                220,
                255
            )

            canvas.drawCircle(
                w / 2f,
                cy,
                40f + i * 27f +
                    sin(animation + i) * 5f,
                paint
            )
        }

        text(
            canvas,
            title,
            w / 2f,
            cy + 8f,
            25f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            line1,
            w / 2f,
            h * 0.57f,
            13f,
            0xffaab8db.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            line2,
            w / 2f,
            h * 0.60f,
            13f,
            0xffaab8db.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            canvas,
            buttonText,
            w * 0.14f,
            h * 0.68f,
            w * 0.86f,
            h * 0.76f
        )
    }

    // =========================================================
    // GAME RESULT
    // =========================================================

    private fun gameResult(
        canvas: Canvas,
        w: Float,
        h: Float,
        title: String,
        result: String,
        xp: String,
        buttonText: String
    ) {
        val cy = h * 0.36f

        text(
            canvas,
            "✦",
            w / 2f,
            cy - 45f,
            48f,
            0xff5ce8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            title,
            w / 2f,
            cy + 10f,
            21f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            result,
            w / 2f,
            cy + 55f,
            28f,
            0xff5ce8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            xp,
            w / 2f,
            cy + 82f,
            12f,
            0xffffce57.toInt(),
            true,
            Paint.Align.CENTER
        )

        button(
            canvas,
            buttonText,
            w * 0.15f,
            h * 0.64f,
            w * 0.85f,
            h * 0.73f
        )
    }

    // =========================================================
    // PROFILE
    // =========================================================

    private fun drawProfile(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Profile",
            "Your MindBlow progress"
        )

        card(
            canvas,
            18f,
            82f,
            w - 18f,
            230f,
            25f
        )

        text(
            canvas,
            "M",
            68f,
            157f,
            48f,
            0xff5ce7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Mind Explorer",
            108f,
            125f,
            20f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "LEVEL $level",
            108f,
            151f,
            12f,
            0xff5ce7ff.toInt(),
            true
        )

        text(
            canvas,
            "$score XP",
            108f,
            179f,
            13f,
            0xffa5b2d5.toInt()
        )

        text(
            canvas,
            "$streak day streak",
            108f,
            202f,
            12f,
            0xffffc64d.toInt()
        )

        val rows = arrayOf(
            "Games completed",
            "Current level",
            "Total XP"
        )

        val values = arrayOf(
            "${score / 25}",
            "$level",
            "$score"
        )

        for (i in 0..2) {
            val y = 278f + i * 53f

            card(
                canvas,
                18f,
                y - 26f,
                w - 18f,
                y + 18f,
                15f
            )

            text(
                canvas,
                rows[i],
                34f,
                y,
                12f,
                0xff9da9cd.toInt()
            )

            text(
                canvas,
                values[i],
                w - 34f,
                y,
                13f,
                Color.WHITE,
                true,
                Paint.Align.RIGHT
            )
        }

        navigation(
            canvas,
            w,
            h,
            4
        )
    }

    // =========================================================
    // TOAST
    // =========================================================

    private fun drawToast(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        val top = h - 135f

        card(
            canvas,
            28f,
            top,
            w - 28f,
            top + 50f,
            18f
        )

        text(
            canvas,
            message,
            w / 2f,
            top + 31f,
            12f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    private fun showMessage(value: String) {
        message = value
        messageUntil =
            System.currentTimeMillis() + 1500L
    }

    // =========================================================
    // GAME UPDATE
    // =========================================================

    private fun updateGames() {

        if (screen != Screen.GAME) {
            return
        }

        when (selectedGame) {

            GameType.NEURO_TAP -> {
                if (
                    gameStarted &&
                    !gameOver &&
                    System.currentTimeMillis() > neuroExpires
                ) {
                    gameOver = true
                    saveScore(gameScore * 8)
                }
            }

            GameType.COLOR_SHIFT -> {
                // No automatic timer.
            }

            GameType.REACTION_RUSH -> {
                if (
                    reactionState == 1 &&
                    System.currentTimeMillis() -
                    reactionStarted >= reactionDelay
                ) {
                    reactionState = 2
                    reactionStarted =
                        System.currentTimeMillis()
                }
            }

            GameType.NUMBER_FLOW -> {
                if (
                    numberShowing &&
                    System.currentTimeMillis() >
                    numberShowUntil
                ) {
                    numberShowing = false
                }
            }

            GameType.PATTERN_PULSE -> {
                if (
                    patternShowing &&
                    System.currentTimeMillis() >
                    patternShowUntil
                ) {
                    patternShowing = false
                }
            }

            GameType.CALM_FLOW -> {
                // Updated inside drawCalmFlow.
            }
        }
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
                // Wait for splash.
            }

            Screen.WELCOME -> {
                if (y > h * 0.65f) {
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

            Screen.GAME -> {
                gameTouch(x, y, w, h)
            }

            Screen.PROFILE -> {
                if (y < 75f) {
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

        if (y > h - 90f) {

            if (x < w * 0.20f) {
                screen = Screen.HOME
                return
            }

            if (x < w * 0.40f) {
                screen = Screen.GAMES
                return
            }

            if (x > w * 0.80f) {
                screen = Screen.PROFILE
                return
            }
        }

        val heroBottom = h * 0.275f

        val row1 = heroBottom + 58f
        val row2 = row1 + 102f

        val gap = 10f
        val cardW = (w - 36f - gap) / 2f

        if (
            y >= row1 &&
            y <= row1 + 92f
        ) {
            if (x < 18f + cardW) {
                startGame(GameType.NEURO_TAP)
            } else {
                startGame(GameType.COLOR_SHIFT)
            }
            return
        }

        if (
            y >= row2 &&
            y <= row2 + 92f
        ) {
            if (x < 18f + cardW) {
                startGame(GameType.REACTION_RUSH)
            } else {
                startGame(GameType.PATTERN_PULSE)
            }
            return
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

        if (y < 70f) {
            screen = Screen.HOME
            return
        }

        if (y > h - 90f) {

            if (x < w * 0.20f) {
                screen = Screen.HOME
                return
            }

            if (x > w * 0.80f) {
                screen = Screen.PROFILE
                return
            }
        }

        val top = 82f
        val gap = 10f
        val cw = (w - 36f - gap) / 2f

        if (y in top..(top + 105f)) {
            if (x < 18f + cw) {
                startGame(GameType.NEURO_TAP)
            } else {
                startGame(GameType.COLOR_SHIFT)
            }
            return
        }

        if (
            y in (top + 115f)..(top + 220f)
        ) {
            if (x < 18f + cw) {
                startGame(GameType.REACTION_RUSH)
            } else {
                startGame(GameType.NUMBER_FLOW)
            }
            return
        }

        if (
            y in (top + 230f)..(top + 335f)
        ) {
            if (x < 18f + cw) {
                startGame(GameType.PATTERN_PULSE)
            } else {
                startGame(GameType.CALM_FLOW)
            }
        }
    }

    // =========================================================
    // GAME TOUCH
    // =========================================================

    private fun gameTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 70f) {
            screen = Screen.GAMES
            return
        }

        when (selectedGame) {

            GameType.NEURO_TAP ->
                neuroTouch(x, y, w, h)

            GameType.COLOR_SHIFT ->
                colorTouch(x, y, w, h)

            GameType.REACTION_RUSH ->
                reactionTouch(x, y, w, h)

            GameType.NUMBER_FLOW ->
                numberTouch(x, y, w, h)

            GameType.PATTERN_PULSE ->
                patternTouch(x, y, w, h)

            GameType.CALM_FLOW ->
                calmTouch(x, y, w, h)
        }
    }

    // =========================================================
    // NEURO TOUCH
    // =========================================================

    private fun neuroTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (!gameStarted) {

            if (y > h * 0.62f) {
                startNeuro()
            }

            return
        }

        if (gameOver) {

            if (y > h * 0.58f) {
                startNeuro()
            }

            return
        }

        val dx = x - neuroX
        val dy = y - neuroY

        if (
            dx * dx + dy * dy <=
            (neuroRadius + 18f) *
            (neuroRadius + 18f)
        ) {

            neuroHits++
            gameScore++
            gameRound++

            score += 8

            neuroRadius =
                maxOf(
                    23f,
                    34f - gameRound * 0.7f
                )

            spawnNeuro(w, h)

            save()
            showMessage("+8 XP  •  NICE!")
        }
    }

    private fun startNeuro() {
        gameStarted = true
        gameOver = false
        gameScore = 0
        gameRound = 1
        neuroHits = 0
        neuroRadius = 34f
        spawnNeuro(width.toFloat(), height.toFloat())
    }

    private fun spawnNeuro(
        w: Float,
        h: Float
    ) {
        neuroX =
            45f + Random.nextFloat() * (w - 90f)

        neuroY =
            h * 0.22f +
                Random.nextFloat() *
                h * 0.50f

        val duration =
            maxOf(
                650L,
                1550L - gameRound * 35L
            )

        neuroExpires =
            System.currentTimeMillis() + duration
    }

    // =========================================================
    // COLOR TOUCH
    // =========================================================

    private fun colorTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (!colorRunning) {

            if (y > h * 0.62f) {
                startColorGame()
            }

            return
        }

        val bw = (w - 52f) / 2f
        val startY = h * 0.52f

        for (i in 0 until 4) {

            val row = i / 2
            val col = i % 2

            val l = 18f + col * (bw + 16f)
            val t = startY + row * 75f
            val r = l + bw
            val b = t + 58f

            if (
                x >= l &&
                x <= r &&
                y >= t &&
                y <= b
            ) {

                if (colorOptions[i] == colorIndex) {

                    gameScore++
                    gameRound++
                    score += 10

                    save()

                    showMessage(
                        "Correct! +10 XP"
                    )

                    nextColorRound()

                } else {

                    showMessage(
                        "Wrong color — try again"
                    )
                }

                return
            }
        }
    }

    private fun startColorGame() {
        gameStarted = true
        colorRunning = true
        gameScore = 0
        gameRound = 1
        nextColorRound()
    }

    private fun nextColorRound() {

        colorIndex =
            Random.nextInt(4)

        colorWord =
            colorNames[
                Random.nextInt(4)
            ]

        colorOptions =
            intArrayOf(0, 1, 2, 3)

        colorOptions.shuffle()
    }

    // =========================================================
    // REACTION TOUCH
    // =========================================================

    private fun reactionTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (reactionState == 0) {

            if (y > h * 0.62f) {
                startReaction()
            }

            return
        }

        if (reactionState == 1) {

            reactionState = 3
            reactionTime = -1L

            showMessage("Too early!")

            return
        }

        if (reactionState == 2) {

            reactionTime =
                System.currentTimeMillis() -
                    reactionStarted

            if (
                reactionBest == Long.MAX_VALUE ||
                reactionTime < reactionBest
            ) {
                reactionBest = reactionTime
            }

            score += 25
            save()

            reactionState = 3

            showMessage(
                "${reactionTime}ms  +25 XP"
            )

            return
        }

        if (reactionState == 3) {

            if (
                y > h * 0.58f
            ) {
                startReaction()
            }
        }
    }

    private fun startReaction() {

        reactionState = 1

        reactionStarted =
            System.currentTimeMillis()

        reactionDelay =
            Random.nextLong(
                1300L,
                3500L
            )
    }

    // =========================================================
    // NUMBER TOUCH
    // =========================================================

    private fun numberTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (!gameStarted) {

            if (y > h * 0.62f) {
                startNumber()
            }

            return
        }

        if (numberShowing) {
            return
        }

        val gridTop = h * 0.36f
        val cell = (w - 70f) / 3f

        for (i in 1..9) {

            val n = i - 1
            val row = n / 3
            val col = n % 3

            val l = 20f + col * (cell + 15f)
            val t = gridTop + row * (cell + 15f)
            val r = l + cell
            val b = t + cell

            if (
                x >= l &&
                x <= r &&
                y >= t &&
                y <= b
            ) {

                if (numberInput.length < 12) {
                    numberInput += i.toString()
                }

                return
            }
        }

        if (y > h * 0.80f) {

            if (x < w * 0.50f) {
                numberInput = ""
                return
            }

            checkNumber()
        }
    }

    private fun startNumber() {

        gameStarted = true
        gameScore = 0
        gameRound = 1
        numberInput = ""

        createNumberSequence()
    }

    private fun createNumberSequence() {

        val length =
            min(3 + gameRound, 8)

        val builder =
            StringBuilder()

        repeat(length) {
            builder.append(
                Random.nextInt(1, 10)
            )
        }

        numberSequence =
            builder.toString()

        numberInput = ""

        numberShowing = true

        numberShowUntil =
            System.currentTimeMillis() +
                1300L +
                length * 180L
    }

    private fun checkNumber() {

        if (numberInput == numberSequence) {

            gameScore++
            gameRound++
            score += 20

            save()

            showMessage(
                "Perfect memory! +20 XP"
            )

            createNumberSequence()

        } else {

            showMessage(
                "Sequence was $numberSequence"
            )

            gameRound = 1

            createNumberSequence()
        }
    }

    // =========================================================
    // PATTERN TOUCH
    // =========================================================

    private fun patternTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (!gameStarted) {

            if (y > h * 0.62f) {
                startPattern()
            }

            return
        }

        if (gameOver) {

            if (y > h * 0.58f) {
                startPattern()
            }

            return
        }

        if (patternShowing) {
            return
        }

        val size = min(w * 0.76f, 320f)
        val left = (w - size) / 2f
        val top = h * 0.26f
        val cell = size / 3f

        for (i in 0 until 9) {

            val row = i / 3
            val col = i % 3

            val l = left + col * cell + 5f
            val t = top + row * cell + 5f
            val r = left + (col + 1) * cell - 5f
            val b = top + (row + 1) * cell - 5f

            if (
                x >= l &&
                x <= r &&
                y >= t &&
                y <= b
            ) {

                patternSelected[i] =
                    !patternSelected[i]

                return
            }
        }

        if (y > h * 0.74f) {
            checkPattern()
        }
    }

    private fun startPattern() {

        gameStarted = true
        gameOver = false
        gameScore = 0
        gameRound = 1

        pattern =
            BooleanArray(9)

        patternSelected =
            BooleanArray(9)

        val count =
            min(2 + gameRound, 5)

        val indexes =
            (0..8).shuffled()

        for (i in 0 until count) {
            pattern[indexes[i]] = true
        }

        patternShowing = true

        patternShowUntil =
            System.currentTimeMillis() +
                1800L
    }

    private fun checkPattern() {

        var correct = true

        for (i in 0 until 9) {

            if (
                pattern[i] !=
                patternSelected[i]
            ) {
                correct = false
                break
            }
        }

        if (correct) {

            gameScore++
            gameRound++

            score += 15
            save()

            showMessage(
                "Pattern matched! +15 XP"
            )

            if (gameRound > 5) {

                gameOver = true

                saveScore(40)

            } else {

                pattern =
                    BooleanArray(9)

                patternSelected =
                    BooleanArray(9)

                val count =
                    min(2 + gameRound, 7)

                val indexes =
                    (0..8).shuffled()

                for (i in 0 until count) {
                    pattern[indexes[i]] = true
                }

                patternShowing = true

                patternShowUntil =
                    System.currentTimeMillis() +
                        maxOf(
                            900L,
                            1900L -
                                gameRound * 180L
                        )
            }

        } else {

            showMessage(
                "Pattern didn't match"
            )

            patternSelected =
                BooleanArray(9)
        }
    }

    // =========================================================
    // CALM TOUCH
    // =========================================================

    private fun calmTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (!gameStarted) {

            if (y > h * 0.62f) {

                gameStarted = true
                gameOver = false

                calmCycles = 0
                calmPhase = 0

                calmPhaseStarted =
                    System.currentTimeMillis()
            }

            return
        }

        if (gameOver) {

            if (y > h * 0.58f) {

                gameStarted = true
                gameOver = false

                calmCycles = 0
                calmPhase = 0

                calmPhaseStarted =
                    System.currentTimeMillis()
            }
        }
    }

    // =========================================================
    // START GAME
    // =========================================================

    private fun startGame(
        game: GameType
    ) {

        selectedGame = game

        gameStarted = false
        gameOver = false
        gameScore = 0
        gameRound = 1

        numberInput = ""

        reactionState = 0

        colorRunning = false

        patternShowing = false

        calmCycles = 0

        screen = Screen.GAME

        invalidate()
    }

    // =========================================================
    // SAVE
    // =========================================================

    private fun save() {

        prefs.edit()
            .putInt("score", score)
            .putInt("streak", streak)
            .apply()
    }

    private fun saveScore(amount: Int) {

        if (amount > 0) {
            score += amount
            save()
        }
    }

    // =========================================================
    // BACK
    // =========================================================

    fun goBack(): Boolean {

        when (screen) {

            Screen.SPLASH -> {
                return false
            }

            Screen.WELCOME -> {
                return false
            }

            Screen.HOME -> {
                return false
            }

            Screen.GAMES -> {
                screen = Screen.HOME
                invalidate()
                return true
            }

            Screen.GAME -> {
                screen = Screen.GAMES
                invalidate()
                return true
            }

            Screen.PROFILE -> {
                screen = Screen.HOME
                invalidate()
                return true
            }
        }
    }
}
