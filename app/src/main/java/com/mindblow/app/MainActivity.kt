package com.mindblow.app

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var mindView: MindBlowView
    private var music: RelaxingMusic? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(5, 6, 25)
        window.navigationBarColor = Color.rgb(5, 6, 25)

        mindView = MindBlowView(this)

        music = RelaxingMusic()

        mindView.onMusicChanged = { enabled ->
            if (enabled) {
                music?.start()
            } else {
                music?.stop()
            }
        }

        setContentView(mindView)
    }

    override fun onResume() {
        super.onResume()

        if (mindView.isMusicEnabled()) {
            music?.start()
        }
    }

    override fun onPause() {
        music?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        music?.release()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!mindView.goBack()) {
            super.onBackPressed()
        }
    }
}

/* ============================================================
   PAGES
   ============================================================ */

private enum class Page {
    INTRO,
    SELECT_GAME,
    READY,
    PLAY
}

/* ============================================================
   GAMES
   ============================================================ */

private enum class GameType {
    PULSE,
    MEMORY,
    COLOR,
    REACTION,
    NUMBER,
    ZEN
}

/* ============================================================
   MAIN VIEW
   ============================================================ */

private class MindBlowView(
    private val ctx: Context
) : View(ctx) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs =
        ctx.getSharedPreferences("mindblow_settings", Context.MODE_PRIVATE)

    private var page = Page.INTRO
    private var selectedGame = GameType.PULSE

    private var score = prefs.getInt("score", 0)
    private var musicEnabled =
        prefs.getBoolean("music_enabled", true)

    private var animation = 0f
    private var lastTime = System.currentTimeMillis()

    var onMusicChanged: ((Boolean) -> Unit)? = null

    /* --------------------------------------------------------
       GAME VARIABLES
       -------------------------------------------------------- */

    // Pulse
    private var pulseX = 180f
    private var pulseY = 450f
    private var pulseRadius = 48f
    private var pulseHits = 0

    // Memory
    private var memorySequence = mutableListOf<Int>()
    private var memoryInput = 0
    private var memoryShowing = true
    private var memoryStartTime = 0L
    private var memoryLength = 3
    private var memoryResult = ""

    // Color
    private var colorTarget = 0
    private var colorOptions = mutableListOf<Int>()
    private var colorResult = ""

    // Reaction
    private var reactionState = 0
    private var reactionStart = 0L
    private var reactionTime = 0L
    private var reactionBest = Long.MAX_VALUE

    // Number
    private var numberNext = 1
    private var numberStart = 0L
    private var numberResult = ""

    // Zen
    private var zenPhase = 0
    private var zenCycles = 0
    private var zenPhaseStart = 0L
    private var zenStarted = false

    private var toastText = ""
    private var toastUntil = 0L

    init {
        isFocusable = true
        resetGame()
        postInvalidateDelayed(16L)
    }

    /* ========================================================
       DRAW
       ======================================================== */

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val now = System.currentTimeMillis()
        val dt = (now - lastTime).coerceAtMost(50L)
        lastTime = now

        animation += dt / 1000f

        val scale = min(
            width / 360f,
            height / 800f
        )

        canvas.save()
        canvas.scale(scale, scale)

        val designW = width / scale
        val designH = height / scale

        drawBackground(canvas, designW, designH)

        when (page) {
            Page.INTRO ->
                drawIntro(canvas, designW, designH)

            Page.SELECT_GAME ->
                drawSelectGame(canvas, designW, designH)

            Page.READY ->
                drawReady(canvas, designW, designH)

            Page.PLAY ->
                drawGameplay(canvas, designW, designH)
        }

        if (toastUntil > now) {
            drawToast(canvas, designW, designH)
        }

        canvas.restore()

        updateGame()

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
            Color.rgb(4, 6, 27),
            Color.rgb(31, 7, 57),
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

        // Animated cyan orb
        paint.color = Color.argb(35, 0, 220, 255)

        canvas.drawCircle(
            55f + sin(animation * 0.45f) * 25f,
            240f + cos(animation * 0.35f) * 25f,
            82f,
            paint
        )

        // Animated purple orb
        paint.color = Color.argb(35, 170, 80, 255)

        canvas.drawCircle(
            w - 30f + cos(animation * 0.4f) * 20f,
            360f + sin(animation * 0.3f) * 25f,
            75f,
            paint
        )

        // Lower orb
        paint.color = Color.argb(25, 30, 130, 255)

        canvas.drawCircle(
            190f + sin(animation * 0.25f) * 30f,
            h - 110f,
            65f,
            paint
        )

        // Mind waves
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(22, 100, 210, 255)

        val centerX = w / 2f
        val centerY = h * 0.58f

        for (i in 0 until 7) {
            canvas.drawCircle(
                centerX,
                centerY,
                32f + i * 28f + sin(animation + i) * 2f,
                paint
            )
        }

        paint.style = Paint.Style.FILL

        // Stars
        for (i in 0 until 24) {
            val sx =
                ((i * 71) % 360).toFloat()

            val sy =
                ((i * 113) % h.toInt().coerceAtLeast(1)).toFloat()

            val alpha =
                (45 + 30 * sin(animation * 0.7f + i)).toInt()
                    .coerceIn(15, 80)

            paint.color =
                Color.argb(alpha, 120, 220, 255)

            canvas.drawCircle(
                sx,
                sy,
                1.2f,
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

    /* ========================================================
       PANEL
       ======================================================== */

    private fun panel(
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
            80,
            80,
            150,
            220
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
        b: Float,
        enabled: Boolean = true
    ) {
        paint.shader = LinearGradient(
            l,
            t,
            r,
            b,
            if (enabled)
                Color.rgb(35, 220, 255)
            else
                Color.rgb(55, 65, 90),
            if (enabled)
                Color.rgb(125, 65, 255)
            else
                Color.rgb(55, 65, 90),
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
            (t + b) / 2f + 6f,
            15f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    /* ========================================================
       INTRO PAGE
       ======================================================== */

    private fun drawIntro(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "MINDBLOW",
            22f,
            48f,
            22f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "RELAX • FOCUS • REFRESH",
            22f,
            67f,
            9f,
            0xff91a0c8.toInt()
        )

        // Large icon
        drawMindIcon(
            canvas,
            w / 2f,
            235f,
            1.8f
        )

        text(
            canvas,
            "You need to",
            w / 2f,
            370f,
            22f,
            0xffb7c4e9.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "REFRESH YOUR MIND",
            w / 2f,
            408f,
            30f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Take a tiny break.",
            w / 2f,
            440f,
            14f,
            0xffa5b0d3.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Play quick games designed to reset",
            w / 2f,
            468f,
            12f,
            0xff8e9abb.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "your attention and calm your mind.",
            w / 2f,
            489f,
            12f,
            0xff8e9abb.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            canvas,
            "START REFRESHING  →",
            35f,
            540f,
            w - 35f,
            596f
        )

        panel(
            canvas,
            35f,
            620f,
            w - 35f,
            680f,
            18f
        )

        text(
            canvas,
            "♪",
            58f,
            655f,
            25f,
            0xff55e6ff.toInt(),
            true
        )

        text(
            canvas,
            "Mind Refreshing Music",
            90f,
            648f,
            13f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Ambient soundtrack",
            90f,
            666f,
            10f,
            0xff8e9abb.toInt()
        )

        musicSwitch(
            canvas,
            w - 72f,
            650f
        )
    }

    /* ========================================================
       GAME SELECTION PAGE
       ======================================================== */

    private fun drawSelectGame(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "SELECT YOUR",
            20f,
            48f,
            12f,
            0xff66e5ff.toInt(),
            true
        )

        text(
            canvas,
            "MIND REFRESHING GAME",
            20f,
            79f,
            27f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Choose a quick challenge to reset your attention.",
            20f,
            101f,
            11f,
            0xff99a6cb.toInt()
        )

        gameCard(
            canvas,
            GameType.PULSE,
            12f,
            130f,
            w / 2f - 7f,
            235f
        )

        gameCard(
            canvas,
            GameType.MEMORY,
            w / 2f + 7f,
            130f,
            w - 12f,
            235f
        )

        gameCard(
            canvas,
            GameType.COLOR,
            12f,
            248f,
            w / 2f - 7f,
            353f
        )

        gameCard(
            canvas,
            GameType.REACTION,
            w / 2f + 7f,
            248f,
            w - 12f,
            353f
        )

        gameCard(
            canvas,
            GameType.NUMBER,
            12f,
            366f,
            w / 2f - 7f,
            471f
        )

        gameCard(
            canvas,
            GameType.ZEN,
            w / 2f + 7f,
            366f,
            w - 12f,
            471f
        )

        panel(
            canvas,
            12f,
            495f,
            w - 12f,
            555f,
            18f
        )

        text(
            canvas,
            "YOUR SCORE",
            28f,
            518f,
            9f,
            0xff8d9bc2.toInt(),
            true
        )

        text(
            canvas,
            "$score XP",
            28f,
            541f,
            18f,
            0xffffd86b.toInt(),
            true
        )

        text(
            canvas,
            "Pick a game above",
            w - 28f,
            535f,
            10f,
            0xff7e8baa.toInt(),
            false,
            Paint.Align.RIGHT
        )

        bottomBack(
            canvas,
            w,
            h
        )
    }

    /* ========================================================
       READY PAGE
       ======================================================== */

    private fun drawReady(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "‹",
            20f,
            48f,
            38f,
            Color.WHITE,
            false
        )

        text(
            canvas,
            "GET READY",
            62f,
            44f,
            13f,
            0xff6ce8ff.toInt(),
            true
        )

        text(
            canvas,
            gameName(selectedGame),
            62f,
            64f,
            21f,
            Color.WHITE,
            true
        )

        drawLargeGameIcon(
            canvas,
            selectedGame,
            w / 2f,
            235f
        )

        text(
            canvas,
            gameSubtitle(selectedGame),
            w / 2f,
            375f,
            18f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            gameDescription(selectedGame),
            w / 2f,
            403f,
            12f,
            0xff9aa7cc.toInt(),
            false,
            Paint.Align.CENTER
        )

        panel(
            canvas,
            30f,
            440f,
            w - 30f,
            515f,
            18f
        )

        text(
            canvas,
            "HOW TO PLAY",
            48f,
            466f,
            10f,
            0xff62e5ff.toInt(),
            true
        )

        text(
            canvas,
            gameHowTo(selectedGame),
            48f,
            491f,
            12f,
            0xffd1d8ec.toInt()
        )

        button(
            canvas,
            "PLAY NOW  →",
            30f,
            550f,
            w - 30f,
            610f
        )

        musicSmall(
            canvas,
            w,
            650f
        )
    }

    /* ========================================================
       GAMEPLAY
       ======================================================== */

    private fun drawGameplay(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "‹",
            18f,
            47f,
            38f,
            Color.WHITE
        )

        text(
            canvas,
            gameName(selectedGame),
            60f,
            39f,
            19f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "XP $score",
            w - 18f,
            39f,
            11f,
            0xffffd86b.toInt(),
            true,
            Paint.Align.RIGHT
        )

        when (selectedGame) {
            GameType.PULSE ->
                drawPulse(canvas, w, h)

            GameType.MEMORY ->
                drawMemory(canvas, w, h)

            GameType.COLOR ->
                drawColor(canvas, w, h)

            GameType.REACTION ->
                drawReaction(canvas, w, h)

            GameType.NUMBER ->
                drawNumber(canvas, w, h)

            GameType.ZEN ->
                drawZen(canvas, w, h)
        }

        musicSmall(
            canvas,
            w,
            h - 80f
        )
    }

    /* ========================================================
       PULSE HUNT
       ======================================================== */

    private fun drawPulse(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "PULSE HUNT",
            w / 2f,
            95f,
            13f,
            0xff61e6ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Tap the glowing pulse",
            w / 2f,
            120f,
            19f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Hits: $pulseHits",
            w / 2f,
            148f,
            12f,
            0xff9eabce.toInt(),
            false,
            Paint.Align.CENTER
        )

        val pulse =
            pulseRadius + sin(animation * 5f) * 5f

        paint.color =
            Color.argb(
                35,
                50,
                220,
                255
            )

        canvas.drawCircle(
            pulseX,
            pulseY,
            pulse + 35f,
            paint
        )

        paint.color =
            0xff4eeaff.toInt()

        canvas.drawCircle(
            pulseX,
            pulseY,
            pulse,
            paint
        )

        paint.color =
            Color.WHITE

        canvas.drawCircle(
            pulseX,
            pulseY,
            9f,
            paint
        )

        text(
            canvas,
            "Find • Tap • Refresh",
            w / 2f,
            610f,
            13f,
            0xff8e9abb.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    /* ========================================================
       MEMORY MATRIX
       ======================================================== */

    private fun drawMemory(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            if (memoryShowing)
                "MEMORIZE THE GLOW"
            else
                "REPEAT THE PATTERN",
            w / 2f,
            100f,
            19f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            if (memoryShowing)
                "Watch the highlighted tiles"
            else
                "Tap them in the same order",
            w / 2f,
            126f,
            11f,
            0xff9da9ca.toInt(),
            false,
            Paint.Align.CENTER
        )

        val size = 70f
        val gap = 9f
        val startX = 53f
        val startY = 170f

        for (i in 0 until 16) {
            val row = i / 4
            val col = i % 4

            val l = startX + col * (size + gap)
            val t = startY + row * (size + gap)

            val highlighted =
                memoryShowing &&
                        memorySequence.contains(i)

            paint.color =
                if (highlighted)
                    0xff50e6ff.toInt()
                else
                    0xff101936.toInt()

            canvas.drawRoundRect(
                l,
                t,
                l + size,
                t + size,
                15f,
                15f,
                paint
            )

            paint.style = Paint.Style.STROKE
            paint.color =
                if (highlighted)
                    0xff8af2ff.toInt()
                else
                    0xff27365e.toInt()

            canvas.drawRoundRect(
                l,
                t,
                l + size,
                t + size,
                15f,
                15f,
                paint
            )

            paint.style = Paint.Style.FILL

            if (highlighted) {
                paint.color = Color.WHITE

                canvas.drawCircle(
                    l + size / 2f,
                    t + size / 2f,
                    10f,
                    paint
                )
            }
        }

        if (memoryResult.isNotEmpty()) {
            text(
                canvas,
                memoryResult,
                w / 2f,
                550f,
                18f,
                0xff64e8ff.toInt(),
                true,
                Paint.Align.CENTER
            )
        }
    }

    /* ========================================================
       COLOR MATCH
       ======================================================== */

    private fun drawColor(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        val names = arrayOf(
            "CYAN",
            "PINK",
            "YELLOW",
            "GREEN"
        )

        val colors = intArrayOf(
            0xff42e6ff.toInt(),
            0xffff5ca8.toInt(),
            0xffffd447.toInt(),
            0xff55e88b.toInt()
        )

        text(
            canvas,
            "COLOR MATCH",
            w / 2f,
            98f,
            20f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Tap the color that matches",
            w / 2f,
            125f,
            12f,
            0xff9aa7c8.toInt(),
            false,
            Paint.Align.CENTER
        )

        paint.color = colors[colorTarget]

        canvas.drawCircle(
            w / 2f,
            225f,
            62f,
            paint
        )

        text(
            canvas,
            names[colorTarget],
            w / 2f,
            315f,
            27f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        for (i in 0 until 4) {
            val row = i / 2
            val col = i % 2

            val l = 25f + col * 165f
            val t = 370f + row * 90f
            val r = l + 145f
            val b = t + 65f

            paint.color = colors[colorOptions[i]]

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
                names[colorOptions[i]],
                (l + r) / 2f,
                t + 41f,
                13f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        if (colorResult.isNotEmpty()) {
            text(
                canvas,
                colorResult,
                w / 2f,
                570f,
                17f,
                0xff61e7ff.toInt(),
                true,
                Paint.Align.CENTER
            )
        }
    }

    /* ========================================================
       QUICK REACTION
       ======================================================== */

    private fun drawReaction(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "QUICK REACTION",
            w / 2f,
            100f,
            20f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "React as soon as GO appears",
            w / 2f,
            127f,
            12f,
            0xff9aa7c8.toInt(),
            false,
            Paint.Align.CENTER
        )

        val color =
            when (reactionState) {
                2 -> 0xff49e889.toInt()
                1 -> 0xffffc44f.toInt()
                else -> 0xff273354.toInt()
            }

        paint.color = color

        canvas.drawCircle(
            w / 2f,
            290f,
            105f,
            paint
        )

        if (reactionState == 0) {
            text(
                canvas,
                "START",
                w / 2f,
                300f,
                24f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        } else if (reactionState == 1) {
            text(
                canvas,
                "WAIT...",
                w / 2f,
                300f,
                22f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        } else {
            text(
                canvas,
                "GO!",
                w / 2f,
                300f,
                30f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        if (reactionTime > 0) {
            text(
                canvas,
                "${reactionTime} ms",
                w / 2f,
                455f,
                28f,
                0xff61e7ff.toInt(),
                true,
                Paint.Align.CENTER
            )
        }

        text(
            canvas,
            if (reactionBest == Long.MAX_VALUE)
                "Best: --"
            else
                "Best: ${reactionBest} ms",
            w / 2f,
            485f,
            12f,
            0xff9ba7c7.toInt(),
            false,
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
        text(
            canvas,
            "NUMBER FLOW",
            w / 2f,
            95f,
            20f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Tap numbers from 1 to 16",
            w / 2f,
            122f,
            12f,
            0xff9aa7c8.toInt(),
            false,
            Paint.Align.CENTER
        )

        val size = 68f
        val gap = 10f
        val startX = 35f
        val startY = 165f

        val nums =
            (1..16).shuffled()

        // deterministic-looking animated layout
        for (i in 0 until 16) {
            val row = i / 4
            val col = i % 4

            val l =
                startX + col * (size + gap)

            val t =
                startY + row * (size + gap)

            panel(
                canvas,
                l,
                t,
                l + size,
                t + size,
                15f
            )

            text(
                canvas,
                nums[i].toString(),
                l + size / 2f,
                t + 43f,
                20f,
                if (nums[i] == numberNext)
                    0xff55e7ff.toInt()
                else
                    Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        text(
            canvas,
            "NEXT: $numberNext",
            w / 2f,
            545f,
            20f,
            0xffffd86b.toInt(),
            true,
            Paint.Align.CENTER
        )

        if (numberResult.isNotEmpty()) {
            text(
                canvas,
                numberResult,
                w / 2f,
                580f,
                14f,
                0xff60e6ff.toInt(),
                true,
                Paint.Align.CENTER
            )
        }
    }

    /* ========================================================
       ZEN BREATH
       ======================================================== */

    private fun drawZen(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            canvas,
            "ZEN BREATH",
            w / 2f,
            98f,
            20f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Slow down and breathe",
            w / 2f,
            125f,
            12f,
            0xff9aa7c8.toInt(),
            false,
            Paint.Align.CENTER
        )

        val elapsed =
            (System.currentTimeMillis() - zenPhaseStart) / 1000f

        val phaseProgress =
            (elapsed / 4f).coerceIn(0f, 1f)

        val breathProgress =
            if (zenPhase % 2 == 0)
                phaseProgress
            else
                1f - phaseProgress

        val radius =
            55f + breathProgress * 60f

        paint.color =
            Color.argb(
                35,
                60,
                230,
                255
            )

        canvas.drawCircle(
            w / 2f,
            290f,
            radius + 25f,
            paint
        )

        paint.color =
            0xff52e4cf.toInt()

        canvas.drawCircle(
            w / 2f,
            290f,
            radius,
            paint
        )

        val instruction =
            if (!zenStarted)
                "Tap START to begin"
            else if (zenPhase % 2 == 0)
                "BREATHE IN"
            else
                "BREATHE OUT"

        text(
            canvas,
            instruction,
            w / 2f,
            300f,
            20f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Cycles completed: $zenCycles / 3",
            w / 2f,
            455f,
            14f,
            0xffb7c2df.toInt(),
            false,
            Paint.Align.CENTER
        )

        if (!zenStarted) {
            button(
                canvas,
                "START BREATHING",
                55f,
                500f,
                w - 55f,
                558f
            )
        } else if (zenCycles >= 3) {
            button(
                canvas,
                "FINISH  +25 XP",
                55f,
                500f,
                w - 55f,
                558f
            )
        }
    }

    /* ========================================================
       GAME CARDS
       ======================================================== */

    private fun gameCard(
        canvas: Canvas,
        game: GameType,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {
        panel(
            canvas,
            l,
            t,
            r,
            b,
            18f
        )

        drawSmallGameIcon(
            canvas,
            game,
            l + 38f,
            t + 47f
        )

        text(
            canvas,
            gameName(game),
            l + 72f,
            t + 38f,
            13f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            gameShort(game),
            l + 72f,
            t + 57f,
            9f,
            0xff8f9cbe.toInt()
        )

        text(
            canvas,
            "TAP →",
            r - 12f,
            b - 13f,
            8f,
            0xff55e6ff.toInt(),
            true,
            Paint.Align.RIGHT
        )
    }

    /* ========================================================
       ICONS
       ======================================================== */

    private fun drawMindIcon(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        scale: Float
    ) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f * scale
        paint.color = 0xff58e8ff.toInt()

        canvas.drawCircle(
            cx,
            cy,
            48f * scale,
            paint
        )

        paint.color = 0xff9a62ff.toInt()

        canvas.drawCircle(
            cx,
            cy,
            25f * scale,
            paint
        )

        paint.style = Paint.Style.FILL

        paint.color = Color.WHITE

        canvas.drawCircle(
            cx,
            cy,
            8f * scale,
            paint
        )
    }

    private fun drawSmallGameIcon(
        canvas: Canvas,
        game: GameType,
        cx: Float,
        cy: Float
    ) {
        when (game) {
            GameType.PULSE -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                paint.color = 0xff4eeaff.toInt()

                canvas.drawCircle(
                    cx,
                    cy,
                    16f,
                    paint
                )

                canvas.drawCircle(
                    cx,
                    cy,
                    8f,
                    paint
                )

                paint.style = Paint.Style.FILL
            }

            GameType.MEMORY -> {
                paint.color = 0xff63e5ff.toInt()

                for (i in 0 until 4) {
                    val x =
                        cx + (i % 2) * 12f - 6f
                    val y =
                        cy + (i / 2) * 12f - 6f

                    canvas.drawCircle(
                        x,
                        y,
                        4f,
                        paint
                    )
                }
            }

            GameType.COLOR -> {
                val cs = intArrayOf(
                    0xffff63ad.toInt(),
                    0xff55eaff.toInt(),
                    0xffffd34e.toInt()
                )

                for (i in 0..2) {
                    paint.color = cs[i]

                    canvas.drawCircle(
                        cx + (i - 1) * 11f,
                        cy,
                        6f,
                        paint
                    )
                }
            }

            GameType.REACTION -> {
                paint.color = 0xffffd447.toInt()

                val path = Path()

                path.moveTo(cx + 4f, cy - 20f)
                path.lineTo(cx - 8f, cy)
                path.lineTo(cx + 2f, cy)
                path.lineTo(cx - 4f, cy + 20f)
                path.lineTo(cx + 12f, cy - 4f)
                path.lineTo(cx + 3f, cy - 4f)
                path.close()

                canvas.drawPath(
                    path,
                    paint
                )
            }

            GameType.NUMBER -> {
                text(
                    canvas,
                    "123",
                    cx,
                    cy + 7f,
                    17f,
                    0xff58e8ff.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            }

            GameType.ZEN -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                paint.color = 0xff63e8ce.toInt()

                canvas.drawCircle(
                    cx,
                    cy,
                    15f,
                    paint
                )

                canvas.drawCircle(
                    cx,
                    cy,
                    7f,
                    paint
                )

                paint.style = Paint.Style.FILL
            }
        }
    }

    private fun drawLargeGameIcon(
        canvas: Canvas,
        game: GameType,
        cx: Float,
        cy: Float
    ) {
        paint.color =
            Color.argb(
                40,
                60,
                220,
                255
            )

        canvas.drawCircle(
            cx,
            cy,
            105f,
            paint
        )

        paint.color =
            Color.argb(
                20,
                150,
                80,
                255
            )

        canvas.drawCircle(
            cx,
            cy,
            135f,
            paint
        )

        drawSmallGameIconLarge(
            canvas,
            game,
            cx,
            cy
        )
    }

    private fun drawSmallGameIconLarge(
        canvas: Canvas,
        game: GameType,
        cx: Float,
        cy: Float
    ) {
        when (game) {
            GameType.PULSE -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 9f
                paint.color = 0xff55eaff.toInt()

                canvas.drawCircle(
                    cx,
                    cy,
                    70f,
                    paint
                )

                canvas.drawCircle(
                    cx,
                    cy,
                    38f,
                    paint
                )

                paint.style = Paint.Style.FILL

                paint.color = Color.WHITE

                canvas.drawCircle(
                    cx,
                    cy,
                    12f,
                    paint
                )
            }

            GameType.MEMORY -> {
                paint.color = 0xff61e7ff.toInt()

                for (i in 0 until 9) {
                    val x =
                        cx + (i % 3 - 1) * 38f
                    val y =
                        cy + (i / 3 - 1) * 38f

                    canvas.drawCircle(
                        x,
                        y,
                        13f,
                        paint
                    )
                }
            }

            GameType.COLOR -> {
                val colors = intArrayOf(
                    0xffff5fa9.toInt(),
                    0xff52eaff.toInt(),
                    0xffffd447.toInt(),
                    0xff61e995.toInt()
                )

                for (i in 0 until 4) {
                    paint.color = colors[i]

                    canvas.drawCircle(
                        cx + (i % 2) * 60f - 30f,
                        cy + (i / 2) * 60f - 30f,
                        25f,
                        paint
                    )
                }
            }

            GameType.REACTION -> {
                paint.color = 0xffffd447.toInt()

                val path = Path()

                path.moveTo(cx + 20f, cy - 75f)
                path.lineTo(cx - 30f, cy)
                path.lineTo(cx + 8f, cy)
                path.lineTo(cx - 20f, cy + 75f)
                path.lineTo(cx + 42f, cy - 10f)
                path.lineTo(cx + 8f, cy - 10f)
                path.close()

                canvas.drawPath(
                    path,
                    paint
                )
            }

            GameType.NUMBER -> {
                text(
                    canvas,
                    "123",
                    cx,
                    cy + 22f,
                    48f,
                    0xff5de7ff.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            }

            GameType.ZEN -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 9f
                paint.color = 0xff5fe7d1.toInt()

                canvas.drawCircle(
                    cx,
                    cy,
                    65f,
                    paint
                )

                canvas.drawCircle(
                    cx,
                    cy,
                    35f,
                    paint
                )

                paint.style = Paint.Style.FILL
            }
        }
    }

    /* ========================================================
       MUSIC SWITCH
       ======================================================== */

    private fun musicSwitch(
        canvas: Canvas,
        cx: Float,
        cy: Float
    ) {
        paint.color =
            if (musicEnabled)
                0xff4de0e9.toInt()
            else
                0xff303a5b.toInt()

        canvas.drawRoundRect(
            cx - 32f,
            cy - 16f,
            cx + 32f,
            cy + 16f,
            18f,
            18f,
            paint
        )

        text(
            canvas,
            if (musicEnabled)
                "ON"
            else
                "OFF",
            cx,
            cy + 5f,
            10f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    private fun musicSmall(
        canvas: Canvas,
        w: Float,
        y: Float
    ) {
        panel(
            canvas,
            18f,
            y,
            w - 18f,
            y + 45f,
            15f
        )

        text(
            canvas,
            "♪",
            35f,
            y + 29f,
            21f,
            0xff59e8ff.toInt(),
            true
        )

        text(
            canvas,
            "Mind Refreshing Music",
            58f,
            y + 20f,
            10f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            if (musicEnabled)
                "Playing"
            else
                "Paused",
            58f,
            y + 34f,
            8f,
            0xff8996b9.toInt()
        )

        musicSwitch(
            canvas,
            w - 50f,
            y + 22f
        )
    }

    /* ========================================================
       BOTTOM BACK
       ======================================================== */

    private fun bottomBack(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        button(
            canvas,
            "BACK",
            35f,
            h - 75f,
            w - 35f,
            h - 22f
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
        panel(
            canvas,
            45f,
            h - 130f,
            w - 45f,
            h - 85f,
            18f
        )

        text(
            canvas,
            toastText,
            w / 2f,
            h - 102f,
            12f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    private fun toast(value: String) {
        toastText = value
        toastUntil = System.currentTimeMillis() + 1200L
    }

    /* ========================================================
       TOUCH
       ======================================================== */

    override fun onTouchEvent(event: MotionEvent): Boolean {

        if (event.action != MotionEvent.ACTION_UP) {
            return true
        }

        val scale =
            min(
                width / 360f,
                height / 800f
            )

        val x = event.x / scale
        val y = event.y / scale

        when (page) {

            Page.INTRO ->
                touchIntro(x, y)

            Page.SELECT_GAME ->
                touchSelectGame(x, y)

            Page.READY ->
                touchReady(x, y)

            Page.PLAY ->
                touchPlay(x, y)
        }

        return true
    }

    /* ========================================================
       INTRO TOUCH
       ======================================================== */

    private fun touchIntro(
        x: Float,
        y: Float
    ) {
        if (y in 530f..610f) {
            page = Page.SELECT_GAME
            return
        }

        if (y in 620f..690f) {
            musicEnabled = !musicEnabled

            prefs.edit()
                .putBoolean(
                    "music_enabled",
                    musicEnabled
                )
                .apply()

            onMusicChanged?.invoke(
                musicEnabled
            )
        }
    }

    /* ========================================================
       SELECT TOUCH
       ======================================================== */

    private fun touchSelectGame(
        x: Float,
        y: Float
    ) {
        when {
            y in 125f..240f &&
                    x < 180f -> {
                chooseGame(GameType.PULSE)
            }

            y in 125f..240f &&
                    x >= 180f -> {
                chooseGame(GameType.MEMORY)
            }

            y in 243f..360f &&
                    x < 180f -> {
                chooseGame(GameType.COLOR)
            }

            y in 243f..360f &&
                    x >= 180f -> {
                chooseGame(GameType.REACTION)
            }

            y in 360f..480f &&
                    x < 180f -> {
                chooseGame(GameType.NUMBER)
            }

            y in 360f..480f &&
                    x >= 180f -> {
                chooseGame(GameType.ZEN)
            }

            y > 700f -> {
                page = Page.INTRO
            }
        }
    }

    private fun chooseGame(
        game: GameType
    ) {
        selectedGame = game
        resetGame()
        page = Page.READY
    }

    /* ========================================================
       READY TOUCH
       ======================================================== */

    private fun touchReady(
        x: Float,
        y: Float
    ) {
        if (y in 540f..630f) {
            resetGame()
            page = Page.PLAY
            return
        }

        if (y < 90f && x < 60f) {
            page = Page.SELECT_GAME
        }
    }

    /* ========================================================
       GAME TOUCH
       ======================================================== */

    private fun touchPlay(
        x: Float,
        y: Float
    ) {
        if (y < 75f && x < 60f) {
            page = Page.READY
            return
        }

        when (selectedGame) {

            GameType.PULSE ->
                touchPulse(x, y)

            GameType.MEMORY ->
                touchMemory(x, y)

            GameType.COLOR ->
                touchColor(x, y)

            GameType.REACTION ->
                touchReaction(x, y)

            GameType.NUMBER ->
                touchNumber(x, y)

            GameType.ZEN ->
                touchZen(x, y)
        }
    }

    /* ========================================================
       PULSE TOUCH
       ======================================================== */

    private fun touchPulse(
        x: Float,
        y: Float
    ) {
        val dx = x - pulseX
        val dy = y - pulseY

        if (dx * dx + dy * dy <=
            (pulseRadius + 25f) *
            (pulseRadius + 25f)
        ) {

            pulseHits++
            score += 10

            saveScore()

            pulseX =
                Random.nextInt(
                    45,
                    315
                ).toFloat()

            pulseY =
                Random.nextInt(
                    190,
                    570
                ).toFloat()

            toast(
                "+10 XP  •  Great focus!"
            )
        }
    }

    /* ========================================================
       MEMORY TOUCH
       ======================================================== */

    private fun touchMemory(
        x: Float,
        y: Float
    ) {
        if (memoryShowing) return

        val size = 70f
        val gap = 9f
        val startX = 53f
        val startY = 170f

        if (x < startX ||
            y < startY
        ) return

        val col =
            ((x - startX) /
                    (size + gap))
                .toInt()

        val row =
            ((y - startY) /
                    (size + gap))
                .toInt()

        if (col !in 0..3 ||
            row !in 0..3
        ) return

        val index =
            row * 4 + col

        if (memoryInput >= memorySequence.size)
            return

        if (index ==
            memorySequence[memoryInput]
        ) {

            memoryInput++

            if (memoryInput ==
                memorySequence.size
            ) {

                score += 25
                saveScore()

                memoryResult =
                    "Pattern complete! +25 XP"

                toast(
                    "Memory complete! +25 XP"
                )

                resetMemory()
            }

        } else {

            memoryResult =
                "Almost! Try again"

            toast(
                "Pattern missed — try again"
            )

            resetMemory()
        }
    }

    /* ========================================================
       COLOR TOUCH
       ======================================================== */

    private fun touchColor(
        x: Float,
        y: Float
    ) {
        if (y !in 360f..550f)
            return

        val col =
            if (x < 180f) 0 else 1

        val row =
            if (y < 455f) 0 else 1

        val index =
            row * 2 + col

        if (index !in 0..3)
            return

        if (colorOptions[index] ==
            colorTarget
        ) {

            score += 15
            saveScore()

            colorResult =
                "Perfect match! +15 XP"

            toast(
                "Perfect! +15 XP"
            )

            setupColor()

        } else {

            colorResult =
                "Try again"

            toast(
                "Find the matching color"
            )
        }
    }

    /* ========================================================
       REACTION TOUCH
       ======================================================== */

    private fun touchReaction(
        x: Float,
        y: Float
    ) {
        val dx =
            x - width / min(
                width / 360f,
                height / 800f
            ) / 2f

        val dy =
            y - 290f

        if (dx * dx + dy * dy > 130f * 130f)
            return

        when (reactionState) {

            0 -> {
                reactionState = 1

                reactionStart =
                    System.currentTimeMillis()
            }

            1 -> {
                toast(
                    "Too early! Wait for GO"
                )

                reactionState = 0
            }

            2 -> {

                reactionTime =
                    System.currentTimeMillis() -
                            reactionStart -
                            0L

                if (reactionTime < reactionBest) {
                    reactionBest = reactionTime
                }

                score +=
                    if (reactionTime < 500)
                        25
                    else
                        15

                saveScore()

                toast(
                    "${reactionTime} ms  • +XP"
                )

                reactionState = 0
            }
        }
    }

    /* ========================================================
       NUMBER TOUCH
       ======================================================== */

    private fun touchNumber(
        x: Float,
        y: Float
    ) {
        val size = 68f
        val gap = 10f
        val startX = 35f
        val startY = 165f

        if (x < startX ||
            y < startY
        ) return

        val col =
            ((x - startX) /
                    (size + gap))
                .toInt()

        val row =
            ((y - startY) /
                    (size + gap))
                .toInt()

        if (col !in 0..3 ||
            row !in 0..3
        ) return

        val index =
            row * 4 + col

        val number =
            numberLayout[index]

        if (number ==
            numberNext
        ) {

            if (numberNext == 1) {
                numberStart =
                    System.currentTimeMillis()
            }

            numberNext++

            if (numberNext > 16) {

                val elapsed =
                    System.currentTimeMillis() -
                            numberStart

                score += 30
                saveScore()

                numberResult =
                    "Finished in ${elapsed} ms"

                toast(
                    "Number Flow complete! +30 XP"
                )

                resetNumber()
            }

        } else {

            toast(
                "Tap $numberNext next"
            )
        }
    }

    /* ========================================================
       ZEN TOUCH
       ======================================================== */

    private fun touchZen(
        x: Float,
        y: Float
    ) {
        if (!zenStarted &&
            y in 490f..570f
        ) {

            zenStarted = true
            zenPhase = 0
            zenCycles = 0
            zenPhaseStart =
                System.currentTimeMillis()

            return
        }

        if (zenStarted &&
            zenCycles >= 3 &&
            y in 490f..570f
        ) {

            score += 25
            saveScore()

            toast(
                "Mind refreshed! +25 XP"
            )

            resetZen()
        }
    }

    /* ========================================================
       UPDATE
       ======================================================== */

    private fun updateGame() {

        val now =
            System.currentTimeMillis()

        if (selectedGame ==
            GameType.MEMORY
        ) {

            if (memoryShowing &&
                now - memoryStartTime >
                1400L
            ) {

                memoryShowing = false
            }
        }

        if (selectedGame ==
            GameType.REACTION
        ) {

            if (reactionState == 1 &&
                now - reactionStart >
                RandomReactionDelay
            ) {

                reactionState = 2

                reactionStart =
                    now
            }
        }

        if (selectedGame ==
            GameType.ZEN &&
            zenStarted &&
            zenCycles < 3
        ) {

            if (now - zenPhaseStart >=
                4000L
            ) {

                zenPhase++

                if (zenPhase % 2 == 0) {
                    zenCycles++
                }

                zenPhaseStart = now

                if (zenCycles >= 3) {
                    toast(
                        "3 calm breaths complete!"
                    )
                }
            }
        }
    }

    /* ========================================================
       GAME RESET
       ======================================================== */

    private fun resetGame() {

        when (selectedGame) {

            GameType.PULSE -> {
                pulseHits = 0

                pulseX =
                    Random.nextInt(
                        50,
                        310
                    ).toFloat()

                pulseY =
                    Random.nextInt(
                        210,
                        570
                    ).toFloat()
            }

            GameType.MEMORY ->
                resetMemory()

            GameType.COLOR ->
                setupColor()

            GameType.REACTION -> {
                reactionState = 0
                reactionTime = 0
            }

            GameType.NUMBER ->
                resetNumber()

            GameType.ZEN ->
                resetZen()
        }
    }

    private fun resetMemory() {

        memorySequence =
            mutableListOf()

        repeat(memoryLength) {
            memorySequence.add(
                Random.nextInt(0, 16)
            )
        }

        memoryInput = 0
        memoryShowing = true
        memoryResult = ""

        memoryStartTime =
            System.currentTimeMillis()
    }

    private fun setupColor() {

        colorTarget =
            Random.nextInt(0, 4)

        colorOptions =
            mutableListOf(
                0,
                1,
                2,
                3
            ).shuffled()
                .toMutableList()

        colorResult = ""
    }

    private fun resetNumber() {

        numberNext = 1
        numberResult = ""

        numberLayout =
            (1..16).shuffled()
                .toIntArray()
    }

    private fun resetZen() {

        zenPhase = 0
        zenCycles = 0
        zenStarted = false

        zenPhaseStart =
            System.currentTimeMillis()
    }

    private var numberLayout =
        (1..16).shuffled()
            .toIntArray()

    /* ========================================================
       HELPERS
       ======================================================== */

    private fun saveScore() {

        prefs.edit()
            .putInt(
                "score",
                score
            )
            .apply()
    }

    fun isMusicEnabled(): Boolean {
        return musicEnabled
    }

    fun goBack(): Boolean {

        return when (page) {

            Page.INTRO ->
                false

            Page.SELECT_GAME -> {
                page = Page.INTRO
                true
            }

            Page.READY -> {
                page = Page.SELECT_GAME
                true
            }

            Page.PLAY -> {
                page = Page.READY
                true
            }
        }
    }

    private fun gameName(
        game: GameType
    ): String {
        return when (game) {
            GameType.PULSE -> "Pulse Hunt"
            GameType.MEMORY -> "Memory Matrix"
            GameType.COLOR -> "Color Match"
            GameType.REACTION -> "Quick Reaction"
            GameType.NUMBER -> "Number Flow"
            GameType.ZEN -> "Zen Breath"
        }
    }

    private fun gameShort(
        game: GameType
    ): String {
        return when (game) {
            GameType.PULSE ->
                "Find the moving glow"

            GameType.MEMORY ->
                "Remember the pattern"

            GameType.COLOR ->
                "Spot the matching color"

            GameType.REACTION ->
                "React as fast as possible"

            GameType.NUMBER ->
                "Tap numbers in order"

            GameType.ZEN ->
                "Slow down and breathe"
        }
    }

    private fun gameSubtitle(
        game: GameType
    ): String {
        return when (game) {
            GameType.PULSE ->
                "Find • Tap • Refresh"

            GameType.MEMORY ->
                "Watch • Remember • Repeat"

            GameType.COLOR ->
                "Look • Match • Focus"

            GameType.REACTION ->
                "Wait • React • Improve"

            GameType.NUMBER ->
                "Find • Order • Flow"

            GameType.ZEN ->
                "Breathe • Relax • Reset"
        }
    }

    private fun gameDescription(
        game: GameType
    ): String {
        return when (game) {
            GameType.PULSE ->
                "A moving target challenge for quick focus."

            GameType.MEMORY ->
                "Remember a short pattern and reproduce it."

            GameType.COLOR ->
                "Train visual attention with color matching."

            GameType.REACTION ->
                "Wait for the signal and react quickly."

            GameType.NUMBER ->
                "Find numbers in sequence without losing flow."

            GameType.ZEN ->
                "A simple guided breathing mini challenge."
        }
    }

    private fun gameHowTo(
        game: GameType
    ): String {
        return when (game) {
            GameType.PULSE ->
                "Tap the bright pulse whenever it appears."

            GameType.MEMORY ->
                "Remember the glowing tiles and tap them back."

            GameType.COLOR ->
                "Choose the button with the matching color."

            GameType.REACTION ->
                "Tap START, wait for GO, then react."

            GameType.NUMBER ->
                "Tap 1, then 2, then 3 and continue to 16."

            GameType.ZEN ->
                "Follow the expanding and contracting breathing circle."
        }
    }

    companion object {
        private const val RandomReactionDelay =
            1200L
    }
}

/* ============================================================
   RELAXING BUILT-IN BACKGROUND MUSIC
   ============================================================ */

private class RelaxingMusic {

    private var audioTrack: AudioTrack? = null
    private var musicThread: Thread? = null

    @Volatile
    private var running = false

    private val sampleRate = 44100

    @Synchronized
    fun start() {

        if (running)
            return

        try {

            val minBuffer =
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

            if (minBuffer <= 0)
                return

            audioTrack =
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
                            .setSampleRate(sampleRate)
                            .setEncoding(
                                AudioFormat.ENCODING_PCM_16BIT
                            )
                            .setChannelMask(
                                AudioFormat.CHANNEL_OUT_MONO
                            )
                            .build()
                    )
                    .setBufferSizeInBytes(
                        minBuffer * 2
                    )
                    .setTransferMode(
                        AudioTrack.MODE_STREAM
                    )
                    .build()

            audioTrack?.play()

            running = true

            musicThread =
                Thread {

                    val buffer =
                        ShortArray(2048)

                    var samplePosition = 0L

                    val notes =
                        doubleArrayOf(
                            261.63,
                            329.63,
                            392.00,
                            329.63,
                            293.66,
                            349.23,
                            440.00,
                            349.23
                        )

                    var noteIndex = 0
                    var noteSamples = 0

                    while (running) {

                        val note =
                            notes[
                                noteIndex %
                                        notes.size
                            ]

                        for (i in buffer.indices) {

                            val global =
                                samplePosition++

                            if (global % sampleRate ==
                                0L
                            ) {
                                // gentle movement
                            }

                            val t =
                                global.toDouble() /
                                        sampleRate

                            val beat =
                                (t / 3.2)
                                    .toInt()

                            noteIndex =
                                beat % notes.size

                            val current =
                                notes[
                                    noteIndex
                                ]

                            val wave1 =
                                sin(
                                    2.0 *
                                            Math.PI *
                                            current *
                                            t
                                )

                            val wave2 =
                                sin(
                                    2.0 *
                                            Math.PI *
                                            current *
                                            0.5 *
                                            t
                                )

                            val wave3 =
                                sin(
                                    2.0 *
                                            Math.PI *
                                            current *
                                            1.5 *
                                            t
                                )

                            val slow =
                                0.5 +
                                        0.5 *
                                        sin(
                                            2.0 *
                                                    Math.PI *
                                                    0.08 *
                                                    t
                                        )

                            val value =
                                (
                                    wave1 * 0.045 +
                                            wave2 * 0.025 +
                                            wave3 * 0.012
                                    ) *
                                    (0.55 + slow * 0.25)

                            buffer[i] =
                                (
                                    value *
                                            Short.MAX_VALUE
                                )
                                    .toInt()
                                    .coerceIn(
                                        Short.MIN_VALUE.toInt(),
                                        Short.MAX_VALUE.toInt()
                                    )
                                    .toShort()

                            noteSamples++
                        }

                        try {
                            audioTrack?.write(
                                buffer,
                                0,
                                buffer.size
                            )
                        } catch (_: Exception) {
                            running = false
                        }
                    }
                }

            musicThread?.start()

        } catch (_: Exception) {
            running = false
        }
    }

    @Synchronized
    fun stop() {

        running = false

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

    fun release() {
        stop()
    }
}
