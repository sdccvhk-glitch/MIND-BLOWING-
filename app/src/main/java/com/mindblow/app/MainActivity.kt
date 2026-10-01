package com.mindblow.app

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {

    private lateinit var mindView: MindBlowView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(5, 7, 24)
        window.navigationBarColor = Color.rgb(5, 7, 24)

        mindView = MindBlowView(this)
        setContentView(mindView)
    }

    override fun onDestroy() {
        mindView.releaseMusic()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!mindView.goBack()) {
            super.onBackPressed()
        }
    }
}

private enum class Screen {
    HOME,
    GAMES,
    MUSIC,
    PROFILE,
    GAME
}

private enum class GameType {
    PULSE,
    COLOR,
    MEMORY,
    NUMBER,
    REACTION,
    BREATH
}

private class MindBlowView(
    private val ctx: Context
) : View(ctx) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs =
        ctx.getSharedPreferences("mindblow_v2", Context.MODE_PRIVATE)

    private var screen = Screen.HOME
    private var selectedGame = GameType.PULSE

    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)

    private var animation = 0f
    private var message = ""
    private var messageUntil = 0L

    private val vibrator =
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // ---------------------------------------------------------
    // MUSIC
    // ---------------------------------------------------------

    private var musicOn = prefs.getBoolean("music", false)
    private var ambientMusic: AmbientMusic? = null

    // ---------------------------------------------------------
    // GAME STATE
    // ---------------------------------------------------------

    private var gameStarted = false
    private var gameScore = 0

    // Pulse Hunt
    private var pulseX = 0f
    private var pulseY = 0f
    private var pulseRadius = 45f
    private var pulseRound = 0

    // Color Match
    private var colorCorrect = 0
    private var colorOptions = IntArray(4)
    private var colorTarget = 0

    // Memory
    private var memoryPattern = mutableListOf<Int>()
    private var memoryUser = mutableListOf<Int>()
    private var memoryShowing = true
    private var memoryShowUntil = 0L
    private var memoryLevel = 1

    // Number Flow
    private var numberPositions = Array(9) { PointF() }
    private var nextNumber = 1
    private var numberStart = 0L

    // Reaction
    private var reactionReady = false
    private var reactionStartedAt = 0L
    private var reactionMessage = "WAIT..."
    private var reactionDelayUntil = 0L

    // Breath
    private var breathRunning = false
    private var breathStartedAt = 0L

    init {
        isFocusable = true

        if (musicOn) {
            startMusic()
        }

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
            Screen.HOME -> drawHome(canvas, w, h)
            Screen.GAMES -> drawGames(canvas, w, h)
            Screen.MUSIC -> drawMusic(canvas, w, h)
            Screen.PROFILE -> drawProfile(canvas, w, h)
            Screen.GAME -> drawGame(canvas, w, h)
        }

        if (messageUntil > System.currentTimeMillis()) {
            drawToast(canvas, w, h)
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
            Color.rgb(38, 7, 62),
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val t = animation

        // Cyan orb
        paint.color = Color.argb(35, 0, 220, 255)

        canvas.drawCircle(
            w * 0.12f + sin(t) * 30f,
            h * 0.22f + cos(t) * 25f,
            110f,
            paint
        )

        // Purple orb
        paint.color = Color.argb(32, 160, 60, 255)

        canvas.drawCircle(
            w * 0.88f + cos(t * .8f) * 35f,
            h * 0.42f + sin(t) * 30f,
            120f,
            paint
        )

        // Blue lower orb
        paint.color = Color.argb(25, 40, 130, 255)

        canvas.drawCircle(
            w * .52f + sin(t * .6f) * 35f,
            h * .82f,
            115f,
            paint
        )

        // Center energy rings
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(24, 100, 220, 255)

        val centerX = w / 2f
        val centerY = h * .54f

        for (i in 0 until 7) {
            canvas.drawCircle(
                centerX,
                centerY,
                45f + i * 38f + sin(t + i) * 4f,
                paint
            )
        }

        // stars
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(100, 110, 210, 255)

        for (i in 0 until 28) {
            val sx =
                ((i * 137) % maxOf(1, width)).toFloat()

            val sy =
                ((i * 239 + 100) % maxOf(1, height)).toFloat()

            val r =
                1f + ((i * 7) % 3)

            canvas.drawCircle(
                sx,
                sy,
                r,
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
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface =
            Typeface.create(
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
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(
            232,
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
            65,
            95,
            150,
            230
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
            Color.rgb(20, 215, 255),
            Color.rgb(130, 60, 255),
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

    // =========================================================
    // LARGE ICON
    // =========================================================

    private fun icon(
        canvas: Canvas,
        type: GameType,
        cx: Float,
        cy: Float,
        size: Float
    ) {
        paint.style = Paint.Style.FILL

        when (type) {

            GameType.PULSE -> {
                paint.color = 0xff45e7ff.toInt()

                canvas.drawCircle(
                    cx,
                    cy,
                    size * .35f,
                    paint
                )

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f

                canvas.drawCircle(
                    cx,
                    cy,
                    size * .65f,
                    paint
                )

                paint.style = Paint.Style.FILL
            }

            GameType.COLOR -> {
                val colors = intArrayOf(
                    0xffff5577.toInt(),
                    0xff55ddff.toInt(),
                    0xffffd34d.toInt(),
                    0xff9c6cff.toInt()
                )

                for (i in 0 until 4) {
                    paint.color = colors[i]

                    val a = i * Math.PI / 2.0

                    canvas.drawCircle(
                        cx + cos(a).toFloat() * size * .38f,
                        cy + sin(a).toFloat() * size * .38f,
                        size * .22f,
                        paint
                    )
                }
            }

            GameType.MEMORY -> {
                paint.color = 0xff58e5ff.toInt()

                for (row in 0 until 2) {
                    for (col in 0 until 2) {
                        val x =
                            cx + (col - .5f) * size * .55f

                        val y =
                            cy + (row - .5f) * size * .55f

                        canvas.drawRoundRect(
                            x - size * .16f,
                            y - size * .16f,
                            x + size * .16f,
                            y + size * .16f,
                            7f,
                            7f,
                            paint
                        )
                    }
                }
            }

            GameType.NUMBER -> {
                text(
                    canvas,
                    "123",
                    cx,
                    cy + size * .18f,
                    size * .55f,
                    0xff59e5ff.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            }

            GameType.REACTION -> {
                paint.color = 0xffffd34d.toInt()

                val path = Path()

                path.moveTo(
                    cx - size * .1f,
                    cy - size * .6f
                )

                path.lineTo(
                    cx + size * .18f,
                    cy - size * .1f
                )

                path.lineTo(
                    cx - size * .02f,
                    cy - size * .1f
                )

                path.lineTo(
                    cx + size * .05f,
                    cy + size * .58f
                )

                path.lineTo(
                    cx - size * .22f,
                    cy + size * .08f
                )

                path.lineTo(
                    cx - size * .03f,
                    cy + size * .08f
                )

                path.close()

                canvas.drawPath(path, paint)
            }

            GameType.BREATH -> {
                paint.color = 0xff65e7c5.toInt()

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f

                canvas.drawCircle(
                    cx,
                    cy,
                    size * .45f,
                    paint
                )

                canvas.drawCircle(
                    cx,
                    cy,
                    size * .68f,
                    paint
                )

                paint.style = Paint.Style.FILL
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
        text(
            canvas,
            "MindBlow",
            18f,
            38f,
            27f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "RELAX • FOCUS • REFRESH",
            19f,
            57f,
            9f,
            0xff8996be.toInt()
        )

        // XP pill
        card(
            canvas,
            w - 94f,
            15f,
            w - 14f,
            50f,
            18f
        )

        text(
            canvas,
            "✦ $score XP",
            w - 54f,
            38f,
            11f,
            0xffffd96a.toInt(),
            true,
            Paint.Align.CENTER
        )

        // Hero
        card(
            canvas,
            12f,
            76f,
            w - 12f,
            175f,
            22f
        )

        text(
            canvas,
            "READY FOR A RESET?",
            25f,
            101f,
            9f,
            0xff62e6ff.toInt(),
            true
        )

        text(
            canvas,
            "Refresh your mind.",
            25f,
            128f,
            22f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Pick a challenge and take a tiny break.",
            25f,
            149f,
            10f,
            0xffa4afd0.toInt()
        )

        button(
            canvas,
            "START A QUICK RESET",
            25f,
            158f,
            w - 25f,
            178f
        )

        // Music card
        text(
            canvas,
            "MIND REFRESHING MUSIC",
            16f,
            205f,
            10f,
            0xff8cecff.toInt(),
            true
        )

        card(
            canvas,
            12f,
            214f,
            w - 12f,
            270f,
            18f
        )

        iconMusic(
            canvas,
            34f,
            242f,
            20f
        )

        text(
            canvas,
            "Mind Refreshing Music",
            60f,
            237f,
            13f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            if (musicOn)
                "Ambient sound is playing"
            else
                "Tap to start calming ambience",
            60f,
            253f,
            9f,
            0xff909cc0.toInt()
        )

        val musicColor =
            if (musicOn)
                0xff55e5ff.toInt()
            else
                0xff687393.toInt()

        paint.color = musicColor

        canvas.drawRoundRect(
            w - 100f,
            230f,
            w - 25f,
            257f,
            14f,
            14f,
            paint
        )

        text(
            canvas,
            if (musicOn) "MUSIC ON" else "MUSIC OFF",
            w - 62f,
            248f,
            9f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        // Games
        text(
            canvas,
            "MIND REFRESHING GAMES",
            16f,
            300f,
            15f,
            Color.WHITE,
            true
        )

        val gap = 9f
        val left = 12f
        val right = w - 12f
        val mid = w / 2f

        gameTile(
            canvas,
            GameType.PULSE,
            "Pulse Hunt",
            "Find the moving glow",
            left,
            315f,
            mid - gap,
            412f
        )

        gameTile(
            canvas,
            GameType.MEMORY,
            "Memory Matrix",
            "Remember the pattern",
            mid + gap,
            315f,
            right,
            412f
        )

        gameTile(
            canvas,
            GameType.REACTION,
            "Quick Reaction",
            "React as fast as possible",
            left,
            421f,
            mid - gap,
            518f
        )

        gameTile(
            canvas,
            GameType.COLOR,
            "Color Match",
            "Spot the matching color",
            mid + gap,
            421f,
            right,
            518f
        )

        gameTile(
            canvas,
            GameType.NUMBER,
            "Number Flow",
            "Tap numbers in order",
            left,
            527f,
            mid - gap,
            624f
        )

        gameTile(
            canvas,
            GameType.BREATH,
            "Zen Breath",
            "Slow down and breathe",
            mid + gap,
            527f,
            right,
            624f
        )

        drawNavigation(
            canvas,
            w,
            h,
            0
        )
    }

    // =========================================================
    // GAME TILE
    // =========================================================

    private fun gameTile(
        canvas: Canvas,
        type: GameType,
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

        icon(
            canvas,
            type,
            l + 38f,
            t + 36f,
            34f
        )

        text(
            canvas,
            title,
            l + 70f,
            t + 35f,
            13f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            l + 70f,
            t + 54f,
            9f,
            0xff8f9abd.toInt()
        )

        text(
            canvas,
            "PLAY ›",
            r - 16f,
            b - 15f,
            9f,
            0xff55e5ff.toInt(),
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
        topBar(
            canvas,
            w,
            "Mind Games",
            "Six quick ways to refresh"
        )

        val mid = w / 2f
        val gap = 9f

        gameTile(
            canvas,
            GameType.PULSE,
            "Pulse Hunt",
            "Find the moving glow",
            12f,
            90f,
            mid - gap,
            185f
        )

        gameTile(
            canvas,
            GameType.MEMORY,
            "Memory Matrix",
            "Remember the pattern",
            mid + gap,
            90f,
            w - 12f,
            185f
        )

        gameTile(
            canvas,
            GameType.REACTION,
            "Quick Reaction",
            "React as fast as possible",
            12f,
            195f,
            mid - gap,
            290f
        )

        gameTile(
            canvas,
            GameType.COLOR,
            "Color Match",
            "Spot the matching color",
            mid + gap,
            195f,
            w - 12f,
            290f
        )

        gameTile(
            canvas,
            GameType.NUMBER,
            "Number Flow",
            "Tap numbers in order",
            12f,
            300f,
            mid - gap,
            395f
        )

        gameTile(
            canvas,
            GameType.BREATH,
            "Zen Breath",
            "Slow down and breathe",
            mid + gap,
            300f,
            w - 12f,
            395f
        )

        card(
            canvas,
            12f,
            415f,
            w - 12f,
            490f,
            20f
        )

        text(
            canvas,
            "TODAY'S RESET",
            28f,
            443f,
            9f,
            0xff60e5ff.toInt(),
            true
        )

        text(
            canvas,
            "Play any game for 2 minutes.",
            28f,
            465f,
            14f,
            Color.WHITE,
            true
        )

        drawNavigation(
            canvas,
            w,
            h,
            1
        )
    }

    // =========================================================
    // MUSIC SCREEN
    // =========================================================

    private fun drawMusic(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        topBar(
            canvas,
            w,
            "Mind Music",
            "Calming sounds for your reset"
        )

        card(
            canvas,
            15f,
            100f,
            w - 15f,
            285f,
            26f
        )

        text(
            canvas,
            "♫",
            w / 2f,
            165f,
            65f,
            0xff63e6ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Ambient Mind Reset",
            w / 2f,
            205f,
            22f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            if (musicOn)
                "Calming ambience is playing"
            else
                "Start your relaxing background sound",
            w / 2f,
            228f,
            11f,
            0xffa1acd0.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            canvas,
            if (musicOn) "TURN MUSIC OFF" else "START MUSIC",
            45f,
            248f,
            w - 45f,
            290f
        )

        text(
            canvas,
            "PLAY WHILE YOU GAME",
            18f,
            335f,
            11f,
            0xff63e6ff.toInt(),
            true
        )

        musicOption(
            canvas,
            "Deep Calm",
            "Soft ambient tones",
            355f,
            0
        )

        musicOption(
            canvas,
            "Night Focus",
            "Low continuous atmosphere",
            425f,
            1
        )

        musicOption(
            canvas,
            "Mind Reset",
            "Slow relaxing pulse",
            495f,
            2
        )

        drawNavigation(
            canvas,
            w,
            h,
            2
        )
    }

    private fun musicOption(
        canvas: Canvas,
        title: String,
        subtitle: String,
        y: Float,
        selected: Int
    ) {
        card(
            canvas,
            15f,
            y,
            width - 15f,
            y + 58f,
            17f
        )

        paint.color =
            if (selected == 0)
                0xff55e5ff.toInt()
            else
                0xff343c62.toInt()

        canvas.drawCircle(
            38f,
            y + 29f,
            10f,
            paint
        )

        text(
            canvas,
            title,
            60f,
            y + 25f,
            13f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            60f,
            y + 42f,
            9f,
            0xff8e9abb.toInt()
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
        topBar(
            canvas,
            w,
            "Your Progress",
            "Your MindBlow journey"
        )

        card(
            canvas,
            15f,
            95f,
            w - 15f,
            225f,
            24f
        )

        text(
            canvas,
            "✦",
            65f,
            170f,
            55f,
            0xff61e5ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Mind Explorer",
            110f,
            140f,
            20f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Level ${score / 500 + 1}",
            110f,
            167f,
            12f,
            0xff9aa7c9.toInt()
        )

        text(
            canvas,
            "$score XP",
            110f,
            194f,
            14f,
            0xffffd76a.toInt(),
            true
        )

        statCard(
            canvas,
            "XP",
            score.toString(),
            15f,
            250f,
            w / 3f - 10f,
            325f
        )

        statCard(
            canvas,
            "STREAK",
            streak.toString(),
            w / 3f,
            250f,
            w * 2f / 3f - 5f,
            325f
        )

        statCard(
            canvas,
            "LEVEL",
            "${score / 500 + 1}",
            w * 2f / 3f,
            250f,
            w - 15f,
            325f
        )

        card(
            canvas,
            15f,
            350f,
            w - 15f,
            440f,
            20f
        )

        text(
            canvas,
            "GAME LIBRARY",
            30f,
            378f,
            10f,
            0xff60e5ff.toInt(),
            true
        )

        text(
            canvas,
            "6 mind refreshing games",
            30f,
            405f,
            15f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Music • Score • Streak • Progress",
            30f,
            426f,
            10f,
            0xff909cbd.toInt()
        )

        drawNavigation(
            canvas,
            w,
            h,
            3
        )
    }

    private fun statCard(
        canvas: Canvas,
        title: String,
        value: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {
        card(
            canvas,
            l + 3f,
            t,
            r - 3f,
            b,
            16f
        )

        text(
            canvas,
            title,
            (l + r) / 2f,
            t + 27f,
            8f,
            0xff8d9abe.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            value,
            (l + r) / 2f,
            t + 53f,
            16f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // TOP BAR
    // =========================================================

    private fun topBar(
        canvas: Canvas,
        w: Float,
        title: String,
        subtitle: String
    ) {
        text(
            canvas,
            "‹",
            20f,
            43f,
            39f,
            Color.WHITE,
            false
        )

        text(
            canvas,
            title,
            57f,
            37f,
            23f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            58f,
            57f,
            9f,
            0xff8f9bbd.toInt()
        )

        card(
            canvas,
            w - 90f,
            15f,
            w - 14f,
            49f,
            17f
        )

        text(
            canvas,
            "✦ $score",
            w - 52f,
            36f,
            10f,
            0xffffd86a.toInt(),
            true,
            Paint.Align.CENTER
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
        val top = h - 74f

        card(
            canvas,
            7f,
            top,
            w - 7f,
            h - 8f,
            20f
        )

        val labels = arrayOf(
            "Home",
            "Games",
            "Music",
            "Profile"
        )

        val icons = arrayOf(
            "⌂",
            "◆",
            "♫",
            "●"
        )

        for (i in 0..3) {

            val x =
                w * (i + .5f) / 4f

            val active =
                i == selected

            val color =
                if (active)
                    0xff5de6ff.toInt()
                else
                    0xff727da3.toInt()

            text(
                canvas,
                icons[i],
                x,
                top + 28f,
                23f,
                color,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                labels[i],
                x,
                top + 50f,
                8f,
                color,
                active,
                Paint.Align.CENTER
            )
        }
    }

    // =========================================================
    // GAME SCREEN
    // =========================================================

    private fun drawGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        val title =
            when (selectedGame) {
                GameType.PULSE -> "Pulse Hunt"
                GameType.COLOR -> "Color Match"
                GameType.MEMORY -> "Memory Matrix"
                GameType.NUMBER -> "Number Flow"
                GameType.REACTION -> "Quick Reaction"
                GameType.BREATH -> "Zen Breath"
            }

        text(
            canvas,
            "‹",
            20f,
            48f,
            40f,
            Color.WHITE
        )

        text(
            canvas,
            title,
            58f,
            40f,
            22f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Score  $gameScore",
            w - 20f,
            39f,
            12f,
            0xffffd66a.toInt(),
            true,
            Paint.Align.RIGHT
        )

        when (selectedGame) {
            GameType.PULSE -> drawPulseGame(canvas, w, h)
            GameType.COLOR -> drawColorGame(canvas, w, h)
            GameType.MEMORY -> drawMemoryGame(canvas, w, h)
            GameType.NUMBER -> drawNumberGame(canvas, w, h)
            GameType.REACTION -> drawReactionGame(canvas, w, h)
            GameType.BREATH -> drawBreathGame(canvas, w, h)
        }
    }

    // =========================================================
    // PULSE HUNT
    // =========================================================

    private fun drawPulseGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "Find the moving pulse",
            w / 2f,
            95f,
            17f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Tap the glowing circle",
            w / 2f,
            119f,
            11f,
            0xff99a5c7.toInt(),
            false,
            Paint.Align.CENTER
        )

        if (!gameStarted) {
            button(
                canvas,
                "START PULSE HUNT",
                45f,
                h * .42f,
                w - 45f,
                h * .42f + 52f
            )
            return
        }

        val areaTop = 150f
        val areaBottom = h - 120f

        pulseX =
            w / 2f +
                    sin(animation * 1.4f) * w * .32f

        pulseY =
            (areaTop + areaBottom) / 2f +
                    cos(animation * 1.1f) * 180f

        paint.color = Color.argb(
            40,
            50,
            230,
            255
        )

        canvas.drawCircle(
            pulseX,
            pulseY,
            80f + sin(animation * 2f) * 10f,
            paint
        )

        paint.color = 0xff50e8ff.toInt()

        canvas.drawCircle(
            pulseX,
            pulseY,
            pulseRadius,
            paint
        )

        paint.color = Color.WHITE

        canvas.drawCircle(
            pulseX - 10f,
            pulseY - 12f,
            8f,
            paint
        )

        text(
            canvas,
            "TAP!",
            w / 2f,
            h - 90f,
            18f,
            0xff55e5ff.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // COLOR MATCH
    // =========================================================

    private fun drawColorGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "Which tile matches?",
            w / 2f,
            98f,
            19f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        val colors = intArrayOf(
            0xffff5577.toInt(),
            0xff55ddff.toInt(),
            0xffffd34d.toInt(),
            0xffa66cff.toInt()
        )

        paint.color = colors[colorTarget]

        canvas.drawRoundRect(
            w / 2f - 48f,
            125f,
            w / 2f + 48f,
            221f,
            25f,
            25f,
            paint
        )

        text(
            canvas,
            "MATCH",
            w / 2f,
            250f,
            10f,
            0xff8e9abc.toInt(),
            true,
            Paint.Align.CENTER
        )

        for (i in 0 until 4) {

            val row = i / 2
            val col = i % 2

            val l =
                25f + col * (w - 75f) / 2f

            val t =
                275f + row * 115f

            val r =
                l + (w - 75f) / 2f

            val b =
                t + 92f

            paint.color = colors[colorOptions[i]]

            canvas.drawRoundRect(
                l,
                t,
                r,
                b,
                22f,
                22f,
                paint
            )
        }

        text(
            canvas,
            "Tap the same color",
            w / 2f,
            535f,
            12f,
            0xff9aa6c6.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // MEMORY MATRIX
    // =========================================================

    private fun drawMemoryGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "Remember the glowing cells",
            w / 2f,
            94f,
            17f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Level $memoryLevel",
            w / 2f,
            118f,
            11f,
            0xff91a0c5.toInt(),
            false,
            Paint.Align.CENTER
        )

        val grid = 4
        val size = min(w - 50f, 330f)
        val left = (w - size) / 2f
        val top = 150f
        val cell = size / grid

        for (i in 0 until 16) {

            val row = i / grid
            val col = i % grid

            val l = left + col * cell + 5f
            val t = top + row * cell + 5f
            val r = left + (col + 1) * cell - 5f
            val b = top + (row + 1) * cell - 5f

            val glowing =
                memoryShowing &&
                        memoryPattern.contains(i)

            val userSelected =
                memoryUser.contains(i)

            paint.color =
                when {
                    glowing ->
                        0xff55e6ff.toInt()

                    userSelected ->
                        0xff8e62ff.toInt()

                    else ->
                        0xff101a42.toInt()
                }

            canvas.drawRoundRect(
                l,
                t,
                r,
                b,
                14f,
                14f,
                paint
            )
        }

        text(
            canvas,
            if (memoryShowing)
                "WATCH..."
            else
                "REPEAT THE PATTERN",
            w / 2f,
            top + size + 45f,
            14f,
            if (memoryShowing)
                0xff55e6ff.toInt()
            else
                Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        if (!gameStarted) {
            button(
                canvas,
                "START MEMORY",
                45f,
                h - 150f,
                w - 45f,
                h - 98f
            )
        }
    }

    // =========================================================
    // NUMBER FLOW
    // =========================================================

    private fun drawNumberGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "Tap numbers in order",
            w / 2f,
            96f,
            18f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            if (nextNumber <= 9)
                "Next: $nextNumber"
            else
                "Perfect!",
            w / 2f,
            122f,
            13f,
            0xff55e5ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        if (!gameStarted) {
            button(
                canvas,
                "START NUMBER FLOW",
                40f,
                h * .43f,
                w - 40f,
                h * .43f + 52f
            )
            return
        }

        for (i in 0 until 9) {

            paint.color =
                if (i + 1 < nextNumber)
                    0xff27315b.toInt()
                else
                    0xff14204a.toInt()

            canvas.drawCircle(
                numberPositions[i].x,
                numberPositions[i].y,
                38f,
                paint
            )

            text(
                canvas,
                "${i + 1}",
                numberPositions[i].x,
                numberPositions[i].y + 9f,
                20f,
                if (i + 1 < nextNumber)
                    0xff697493.toInt()
                else
                    Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }
    }

    // =========================================================
    // REACTION
    // =========================================================

    private fun drawReactionGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "Wait for green",
            w / 2f,
            100f,
            20f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            reactionMessage,
            w / 2f,
            130f,
            12f,
            0xffa3aecf.toInt(),
            true,
            Paint.Align.CENTER
        )

        val color =
            if (reactionReady)
                0xff55e887.toInt()
            else
                0xffff5577.toInt()

        paint.color = color

        canvas.drawCircle(
            w / 2f,
            h * .43f,
            105f + sin(animation * 2f) * 8f,
            paint
        )

        text(
            canvas,
            if (reactionReady) "TAP!" else "WAIT",
            w / 2f,
            h * .43f + 15f,
            25f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        if (!gameStarted) {
            button(
                canvas,
                "START REACTION",
                40f,
                h - 160f,
                w - 40f,
                h - 108f
            )
        }
    }

    // =========================================================
    // ZEN BREATH
    // =========================================================

    private fun drawBreathGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "Slow breathing reset",
            w / 2f,
            100f,
            20f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        val phase =
            if (breathRunning) {
                ((System.currentTimeMillis() -
                        breathStartedAt) % 8000L) / 8000f
            } else {
                0.5f
            }

        val scale =
            if (breathRunning) {
                if (phase < .5f) {
                    .65f + phase * .7f
                } else {
                    1.0f - (phase - .5f) * .7f
                }
            } else {
                .72f
            }

        paint.color = Color.argb(
            35,
            80,
            230,
            200
        )

        canvas.drawCircle(
            w / 2f,
            h * .42f,
            150f * scale,
            paint
        )

        paint.color = 0xff65e7c5.toInt()

        canvas.drawCircle(
            w / 2f,
            h * .42f,
            85f * scale,
            paint
        )

        text(
            canvas,
            if (!breathRunning)
                "READY"
            else if (phase < .5f)
                "BREATHE IN"
            else
                "BREATHE OUT",
            w / 2f,
            h * .42f + 8f,
            17f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Follow the expanding circle",
            w / 2f,
            h * .42f + 150f,
            12f,
            0xffa2adcc.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            canvas,
            if (breathRunning) "STOP" else "START BREATHING",
            45f,
            h - 155f,
            w - 45f,
            h - 103f
        )
    }

    // =========================================================
    // MUSIC ICON
    // =========================================================

    private fun iconMusic(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float
    ) {
        paint.color = 0xff63e6ff.toInt()

        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE

        canvas.drawLine(
            x,
            y - size,
            x,
            y + size,
            paint
        )

        canvas.drawLine(
            x,
            y - size,
            x + size * .8f,
            y - size * .7f,
            paint
        )

        paint.style = Paint.Style.FILL

        canvas.drawCircle(
            x - 4f,
            y + size,
            size * .38f,
            paint
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
            35f,
            h - 135f,
            w - 35f,
            h - 85f,
            18f
        )

        text(
            canvas,
            message,
            w / 2f,
            h - 104f,
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

            Screen.HOME -> {
                homeTouch(x, y, w, h)
            }

            Screen.GAMES -> {
                gamesTouch(x, y, w, h)
            }

            Screen.MUSIC -> {
                musicTouch(x, y, w, h)
            }

            Screen.PROFILE -> {
                profileTouch(x, y, w, h)
            }

            Screen.GAME -> {
                gameTouch(x, y, w, h)
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
                x < w * .25f ->
                    screen = Screen.HOME

                x < w * .50f ->
                    screen = Screen.GAMES

                x < w * .75f ->
                    screen = Screen.MUSIC

                else ->
                    screen = Screen.PROFILE
            }

            return
        }

        // Music card
        if (y in 214f..270f) {
            toggleMusic()
            return
        }

        // six games
        if (y in 315f..624f) {

            val gameIndex =
                when {
                    y < 418f && x < w / 2f -> 0
                    y < 418f -> 1
                    y < 523f && x < w / 2f -> 2
                    y < 523f -> 3
                    y < 630f && x < w / 2f -> 4
                    else -> 5
                }

            openGame(gameIndex)
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

        if (y > h - 90f) {

            when {
                x < w * .25f ->
                    screen = Screen.HOME

                x < w * .50f ->
                    screen = Screen.GAMES

                x < w * .75f ->
                    screen = Screen.MUSIC

                else ->
                    screen = Screen.PROFILE
            }

            return
        }

        if (y < 75f) {
            screen = Screen.HOME
            return
        }

        val gameIndex =
            when {
                y in 90f..185f &&
                        x < w / 2f -> 0

                y in 90f..185f -> 1

                y in 195f..290f &&
                        x < w / 2f -> 2

                y in 195f..290f -> 3

                y in 300f..395f &&
                        x < w / 2f -> 4

                y in 300f..395f -> 5

                else -> -1
            }

        if (gameIndex >= 0) {
            openGame(gameIndex)
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

        if (y > h - 90f) {

            when {
                x < w * .25f ->
                    screen = Screen.HOME

                x < w * .50f ->
                    screen = Screen.GAMES

                x < w * .75f ->
                    screen = Screen.MUSIC

                else ->
                    screen = Screen.PROFILE
            }

            return
        }

        if (y < 75f) {
            screen = Screen.HOME
            return
        }

        if (y in 100f..310f) {
            toggleMusic()
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

        if (y > h - 90f) {

            when {
                x < w * .25f ->
                    screen = Screen.HOME

                x < w * .50f ->
                    screen = Screen.GAMES

                x < w * .75f ->
                    screen = Screen.MUSIC

                else ->
                    screen = Screen.PROFILE
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

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        when (selectedGame) {

            GameType.PULSE ->
                pulseTouch(x, y)

            GameType.COLOR ->
                colorTouch(x, y, w)

            GameType.MEMORY ->
                memoryTouch(x, y, w)

            GameType.NUMBER ->
                numberTouch(x, y)

            GameType.REACTION ->
                reactionTouch()

            GameType.BREATH ->
                breathTouch()
        }
    }

    // =========================================================
    // OPEN GAME
    // =========================================================

    private fun openGame(index: Int) {

        selectedGame =
            when (index) {
                0 -> GameType.PULSE
                1 -> GameType.MEMORY
                2 -> GameType.REACTION
                3 -> GameType.COLOR
                4 -> GameType.NUMBER
                else -> GameType.BREATH
            }

        resetGame()

        screen = Screen.GAME
    }

    private fun resetGame() {

        gameStarted = false
        gameScore = 0

        pulseRound = 0

        colorCorrect =
            Random.nextInt(4)

        colorTarget =
            Random.nextInt(4)

        colorOptions =
            IntArray(4) { it }

        colorOptions.shuffle()

        memoryLevel = 1
        memoryUser.clear()
        memoryPattern.clear()
        memoryShowing = true

        nextNumber = 1

        reactionReady = false
        reactionMessage = "WAIT..."
        reactionDelayUntil = 0L

        breathRunning = false
    }

    // =========================================================
    // PULSE TOUCH
    // =========================================================

    private fun pulseTouch(
        x: Float,
        y: Float
    ) {

        if (!gameStarted) {
            gameStarted = true
            score += 5
            showMessage("Pulse Hunt started!")
            vibrate()
            return
        }

        val dx = x - pulseX
        val dy = y - pulseY

        if (dx * dx + dy * dy <= 80f * 80f) {

            gameScore += 10
            score += 10

            pulseRound++

            vibrate()

            showMessage("+10 XP • Great!")

            if (pulseRound >= 10) {
                finishGame()
            }
        }
    }

    // =========================================================
    // COLOR TOUCH
    // =========================================================

    private fun colorTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (y < 270f) {
            return
        }

        val col =
            if (x < w / 2f) 0 else 1

        val row =
            if (y < 390f) 0 else 1

        val index =
            row * 2 + col

        if (index !in 0..3) return

        if (colorOptions[index] == colorTarget) {

            gameScore += 10
            score += 10

            vibrate()

            showMessage("Correct! +10 XP")

            colorTarget =
                Random.nextInt(4)

            colorOptions =
                IntArray(4) { it }

            colorOptions.shuffle()

        } else {
            showMessage("Try again!")
        }
    }

    // =========================================================
    // MEMORY
    // =========================================================

    private fun memoryTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (!gameStarted) {

            gameStarted = true

            memoryPattern.clear()
            memoryUser.clear()

            repeat(
                min(
                    3 + memoryLevel,
                    8
                )
            ) {
                var value: Int

                do {
                    value = Random.nextInt(16)
                } while (
                    memoryPattern.contains(value)
                )

                memoryPattern.add(value)
            }

            memoryShowing = true
            memoryShowUntil =
                System.currentTimeMillis() + 1800L

            showMessage("Watch carefully!")
            return
        }

        if (
            memoryShowing &&
            System.currentTimeMillis() >= memoryShowUntil
        ) {
            memoryShowing = false
        }

        if (memoryShowing) {
            return
        }

        val size = min(w - 50f, 330f)
        val left = (w - size) / 2f
        val top = 150f
        val cell = size / 4f

        if (
            x < left ||
            x > left + size ||
            y < top ||
            y > top + size
        ) return

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

        if (memoryUser.contains(index)) return

        memoryUser.add(index)

        if (!memoryPattern.contains(index)) {

            showMessage("Wrong cell — try again")

            memoryUser.clear()

            memoryShowing = true
            memoryShowUntil =
                System.currentTimeMillis() + 1200L

            return
        }

        if (
            memoryUser.size ==
            memoryPattern.size
        ) {

            gameScore += memoryLevel * 10
            score += memoryLevel * 10

            vibrate()

            showMessage(
                "Pattern cleared! +${memoryLevel * 10} XP"
            )

            memoryLevel++

            memoryUser.clear()
            memoryPattern.clear()

            memoryShowing = true

            memoryShowUntil =
                System.currentTimeMillis() + 1500L

            repeat(
                min(
                    3 + memoryLevel,
                    8
                )
            ) {
                var value: Int

                do {
                    value = Random.nextInt(16)
                } while (
                    memoryPattern.contains(value)
                )

                memoryPattern.add(value)
            }
        }
    }

    // =========================================================
    // NUMBER FLOW
    // =========================================================

    private fun numberTouch(
        x: Float,
        y: Float
    ) {

        if (!gameStarted) {

            gameStarted = true
            nextNumber = 1
            numberStart =
                System.currentTimeMillis()

            generateNumberPositions()

            showMessage("Go!")

            return
        }

        if (nextNumber > 9) return

        val p =
            numberPositions[nextNumber - 1]

        val dx = x - p.x
        val dy = y - p.y

        if (
            dx * dx +
            dy * dy <=
            45f * 45f
        ) {

            nextNumber++

            gameScore += 5
            score += 5

            vibrate()

            if (nextNumber > 9) {

                val elapsed =
                    System.currentTimeMillis() -
                            numberStart

                val bonus =
                    if (elapsed < 7000L) 25 else 10

                score += bonus
                gameScore += bonus

                showMessage(
                    "Perfect flow! +$bonus bonus"
                )

            } else {
                showMessage("Good! Next $nextNumber")
            }
        }
    }

    private fun generateNumberPositions() {

        val w = width.toFloat()
        val h = height.toFloat()

        val areaLeft = 65f
        val areaRight = w - 65f
        val areaTop = 175f
        val areaBottom = h - 145f

        for (i in 0 until 9) {

            var x: Float
            var y: Float

            var valid: Boolean

            do {

                x =
                    Random.nextFloat() *
                            (areaRight - areaLeft) +
                            areaLeft

                y =
                    Random.nextFloat() *
                            (areaBottom - areaTop) +
                            areaTop

                valid = true

                for (j in 0 until i) {

                    val dx =
                        x - numberPositions[j].x

                    val dy =
                        y - numberPositions[j].y

                    if (
                        dx * dx +
                        dy * dy <
                        85f * 85f
                    ) {
                        valid = false
                        break
                    }
                }

            } while (!valid)

            numberPositions[i] =
                PointF(x, y)
        }
    }

    // =========================================================
    // REACTION
    // =========================================================

    private fun reactionTouch() {

        if (!gameStarted) {

            gameStarted = true
            reactionReady = false
            reactionMessage = "WAIT..."

            reactionDelayUntil =
                System.currentTimeMillis() +
                        Random.nextLong(1500L, 3500L)

            return
        }

        if (!reactionReady) {

            if (
                System.currentTimeMillis() >=
                reactionDelayUntil
            ) {

                reactionReady = true
                reactionStartedAt =
                    System.currentTimeMillis()

                reactionMessage = "TAP NOW!"

            } else {

                showMessage(
                    "Too early! Try again."
                )

                gameStarted = false
            }

            return
        }

        val reactionTime =
            System.currentTimeMillis() -
                    reactionStartedAt

        val points =
            when {
                reactionTime < 250L -> 30
                reactionTime < 450L -> 20
                else -> 10
            }

        gameScore += points
        score += points

        vibrate()

        showMessage(
            "${reactionTime}ms • +$points XP"
        )

        gameStarted = false
        reactionReady = false
        reactionMessage = "WAIT..."
    }

    // =========================================================
    // BREATH
    // =========================================================

    private fun breathTouch() {

        if (!breathRunning) {

            breathRunning = true
            breathStartedAt =
                System.currentTimeMillis()

            showMessage(
                "Breathe slowly..."
            )

        } else {

            breathRunning = false

            score += 15
            gameScore += 15

            vibrate()

            showMessage(
                "Nice reset! +15 XP"
            )
        }
    }

    // =========================================================
    // FINISH GAME
    // =========================================================

    private fun finishGame() {

        streak++

        prefs.edit()
            .putInt("score", score)
            .putInt("streak", streak)
            .apply()

        showMessage(
            "Game complete! +$gameScore XP"
        )

        gameStarted = false
    }

    // =========================================================
    // VIBRATION
    // =========================================================

    private fun vibrate() {

        try {

            if (Build.VERSION.SDK_INT >= 26) {

                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        35L,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )

            } else {

                @Suppress("DEPRECATION")
                vibrator?.vibrate(35L)
            }

        } catch (_: Exception) {
        }
    }

    // =========================================================
    // MUSIC
    // =========================================================

    private fun toggleMusic() {

        musicOn = !musicOn

        prefs.edit()
            .putBoolean("music", musicOn)
            .apply()

        if (musicOn) {
            startMusic()
            showMessage("Mind music ON 🎵")
        } else {
            stopMusic()
            showMessage("Mind music OFF")
        }
    }

    private fun startMusic() {

        if (ambientMusic == null) {

            ambientMusic =
                AmbientMusic().also {
                    it.start()
                }

        } else {

            ambientMusic?.start()
        }
    }

    fun releaseMusic() {
        stopMusic()
    }

    private fun stopMusic() {
        ambientMusic?.stop()
        ambientMusic = null
    }

    // =========================================================
    // BACK
    // =========================================================

    fun goBack(): Boolean {

        if (screen == Screen.GAME) {
            screen = Screen.GAMES
            return true
        }

        if (screen != Screen.HOME) {
            screen = Screen.HOME
            return true
        }

        return false
    }

    // =========================================================
    // AMBIENT MUSIC ENGINE
    // No MP3 file required.
    // =========================================================

    private class AmbientMusic {

        private val running =
            AtomicBoolean(false)

        private var thread: Thread? = null
        private var track: AudioTrack? = null

        private val sampleRate = 22050

        fun start() {

            if (running.get()) return

            running.set(true)

            thread = Thread {

                try {

                    val minBuffer =
                        AudioTrack.getMinBufferSize(
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT
                        )

                    val bufferSize =
                        maxOf(
                            minBuffer,
                            sampleRate / 2
                        )

                    val audioTrack =
                        if (Build.VERSION.SDK_INT >= 23) {

                            AudioTrack.Builder()
                                .setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setUsage(
                                            AudioAttributes.USAGE_MEDIA
                                        )
                                        .setContentType(
                                            AudioAttributes.CONTENT_TYPE_MUSIC
                                        )
                                        .build()
                                )
                                .setAudioFormat(
                                    AudioFormat.Builder()
                                        .setSampleRate(
                                            sampleRate
                                        )
                                        .setChannelMask(
                                            AudioFormat.CHANNEL_OUT_MONO
                                        )
                                        .setEncoding(
                                            AudioFormat.ENCODING_PCM_16BIT
                                        )
                                        .build()
                                )
                                .setBufferSizeInBytes(
                                    bufferSize
                                )
                                .setTransferMode(
                                    AudioTrack.MODE_STREAM
                                )
                                .build()

                        } else {

                            @Suppress("DEPRECATION")
                            AudioTrack(
                                android.media.AudioManager.STREAM_MUSIC,
                                sampleRate,
                                AudioFormat.CHANNEL_OUT_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufferSize,
                                AudioTrack.MODE_STREAM
                            )
                        }

                    track = audioTrack

                    audioTrack.play()

                    val samples =
                        ShortArray(sampleRate / 2)

                    var phase = 0.0
                    var slowPhase = 0.0

                    while (running.get()) {

                        for (i in samples.indices) {

                            val time =
                                i.toDouble() /
                                        sampleRate

                            val tone1 =
                                sin(
                                    phase
                                ) * 0.045

                            val tone2 =
                                sin(
                                    slowPhase
                                ) * 0.025

                            val wave =
                                tone1 + tone2

                            samples[i] =
                                (wave *
                                        Short.MAX_VALUE)
                                    .toInt()
                                    .toShort()

                            phase +=
                                2.0 *
                                        Math.PI *
                                        174.0 /
                                        sampleRate

                            slowPhase +=
                                2.0 *
                                        Math.PI *
                                        87.0 /
                                        sampleRate

                            if (phase > Math.PI * 2)
                                phase -= Math.PI * 2

                            if (slowPhase > Math.PI * 2)
                                slowPhase -= Math.PI * 2
                        }

                        audioTrack.write(
                            samples,
                            0,
                            samples.size
                        )
                    }

                    try {
                        audioTrack.stop()
                    } catch (_: Exception) {
                    }

                    audioTrack.release()

                    track = null

                } catch (_: Exception) {

                    track = null
                }

            }.apply {
                isDaemon = true
                start()
            }
        }

        fun stop() {

            running.set(false)

            try {
                track?.stop()
            } catch (_: Exception) {
            }

            try {
                track?.release()
            } catch (_: Exception) {
            }

            track = null

            thread?.interrupt()
            thread = null
        }
    }

    private fun saveProgress() {

        prefs.edit()
            .putInt("score", score)
            .putInt("streak", streak)
            .putBoolean("music", musicOn)
            .apply()
    }
}
