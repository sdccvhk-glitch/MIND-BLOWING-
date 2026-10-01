package com.mindblow.app

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var mindView: MindBlowView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        mindView = MindBlowView(this)
        setContentView(mindView)
    }

    override fun onResume() {
        super.onResume()
        if (::mindView.isInitialized) {
            mindView.resumeMusic()
        }
    }

    override fun onPause() {
        if (::mindView.isInitialized) {
            mindView.pauseMusic()
        }
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::mindView.isInitialized && mindView.goBack()) {
            return
        }
        super.onBackPressed()
    }
}

private enum class AppScreen {
    HOME,
    GAMES,
    MUSIC,
    DAILY,
    PROFILE,
    PULSE,
    MEMORY,
    REACTION,
    COLORS,
    NUMBER,
    BREATHE
}

private class MindBlowView(
    private val appContext: Context
) : View(appContext) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)

    private val prefs =
        appContext.getSharedPreferences("mindblow_native", Context.MODE_PRIVATE)

    private var screen =
        AppScreen.HOME

    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)
    private var musicEnabled = prefs.getBoolean("music", true)

    private var animation = 0f

    private var topInset = 0
    private var bottomInset = 0

    private var toast = ""
    private var toastUntil = 0L

    // ---------------------------------------------------------
    // PULSE HUNT
    // ---------------------------------------------------------

    private var pulseX = 0f
    private var pulseY = 0f
    private var pulseRadius = 52f
    private var pulseRound = 0
    private var pulseRunning = false
    private var pulseStartedAt = 0L
    private var pulseTimeLimit = 30_000L

    // ---------------------------------------------------------
    // MEMORY MATRIX
    // ---------------------------------------------------------

    private var memorySequence = mutableListOf<Int>()
    private var memoryInput = mutableListOf<Int>()
    private var memoryShowing = false
    private var memoryShowIndex = 0
    private var memoryNextAt = 0L
    private var memoryRound = 1

    // ---------------------------------------------------------
    // QUICK REACTION
    // ---------------------------------------------------------

    private var reactionState = 0
    private var reactionStartedAt = 0L
    private var reactionWaitUntil = 0L
    private var reactionBest = Long.MAX_VALUE
    private var reactionFalseStart = false

    // ---------------------------------------------------------
    // COLOR MATCH
    // ---------------------------------------------------------

    private val colorNames = arrayOf(
        "RED",
        "BLUE",
        "GREEN",
        "YELLOW",
        "PURPLE",
        "ORANGE"
    )

    private val colorValues = intArrayOf(
        0xffff4f6d.toInt(),
        0xff42cfff.toInt(),
        0xff51e89a.toInt(),
        0xffffd65a.toInt(),
        0xffa875ff.toInt(),
        0xffff9c52.toInt()
    )

    private var colorWord = 0
    private var colorInk = 0
    private var colorCorrect = 0
    private var colorRound = 0

    // ---------------------------------------------------------
    // NUMBER FLOW
    // ---------------------------------------------------------

    private var numberTarget = 1
    private var numberNext = 1
    private var numberGrid = MutableList(9) { it + 1 }
    private var numberCorrect = 0
    private var numberRound = 0

    // ---------------------------------------------------------
    // BREATH
    // ---------------------------------------------------------

    private var breathStart = 0L
    private var breathRunning = false
    private var breathPhase = 0

    // ---------------------------------------------------------
    // MUSIC
    // ---------------------------------------------------------

    @Volatile
    private var musicPlaying = false

    @Volatile
    private var musicThreadRunning = false

    private var audioTrack: AudioTrack? = null
    private var musicThread: Thread? = null

    private val sampleRate = 44100

    init {
        isFocusable = true

        stroke.style = Paint.Style.STROKE

        if (Build.VERSION.SDK_INT >= 30) {
            setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars()
                )

                topInset = bars.top
                bottomInset = bars.bottom

                invalidate()
                insets
            }
        }

        post {
            if (musicEnabled) {
                startMusic()
            }
        }

        postInvalidateDelayed(16L)
    }

    // =========================================================
    // DRAW
    // =========================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        animation += 0.018f

        val w = width.toFloat()
        val h = height.toFloat()

        drawBackground(canvas, w, h)

        when (screen) {
            AppScreen.HOME -> drawHome(canvas, w, h)
            AppScreen.GAMES -> drawGames(canvas, w, h)
            AppScreen.MUSIC -> drawMusic(canvas, w, h)
            AppScreen.DAILY -> drawDaily(canvas, w, h)
            AppScreen.PROFILE -> drawProfile(canvas, w, h)

            AppScreen.PULSE -> drawPulse(canvas, w, h)
            AppScreen.MEMORY -> drawMemory(canvas, w, h)
            AppScreen.REACTION -> drawReaction(canvas, w, h)
            AppScreen.COLORS -> drawColors(canvas, w, h)
            AppScreen.NUMBER -> drawNumber(canvas, w, h)
            AppScreen.BREATHE -> drawBreathe(canvas, w, h)
        }

        if (toastUntil > System.currentTimeMillis()) {
            drawToast(canvas, w, h)
        }

        updateGames()

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
            Color.rgb(35, 5, 57),
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val t = animation

        // Cyan orb
        paint.color = Color.argb(35, 30, 220, 255)

        canvas.drawCircle(
            w * 0.12f + sin(t) * 25f,
            h * 0.22f + cos(t * 0.7f) * 20f,
            100f,
            paint
        )

        // Purple orb
        paint.color = Color.argb(38, 150, 70, 255)

        canvas.drawCircle(
            w * 0.88f + cos(t * 0.8f) * 28f,
            h * 0.38f + sin(t) * 25f,
            105f,
            paint
        )

        // Bottom orb
        paint.color = Color.argb(25, 40, 160, 255)

        canvas.drawCircle(
            w * 0.55f + sin(t * 0.4f) * 35f,
            h * 0.83f,
            95f,
            paint
        )

        // Stars
        paint.color = Color.argb(95, 100, 220, 255)

        for (i in 0 until 38) {
            val x =
                ((i * 97) % maxOf(1, width)).toFloat()

            val y =
                (((i * 173) + sin(t + i) * 20f) %
                        maxOf(1, height)).toFloat()

            val r = if (i % 4 == 0) 1.8f else 1f

            canvas.drawCircle(x, y, r, paint)
        }

        // Central rings
        stroke.color = Color.argb(22, 100, 210, 255)
        stroke.strokeWidth = 1f

        val cx = w * 0.5f
        val cy = h * 0.53f

        for (i in 1..7) {
            canvas.drawCircle(
                cx,
                cy,
                38f + i * 31f + sin(t + i) * 3f,
                stroke
            )
        }
    }

    // =========================================================
    // BASIC DRAW HELPERS
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

        canvas.drawText(value, x, y, paint)
    }

    private fun card(
        canvas: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        radius: Float = 18f
    ) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(225, 9, 17, 43)

        canvas.drawRoundRect(
            l,
            t,
            r,
            b,
            radius,
            radius,
            paint
        )

        stroke.color = Color.argb(65, 100, 180, 255)
        stroke.strokeWidth = 1f
        stroke.style = Paint.Style.STROKE

        canvas.drawRoundRect(
            l,
            t,
            r,
            b,
            radius,
            radius,
            stroke
        )
    }

    private fun gradientButton(
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
            0xff27dfff.toInt(),
            0xff8148ff.toInt(),
            Shader.TileMode.CLAMP
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

        paint.shader = null

        text(
            canvas,
            label,
            (l + r) / 2f,
            (t + b) / 2f + 5f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    private fun iconCircle(
        canvas: Canvas,
        symbol: String,
        x: Float,
        y: Float,
        radius: Float,
        color: Int
    ) {
        paint.color = Color.argb(
            45,
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )

        canvas.drawCircle(
            x,
            y,
            radius,
            paint
        )

        text(
            canvas,
            symbol,
            x,
            y + radius * 0.34f,
            radius * 0.72f,
            color,
            true,
            Paint.Align.CENTER
        )
    }

    private fun header(
        canvas: Canvas,
        title: String,
        subtitle: String
    ) {
        text(
            canvas,
            "‹",
            22f,
            topInset + 39f,
            40f,
            Color.WHITE,
            false
        )

        text(
            canvas,
            title,
            60f,
            topInset + 33f,
            23f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            60f,
            topInset + 54f,
            10f,
            0xff93a3cf.toInt()
        )
    }

    // =========================================================
    // BOTTOM NAVIGATION
    // =========================================================

    private fun navTop(h: Float): Float {
        return h - bottomInset - 82f
    }

    private fun drawNavigation(
        canvas: Canvas,
        w: Float,
        h: Float,
        selected: Int
    ) {
        val top = navTop(h)
        val bottom = h - bottomInset - 8f

        card(
            canvas,
            7f,
            top,
            w - 7f,
            bottom,
            20f
        )

        val icons = arrayOf(
            "⌂",
            "◆",
            "♫",
            "★",
            "●"
        )

        val names = arrayOf(
            "Home",
            "Games",
            "Music",
            "Daily",
            "Profile"
        )

        for (i in 0..4) {

            val x = w * (i + 0.5f) / 5f

            val active =
                i == selected

            val c =
                if (active) {
                    0xff58e9ff.toInt()
                } else {
                    0xff8390bb.toInt()
                }

            // Bigger icon
            text(
                canvas,
                icons[i],
                x,
                top + 32f,
                27f,
                c,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                names[i],
                x,
                top + 56f,
                10f,
                c,
                active,
                Paint.Align.CENTER
            )

            if (active) {
                paint.color = c

                canvas.drawCircle(
                    x,
                    top + 67f,
                    3f,
                    paint
                )
            }
        }
    }

    // =========================================================
    // HOME
    // =========================================================

    private fun drawHome(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        val y = topInset.toFloat()

        text(
            canvas,
            "MindBlow",
            16f,
            y + 31f,
            25f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "RELAX • FOCUS • REFRESH",
            16f,
            y + 48f,
            8f,
            0xff8190bb.toInt()
        )

        card(
            canvas,
            w - 76f,
            y + 9f,
            w - 10f,
            y + 39f,
            15f
        )

        text(
            canvas,
            "✦ $score",
            w - 43f,
            y + 29f,
            10f,
            0xffffd86b.toInt(),
            true,
            Paint.Align.CENTER
        )

        // Hero
        card(
            canvas,
            10f,
            y + 62f,
            w - 10f,
            y + 137f,
            18f
        )

        text(
            canvas,
            "READY FOR A RESET?",
            20f,
            y + 82f,
            8f,
            0xff63e7ff.toInt(),
            true
        )

        text(
            canvas,
            "Refresh your mind.",
            20f,
            y + 104f,
            17f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Pick a challenge and take a tiny break.",
            20f,
            y + 120f,
            9f,
            0xffa3b0d2.toInt()
        )

        gradientButton(
            canvas,
            "START QUICK RESET",
            20f,
            y + 125f,
            w - 20f,
            y + 133f
        )

        // Music
        text(
            canvas,
            "MIND REFRESHING MUSIC",
            12f,
            y + 156f,
            9f,
            0xff9caad0.toInt(),
            true
        )

        card(
            canvas,
            10f,
            y + 164f,
            w - 10f,
            y + 211f,
            15f
        )

        text(
            canvas,
            "♫",
            27f,
            y + 194f,
            25f,
            0xff67eaff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Mind Refreshing Music",
            48f,
            y + 184f,
            11f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            if (musicPlaying) "Ambient sound is playing"
            else "Music is paused",
            48f,
            y + 199f,
            8f,
            0xff8f9cc0.toInt()
        )

        drawMusicButton(
            canvas,
            w - 78f,
            y + 177f
        )

        // Games title
        text(
            canvas,
            "MIND REFRESHING GAMES",
            12f,
            y + 238f,
            10f,
            Color.WHITE,
            true
        )

        gameHomeCard(
            canvas,
            "◉",
            "Pulse Hunt",
            "Find the moving glow",
            10f,
            y + 248f,
            w / 2f - 5f,
            y + 309f,
            0
        )

        gameHomeCard(
            canvas,
            "••",
            "Memory Matrix",
            "Remember the pattern",
            w / 2f + 5f,
            y + 248f,
            w - 10f,
            y + 309f,
            1
        )

        gameHomeCard(
            canvas,
            "ϟ",
            "Quick Reaction",
            "React as fast as possible",
            10f,
            y + 315f,
            w / 2f - 5f,
            y + 376f,
            2
        )

        gameHomeCard(
            canvas,
            "●",
            "Color Match",
            "Spot the matching color",
            w / 2f + 5f,
            y + 315f,
            w - 10f,
            y + 376f,
            3
        )

        gameHomeCard(
            canvas,
            "123",
            "Number Flow",
            "Tap numbers in order",
            10f,
            y + 382f,
            w / 2f - 5f,
            y + 443f,
            4
        )

        gameHomeCard(
            canvas,
            "◎",
            "Zen Breath",
            "Slow down and breathe",
            w / 2f + 5f,
            y + 382f,
            w - 10f,
            y + 443f,
            5
        )

        drawNavigation(
            canvas,
            w,
            h,
            0
        )
    }

    private fun gameHomeCard(
        canvas: Canvas,
        icon: String,
        title: String,
        subtitle: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        game: Int
    ) {
        card(canvas, l, t, r, b, 15f)

        val colors = intArrayOf(
            0xff52e9ff.toInt(),
            0xff63d9ff.toInt(),
            0xffffd35c.toInt(),
            0xffff73a7.toInt(),
            0xff68e7ff.toInt(),
            0xff6ff0cb.toInt()
        )

        text(
            canvas,
            icon,
            l + 27f,
            t + 32f,
            20f,
            colors[game],
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            title,
            l + 48f,
            t + 25f,
            10f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            l + 48f,
            t + 39f,
            7f,
            0xff8290b4.toInt()
        )

        text(
            canvas,
            "PLAY ›",
            r - 11f,
            b - 9f,
            7f,
            0xff5de9ff.toInt(),
            true,
            Paint.Align.RIGHT
        )
    }

    // =========================================================
    // GAMES SCREEN
    // =========================================================

    private fun drawGames(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Mind Games",
            "Choose a quick mental reset"
        )

        val y = topInset + 82f

        gameLarge(
            canvas,
            "◉",
            "Pulse Hunt",
            "Find the moving glow",
            10f,
            y,
            w / 2f - 5f,
            y + 104f,
            0
        )

        gameLarge(
            canvas,
            "••",
            "Memory Matrix",
            "Remember the pattern",
            w / 2f + 5f,
            y,
            w - 10f,
            y + 104f,
            1
        )

        gameLarge(
            canvas,
            "ϟ",
            "Quick Reaction",
            "React as fast as possible",
            10f,
            y + 114f,
            w / 2f - 5f,
            y + 218f,
            2
        )

        gameLarge(
            canvas,
            "●",
            "Color Match",
            "Train visual attention",
            w / 2f + 5f,
            y + 114f,
            w - 10f,
            y + 218f,
            3
        )

        gameLarge(
            canvas,
            "123",
            "Number Flow",
            "Tap numbers in order",
            10f,
            y + 228f,
            w / 2f - 5f,
            y + 332f,
            4
        )

        gameLarge(
            canvas,
            "◎",
            "Zen Breath",
            "Slow down and breathe",
            w / 2f + 5f,
            y + 228f,
            w - 10f,
            y + 332f,
            5
        )

        card(
            canvas,
            10f,
            y + 350f,
            w - 10f,
            y + 430f,
            18f
        )

        text(
            canvas,
            "TODAY'S GOAL",
            24f,
            y + 376f,
            9f,
            0xff62e7ff.toInt(),
            true
        )

        text(
            canvas,
            "Play 3 different games",
            24f,
            y + 400f,
            15f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Small breaks can reset your attention.",
            24f,
            y + 418f,
            9f,
            0xff8d9bc1.toInt()
        )

        drawNavigation(
            canvas,
            w,
            h,
            1
        )
    }

    private fun gameLarge(
        canvas: Canvas,
        icon: String,
        title: String,
        subtitle: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        game: Int
    ) {
        card(canvas, l, t, r, b, 18f)

        val colors = intArrayOf(
            0xff55e7ff.toInt(),
            0xff6be1ff.toInt(),
            0xffffd35d.toInt(),
            0xffff6f9c.toInt(),
            0xff68e7ff.toInt(),
            0xff68f1c4.toInt()
        )

        iconCircle(
            canvas,
            icon,
            l + 43f,
            t + 48f,
            28f,
            colors[game]
        )

        text(
            canvas,
            title,
            l + 80f,
            t + 43f,
            13f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            l + 80f,
            t + 62f,
            8f,
            0xff8998bd.toInt()
        )

        text(
            canvas,
            "PLAY",
            r - 16f,
            b - 14f,
            9f,
            0xff57e8ff.toInt(),
            true,
            Paint.Align.RIGHT
        )
    }

    // =========================================================
    // MUSIC
    // =========================================================

    private fun drawMusicButton(
        canvas: Canvas,
        x: Float,
        y: Float
    ) {
        val left = x - 57f
        val right = x + 57f

        paint.color =
            if (musicPlaying) {
                0xff4bdcf5.toInt()
            } else {
                0xff26304e.toInt()
            }

        canvas.drawRoundRect(
            left,
            y - 15f,
            right,
            y + 15f,
            15f,
            15f,
            paint
        )

        text(
            canvas,
            if (musicPlaying) "MUSIC ON" else "MUSIC OFF",
            x,
            y + 4f,
            9f,
            if (musicPlaying) Color.WHITE else 0xff9ba8c8.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    private fun drawMusic(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Mind Refreshing",
            "Ambient sounds for your break"
        )

        val y = topInset + 90f

        card(
            canvas,
            12f,
            y,
            w - 12f,
            y + 108f,
            20f
        )

        text(
            canvas,
            "♫",
            w / 2f,
            y + 57f,
            54f,
            0xff64e9ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            if (musicPlaying) "Ambient music is playing"
            else "Ambient music is paused",
            w / 2f,
            y + 82f,
            14f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        drawMusicButton(
            canvas,
            w / 2f,
            y + 135f
        )

        val centerY = y + 250f

        for (i in 0 until 18) {
            val angle =
                animation * (0.35f + i * 0.01f) +
                        i * 0.35f

            val radius = 45f + i * 9f

            val x =
                w / 2f + cos(angle) * radius

            val yy =
                centerY + sin(angle) * radius

            paint.color =
                Color.argb(
                    80,
                    70 + i * 5,
                    210,
                    255
                )

            canvas.drawCircle(
                x.toFloat(),
                yy.toFloat(),
                3f + (i % 3),
                paint
            )
        }

        text(
            canvas,
            "Mind Refreshing Mode",
            w / 2f,
            y + 420f,
            20f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Soft ambient tones designed for quiet focus.",
            w / 2f,
            y + 446f,
            10f,
            0xff8f9dc0.toInt(),
            false,
            Paint.Align.CENTER
        )

        drawNavigation(
            canvas,
            w,
            h,
            2
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
            "A small challenge for today"
        )

        val y = topInset + 86f

        card(
            canvas,
            12f,
            y,
            w - 12f,
            y + 120f,
            20f
        )

        text(
            canvas,
            "TODAY",
            27f,
            y + 28f,
            9f,
            0xff63e9ff.toInt(),
            true
        )

        text(
            canvas,
            "3 quick rounds",
            27f,
            y + 58f,
            22f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Pulse Hunt • Color Match • Zen Breath",
            27f,
            y + 82f,
            10f,
            0xff9ba9ca.toInt()
        )

        gradientButton(
            canvas,
            "START DAILY RESET",
            27f,
            y + 91f,
            w - 27f,
            y + 112f
        )

        text(
            canvas,
            "CURRENT STREAK",
            20f,
            y + 162f,
            9f,
            0xff8291b7.toInt(),
            true
        )

        text(
            canvas,
            "$streak DAYS",
            20f,
            y + 195f,
            30f,
            0xffffd86b.toInt(),
            true
        )

        text(
            canvas,
            "Score: $score XP",
            20f,
            y + 220f,
            11f,
            0xffa5b1d1.toInt()
        )

        drawNavigation(
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
            "Your Progress",
            "Keep your mind moving"
        )

        val y = topInset + 88f

        card(
            canvas,
            12f,
            y,
            w - 12f,
            y + 150f,
            22f
        )

        iconCircle(
            canvas,
            "✦",
            60f,
            y + 65f,
            35f,
            0xff62e8ff.toInt()
        )

        text(
            canvas,
            "Mind Explorer",
            112f,
            y + 52f,
            18f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Level ${score / 100 + 1}",
            112f,
            y + 73f,
            10f,
            0xff8f9dc0.toInt()
        )

        text(
            canvas,
            "$score XP",
            112f,
            y + 103f,
            21f,
            0xffffd76b.toInt(),
            true
        )

        text(
            canvas,
            "$streak day streak",
            112f,
            y + 123f,
            9f,
            0xff7f8fb7.toInt()
        )

        profileStat(
            canvas,
            "Games Played",
            "${prefs.getInt("games", 0)}",
            12f,
            y + 175f,
            w / 2f - 8f,
            y + 245f
        )

        profileStat(
            canvas,
            "Best Reaction",
            if (reactionBest == Long.MAX_VALUE) "--"
            else "${reactionBest}ms",
            w / 2f + 8f,
            y + 175f,
            w - 12f,
            y + 245f
        )

        card(
            canvas,
            12f,
            y + 265f,
            w - 12f,
            y + 340f,
            18f
        )

        text(
            canvas,
            "MUSIC",
            27f,
            y + 292f,
            9f,
            0xff7f90b8.toInt(),
            true
        )

        text(
            canvas,
            if (musicPlaying) "Ambient music enabled"
            else "Ambient music disabled",
            27f,
            y + 318f,
            13f,
            Color.WHITE,
            true
        )

        drawMusicButton(
            canvas,
            w - 72f,
            y + 309f
        )

        drawNavigation(
            canvas,
            w,
            h,
            4
        )
    }

    private fun profileStat(
        canvas: Canvas,
        title: String,
        value: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {
        card(canvas, l, t, r, b, 17f)

        text(
            canvas,
            title,
            l + 14f,
            t + 25f,
            8f,
            0xff8492b8.toInt(),
            true
        )

        text(
            canvas,
            value,
            l + 14f,
            t + 53f,
            19f,
            Color.WHITE,
            true
        )
    }

    // =========================================================
    // PULSE HUNT
    // =========================================================

    private fun startPulse() {
        pulseRound = 0
        pulseRunning = true
        pulseStartedAt = System.currentTimeMillis()
        newPulseTarget()
    }

    private fun newPulseTarget() {
        val w = width.toFloat()
        val safeTop = topInset + 100f
        val safeBottom = height - bottomInset - 120f

        pulseX = Random.nextFloat() *
                (w - 100f) + 50f

        pulseY = Random.nextFloat() *
                maxOf(150f, safeBottom - safeTop) +
                safeTop

        pulseRadius =
            44f + Random.nextFloat() * 20f
    }

    private fun drawPulse(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Pulse Hunt",
            "Tap the moving glow"
        )

        val remaining =
            maxOf(
                0L,
                pulseTimeLimit -
                        (System.currentTimeMillis() - pulseStartedAt)
            )

        text(
            canvas,
            "${remaining / 1000}s",
            w - 25f,
            topInset + 38f,
            17f,
            0xffffd86b.toInt(),
            true,
            Paint.Align.RIGHT
        )

        // Glow
        for (i in 4 downTo 1) {
            paint.color =
                Color.argb(
                    20 + i * 9,
                    70,
                    230,
                    255
                )

            canvas.drawCircle(
                pulseX,
                pulseY,
                pulseRadius + i * 12f +
                        sin(animation * 4f) * 4f,
                paint
            )
        }

        paint.color = 0xff5be9ff.toInt()

        canvas.drawCircle(
            pulseX,
            pulseY,
            pulseRadius,
            paint
        )

        text(
            canvas,
            "TAP",
            pulseX,
            pulseY + 6f,
            12f,
            Color.rgb(3, 20, 40),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Hits: $pulseRound",
            w / 2f,
            h - bottomInset - 105f,
            17f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Find the glow before it moves.",
            w / 2f,
            h - bottomInset - 78f,
            10f,
            0xff8d9bbe.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // MEMORY MATRIX
    // =========================================================

    private fun startMemory() {
        memoryRound = 1
        memorySequence.clear()
        memoryInput.clear()
        memoryShowing = true
        memoryShowIndex = 0
        memoryNextAt = System.currentTimeMillis() + 700L
        addMemoryStep()
    }

    private fun addMemoryStep() {
        memorySequence.add(
            Random.nextInt(9)
        )

        memoryInput.clear()
        memoryShowing = true
        memoryShowIndex = 0
        memoryNextAt = System.currentTimeMillis() + 700L
    }

    private fun drawMemory(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Memory Matrix",
            "Remember the glowing pattern"
        )

        text(
            canvas,
            "ROUND $memoryRound",
            w - 20f,
            topInset + 38f,
            10f,
            0xffffd86b.toInt(),
            true,
            Paint.Align.RIGHT
        )

        val size = min(
            w - 50f,
            330f
        )

        val left = (w - size) / 2f
        val top = topInset + 95f
        val gap = 10f
        val cell = (size - gap * 2f) / 3f

        for (i in 0 until 9) {
            val row = i / 3
            val col = i % 3

            val l = left + col * (cell + gap)
            val t = top + row * (cell + gap)
            val r = l + cell
            val b = t + cell

            val highlighted =
                memoryShowing &&
                        memoryShowIndex < memorySequence.size &&
                        memorySequence[memoryShowIndex] == i

            val selected =
                memoryInput.contains(i)

            paint.color =
                when {
                    highlighted ->
                        0xff52e8ff.toInt()

                    selected ->
                        0xff795cff.toInt()

                    else ->
                        0xff101936.toInt()
                }

            canvas.drawRoundRect(
                l,
                t,
                r,
                b,
                17f,
                17f,
                paint
            )

            stroke.color =
                if (highlighted)
                    0xff8df3ff.toInt()
                else
                    Color.argb(55, 110, 170, 230)

            stroke.strokeWidth = 1f

            canvas.drawRoundRect(
                l,
                t,
                r,
                b,
                17f,
                17f,
                stroke
            )

            text(
                canvas,
                "${i + 1}",
                (l + r) / 2f,
                (t + b) / 2f + 6f,
                16f,
                if (highlighted)
                    Color.rgb(4, 25, 45)
                else
                    0xff9eacd0.toInt(),
                true,
                Paint.Align.CENTER
            )
        }

        text(
            canvas,
            if (memoryShowing)
                "WATCH..."
            else
                "REPEAT THE PATTERN",
            w / 2f,
            top + size + 38f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Pattern length: ${memorySequence.size}",
            w / 2f,
            top + size + 61f,
            10f,
            0xff8998bd.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // QUICK REACTION
    // =========================================================

    private fun startReaction() {
        reactionState = 1
        reactionFalseStart = false
        reactionWaitUntil =
            System.currentTimeMillis() +
                    Random.nextLong(1300L, 3300L)
    }

    private fun drawReaction(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Quick Reaction",
            "Wait for green, then tap"
        )

        val cx = w / 2f
        val cy = topInset +
                (h - topInset - bottomInset) * 0.48f

        val color =
            when (reactionState) {
                2 -> 0xff54ee91.toInt()
                3 -> 0xffff5c76.toInt()
                else -> 0xffffc957.toInt()
            }

        for (i in 4 downTo 1) {
            paint.color =
                Color.argb(
                    18 + i * 8,
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color)
                )

            canvas.drawCircle(
                cx,
                cy,
                70f + i * 20f,
                paint
            )
        }

        paint.color = color

        canvas.drawCircle(
            cx,
            cy,
            72f,
            paint
        )

        val label =
            when (reactionState) {
                0 -> "TAP TO START"
                1 -> "WAIT..."
                2 -> "TAP!"
                3 -> "TOO EARLY"
                4 -> "GOOD!"
                else -> "TAP"
            }

        text(
            canvas,
            label,
            cx,
            cy + 7f,
            14f,
            Color.rgb(5, 15, 35),
            true,
            Paint.Align.CENTER
        )

        if (reactionBest != Long.MAX_VALUE) {
            text(
                canvas,
                "Best: ${reactionBest} ms",
                cx,
                cy + 145f,
                14f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }
    }

    // =========================================================
    // COLOR MATCH
    // =========================================================

    private fun startColors() {
        colorCorrect = 0
        colorRound = 0
        nextColorRound()
    }

    private fun nextColorRound() {
        colorWord =
            Random.nextInt(colorNames.size)

        colorInk =
            Random.nextInt(colorValues.size)

        colorRound++
    }

    private fun drawColors(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Color Match",
            "Choose the ink color, not the word"
        )

        text(
            canvas,
            "$colorCorrect / 10",
            w - 20f,
            topInset + 38f,
            12f,
            0xffffd86b.toInt(),
            true,
            Paint.Align.RIGHT
        )

        card(
            canvas,
            20f,
            topInset + 105f,
            w - 20f,
            topInset + 225f,
            22f
        )

        text(
            canvas,
            colorNames[colorWord],
            w / 2f,
            topInset + 175f,
            40f,
            colorValues[colorInk],
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Which COLOR is the word printed in?",
            w / 2f,
            topInset + 252f,
            12f,
            0xffa5b1d2.toInt(),
            false,
            Paint.Align.CENTER
        )

        for (i in 0 until 6) {
            val col = i % 2
            val row = i / 2

            val l = 20f + col * (w - 50f) / 2f
            val r = w - 20f - (1 - col) * (w - 50f) / 2f
            val t = topInset + 275f + row * 63f
            val b = t + 52f

            card(
                canvas,
                l,
                t,
                r,
                b,
                15f
            )

            paint.color = colorValues[i]

            canvas.drawCircle(
                l + 28f,
                (t + b) / 2f,
                12f,
                paint
            )

            text(
                canvas,
                colorNames[i],
                l + 52f,
                (t + b) / 2f + 5f,
                11f,
                Color.WHITE,
                true
            )
        }
    }

    // =========================================================
    // NUMBER FLOW
    // =========================================================

    private fun startNumber() {
        numberNext = 1
        numberCorrect = 0
        numberRound = 1
        numberGrid = (1..9).shuffled().toMutableList()
    }

    private fun drawNumber(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Number Flow",
            "Tap numbers from 1 to 9"
        )

        text(
            canvas,
            "$numberCorrect / 9",
            w - 20f,
            topInset + 38f,
            12f,
            0xffffd86b.toInt(),
            true,
            Paint.Align.RIGHT
        )

        val size = min(
            w - 50f,
            330f
        )

        val left = (w - size) / 2f
        val top = topInset + 105f
        val gap = 10f
        val cell = (size - gap * 2f) / 3f

        for (i in 0 until 9) {
            val row = i / 3
            val col = i % 3

            val l = left + col * (cell + gap)
            val t = top + row * (cell + gap)
            val r = l + cell
            val b = t + cell

            card(
                canvas,
                l,
                t,
                r,
                b,
                18f
            )

            val value = numberGrid[i]

            text(
                canvas,
                value.toString(),
                (l + r) / 2f,
                (t + b) / 2f + 9f,
                25f,
                if (value < numberNext)
                    0xff53617e.toInt()
                else
                    0xff63e8ff.toInt(),
                true,
                Paint.Align.CENTER
            )
        }

        text(
            canvas,
            "NEXT: $numberNext",
            w / 2f,
            top + size + 42f,
            16f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // ZEN BREATH
    // =========================================================

    private fun startBreath() {
        breathRunning = true
        breathStart = System.currentTimeMillis()
        breathPhase = 0
    }

    private fun drawBreathe(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Zen Breath",
            "Slow down and breathe"
        )

        val elapsed =
            if (breathRunning)
                System.currentTimeMillis() -
                        breathStart
            else 0L

        val cycle =
            (elapsed % 12000L).toFloat()

        val phase =
            when {
                cycle < 4000f -> 0
                cycle < 6000f -> 1
                cycle < 10000f -> 2
                else -> 3
            }

        val progress =
            when (phase) {
                0 -> cycle / 4000f
                1 -> (cycle - 4000f) / 2000f
                2 -> (cycle - 6000f) / 4000f
                else -> (cycle - 10000f) / 2000f
            }

        val scale =
            when (phase) {
                0 -> 0.65f + progress * 0.35f
                1 -> 1f
                2 -> 1f - progress * 0.35f
                else -> 0.65f
            }

        val cx = w / 2f
        val cy = topInset +
                (h - topInset - bottomInset) * 0.47f

        for (i in 5 downTo 1) {
            paint.color =
                Color.argb(
                    12 + i * 7,
                    80,
                    235,
                    205
                )

            canvas.drawCircle(
                cx,
                cy,
                72f * scale + i * 18f,
                paint
            )
        }

        paint.color = 0xff58e6ca.toInt()

        canvas.drawCircle(
            cx,
            cy,
            75f * scale,
            paint
        )

        val label =
            if (!breathRunning)
                "START"
            else
                when (phase) {
                    0 -> "BREATHE IN"
                    1 -> "HOLD"
                    2 -> "BREATHE OUT"
                    else -> "REST"
                }

        text(
            canvas,
            label,
            cx,
            cy + 7f,
            14f,
            Color.rgb(5, 30, 30),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Follow the circle",
            cx,
            cy + 145f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "4 seconds in • 2 hold • 4 seconds out",
            cx,
            cy + 169f,
            9f,
            0xff8998b9.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // TOAST
    // =========================================================

    private fun showToastMessage(message: String) {
        toast = message
        toastUntil = System.currentTimeMillis() + 1800L
        invalidate()
    }

    private fun drawToast(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        val l = 25f
        val r = w - 25f
        val b = h - bottomInset - 95f
        val t = b - 48f

        paint.color = Color.argb(
            235,
            15,
            25,
            52
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

        text(
            canvas,
            toast,
            w / 2f,
            t + 30f,
            11f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // GAME UPDATES
    // =========================================================

    private fun updateGames() {

        val now = System.currentTimeMillis()

        // Pulse
        if (screen == AppScreen.PULSE && pulseRunning) {
            if (now - pulseStartedAt >= pulseTimeLimit) {
                pulseRunning = false
                showToastMessage(
                    "Pulse Hunt complete: +${pulseRound * 5} XP"
                )
            }
        }

        // Memory
        if (screen == AppScreen.MEMORY && memoryShowing) {
            if (now >= memoryNextAt) {
                memoryShowIndex++

                if (memoryShowIndex >= memorySequence.size) {
                    memoryShowing = false
                } else {
                    memoryNextAt = now + 600L
                }
            }
        }

        // Reaction
        if (screen == AppScreen.REACTION &&
            reactionState == 1 &&
            now >= reactionWaitUntil
        ) {
            reactionState = 2
            reactionStartedAt = now
        }

        // Breath
        if (screen == AppScreen.BREATHE &&
            breathRunning
        ) {
            // Animation is calculated in drawBreathe.
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

        // Bottom navigation gets priority.
        val navY = navTop(h)

        if (y >= navY && y <= h - bottomInset) {

            val index =
                ((x / w) * 5f)
                    .toInt()
                    .coerceIn(0, 4)

            when (index) {
                0 -> screen = AppScreen.HOME
                1 -> screen = AppScreen.GAMES
                2 -> screen = AppScreen.MUSIC
                3 -> screen = AppScreen.DAILY
                4 -> screen = AppScreen.PROFILE
            }

            invalidate()
            return true
        }

        when (screen) {

            AppScreen.HOME ->
                handleHomeTouch(x, y, w, h)

            AppScreen.GAMES ->
                handleGamesTouch(x, y, w, h)

            AppScreen.MUSIC ->
                handleMusicTouch(x, y, w, h)

            AppScreen.DAILY ->
                handleDailyTouch(x, y, w, h)

            AppScreen.PROFILE ->
                handleProfileTouch(x, y, w, h)

            AppScreen.PULSE ->
                handlePulseTouch(x, y)

            AppScreen.MEMORY ->
                handleMemoryTouch(x, y)

            AppScreen.REACTION ->
                handleReactionTouch(x, y)

            AppScreen.COLORS ->
                handleColorsTouch(x, y)

            AppScreen.NUMBER ->
                handleNumberTouch(x, y)

            AppScreen.BREATHE ->
                handleBreatheTouch(x, y)
        }

        return true
    }

    // =========================================================
    // HOME TOUCH
    // =========================================================

    private fun handleHomeTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        val yy = y - topInset

        // Music button
        if (yy in 164f..211f &&
            x > w - 150f
        ) {
            toggleMusic()
            return
        }

        // Quick reset
        if (yy in 125f..140f) {
            openPulse()
            return
        }

        if (yy in 248f..309f) {
            if (x < w / 2f) openPulse()
            else openMemory()
            return
        }

        if (yy in 315f..376f) {
            if (x < w / 2f) openReaction()
            else openColors()
            return
        }

        if (yy in 382f..443f) {
            if (x < w / 2f) openNumber()
            else openBreathe()
        }
    }

    // =========================================================
    // GAMES TOUCH
    // =========================================================

    private fun handleGamesTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        val yy = y - topInset

        if (yy in 82f..186f) {
            if (x < w / 2f) openPulse()
            else openMemory()
        }

        else if (yy in 196f..300f) {
            if (x < w / 2f) openReaction()
            else openColors()
        }

        else if (yy in 310f..414f) {
            if (x < w / 2f) openNumber()
            else openBreathe()
        }
    }

    // =========================================================
    // MUSIC TOUCH
    // =========================================================

    private fun handleMusicTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        val yy = y - topInset

        if (yy in 105f..175f) {
            toggleMusic()
        }
    }

    // =========================================================
    // DAILY TOUCH
    // =========================================================

    private fun handleDailyTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        val yy = y - topInset

        if (yy in 170f..230f) {
            openPulse()
        }
    }

    // =========================================================
    // PROFILE TOUCH
    // =========================================================

    private fun handleProfileTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        val yy = y - topInset

        if (yy in 250f..350f) {
            toggleMusic()
        }
    }

    // =========================================================
    // PULSE TOUCH
    // =========================================================

    private fun handlePulseTouch(
        x: Float,
        y: Float
    ) {
        if (!pulseRunning) {
            startPulse()
            return
        }

        val distance =
            kotlin.math.sqrt(
                (x - pulseX) * (x - pulseX) +
                        (y - pulseY) * (y - pulseY)
            )

        if (distance <= pulseRadius + 20f) {
            pulseRound++

            addScore(5)

            newPulseTarget()
        }
    }

    // =========================================================
    // MEMORY TOUCH
    // =========================================================

    private fun handleMemoryTouch(
        x: Float,
        y: Float
    ) {
        if (memoryShowing) {
            return
        }

        val size = min(
            width.toFloat() - 50f,
            330f
        )

        val left = (width - size) / 2f
        val top = topInset + 95f
        val gap = 10f
        val cell = (size - gap * 2f) / 3f

        for (i in 0 until 9) {

            val row = i / 3
            val col = i % 3

            val l = left + col * (cell + gap)
            val t = top + row * (cell + gap)
            val r = l + cell
            val b = t + cell

            if (x >= l && x <= r &&
                y >= t && y <= b
            ) {
                if (!memoryInput.contains(i)) {
                    memoryInput.add(i)
                }

                if (memoryInput.size ==
                    memorySequence.size
                ) {

                    if (memoryInput ==
                        memorySequence
                    ) {
                        addScore(
                            10 + memoryRound * 3
                        )

                        memoryRound++

                        if (memoryRound > 8) {
                            showToastMessage(
                                "Memory complete! +50 XP"
                            )

                            addScore(50)
                            startMemory()
                        } else {
                            addMemoryStep()
                        }

                    } else {
                        showToastMessage(
                            "Pattern missed — try again"
                        )

                        memoryRound = 1
                        memorySequence.clear()
                        addMemoryStep()
                    }
                }
            }
        }
    }

    // =========================================================
    // REACTION TOUCH
    // =========================================================

    private fun handleReactionTouch(
        x: Float,
        y: Float
    ) {
        when (reactionState) {

            0 -> {
                startReaction()
            }

            1 -> {
                reactionState = 3
                reactionFalseStart = true
                showToastMessage(
                    "Too early!"
                )
            }

            2 -> {
                val result =
                    System.currentTimeMillis() -
                            reactionStartedAt

                reactionBest =
                    minOf(
                        reactionBest,
                        result
                    )

                addScore(
                    maxOf(
                        5,
                        40 - (result / 10).toInt()
                    )
                )

                reactionState = 4

                showToastMessage(
                    "${result}ms reaction! +XP"
                )
            }

            3, 4 -> {
                startReaction()
            }
        }
    }

    // =========================================================
    // COLORS TOUCH
    // =========================================================

    private fun handleColorsTouch(
        x: Float,
        y: Float
    ) {
        val w = width.toFloat()

        for (i in 0 until 6) {
            val col = i % 2
            val row = i / 2

            val l = 20f + col * (w - 50f) / 2f
            val r = w - 20f - (1 - col) * (w - 50f) / 2f
            val t = topInset + 275f + row * 63f
            val b = t + 52f

            if (x >= l && x <= r &&
                y >= t && y <= b
            ) {

                if (i == colorInk) {
                    colorCorrect++
                    addScore(8)

                    if (colorCorrect >= 10) {
                        addScore(30)

                        showToastMessage(
                            "Color Match complete!"
                        )

                        startColors()
                    } else {
                        nextColorRound()
                    }

                } else {
                    showToastMessage(
                        "Look at the ink color!"
                    )

                    nextColorRound()
                }

                return
            }
        }
    }

    // =========================================================
    // NUMBER TOUCH
    // =========================================================

    private fun handleNumberTouch(
        x: Float,
        y: Float
    ) {
        val size = min(
            width.toFloat() - 50f,
            330f
        )

        val left = (width - size) / 2f
        val top = topInset + 105f
        val gap = 10f
        val cell = (size - gap * 2f) / 3f

        for (i in 0 until 9) {

            val row = i / 3
            val col = i % 3

            val l = left + col * (cell + gap)
            val t = top + row * (cell + gap)
            val r = l + cell
            val b = t + cell

            if (x >= l && x <= r &&
                y >= t && y <= b
            ) {

                val value = numberGrid[i]

                if (value == numberNext) {

                    numberCorrect++
                    numberNext++

                    addScore(4)

                    if (numberNext > 9) {
                        addScore(25)

                        showToastMessage(
                            "Number Flow complete!"
                        )

                        startNumber()
                    }

                } else {
                    showToastMessage(
                        "Next number is $numberNext"
                    )
                }

                return
            }
        }
    }

    // =========================================================
    // BREATH TOUCH
    // =========================================================

    private fun handleBreatheTouch(
        x: Float,
        y: Float
    ) {
        if (!breathRunning) {
            startBreath()
        } else {
            breathRunning = false
            addScore(10)

            showToastMessage(
                "Nice reset. +10 XP"
            )
        }
    }

    // =========================================================
    // OPEN GAMES
    // =========================================================

    private fun openPulse() {
        screen = AppScreen.PULSE
        startPulse()
        invalidate()
    }

    private fun openMemory() {
        screen = AppScreen.MEMORY
        startMemory()
        invalidate()
    }

    private fun openReaction() {
        screen = AppScreen.REACTION
        reactionState = 0
        invalidate()
    }

    private fun openColors() {
        screen = AppScreen.COLORS
        startColors()
        invalidate()
    }

    private fun openNumber() {
        screen = AppScreen.NUMBER
        startNumber()
        invalidate()
    }

    private fun openBreathe() {
        screen = AppScreen.BREATHE
        breathRunning = false
        invalidate()
    }

    // =========================================================
    // SCORE
    // =========================================================

    private fun addScore(points: Int) {
        score += points

        prefs.edit()
            .putInt("score", score)
            .putInt(
                "games",
                prefs.getInt("games", 0) + 1
            )
            .apply()
    }

    // =========================================================
    // MUSIC CONTROL
    // =========================================================

    private fun toggleMusic() {

        musicEnabled = !musicEnabled

        prefs.edit()
            .putBoolean(
                "music",
                musicEnabled
            )
            .apply()

        if (musicEnabled) {
            startMusic()
            showToastMessage(
                "Mind refreshing music ON"
            )
        } else {
            stopMusic()
            showToastMessage(
                "Music OFF"
            )
        }

        invalidate()
    }

    private fun startMusic() {

        if (musicPlaying) {
            return
        }

        try {

            val minBuffer =
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

            if (minBuffer <= 0) {
                return
            }

            val bufferSize =
                maxOf(
                    minBuffer * 2,
                    4096
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

            audioTrack = track

            track.setVolume(0.20f)

            track.play()

            musicPlaying = true
            musicThreadRunning = true

            musicThread =
                Thread {

                    val chunkSize = 2048
                    val buffer =
                        ShortArray(chunkSize)

                    var sampleIndex = 0L

                    while (musicThreadRunning) {

                        for (i in buffer.indices) {

                            val t =
                                sampleIndex.toDouble() /
                                        sampleRate.toDouble()

                            /*
                             * Soft ambient chord:
                             *
                             * 220Hz
                             * 277.18Hz
                             * 329.63Hz
                             *
                             * Very low volume + slow modulation
                             * makes it suitable as a simple
                             * built-in ambient background.
                             */

                            val wave1 =
                                sin(
                                    2.0 * PI *
                                            220.0 * t
                                )

                            val wave2 =
                                sin(
                                    2.0 * PI *
                                            277.18 * t
                                )

                            val wave3 =
                                sin(
                                    2.0 * PI *
                                            329.63 * t
                                )

                            val modulation =
                                0.5 +
                                        0.5 *
                                        sin(
                                            2.0 * PI *
                                                    0.075 * t
                                        )

                            val slow =
                                0.5 +
                                        0.5 *
                                        sin(
                                            2.0 * PI *
                                                    0.035 * t
                                        )

                            val value =
                                (
                                        wave1 * 0.42 +
                                                wave2 * 0.31 +
                                                wave3 * 0.22
                                        ) *
                                        modulation *
                                        slow *
                                        0.20

                            buffer[i] =
                                (value * 32767.0)
                                    .toInt()
                                    .coerceIn(
                                        Short.MIN_VALUE.toInt(),
                                        Short.MAX_VALUE.toInt()
                                    )
                                    .toShort()

                            sampleIndex++
                        }

                        try {
                            track.write(
                                buffer,
                                0,
                                buffer.size
                            )
                        } catch (_: Exception) {
                            break
                        }
                    }
                }

            musicThread?.start()

        } catch (_: Exception) {
            musicPlaying = false
        }
    }

    private fun stopMusic() {

        musicThreadRunning = false
        musicPlaying = false

        try {
            musicThread?.interrupt()
        } catch (_: Exception) {
        }

        musicThread = null

        try {
            audioTrack?.pause()
        } catch (_: Exception) {
        }

        try {
            audioTrack?.flush()
        } catch (_: Exception) {
        }

        try {
            audioTrack?.release()
        } catch (_: Exception) {
        }

        audioTrack = null
    }

    fun resumeMusic() {
        if (musicEnabled && !musicPlaying) {
            startMusic()
        }
    }

    fun pauseMusic() {
        /*
         * Keep the user's music preference.
         * Stop the audio while Activity is not visible.
         */
        stopMusic()
    }

    // =========================================================
    // BACK
    // =========================================================

    fun goBack(): Boolean {

        when (screen) {

            AppScreen.HOME -> {
                return false
            }

            AppScreen.GAMES,
            AppScreen.MUSIC,
            AppScreen.DAILY,
            AppScreen.PROFILE -> {
                screen = AppScreen.HOME
                invalidate()
                return true
            }

            AppScreen.PULSE,
            AppScreen.MEMORY,
            AppScreen.REACTION,
            AppScreen.COLORS,
            AppScreen.NUMBER,
            AppScreen.BREATHE -> {
                screen = AppScreen.GAMES
                invalidate()
                return true
            }
        }
    }
}
