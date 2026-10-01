package com.mindblow.app

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
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

        window.statusBarColor = Color.rgb(4, 6, 25)
        window.navigationBarColor = Color.rgb(4, 6, 25)

        gameView = MindBlowView(this)
        setContentView(gameView)
    }

    override fun onResume() {
        super.onResume()
        if (::gameView.isInitialized) {
            gameView.resumeMusicIfEnabled()
        }
    }

    override fun onPause() {
        if (::gameView.isInitialized) {
            gameView.pauseMusic()
        }
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!gameView.goBack()) {
            super.onBackPressed()
        }
    }
}

/* ============================================================
   SCREENS
   ============================================================ */

private enum class Screen {
    HOME,
    GAMES,
    MUSIC,
    GLOW,
    MEMORY,
    REACTION,
    BREATHE,
    COLOR,
    NUMBER,
    DAILY,
    PROFILE
}

/* ============================================================
   MAIN VIEW
   ============================================================ */

private class MindBlowView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs =
        context.getSharedPreferences("mindblow_data", Context.MODE_PRIVATE)

    private var screen = Screen.HOME

    private var animation = 0f

    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)

    private var message = ""
    private var messageUntil = 0L

    /* ---------------- MUSIC ---------------- */

    private var musicEnabled =
        prefs.getBoolean("music_enabled", true)

    private var musicPlayer: RelaxMusic? = null

    /* ---------------- GLOW ---------------- */

    private var glowX = 0f
    private var glowY = 0f
    private var glowRadius = 32f
    private var glowRound = 0

    /* ---------------- MEMORY ---------------- */

    private val memorySymbols = arrayOf(
        "A", "B", "C", "D", "E", "F"
    )

    private var memoryCards = mutableListOf<Int>()
    private var memoryOpen1 = -1
    private var memoryOpen2 = -1
    private var memoryLocked = false
    private var memoryMatches = 0

    /* ---------------- REACTION ---------------- */

    private var reactionX = 0f
    private var reactionY = 0f
    private var reactionReadyAt = 0L
    private var reactionStartedAt = 0L
    private var reactionState = 0
    private var reactionBest = Long.MAX_VALUE

    /* ---------------- BREATHE ---------------- */

    private var breatheStart = System.currentTimeMillis()
    private var breathePhase = 0

    /* ---------------- COLOR ---------------- */

    private var colorTarget = 0
    private var colorOptions = mutableListOf<Int>()
    private var colorRound = 0

    /* ---------------- NUMBER ---------------- */

    private var numberSequence = mutableListOf<Int>()
    private var numberExpected = 1
    private var numberRound = 0

    init {
        isFocusable = true

        musicPlayer = RelaxMusic()

        if (musicEnabled) {
            musicPlayer?.start()
        }

        createMemoryGame()
        createColorGame()
        createNumberGame()

        postInvalidateDelayed(16L)
    }

    /* ========================================================
       DRAW
       ======================================================== */

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
            Screen.GLOW -> drawGlow(canvas, w, h)
            Screen.MEMORY -> drawMemory(canvas, w, h)
            Screen.REACTION -> drawReaction(canvas, w, h)
            Screen.BREATHE -> drawBreathe(canvas, w, h)
            Screen.COLOR -> drawColor(canvas, w, h)
            Screen.NUMBER -> drawNumber(canvas, w, h)
            Screen.DAILY -> drawDaily(canvas, w, h)
            Screen.PROFILE -> drawProfile(canvas, w, h)
        }

        if (messageUntil > System.currentTimeMillis()) {
            drawToast(canvas, w, h)
        }

        postInvalidateDelayed(16L)
    }

    /* ========================================================
       BACKGROUND
       ======================================================== */

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
            Color.rgb(4, 7, 28),
            Color.rgb(30, 5, 52),
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

        paint.color = Color.argb(38, 20, 220, 255)
        canvas.drawCircle(
            w * 0.15f + sin(t) * 45f,
            h * 0.23f,
            120f,
            paint
        )

        paint.color = Color.argb(35, 150, 70, 255)
        canvas.drawCircle(
            w * 0.86f + cos(t * .7f) * 35f,
            h * 0.37f,
            145f,
            paint
        )

        paint.color = Color.argb(25, 30, 240, 190)
        canvas.drawCircle(
            w * .52f,
            h * .82f + sin(t) * 25f,
            120f,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(18, 100, 220, 255)

        val cx = w / 2f
        val cy = h * .49f

        for (i in 0 until 7) {
            canvas.drawCircle(
                cx,
                cy,
                45f + i * 45f + sin(t + i) * 4f,
                paint
            )
        }

        paint.style = Paint.Style.FILL

        /* small stars */

        for (i in 0 until 18) {
            val sx =
                ((i * 97) % max(1, width)).toFloat()

            val sy =
                ((i * 173) % max(1, height)).toFloat()

            paint.color = Color.argb(
                55,
                100,
                220,
                255
            )

            canvas.drawCircle(
                sx,
                sy,
                1.5f + sin(t * 2f + i) * .7f,
                paint
            )
        }
    }

    /* ========================================================
       TEXT
       ======================================================== */

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

    /* ========================================================
       CARD
       ======================================================== */

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
            9,
            16,
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

    /* ========================================================
       GRADIENT BUTTON
       ======================================================== */

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
            Color.rgb(130, 60, 255),
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
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    /* ========================================================
       HEADER
       ======================================================== */

    private fun header(
        canvas: Canvas,
        title: String,
        subtitle: String
    ) {
        text(
            canvas,
            "‹",
            22f,
            48f,
            38f,
            Color.WHITE
        )

        text(
            canvas,
            title,
            58f,
            40f,
            24f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            58f,
            62f,
            11f,
            0xff9ba9d5.toInt()
        )
    }

    /* ========================================================
       BOTTOM NAV
       ======================================================== */

    private fun navigation(
        canvas: Canvas,
        w: Float,
        h: Float,
        selected: Int
    ) {
        val top = h - 86f

        card(
            canvas,
            8f,
            top,
            w - 8f,
            h - 8f,
            22f
        )

        val names = arrayOf(
            "HOME",
            "GAMES",
            "MUSIC",
            "DAILY",
            "PROFILE"
        )

        val icons = arrayOf(
            "⌂",
            "✦",
            "♫",
            "★",
            "●"
        )

        for (i in 0..4) {

            val x =
                w * (i + .5f) / 5f

            val active =
                i == selected

            val color =
                if (active)
                    0xff55e7ff.toInt()
                else
                    0xff7e89ad.toInt()

            /* BIGGER ICONS */

            text(
                canvas,
                icons[i],
                x,
                top + 34f,
                27f,
                color,
                true,
                Paint.Align.CENTER
            )

            text(
                canvas,
                names[i],
                x,
                top + 61f,
                9f,
                color,
                true,
                Paint.Align.CENTER
            )
        }
    }

    /* ========================================================
       HOME
       ======================================================== */

    private fun drawHome(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "MindBlow",
            18f,
            39f,
            27f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Level ${score / 250 + 1}  •  Refresh your mind",
            18f,
            60f,
            11f,
            0xff9da9d0.toInt()
        )

        card(
            canvas,
            w - 100f,
            15f,
            w - 15f,
            54f,
            17f
        )

        text(
            canvas,
            "✦ $score XP",
            w - 57f,
            39f,
            12f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )

        /* QUICK RESET */

        card(
            canvas,
            12f,
            76f,
            w - 12f,
            165f,
            22f
        )

        text(
            canvas,
            "HOW DO YOU FEEL?",
            24f,
            98f,
            9f,
            0xff58ddff.toInt(),
            true
        )

        text(
            canvas,
            "Take a tiny break.",
            24f,
            124f,
            21f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Play something. Breathe. Reset.",
            24f,
            145f,
            11f,
            0xffa9b6dc.toInt()
        )

        button(
            canvas,
            "START A QUICK RESET",
            24f,
            151f,
            w - 24f,
            162f
        )

        /* MUSIC */

        text(
            canvas,
            "MIND REFRESHING",
            16f,
            192f,
            9f,
            0xff59dcff.toInt(),
            true
        )

        card(
            canvas,
            12f,
            201f,
            w - 12f,
            259f,
            17f
        )

        text(
            canvas,
            "♫",
            30f,
            237f,
            25f,
            0xff8c75ff.toInt(),
            true
        )

        text(
            canvas,
            "Mind Refreshing Music",
            57f,
            226f,
            13f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            if (musicEnabled)
                "Ambient music is playing"
            else
                "Music is paused",
            57f,
            244f,
            9f,
            0xff9ba8ce.toInt()
        )

        val musicColor =
            if (musicEnabled)
                0xff43d9ff.toInt()
            else
                0xff525b7c.toInt()

        paint.color = musicColor

        canvas.drawRoundRect(
            w - 95f,
            218f,
            w - 25f,
            246f,
            16f,
            16f,
            paint
        )

        text(
            canvas,
            if (musicEnabled) "MUSIC ON" else "MUSIC OFF",
            w - 60f,
            238f,
            9f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        /* GAMES */

        text(
            canvas,
            "EXPLORE GAMES",
            16f,
            284f,
            9f,
            0xff59dcff.toInt(),
            true
        )

        gameTile(
            canvas,
            "✦",
            "Glow",
            "Find it",
            12f,
            293f,
            w / 2f - 6f,
            365f
        )

        gameTile(
            canvas,
            "◆",
            "Memory",
            "Match it",
            w / 2f + 6f,
            293f,
            w - 12f,
            365f
        )

        gameTile(
            canvas,
            "⚡",
            "Reaction",
            "React fast",
            12f,
            373f,
            w / 2f - 6f,
            445f
        )

        gameTile(
            canvas,
            "◉",
            "Breathe",
            "Slow down",
            w / 2f + 6f,
            373f,
            w - 12f,
            445f
        )

        navigation(
            canvas,
            w,
            h,
            0
        )
    }

    /* ========================================================
       GAME TILE
       ======================================================== */

    private fun gameTile(
        canvas: Canvas,
        icon: String,
        title: String,
        sub: String,
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

        /* Larger icon */

        text(
            canvas,
            icon,
            l + 28f,
            t + 36f,
            24f,
            0xff59e6ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            title,
            l + 50f,
            t + 29f,
            14f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            sub,
            l + 50f,
            t + 48f,
            9f,
            0xff98a6cd.toInt()
        )
    }

    /* ========================================================
       GAMES SCREEN
       ======================================================== */

    private fun drawGames(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Mind Games",
            "Quick 2–5 minute resets"
        )

        gameTile(
            canvas,
            "✦",
            "Glow",
            "Visual focus",
            12f,
            90f,
            w / 2f - 6f,
            174f
        )

        gameTile(
            canvas,
            "◆",
            "Memory",
            "Memory training",
            w / 2f + 6f,
            90f,
            w - 12f,
            174f
        )

        gameTile(
            canvas,
            "⚡",
            "Reaction",
            "Reaction speed",
            12f,
            184f,
            w / 2f - 6f,
            268f
        )

        gameTile(
            canvas,
            "◉",
            "Breathe",
            "Calm your mind",
            w / 2f + 6f,
            184f,
            w - 12f,
            268f
        )

        gameTile(
            canvas,
            "◆",
            "Color Hunt",
            "Visual attention",
            12f,
            278f,
            w / 2f - 6f,
            362f
        )

        gameTile(
            canvas,
            "123",
            "Number Flow",
            "Thinking speed",
            w / 2f + 6f,
            278f,
            w - 12f,
            362f
        )

        card(
            canvas,
            12f,
            380f,
            w - 12f,
            450f,
            18f
        )

        text(
            canvas,
            "TIP",
            25f,
            403f,
            9f,
            0xff59e6ff.toInt(),
            true
        )

        text(
            canvas,
            "Try 2–5 minutes between study sessions.",
            25f,
            424f,
            11f,
            0xffaab7dc.toInt()
        )

        navigation(
            canvas,
            w,
            h,
            1
        )
    }

    /* ========================================================
       MUSIC SCREEN
       ======================================================== */

    private fun drawMusic(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Mind Music",
            "Relax • focus • refresh"
        )

        card(
            canvas,
            15f,
            95f,
            w - 15f,
            260f,
            25f
        )

        text(
            canvas,
            "♫",
            w / 2f,
            165f,
            70f,
            0xff62e7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Mind Refreshing Music",
            w / 2f,
            202f,
            22f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Soft ambient sound for a calmer mind",
            w / 2f,
            226f,
            11f,
            0xffaab7dc.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            canvas,
            if (musicEnabled) "PAUSE MUSIC" else "PLAY MUSIC",
            40f,
            245f,
            w - 40f,
            300f
        )

        card(
            canvas,
            18f,
            325f,
            w - 18f,
            415f,
            22f
        )

        text(
            canvas,
            "CURRENT SESSION",
            35f,
            355f,
            10f,
            0xff58dcff.toInt(),
            true
        )

        text(
            canvas,
            if (musicEnabled)
                "Ambient sound is playing"
            else
                "Music is paused",
            35f,
            385f,
            16f,
            Color.WHITE,
            true
        )

        navigation(
            canvas,
            w,
            h,
            2
        )
    }

    /* ========================================================
       GLOW GAME
       ======================================================== */

    private fun drawGlow(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Glow",
            "Find the moving light"
        )

        text(
            canvas,
            "ROUND $glowRound",
            22f,
            98f,
            12f,
            0xff5ce7ff.toInt(),
            true
        )

        val pulse =
            8f * sin(animation * 3f)

        paint.color = Color.argb(
            45,
            70,
            230,
            255
        )

        canvas.drawCircle(
            glowX,
            glowY,
            glowRadius + 35f + pulse,
            paint
        )

        paint.color = 0xff57e8ff.toInt()

        canvas.drawCircle(
            glowX,
            glowY,
            glowRadius + pulse,
            paint
        )

        paint.color = Color.WHITE

        canvas.drawCircle(
            glowX - 8f,
            glowY - 8f,
            7f,
            paint
        )

        text(
            canvas,
            "TAP THE GLOW",
            w / 2f,
            h - 150f,
            18f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    /* ========================================================
       MEMORY GAME
       ======================================================== */

    private fun drawMemory(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Memory Match",
            "Match the hidden pairs"
        )

        val size =
            min(w - 40f, 330f)

        val left =
            (w - size) / 2f

        val top = 100f

        val cell =
            size / 4f

        for (i in 0 until 12) {

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

            val open =
                i == memoryOpen1 ||
                        i == memoryOpen2

            if (open) {

                text(
                    canvas,
                    memorySymbols[memoryCards[i]],
                    (l + r) / 2f,
                    (tt + b) / 2f + 10f,
                    27f,
                    0xff62e7ff.toInt(),
                    true,
                    Paint.Align.CENTER
                )

            } else {

                text(
                    canvas,
                    "?",
                    (l + r) / 2f,
                    (tt + b) / 2f + 10f,
                    25f,
                    0xff58698f.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            }
        }

        text(
            canvas,
            "MATCHES  $memoryMatches / 6",
            w / 2f,
            top + size + 35f,
            14f,
            0xffb5c1df.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    /* ========================================================
       REACTION GAME
       ======================================================== */

    private fun drawReaction(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Reaction",
            "Wait for green • then tap"
        )

        val cx = w / 2f
        val cy = 285f

        val color =
            when (reactionState) {
                0 -> 0xff58678f.toInt()
                1 -> 0xffff5b6e.toInt()
                else -> 0xff42e89a.toInt()
            }

        paint.color = color

        canvas.drawCircle(
            cx,
            cy,
            95f,
            paint
        )

        text(
            canvas,
            when (reactionState) {
                0 -> "START"
                1 -> "WAIT"
                else -> "TAP!"
            },
            cx,
            cy + 8f,
            24f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        if (reactionBest != Long.MAX_VALUE) {
            text(
                canvas,
                "BEST ${reactionBest} ms",
                cx,
                420f,
                15f,
                0xffb7c3df.toInt(),
                true,
                Paint.Align.CENTER
            )
        }
    }

    /* ========================================================
       BREATHE GAME
       ======================================================== */

    private fun drawBreathe(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Breathe",
            "Slow your breathing"
        )

        val elapsed =
            (System.currentTimeMillis() - breatheStart) % 12000L

        val phase =
            when {
                elapsed < 4000L -> 0
                elapsed < 6000L -> 1
                else -> 2
            }

        val progress =
            when (phase) {
                0 -> elapsed / 4000f
                1 -> 1f
                else -> 1f -
                        ((elapsed - 6000L) / 6000f)
            }

        val radius =
            80f + 90f * progress

        paint.color = Color.argb(
            35,
            70,
            220,
            255
        )

        canvas.drawCircle(
            w / 2f,
            280f,
            radius + 30f,
            paint
        )

        paint.color = 0xff54dcff.toInt()

        canvas.drawCircle(
            w / 2f,
            280f,
            radius,
            paint
        )

        text(
            canvas,
            when (phase) {
                0 -> "BREATHE IN"
                1 -> "HOLD"
                else -> "BREATHE OUT"
            },
            w / 2f,
            286f,
            19f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Follow the circle",
            w / 2f,
            450f,
            14f,
            0xffaeb9d9.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    /* ========================================================
       COLOR HUNT
       ======================================================== */

    private fun drawColor(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Color Hunt",
            "Find the different color"
        )

        text(
            canvas,
            "ROUND $colorRound",
            w / 2f,
            100f,
            14f,
            0xff5ce7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        val size = 85f
        val gap = 14f

        for (i in 0 until 9) {

            val row = i / 3
            val col = i % 3

            val x =
                w / 2f -
                        1.5f * size -
                        gap +
                        col * (size + gap) +
                        size / 2f

            val y =
                175f +
                        row * (size + gap) +
                        size / 2f

            paint.color =
                colorOptions.getOrElse(i) {
                    Color.CYAN
                }

            canvas.drawRoundRect(
                x - size / 2f,
                y - size / 2f,
                x + size / 2f,
                y + size / 2f,
                18f,
                18f,
                paint
            )
        }

        text(
            canvas,
            "Which tile is different?",
            w / 2f,
            490f,
            16f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    /* ========================================================
       NUMBER FLOW
       ======================================================== */

    private fun drawNumber(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            canvas,
            "Number Flow",
            "Tap numbers in order"
        )

        text(
            canvas,
            "NEXT  $numberExpected",
            w / 2f,
            105f,
            17f,
            0xff59e6ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        val size = 72f
        val gap = 14f

        for (i in 0 until 9) {

            val row = i / 3
            val col = i % 3

            val x =
                w / 2f -
                        1.5f * size -
                        gap +
                        col * (size + gap) +
                        size / 2f

            val y =
                165f +
                        row * (size + gap) +
                        size / 2f

            card(
                canvas,
                x - size / 2f,
                y - size / 2f,
                x + size / 2f,
                y + size / 2f,
                16f
            )

            text(
                canvas,
                numberSequence[i].toString(),
                x,
                y + 8f,
                21f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        text(
            canvas,
            "Complete the sequence",
            w / 2f,
            490f,
            15f,
            0xffaeb9d8.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    /* ========================================================
       DAILY
       ======================================================== */

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
            15f,
            100f,
            w - 15f,
            285f,
            24f
        )

        text(
            canvas,
            "TODAY'S RESET",
            35f,
            137f,
            10f,
            0xff5ce7ff.toInt(),
            true
        )

        text(
            canvas,
            "Refresh your attention",
            35f,
            174f,
            23f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Play one quick game and earn XP.",
            35f,
            202f,
            12f,
            0xffaeb9d9.toInt()
        )

        button(
            canvas,
            "PLAY GLOW",
            35f,
            225f,
            w - 35f,
            275f
        )

        text(
            canvas,
            "🔥 $streak DAY STREAK",
            w / 2f,
            345f,
            18f,
            0xffffb42d.toInt(),
            true,
            Paint.Align.CENTER
        )

        navigation(
            canvas,
            w,
            h,
            3
        )
    }

    /* ========================================================
       PROFILE
       ======================================================== */

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
            95f,
            w - 15f,
            245f,
            24f
        )

        text(
            canvas,
            "✦",
            70f,
            175f,
            55f,
            0xff59e6ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Mind Explorer",
            115f,
            145f,
            20f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Level ${score / 250 + 1}",
            115f,
            174f,
            13f,
            0xffaeb9d9.toInt()
        )

        text(
            canvas,
            "$score XP",
            115f,
            203f,
            14f,
            0xffffd66b.toInt(),
            true
        )

        text(
            canvas,
            "Games played: ${score / 10}",
            30f,
            290f,
            15f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Streak: $streak days",
            30f,
            320f,
            15f,
            0xffffb42d.toInt(),
            true
        )

        navigation(
            canvas,
            w,
            h,
            4
        )
    }

    /* ========================================================
       TOAST
       ======================================================== */

    private fun drawToast(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        card(
            canvas,
            30f,
            h - 145f,
            w - 30f,
            h - 92f,
            18f
        )

        text(
            canvas,
            message,
            w / 2f,
            h - 112f,
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
            .putBoolean("music_enabled", musicEnabled)
            .apply()
    }

    /* ========================================================
       MEMORY SETUP
       ======================================================== */

    private fun createMemoryGame() {

        val values = mutableListOf<Int>()

        for (i in 0 until 6) {
            values.add(i)
            values.add(i)
        }

        values.shuffle()

        memoryCards = values
    }

    /* ========================================================
       COLOR SETUP
       ======================================================== */

    private fun createColorGame() {

        val base = arrayOf(
            Color.rgb(50, 220, 255),
            Color.rgb(50, 220, 255),
            Color.rgb(50, 220, 255),
            Color.rgb(50, 220, 255),
            Color.rgb(50, 220, 255),
            Color.rgb(50, 220, 255),
            Color.rgb(50, 220, 255),
            Color.rgb(50, 220, 255),
            Color.rgb(100, 245, 180)
        )

        colorOptions =
            base.toMutableList()

        colorOptions.shuffle()

        colorTarget =
            colorOptions.indexOfFirst {
                it != colorOptions[0]
            }
    }

    /* ========================================================
       NUMBER SETUP
       ======================================================== */

    private fun createNumberGame() {

        numberSequence =
            (1..9).shuffled().toMutableList()

        numberExpected = 1
    }

    /* ========================================================
       TOUCH
       ======================================================== */

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

            Screen.HOME ->
                homeTouch(x, y, w, h)

            Screen.GAMES ->
                gamesTouch(x, y, w, h)

            Screen.MUSIC ->
                musicTouch(x, y, w, h)

            Screen.GLOW ->
                glowTouch(x, y, w, h)

            Screen.MEMORY ->
                memoryTouch(x, y, w, h)

            Screen.REACTION ->
                reactionTouch(x, y, w, h)

            Screen.BREATHE ->
                breatheTouch(y)

            Screen.COLOR ->
                colorTouch(x, y, w)

            Screen.NUMBER ->
                numberTouch(x, y, w)

            Screen.DAILY ->
                dailyTouch(y)

            Screen.PROFILE ->
                profileTouch(y)
        }

        invalidate()

        return true
    }

    /* ========================================================
       HOME TOUCH
       ======================================================== */

    private fun homeTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y > h - 100f) {

            when {
                x < w * .20f ->
                    screen = Screen.HOME

                x < w * .40f ->
                    screen = Screen.GAMES

                x < w * .60f ->
                    screen = Screen.MUSIC

                x < w * .80f ->
                    screen = Screen.DAILY

                else ->
                    screen = Screen.PROFILE
            }

            return
        }

        /* music button */

        if (y in 200f..265f) {

            musicEnabled =
                !musicEnabled

            if (musicEnabled) {
                musicPlayer?.start()
                showMessage("Mind refreshing music ON")
            } else {
                musicPlayer?.stop()
                showMessage("Music OFF")
            }

            save()
            return
        }

        /* quick reset */

        if (y in 75f..170f) {
            startGlow(w, h)
            return
        }

        /* games */

        if (y in 290f..365f) {

            if (x < w / 2f) {
                startGlow(w, h)
            } else {
                startMemory()
            }

            return
        }

        if (y in 370f..455f) {

            if (x < w / 2f) {
                startReaction()
            } else {
                screen = Screen.BREATHE
                breatheStart =
                    System.currentTimeMillis()
            }
        }
    }

    /* ========================================================
       GAMES TOUCH
       ======================================================== */

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

        if (y > h - 100f) {

            when {
                x < w * .20f ->
                    screen = Screen.HOME

                x < w * .40f ->
                    screen = Screen.GAMES

                x < w * .60f ->
                    screen = Screen.MUSIC

                x < w * .80f ->
                    screen = Screen.DAILY

                else ->
                    screen = Screen.PROFILE
            }

            return
        }

        when {

            y in 90f..174f ->
                startGlow(w, h)

            y in 184f..268f ->
                startMemory()

            y in 278f..362f ->
                if (x < w / 2f)
                    startColor()
                else
                    startNumber()

            y > 365f ->
                screen = Screen.DAILY
        }
    }

    /* ========================================================
       MUSIC TOUCH
       ======================================================== */

    private fun musicTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 75f) {
            screen = Screen.HOME
            return
        }

        if (y > h - 100f) {
            screen = Screen.HOME
            return
        }

        if (y in 230f..315f) {

            musicEnabled =
                !musicEnabled

            if (musicEnabled) {
                musicPlayer?.start()
                showMessage("Music started")
            } else {
                musicPlayer?.stop()
                showMessage("Music paused")
            }

            save()
        }
    }

    /* ========================================================
       GLOW TOUCH
       ======================================================== */

    private fun startGlow(
        w: Float,
        h: Float
    ) {
        screen = Screen.GLOW
        glowRound++

        glowX =
            70f + Random.nextFloat() *
                    (w - 140f)

        glowY =
            150f + Random.nextFloat() *
                    min(380f, h - 310f)
    }

    private fun glowTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        val distance =
            kotlin.math.sqrt(
                (x - glowX) * (x - glowX) +
                        (y - glowY) * (y - glowY)
            )

        if (distance < 65f) {

            score += 20
            save()

            showMessage("Great focus! +20 XP")

            startGlow(w, h)
        }
    }

    /* ========================================================
       MEMORY TOUCH
       ======================================================== */

    private fun startMemory() {

        createMemoryGame()

        memoryOpen1 = -1
        memoryOpen2 = -1
        memoryMatches = 0
        memoryLocked = false

        screen = Screen.MEMORY
    }

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

        if (memoryLocked) return

        val size =
            min(w - 40f, 330f)

        val left =
            (w - size) / 2f

        val top = 100f

        if (
            x < left ||
            x > left + size ||
            y < top ||
            y > top + size
        ) return

        val cell =
            size / 4f

        val col =
            ((x - left) / cell)
                .toInt()
                .coerceIn(0, 3)

        val row =
            ((y - top) / cell)
                .toInt()
                .coerceIn(0, 2)

        val index =
            row * 4 + col

        if (index >= 12) return

        if (memoryOpen1 == -1) {

            memoryOpen1 = index

        } else if (
            memoryOpen2 == -1 &&
            index != memoryOpen1
        ) {

            memoryOpen2 = index
            memoryLocked = true

            if (
                memoryCards[memoryOpen1] ==
                memoryCards[memoryOpen2]
            ) {

                score += 30
                memoryMatches++

                showMessage("Match! +30 XP")

                memoryLocked = false
                memoryOpen1 = -1
                memoryOpen2 = -1

                if (memoryMatches >= 6) {

                    score += 50
                    showMessage("Memory complete! +50 XP")

                    createMemoryGame()
                    memoryMatches = 0
                }

                save()

            } else {

                postDelayed({

                    memoryOpen1 = -1
                    memoryOpen2 = -1
                    memoryLocked = false
                    invalidate()

                }, 650L)
            }
        }
    }

    /* ========================================================
       REACTION
       ======================================================== */

    private fun startReaction() {

        screen = Screen.REACTION

        reactionState = 1

        reactionReadyAt =
            System.currentTimeMillis() +
                    Random.nextLong(1200L, 3500L)
    }

    private fun reactionTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        if (reactionState == 0) {

            startReaction()
            return
        }

        if (reactionState == 1) {

            if (System.currentTimeMillis() >=
                reactionReadyAt
            ) {

                reactionState = 2

                reactionStartedAt =
                    System.currentTimeMillis()

            } else {

                showMessage("Too early! Try again")
                reactionState = 0
            }

            return
        }

        if (reactionState == 2) {

            val reaction =
                System.currentTimeMillis() -
                        reactionStartedAt

            if (reaction < reactionBest) {
                reactionBest = reaction
            }

            score += 25
            save()

            showMessage(
                "$reaction ms • +25 XP"
            )

            reactionState = 0
        }
    }

    /* ========================================================
       BREATHE TOUCH
       ======================================================== */

    private fun breatheTouch(y: Float) {

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        breatheStart =
            System.currentTimeMillis()

        score += 5
        save()

        showMessage("Nice slow breath • +5 XP")
    }

    /* ========================================================
       COLOR
       ======================================================== */

    private fun startColor() {

        createColorGame()

        colorRound++

        screen = Screen.COLOR
    }

    private fun colorTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        val size = 85f
        val gap = 14f

        for (i in 0 until 9) {

            val row = i / 3
            val col = i % 3

            val cx =
                w / 2f -
                        1.5f * size -
                        gap +
                        col * (size + gap) +
                        size / 2f

            val cy =
                175f +
                        row * (size + gap) +
                        size / 2f

            if (
                x >= cx - size / 2f &&
                x <= cx + size / 2f &&
                y >= cy - size / 2f &&
                y <= cy + size / 2f
            ) {

                if (i == colorTarget) {

                    score += 25
                    save()

                    showMessage(
                        "Perfect color! +25 XP"
                    )

                    startColor()

                } else {

                    showMessage(
                        "Try another tile"
                    )
                }

                return
            }
        }
    }

    /* ========================================================
       NUMBER
       ======================================================== */

    private fun startNumber() {

        createNumberGame()

        numberRound++

        screen = Screen.NUMBER
    }

    private fun numberTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        val size = 72f
        val gap = 14f

        for (i in 0 until 9) {

            val row = i / 3
            val col = i % 3

            val cx =
                w / 2f -
                        1.5f * size -
                        gap +
                        col * (size + gap) +
                        size / 2f

            val cy =
                165f +
                        row * (size + gap) +
                        size / 2f

            if (
                x >= cx - size / 2f &&
                x <= cx + size / 2f &&
                y >= cy - size / 2f &&
                y <= cy + size / 2f
            ) {

                val value =
                    numberSequence[i]

                if (value == numberExpected) {

                    numberExpected++

                    score += 5
                    save()

                    if (numberExpected > 9) {

                        score += 40
                        save()

                        showMessage(
                            "Number Flow complete! +40 XP"
                        )

                        startNumber()

                    } else {

                        showMessage(
                            "Good! Next $numberExpected"
                        )
                    }

                } else {

                    showMessage(
                        "Find $numberExpected"
                    )
                }

                return
            }
        }
    }

    /* ========================================================
       DAILY
       ======================================================== */

    private fun dailyTouch(y: Float) {

        if (y < 75f) {
            screen = Screen.HOME
            return
        }

        if (y in 210f..300f) {

            score += 50
            streak++

            save()

            showMessage(
                "Daily reset complete! +50 XP"
            )

            startGlow(width.toFloat(), height.toFloat())
        }
    }

    /* ========================================================
       PROFILE
       ======================================================== */

    private fun profileTouch(y: Float) {

        if (y < 75f) {
            screen = Screen.HOME
        }
    }

    /* ========================================================
       BACK
       ======================================================== */

    fun goBack(): Boolean {

        return if (screen == Screen.HOME) {

            false

        } else {

            screen = Screen.HOME
            invalidate()
            true
        }
    }

    /* ========================================================
       MUSIC CONTROL
       ======================================================== */

    fun resumeMusicIfEnabled() {

        if (musicEnabled) {
            musicPlayer?.start()
        }
    }

    fun pauseMusic() {

        musicPlayer?.pause()
    }
}

/* ============================================================
   BUILT-IN MIND REFRESHING MUSIC
   No MP3 file required.
   ============================================================ */

private class RelaxMusic {

    private var audioTrack: AudioTrack? = null
    private var musicThread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var paused = false

    private val sampleRate = 22050

    fun start() {

        if (running) {
            paused = false
            return
        }

        running = true
        paused = false

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

        audioTrack =
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )

        audioTrack?.setVolume(0.16f)
        audioTrack?.play()

        musicThread = Thread {

            val buffer =
                ShortArray(sampleRate / 4)

            var samplePosition = 0L

            val notes = doubleArrayOf(
                261.63,
                329.63,
                392.00,
                329.63,
                293.66,
                349.23,
                440.00,
                349.23
            )

            while (running) {

                if (paused) {

                    try {
                        Thread.sleep(80L)
                    } catch (_: InterruptedException) {
                    }

                    continue
                }

                for (i in buffer.indices) {

                    val global =
                        samplePosition + i

                    val beat =
                        global.toDouble() /
                                sampleRate.toDouble()

                    val noteIndex =
                        ((beat / 1.8).toInt()) %
                                notes.size

                    val frequency =
                        notes[noteIndex]

                    val local =
                        beat % 1.8

                    val fadeIn =
                        min(
                            1.0,
                            local / 0.18
                        )

                    val fadeOut =
                        min(
                            1.0,
                            (1.8 - local) / 0.35
                        )

                    val envelope =
                        fadeIn * fadeOut

                    val wave =
                        sin(
                            2.0 *
                                    Math.PI *
                                    frequency *
                                    beat
                        )

                    val soft =
                        sin(
                            2.0 *
                                    Math.PI *
                                    frequency *
                                    2.0 *
                                    beat
                        ) * 0.18

                    val value =
                        (wave + soft) *
                                envelope *
                                0.18

                    buffer[i] =
                        (value * 32767.0)
                            .toInt()
                            .coerceIn(
                                Short.MIN_VALUE.toInt(),
                                Short.MAX_VALUE.toInt()
                            )
                            .toShort()
                }

                samplePosition += buffer.size

                try {

                    audioTrack?.write(
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
    }

    fun pause() {
        paused = true

        try {
            audioTrack?.pause()
        } catch (_: Exception) {
        }
    }

    fun stop() {

        paused = true
        running = false

        try {
            musicThread?.interrupt()
        } catch (_: Exception) {
        }

        musicThread = null

        try {
            audioTrack?.stop()
        } catch (_: Exception) {
        }

        try {
            audioTrack?.release()
        } catch (_: Exception) {
        }

        audioTrack = null
    }
}
