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

        window.statusBarColor = Color.rgb(5, 8, 25)
        window.navigationBarColor = Color.rgb(5, 8, 25)

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

    private var splashTime = System.currentTimeMillis()
    private var animation = 0f

    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)

    // ---------------- PUZZLE ----------------

    private var puzzleTarget = Random.nextInt(16)
    private var puzzleSolved = 0

    // ---------------- MEMORY ----------------

    private val memorySymbols = arrayOf(
        "★", "◆", "●",
        "✦", "☀", "☾",
        "✿", "❖", "♣"
    )

    private var memoryBoard =
        MutableList(9) { it }.apply { shuffle() }

    private var memoryFirst = -1
    private var memorySecond = -1
    private var memoryPreviewUntil = 0L
    private var memoryMatches = 0

    // ---------------- FOCUS ----------------

    private var focusTargetX = 0f
    private var focusTargetY = 0f
    private var focusHits = 0
    private var focusStarted = false

    // ---------------- RELAX ----------------

    private var relaxTaps = 0
    private var relaxPulse = 0f

    // ---------------- MESSAGE ----------------

    private var message = ""
    private var messageUntil = 0L

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

        drawBackground(canvas, w, h)

        when (screen) {

            Screen.SPLASH ->
                drawSplash(canvas, w, h)

            Screen.WELCOME ->
                drawWelcome(canvas, w, h)

            Screen.HOME ->
                drawHome(canvas, w, h)

            Screen.GAMES ->
                drawGames(canvas, w, h)

            Screen.PUZZLE ->
                drawPuzzle(canvas, w, h)

            Screen.MEMORY ->
                drawMemory(canvas, w, h)

            Screen.FOCUS ->
                drawFocus(canvas, w, h)

            Screen.RELAX ->
                drawRelax(canvas, w, h)

            Screen.DAILY ->
                drawDaily(canvas, w, h)

            Screen.PROFILE ->
                drawProfile(canvas, w, h)
        }

        if (messageUntil > System.currentTimeMillis()) {
            drawToast(canvas, w, h)
        }

        if (
            screen == Screen.SPLASH &&
            System.currentTimeMillis() - splashTime > 1600L
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

        paint.style = Paint.Style.FILL

        paint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            Color.rgb(4, 7, 25),
            Color.rgb(42, 7, 64),
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(0f, 0f, w, h, paint)

        paint.shader = null

        val t = animation

        paint.color = Color.argb(
            45,
            20,
            220,
            255
        )

        canvas.drawCircle(
            w * 0.15f + sin(t) * 35f,
            h * 0.18f + cos(t) * 30f,
            145f,
            paint
        )

        paint.color = Color.argb(
            38,
            160,
            70,
            255
        )

        canvas.drawCircle(
            w * 0.85f + cos(t * 0.8f) * 45f,
            h * 0.38f + sin(t) * 40f,
            165f,
            paint
        )

        paint.color = Color.argb(
            28,
            20,
            255,
            180
        )

        canvas.drawCircle(
            w * 0.50f + sin(t * 0.6f) * 50f,
            h * 0.82f,
            180f,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(
            25,
            100,
            220,
            255
        )

        for (i in 0 until 6) {

            canvas.drawCircle(
                w / 2f,
                h * 0.45f,
                70f + i * 52f + sin(t + i) * 5f,
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
        paint.style = Paint.Style.FILL
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
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        radius: Float = 20f
    ) {

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(
            230,
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
            75,
            100,
            190,
            240
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

    private fun button(
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

        paint.style = Paint.Style.FILL

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

    private fun header(
        canvas: Canvas,
        title: String,
        subtitle: String
    ) {

        text(
            canvas,
            "<",
            22f,
            45f,
            30f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            title,
            58f,
            41f,
            22f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            58f,
            62f,
            11f,
            0xff9da8cf.toInt()
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

        val top = h - 78f

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
            "H",
            "G",
            "F",
            "D",
            "P"
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
                17f,
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

        text(
            canvas,
            "✦",
            w / 2f,
            h * .39f,
            80f,
            0xff62e8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "MindBlow",
            w / 2f,
            h * .49f,
            40f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "RELAX  •  FOCUS  •  REFRESH",
            w / 2f,
            h * .545f,
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
            h * .47f,
            95f,
            0xff63e8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Play • Relax • Repeat",
            w / 2f,
            h * .61f,
            24f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Quick challenges designed to refresh",
            w / 2f,
            h * .66f,
            14f,
            0xffaeb8d9.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "your attention without pressure.",
            w / 2f,
            h * .695f,
            14f,
            0xffaeb8d9.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            canvas,
            "GET STARTED",
            w * .14f,
            h * .77f,
            w * .86f,
            h * .85f
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

        card(
            canvas,
            w - 115f,
            18f,
            w - 18f,
            57f,
            16f
        )

        text(
            canvas,
            "★ $score",
            w - 66f,
            43f,
            13f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )

        card(
            canvas,
            18f,
            82f,
            w - 18f,
            190f,
            23f
        )

        text(
            canvas,
            "Good to see you!",
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

        button(
            canvas,
            "DAILY CHALLENGE",
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

        gameCard(
            canvas,
            "✦",
            "Puzzle",
            "Think & solve",
            18f,
            262f,
            w / 2f - 8f,
            352f
        )

        gameCard(
            canvas,
            "◉",
            "Memory",
            "Remember & grow",
            w / 2f + 8f,
            262f,
            w - 18f,
            352f
        )

        gameCard(
            canvas,
            "◎",
            "Focus",
            "Stay sharp",
            18f,
            362f,
            w / 2f - 8f,
            452f
        )

        gameCard(
            canvas,
            "◈",
            "Relax",
            "Just breathe",
            w / 2f + 8f,
            362f,
            w - 18f,
            452f
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
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {

        card(
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

        header(
            canvas,
            "Mind Games",
            "Choose a refreshing challenge"
        )

        gameCard(
            canvas,
            "✦",
            "Puzzle",
            "Find the glowing tile",
            18f,
            90f,
            w / 2f - 8f,
            195f
        )

        gameCard(
            canvas,
            "◉",
            "Memory",
            "Remember symbols",
            w / 2f + 8f,
            90f,
            w - 18f,
            195f
        )

        gameCard(
            canvas,
            "◎",
            "Focus",
            "Tap the moving glow",
            18f,
            208f,
            w / 2f - 8f,
            313f
        )

        gameCard(
            canvas,
            "◈",
            "Relax",
            "Calming breathing",
            w / 2f + 8f,
            208f,
            w - 18f,
            313f
        )

        card(
            canvas,
            18f,
            330f,
            w - 18f,
            410f,
            20f
        )

        text(
            canvas,
            "Daily Challenge",
            34f,
            360f,
            18f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Complete today's quick challenge",
            34f,
            382f,
            12f,
            0xffa8b2d2.toInt()
        )

        button(
            canvas,
            "PLAY TODAY",
            34f,
            390f,
            w - 34f,
            410f
        )

        navigation(
            canvas,
            w,
            h,
            1
        )
    }

    // =========================================================
    // PUZZLE GAME
    // =========================================================

    private fun drawPuzzle(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Puzzle",
            "Find the glowing tile"
        )

        text(
            canvas,
            "Solved: $puzzleSolved",
            w - 20f,
            42f,
            11f,
            0xff61ddff.toInt(),
            true,
            Paint.Align.RIGHT
        )

        val size = min(
            w * .82f,
            330f
        )

        val left = (w - size) / 2f
        val top = 100f
        val cell = size / 4f

        for (i in 0 until 16) {

            val row = i / 4
            val col = i % 4

            val l = left + col * cell + 4f
            val t = top + row * cell + 4f
            val r = left + (col + 1) * cell - 4f
            val b = top + (row + 1) * cell - 4f

            card(
                canvas,
                l,
                t,
                r,
                b,
                12f
            )

            if (i == puzzleTarget) {

                paint.color = 0xff48e7ff.toInt()

                canvas.drawCircle(
                    (l + r) / 2f,
                    (t + b) / 2f,
                    14f + sin(animation * 4f) * 4f,
                    paint
                )
            }
        }

        text(
            canvas,
            "Tap the glowing circle",
            w / 2f,
            top + size + 32f,
            15f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Every correct tap gives +25 XP",
            w / 2f,
            top + size + 55f,
            11f,
            0xff9da8cc.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // MEMORY GAME
    // =========================================================

    private fun drawMemory(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Memory",
            "Remember the symbols"
        )

        text(
            canvas,
            "Matches: $memoryMatches",
            w - 20f,
            42f,
            11f,
            0xff61ddff.toInt(),
            true,
            Paint.Align.RIGHT
        )

        val size = min(
            w * .82f,
            330f
        )

        val left = (w - size) / 2f
        val top = 95f
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

            card(
                canvas,
                l,
                t,
                r,
                b,
                15f
            )

            val revealed =
                preview ||
                i == memoryFirst ||
                i == memorySecond

            if (revealed) {

                text(
                    canvas,
                    memorySymbols[memoryBoard[i]],
                    (l + r) / 2f,
                    (t + b) / 2f + 10f,
                    29f,
                    0xff62e8ff.toInt(),
                    true,
                    Paint.Align.CENTER
                )

            } else {

                text(
                    canvas,
                    "?",
                    (l + r) / 2f,
                    (t + b) / 2f + 10f,
                    27f,
                    0xff7883a8.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            }
        }

        text(
            canvas,
            if (preview)
                "Memorize the board..."
            else
                "Tap two cards",
            w / 2f,
            top + size + 32f,
            15f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Matching cards give +20 XP",
            w / 2f,
            top + size + 54f,
            11f,
            0xff9da8cc.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // FOCUS GAME
    // =========================================================

    private fun drawFocus(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Focus",
            "Tap the moving glow"
        )

        text(
            canvas,
            "Hits: $focusHits",
            20f,
            92f,
            16f,
            Color.WHITE,
            true
        )

        if (!focusStarted) {

            button(
                canvas,
                "START FOCUS",
                w * .20f,
                230f,
                w * .80f,
                285f
            )

            text(
                canvas,
                "Tap the moving circle as it appears",
                w / 2f,
                325f,
                13f,
                0xffaeb8d8.toInt(),
                false,
                Paint.Align.CENTER
            )

            return
        }

        val targetX =
            w / 2f +
                cos(animation.toDouble()).toFloat() *
                w * .30f

        val targetY =
            245f +
                sin((animation * 1.35f).toDouble()).toFloat() *
                110f

        focusTargetX = targetX
        focusTargetY = targetY

        for (i in 1..4) {

            paint.color = Color.argb(
                22,
                70,
                220,
                255
            )

            canvas.drawCircle(
                targetX,
                targetY,
                20f + i * 16f,
                paint
            )
        }

        paint.color = 0xff55e6ff.toInt()

        canvas.drawCircle(
            targetX,
            targetY,
            21f,
            paint
        )

        text(
            canvas,
            "TAP!",
            targetX,
            targetY + 5f,
            10f,
            Color.BLACK,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Follow the glow",
            w / 2f,
            430f,
            15f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // RELAX
    // =========================================================

    private fun drawRelax(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Relax",
            "Take a slow breathing break"
        )

        val cx = w / 2f
        val cy = 255f

        val pulse =
            65f + sin(animation * 1.4f) * 25f

        for (i in 0..5) {

            paint.color = Color.argb(
                32 - i * 4,
                50,
                210,
                255
            )

            canvas.drawCircle(
                cx,
                cy,
                pulse + i * 35f,
                paint
            )
        }

        paint.color = 0xff63e8ff.toInt()

        canvas.drawCircle(
            cx,
            cy,
            pulse,
            paint
        )

        text(
            canvas,
            if (sin(animation * 1.4f) > 0)
                "BREATHE IN"
            else
                "BREATHE OUT",
            cx,
            cy + 6f,
            13f,
            Color.BLACK,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Slow breathing",
            cx,
            390f,
            27f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Tap anywhere to count a calm breath",
            cx,
            420f,
            13f,
            0xffaeb8d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Breaths: $relaxTaps",
            cx,
            455f,
            16f,
            0xff61ddff.toInt(),
            true,
            Paint.Align.CENTER
        )
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
            "Daily Challenge",
            "One small win today"
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
            "TODAY",
            38f,
            125f,
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
            "Complete a quick refreshing game",
            38f,
            194f,
            13f,
            0xffaeb8d7.toInt()
        )

        text(
            canvas,
            "and earn bonus XP.",
            38f,
            216f,
            13f,
            0xffaeb8d7.toInt()
        )

        button(
            canvas,
            "PLAY TODAY",
            38f,
            230f,
            w - 38f,
            270f
        )

        text(
            canvas,
            "Streak: $streak days",
            22f,
            325f,
            19f,
            0xffffb52e.toInt(),
            true
        )

        button(
            canvas,
            "CLAIM +50 XP",
            22f,
            350f,
            w - 22f,
            402f
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

        text(
            canvas,
            "✦",
            65f,
            155f,
            50f,
            0xff63e8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "MindExplorer",
            110f,
            130f,
            21f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Level ${score / 500 + 1}",
            110f,
            157f,
            13f,
            0xffaeb8d8.toInt()
        )

        text(
            canvas,
            "$score XP",
            110f,
            188f,
            14f,
            0xff63e8ff.toInt(),
            true
        )

        text(
            canvas,
            "Games completed: ${score / 25}",
            25f,
            265f,
            16f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Current streak: $streak",
            25f,
            295f,
            16f,
            0xffffb52e.toInt(),
            true
        )

        card(
            canvas,
            18f,
            325f,
            w - 18f,
            450f,
            22f
        )

        text(
            canvas,
            "Settings",
            40f,
            365f,
            17f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Sound & Music",
            40f,
            405f,
            15f,
            0xffb9c3df.toInt()
        )

        text(
            canvas,
            "Dark Theme",
            40f,
            440f,
            15f,
            0xffb9c3df.toInt()
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

        card(
            canvas,
            28f,
            h - 135f,
            w - 28f,
            h - 85f,
            18f
        )

        text(
            canvas,
            message,
            w / 2f,
            h - 104f,
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

    private fun save() {

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

        val w = width.toFloat()
        val h = height.toFloat()

        when (screen) {

            Screen.SPLASH -> {
                // Wait for splash
            }

            Screen.WELCOME -> {

                if (y > h * .70f) {

                    prefs.edit()
                        .putBoolean("welcome_seen", true)
                        .apply()

                    screen = Screen.HOME
                }
            }

            Screen.HOME -> {

                homeTouch(
                    x,
                    y,
                    w,
                    h
                )
            }

            Screen.GAMES -> {

                gamesTouch(
                    x,
                    y,
                    w,
                    h
                )
            }

            Screen.PUZZLE -> {

                puzzleTouch(
                    x,
                    y,
                    w,
                    h
                )
            }

            Screen.MEMORY -> {

                memoryTouch(
                    x,
                    y,
                    w,
                    h
                )
            }

            Screen.FOCUS -> {

                focusTouch(
                    x,
                    y,
                    w,
                    h
                )
            }

            Screen.RELAX -> {

                relaxTouch(
                    x,
                    y,
                    w,
                    h
                )
            }

            Screen.DAILY -> {

                dailyTouch(
                    x,
                    y,
                    w,
                    h
                )
            }

            Screen.PROFILE -> {

                profileTouch(
                    x,
                    y,
                    w,
                    h
                )
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

            when {

                x < w * .20f -> {
                    screen = Screen.HOME
                }

                x < w * .40f -> {
                    screen = Screen.GAMES
                }

                x < w * .60f -> {
                    screen = Screen.FOCUS
                    focusStarted = false
                }

                x < w * .80f -> {
                    screen = Screen.DAILY
                }

                else -> {
                    screen = Screen.PROFILE
                }
            }

            return
        }

        if (y >= 170f && y <= 225f) {

            screen = Screen.DAILY
            return
        }

        if (y >= 255f && y <= 355f) {

            if (x < w / 2f) {

                puzzleTarget = Random.nextInt(16)
                screen = Screen.PUZZLE

            } else {

                startMemory()
            }

            return
        }

        if (y >= 355f && y <= 465f) {

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

        if (y < 75f) {

            screen = Screen.HOME
            return
        }

        if (y > h - 90f) {

            when {

                x < w * .20f ->
                    screen = Screen.HOME

                x < w * .40f ->
                    screen = Screen.GAMES

                x < w * .60f -> {
                    focusStarted = false
                    screen = Screen.FOCUS
                }

                x < w * .80f ->
                    screen = Screen.DAILY

                else ->
                    screen = Screen.PROFILE
            }

            return
        }

        if (y in 85f..200f) {

            if (x < w / 2f) {

                puzzleTarget = Random.nextInt(16)
                screen = Screen.PUZZLE

            } else {

                startMemory()
            }

            return
        }

        if (y in 200f..325f) {

            if (x < w / 2f) {

                focusStarted = false
                screen = Screen.FOCUS

            } else {

                screen = Screen.RELAX
            }

            return
        }

        if (y in 325f..430f) {

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

        if (y < 75f) {

            screen = Screen.GAMES
            return
        }

        val size = min(
            w * .82f,
            330f
        )

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

        if (index == puzzleTarget) {

            puzzleSolved++

            score += 25

            save()

            showMessage(
                "Correct! +25 XP"
            )

            puzzleTarget =
                Random.nextInt(16)

        } else {

            showMessage(
                "Try the glowing tile"
            )
        }
    }

    // =========================================================
    // MEMORY START
    // =========================================================

    private fun startMemory() {

        memoryBoard =
            MutableList(9) { it }
                .apply { shuffle() }

        memoryFirst = -1
        memorySecond = -1
        memoryMatches = 0

        memoryPreviewUntil =
            System.currentTimeMillis() + 2200L

        screen = Screen.MEMORY
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

        if (
            System.currentTimeMillis() <
            memoryPreviewUntil
        ) {
            return
        }

        val size = min(
            w * .82f,
            330f
        )

        val left = (w - size) / 2f
        val top = 95f
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
            return
        }

        if (memorySecond != -1) {
            return
        }

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

            postDelayed({

                memoryFirst = -1
                memorySecond = -1

                if (memoryMatches >= 4) {

                    showMessage(
                        "Memory round complete!"
                    )
                }

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

            if (
                y > 210f &&
                y < 310f
            ) {

                focusStarted = true
                focusHits = 0

                showMessage(
                    "Focus started!"
                )
            }

            return
        }

        val dx =
            x - focusTargetX

        val dy =
            y - focusTargetY

        val distance =
            kotlin.math.sqrt(
                dx * dx + dy * dy
            )

        if (distance < 55f) {

            focusHits++

            score += 5

            save()

            showMessage(
                "Great focus! +5 XP"
            )

        } else {

            showMessage(
                "Follow the glowing circle"
            )
        }
    }

    // =========================================================
    // RELAX TOUCH
    // =========================================================

    private fun relaxTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 75f) {

            screen = Screen.GAMES
            return
        }

        relaxTaps++

        relaxPulse =
            animation

        if (relaxTaps % 5 == 0) {

            score += 5

            save()

            showMessage(
                "Nice breathing! +5 XP"
            )

        } else {

            showMessage(
                "Breathe slowly..."
            )
        }
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

        if (y > h - 90f) {

            when {

                x < w * .20f ->
                    screen = Screen.HOME

                x < w * .40f ->
                    screen = Screen.GAMES

                x < w * .60f -> {
                    focusStarted = false
                    screen = Screen.FOCUS
                }

                x < w * .80f ->
                    screen = Screen.DAILY

                else ->
                    screen = Screen.PROFILE
            }

            return
        }

        if (y in 220f..285f) {

            puzzleTarget =
                Random.nextInt(16)

            screen = Screen.PUZZLE

            return
        }

        if (y in 330f..420f) {

            score += 50
            streak++

            save()

            showMessage(
                "Daily reward! +50 XP"
            )
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
            return
        }

        if (y > h - 90f) {

            when {

                x < w * .20f ->
                    screen = Screen.HOME

                x < w * .40f ->
                    screen = Screen.GAMES

                x < w * .60f -> {
                    focusStarted = false
                    screen = Screen.FOCUS
                }

                x < w * .80f ->
                    screen = Screen.DAILY

                else ->
                    screen = Screen.PROFILE
            }
        }
    }

    // =========================================================
    // BACK
    // =========================================================

    fun goBack(): Boolean {

        return when (screen) {

            Screen.HOME,
            Screen.SPLASH -> false

            Screen.WELCOME -> {
                screen = Screen.HOME
                invalidate()
                true
            }

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
