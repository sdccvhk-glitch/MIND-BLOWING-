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
import android.os.SystemClock
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

        window.statusBarColor = Color.rgb(4, 7, 24)
        window.navigationBarColor = Color.rgb(4, 7, 24)

        mindView = MindBlowView(this)
        setContentView(mindView)
    }

    @Suppress("DEPRECATION")
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
    FOCUS,
    COLOR,
    MEMORY,
    NUMBER,
    CALM,
    DAILY,
    PROFILE
}

private class MindBlowView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val prefs =
        context.getSharedPreferences(
            "mindblow_data",
            Context.MODE_PRIVATE
        )

    private var screen =
        if (prefs.getBoolean("welcome_seen", false)) {
            Screen.HOME
        } else {
            Screen.SPLASH
        }

    private var startedAt = SystemClock.uptimeMillis()
    private var animation = 0f

    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)

    private var toastText = ""
    private var toastUntil = 0L

    // ---------------------------------------------------------
    // FOCUS ORB
    // ---------------------------------------------------------

    private var focusScore = 0
    private var focusRound = 0
    private var focusX = 195f
    private var focusY = 300f
    private var focusRadius = 27f
    private var focusEnd = 0L
    private var focusStarted = false

    // ---------------------------------------------------------
    // COLOR HUNT
    // ---------------------------------------------------------

    private var colorTarget = 0
    private var colorLevel = 1
    private var colorGrid = 4
    private var colorRoundStarted = false
    private var colorMessage = ""

    // ---------------------------------------------------------
    // MEMORY MATCH
    // ---------------------------------------------------------

    private val memorySymbols = arrayOf(
        "★", "◆", "●", "✦",
        "☀", "☾", "✿", "❖"
    )

    private var memoryCards = mutableListOf<Int>()
    private var memoryOpen1 = -1
    private var memoryOpen2 = -1
    private var memoryMatched = BooleanArray(16)
    private var memoryLocked = false
    private var memoryMoves = 0
    private var memoryPairs = 0
    private var memoryStarted = false
    private var memoryHideAt = 0L

    // ---------------------------------------------------------
    // NUMBER FLOW
    // ---------------------------------------------------------

    private var numberSequence = mutableListOf<Int>()
    private var numberInput = ""
    private var numberLevel = 1
    private var numberShowing = false
    private var numberShowUntil = 0L
    private var numberRunning = false

    // ---------------------------------------------------------
    // CALM FLOW
    // ---------------------------------------------------------

    private var calmRunning = false
    private var calmStart = 0L
    private var calmSeconds = 0
    private var calmPhase = "READY"

    init {
        isFocusable = true
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
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

        drawBackground(canvas, w, h)

        when (screen) {
            Screen.SPLASH -> drawSplash(canvas, w, h)
            Screen.WELCOME -> drawWelcome(canvas, w, h)
            Screen.HOME -> drawHome(canvas, w, h)
            Screen.GAMES -> drawGames(canvas, w, h)
            Screen.FOCUS -> drawFocus(canvas, w, h)
            Screen.COLOR -> drawColorHunt(canvas, w, h)
            Screen.MEMORY -> drawMemory(canvas, w, h)
            Screen.NUMBER -> drawNumberFlow(canvas, w, h)
            Screen.CALM -> drawCalm(canvas, w, h)
            Screen.DAILY -> drawDaily(canvas, w, h)
            Screen.PROFILE -> drawProfile(canvas, w, h)
        }

        if (toastUntil > SystemClock.uptimeMillis()) {
            drawToast(canvas, w, h)
        }

        if (
            screen == Screen.SPLASH &&
            SystemClock.uptimeMillis() - startedAt > 1400L
        ) {
            screen = Screen.WELCOME
        }

        updateGames()

        invalidate()
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
            Color.rgb(3, 6, 25),
            Color.rgb(29, 8, 57),
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

        val t = animation

        // Large moving glow
        paint.color = Color.argb(30, 25, 225, 255)

        canvas.drawCircle(
            w * 0.15f + sin(t) * 45f,
            h * 0.18f + cos(t * 1.2f) * 35f,
            150f,
            paint
        )

        paint.color = Color.argb(28, 145, 70, 255)

        canvas.drawCircle(
            w * 0.85f + cos(t * 0.7f) * 50f,
            h * 0.35f + sin(t) * 40f,
            175f,
            paint
        )

        paint.color = Color.argb(20, 20, 255, 180)

        canvas.drawCircle(
            w * 0.50f + sin(t * 0.5f) * 55f,
            h * 0.82f,
            190f,
            paint
        )

        // Orbit lines
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(22, 110, 220, 255)

        for (i in 0 until 7) {
            canvas.drawCircle(
                w / 2f,
                h * 0.44f,
                80f + i * 48f + sin(t + i) * 6f,
                paint
            )
        }

        paint.style = Paint.Style.FILL

        // Floating particles
        for (i in 0 until 22) {
            val px =
                ((i * 79f + animation * (12f + i)) % (w + 40f)) - 20f

            val py =
                ((i * 127f + sin(animation + i) * 25f) % (h + 40f)) - 20f

            paint.color = Color.argb(
                55,
                120,
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
            225,
            12,
            18,
            44
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
        paint.strokeWidth = 1.1f
        paint.color = Color.argb(
            65,
            100,
            190,
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
    // BUTTON
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
            Color.rgb(15, 215, 255),
            Color.rgb(130, 65, 255),
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
            49f,
            38f
        )

        text(
            canvas,
            title,
            58f,
            40f,
            23f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            58f,
            62f,
            11f,
            0xff9ca9d0.toInt()
        )
    }

    // =========================================================
    // NAVIGATION
    // =========================================================

    private fun navigation(
        canvas: Canvas,
        w: Float,
        h: Float,
        selected: Int
    ) {

        val top = h - 80f

        card(
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

        val labels = arrayOf(
            "Home",
            "Games",
            "Focus",
            "Daily",
            "Profile"
        )

        for (i in 0..4) {

            val x =
                w * (i + 0.5f) / 5f

            val color =
                if (i == selected) {
                    0xff55e7ff.toInt()
                } else {
                    0xff7582a8.toInt()
                }

            text(
                canvas,
                icons[i],
                x,
                top + 29f,
                19f,
                color,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                labels[i],
                x,
                top + 50f,
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

        val pulse =
            1f + sin(animation * 2f) * 0.08f

        text(
            canvas,
            "✦",
            w / 2f,
            h * 0.40f,
            80f * pulse,
            0xff5fe9ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "MindBlow",
            w / 2f,
            h * 0.50f,
            42f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "RESET • PLAY • REFRESH",
            w / 2f,
            h * 0.55f,
            12f,
            0xffaebbe1.toInt(),
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
            72f,
            32f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Your tiny digital brain break.",
            w / 2f,
            101f,
            14f,
            0xffaab7d9.toInt(),
            false,
            Paint.Align.CENTER
        )

        val pulse =
            1f + sin(animation * 2f) * 0.08f

        text(
            canvas,
            "✦",
            w / 2f,
            h * 0.44f,
            92f * pulse,
            0xff5de8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "PLAY • FOCUS • RELAX",
            w / 2f,
            h * 0.59f,
            23f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Short games made for a quick mental reset.",
            w / 2f,
            h * 0.65f,
            13f,
            0xffaeb9d9.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            canvas,
            "START MY JOURNEY",
            w * 0.12f,
            h * 0.76f,
            w * 0.88f,
            h * 0.84f
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
            39f,
            28f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Level ${score / 250 + 1}  •  Mind reset zone",
            20f,
            61f,
            11f,
            0xff9ba8cf.toInt()
        )

        card(
            canvas,
            w - 116f,
            17f,
            w - 18f,
            57f,
            16f
        )

        text(
            canvas,
            "✦ $score",
            w - 67f,
            42f,
            14f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )

        card(
            canvas,
            18f,
            80f,
            w - 18f,
            184f,
            23f
        )

        text(
            canvas,
            "READY FOR A RESET?",
            34f,
            111f,
            11f,
            0xff5ce8ff.toInt(),
            true
        )

        text(
            canvas,
            "Pick a challenge.",
            34f,
            143f,
            22f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "A few minutes can refresh your attention.",
            34f,
            165f,
            11f,
            0xffa8b5d8.toInt()
        )

        button(
            canvas,
            "DAILY RESET  ›",
            34f,
            178f,
            w - 34f,
            218f
        )

        text(
            canvas,
            "Mind games",
            20f,
            250f,
            19f,
            Color.WHITE,
            true
        )

        gameCard(
            canvas,
            "◎",
            "Focus Orb",
            "Tap the moving glow",
            18f,
            266f,
            w / 2f - 8f,
            355f
        )

        gameCard(
            canvas,
            "◆",
            "Color Hunt",
            "Find the odd tile",
            w / 2f + 8f,
            266f,
            w - 18f,
            355f
        )

        gameCard(
            canvas,
            "✦",
            "Memory",
            "Match the pairs",
            18f,
            365f,
            w / 2f - 8f,
            454f
        )

        gameCard(
            canvas,
            "123",
            "Number Flow",
            "Remember numbers",
            w / 2f + 8f,
            365f,
            w - 18f,
            454f
        )

        navigation(
            canvas,
            w,
            h,
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
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {

        card(
            canvas,
            l,
            t,
            r,
            b,
            19f
        )

        text(
            canvas,
            icon,
            l + 34f,
            t + 40f,
            if (icon == "123") 17f else 23f,
            0xff61e6ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            title,
            l + 18f,
            t + 67f,
            15f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            l + 18f,
            t + 84f,
            9.5f,
            0xff98a6cc.toInt()
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

        header(
            canvas,
            "Mind Games",
            "Choose your mental reset"
        )

        gameCard(
            canvas,
            "◎",
            "Focus Orb",
            "Reaction & focus",
            18f,
            92f,
            w / 2f - 8f,
            195f
        )

        gameCard(
            canvas,
            "◆",
            "Color Hunt",
            "Visual attention",
            w / 2f + 8f,
            92f,
            w - 18f,
            195f
        )

        gameCard(
            canvas,
            "✦",
            "Memory Match",
            "Memory training",
            18f,
            207f,
            w / 2f - 8f,
            310f
        )

        gameCard(
            canvas,
            "123",
            "Number Flow",
            "Working memory",
            w / 2f + 8f,
            207f,
            w - 18f,
            310f
        )

        gameCard(
            canvas,
            "◌",
            "Calm Flow",
            "Breathing reset",
            18f,
            322f,
            w - 18f,
            425f
        )

        card(
            canvas,
            18f,
            438f,
            w - 18f,
            505f,
            18f
        )

        text(
            canvas,
            "TIP",
            34f,
            466f,
            10f,
            0xff5ce7ff.toInt(),
            true
        )

        text(
            canvas,
            "Try 2–3 minutes between study sessions.",
            34f,
            489f,
            12f,
            0xffaab7d8.toInt()
        )

        navigation(
            canvas,
            w,
            h,
            1
        )
    }

    // =========================================================
    // FOCUS ORB
    // =========================================================

    private fun drawFocus(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Focus Orb",
            "Tap the moving light"
        )

        card(
            canvas,
            18f,
            82f,
            w - 18f,
            136f,
            17f
        )

        text(
            canvas,
            "SCORE  $focusScore",
            34f,
            110f,
            14f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "ROUND  $focusRound",
            w - 34f,
            110f,
            12f,
            0xff91a0c9.toInt(),
            true,
            Paint.Align.RIGHT
        )

        if (!focusStarted) {

            text(
                canvas,
                "READY?",
                w / 2f,
                255f,
                32f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                "Tap START, then hit the glowing orb.",
                w / 2f,
                287f,
                13f,
                0xffaab7d9.toInt(),
                false,
                Paint.Align.CENTER
            )

            button(
                canvas,
                "START FOCUS",
                w * 0.20f,
                330f,
                w * 0.80f,
                382f
            )

        } else {

            val remaining =
                maxOf(
                    0L,
                    focusEnd - SystemClock.uptimeMillis()
                )

            text(
                canvas,
                "${remaining / 1000}.${(remaining % 1000) / 100}s",
                w / 2f,
                170f,
                18f,
                0xffaebce2.toInt(),
                true,
                Paint.Align.CENTER
            )

            val pulse =
                1f + sin(animation * 4f) * 0.12f

            paint.color = Color.argb(
                30,
                70,
                225,
                255
            )

            canvas.drawCircle(
                focusX,
                focusY,
                60f * pulse,
                paint
            )

            paint.color = 0xff51e8ff.toInt()

            canvas.drawCircle(
                focusX,
                focusY,
                focusRadius,
                paint
            )

            paint.color = Color.WHITE

            canvas.drawCircle(
                focusX - 7f,
                focusY - 7f,
                5f,
                paint
            )

            text(
                canvas,
                "TAP!",
                w / 2f,
                470f,
                20f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        navigation(
            canvas,
            w,
            h,
            2
        )
    }

    // =========================================================
    // COLOR HUNT
    // =========================================================

    private fun drawColorHunt(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Color Hunt",
            "Find the different tile"
        )

        text(
            canvas,
            "LEVEL $colorLevel",
            20f,
            94f,
            14f,
            0xff62e7ff.toInt(),
            true
        )

        text(
            canvas,
            colorMessage,
            w - 20f,
            94f,
            12f,
            0xffa9b7da.toInt(),
            false,
            Paint.Align.RIGHT
        )

        val gridSize =
            if (colorGrid < 5) 4 else 5

        val boardSize =
            min(w - 40f, 330f)

        val left =
            (w - boardSize) / 2f

        val top = 125f

        val cell =
            boardSize / gridSize.toFloat()

        val base = Color.rgb(
            45,
            160,
            230
        )

        val odd = Color.rgb(
            80,
            195,
            240
        )

        for (i in 0 until gridSize * gridSize) {

            val row =
                i / gridSize

            val col =
                i % gridSize

            val l =
                left + col * cell + 4f

            val tt =
                top + row * cell + 4f

            val r =
                left + (col + 1) * cell - 4f

            val b =
                top + (row + 1) * cell - 4f

            paint.color =
                if (i == colorTarget) odd else base

            canvas.drawRoundRect(
                l,
                tt,
                r,
                b,
                15f,
                15f,
                paint
            )
        }

        text(
            canvas,
            "One tile is slightly different.",
            w / 2f,
            top + boardSize + 32f,
            14f,
            0xffb0bddf.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Trust your eyes.",
            w / 2f,
            top + boardSize + 56f,
            11f,
            0xff7e8caf.toInt(),
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

        header(
            canvas,
            "Memory Match",
            "Match every pair"
        )

        text(
            canvas,
            "PAIRS  $memoryPairs / 8",
            20f,
            94f,
            13f,
            0xff61e7ff.toInt(),
            true
        )

        text(
            canvas,
            "MOVES  $memoryMoves",
            w - 20f,
            94f,
            12f,
            0xffa3b1d3.toInt(),
            true,
            Paint.Align.RIGHT
        )

        if (!memoryStarted) {

            text(
                canvas,
                "Ready?",
                w / 2f,
                245f,
                32f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                "Find all 8 matching pairs.",
                w / 2f,
                278f,
                13f,
                0xffaab7d9.toInt(),
                false,
                Paint.Align.CENTER
            )

            button(
                canvas,
                "START MEMORY",
                w * 0.20f,
                320f,
                w * 0.80f,
                372f
            )

            return
        }

        val board =
            min(w - 38f, 350f)

        val left =
            (w - board) / 2f

        val top = 112f
        val cell = board / 4f

        for (i in 0 until 16) {

            val row =
                i / 4

            val col =
                i % 4

            val l =
                left + col * cell + 4f

            val tt =
                top + row * cell + 4f

            val r =
                left + (col + 1) * cell - 4f

            val b =
                top + (row + 1) * cell - 4f

            val open =
                memoryMatched[i] ||
                        i == memoryOpen1 ||
                        i == memoryOpen2

            if (open) {

                paint.color =
                    Color.argb(
                        235,
                        18,
                        55,
                        82
                    )

                canvas.drawRoundRect(
                    l,
                    tt,
                    r,
                    b,
                    13f,
                    13f,
                    paint
                )

                val symbol =
                    memorySymbols[
                        memoryCards[i]
                    ]

                text(
                    canvas,
                    symbol,
                    (l + r) / 2f,
                    (tt + b) / 2f + 10f,
                    25f,
                    0xff61e8ff.toInt(),
                    true,
                    Paint.Align.CENTER
                )

            } else {

                paint.color =
                    Color.rgb(
                        22,
                        29,
                        62
                    )

                canvas.drawRoundRect(
                    l,
                    tt,
                    r,
                    b,
                    13f,
                    13f,
                    paint
                )

                text(
                    canvas,
                    "?",
                    (l + r) / 2f,
                    (tt + b) / 2f + 9f,
                    22f,
                    0xff6979a8.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            }
        }

        if (memoryPairs >= 8) {

            card(
                canvas,
                28f,
                310f,
                w - 28f,
                410f,
                22f
            )

            text(
                canvas,
                "MEMORY COMPLETE!",
                w / 2f,
                345f,
                19f,
                0xff62e7ff.toInt(),
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                "+100 XP",
                w / 2f,
                374f,
                15f,
                0xffffd36a.toInt(),
                true,
                Paint.Align.CENTER
            )
        }
    }

    // =========================================================
    // NUMBER FLOW
    // =========================================================

    private fun drawNumberFlow(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Number Flow",
            "Remember the sequence"
        )

        text(
            canvas,
            "LEVEL $numberLevel",
            20f,
            94f,
            13f,
            0xff61e7ff.toInt(),
            true
        )

        if (!numberRunning) {

            text(
                canvas,
                "Train your working memory.",
                w / 2f,
                230f,
                21f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                "Remember the numbers, then enter them.",
                w / 2f,
                265f,
                13f,
                0xffaab7d9.toInt(),
                false,
                Paint.Align.CENTER
            )

            button(
                canvas,
                "START NUMBER FLOW",
                w * 0.13f,
                320f,
                w * 0.87f,
                372f
            )

            return
        }

        if (numberShowing) {

            text(
                canvas,
                numberSequence.joinToString("  "),
                w / 2f,
                245f,
                35f,
                0xff62e7ff.toInt(),
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                "MEMORIZE",
                w / 2f,
                285f,
                12f,
                0xff9eacd2.toInt(),
                true,
                Paint.Align.CENTER
            )

        } else {

            card(
                canvas,
                35f,
                170f,
                w - 35f,
                245f,
                20f
            )

            text(
                canvas,
                if (numberInput.isEmpty()) "Enter sequence" else numberInput,
                w / 2f,
                217f,
                27f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )

            drawNumberPad(
                canvas,
                w
            )
        }
    }

    private fun drawNumberPad(
        canvas: Canvas,
        w: Float
    ) {

        val startY = 280f
        val gap = 9f
        val size = (w - 64f) / 3f

        for (n in 1..9) {

            val index = n - 1

            val row =
                index / 3

            val col =
                index % 3

            val l =
                20f + col * (size + gap)

            val t =
                startY + row * (size * 0.65f + gap)

            val r =
                l + size

            val b =
                t + size * 0.55f

            card(
                canvas,
                l,
                t,
                r,
                b,
                14f
            )

            text(
                canvas,
                n.toString(),
                (l + r) / 2f,
                (t + b) / 2f + 8f,
                19f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        val zeroTop =
            startY + 3 * (size * 0.65f + gap)

        card(
            canvas,
            20f,
            zeroTop,
            w / 2f - 5f,
            zeroTop + size * 0.55f,
            14f
        )

        text(
            canvas,
            "0",
            w / 4f - 2f,
            zeroTop + size * 0.35f,
            19f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        button(
            canvas,
            "CHECK",
            w / 2f + 5f,
            zeroTop,
            w - 20f,
            zeroTop + size * 0.55f
        )
    }

    // =========================================================
    // CALM FLOW
    // =========================================================

    private fun drawCalm(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Calm Flow",
            "A tiny breathing reset"
        )

        val elapsed =
            if (calmRunning) {
                SystemClock.uptimeMillis() - calmStart
            } else {
                0L
            }

        if (calmRunning) {
            calmSeconds =
                (elapsed / 1000L).toInt()

            val phaseTime =
                (elapsed / 4000L) % 3L

            calmPhase =
                when (phaseTime.toInt()) {
                    0 -> "BREATHE IN"
                    1 -> "HOLD"
                    else -> "BREATHE OUT"
                }
        }

        val phaseProgress =
            ((elapsed % 4000L).toFloat() / 4000f)

        val wave =
            when (calmPhase) {
                "BREATHE IN" -> phaseProgress
                "BREATHE OUT" -> 1f - phaseProgress
                else -> 1f
            }

        val radius =
            65f + wave * 75f

        paint.color =
            Color.argb(
                28,
                70,
                220,
                255
            )

        canvas.drawCircle(
            w / 2f,
            250f,
            radius + 35f,
            paint
        )

        paint.color =
            0xff57e7ff.toInt()

        canvas.drawCircle(
            w / 2f,
            250f,
            radius,
            paint
        )

        text(
            canvas,
            if (calmRunning) calmPhase else "READY",
            w / 2f,
            255f,
            18f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            if (calmRunning)
                "$calmSeconds seconds"
            else
                "Slow breathing can help you pause.",
            w / 2f,
            330f,
            13f,
            0xffaebadd.toInt(),
            false,
            Paint.Align.CENTER
        )

        if (!calmRunning) {

            button(
                canvas,
                "START 60 SECOND RESET",
                w * 0.12f,
                380f,
                w * 0.88f,
                432f
            )

        } else {

            button(
                canvas,
                "END SESSION",
                w * 0.20f,
                380f,
                w * 0.80f,
                432f
            )
        }
    }

    // =========================================================
    // DAILY
    // =========================================================

    private fun drawDaily(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Daily Reset",
            "One small challenge today"
        )

        card(
            canvas,
            18f,
            90f,
            w - 18f,
            280f,
            24f
        )

        text(
            canvas,
            "TODAY'S MISSION",
            36f,
            127f,
            11f,
            0xff61e7ff.toInt(),
            true
        )

        text(
            canvas,
            "Play 1 mind game",
            36f,
            165f,
            25f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Finish any game to complete",
            36f,
            195f,
            13f,
            0xffaab7d8.toInt()
        )

        text(
            canvas,
            "your daily reset.",
            36f,
            216f,
            13f,
            0xffaab7d8.toInt()
        )

        button(
            canvas,
            "PLAY A GAME",
            36f,
            232f,
            w - 36f,
            270f
        )

        text(
            canvas,
            "🔥  $streak day streak",
            22f,
            330f,
            19f,
            0xffffb52e.toInt(),
            true
        )

        card(
            canvas,
            20f,
            355f,
            w - 20f,
            425f,
            20f
        )

        text(
            canvas,
            "Your progress",
            38f,
            388f,
            15f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "$score XP earned",
            38f,
            412f,
            12f,
            0xffa7b5d8.toInt()
        )

        navigation(
            canvas,
            w,
            h,
            3
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
            88f,
            w - 18f,
            220f,
            24f
        )

        paint.color =
            Color.argb(
                40,
                60,
                220,
                255
            )

        canvas.drawCircle(
            68f,
            151f,
            42f,
            paint
        )

        text(
            canvas,
            "✦",
            68f,
            167f,
            42f,
            0xff62e8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Mind Explorer",
            125f,
            135f,
            20f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Level ${score / 250 + 1}",
            125f,
            163f,
            13f,
            0xffaab7d9.toInt()
        )

        text(
            canvas,
            "$score XP",
            125f,
            190f,
            13f,
            0xffffd46a.toInt(),
            true
        )

        text(
            canvas,
            "🔥 $streak day streak",
            125f,
            214f,
            12f,
            0xffffb52e.toInt()
        )

        card(
            canvas,
            18f,
            240f,
            w - 18f,
            410f,
            22f
        )

        text(
            canvas,
            "Games completed",
            38f,
            280f,
            15f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "${score / 20}",
            w - 38f,
            280f,
            18f,
            0xff5fe7ff.toInt(),
            true,
            Paint.Align.RIGHT
        )

        text(
            canvas,
            "XP needed for next level",
            38f,
            325f,
            14f,
            0xffa5b2d5.toInt()
        )

        text(
            canvas,
            "${250 - (score % 250)}",
            w - 38f,
            325f,
            14f,
            Color.WHITE,
            true,
            Paint.Align.RIGHT
        )

        text(
            canvas,
            "Keep playing to unlock more progress.",
            38f,
            365f,
            11f,
            0xff7787b1.toInt()
        )

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

        val top =
            h - 138f

        card(
            canvas,
            25f,
            top,
            w - 25f,
            top + 54f,
            18f
        )

        text(
            canvas,
            toastText,
            w / 2f,
            top + 33f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    private fun toast(message: String) {

        toastText = message

        toastUntil =
            SystemClock.uptimeMillis() + 1400L
    }

    // =========================================================
    // GAME UPDATES
    // =========================================================

    private fun updateGames() {

        val now =
            SystemClock.uptimeMillis()

        if (
            screen == Screen.FOCUS &&
            focusStarted &&
            now >= focusEnd
        ) {

            focusStarted = false

            if (focusScore > 0) {
                score += focusScore * 5
                save()
                toast(
                    "Focus finished! +${focusScore * 5} XP"
                )
            }

            focusScore = 0
        }

        if (
            screen == Screen.MEMORY &&
            memoryHideAt > 0L &&
            now >= memoryHideAt &&
            memoryOpen1 >= 0 &&
            memoryOpen2 >= 0
        ) {

            memoryOpen1 = -1
            memoryOpen2 = -1
            memoryLocked = false
            memoryHideAt = 0L
        }

        if (
            screen == Screen.NUMBER &&
            numberShowing &&
            now >= numberShowUntil
        ) {

            numberShowing = false
        }

        if (
            screen == Screen.CALM &&
            calmRunning &&
            calmSeconds >= 60
        ) {

            calmRunning = false
            score += 25
            streak += 1
            save()

            toast(
                "60 second reset complete! +25 XP"
            )
        }
    }

    // =========================================================
    // TOUCH
    // =========================================================

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        if (event.action != MotionEvent.ACTION_UP) {
            return true
        }

        val x =
            event.x

        val y =
            event.y

        val w =
            width.toFloat()

        val h =
            height.toFloat()

        when (screen) {

            Screen.SPLASH -> {
                screen = Screen.WELCOME
            }

            Screen.WELCOME -> {

                if (y > h * 0.68f) {

                    prefs.edit()
                        .putBoolean(
                            "welcome_seen",
                            true
                        )
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

            Screen.FOCUS -> {
                focusTouch(x, y, w, h)
            }

            Screen.COLOR -> {
                colorTouch(x, y, w, h)
            }

            Screen.MEMORY -> {
                memoryTouch(x, y, w, h)
            }

            Screen.NUMBER -> {
                numberTouch(x, y, w, h)
            }

            Screen.CALM -> {
                calmTouch(x, y, w, h)
            }

            Screen.DAILY -> {
                dailyTouch(x, y, w, h)
            }

            Screen.PROFILE -> {
                profileTouch(x, y, w, h)
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
                    startFocus()
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

        if (y in 175f..225f) {

            screen = Screen.DAILY
            return
        }

        if (y in 266f..355f) {

            if (x < w / 2f) {
                startFocus()
            } else {
                startColor()
            }

            return
        }

        if (y in 365f..455f) {

            if (x < w / 2f) {
                startMemory()
            } else {
                startNumber()
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

        if (y < 75f) {
            screen = Screen.HOME
            return
        }

        if (y > h - 95f) {

            when {
                x < w * 0.20f ->
                    screen = Screen.HOME

                x < w * 0.40f ->
                    screen = Screen.GAMES

                x < w * 0.60f ->
                    startFocus()

                x < w * 0.80f ->
                    screen = Screen.DAILY

                else ->
                    screen = Screen.PROFILE
            }

            return
        }

        if (y in 92f..195f) {

            if (x < w / 2f) {
                startFocus()
            } else {
                startColor()
            }

            return
        }

        if (y in 207f..310f) {

            if (x < w / 2f) {
                startMemory()
            } else {
                startNumber()
            }

            return
        }

        if (y in 322f..425f) {

            startCalm()
        }
    }

    // =========================================================
    // FOCUS TOUCH
    // =========================================================

    private fun focusTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        if (!focusStarted) {

            if (y in 310f..410f) {
                startFocus()
            }

            return
        }

        val dx =
            x - focusX

        val dy =
            y - focusY

        val distance =
            dx * dx + dy * dy

        if (distance <= focusRadius * focusRadius * 2.2f) {

            focusScore += 1
            focusRound += 1

            score += 5
            save()

            moveFocusOrb()

            toast(
                "+5 XP  •  Nice focus!"
            )
        }
    }

    private fun startFocus() {

        screen = Screen.FOCUS

        focusScore = 0
        focusRound = 1
        focusStarted = true

        focusEnd =
            SystemClock.uptimeMillis() + 30000L

        moveFocusOrb()
    }

    private fun moveFocusOrb() {

        val w =
            width.toFloat()

        focusX =
            40f + Random.nextFloat() * (w - 80f)

        focusY =
            210f + Random.nextFloat() * 220f

        focusRadius =
            24f + Random.nextFloat() * 7f
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

        if (y < 75f) {

            screen = Screen.GAMES
            return
        }

        val gridSize =
            if (colorGrid < 5) 4 else 5

        val boardSize =
            min(w - 40f, 330f)

        val left =
            (w - boardSize) / 2f

        val top = 125f

        if (
            x < left ||
            x > left + boardSize ||
            y < top ||
            y > top + boardSize
        ) {
            return
        }

        val cell =
            boardSize / gridSize.toFloat()

        val col =
            ((x - left) / cell).toInt()

        val row =
            ((y - top) / cell).toInt()

        if (
            col < 0 ||
            col >= gridSize ||
            row < 0 ||
            row >= gridSize
        ) {
            return
        }

        val index =
            row * gridSize + col

        if (index == colorTarget) {

            score += 10 + colorLevel * 2
            colorLevel += 1

            if (colorLevel % 4 == 0) {
                colorGrid = 5
            }

            save()

            colorMessage =
                "+${10 + colorLevel * 2} XP"

            makeColorRound()

            toast(
                "Great eyes! +XP"
            )
        } else {

            colorMessage =
                "Try again"

            toast(
                "Almost! Find the brighter tile."
            )
        }
    }

    private fun startColor() {

        screen = Screen.COLOR

        colorLevel = 1
        colorGrid = 4

        makeColorRound()
    }

    private fun makeColorRound() {

        val count =
            colorGrid * colorGrid

        colorTarget =
            Random.nextInt(count)
    }

    // =========================================================
    // MEMORY TOUCH
    // =========================================================

    private fun memoryTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 75f) {

            screen = Screen.GAMES
            return
        }

        if (!memoryStarted) {

            if (y in 300f..390f) {
                startMemory()
            }

            return
        }

        if (memoryPairs >= 8) {

            if (y > 300f && y < 430f) {
                startMemory()
            }

            return
        }

        if (memoryLocked) {
            return
        }

        val board =
            min(w - 38f, 350f)

        val left =
            (w - board) / 2f

        val top = 112f

        if (
            x < left ||
            x > left + board ||
            y < top ||
            y > top + board
        ) {
            return
        }

        val cell =
            board / 4f

        val col =
            ((x - left) / cell).toInt()

        val row =
            ((y - top) / cell).toInt()

        if (
            col !in 0..3 ||
            row !in 0..3
        ) {
            return
        }

        val index =
            row * 4 + col

        if (
            memoryMatched[index] ||
            index == memoryOpen1
        ) {
            return
        }

        if (memoryOpen1 == -1) {

            memoryOpen1 = index

        } else {

            memoryOpen2 = index
            memoryMoves += 1
            memoryLocked = true

            if (
                memoryCards[memoryOpen1] ==
                memoryCards[memoryOpen2]
            ) {

                memoryMatched[memoryOpen1] = true
                memoryMatched[memoryOpen2] = true

                memoryPairs += 1

                score += 15

                save()

                memoryOpen1 = -1
                memoryOpen2 = -1
                memoryLocked = false

                toast(
                    "Match! +15 XP"
                )

                if (memoryPairs >= 8) {

                    score += 50
                    save()

                    toast(
                        "Memory complete! +50 bonus"
                    )
                }

            } else {

                memoryHideAt =
                    SystemClock.uptimeMillis() + 650L

                toast(
                    "Not a match"
                )
            }
        }
    }

    private fun startMemory() {

        screen = Screen.MEMORY

        memoryCards =
            mutableListOf<Int>().apply {

                for (i in 0 until 8) {
                    add(i)
                    add(i)
                }

                shuffle()
            }

        memoryOpen1 = -1
        memoryOpen2 = -1

        memoryMatched =
            BooleanArray(16)

        memoryLocked = false
        memoryMoves = 0
        memoryPairs = 0
        memoryStarted = true
        memoryHideAt = 0L
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

        if (y < 75f) {

            screen = Screen.GAMES
            return
        }

        if (!numberRunning) {

            if (y in 310f..390f) {
                startNumber()
            }

            return
        }

        if (numberShowing) {
            return
        }

        val startY = 280f
        val gap = 9f
        val size = (w - 64f) / 3f

        for (n in 1..9) {

            val index =
                n - 1

            val row =
                index / 3

            val col =
                index % 3

            val l =
                20f + col * (size + gap)

            val t =
                startY + row * (size * 0.65f + gap)

            val r =
                l + size

            val b =
                t + size * 0.55f

            if (
                x >= l &&
                x <= r &&
                y >= t &&
                y <= b
            ) {

                numberInput += n.toString()
                return
            }
        }

        val zeroTop =
            startY + 3 * (size * 0.65f + gap)

        if (
            x >= 20f &&
            x <= w / 2f - 5f &&
            y >= zeroTop &&
            y <= zeroTop + size * 0.55f
        ) {

            numberInput += "0"
            return
        }

        if (
            x >= w / 2f + 5f &&
            x <= w - 20f &&
            y >= zeroTop &&
            y <= zeroTop + size * 0.55f
        ) {

            checkNumber()
        }
    }

    private fun startNumber() {

        screen = Screen.NUMBER

        numberRunning = true
        numberLevel = 1

        createNumberRound()
    }

    private fun createNumberRound() {

        numberSequence =
            MutableList(
                2 + numberLevel.coerceAtMost(6)
            ) {
                Random.nextInt(0, 10)
            }

        numberInput = ""

        numberShowing = true

        numberShowUntil =
            SystemClock.uptimeMillis() +
                    1200L +
                    numberSequence.size * 350L
    }

    private fun checkNumber() {

        val correct =
            numberInput ==
                    numberSequence.joinToString("")

        if (correct) {

            val gained =
                20 + numberLevel * 5

            score += gained

            numberLevel += 1

            save()

            toast(
                "Correct! +$gained XP"
            )

            createNumberRound()

        } else {

            toast(
                "Not quite. Try the next round."
            )

            numberInput = ""

            createNumberRound()
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

        if (y < 75f) {

            screen = Screen.GAMES
            return
        }

        if (!calmRunning) {

            if (y in 360f..455f) {

                calmRunning = true
                calmStart =
                    SystemClock.uptimeMillis()

                calmSeconds = 0
                calmPhase = "BREATHE IN"
            }

        } else {

            if (y in 360f..455f) {

                calmRunning = false

                toast(
                    "Nice reset. Come back anytime."
                )
            }
        }
    }

    private fun startCalm() {

        screen = Screen.CALM

        calmRunning = false
        calmSeconds = 0
        calmPhase = "READY"
    }

    // =========================================================
    // DAILY TOUCH
    // =========================================================

    private fun dailyTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 75f) {

            screen = Screen.HOME
            return
        }

        if (y in 220f..285f) {

            startFocus()
        }
    }

    // =========================================================
    // PROFILE TOUCH
    // =========================================================

    private fun profileTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 75f) {
            screen = Screen.HOME
        }
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

            Screen.GAMES,
            Screen.DAILY,
            Screen.PROFILE -> {
                screen = Screen.HOME
            }

            Screen.FOCUS,
            Screen.COLOR,
            Screen.MEMORY,
            Screen.NUMBER,
            Screen.CALM -> {
                screen = Screen.GAMES
            }
        }

        focusStarted = false
        calmRunning = false
        numberRunning = false

        invalidate()

        return true
    }
}
