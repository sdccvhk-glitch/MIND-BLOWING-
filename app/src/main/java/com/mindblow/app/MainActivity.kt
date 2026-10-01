package com.mindblow.app

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    override fun onPause() {
        super.onPause()
        gameView.stopMusic()
    }

    override fun onResume() {
        super.onResume()
        if (::gameView.isInitialized && gameView.musicEnabled) {
            gameView.startMusic()
        }
    }
}

private enum class Screen {
    SPLASH,
    WELCOME,
    HOME,
    GAMES,
    GLOW,
    MEMORY,
    REACTION,
    NUMBER,
    COLOR,
    BREATHE,
    DAILY,
    PROFILE,
    MUSIC
}

private class MindBlowView(
    context: Context
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs =
        context.getSharedPreferences("mindblow_data", Context.MODE_PRIVATE)

    private val handler = Handler(Looper.getMainLooper())

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

    var musicEnabled =
        prefs.getBoolean("music_enabled", true)

    private var musicTrack: AudioTrack? = null
    private var musicThread: Thread? = null

    private var message = ""
    private var messageUntil = 0L

    // ---------------------------------------------------------
    // GLOW HUNT
    // ---------------------------------------------------------

    private var glowTarget = Random.nextInt(16)

    // ---------------------------------------------------------
    // MEMORY
    // ---------------------------------------------------------

    private val symbols = arrayOf(
        "★", "◆", "●",
        "✦", "☀", "☾",
        "✿", "❖", "♥"
    )

    private var memoryBoard =
        MutableList(9) { it }.apply { shuffle() }

    private var memoryFirst = -1
    private var memorySecond = -1
    private var memoryPreviewUntil = 0L

    // ---------------------------------------------------------
    // REACTION
    // ---------------------------------------------------------

    private var reactionReady = false
    private var reactionStart = 0L
    private var reactionBest = Long.MAX_VALUE

    // ---------------------------------------------------------
    // NUMBER FLOW
    // ---------------------------------------------------------

    private var numberSequence = ""
    private var numberAnswer = ""
    private var numberShowing = false
    private var numberLevel = 1

    // ---------------------------------------------------------
    // COLOR FOCUS
    // ---------------------------------------------------------

    private val colorNames = arrayOf(
        "RED",
        "BLUE",
        "GREEN",
        "YELLOW"
    )

    private val colorValues = intArrayOf(
        Color.rgb(255, 75, 90),
        Color.rgb(70, 180, 255),
        Color.rgb(70, 230, 145),
        Color.rgb(255, 205, 65)
    )

    private var colorWord = 0
    private var colorInk = 0
    private var colorScore = 0

    // ---------------------------------------------------------
    // BREATHING
    // ---------------------------------------------------------

    private var breathStart = 0L
    private var breathRunning = false
    private var breathCycles = 0

    init {
        isFocusable = true
        startMusicIfNeeded()
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

            Screen.GLOW ->
                drawGlow(canvas, w, h)

            Screen.MEMORY ->
                drawMemory(canvas, w, h)

            Screen.REACTION ->
                drawReaction(canvas, w, h)

            Screen.NUMBER ->
                drawNumber(canvas, w, h)

            Screen.COLOR ->
                drawColor(canvas, w, h)

            Screen.BREATHE ->
                drawBreathe(canvas, w, h)

            Screen.DAILY ->
                drawDaily(canvas, w, h)

            Screen.PROFILE ->
                drawProfile(canvas, w, h)

            Screen.MUSIC ->
                drawMusic(canvas, w, h)
        }

        if (messageUntil > System.currentTimeMillis()) {
            drawToast(canvas, w, h)
        }

        if (
            screen == Screen.SPLASH &&
            System.currentTimeMillis() - splashTime > 1300L
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
            Color.rgb(3, 6, 24),
            Color.rgb(37, 7, 60),
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

        paint.color =
            Color.argb(45, 0, 220, 255)

        canvas.drawCircle(
            w * 0.16f + sin(t) * 45f,
            h * 0.18f + cos(t) * 30f,
            145f,
            paint
        )

        paint.color =
            Color.argb(36, 155, 60, 255)

        canvas.drawCircle(
            w * 0.84f + cos(t * 0.7f) * 45f,
            h * 0.35f + sin(t) * 35f,
            170f,
            paint
        )

        paint.color =
            Color.argb(30, 30, 255, 170)

        canvas.drawCircle(
            w * 0.50f + sin(t * 0.6f) * 40f,
            h * 0.82f,
            180f,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f

        paint.color =
            Color.argb(22, 80, 210, 255)

        for (i in 0 until 7) {

            canvas.drawCircle(
                w / 2f,
                h * 0.43f,
                65f + i * 52f +
                        sin(t + i) * 5f,
                paint
            )
        }

        paint.style = Paint.Style.FILL

        // Floating particles
        for (i in 0 until 18) {

            val px =
                ((i * 97f + sin(t * 0.7f + i) * 50f) %
                        max(w, 1f))

            val py =
                ((i * 137f + cos(t * 0.5f + i) * 40f) %
                        max(h, 1f))

            paint.color =
                Color.argb(
                    80,
                    100,
                    220,
                    255
                )

            canvas.drawCircle(
                px,
                py,
                1.5f + (i % 3),
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

        paint.typeface =
            Typeface.create(
                "sans-serif",
                if (bold)
                    Typeface.BOLD
                else
                    Typeface.NORMAL
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

        paint.color =
            Color.argb(
                225,
                12,
                18,
                43
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
        paint.strokeWidth = 1.1f

        paint.color =
            Color.argb(
                75,
                100,
                170,
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

        paint.shader =
            LinearGradient(
                left,
                top,
                right,
                bottom,
                Color.rgb(20, 215, 255),
                Color.rgb(140, 60, 255),
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

    private fun header(
        canvas: Canvas,
        title: String,
        subtitle: String
    ) {

        text(
            canvas,
            "‹",
            20f,
            51f,
            40f
        )

        text(
            canvas,
            title,
            58f,
            42f,
            23f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            58f,
            63f,
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
            9f,
            top,
            w - 9f,
            h - 7f,
            23f
        )

        val icons =
            arrayOf(
                "⌂",
                "◆",
                "◎",
                "★",
                "●"
            )

        val labels =
            arrayOf(
                "Home",
                "Games",
                "Focus",
                "Daily",
                "Profile"
            )

        for (i in 0..4) {

            val x =
                w * (i + 0.5f) / 5f

            val col =
                if (i == selected)
                    0xff55e6ff.toInt()
                else
                    0xff7883a8.toInt()

            text(
                canvas,
                icons[i],
                x,
                top + 29f,
                20f,
                col,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                labels[i],
                x,
                top + 50f,
                9f,
                col,
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
            h * 0.39f,
            82f,
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
            "RESET • REFRESH • RECHARGE",
            w / 2f,
            h * 0.545f,
            12f,
            0xffb8c4e5.toInt(),
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
            "Your little escape from the noise.",
            w / 2f,
            101f,
            14f,
            0xffaeb8d9.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "✦",
            w / 2f,
            h * 0.43f,
            90f,
            0xff63e8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "PLAY • BREATHE • REFRESH",
            w / 2f,
            h * 0.59f,
            22f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Quick games for your brain.",
            w / 2f,
            h * 0.65f,
            14f,
            0xffaeb8d9.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            canvas,
            "ENTER MINDSPACE",
            w * .14f,
            h * .76f,
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
            "Level ${score / 500 + 1}  •  Refresh your mind",
            20f,
            62f,
            11f,
            0xff9ca8cc.toInt()
        )

        card(
            canvas,
            w - 118f,
            17f,
            w - 18f,
            58f,
            16f
        )

        text(
            canvas,
            "✦ $score XP",
            w - 68f,
            43f,
            13f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )

        // Hero
        card(
            canvas,
            18f,
            82f,
            w - 18f,
            204f,
            25f
        )

        text(
            canvas,
            "HOW DO YOU FEEL?",
            35f,
            112f,
            11f,
            0xff5fe7ff.toInt(),
            true
        )

        text(
            canvas,
            "Take a tiny break.",
            35f,
            145f,
            23f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Play something. Breathe. Reset.",
            35f,
            169f,
            12f,
            0xffaab5d6.toInt()
        )

        button(
            canvas,
            "START A QUICK RESET",
            35f,
            178f,
            w - 35f,
            194f
        )

        text(
            canvas,
            "MIND REFRESHING",
            20f,
            236f,
            12f,
            0xff61ddff.toInt(),
            true
        )

        // Music
        card(
            canvas,
            18f,
            248f,
            w - 18f,
            310f,
            18f
        )

        text(
            canvas,
            if (musicEnabled) "♫" else "🔇",
            39f,
            285f,
            25f,
            0xff8b7cff.toInt(),
            true
        )

        text(
            canvas,
            "Mind Refreshing Music",
            75f,
            274f,
            15f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            if (musicEnabled)
                "Ambient sound is playing"
            else
                "Music is paused",
            75f,
            294f,
            10f,
            0xff9da8cc.toInt()
        )

        button(
            canvas,
            if (musicEnabled) "MUSIC ON" else "MUSIC OFF",
            w - 130f,
            265f,
            w - 32f,
            300f
        )

        text(
            canvas,
            "EXPLORE GAMES",
            20f,
            340f,
            12f,
            0xff61ddff.toInt(),
            true
        )

        gameCard(
            canvas,
            "✦",
            "Glow",
            "Find it",
            18f,
            352f,
            w / 2f - 8f,
            430f
        )

        gameCard(
            canvas,
            "◆",
            "Memory",
            "Match it",
            w / 2f + 8f,
            352f,
            w - 18f,
            430f
        )

        gameCard(
            canvas,
            "⚡",
            "Reaction",
            "React fast",
            18f,
            438f,
            w / 2f - 8f,
            516f
        )

        gameCard(
            canvas,
            "◎",
            "Breathe",
            "Slow down",
            w / 2f + 8f,
            438f,
            w - 18f,
            516f
        )

        navigation(
            canvas,
            w,
            h,
            0
        )
    }

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
            18f
        )

        text(
            canvas,
            icon,
            left + 31f,
            top + 36f,
            22f,
            0xff5ce7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            title,
            left + 17f,
            top + 58f,
            14f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            left + 17f,
            top + 74f,
            9f,
            0xff9da8cc.toInt()
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
            "Six quick ways to reset"
        )

        gameCard(
            canvas,
            "✦",
            "Glow Hunt",
            "Find the light",
            18f,
            92f,
            w / 2f - 8f,
            178f
        )

        gameCard(
            canvas,
            "◆",
            "Memory",
            "Match symbols",
            w / 2f + 8f,
            92f,
            w - 18f,
            178f
        )

        gameCard(
            canvas,
            "⚡",
            "Reaction",
            "Test reflexes",
            18f,
            188f,
            w / 2f - 8f,
            274f
        )

        gameCard(
            canvas,
            "123",
            "Number Flow",
            "Remember numbers",
            w / 2f + 8f,
            188f,
            w - 18f,
            274f
        )

        gameCard(
            canvas,
            "●",
            "Color Focus",
            "Train attention",
            18f,
            284f,
            w / 2f - 8f,
            370f
        )

        gameCard(
            canvas,
            "☾",
            "Breathe",
            "Calm your mind",
            w / 2f + 8f,
            284f,
            w - 18f,
            370f
        )

        card(
            canvas,
            18f,
            390f,
            w - 18f,
            455f,
            20f
        )

        text(
            canvas,
            "🔥 $streak day streak",
            35f,
            423f,
            16f,
            0xffffb52e.toInt(),
            true
        )

        text(
            canvas,
            "$score XP collected",
            w - 35f,
            423f,
            11f,
            0xff9ca8cc.toInt(),
            false,
            Paint.Align.RIGHT
        )

        navigation(
            canvas,
            w,
            h,
            1
        )
    }

    // =========================================================
    // GLOW HUNT
    // =========================================================

    private fun drawGlow(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Glow Hunt",
            "Find the glowing tile"
        )

        text(
            canvas,
            "FOCUS",
            w / 2f,
            91f,
            11f,
            0xff61ddff.toInt(),
            true,
            Paint.Align.CENTER
        )

        val size =
            min(w * .82f, 330f)

        val left =
            (w - size) / 2f

        val top = 115f
        val cell = size / 4f

        for (i in 0 until 16) {

            val row = i / 4
            val col = i % 4

            val l =
                left + col * cell + 4f

            val tt =
                top + row * cell + 4f

            val r =
                left + (col + 1) * cell - 4f

            val b =
                top + (row + 1) * cell - 4f

            card(
                canvas,
                l,
                tt,
                r,
                b,
                14f
            )

            if (i == glowTarget) {

                val pulse =
                    18f +
                            sin(animation * 5f) * 5f

                paint.color =
                    0xff42e8ff.toInt()

                canvas.drawCircle(
                    (l + r) / 2f,
                    (tt + b) / 2f,
                    pulse,
                    paint
                )

                paint.color =
                    Color.WHITE

                canvas.drawCircle(
                    (l + r) / 2f,
                    (tt + b) / 2f,
                    5f,
                    paint
                )
            }
        }

        text(
            canvas,
            "Tap the glowing tile",
            w / 2f,
            top + size + 32f,
            15f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "+25 XP",
            w / 2f,
            top + size + 55f,
            11f,
            0xff7f8bb2.toInt(),
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
            "Remember the symbols"
        )

        val size =
            min(w * .82f, 330f)

        val left =
            (w - size) / 2f

        val top = 105f
        val cell = size / 3f

        val preview =
            System.currentTimeMillis() <
                    memoryPreviewUntil

        for (i in 0 until 9) {

            val row = i / 3
            val col = i % 3

            val l =
                left + col * cell + 5f

            val tt =
                top + row * cell + 5f

            val r =
                left + (col + 1) * cell - 5f

            val b =
                top + (row + 1) * cell - 5f

            card(
                canvas,
                l,
                tt,
                r,
                b,
                16f
            )

            val reveal =
                preview ||
                        i == memoryFirst ||
                        i == memorySecond

            text(
                canvas,
                if (reveal)
                    symbols[memoryBoard[i]]
                else
                    "?",
                (l + r) / 2f,
                (tt + b) / 2f + 10f,
                28f,
                if (reveal)
                    0xff63e8ff.toInt()
                else
                    0xff647092.toInt(),
                true,
                Paint.Align.CENTER
            )
        }

        text(
            canvas,
            if (preview)
                "Remember the board..."
            else
                "Tap two matching cards",
            w / 2f,
            top + size + 32f,
            14f,
            0xffb5bfdf.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // REACTION
    // =========================================================

    private fun drawReaction(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Reaction Tap",
            "Wait for cyan, then tap"
        )

        text(
            canvas,
            if (reactionReady)
                "TAP NOW!"
            else
                "WAIT...",
            w / 2f,
            125f,
            24f,
            if (reactionReady)
                0xff54f0b2.toInt()
            else
                0xffffc857.toInt(),
            true,
            Paint.Align.CENTER
        )

        val cx = w / 2f
        val cy = 280f

        val radius =
            95f + sin(animation * 3f) * 8f

        paint.color =
            if (reactionReady)
                0xff35edb0.toInt()
            else
                0xff754cff.toInt()

        canvas.drawCircle(
            cx,
            cy,
            radius,
            paint
        )

        paint.color =
            Color.argb(
                60,
                255,
                255,
                255
            )

        canvas.drawCircle(
            cx,
            cy,
            radius - 20f,
            paint
        )

        text(
            canvas,
            "●",
            cx,
            cy + 23f,
            42f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        if (reactionBest != Long.MAX_VALUE) {

            text(
                canvas,
                "Best: ${reactionBest} ms",
                w / 2f,
                425f,
                16f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        text(
            canvas,
            "Tap the circle",
            w / 2f,
            465f,
            13f,
            0xffaeb8d8.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // NUMBER FLOW
    // =========================================================

    private fun drawNumber(
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
            w / 2f,
            110f,
            12f,
            0xff61ddff.toInt(),
            true,
            Paint.Align.CENTER
        )

        card(
            canvas,
            24f,
            140f,
            w - 24f,
            250f,
            25f
        )

        if (numberShowing) {

            text(
                canvas,
                numberSequence,
                w / 2f,
                205f,
                32f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                "MEMORIZE",
                w / 2f,
                230f,
                10f,
                0xff65ddff.toInt(),
                true,
                Paint.Align.CENTER
            )

        } else {

            text(
                canvas,
                "What was the number?",
                w / 2f,
                185f,
                16f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                "Tap START to see it",
                w / 2f,
                215f,
                12f,
                0xff9ca8cc.toInt(),
                false,
                Paint.Align.CENTER
            )
        }

        button(
            canvas,
            if (numberShowing)
                "REMEMBER..."
            else
                "START ROUND",
            45f,
            290f,
            w - 45f,
            345f
        )

        card(
            canvas,
            45f,
            370f,
            w - 45f,
            425f,
            18f
        )

        text(
            canvas,
            if (numberAnswer.isEmpty())
                "Your answer appears here"
            else
                numberAnswer,
            w / 2f,
            405f,
            16f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // COLOR FOCUS
    // =========================================================

    private fun drawColor(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Color Focus",
            "Tap the ink color"
        )

        text(
            canvas,
            "SCORE  $colorScore",
            w / 2f,
            105f,
            13f,
            0xff61ddff.toInt(),
            true,
            Paint.Align.CENTER
        )

        card(
            canvas,
            35f,
            135f,
            w - 35f,
            245f,
            25f
        )

        text(
            canvas,
            colorNames[colorWord],
            w / 2f,
            205f,
            42f,
            colorValues[colorInk],
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "What COLOR is the word?",
            w / 2f,
            278f,
            14f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        for (i in 0 until 4) {

            val row = i / 2
            val col = i % 2

            val l =
                28f +
                        col * (w - 56f) / 2f

            val r =
                28f +
                        (col + 1) *
                        (w - 56f) / 2f -
                        8f

            val top =
                310f +
                        row * 75f

            card(
                canvas,
                l,
                top,
                r,
                top + 60f,
                16f
            )

            text(
                canvas,
                colorNames[i],
                (l + r) / 2f,
                top + 37f,
                14f,
                colorValues[i],
                true,
                Paint.Align.CENTER
            )
        }
    }

    // =========================================================
    // BREATHE
    // =========================================================

    private fun drawBreathe(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Breathe",
            "Slow your mind down"
        )

        val elapsed =
            if (breathRunning)
                System.currentTimeMillis() -
                        breathStart
            else
                0L

        val phase =
            (elapsed % 8000L) / 8000f

        val size =
            if (phase < 0.5f)
                75f + phase * 180f
            else
                165f - (phase - 0.5f) * 180f

        val cx = w / 2f
        val cy = 265f

        for (i in 0..4) {

            paint.color =
                Color.argb(
                    22 - i * 3,
                    60,
                    220,
                    255
                )

            canvas.drawCircle(
                cx,
                cy,
                size + i * 32f,
                paint
            )
        }

        paint.color =
            0xff55dfff.toInt()

        canvas.drawCircle(
            cx,
            cy,
            size,
            paint
        )

        text(
            canvas,
            if (!breathRunning)
                "READY"
            else if (phase < 0.5f)
                "BREATHE IN"
            else
                "BREATHE OUT",
            cx,
            cy + 7f,
            18f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Cycles: $breathCycles",
            cx,
            415f,
            15f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        button(
            canvas,
            if (breathRunning)
                "STOP"
            else
                "START BREATHING",
            45f,
            450f,
            w - 45f,
            505f
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
            "Daily Reset",
            "One small win today"
        )

        card(
            canvas,
            18f,
            90f,
            w - 18f,
            280f,
            25f
        )

        text(
            canvas,
            "TODAY'S CHALLENGE",
            38f,
            128f,
            11f,
            0xff61ddff.toInt(),
            true
        )

        text(
            canvas,
            "Clear your mind",
            38f,
            165f,
            25f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Complete any MindBlow game",
            38f,
            195f,
            13f,
            0xffaeb8d7.toInt()
        )

        text(
            canvas,
            "and earn bonus XP.",
            38f,
            217f,
            13f,
            0xffaeb8d7.toInt()
        )

        button(
            canvas,
            "PLAY A GAME",
            38f,
            232f,
            w - 38f,
            267f
        )

        text(
            canvas,
            "🔥 $streak day streak",
            22f,
            330f,
            20f,
            0xffffb52e.toInt(),
            true
        )

        button(
            canvas,
            "CLAIM +50 XP",
            22f,
            360f,
            w - 22f,
            412f
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
            "Your MindBlow journey"
        )

        card(
            canvas,
            18f,
            88f,
            w - 18f,
            220f,
            25f
        )

        text(
            canvas,
            "✦",
            65f,
            160f,
            54f,
            0xff65e0ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Mind Explorer",
            105f,
            130f,
            21f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Level ${score / 500 + 1}",
            105f,
            157f,
            13f,
            0xffaeb8d8.toInt()
        )

        text(
            canvas,
            "$score XP",
            105f,
            184f,
            13f,
            0xff7f8bb2.toInt()
        )

        text(
            canvas,
            "🔥 $streak day streak",
            105f,
            207f,
            12f,
            0xffffb52e.toInt()
        )

        card(
            canvas,
            18f,
            240f,
            w - 18f,
            405f,
            22f
        )

        text(
            canvas,
            "♫  Music",
            40f,
            280f,
            16f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            if (musicEnabled)
                "Mind refreshing audio ON"
            else
                "Audio OFF",
            40f,
            302f,
            11f,
            0xff9da8cc.toInt()
        )

        text(
            canvas,
            "●  XP",
            40f,
            344f,
            16f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "$score total experience",
            40f,
            366f,
            11f,
            0xff9da8cc.toInt()
        )

        navigation(
            canvas,
            w,
            h,
            4
        )
    }

    // =========================================================
    // MUSIC
    // =========================================================

    private fun drawMusic(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            canvas,
            "Mind Music",
            "Relaxing ambient sound"
        )

        card(
            canvas,
            25f,
            100f,
            w - 25f,
            315f,
            28f
        )

        val pulse =
            80f +
                    sin(animation * 2f) * 10f

        paint.color =
            Color.argb(
                35,
                50,
                220,
                255
            )

        canvas.drawCircle(
            w / 2f,
            205f,
            pulse + 50f,
            paint
        )

        paint.color =
            0xff5ce5ff.toInt()

        canvas.drawCircle(
            w / 2f,
            205f,
            pulse,
            paint
        )

        text(
            canvas,
            "♫",
            w / 2f,
            222f,
            42f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Mind Refreshing",
            w / 2f,
            275f,
            21f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Soft ambient background sound",
            w / 2f,
            298f,
            11f,
            0xffaeb8d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            canvas,
            if (musicEnabled)
                "PAUSE MUSIC"
            else
                "PLAY MUSIC",
            35f,
            350f,
            w - 35f,
            405f
        )

        text(
            canvas,
            "The music is generated inside the app.",
            w / 2f,
            445f,
            11f,
            0xff8793b7.toInt(),
            false,
            Paint.Align.CENTER
        )

        navigation(
            canvas,
            w,
            h,
            0
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
            h - 84f,
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

    private fun message(value: String) {
        message = value
        messageUntil =
            System.currentTimeMillis() + 1400L
    }

    private fun save() {

        prefs.edit()
            .putInt("score", score)
            .putInt("streak", streak)
            .putBoolean(
                "music_enabled",
                musicEnabled
            )
            .apply()
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

        val x = event.x
        val y = event.y
        val w = width.toFloat()
        val h = height.toFloat()

        when (screen) {

            Screen.SPLASH -> {
                screen = Screen.WELCOME
            }

            Screen.WELCOME -> {

                if (y > h * .68f) {

                    prefs.edit()
                        .putBoolean(
                            "welcome_seen",
                            true
                        )
                        .apply()

                    screen = Screen.HOME
                }
            }

            Screen.HOME ->
                homeTouch(x, y, w, h)

            Screen.GAMES ->
                gamesTouch(x, y, w, h)

            Screen.GLOW ->
                glowTouch(x, y, w, h)

            Screen.MEMORY ->
                memoryTouch(x, y, w, h)

            Screen.REACTION ->
                reactionTouch(x, y, w, h)

            Screen.NUMBER ->
                numberTouch(x, y, w, h)

            Screen.COLOR ->
                colorTouch(x, y, w, h)

            Screen.BREATHE ->
                breatheTouch(x, y, w, h)

            Screen.DAILY ->
                dailyTouch(x, y, w, h)

            Screen.PROFILE ->
                profileTouch(x, y, w, h)

            Screen.MUSIC ->
                musicTouch(x, y, w, h)
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

            if (x < w * .20f) {
                screen = Screen.HOME
                return
            }

            if (x < w * .40f) {
                screen = Screen.GAMES
                return
            }

            if (x < w * .60f) {
                startBreathing()
                return
            }

            if (x < w * .80f) {
                screen = Screen.DAILY
                return
            }

            screen = Screen.PROFILE
            return
        }

        if (y in 245f..315f) {
            screen = Screen.MUSIC
            return
        }

        if (y in 350f..430f) {

            if (x < w / 2f) {
                startGlow()
            } else {
                startMemory()
            }

            return
        }

        if (y in 435f..525f) {

            if (x < w / 2f) {
                startReaction()
            } else {
                startBreathing()
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

            if (x < w * .20f) {
                screen = Screen.HOME
            } else if (x < w * .40f) {
                screen = Screen.GAMES
            } else if (x < w * .60f) {
                startBreathing()
            } else if (x < w * .80f) {
                screen = Screen.DAILY
            } else {
                screen = Screen.PROFILE
            }

            return
        }

        when {

            y in 92f..178f -> {

                if (x < w / 2f)
                    startGlow()
                else
                    startMemory()
            }

            y in 188f..274f -> {

                if (x < w / 2f)
                    startReaction()
                else
                    startNumber()
            }

            y in 284f..370f -> {

                if (x < w / 2f)
                    startColor()
                else
                    startBreathing()
            }
        }
    }

    // =========================================================
    // GLOW TOUCH
    // =========================================================

    private fun startGlow() {

        glowTarget =
            Random.nextInt(16)

        screen = Screen.GLOW
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

        val size =
            min(w * .82f, 330f)

        val left =
            (w - size) / 2f

        val top = 115f

        if (
            x < left ||
            x > left + size ||
            y < top ||
            y > top + size
        ) {
            return
        }

        val cell = size / 4f

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

        if (index == glowTarget) {

            score += 25
            save()

            message("Perfect! +25 XP")

            glowTarget =
                Random.nextInt(16)

        } else {

            message("Look for the glow ✨")
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
            System.currentTimeMillis()
            < memoryPreviewUntil
        ) {
            return
        }

        val size =
            min(w * .82f, 330f)

        val left =
            (w - size) / 2f

        val top = 105f

        if (
            x < left ||
            x > left + size ||
            y < top ||
            y > top + size
        ) {
            return
        }

        val cell =
            size / 3f

        val col =
            ((x - left) / cell)
                .toInt()
                .coerceIn(0, 2)

        val row =
            ((y - top) / cell)
                .toInt()
                .coerceIn(0, 2)

        val index =
            row * 3 + col

        if (memoryFirst == -1) {

            memoryFirst = index

        } else if (
            memorySecond == -1 &&
            index != memoryFirst
        ) {

            memorySecond = index

            val match =
                memoryBoard[memoryFirst] ==
                        memoryBoard[memorySecond]

            if (match) {

                score += 20
                save()

                message("Match! +20 XP")

                handler.postDelayed({

                    memoryFirst = -1
                    memorySecond = -1

                    invalidate()

                }, 500L)

            } else {

                message("Not a match")

                handler.postDelayed({

                    memoryFirst = -1
                    memorySecond = -1

                    invalidate()

                }, 700L)
            }
        }
    }

    // =========================================================
    // REACTION
    // =========================================================

    private fun startReaction() {

        reactionReady = false
        reactionStart = 0L

        screen = Screen.REACTION

        val delay =
            Random.nextLong(
                1200L,
                3500L
            )

        handler.postDelayed({

            if (screen == Screen.REACTION) {

                reactionReady = true
                reactionStart =
                    System.currentTimeMillis()

                invalidate()
            }

        }, delay)
    }

    private fun reactionTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        val dx =
            x - w / 2f

        val dy =
            y - 280f

        if (dx * dx + dy * dy > 130f * 130f) {
            return
        }

        if (!reactionReady) {

            message("Too early! Wait for green.")
            return
        }

        val result =
            System.currentTimeMillis() -
                    reactionStart

        reactionBest =
            min(
                reactionBest,
                result
            )

        reactionReady = false

        score +=
            if (result < 300L)
                35
            else
                20

        save()

        message(
            "$result ms  •  +${if (result < 300L) 35 else 20} XP"
        )

        handler.postDelayed({
            if (screen == Screen.REACTION) {
                startReaction()
            }
        }, 900L)
    }

    // =========================================================
    // NUMBER
    // =========================================================

    private fun startNumber() {

        numberLevel = 1
        numberSequence = ""
        numberAnswer = ""
        numberShowing = false

        screen = Screen.NUMBER
    }

    private fun numberTouch(
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
            y in 285f..355f &&
            !numberShowing
        ) {

            val length =
                min(
                    3 + numberLevel,
                    8
                )

            numberSequence =
                buildString {

                    repeat(length) {
                        append(
                            Random.nextInt(
                                0,
                                10
                            )
                        )
                    }
                }

            numberAnswer = ""
            numberShowing = true

            invalidate()

            handler.postDelayed({

                if (screen == Screen.NUMBER) {
                    numberShowing = false
                    numberAnswer = numberSequence
                    invalidate()
                }

            }, 1800L)
        }
    }

    // =========================================================
    // COLOR
    // =========================================================

    private fun startColor() {

        colorScore = 0
        nextColorRound()

        screen = Screen.COLOR
    }

    private fun nextColorRound() {

        colorWord =
            Random.nextInt(4)

        colorInk =
            Random.nextInt(4)
    }

    private fun colorTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        if (y < 310f || y > 620f) {
            return
        }

        val row =
            ((y - 310f) / 75f)
                .toInt()
                .coerceIn(0, 1)

        val col =
            if (x < w / 2f)
                0
            else
                1

        val selected =
            row * 2 + col

        if (selected == colorInk) {

            colorScore++
            score += 10

            message("+10 XP • Correct!")

            if (colorScore % 5 == 0) {
                save()
            }

        } else {

            message("Focus on the ink color")
        }

        nextColorRound()
    }

    // =========================================================
    // BREATHING
    // =========================================================

    private fun startBreathing() {

        breathRunning = false
        breathCycles = 0

        screen = Screen.BREATHE
    }

    private fun breatheTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            breathRunning = false
            return
        }

        if (y in 440f..525f) {

            if (!breathRunning) {

                breathRunning = true
                breathStart =
                    System.currentTimeMillis()

            } else {

                breathRunning = false

                score += 15
                breathCycles++

                save()

                message(
                    "Calm moment complete • +15 XP"
                )
            }
        }
    }

    // =========================================================
    // DAILY
    // =========================================================

    private fun dailyTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 80f) {
            screen = Screen.HOME
            return
        }

        if (y in 225f..285f) {

            startGlow()
            return
        }

        if (y in 345f..430f) {

            score += 50
            streak++
            save()

            message(
                "Daily reward! +50 XP"
            )
        }
    }

    // =========================================================
    // PROFILE
    // =========================================================

    private fun profileTouch(
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

            if (x < w * .20f) {
                screen = Screen.HOME
            } else if (x < w * .40f) {
                screen = Screen.GAMES
            } else if (x < w * .60f) {
                startBreathing()
            } else if (x < w * .80f) {
                screen = Screen.DAILY
            }

            return
        }

        if (y in 250f..330f) {
            screen = Screen.MUSIC
        }
    }

    // =========================================================
    // MUSIC TOUCH
    // =========================================================

    private fun musicTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 80f) {
            screen = Screen.HOME
            return
        }

        if (y in 340f..420f) {

            musicEnabled = !musicEnabled

            if (musicEnabled) {
                startMusic()
                message("Mind refreshing music ON")
            } else {
                stopMusic()
                message("Music paused")
            }

            save()
        }
    }

    // =========================================================
    // MUSIC ENGINE
    // =========================================================

    private fun startMusicIfNeeded() {

        if (musicEnabled) {
            startMusic()
        }
    }

    fun startMusic() {

        if (musicTrack != null) {
            return
        }

        musicThread =
            Thread {

                try {

                    val sampleRate = 22050

                    val minBuffer =
                        AudioTrack.getMinBufferSize(
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )

                    val bufferSize =
                        max(
                            minBuffer,
                            sampleRate / 2
                        )

                    val track =
                        AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferSize,
                            AudioTrack.MODE_STREAM
                        )

                    musicTrack = track

                    track.play()

                    var phase = 0.0

                    while (
                        musicEnabled &&
                        !Thread.currentThread()
                            .isInterrupted
                    ) {

                        val samples =
                            ShortArray(
                                sampleRate / 4
                            )

                        for (i in samples.indices) {

                            val time =
                                phase /
                                        sampleRate.toDouble()

                            val wave1 =
                                sin(
                                    2.0 *
                                            Math.PI *
                                            110.0 *
                                            time
                                )

                            val wave2 =
                                sin(
                                    2.0 *
                                            Math.PI *
                                            164.81 *
                                            time
                                )

                            val wave3 =
                                sin(
                                    2.0 *
                                            Math.PI *
                                            220.0 *
                                            time
                                )

                            val envelope =
                                0.12 +
                                        0.04 *
                                        sin(
                                            2.0 *
                                                    Math.PI *
                                                    0.08 *
                                                    time
                                        )

                            val sample =
                                (
                                        (
                                                wave1 *
                                                        0.45 +
                                                        wave2 *
                                                        0.30 +
                                                        wave3 *
                                                        0.20
                                                ) *
                                                envelope *
                                                Short.MAX_VALUE
                                        ).toInt()
                                    .coerceIn(
                                        Short.MIN_VALUE.toInt(),
                                        Short.MAX_VALUE.toInt()
                                    )
                                    .toShort()

                            samples[i] = sample

                            phase++
                        }

                        track.write(
                            samples,
                            0,
                            samples.size
                        )
                    }

                    track.stop()
                    track.release()

                    musicTrack = null

                } catch (_: Exception) {

                    musicTrack = null
                }

            }

        musicThread?.start()
    }

    fun stopMusic() {

        musicEnabled =
            if (screen == Screen.MUSIC)
                musicEnabled
            else
                musicEnabled

        musicThread?.interrupt()
        musicThread = null

        try {
            musicTrack?.stop()
            musicTrack?.release()
        } catch (_: Exception) {
        }

        musicTrack = null
    }

    // =========================================================
    // BACK
    // =========================================================

    fun goBack(): Boolean {

        when (screen) {

            Screen.HOME,
            Screen.SPLASH,
            Screen.WELCOME -> {
                return false
            }

            Screen.GAMES,
            Screen.DAILY,
            Screen.PROFILE,
            Screen.MUSIC -> {
                screen = Screen.HOME
            }

            else -> {
                screen = Screen.GAMES
            }
        }

        breathRunning = false
        reactionReady = false

        invalidate()

        return true
    }
}
