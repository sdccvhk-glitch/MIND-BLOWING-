package com.mindblow.app

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var gameView: MindBlowView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(5, 7, 24)
        window.navigationBarColor = Color.rgb(5, 7, 24)

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
    BUBBLE,
    CANDY,
    COLOR,
    MEMORY,
    NUMBER,
    CALM,
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

    private var splashTime = System.currentTimeMillis()
    private var animation = 0f

    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)

    private var toastText = ""
    private var toastUntil = 0L

    // ---------------------------------------------------------
    // BUBBLE BLAST
    // ---------------------------------------------------------

    private val bubbleRows = 7
    private val bubbleCols = 6
    private val bubbleColors = intArrayOf(
        0xffff4f8b.toInt(),
        0xff4fdcff.toInt(),
        0xffffc857.toInt(),
        0xff9b6cff.toInt(),
        0xff50e3a4.toInt()
    )

    private var bubbles = Array(bubbleRows) {
        IntArray(bubbleCols) { Random.nextInt(bubbleColors.size) }
    }

    private var bubbleShots = 0

    // ---------------------------------------------------------
    // CANDY MATCH
    // ---------------------------------------------------------

    private val candyColors = intArrayOf(
        0xffff5c8a.toInt(),
        0xffffc857.toInt(),
        0xff55d9ff.toInt(),
        0xff9c6cff.toInt(),
        0xff57e39d.toInt(),
        0xffff8c52.toInt()
    )

    private val candyRows = 6
    private val candyCols = 6

    private var candies = Array(candyRows) {
        IntArray(candyCols) {
            Random.nextInt(candyColors.size)
        }
    }

    private var candyStartRow = -1
    private var candyStartCol = -1
    private var candyMoves = 0

    // ---------------------------------------------------------
    // COLOR RUSH
    // ---------------------------------------------------------

    private var colorTarget = Random.nextInt(candyColors.size)
    private var colorOptions = IntArray(6) {
        Random.nextInt(candyColors.size)
    }

    private var colorScore = 0
    private var colorTimeUntil = System.currentTimeMillis() + 30000L

    // ---------------------------------------------------------
    // MEMORY
    // ---------------------------------------------------------

    private val memoryIcons = arrayOf(
        "★", "◆", "●", "✦", "☀", "☾", "✿", "❖"
    )

    private var memoryCards = MutableList(16) { it / 2 }.apply {
        shuffle()
    }

    private var memoryOpen1 = -1
    private var memoryOpen2 = -1
    private var memoryMatched = BooleanArray(16)
    private var memoryBusy = false
    private var memoryScore = 0

    // ---------------------------------------------------------
    // NUMBER FLOW
    // ---------------------------------------------------------

    private var numberSequence = IntArray(12) { it + 1 }
    private var numberNext = 1
    private var numberScore = 0

    // ---------------------------------------------------------
    // CALM FLOW
    // ---------------------------------------------------------

    private var calmScore = 0
    private var calmX = 0f
    private var calmY = 0f
    private var calmStarted = false

    // ---------------------------------------------------------
    // TOUCH
    // ---------------------------------------------------------

    private var downX = 0f
    private var downY = 0f

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

        animation += 0.025f

        drawBackground(canvas, w, h)

        when (screen) {
            Screen.SPLASH -> drawSplash(canvas, w, h)
            Screen.WELCOME -> drawWelcome(canvas, w, h)
            Screen.HOME -> drawHome(canvas, w, h)
            Screen.GAMES -> drawGames(canvas, w, h)
            Screen.BUBBLE -> drawBubbleGame(canvas, w, h)
            Screen.CANDY -> drawCandyGame(canvas, w, h)
            Screen.COLOR -> drawColorGame(canvas, w, h)
            Screen.MEMORY -> drawMemoryGame(canvas, w, h)
            Screen.NUMBER -> drawNumberGame(canvas, w, h)
            Screen.CALM -> drawCalmGame(canvas, w, h)
            Screen.PROFILE -> drawProfile(canvas, w, h)
        }

        if (toastUntil > System.currentTimeMillis()) {
            drawToast(canvas, w, h)
        }

        if (
            screen == Screen.SPLASH &&
            System.currentTimeMillis() - splashTime > 1400L
        ) {
            screen = Screen.WELCOME
        }

        if (screen == Screen.COLOR && colorTimeUntil < System.currentTimeMillis()) {
            colorTimeUntil = System.currentTimeMillis() + 30000L
            colorScore = 0
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
            Color.rgb(4, 7, 27),
            Color.rgb(35, 5, 60),
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val t = animation

        // Cyan glow
        paint.color = Color.argb(34, 30, 220, 255)
        canvas.drawCircle(
            w * 0.15f + sin(t) * 35f,
            h * 0.25f,
            125f,
            paint
        )

        // Purple glow
        paint.color = Color.argb(30, 155, 70, 255)
        canvas.drawCircle(
            w * 0.88f + cos(t * 0.7f) * 30f,
            h * 0.45f,
            145f,
            paint
        )

        // Blue lower glow
        paint.color = Color.argb(23, 50, 180, 255)
        canvas.drawCircle(
            w * 0.45f,
            h * 0.82f,
            160f,
            paint
        )

        // Radar rings
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(22, 100, 210, 255)

        val centerX = w * 0.5f
        val centerY = h * 0.57f

        for (i in 0 until 7) {
            canvas.drawCircle(
                centerX,
                centerY,
                45f + i * 38f + sin(t + i) * 3f,
                paint
            )
        }

        paint.style = Paint.Style.FILL

        // Small stars
        for (i in 0 until 28) {
            val sx = ((i * 83) % max(1, width)).toFloat()
            val sy = ((i * 137) % max(1, height)).toFloat()
            paint.color = Color.argb(
                40 + (i % 3) * 15,
                120,
                190,
                255
            )
            canvas.drawCircle(
                sx,
                sy,
                if (i % 4 == 0) 1.8f else 1f,
                paint
            )
        }
    }

    // =========================================================
    // TEXT
    // =========================================================

    private fun txt(
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
        paint.strokeWidth = 1.3f
        paint.color = Color.argb(
            80,
            80,
            155,
            225
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
            Color.rgb(28, 220, 255),
            Color.rgb(137, 68, 255),
            Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(
            l,
            t,
            r,
            b,
            20f,
            20f,
            paint
        )

        paint.shader = null

        txt(
            canvas,
            label,
            (l + r) / 2f,
            (t + b) / 2f + 6f,
            15f,
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
        txt(
            canvas,
            "‹",
            20f,
            45f,
            40f,
            Color.WHITE,
            false
        )

        txt(
            canvas,
            title,
            58f,
            37f,
            23f,
            Color.WHITE,
            true
        )

        txt(
            canvas,
            subtitle,
            59f,
            58f,
            11f,
            0xff9da9d1.toInt()
        )
    }

    // =========================================================
    // BOTTOM NAV
    // =========================================================

    private fun nav(
        canvas: Canvas,
        w: Float,
        h: Float,
        selected: Int
    ) {
        val top = h - 78f

        card(
            canvas,
            8f,
            top,
            w - 8f,
            h - 8f,
            22f
        )

        val icons = arrayOf(
            "⌂",
            "✦",
            "⚡",
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

            val c =
                if (i == selected) {
                    0xff59eaff.toInt()
                } else {
                    0xff8a94b8.toInt()
                }

            txt(
                canvas,
                icons[i],
                x,
                top + 30f,
                25f,
                c,
                true,
                Paint.Align.CENTER
            )

            txt(
                canvas,
                names[i],
                x,
                top + 54f,
                10f,
                c,
                true,
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
        txt(
            canvas,
            "✦",
            w / 2f,
            h * 0.40f,
            85f,
            0xff58e8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        txt(
            canvas,
            "MINDBLOW",
            w / 2f,
            h * 0.51f,
            38f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        txt(
            canvas,
            "PLAY  •  RESET  •  REFRESH",
            w / 2f,
            h * 0.56f,
            13f,
            0xffaebbdc.toInt(),
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
        txt(
            canvas,
            "MINDBLOW",
            w / 2f,
            75f,
            31f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        txt(
            canvas,
            "Your mini mental playground",
            w / 2f,
            105f,
            14f,
            0xffaab7db.toInt(),
            false,
            Paint.Align.CENTER
        )

        txt(
            canvas,
            "✦",
            w / 2f,
            h * 0.43f,
            100f,
            0xff5de7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        txt(
            canvas,
            "PLAY. THINK. RESET.",
            w / 2f,
            h * 0.57f,
            25f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        txt(
            canvas,
            "Six quick games for your brain.",
            w / 2f,
            h * 0.63f,
            14f,
            0xffaab7d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            canvas,
            "START PLAYING",
            w * 0.12f,
            h * 0.73f,
            w * 0.88f,
            h * 0.82f
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
        txt(
            canvas,
            "MINDBLOW",
            18f,
            38f,
            25f,
            Color.WHITE,
            true
        )

        txt(
            canvas,
            "A quick reset for your brain",
            19f,
            59f,
            11f,
            0xff96a4cb.toInt()
        )

        card(
            canvas,
            w - 115f,
            15f,
            w - 16f,
            59f,
            18f
        )

        txt(
            canvas,
            "$score XP",
            w - 65f,
            42f,
            14f,
            0xffffd45f.toInt(),
            true,
            Paint.Align.CENTER
        )

        // Main hero
        card(
            canvas,
            14f,
            82f,
            w - 14f,
            218f,
            25f
        )

        txt(
            canvas,
            "LEVEL ${score / 500 + 1}",
            29f,
            108f,
            10f,
            0xff59e6ff.toInt(),
            true
        )

        txt(
            canvas,
            "Ready for a quick reset?",
            29f,
            137f,
            22f,
            Color.WHITE,
            true
        )

        txt(
            canvas,
            "Pick a game and refresh your focus.",
            29f,
            161f,
            12f,
            0xff9da9cf.toInt()
        )

        // Progress
        paint.color = 0xff172448.toInt()
        canvas.drawRoundRect(
            29f,
            190f,
            w - 29f,
            196f,
            4f,
            4f,
            paint
        )

        paint.color = 0xff36d9ff.toInt()

        val progress =
            ((score % 500) / 500f).coerceIn(0f, 1f)

        canvas.drawRoundRect(
            29f,
            190f,
            29f + (w - 58f) * progress,
            196f,
            4f,
            4f,
            paint
        )

        txt(
            canvas,
            "QUICK PLAY",
            18f,
            247f,
            14f,
            Color.WHITE,
            true
        )

        // Four large game cards
        homeGameCard(
            canvas,
            "🫧",
            "BUBBLE BLAST",
            "Pop matching bubbles",
            14f,
            260f,
            w / 2f - 7f,
            335f
        )

        homeGameCard(
            canvas,
            "🍬",
            "CANDY MATCH",
            "Swipe & match 3",
            w / 2f + 7f,
            260f,
            w - 14f,
            335f
        )

        homeGameCard(
            canvas,
            "⚡",
            "COLOR RUSH",
            "React quickly",
            14f,
            343f,
            w / 2f - 7f,
            418f
        )

        homeGameCard(
            canvas,
            "🧠",
            "MEMORY FLIP",
            "Find the pairs",
            w / 2f + 7f,
            343f,
            w - 14f,
            418f
        )

        // Bottom row
        homeGameCard(
            canvas,
            "🔢",
            "NUMBER FLOW",
            "Follow the numbers",
            14f,
            426f,
            w / 2f - 7f,
            501f
        )

        homeGameCard(
            canvas,
            "🌊",
            "CALM FLOW",
            "Slow down & breathe",
            w / 2f + 7f,
            426f,
            w - 14f,
            501f
        )

        card(
            canvas,
            14f,
            515f,
            w - 14f,
            566f,
            17f
        )

        txt(
            canvas,
            "🔥",
            30f,
            547f,
            21f
        )

        txt(
            canvas,
            "$streak DAY STREAK",
            61f,
            540f,
            12f,
            0xffffc857.toInt(),
            true
        )

        txt(
            canvas,
            "Keep your mind moving.",
            61f,
            555f,
            10f,
            0xff96a3c9.toInt()
        )

        nav(
            canvas,
            w,
            h,
            0
        )
    }

    private fun homeGameCard(
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
            17f
        )

        txt(
            canvas,
            icon,
            l + 28f,
            t + 31f,
            25f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        txt(
            canvas,
            title,
            l + 50f,
            t + 27f,
            12f,
            Color.WHITE,
            true
        )

        txt(
            canvas,
            subtitle,
            l + 50f,
            t + 46f,
            9f,
            0xff98a5ca.toInt()
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
            "Choose your next brain reset"
        )

        gameMenuCard(
            canvas,
            "🫧",
            "BUBBLE BLAST",
            "Pop matching bubbles",
            14f,
            82f,
            w / 2f - 7f,
            166f
        )

        gameMenuCard(
            canvas,
            "🍬",
            "CANDY MATCH",
            "Swipe to match 3",
            w / 2f + 7f,
            82f,
            w - 14f,
            166f
        )

        gameMenuCard(
            canvas,
            "⚡",
            "COLOR RUSH",
            "Fast visual reaction",
            14f,
            176f,
            w / 2f - 7f,
            260f
        )

        gameMenuCard(
            canvas,
            "🧠",
            "MEMORY FLIP",
            "Train your memory",
            w / 2f + 7f,
            176f,
            w - 14f,
            260f
        )

        gameMenuCard(
            canvas,
            "🔢",
            "NUMBER FLOW",
            "Follow the sequence",
            14f,
            270f,
            w / 2f - 7f,
            354f
        )

        gameMenuCard(
            canvas,
            "🌊",
            "CALM FLOW",
            "Relaxing tap game",
            w / 2f + 7f,
            270f,
            w - 14f,
            354f
        )

        card(
            canvas,
            14f,
            372f,
            w - 14f,
            435f,
            20f
        )

        txt(
            canvas,
            "TIP",
            29f,
            398f,
            10f,
            0xff55e5ff.toInt(),
            true
        )

        txt(
            canvas,
            "Try 2–5 minutes between study sessions.",
            29f,
            421f,
            11f,
            0xffa2add0.toInt()
        )

        nav(
            canvas,
            w,
            h,
            1
        )
    }

    private fun gameMenuCard(
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
            18f
        )

        txt(
            canvas,
            icon,
            l + 30f,
            t + 35f,
            28f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        txt(
            canvas,
            title,
            l + 53f,
            t + 31f,
            12f,
            Color.WHITE,
            true
        )

        txt(
            canvas,
            subtitle,
            l + 53f,
            t + 50f,
            9f,
            0xff9aa7cc.toInt()
        )
    }

    // =========================================================
    // BUBBLE BLAST
    // =========================================================

    private fun drawBubbleGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Bubble Blast",
            "Pop groups of matching bubbles"
        )

        txt(
            canvas,
            "SCORE $bubbleShots",
            w - 18f,
            38f,
            12f,
            0xffffd45f.toInt(),
            true,
            Paint.Align.RIGHT
        )

        val boardW = min(w - 28f, 390f)
        val cell = boardW / bubbleCols.toFloat()
        val left = (w - boardW) / 2f
        val top = 90f

        for (r in 0 until bubbleRows) {
            for (col in 0 until bubbleCols) {

                val cx =
                    left + col * cell + cell / 2f

                val cy =
                    top + r * cell + cell / 2f

                val radius =
                    cell * 0.35f

                drawBubble(
                    canvas,
                    cx,
                    cy,
                    radius,
                    bubbleColors[bubbles[r][col]]
                )
            }
        }

        card(
            canvas,
            22f,
            top + bubbleRows * cell + 20f,
            w - 22f,
            top + bubbleRows * cell + 79f,
            19f
        )

        txt(
            canvas,
            "TAP A BUBBLE GROUP",
            w / 2f,
            top + bubbleRows * cell + 47f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        txt(
            canvas,
            "2+ connected bubbles = POP",
            w / 2f,
            top + bubbleRows * cell + 66f,
            10f,
            0xff9aa7cc.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    private fun drawBubble(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int
    ) {
        paint.color = color
        canvas.drawCircle(cx, cy, radius, paint)

        paint.color = Color.argb(
            130,
            255,
            255,
            255
        )

        canvas.drawCircle(
            cx - radius * 0.30f,
            cy - radius * 0.32f,
            radius * 0.18f,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(
            90,
            255,
            255,
            255
        )

        canvas.drawCircle(
            cx,
            cy,
            radius,
            paint
        )

        paint.style = Paint.Style.FILL
    }

    // =========================================================
    // CANDY MATCH
    // =========================================================

    private fun drawCandyGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Candy Match",
            "Swipe candies to make matches"
        )

        txt(
            canvas,
            "MOVES $candyMoves",
            18f,
            86f,
            12f,
            0xff55e5ff.toInt(),
            true
        )

        txt(
            canvas,
            "XP +${candyMoves * 5}",
            w - 18f,
            86f,
            12f,
            0xffffd45f.toInt(),
            true,
            Paint.Align.RIGHT
        )

        val boardW = min(w - 24f, 390f)
        val cell = boardW / candyCols
        val left = (w - boardW) / 2f
        val top = 103f

        for (r in 0 until candyRows) {
            for (col in 0 until candyCols) {

                val cx =
                    left + col * cell + cell / 2f

                val cy =
                    top + r * cell + cell / 2f

                drawCandy(
                    canvas,
                    cx,
                    cy,
                    cell * 0.36f,
                    candyColors[candies[r][col]]
                )
            }
        }

        txt(
            canvas,
            "SWIPE ANY CANDY",
            w / 2f,
            top + boardW + 31f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        txt(
            canvas,
            "Match 3 or more in a row",
            w / 2f,
            top + boardW + 52f,
            10f,
            0xff9ca8cc.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    private fun drawCandy(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int
    ) {
        paint.color = color
        canvas.drawCircle(cx, cy, radius, paint)

        paint.color = Color.argb(
            150,
            255,
            255,
            255
        )

        canvas.drawCircle(
            cx - radius * 0.3f,
            cy - radius * 0.32f,
            radius * 0.18f,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(
            100,
            255,
            255,
            255
        )

        canvas.drawCircle(
            cx,
            cy,
            radius,
            paint
        )

        paint.style = Paint.Style.FILL
    }

    // =========================================================
    // COLOR RUSH
    // =========================================================

    private fun drawColorGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Color Rush",
            "Tap the color shown above"
        )

        val remaining =
            max(
                0L,
                colorTimeUntil - System.currentTimeMillis()
            )

        txt(
            canvas,
            "${remaining / 1000L}s",
            w - 18f,
            38f,
            14f,
            0xffffd45f.toInt(),
            true,
            Paint.Align.RIGHT
        )

        txt(
            canvas,
            "TAP",
            w / 2f,
            130f,
            13f,
            0xff98a6ca.toInt(),
            true,
            Paint.Align.CENTER
        )

        paint.color = candyColors[colorTarget]

        canvas.drawCircle(
            w / 2f,
            205f,
            62f + sin(animation * 2f) * 4f,
            paint
        )

        txt(
            canvas,
            "COLOR $colorScore",
            w / 2f,
            310f,
            20f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        val gap = 12f
        val cardW = (w - 40f - gap * 2f) / 3f

        for (i in 0 until 6) {
            val row = i / 3
            val col = i % 3

            val l =
                20f + col * (cardW + gap)

            val t =
                345f + row * 90f

            val r = l + cardW
            val b = t + 72f

            paint.color = candyColors[colorOptions[i]]

            canvas.drawRoundRect(
                l,
                t,
                r,
                b,
                18f,
                18f,
                paint
            )

            paint.color = Color.argb(
                80,
                255,
                255,
                255
            )

            canvas.drawCircle(
                l + 22f,
                t + 20f,
                7f,
                paint
            )
        }
    }

    // =========================================================
    // MEMORY
    // =========================================================

    private fun drawMemoryGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Memory Flip",
            "Find every matching pair"
        )

        txt(
            canvas,
            "$memoryScore XP",
            w - 18f,
            38f,
            13f,
            0xffffd45f.toInt(),
            true,
            Paint.Align.RIGHT
        )

        val boardW = min(w - 24f, 390f)
        val cell = boardW / 4f
        val left = (w - boardW) / 2f
        val top = 92f

        for (i in 0 until 16) {
            val row = i / 4
            val col = i % 4

            val l = left + col * cell + 4f
            val t = top + row * cell + 4f
            val r = left + (col + 1) * cell - 4f
            val b = top + (row + 1) * cell - 4f

            val open =
                memoryMatched[i] ||
                        i == memoryOpen1 ||
                        i == memoryOpen2

            if (open) {
                paint.color = 0xff162c4d.toInt()
                canvas.drawRoundRect(
                    l,
                    t,
                    r,
                    b,
                    15f,
                    15f,
                    paint
                )

                txt(
                    canvas,
                    memoryIcons[memoryCards[i]],
                    (l + r) / 2f,
                    (t + b) / 2f + 12f,
                    27f,
                    0xff5de6ff.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            } else {
                card(
                    canvas,
                    l,
                    t,
                    r,
                    b,
                    15f
                )

                txt(
                    canvas,
                    "?",
                    (l + r) / 2f,
                    (t + b) / 2f + 10f,
                    24f,
                    0xff7382ae.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            }
        }

        txt(
            canvas,
            "TAP TWO CARDS",
            w / 2f,
            top + boardW + 31f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // NUMBER FLOW
    // =========================================================

    private fun drawNumberGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Number Flow",
            "Tap numbers from 1 to 12"
        )

        txt(
            canvas,
            "NEXT $numberNext",
            w / 2f,
            88f,
            17f,
            0xff5de7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        val boardW = min(w - 28f, 390f)
        val cell = boardW / 3f
        val left = (w - boardW) / 2f
        val top = 120f

        for (i in 0 until 12) {
            val row = i / 3
            val col = i % 3

            val cx =
                left + col * cell + cell / 2f

            val cy =
                top + row * cell + cell / 2f

            paint.color =
                if (numberSequence[i] < numberNext) {
                    0xff162848.toInt()
                } else {
                    0xff172044.toInt()
                }

            canvas.drawCircle(
                cx,
                cy,
                cell * 0.32f,
                paint
            )

            txt(
                canvas,
                numberSequence[i].toString(),
                cx,
                cy + 9f,
                20f,
                if (numberSequence[i] < numberNext) {
                    0xff4f6b89.toInt()
                } else {
                    Color.WHITE
                },
                true,
                Paint.Align.CENTER
            )
        }

        txt(
            canvas,
            "Score $numberScore",
            w / 2f,
            top + boardW + 38f,
            14f,
            0xffffd45f.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // CALM FLOW
    // =========================================================

    private fun drawCalmGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Calm Flow",
            "Follow the glow and breathe"
        )

        txt(
            canvas,
            "$calmScore",
            w / 2f,
            105f,
            22f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        if (!calmStarted) {
            txt(
                canvas,
                "TAP START",
                w / 2f,
                h * 0.40f,
                22f,
                0xff5de7ff.toInt(),
                true,
                Paint.Align.CENTER
            )

            txt(
                canvas,
                "Slow taps • no pressure",
                w / 2f,
                h * 0.45f,
                13f,
                0xffa5b0d0.toInt(),
                false,
                Paint.Align.CENTER
            )
        } else {
            val pulse =
                55f + sin(animation * 2f) * 9f

            paint.color = Color.argb(
                35,
                60,
                220,
                255
            )

            canvas.drawCircle(
                calmX,
                calmY,
                pulse + 35f,
                paint
            )

            paint.color = 0xff59e7ff.toInt()

            canvas.drawCircle(
                calmX,
                calmY,
                pulse,
                paint
            )

            paint.color = Color.argb(
                170,
                255,
                255,
                255
            )

            canvas.drawCircle(
                calmX - 15f,
                calmY - 18f,
                10f,
                paint
            )
        }
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
            15f,
            85f,
            w - 15f,
            215f,
            24f
        )

        txt(
            canvas,
            "✦",
            65f,
            155f,
            50f,
            0xff5de7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        txt(
            canvas,
            "Mind Explorer",
            108f,
            127f,
            20f,
            Color.WHITE,
            true
        )

        txt(
            canvas,
            "Level ${score / 500 + 1}",
            108f,
            153f,
            13f,
            0xffaab7da.toInt()
        )

        txt(
            canvas,
            "$score XP",
            108f,
            181f,
            14f,
            0xffffd45f.toInt(),
            true
        )

        card(
            canvas,
            15f,
            235f,
            w - 15f,
            350f,
            21f
        )

        txt(
            canvas,
            "TOTAL XP",
            34f,
            267f,
            11f,
            0xff8e9cc3.toInt(),
            true
        )

        txt(
            canvas,
            "$score",
            34f,
            300f,
            25f,
            Color.WHITE,
            true
        )

        txt(
            canvas,
            "STREAK",
            180f,
            267f,
            11f,
            0xff8e9cc3.toInt(),
            true
        )

        txt(
            canvas,
            "$streak days",
            180f,
            300f,
            20f,
            0xffffc857.toInt(),
            true
        )

        txt(
            canvas,
            "Six games available",
            34f,
            330f,
            11f,
            0xffa1acd0.toInt()
        )

        nav(
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
        card(
            canvas,
            24f,
            h - 135f,
            w - 24f,
            h - 82f,
            19f
        )

        txt(
            canvas,
            toastText,
            w / 2f,
            h - 103f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    private fun toast(value: String) {
        toastText = value
        toastUntil =
            System.currentTimeMillis() + 1300L
    }

    private fun saveScore() {
        prefs.edit()
            .putInt("score", score)
            .putInt("streak", streak)
            .apply()
    }

    // =========================================================
    // TOUCH
    // =========================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {

        val x = event.x
        val y = event.y

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                return true
            }

            MotionEvent.ACTION_UP -> {

                val dx = x - downX
                val dy = y - downY

                handleTouch(
                    x,
                    y,
                    dx,
                    dy
                )

                invalidate()
                return true
            }
        }

        return true
    }

    private fun handleTouch(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float
    ) {
        val w = width.toFloat()
        val h = height.toFloat()

        when (screen) {

            Screen.SPLASH -> {
                screen = Screen.WELCOME
            }

            Screen.WELCOME -> {
                if (y > h * 0.68f) {
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

            Screen.BUBBLE -> {
                bubbleTouch(x, y, w)
            }

            Screen.CANDY -> {
                candyTouch(x, y, dx, dy, w)
            }

            Screen.COLOR -> {
                colorTouch(x, y, w)
            }

            Screen.MEMORY -> {
                memoryTouch(x, y, w)
            }

            Screen.NUMBER -> {
                numberTouch(x, y, w)
            }

            Screen.CALM -> {
                calmTouch(x, y, w, h)
            }

            Screen.PROFILE -> {
                if (y < 75f) {
                    screen = Screen.HOME
                } else {
                    bottomNavigation(x, y, w, h)
                }
            }
        }
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
        if (bottomNavigation(x, y, w, h)) return

        when {
            y in 250f..340f -> {
                if (x < w / 2f) {
                    startBubble()
                } else {
                    startCandy()
                }
            }

            y in 340f..425f -> {
                if (x < w / 2f) {
                    startColor()
                } else {
                    startMemory()
                }
            }

            y in 425f..510f -> {
                if (x < w / 2f) {
                    startNumber()
                } else {
                    startCalm()
                }
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
        if (y < 70f) {
            screen = Screen.HOME
            return
        }

        if (bottomNavigation(x, y, w, h)) return

        when {
            y in 75f..170f -> {
                if (x < w / 2f) {
                    startBubble()
                } else {
                    startCandy()
                }
            }

            y in 170f..265f -> {
                if (x < w / 2f) {
                    startColor()
                } else {
                    startMemory()
                }
            }

            y in 265f..365f -> {
                if (x < w / 2f) {
                    startNumber()
                } else {
                    startCalm()
                }
            }
        }
    }

    // =========================================================
    // BOTTOM NAV TOUCH
    // =========================================================

    private fun bottomNavigation(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ): Boolean {

        if (y < h - 82f) {
            return false
        }

        val index =
            (x / (w / 5f)).toInt().coerceIn(0, 4)

        when (index) {
            0 -> screen = Screen.HOME
            1 -> screen = Screen.GAMES
            2 -> {
                startColor()
            }
            3 -> {
                startNumber()
            }
            4 -> screen = Screen.PROFILE
        }

        return true
    }

    // =========================================================
    // START GAMES
    // =========================================================

    private fun startBubble() {
        bubbles = Array(bubbleRows) {
            IntArray(bubbleCols) {
                Random.nextInt(bubbleColors.size)
            }
        }

        bubbleShots = 0
        screen = Screen.BUBBLE
    }

    private fun startCandy() {
        candies = Array(candyRows) {
            IntArray(candyCols) {
                Random.nextInt(candyColors.size)
            }
        }

        candyMoves = 0
        candyStartRow = -1
        candyStartCol = -1
        screen = Screen.CANDY
    }

    private fun startColor() {
        colorScore = 0
        colorTarget =
            Random.nextInt(candyColors.size)

        for (i in colorOptions.indices) {
            colorOptions[i] =
                Random.nextInt(candyColors.size)
        }

        colorTimeUntil =
            System.currentTimeMillis() + 30000L

        screen = Screen.COLOR
    }

    private fun startMemory() {
        memoryCards =
            MutableList(16) { it / 2 }.apply {
                shuffle()
            }

        memoryOpen1 = -1
        memoryOpen2 = -1
        memoryMatched = BooleanArray(16)
        memoryBusy = false
        memoryScore = 0

        screen = Screen.MEMORY
    }

    private fun startNumber() {
        numberSequence =
            IntArray(12) { it + 1 }

        numberSequence.shuffle()

        numberNext = 1
        numberScore = 0

        screen = Screen.NUMBER
    }

    private fun startCalm() {
        calmScore = 0
        calmStarted = false
        calmX = width / 2f
        calmY = height * 0.48f
        screen = Screen.CALM
    }

    // =========================================================
    // BUBBLE TOUCH
    // =========================================================

    private fun bubbleTouch(
        x: Float,
        y: Float,
        w: Float
    ) {
        if (y < 70f) {
            screen = Screen.GAMES
            return
        }

        if (bottomNavigation(
                x,
                y,
                w,
                height.toFloat()
            )
        ) {
            return
        }

        val boardW = min(w - 28f, 390f)
        val cell = boardW / bubbleCols
        val left = (w - boardW) / 2f
        val top = 90f

        if (
            x < left ||
            x > left + boardW ||
            y < top ||
            y > top + bubbleRows * cell
        ) {
            return
        }

        val col =
            ((x - left) / cell)
                .toInt()
                .coerceIn(0, bubbleCols - 1)

        val row =
            ((y - top) / cell)
                .toInt()
                .coerceIn(0, bubbleRows - 1)

        val group =
            bubbleGroup(row, col)

        if (group.size >= 2) {

            for (index in group) {
                val r = index / bubbleCols
                val c = index % bubbleCols

                bubbles[r][c] =
                    Random.nextInt(bubbleColors.size)
            }

            bubbleShots += group.size
            score += group.size * 4
            saveScore()

            toast(
                "+${group.size * 4} XP • ${group.size} bubbles!"
            )
        } else {
            toast("Try a group of 2 or more")
        }
    }

    private fun bubbleGroup(
        startRow: Int,
        startCol: Int
    ): MutableList<Int> {

        val result = mutableListOf<Int>()

        val target =
            bubbles[startRow][startCol]

        val queue =
            ArrayDeque<Pair<Int, Int>>()

        val visited =
            BooleanArray(bubbleRows * bubbleCols)

        queue.add(
            Pair(
                startRow,
                startCol
            )
        )

        while (queue.isNotEmpty()) {

            val current = queue.removeFirst()

            val r = current.first
            val c = current.second

            if (
                r !in 0 until bubbleRows ||
                c !in 0 until bubbleCols
            ) {
                continue
            }

            val index =
                r * bubbleCols + c

            if (visited[index]) {
                continue
            }

            visited[index] = true

            if (bubbles[r][c] != target) {
                continue
            }

            result.add(index)

            queue.add(Pair(r - 1, c))
            queue.add(Pair(r + 1, c))
            queue.add(Pair(r, c - 1))
            queue.add(Pair(r, c + 1))
        }

        return result
    }

    // =========================================================
    // CANDY TOUCH
    // =========================================================

    private fun candyTouch(
        x: Float,
        y: Float,
        dx: Float,
        dy: Float,
        w: Float
    ) {
        if (y < 70f) {
            screen = Screen.GAMES
            return
        }

        if (bottomNavigation(
                x,
                y,
                w,
                height.toFloat()
            )
        ) {
            return
        }

        val boardW = min(w - 24f, 390f)
        val cell = boardW / candyCols
        val left = (w - boardW) / 2f
        val top = 103f

        if (
            downX < left ||
            downX > left + boardW ||
            downY < top ||
            downY > top + boardW
        ) {
            return
        }

        val startCol =
            ((downX - left) / cell)
                .toInt()
                .coerceIn(0, candyCols - 1)

        val startRow =
            ((downY - top) / cell)
                .toInt()
                .coerceIn(0, candyRows - 1)

        var targetRow = startRow
        var targetCol = startCol

        if (abs(dx) > abs(dy)) {
            if (dx > 25f) {
                targetCol++
            } else if (dx < -25f) {
                targetCol--
            }
        } else {
            if (dy > 25f) {
                targetRow++
            } else if (dy < -25f) {
                targetRow--
            }
        }

        if (
            targetRow !in 0 until candyRows ||
            targetCol !in 0 until candyCols
        ) {
            return
        }

        val temp =
            candies[startRow][startCol]

        candies[startRow][startCol] =
            candies[targetRow][targetCol]

        candies[targetRow][targetCol] =
            temp

        candyMoves++

        val matches = findCandyMatches()

        if (matches.isEmpty()) {

            val reverse =
                candies[startRow][startCol]

            candies[startRow][startCol] =
                candies[targetRow][targetCol]

            candies[targetRow][targetCol] =
                reverse

            candyMoves--
            toast("Make a line of 3!")
            return
        }

        val gained =
            matches.size * 8

        score += gained
        saveScore()

        for (p in matches) {
            candies[p.first][p.second] =
                Random.nextInt(candyColors.size)
        }

        toast(
            "Sweet! +$gained XP"
        )
    }

    private fun findCandyMatches(): MutableSet<Pair<Int, Int>> {

        val result =
            mutableSetOf<Pair<Int, Int>>()

        for (r in 0 until candyRows) {

            var c = 0

            while (c < candyCols) {

                val color =
                    candies[r][c]

                var end = c + 1

                while (
                    end < candyCols &&
                    candies[r][end] == color
                ) {
                    end++
                }

                if (end - c >= 3) {
                    for (x in c until end) {
                        result.add(
                            Pair(r, x)
                        )
                    }
                }

                c = end
            }
        }

        for (c in 0 until candyCols) {

            var r = 0

            while (r < candyRows) {

                val color =
                    candies[r][c]

                var end = r + 1

                while (
                    end < candyRows &&
                    candies[end][c] == color
                ) {
                    end++
                }

                if (end - r >= 3) {
                    for (x in r until end) {
                        result.add(
                            Pair(x, c)
                        )
                    }
                }

                r = end
            }
        }

        return result
    }

    // =========================================================
    // COLOR TOUCH
    // =========================================================

    private fun colorTouch(
        x: Float,
        y: Float,
        w: Float
    ) {
        if (y < 70f) {
            screen = Screen.GAMES
            return
        }

        if (bottomNavigation(
                x,
                y,
                w,
                height.toFloat()
            )
        ) {
            return
        }

        if (y !in 340f..540f) {
            return
        }

        val gap = 12f
        val cardW =
            (w - 40f - gap * 2f) / 3f

        val col =
            ((x - 20f) / (cardW + gap))
                .toInt()
                .coerceIn(0, 2)

        val row =
            ((y - 345f) / 90f)
                .toInt()
                .coerceIn(0, 1)

        val index =
            row * 3 + col

        if (
            index !in colorOptions.indices
        ) {
            return
        }

        if (
            colorOptions[index] ==
            colorTarget
        ) {

            colorScore++

            score += 5
            saveScore()

            colorTarget =
                Random.nextInt(candyColors.size)

            for (i in colorOptions.indices) {
                colorOptions[i] =
                    Random.nextInt(candyColors.size)
            }

            toast(
                "Fast! +5 XP"
            )
        } else {
            toast("Wrong color!")
        }
    }

    // =========================================================
    // MEMORY TOUCH
    // =========================================================

    private fun memoryTouch(
        x: Float,
        y: Float,
        w: Float
    ) {
        if (y < 70f) {
            screen = Screen.GAMES
            return
        }

        if (memoryBusy) {
            return
        }

        if (bottomNavigation(
                x,
                y,
                w,
                height.toFloat()
            )
        ) {
            return
        }

        val boardW =
            min(w - 24f, 390f)

        val cell =
            boardW / 4f

        val left =
            (w - boardW) / 2f

        val top = 92f

        if (
            x < left ||
            x > left + boardW ||
            y < top ||
            y > top + boardW
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

            if (
                memoryCards[memoryOpen1] ==
                memoryCards[memoryOpen2]
            ) {

                memoryMatched[memoryOpen1] =
                    true

                memoryMatched[memoryOpen2] =
                    true

                memoryScore += 20
                score += 20
                saveScore()

                toast("Perfect match! +20 XP")

                memoryOpen1 = -1
                memoryOpen2 = -1

            } else {

                memoryBusy = true

                postDelayed({

                    memoryOpen1 = -1
                    memoryOpen2 = -1
                    memoryBusy = false

                    invalidate()

                }, 550L)

                toast("Remember their positions!")
            }
        }
    }

    // =========================================================
    // NUMBER TOUCH
    // =========================================================

    private fun numberTouch(
        x: Float,
        y: Float,
        w: Float
    ) {
        if (y < 70f) {
            screen = Screen.GAMES
            return
        }

        if (bottomNavigation(
                x,
                y,
                w,
                height.toFloat()
            )
        ) {
            return
        }

        val boardW =
            min(w - 28f, 390f)

        val cell =
            boardW / 3f

        val left =
            (w - boardW) / 2f

        val top = 120f

        if (
            x < left ||
            x > left + boardW ||
            y < top ||
            y > top + boardW
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
                .coerceIn(0, 3)

        val index =
            row * 3 + col

        if (index >= numberSequence.size) {
            return
        }

        val value =
            numberSequence[index]

        if (value == numberNext) {

            numberNext++
            numberScore++
            score += 3
            saveScore()

            if (numberNext > 12) {

                toast(
                    "Sequence complete! +36 XP"
                )

                numberSequence =
                    IntArray(12) { it + 1 }

                numberSequence.shuffle()
                numberNext = 1

            } else {

                toast(
                    "Nice! Next $numberNext"
                )
            }

        } else {
            toast(
                "Find $numberNext"
            )
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
        if (y < 70f) {
            screen = Screen.GAMES
            return
        }

        if (bottomNavigation(
                x,
                y,
                w,
                h
            )
        ) {
            return
        }

        if (!calmStarted) {

            calmStarted = true

            calmX =
                w * 0.25f +
                        Random.nextFloat() * w * 0.5f

            calmY =
                h * 0.35f +
                        Random.nextFloat() * h * 0.3f

            toast("Breathe... follow the glow")

            return
        }

        val distance =
            kotlin.math.sqrt(
                (x - calmX) * (x - calmX) +
                        (y - calmY) * (y - calmY)
            )

        if (distance < 90f) {

            calmScore++

            score += 4
            saveScore()

            calmX =
                w * 0.18f +
                        Random.nextFloat() * w * 0.64f

            calmY =
                h * 0.25f +
                        Random.nextFloat() * h * 0.48f

            toast(
                "Calm +4 XP"
            )
        }
    }

    // =========================================================
    // BACK
    // =========================================================

    fun goBack(): Boolean {

        return when (screen) {

            Screen.HOME,
            Screen.SPLASH,
            Screen.WELCOME -> false

            Screen.GAMES -> {
                screen = Screen.HOME
                invalidate()
                true
            }

            Screen.BUBBLE,
            Screen.CANDY,
            Screen.COLOR,
            Screen.MEMORY,
            Screen.NUMBER,
            Screen.CALM -> {
                screen = Screen.GAMES
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
