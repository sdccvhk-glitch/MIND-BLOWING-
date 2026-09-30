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

        window.statusBarColor = Color.rgb(8, 10, 22)
        window.navigationBarColor = Color.rgb(8, 10, 22)

        gameView = MindBlowView(this)
        setContentView(gameView)
    }

    @Suppress("DEPRECATION")
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

    // ------------------------------------------------------------
    // BASIC STATE
    // ------------------------------------------------------------

    private var screen = Screen.SPLASH
    private var previousScreen = Screen.HOME

    private val prefs =
        context.getSharedPreferences("mindblow", Context.MODE_PRIVATE)

    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 1)
    private var welcomeSeen = prefs.getBoolean("welcome_seen", false)

    private var splashStart = System.currentTimeMillis()

    private var message = ""
    private var messageUntil = 0L

    private var animationTime = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val random = Random(System.currentTimeMillis())

    // ------------------------------------------------------------
    // PUZZLE
    // ------------------------------------------------------------

    private var puzzleTarget = 0
    private var puzzleHits = 0
    private var puzzleMisses = 0

    // ------------------------------------------------------------
    // MEMORY
    // ------------------------------------------------------------

    private val memoryValues = mutableListOf<Int>()
    private val memoryOpen = mutableSetOf<Int>()
    private val memoryMatched = mutableSetOf<Int>()

    private var memoryFirst = -1
    private var memorySecond = -1
    private var memoryMoves = 0
    private var memoryLocked = false
    private var memoryStarted = false

    // ------------------------------------------------------------
    // FOCUS
    // ------------------------------------------------------------

    private var focusX = 0f
    private var focusY = 0f
    private var focusRadius = 34f
    private var focusHits = 0
    private var focusMisses = 0
    private var focusRunning = false
    private var focusStartTime = 0L
    private var focusDuration = 30_000L

    // ------------------------------------------------------------
    // RELAX
    // ------------------------------------------------------------

    private var relaxRunning = false
    private var relaxStartTime = 0L
    private var relaxDuration = 60_000L

    // ------------------------------------------------------------
    // DAILY
    // ------------------------------------------------------------

    private var dailyClaimed = prefs.getString("daily_date", "") == todayKey()

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        textPaint.typeface = Typeface.create(
            Typeface.DEFAULT,
            Typeface.NORMAL
        )

        postDelayed({
            if (welcomeSeen) {
                screen = Screen.HOME
            } else {
                screen = Screen.WELCOME
            }
            invalidate()
        }, 1200L)

        startAnimation()
    }

    // ------------------------------------------------------------
    // ANIMATION LOOP
    // ------------------------------------------------------------

    private fun startAnimation() {
        postDelayed({
            animationTime += 0.016f

            if (screen == Screen.FOCUS && focusRunning) {
                updateFocus()
            }

            if (screen == Screen.RELAX && relaxRunning) {
                updateRelax()
            }

            invalidate()
            startAnimation()
        }, 16L)
    }

    // ------------------------------------------------------------
    // DIMENSIONS
    // ------------------------------------------------------------

    private fun navHeight(): Float {
        return min(82f, height * 0.105f)
    }

    private fun topHeight(): Float {
        return min(82f, height * 0.105f)
    }

    private fun contentTop(): Float {
        return topHeight()
    }

    private fun contentBottom(): Float {
        return height.toFloat() - navHeight()
    }

    private fun contentHeight(): Float {
        return (contentBottom() - contentTop()).coerceAtLeast(1f)
    }

    private fun centerX(): Float {
        return width / 2f
    }

    private fun scale(): Float {
        return min(width / 390f, height / 844f)
    }

    // ------------------------------------------------------------
    // DRAW
    // ------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawBackground(canvas)

        when (screen) {
            Screen.SPLASH -> drawSplash(canvas)
            Screen.WELCOME -> drawWelcome(canvas)
            Screen.HOME -> drawHome(canvas)
            Screen.GAMES -> drawGames(canvas)
            Screen.PUZZLE -> drawPuzzle(canvas)
            Screen.MEMORY -> drawMemory(canvas)
            Screen.FOCUS -> drawFocus(canvas)
            Screen.RELAX -> drawRelax(canvas)
            Screen.DAILY -> drawDaily(canvas)
            Screen.PROFILE -> drawProfile(canvas)
        }

        if (screen != Screen.SPLASH && screen != Screen.WELCOME) {
            drawBottomNavigation(canvas)
        }

        drawToast(canvas)
    }

    // ------------------------------------------------------------
    // BACKGROUND
    // ------------------------------------------------------------

    private fun drawBackground(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        val gradient = LinearGradient(
            0f,
            0f,
            w,
            h,
            Color.rgb(7, 9, 24),
            Color.rgb(17, 10, 38),
            Shader.TileMode.CLAMP
        )

        bgPaint.shader = gradient
        canvas.drawRect(0f, 0f, w, h, bgPaint)
        bgPaint.shader = null

        // Moving glow circles
        val t = animationTime

        val x1 = w * 0.18f + sin(t * 0.7f) * w * 0.08f
        val y1 = h * 0.18f + cos(t * 0.8f) * h * 0.05f

        val x2 = w * 0.82f + cos(t * 0.55f) * w * 0.09f
        val y2 = h * 0.35f + sin(t * 0.65f) * h * 0.07f

        val x3 = w * 0.52f + sin(t * 0.45f) * w * 0.12f
        val y3 = h * 0.82f + cos(t * 0.6f) * h * 0.05f

        drawGlow(canvas, x1, y1, w * 0.22f, Color.rgb(85, 45, 180))
        drawGlow(canvas, x2, y2, w * 0.20f, Color.rgb(20, 120, 210))
        drawGlow(canvas, x3, y3, w * 0.25f, Color.rgb(160, 40, 150))

        // Small floating particles
        fillPaint.style = Paint.Style.FILL

        for (i in 0 until 22) {
            val px = ((i * 71 + t * (12 + i % 5) * 8) % (w + 40)) - 20f
            val py = ((i * 113 + t * (8 + i % 4) * 5) % (h + 40)) - 20f
            val radius = 1.2f + (i % 3)

            fillPaint.color = Color.argb(
                35 + (i % 4) * 10,
                180,
                160,
                255
            )

            canvas.drawCircle(px, py, radius, fillPaint)
        }
    }

    private fun drawGlow(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        color: Int
    ) {
        val shader = RadialGradient(
            x,
            y,
            radius,
            Color.argb(95, Color.red(color), Color.green(color), Color.blue(color)),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )

        fillPaint.shader = shader
        canvas.drawCircle(x, y, radius, fillPaint)
        fillPaint.shader = null
    }

    // ------------------------------------------------------------
    // TEXT HELPERS
    // ------------------------------------------------------------

    private fun txt(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        align: Paint.Align = Paint.Align.LEFT,
        bold: Boolean = false
    ) {
        textPaint.shader = null
        textPaint.color = color
        textPaint.textSize = size
        textPaint.textAlign = align
        textPaint.typeface = Typeface.create(
            Typeface.DEFAULT,
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )

        canvas.drawText(text, x, y, textPaint)
    }

    private fun centerText(
        canvas: Canvas,
        text: String,
        x: Float,
        centerY: Float,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) {
        val metrics = textPaint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f

        txt(
            canvas,
            text,
            x,
            baseline,
            size,
            color,
            Paint.Align.CENTER,
            bold
        )
    }

    // ------------------------------------------------------------
    // CARD / BUTTON
    // ------------------------------------------------------------

    private fun card(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float = 20f,
        alpha: Int = 210
    ) {
        fillPaint.shader = null
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.argb(alpha, 24, 25, 52)

        canvas.drawRoundRect(
            left,
            top,
            right,
            bottom,
            radius,
            radius,
            fillPaint
        )

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.2f
        strokePaint.color = Color.argb(70, 255, 255, 255)

        canvas.drawRoundRect(
            left,
            top,
            right,
            bottom,
            radius,
            radius,
            strokePaint
        )
    }

    private fun button(
        canvas: Canvas,
        text: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        active: Boolean = true
    ) {
        val gradient = LinearGradient(
            left,
            top,
            right,
            bottom,
            if (active) Color.rgb(100, 65, 220)
            else Color.rgb(55, 55, 75),
            if (active) Color.rgb(190, 60, 190)
            else Color.rgb(45, 45, 60),
            Shader.TileMode.CLAMP
        )

        fillPaint.shader = gradient
        fillPaint.style = Paint.Style.FILL

        canvas.drawRoundRect(
            left,
            top,
            right,
            bottom,
            17f,
            17f,
            fillPaint
        )

        fillPaint.shader = null

        centerText(
            canvas,
            text,
            (left + right) / 2f,
            (top + bottom) / 2f,
            15f,
            Color.WHITE,
            true
        )
    }

    // ------------------------------------------------------------
    // HEADER
    // ------------------------------------------------------------

    private fun header(
        canvas: Canvas,
        title: String,
        showBack: Boolean = true
    ) {
        val h = topHeight()

        if (showBack) {
            centerText(
                canvas,
                "‹",
                32f,
                h / 2f,
                38f,
                Color.WHITE,
                false
            )

            txt(
                canvas,
                title,
                62f,
                h / 2f + 7f,
                22f,
                Color.WHITE,
                Paint.Align.LEFT,
                true
            )
        } else {
            txt(
                canvas,
                title,
                24f,
                h / 2f + 7f,
                24f,
                Color.WHITE,
                Paint.Align.LEFT,
                true
            )
        }
    }

    // ------------------------------------------------------------
    // SPLASH
    // ------------------------------------------------------------

    private fun drawSplash(canvas: Canvas) {
        val cx = centerX()
        val cy = height * 0.43f

        val pulse = 1f + sin(animationTime * 3f) * 0.05f

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 3f
        strokePaint.color = Color.argb(120, 170, 100, 255)

        canvas.drawCircle(cx, cy, 90f * pulse, strokePaint)
        canvas.drawCircle(cx, cy, 112f * pulse, strokePaint)

        fillPaint.color = Color.rgb(110, 65, 220)
        fillPaint.style = Paint.Style.FILL

        canvas.drawCircle(cx, cy, 68f * pulse, fillPaint)

        centerText(
            canvas,
            "MB",
            cx,
            cy,
            34f,
            Color.WHITE,
            true
        )

        centerText(
            canvas,
            "MIND BLOW",
            cx,
            cy + 115f,
            27f,
            Color.WHITE,
            true
        )

        centerText(
            canvas,
            "Refresh your mind",
            cx,
            cy + 148f,
            14f,
            Color.argb(190, 255, 255, 255)
        )
    }

    // ------------------------------------------------------------
    // WELCOME
    // ------------------------------------------------------------

    private fun drawWelcome(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        val cy = h * 0.25f

        drawGlow(
            canvas,
            centerX(),
            cy,
            w * 0.32f,
            Color.rgb(100, 60, 230)
        )

        centerText(
            canvas,
            "MIND BLOW",
            centerX(),
            cy,
            38f,
            Color.WHITE,
            true
        )

        centerText(
            canvas,
            "Train • Relax • Refresh",
            centerX(),
            cy + 54f,
            16f,
            Color.rgb(195, 190, 225)
        )

        card(
            canvas,
            24f,
            h * 0.43f,
            w - 24f,
            h * 0.70f
        )

        centerText(
            canvas,
            "A tiny break for your brain.",
            centerX(),
            h * 0.49f,
            21f,
            Color.WHITE,
            true
        )

        centerText(
            canvas,
            "Play quick challenges, improve focus,",
            centerX(),
            h * 0.55f,
            14f,
            Color.LTGRAY
        )

        centerText(
            canvas,
            "and take a moment to relax.",
            centerX(),
            h * 0.585f,
            14f,
            Color.LTGRAY
        )

        button(
            canvas,
            "GET STARTED",
            45f,
            h * 0.76f,
            w - 45f,
            h * 0.84f
        )
    }

    // ------------------------------------------------------------
    // HOME
    // ------------------------------------------------------------

    private fun drawHome(canvas: Canvas) {
        header(canvas, "Mind Blow", false)

        val top = contentTop() + 10f
        val w = width.toFloat()

        card(
            canvas,
            20f,
            top,
            w - 20f,
            top + 145f
        )

        txt(
            canvas,
            "YOUR PROGRESS",
            38f,
            top + 33f,
            12f,
            Color.rgb(170, 165, 200),
            Paint.Align.LEFT,
            true
        )

        txt(
            canvas,
            score.toString(),
            38f,
            top + 82f,
            36f,
            Color.WHITE,
            Paint.Align.LEFT,
            true
        )

        txt(
            canvas,
            "XP SCORE",
            40f,
            top + 108f,
            11f,
            Color.rgb(150, 145, 180)
        )

        val dividerX = w * 0.53f

        fillPaint.color = Color.argb(60, 255, 255, 255)
        canvas.drawRect(
            dividerX,
            top + 25f,
            dividerX + 1f,
            top + 120f,
            fillPaint
        )

        txt(
            canvas,
            "🔥 $streak",
            dividerX + 25f,
            top + 67f,
            27f,
            Color.WHITE,
            Paint.Align.LEFT,
            true
        )

        txt(
            canvas,
            "DAY STREAK",
            dividerX + 27f,
            top + 99f,
            11f,
            Color.rgb(150, 145, 180)
        )

        val available = contentBottom() - (top + 165f)

        val gameTop = top + 165f
        val gap = 12f
        val boxW = (w - 40f - gap) / 2f
        val boxH = min(145f, available * 0.28f)

        homeCard(
            canvas,
            20f,
            gameTop,
            20f + boxW,
            gameTop + boxH,
            "⚡",
            "Quick Games",
            "Challenge yourself"
        )

        homeCard(
            canvas,
            20f + boxW + gap,
            gameTop,
            w - 20f,
            gameTop + boxH,
            "🎯",
            "Focus",
            "Train attention"
        )

        homeCard(
            canvas,
            20f,
            gameTop + boxH + gap,
            20f + boxW,
            gameTop + boxH * 2f + gap,
            "🧠",
            "Memory",
            "Remember more"
        )

        homeCard(
            canvas,
            20f + boxW + gap,
            gameTop + boxH + gap,
            w - 20f,
            gameTop + boxH * 2f + gap,
            "🌙",
            "Relax",
            "Slow down"
        )
    }

    private fun homeCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        icon: String,
        title: String,
        subtitle: String
    ) {
        card(canvas, left, top, right, bottom)

        centerText(
            canvas,
            icon,
            left + 35f,
            top + 35f,
            24f,
            Color.WHITE
        )

        txt(
            canvas,
            title,
            left + 18f,
            top + 75f,
            16f,
            Color.WHITE,
            Paint.Align.LEFT,
            true
        )

        txt(
            canvas,
            subtitle,
            left + 18f,
            top + 100f,
            11f,
            Color.rgb(165, 160, 190)
        )
    }

    // ------------------------------------------------------------
    // GAMES
    // ------------------------------------------------------------

    private fun drawGames(canvas: Canvas) {
        header(canvas, "Quick Games")

        val w = width.toFloat()
        val top = contentTop() + 15f

        gameListCard(
            canvas,
            top,
            "🎯",
            "Target Puzzle",
            "Find the correct tile",
            "PLAY"
        )

        gameListCard(
            canvas,
            top + 132f,
            "🧠",
            "Memory Match",
            "Match all the hidden pairs",
            "PLAY"
        )

        gameListCard(
            canvas,
            top + 264f,
            "⚡",
            "Focus Rush",
            "Tap the moving target",
            "PLAY"
        )

        gameListCard(
            canvas,
            top + 396f,
            "🌙",
            "Relax Breathing",
            "Follow a calm breathing rhythm",
            "START"
        )
    }

    private fun gameListCard(
        canvas: Canvas,
        top: Float,
        icon: String,
        title: String,
        subtitle: String,
        action: String
    ) {
        val w = width.toFloat()

        card(
            canvas,
            20f,
            top,
            w - 20f,
            top + 112f
        )

        centerText(
            canvas,
            icon,
            55f,
            top + 56f,
            28f,
            Color.WHITE
        )

        txt(
            canvas,
            title,
            88f,
            top + 43f,
            17f,
            Color.WHITE,
            Paint.Align.LEFT,
            true
        )

        txt(
            canvas,
            subtitle,
            88f,
            top + 70f,
            12f,
            Color.rgb(165, 160, 190)
        )

        button(
            canvas,
            action,
            w - 112f,
            top + 34f,
            w - 36f,
            top + 78f
        )
    }

    // ------------------------------------------------------------
    // PUZZLE
    // ------------------------------------------------------------

    private fun startPuzzle() {
        puzzleTarget = random.nextInt(16)
        puzzleHits = 0
        puzzleMisses = 0
    }

    private fun drawPuzzle(canvas: Canvas) {
        header(canvas, "Target Puzzle")

        val w = width.toFloat()
        val top = contentTop() + 10f

        txt(
            canvas,
            "Tap the highlighted tile",
            centerX(),
            top + 20f,
            16f,
            Color.WHITE,
            Paint.Align.CENTER,
            true
        )

        txt(
            canvas,
            "Hits: $puzzleHits    Misses: $puzzleMisses",
            centerX(),
            top + 48f,
            13f,
            Color.rgb(175, 170, 200),
            Paint.Align.CENTER
        )

        val gridTop = top + 72f
        val side = min(w - 40f, contentBottom() - gridTop - 20f)
        val cell = side / 4f
        val gap = 7f

        for (row in 0 until 4) {
            for (col in 0 until 4) {

                val index = row * 4 + col

                val left = 20f + col * cell
                val topCell = gridTop + row * cell
                val right = left + cell - gap
                val bottom = topCell + cell - gap

                fillPaint.style = Paint.Style.FILL

                if (index == puzzleTarget) {
                    val pulse = 0.9f + sin(animationTime * 5f) * 0.1f

                    fillPaint.color = Color.rgb(
                        115 + (pulse * 35).toInt(),
                        65,
                        220
                    )
                } else {
                    fillPaint.color = Color.rgb(31, 32, 60)
                }

                canvas.drawRoundRect(
                    left,
                    topCell,
                    right,
                    bottom,
                    13f,
                    13f,
                    fillPaint
                )

                if (index == puzzleTarget) {
                    centerText(
                        canvas,
                        "●",
                        (left + right) / 2f,
                        (topCell + bottom) / 2f,
                        20f,
                        Color.WHITE,
                        true
                    )
                }
            }
        }

        if (puzzleHits >= 10) {
            card(
                canvas,
                30f,
                contentBottom() - 82f,
                w - 30f,
                contentBottom() - 25f
            )

            centerText(
                canvas,
                "Great job! 10 targets completed!",
                centerX(),
                contentBottom() - 54f,
                15f,
                Color.WHITE,
                true
            )
        }
    }

    // ------------------------------------------------------------
    // MEMORY
    // ------------------------------------------------------------

    private fun startMemory() {
        memoryValues.clear()
        memoryOpen.clear()
        memoryMatched.clear()

        memoryFirst = -1
        memorySecond = -1
        memoryMoves = 0
        memoryLocked = false
        memoryStarted = true

        for (i in 0 until 6) {
            memoryValues.add(i)
            memoryValues.add(i)
        }

        memoryValues.shuffle(random)
    }

    private fun drawMemory(canvas: Canvas) {
        header(canvas, "Memory Match")

        val w = width.toFloat()
        val top = contentTop() + 8f

        txt(
            canvas,
            "Match all 6 pairs",
            centerX(),
            top + 18f,
            16f,
            Color.WHITE,
            Paint.Align.CENTER,
            true
        )

        txt(
            canvas,
            "Moves: $memoryMoves",
            centerX(),
            top + 44f,
            13f,
            Color.rgb(175, 170, 200),
            Paint.Align.CENTER
        )

        val gridTop = top + 60f
        val side = min(w - 32f, contentBottom() - gridTop - 15f)
        val cellW = side / 3f
        val cellH = side / 4f
        val gap = 6f

        for (i in 0 until 12) {
            val row = i / 3
            val col = i % 3

            val left = 16f + col * cellW
            val cellTop = gridTop + row * cellH
            val right = left + cellW - gap
            val bottom = cellTop + cellH - gap

            val revealed =
                memoryOpen.contains(i) || memoryMatched.contains(i)

            fillPaint.style = Paint.Style.FILL

            fillPaint.color =
                if (memoryMatched.contains(i)) {
                    Color.rgb(47, 125, 105)
                } else if (revealed) {
                    Color.rgb(85, 62, 150)
                } else {
                    Color.rgb(31, 32, 60)
                }

            canvas.drawRoundRect(
                left,
                cellTop,
                right,
                bottom,
                13f,
                13f,
                fillPaint
            )

            if (revealed) {
                centerText(
                    canvas,
                    memorySymbol(memoryValues[i]),
                    (left + right) / 2f,
                    (cellTop + bottom) / 2f,
                    25f,
                    Color.WHITE,
                    true
                )
            } else {
                centerText(
                    canvas,
                    "?",
                    (left + right) / 2f,
                    (cellTop + bottom) / 2f,
                    23f,
                    Color.rgb(150, 145, 180),
                    true
                )
            }
        }

        if (memoryMatched.size == 12) {
            card(
                canvas,
                25f,
                contentBottom() - 78f,
                w - 25f,
                contentBottom() - 22f
            )

            centerText(
                canvas,
                "🎉 Memory complete!",
                centerX(),
                contentBottom() - 50f,
                17f,
                Color.WHITE,
                true
            )
        }
    }

    private fun memorySymbol(value: Int): String {
        return when (value) {
            0 -> "★"
            1 -> "●"
            2 -> "▲"
            3 -> "◆"
            4 -> "✦"
            else -> "♥"
        }
    }

    // ------------------------------------------------------------
    // FOCUS
    // ------------------------------------------------------------

    private fun startFocus() {
        focusHits = 0
        focusMisses = 0
        focusRunning = true
        focusStartTime = System.currentTimeMillis()

        placeFocusTarget()
    }

    private fun placeFocusTarget() {
        val w = width.toFloat()
        val top = contentTop() + 80f
        val bottom = contentBottom() - 20f

        val minX = 45f
        val maxX = (w - 45f).coerceAtLeast(minX + 1f)

        val minY = top + 20f
        val maxY = (bottom - 20f).coerceAtLeast(minY + 1f)

        focusX = minX + random.nextFloat() * (maxX - minX)
        focusY = minY + random.nextFloat() * (maxY - minY)
    }

    private fun updateFocus() {
        if (!focusRunning) return

        val elapsed = System.currentTimeMillis() - focusStartTime

        if (elapsed >= focusDuration) {
            focusRunning = false
            showMessage("Focus finished: $focusHits hits")
            return
        }

        // Slowly move the target
        focusX += sin(animationTime * 2.7f) * 0.8f
        focusY += cos(animationTime * 2.1f) * 0.6f

        val r = focusRadius + 15f

        focusX = focusX.coerceIn(r, width - r)
        focusY = focusY.coerceIn(
            contentTop() + r,
            contentBottom() - r
        )
    }

    private fun drawFocus(canvas: Canvas) {
        header(canvas, "Focus Rush")

        val remaining =
            ((focusDuration - (System.currentTimeMillis() - focusStartTime))
                .coerceAtLeast(0L)) / 1000L

        txt(
            canvas,
            if (focusRunning) "$remaining s" else "READY",
            centerX(),
            contentTop() + 30f,
            20f,
            Color.WHITE,
            Paint.Align.CENTER,
            true
        )

        txt(
            canvas,
            "Hits: $focusHits   Misses: $focusMisses",
            centerX(),
            contentTop() + 58f,
            14f,
            Color.rgb(175, 170, 200),
            Paint.Align.CENTER
        )

        if (focusRunning) {
            val pulse =
                1f + sin(animationTime * 6f) * 0.08f

            drawGlow(
                canvas,
                focusX,
                focusY,
                75f * pulse,
                Color.rgb(100, 70, 220)
            )

            fillPaint.color = Color.rgb(130, 75, 230)

            canvas.drawCircle(
                focusX,
                focusY,
                focusRadius * pulse,
                fillPaint
            )

            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 3f
            strokePaint.color = Color.WHITE

            canvas.drawCircle(
                focusX,
                focusY,
                focusRadius * 0.5f,
                strokePaint
            )
        } else {
            button(
                canvas,
                "START FOCUS",
                55f,
                height * 0.65f,
                width - 55f,
                height * 0.73f
            )

            centerText(
                canvas,
                "Tap the moving target as accurately as possible.",
                centerX(),
                height * 0.79f,
                13f,
                Color.rgb(175, 170, 200)
            )
        }
    }

    // ------------------------------------------------------------
    // RELAX
    // ------------------------------------------------------------

    private fun startRelax() {
        relaxRunning = true
        relaxStartTime = System.currentTimeMillis()
    }

    private fun drawRelax(canvas: Canvas) {
        header(canvas, "Relax")

        val elapsed =
            if (relaxRunning) {
                System.currentTimeMillis() - relaxStartTime
            } else {
                0L
            }

        val remaining =
            ((relaxDuration - elapsed).coerceAtLeast(0L)) / 1000L

        if (relaxRunning && elapsed >= relaxDuration) {
            relaxRunning = false
            showMessage("Relax session complete")
        }

        val cycle = 8_000L
        val phase = (elapsed % cycle).toFloat() / cycle.toFloat()

        val expanding = phase < 0.5f

        val normalized =
            if (expanding) {
                phase * 2f
            } else {
                1f - (phase - 0.5f) * 2f
            }

        val radius = 75f + normalized * 55f

        drawGlow(
            canvas,
            centerX(),
            height * 0.44f,
            radius * 1.8f,
            Color.rgb(70, 100, 210)
        )

        fillPaint.color = Color.rgb(70, 80, 155)

        canvas.drawCircle(
            centerX(),
            height * 0.44f,
            radius,
            fillPaint
        )

        centerText(
            canvas,
            if (expanding) "BREATHE IN" else "BREATHE OUT",
            centerX(),
            height * 0.44f,
            19f,
            Color.WHITE,
            true
        )

        centerText(
            canvas,
            if (relaxRunning) "$remaining s" else "60 seconds",
            centerX(),
            height * 0.44f + 35f,
            13f,
            Color.argb(210, 255, 255, 255)
        )

        txt(
            canvas,
            "Slow breathing can give your mind a short reset.",
            centerX(),
            height * 0.64f,
            13f,
            Color.rgb(175, 170, 200),
            Paint.Align.CENTER
        )

        if (!relaxRunning) {
            button(
                canvas,
                "START SESSION",
                55f,
                height * 0.71f,
                width - 55f,
                height * 0.79f
            )
        }
    }

    // ------------------------------------------------------------
    // DAILY
    // ------------------------------------------------------------

    private fun drawDaily(canvas: Canvas) {
        header(canvas, "Daily Challenge")

        val w = width.toFloat()
        val top = contentTop() + 20f

        card(
            canvas,
            20f,
            top,
            w - 20f,
            top + 190f
        )

        centerText(
            canvas,
            "TODAY",
            centerX(),
            top + 40f,
            13f,
            Color.rgb(175, 170, 200),
            true
        )

        centerText(
            canvas,
            "One small challenge.",
            centerX(),
            top + 82f,
            23f,
            Color.WHITE,
            true
        )

        centerText(
            canvas,
            "Complete today's puzzle to earn XP.",
            centerX(),
            top + 120f,
            13f,
            Color.rgb(175, 170, 200)
        )

        if (dailyClaimed) {
            button(
                canvas,
                "COMPLETED ✓",
                45f,
                top + 145f,
                w - 45f,
                top + 185f,
                false
            )
        } else {
            button(
                canvas,
                "PLAY DAILY",
                45f,
                top + 145f,
                w - 45f,
                top + 185f
            )
        }

        card(
            canvas,
            20f,
            top + 215f,
            w - 20f,
            top + 330f
        )

        txt(
            canvas,
            "CURRENT STREAK",
            40f,
            top + 250f,
            12f,
            Color.rgb(170, 165, 200),
            Paint.Align.LEFT,
            true
        )

        txt(
            canvas,
            "🔥 $streak days",
            40f,
            top + 292f,
            25f,
            Color.WHITE,
            Paint.Align.LEFT,
            true
        )
    }

    // ------------------------------------------------------------
    // PROFILE
    // ------------------------------------------------------------

    private fun drawProfile(canvas: Canvas) {
        header(canvas, "Profile")

        val w = width.toFloat()
        val top = contentTop() + 15f

        drawGlow(
            canvas,
            centerX(),
            top + 65f,
            65f,
            Color.rgb(90, 60, 220)
        )

        fillPaint.color = Color.rgb(76, 57, 145)
        canvas.drawCircle(
            centerX(),
            top + 65f,
            48f,
            fillPaint
        )

        centerText(
            canvas,
            "MB",
            centerX(),
            top + 65f,
            23f,
            Color.WHITE,
            true
        )

        centerText(
            canvas,
            "Mind Blower",
            centerX(),
            top + 132f,
            21f,
            Color.WHITE,
            true
        )

        centerText(
            canvas,
            "Keep refreshing your mind.",
            centerX(),
            top + 160f,
            13f,
            Color.rgb(175, 170, 200)
        )

        statCard(
            canvas,
            20f,
            top + 205f,
            w / 2f - 8f,
            top + 295f,
            score.toString(),
            "TOTAL XP"
        )

        statCard(
            canvas,
            w / 2f + 8f,
            top + 205f,
            w - 20f,
            top + 295f,
            streak.toString(),
            "STREAK"
        )

        card(
            canvas,
            20f,
            top + 320f,
            w - 20f,
            top + 390f
        )

        txt(
            canvas,
            "Games available",
            40f,
            top + 348f,
            14f,
            Color.WHITE,
            Paint.Align.LEFT,
            true
        )

        txt(
            canvas,
            "Puzzle • Memory • Focus • Relax",
            40f,
            top + 373f,
            12f,
            Color.rgb(165, 160, 190)
        )
    }

    private fun statCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        value: String,
        label: String
    ) {
        card(canvas, left, top, right, bottom)

        centerText(
            canvas,
            value,
            (left + right) / 2f,
            top + 34f,
            25f,
            Color.WHITE,
            true
        )

        centerText(
            canvas,
            label,
            (left + right) / 2f,
            top + 66f,
            10f,
            Color.rgb(160, 155, 185),
            true
        )
    }

    // ------------------------------------------------------------
    // BOTTOM NAVIGATION
    // ------------------------------------------------------------

    private fun drawBottomNavigation(canvas: Canvas) {
        val top = height.toFloat() - navHeight()
        val w = width.toFloat()

        fillPaint.color = Color.argb(235, 12, 13, 30)
        canvas.drawRect(
            0f,
            top,
            w,
            height.toFloat(),
            fillPaint
        )

        val labels = arrayOf(
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

        val screens = arrayOf(
            Screen.HOME,
            Screen.GAMES,
            Screen.FOCUS,
            Screen.DAILY,
            Screen.PROFILE
        )

        val itemW = w / labels.size

        for (i in labels.indices) {
            val cx = itemW * i + itemW / 2f

            val selected = screen == screens[i]

            val color =
                if (selected) Color.rgb(190, 110, 255)
                else Color.rgb(135, 130, 160)

            centerText(
                canvas,
                icons[i],
                cx,
                top + 25f,
                21f,
                color,
                true
            )

            centerText(
                canvas,
                labels[i],
                cx,
                top + 54f,
                10f,
                color,
                selected
            )
        }
    }

    // ------------------------------------------------------------
    // TOAST / MESSAGE
    // ------------------------------------------------------------

    private fun showMessage(text: String) {
        message = text
        messageUntil = System.currentTimeMillis() + 1800L
        invalidate()
    }

    private fun drawToast(canvas: Canvas) {
        if (message.isEmpty()) return

        if (System.currentTimeMillis() > messageUntil) {
            message = ""
            return
        }

        val w = width.toFloat()

        card(
            canvas,
            25f,
            height * 0.08f,
            w - 25f,
            height * 0.08f + 52f,
            18f,
            235
        )

        centerText(
            canvas,
            message,
            centerX(),
            height * 0.08f + 26f,
            13f,
            Color.WHITE,
            true
        )
    }

    // ------------------------------------------------------------
    // TOUCH
    // ------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {

        if (event.action != MotionEvent.ACTION_UP) {
            return true
        }

        val x = event.x
        val y = event.y

        when (screen) {

            Screen.SPLASH -> {
                return true
            }

            Screen.WELCOME -> {
                if (y > height * 0.70f) {
                    welcomeSeen = true

                    prefs.edit()
                        .putBoolean("welcome_seen", true)
                        .apply()

                    screen = Screen.HOME
                    invalidate()
                }

                return true
            }

            Screen.HOME -> {
                handleHomeTouch(x, y)
            }

            Screen.GAMES -> {
                handleGamesTouch(x, y)
            }

            Screen.PUZZLE -> {
                handlePuzzleTouch(x, y)
            }

            Screen.MEMORY -> {
                handleMemoryTouch(x, y)
            }

            Screen.FOCUS -> {
                handleFocusTouch(x, y)
            }

            Screen.RELAX -> {
                handleRelaxTouch(x, y)
            }

            Screen.DAILY -> {
                handleDailyTouch(x, y)
            }

            Screen.PROFILE -> {
                handleProfileTouch(x, y)
            }
        }

        return true
    }

    // ------------------------------------------------------------
    // HOME TOUCH
    // ------------------------------------------------------------

    private fun handleHomeTouch(x: Float, y: Float) {
        val bottom = height - navHeight()

        if (y >= bottom) {
            handleNavigation(x)
            return
        }

        val w = width.toFloat()
        val top = contentTop() + 175f
        val gap = 12f
        val boxW = (w - 40f - gap) / 2f
        val boxH = min(
            145f,
            (contentBottom() - top) * 0.28f
        )

        if (y >= top && y <= top + boxH) {
            if (x < 20f + boxW) {
                previousScreen = Screen.HOME
                startPuzzle()
                screen = Screen.PUZZLE
            } else {
                previousScreen = Screen.HOME
                startFocus()
                screen = Screen.FOCUS
            }

            invalidate()
            return
        }

        if (
            y >= top + boxH + gap &&
            y <= top + boxH * 2f + gap
        ) {
            if (x < 20f + boxW) {
                previousScreen = Screen.HOME
                startMemory()
                screen = Screen.MEMORY
            } else {
                previousScreen = Screen.HOME
                relaxRunning = false
                screen = Screen.RELAX
            }

            invalidate()
            return
        }
    }

    // ------------------------------------------------------------
    // GAMES TOUCH
    // ------------------------------------------------------------

    private fun handleGamesTouch(x: Float, y: Float) {
        val bottom = height - navHeight()

        if (y >= bottom) {
            handleNavigation(x)
            return
        }

        val top = contentTop() + 15f

        when {
            y >= top && y < top + 112f -> {
                previousScreen = Screen.GAMES
                startPuzzle()
                screen = Screen.PUZZLE
            }

            y >= top + 132f &&
                    y < top + 244f -> {
                previousScreen = Screen.GAMES
                startMemory()
                screen = Screen.MEMORY
            }

            y >= top + 264f &&
                    y < top + 376f -> {
                previousScreen = Screen.GAMES
                startFocus()
                screen = Screen.FOCUS
            }

            y >= top + 396f &&
                    y < top + 508f -> {
                previousScreen = Screen.GAMES
                relaxRunning = false
                screen = Screen.RELAX
            }
        }

        invalidate()
    }

    // ------------------------------------------------------------
    // PUZZLE TOUCH
    // ------------------------------------------------------------

    private fun handlePuzzleTouch(x: Float, y: Float) {
        if (isBackPressed(x, y)) {
            goBack()
            return
        }

        val gridTop = contentTop() + 82f
        val side = min(
            width - 40f,
            contentBottom() - gridTop - 20f
        )

        if (
            x < 20f ||
            x > 20f + side ||
            y < gridTop ||
            y > gridTop + side
        ) {
            return
        }

        val cell = side / 4f

        val col = (x - 20f).toInt() / cell.toInt()
        val row = (y - gridTop).toInt() / cell.toInt()

        if (row !in 0..3 || col !in 0..3) {
            return
        }

        val index = row * 4 + col

        if (index == puzzleTarget) {
            puzzleHits++

            score += 5
            saveScore()

            showMessage("+5 XP")

            puzzleTarget = random.nextInt(16)

            if (puzzleHits >= 10) {
                score += 25
                saveScore()
                showMessage("Puzzle complete! +25 XP")
            }
        } else {
            puzzleMisses++
            showMessage("Try the glowing tile")
        }

        invalidate()
    }

    // ------------------------------------------------------------
    // MEMORY TOUCH
    // ------------------------------------------------------------

    private fun handleMemoryTouch(x: Float, y: Float) {
        if (isBackPressed(x, y)) {
            goBack()
            return
        }

        if (memoryLocked) return
        if (memoryMatched.size == 12) return

        val gridTop = contentTop() + 60f
        val side = min(
            width - 32f,
            contentBottom() - gridTop - 15f
        )

        val cellW = side / 3f
        val cellH = side / 4f

        val col = ((x - 16f) / cellW).toInt()
        val row = ((y - gridTop) / cellH).toInt()

        if (col !in 0..2 || row !in 0..3) {
            return
        }

        val index = row * 3 + col

        if (index !in 0 until 12) return
        if (memoryMatched.contains(index)) return
        if (memoryOpen.contains(index)) return

        memoryOpen.add(index)

        if (memoryFirst == -1) {
            memoryFirst = index
            invalidate()
            return
        }

        memorySecond = index
        memoryMoves++

        if (
            memoryValues[memoryFirst] ==
            memoryValues[memorySecond]
        ) {
            memoryMatched.add(memoryFirst)
            memoryMatched.add(memorySecond)

            memoryOpen.remove(memoryFirst)
            memoryOpen.remove(memorySecond)

            score += 10
            saveScore()

            showMessage("+10 XP")

            memoryFirst = -1
            memorySecond = -1

            if (memoryMatched.size == 12) {
                score += 30
                saveScore()
                showMessage("Memory complete! +30 XP")
            }

            invalidate()
        } else {
            memoryLocked = true
            invalidate()

            postDelayed({
                memoryOpen.remove(memoryFirst)
                memoryOpen.remove(memorySecond)

                memoryFirst = -1
                memorySecond = -1
                memoryLocked = false

                invalidate()
            }, 700L)
        }
    }

    // ------------------------------------------------------------
    // FOCUS TOUCH
    // ------------------------------------------------------------

    private fun handleFocusTouch(x: Float, y: Float) {
        if (isBackPressed(x, y)) {
            focusRunning = false
            goBack()
            return
        }

        if (!focusRunning) {
            if (
                y > height * 0.60f &&
                y < height * 0.78f
            ) {
                startFocus()
                invalidate()
            }

            return
        }

        val dx = x - focusX
        val dy = y - focusY
        val distance = kotlin.math.sqrt(
            dx * dx + dy * dy
        )

        if (distance <= focusRadius + 12f) {
            focusHits++

            score += 3
            saveScore()

            placeFocusTarget()

            showMessage("+3 XP")
        } else {
            focusMisses++
            showMessage("Miss")
        }

        invalidate()
    }

    // ------------------------------------------------------------
    // RELAX TOUCH
    // ------------------------------------------------------------

    private fun handleRelaxTouch(x: Float, y: Float) {
        if (isBackPressed(x, y)) {
            relaxRunning = false
            goBack()
            return
        }

        if (!relaxRunning) {
            if (
                y > height * 0.66f &&
                y < height * 0.82f
            ) {
                startRelax()
                invalidate()
            }
        }
    }

    // ------------------------------------------------------------
    // DAILY TOUCH
    // ------------------------------------------------------------

    private fun handleDailyTouch(x: Float, y: Float) {
        val bottom = height - navHeight()

        if (y >= bottom) {
            handleNavigation(x)
            return
        }

        if (dailyClaimed) return

        val top = contentTop() + 20f

        if (
            y >= top + 135f &&
            y <= top + 205f
        ) {
            dailyClaimed = true

            score += 20

            if (streak < 999) {
                streak++
            }

            prefs.edit()
                .putString("daily_date", todayKey())
                .putInt("score", score)
                .putInt("streak", streak)
                .apply()

            showMessage("Daily complete! +20 XP")

            invalidate()
        }
    }

    // ------------------------------------------------------------
    // PROFILE TOUCH
    // ------------------------------------------------------------

    private fun handleProfileTouch(x: Float, y: Float) {
        val bottom = height - navHeight()

        if (y >= bottom) {
            handleNavigation(x)
        }
    }

    // ------------------------------------------------------------
    // NAVIGATION
    // ------------------------------------------------------------

    private fun handleNavigation(x: Float) {
        val itemW = width.toFloat() / 5f
        val index = (x / itemW).toInt().coerceIn(0, 4)

        screen = when (index) {
            0 -> Screen.HOME
            1 -> Screen.GAMES
            2 -> {
                if (!focusRunning) {
                    startFocus()
                }
                Screen.FOCUS
            }

            3 -> Screen.DAILY
            else -> Screen.PROFILE
        }

        invalidate()
    }

    // ------------------------------------------------------------
    // BACK
    // ------------------------------------------------------------

    fun goBack(): Boolean {
        when (screen) {
            Screen.PUZZLE,
            Screen.MEMORY,
            Screen.FOCUS,
            Screen.RELAX -> {
                focusRunning = false
                relaxRunning = false
                screen = previousScreen
                invalidate()
                return true
            }

            Screen.HOME,
            Screen.GAMES,
            Screen.DAILY,
            Screen.PROFILE -> {
                screen = Screen.HOME
                invalidate()
                return true
            }

            Screen.WELCOME -> {
                return false
            }

            Screen.SPLASH -> {
                return false
            }
        }
    }

    private fun isBackPressed(
        x: Float,
        y: Float
    ): Boolean {
        return y < topHeight() &&
                x < 65f
    }

    // ------------------------------------------------------------
    // SCORE
    // ------------------------------------------------------------

    private fun saveScore() {
        prefs.edit()
            .putInt("score", score)
            .putInt("streak", streak)
            .apply()
    }

    // ------------------------------------------------------------
    // DAILY DATE
    // ------------------------------------------------------------

    private fun todayKey(): String {
        val calendar = java.util.Calendar.getInstance()

        val year = calendar.get(java.util.Calendar.YEAR)
        val month =
            calendar.get(java.util.Calendar.MONTH) + 1
        val day =
            calendar.get(java.util.Calendar.DAY_OF_MONTH)

        return "$year-$month-$day"
    }
}
