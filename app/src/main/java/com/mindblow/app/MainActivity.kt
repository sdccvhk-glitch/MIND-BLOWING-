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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var mindView: MindBlowView
    private var music: RelaxMusic? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(5, 7, 25)
        window.navigationBarColor = Color.rgb(5, 7, 25)

        music = RelaxMusic()

        mindView = MindBlowView(this) {
            if (music?.isPlaying() == true) {
                music?.stop()
            } else {
                music?.start()
            }
        }

        setContentView(mindView)

        music?.start()
    }

    override fun onResume() {
        super.onResume()
        if (::mindView.isInitialized && mindView.musicEnabled) {
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
   SCREENS
   ============================================================ */

private enum class Screen {
    HOME,
    GAMES,
    MUSIC,
    GLOW,
    MEMORY,
    REACTION,
    COLOR,
    NUMBER,
    CALM,
    DAILY,
    PROFILE
}

/* ============================================================
   MAIN VIEW
   ============================================================ */

private class MindBlowView(
    context: Context,
    private val musicToggle: () -> Unit
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs =
        context.getSharedPreferences("mindblow_data", Context.MODE_PRIVATE)

    private var screen =
        Screen.HOME

    var musicEnabled =
        prefs.getBoolean("music_enabled", true)
        private set

    private var xp =
        prefs.getInt("xp", 0)

    private var streak =
        prefs.getInt("streak", 0)

    private var animation = 0f

    private var toastText = ""
    private var toastUntil = 0L

    /* ---------------- GLOW ---------------- */

    private var glowX = 0f
    private var glowY = 0f
    private var glowScore = 0
    private var glowRound = 0

    /* ---------------- MEMORY ---------------- */

    private val memorySymbols = arrayOf(
        "★", "◆", "●",
        "✦", "☀", "☾",
        "✿", "❖", "♥"
    )

    private var memoryCards =
        MutableList(9) { it }.apply { shuffle() }

    private var memoryFirst = -1
    private var memorySecond = -1
    private var memoryPreviewUntil = 0L
    private var memoryMatched = BooleanArray(9)

    /* ---------------- REACTION ---------------- */

    private var reactionState = 0
    private var reactionStart = 0L
    private var reactionResult = 0L
    private var reactionDelayUntil = 0L

    /* ---------------- COLOR ---------------- */

    private var colorTarget = 0
    private var colorOptions = IntArray(4)
    private var colorRound = 0

    /* ---------------- NUMBER ---------------- */

    private var numberSequence = mutableListOf<Int>()
    private var numberNext = 1
    private var numberRound = 0

    /* ---------------- CALM ---------------- */

    private var calmBreath = 0
    private var calmStart = System.currentTimeMillis()

    init {
        isFocusable = true
        createGlow()
        createColorRound()
        createNumberRound()
        startMemory()

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
            Screen.COLOR -> drawColor(canvas, w, h)
            Screen.NUMBER -> drawNumber(canvas, w, h)
            Screen.CALM -> drawCalm(canvas, w, h)
            Screen.DAILY -> drawDaily(canvas, w, h)
            Screen.PROFILE -> drawProfile(canvas, w, h)
        }

        if (toastUntil > System.currentTimeMillis()) {
            drawToast(canvas, w, h)
        }

        postInvalidateDelayed(16L)
    }

    /* ========================================================
       BACKGROUND
       ======================================================== */

    private fun drawBackground(
        c: Canvas,
        w: Float,
        h: Float
    ) {

        paint.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            Color.rgb(4, 7, 25),
            Color.rgb(34, 5, 58),
            Shader.TileMode.CLAMP
        )

        c.drawRect(0f, 0f, w, h, paint)

        paint.shader = null

        /* cyan glow */

        paint.color = Color.argb(45, 0, 220, 255)

        c.drawCircle(
            w * 0.15f + sin(animation) * 35f,
            h * 0.20f,
            130f,
            paint
        )

        /* purple glow */

        paint.color = Color.argb(38, 150, 60, 255)

        c.drawCircle(
            w * 0.88f + cos(animation * 0.8f) * 35f,
            h * 0.35f,
            150f,
            paint
        )

        /* blue bottom glow */

        paint.color = Color.argb(25, 30, 150, 255)

        c.drawCircle(
            w * 0.52f,
            h * 0.88f,
            145f,
            paint
        )

        /* stars */

        paint.color = Color.argb(100, 100, 220, 255)

        for (i in 0 until 35) {

            val sx =
                ((i * 83) % max(1, width)).toFloat()

            val sy =
                ((i * 137) % max(1, height)).toFloat()

            val pulse =
                1f + sin(animation * 2f + i) * 0.5f

            c.drawCircle(
                sx,
                sy,
                pulse,
                paint
            )
        }

        /* center rings */

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(30, 100, 220, 255)

        val centerX = w / 2f
        val centerY = h * 0.53f

        for (i in 0 until 7) {

            c.drawCircle(
                centerX,
                centerY,
                45f + i * 43f,
                paint
            )
        }

        paint.style = Paint.Style.FILL
    }

    /* ========================================================
       TEXT
       ======================================================== */

    private fun txt(
        c: Canvas,
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

        c.drawText(
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
        c: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        radius: Float = 20f
    ) {

        paint.shader = null

        paint.color =
            Color.argb(
                225,
                10,
                18,
                45
            )

        c.drawRoundRect(
            l,
            t,
            r,
            b,
            radius,
            radius,
            paint
        )

        paint.style =
            Paint.Style.STROKE

        paint.strokeWidth = 1.2f

        paint.color =
            Color.argb(
                70,
                100,
                180,
                255
            )

        c.drawRoundRect(
            l,
            t,
            r,
            b,
            radius,
            radius,
            paint
        )

        paint.style =
            Paint.Style.FILL
    }

    /* ========================================================
       BUTTON
       ======================================================== */

    private fun button(
        c: Canvas,
        label: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {

        paint.shader =
            LinearGradient(
                l,
                t,
                r,
                b,
                Color.rgb(20, 210, 255),
                Color.rgb(130, 60, 255),
                Shader.TileMode.CLAMP
            )

        c.drawRoundRect(
            l,
            t,
            r,
            b,
            18f,
            18f,
            paint
        )

        paint.shader = null

        txt(
            c,
            label,
            (l + r) / 2f,
            (t + b) / 2f + 6f,
            14f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    /* ========================================================
       HEADER
       ======================================================== */

    private fun header(
        c: Canvas,
        title: String,
        subtitle: String
    ) {

        txt(
            c,
            "‹",
            20f,
            47f,
            38f,
            Color.WHITE,
            true
        )

        txt(
            c,
            title,
            57f,
            40f,
            22f,
            Color.WHITE,
            true
        )

        txt(
            c,
            subtitle,
            58f,
            61f,
            10f,
            0xff9da8cf.toInt()
        )
    }

    /* ========================================================
       NAVIGATION
       ======================================================== */

    private fun navigation(
        c: Canvas,
        w: Float,
        h: Float,
        selected: Int
    ) {

        val top = h - 84f

        card(
            c,
            8f,
            top,
            w - 8f,
            h - 8f,
            22f
        )

        val icons =
            arrayOf(
                "⌂",
                "✦",
                "♫",
                "★",
                "●"
            )

        val labels =
            arrayOf(
                "Home",
                "Games",
                "Music",
                "Daily",
                "Profile"
            )

        for (i in 0..4) {

            val x =
                w * (i + 0.5f) / 5f

            val selectedColor =
                if (i == selected)
                    0xff57e7ff.toInt()
                else
                    0xff737fa6.toInt()

            txt(
                c,
                icons[i],
                x,
                top + 31f,
                25f,
                selectedColor,
                true,
                Paint.Align.CENTER
            )

            txt(
                c,
                labels[i],
                x,
                top + 56f,
                9f,
                selectedColor,
                true,
                Paint.Align.CENTER
            )
        }
    }

    /* ========================================================
       HOME
       ======================================================== */

    private fun drawHome(
        c: Canvas,
        w: Float,
        h: Float
    ) {

        txt(
            c,
            "MindBlow",
            18f,
            40f,
            27f,
            Color.WHITE,
            true
        )

        txt(
            c,
            "Reset your mind • one minute at a time",
            18f,
            61f,
            10f,
            0xff9da8cf.toInt()
        )

        card(
            c,
            w - 105f,
            17f,
            w - 15f,
            55f,
            18f
        )

        txt(
            c,
            "✦ $xp XP",
            w - 60f,
            41f,
            12f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )

        /* reset card */

        card(
            c,
            14f,
            78f,
            w - 14f,
            171f,
            22f
        )

        txt(
            c,
            "HOW DO YOU FEEL?",
            28f,
            101f,
            9f,
            0xff59e7ff.toInt(),
            true
        )

        txt(
            c,
            "Take a tiny break.",
            28f,
            127f,
            20f,
            Color.WHITE,
            true
        )

        txt(
            c,
            "Play something. Breathe. Reset.",
            28f,
            147f,
            10f,
            0xffaab5d7.toInt()
        )

        button(
            c,
            "START A QUICK RESET",
            28f,
            153f,
            w - 28f,
            169f
        )

        /* music */

        txt(
            c,
            "MIND REFRESHING",
            18f,
            198f,
            9f,
            0xff60e6ff.toInt(),
            true
        )

        card(
            c,
            14f,
            207f,
            w - 14f,
            263f,
            17f
        )

        txt(
            c,
            "♫",
            31f,
            242f,
            25f,
            0xff7f7cff.toInt(),
            true
        )

        txt(
            c,
            "Mind Refreshing Music",
            65f,
            229f,
            13f,
            Color.WHITE,
            true
        )

        txt(
            c,
            if (musicEnabled)
                "Ambient sound is playing"
            else
                "Music is paused",
            65f,
            246f,
            9f,
            0xff99a5ca.toInt()
        )

        val musicLeft = w - 88f

        button(
            c,
            if (musicEnabled) "MUSIC ON" else "MUSIC OFF",
            musicLeft,
            221f,
            w - 23f,
            249f
        )

        /* games */

        txt(
            c,
            "EXPLORE GAMES",
            18f,
            290f,
            9f,
            0xff60e6ff.toInt(),
            true
        )

        miniGame(
            c,
            "✦",
            "Glow",
            "Find it",
            14f,
            299f,
            w / 2f - 7f,
            366f
        )

        miniGame(
            c,
            "◆",
            "Memory",
            "Match it",
            w / 2f + 7f,
            299f,
            w - 14f,
            366f
        )

        miniGame(
            c,
            "⚡",
            "Reaction",
            "React fast",
            14f,
            374f,
            w / 2f - 7f,
            441f
        )

        miniGame(
            c,
            "◎",
            "Breathe",
            "Slow down",
            w / 2f + 7f,
            374f,
            w - 14f,
            441f
        )

        navigation(
            c,
            w,
            h,
            0
        )
    }

    /* ========================================================
       GAME CARD
       ======================================================== */

    private fun miniGame(
        c: Canvas,
        icon: String,
        title: String,
        sub: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {

        card(
            c,
            l,
            t,
            r,
            b,
            17f
        )

        txt(
            c,
            icon,
            l + 30f,
            t + 30f,
            22f,
            0xff62e6ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        txt(
            c,
            title,
            l + 50f,
            t + 29f,
            13f,
            Color.WHITE,
            true
        )

        txt(
            c,
            sub,
            l + 50f,
            t + 47f,
            8f,
            0xff9da8cc.toInt()
        )
    }

    /* ========================================================
       GAMES
       ======================================================== */

    private fun drawGames(
        c: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            c,
            "Mind Games",
            "Quick challenges for your brain"
        )

        gameBig(
            c,
            "✦",
            "Glow Tap",
            "Find the moving glow",
            14f,
            86f,
            w / 2f - 7f,
            175f
        )

        gameBig(
            c,
            "◆",
            "Memory Match",
            "Remember the symbols",
            w / 2f + 7f,
            86f,
            w - 14f,
            175f
        )

        gameBig(
            c,
            "⚡",
            "Reaction",
            "Test your reaction",
            14f,
            185f,
            w / 2f - 7f,
            274f
        )

        gameBig(
            c,
            "●",
            "Color Hunt",
            "Find the target color",
            w / 2f + 7f,
            185f,
            w - 14f,
            274f
        )

        gameBig(
            c,
            "123",
            "Number Flow",
            "Tap numbers in order",
            14f,
            284f,
            w / 2f - 7f,
            373f
        )

        gameBig(
            c,
            "☾",
            "Calm Flow",
            "Follow your breathing",
            w / 2f + 7f,
            284f,
            w - 14f,
            373f
        )

        card(
            c,
            14f,
            391f,
            w - 14f,
            454f,
            17f
        )

        txt(
            c,
            "TIP",
            28f,
            414f,
            9f,
            0xff5ce6ff.toInt(),
            true
        )

        txt(
            c,
            "Try 2–5 minutes between study sessions.",
            28f,
            435f,
            10f,
            0xffaab5d6.toInt()
        )

        navigation(
            c,
            w,
            h,
            1
        )
    }

    private fun gameBig(
        c: Canvas,
        icon: String,
        title: String,
        sub: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {

        card(
            c,
            l,
            t,
            r,
            b,
            18f
        )

        txt(
            c,
            icon,
            l + 28f,
            t + 39f,
            23f,
            0xff5fe5ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        txt(
            c,
            title,
            l + 52f,
            t + 34f,
            14f,
            Color.WHITE,
            true
        )

        txt(
            c,
            sub,
            l + 52f,
            t + 55f,
            9f,
            0xff9ba6ca.toInt()
        )
    }

    /* ========================================================
       MUSIC
       ======================================================== */

    private fun drawMusic(
        c: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            c,
            "Mind Refreshing",
            "Ambient sounds for a calmer mind"
        )

        card(
            c,
            18f,
            92f,
            w - 18f,
            255f,
            25f
        )

        txt(
            c,
            "♫",
            w / 2f,
            160f,
            65f,
            0xff6be7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        txt(
            c,
            "MindBlow Ambient",
            w / 2f,
            195f,
            21f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        txt(
            c,
            "Soft • Slow • Refreshing",
            w / 2f,
            218f,
            11f,
            0xffaeb9da.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            c,
            if (musicEnabled)
                "PAUSE MUSIC"
            else
                "PLAY MUSIC",
            50f,
            225f,
            w - 50f,
            265f
        )

        card(
            c,
            18f,
            280f,
            w - 18f,
            350f,
            18f
        )

        txt(
            c,
            "🎧  Background music",
            35f,
            310f,
            15f,
            Color.WHITE,
            true
        )

        txt(
            c,
            "Music continues while you explore the games.",
            35f,
            333f,
            10f,
            0xffa2afd3.toInt()
        )
    }

    /* ========================================================
       GLOW GAME
       ======================================================== */

    private fun createGlow() {

        glowX =
            Random.nextFloat()

        glowY =
            Random.nextFloat()
    }

    private fun drawGlow(
        c: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            c,
            "Glow Tap",
            "Find the glowing orb"
        )

        txt(
            c,
            "Score  $glowScore",
            22f,
            95f,
            15f,
            Color.WHITE,
            true
        )

        txt(
            c,
            "Round ${glowRound + 1}",
            w - 22f,
            95f,
            12f,
            0xff9da9cd.toInt(),
            false,
            Paint.Align.RIGHT
        )

        val areaTop = 120f
        val areaBottom = h - 125f

        val px =
            55f + glowX * (w - 110f)

        val py =
            areaTop + glowY * (areaBottom - areaTop)

        for (i in 1..5) {

            paint.color =
                Color.argb(
                    18,
                    50,
                    230,
                    255
                )

            c.drawCircle(
                px,
                py,
                20f + i * 16f,
                paint
            )
        }

        paint.color =
            0xff5de9ff.toInt()

        c.drawCircle(
            px,
            py,
            22f + sin(animation * 4f) * 5f,
            paint
        )

        txt(
            c,
            "TAP THE GLOW",
            w / 2f,
            h - 95f,
            12f,
            0xffb8c5e7.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    /* ========================================================
       MEMORY GAME
       ======================================================== */

    private fun startMemory() {

        memoryCards =
            MutableList(9) { it }.apply {
                shuffle()
            }

        memoryFirst = -1
        memorySecond = -1
        memoryMatched = BooleanArray(9)

        memoryPreviewUntil =
            System.currentTimeMillis() + 1800L
    }

    private fun drawMemory(
        c: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            c,
            "Memory Match",
            "Remember the symbols"
        )

        val size =
            min(w - 48f, 330f)

        val left =
            (w - size) / 2f

        val top =
            110f

        val cell =
            size / 3f

        val preview =
            System.currentTimeMillis() <
                    memoryPreviewUntil

        for (i in 0 until 9) {

            val row =
                i / 3

            val col =
                i % 3

            val l =
                left + col * cell + 5f

            val tt =
                top + row * cell + 5f

            val r =
                left + (col + 1) * cell - 5f

            val b =
                top + (row + 1) * cell - 5f

            card(
                c,
                l,
                tt,
                r,
                b,
                17f
            )

            val reveal =
                preview ||
                        i == memoryFirst ||
                        i == memorySecond ||
                        memoryMatched[i]

            if (reveal) {

                txt(
                    c,
                    memorySymbols[memoryCards[i]],
                    (l + r) / 2f,
                    (tt + b) / 2f + 13f,
                    31f,
                    0xff64e7ff.toInt(),
                    true,
                    Paint.Align.CENTER
                )

            } else {

                txt(
                    c,
                    "?",
                    (l + r) / 2f,
                    (tt + b) / 2f + 12f,
                    28f,
                    0xff707da7.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            }
        }

        txt(
            c,
            if (preview)
                "Remember the board..."
            else
                "Find matching symbols",
            w / 2f,
            top + size + 35f,
            14f,
            0xffb1bddf.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    /* ========================================================
       REACTION
       ======================================================== */

    private fun drawReaction(
        c: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            c,
            "Reaction",
            "Tap when the circle turns green"
        )

        txt(
            c,
            if (reactionResult > 0)
                "${reactionResult} ms"
            else
                "Ready?",
            w / 2f,
            120f,
            26f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        val color =
            when (reactionState) {
                1 -> 0xffff5252.toInt()
                2 -> 0xff50e87b.toInt()
                else -> 0xff4fcfff.toInt()
            }

        paint.color = color

        c.drawCircle(
            w / 2f,
            h * 0.48f,
            82f + sin(animation * 3f) * 5f,
            paint
        )

        txt(
            c,
            when (reactionState) {
                1 -> "WAIT..."
                2 -> "TAP!"
                else -> "START"
            },
            w / 2f,
            h * 0.48f + 10f,
            20f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        txt(
            c,
            "Tap the circle",
            w / 2f,
            h * 0.68f,
            14f,
            0xffadb9da.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    /* ========================================================
       COLOR HUNT
       ======================================================== */

    private fun createColorRound() {

        colorTarget =
            Random.nextInt(4)

        colorOptions =
            intArrayOf(
                0xff42dfff.toInt(),
                0xff9b5cff.toInt(),
                0xffffc857.toInt(),
                0xffff5c8a.toInt()
            )
    }

    private fun drawColor(
        c: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            c,
            "Color Hunt",
            "Find the target color"
        )

        txt(
            c,
            "Target",
            w / 2f,
            115f,
            12f,
            0xffaab6d9.toInt(),
            true,
            Paint.Align.CENTER
        )

        paint.color =
            colorOptions[colorTarget]

        c.drawCircle(
            w / 2f,
            160f,
            34f,
            paint
        )

        txt(
            c,
            "Which one matches?",
            w / 2f,
            220f,
            16f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        for (i in 0 until 4) {

            val col =
                i % 2

            val row =
                i / 2

            val l =
                35f + col * (w - 85f) / 2f

            val tt =
                245f + row * 105f

            val r =
                l + (w - 85f) / 2f - 15f

            val b =
                tt + 82f

            card(
                c,
                l,
                tt,
                r,
                b,
                20f
            )

            paint.color =
                colorOptions[i]

            c.drawCircle(
                (l + r) / 2f,
                (tt + b) / 2f,
                27f,
                paint
            )
        }
    }

    /* ========================================================
       NUMBER FLOW
       ======================================================== */

    private fun createNumberRound() {

        numberSequence =
            (1..9).shuffled().toMutableList()

        numberNext = 1
    }

    private fun drawNumber(
        c: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            c,
            "Number Flow",
            "Tap numbers from 1 to 9"
        )

        txt(
            c,
            "Next: $numberNext",
            w / 2f,
            110f,
            22f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        val size =
            min(w - 50f, 330f)

        val left =
            (w - size) / 2f

        val top =
            145f

        val cell =
            size / 3f

        for (i in 0 until 9) {

            val row =
                i / 3

            val col =
                i % 3

            val l =
                left + col * cell + 6f

            val tt =
                top + row * cell + 6f

            val r =
                left + (col + 1) * cell - 6f

            val b =
                top + (row + 1) * cell - 6f

            card(
                c,
                l,
                tt,
                r,
                b,
                18f
            )

            txt(
                c,
                numberSequence[i].toString(),
                (l + r) / 2f,
                (tt + b) / 2f + 11f,
                24f,
                if (numberSequence[i] < numberNext)
                    0xff4e678c.toInt()
                else
                    Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }
    }

    /* ========================================================
       CALM FLOW
       ======================================================== */

    private fun drawCalm(
        c: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            c,
            "Calm Flow",
            "Follow the breathing circle"
        )

        val seconds =
            ((System.currentTimeMillis() -
                    calmStart) / 1000L) % 8L

        val phase =
            seconds.toFloat() / 8f

        val radius =
            if (seconds < 4)
                70f + phase * 120f
            else
                190f - (phase - 0.5f) * 240f

        for (i in 1..6) {

            paint.color =
                Color.argb(
                    20,
                    70,
                    220,
                    255
                )

            c.drawCircle(
                w / 2f,
                h * 0.48f,
                radius + i * 24f,
                paint
            )
        }

        paint.color =
            0xff62e7ff.toInt()

        c.drawCircle(
            w / 2f,
            h * 0.48f,
            radius,
            paint
        )

        txt(
            c,
            if (seconds < 4)
                "BREATHE IN"
            else
                "BREATHE OUT",
            w / 2f,
            h * 0.48f + 8f,
            17f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        txt(
            c,
            "Slow • steady • relaxed",
            w / 2f,
            h * 0.72f,
            14f,
            0xffaeb9da.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    /* ========================================================
       DAILY
       ======================================================== */

    private fun drawDaily(
        c: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            c,
            "Daily Reset",
            "One small win today"
        )

        card(
            c,
            18f,
            90f,
            w - 18f,
            285f,
            23f
        )

        txt(
            c,
            "TODAY'S RESET",
            35f,
            126f,
            10f,
            0xff60e6ff.toInt(),
            true
        )

        txt(
            c,
            "Refresh your mind",
            35f,
            163f,
            24f,
            Color.WHITE,
            true
        )

        txt(
            c,
            "Play one game for a few minutes.",
            35f,
            190f,
            12f,
            0xffaeb9da.toInt()
        )

        txt(
            c,
            "Then take a slow breath.",
            35f,
            211f,
            12f,
            0xffaeb9da.toInt()
        )

        button(
            c,
            "START RESET",
            35f,
            232f,
            w - 35f,
            270f
        )

        txt(
            c,
            "🔥  $streak day streak",
            w / 2f,
            330f,
            19f,
            0xffffb52e.toInt(),
            true,
            Paint.Align.CENTER
        )

        button(
            c,
            "CLAIM +50 XP",
            45f,
            365f,
            w - 45f,
            410f
        )

        navigation(
            c,
            w,
            h,
            3
        )
    }

    /* ========================================================
       PROFILE
       ======================================================== */

    private fun drawProfile(
        c: Canvas,
        w: Float,
        h: Float
    ) {

        header(
            c,
            "Profile",
            "Your MindBlow progress"
        )

        card(
            c,
            18f,
            90f,
            w - 18f,
            220f,
            24f
        )

        txt(
            c,
            "✦",
            62f,
            157f,
            50f,
            0xff63e7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        txt(
            c,
            "Mind Explorer",
            105f,
            135f,
            20f,
            Color.WHITE,
            true
        )

        txt(
            c,
            "Level ${xp / 500 + 1}",
            105f,
            160f,
            12f,
            0xffaeb9da.toInt()
        )

        txt(
            c,
            "$xp XP",
            105f,
            187f,
            13f,
            0xffffd66b.toInt(),
            true
        )

        txt(
            c,
            "🔥  $streak day streak",
            105f,
            208f,
            11f,
            0xffffb52e.toInt()
        )

        card(
            c,
            18f,
            240f,
            w - 18f,
            380f,
            20f
        )

        txt(
            c,
            "♫  Mind Refreshing Music",
            36f,
            278f,
            15f,
            Color.WHITE,
            true
        )

        txt(
            c,
            if (musicEnabled)
                "Music is ON"
            else
                "Music is OFF",
            36f,
            303f,
            11f,
            0xff9da9cc.toInt()
        )

        button(
            c,
            if (musicEnabled)
                "TURN OFF"
            else
                "TURN ON",
            36f,
            320f,
            w - 36f,
            360f
        )

        navigation(
            c,
            w,
            h,
            4
        )
    }

    /* ========================================================
       TOAST
       ======================================================== */

    private fun toast(
        c: Canvas,
        w: Float,
        h: Float
    ) {

        card(
            c,
            28f,
            h - 145f,
            w - 28f,
            h - 95f,
            18f
        )

        txt(
            c,
            toastText,
            w / 2f,
            h - 113f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    private fun showToast(
        message: String
    ) {

        toastText = message

        toastUntil =
            System.currentTimeMillis() + 1300L

        invalidate()
    }

    /* ========================================================
       SAVE
       ======================================================== */

    private fun save() {

        prefs.edit()
            .putInt("xp", xp)
            .putInt("streak", streak)
            .putBoolean(
                "music_enabled",
                musicEnabled
            )
            .apply()
    }

    private fun addXP(amount: Int) {

        xp += amount
        save()

        showToast("+$amount XP ✨")
    }

    /* ========================================================
       TOUCH
       ======================================================== */

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        if (event.action != MotionEvent.ACTION_UP)
            return true

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

            Screen.COLOR ->
                colorTouch(x, y, w, h)

            Screen.NUMBER ->
                numberTouch(x, y, w, h)

            Screen.CALM ->
                calmTouch(x, y, w, h)

            Screen.DAILY ->
                dailyTouch(x, y, w, h)

            Screen.PROFILE ->
                profileTouch(x, y, w, h)
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

                x < w * 0.20f ->
                    screen = Screen.HOME

                x < w * 0.40f ->
                    screen = Screen.GAMES

                x < w * 0.60f ->
                    screen = Screen.MUSIC

                x < w * 0.80f ->
                    screen = Screen.DAILY

                else ->
                    screen = Screen.PROFILE
            }

            return
        }

        if (y in 207f..270f) {

            musicEnabled = !musicEnabled

            if (musicEnabled)
                musicToggle()

            else
                musicToggle()

            save()

            return
        }

        if (y in 299f..366f) {

            if (x < w / 2f)
                startGlow()
            else
                startMemory()

            return
        }

        if (y in 374f..441f) {

            if (x < w / 2f)
                startReaction()
            else
                screen = Screen.CALM

            return
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

                x < w * 0.20f ->
                    screen = Screen.HOME

                x < w * 0.40f ->
                    screen = Screen.GAMES

                x < w * 0.60f ->
                    screen = Screen.MUSIC

                x < w * 0.80f ->
                    screen = Screen.DAILY

                else ->
                    screen = Screen.PROFILE
            }

            return
        }

        when {

            y in 86f..175f ->
                if (x < w / 2f)
                    startGlow()
                else
                    startMemory()

            y in 185f..274f ->
                if (x < w / 2f)
                    startReaction()
                else {
                    createColorRound()
                    screen = Screen.COLOR
                }

            y in 284f..373f ->
                if (x < w / 2f) {
                    createNumberRound()
                    screen = Screen.NUMBER
                } else {
                    calmStart =
                        System.currentTimeMillis()
                    screen = Screen.CALM
                }
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

        if (y in 210f..280f) {

            musicEnabled =
                !musicEnabled

            if (musicEnabled)
                musicToggle()
            else
                musicToggle()

            save()
        }
    }

    /* ========================================================
       GLOW TOUCH
       ======================================================== */

    private fun startGlow() {

        createGlow()
        glowScore = 0
        glowRound = 0
        screen = Screen.GLOW
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

        val areaTop = 120f
        val areaBottom = h - 125f

        val gx =
            55f + glowX * (w - 110f)

        val gy =
            areaTop +
                    glowY *
                    (areaBottom - areaTop)

        val distance =
            kotlin.math.sqrt(
                ((x - gx) * (x - gx) +
                        (y - gy) * (y - gy)).toDouble()
            )

        if (distance < 55) {

            glowScore++
            glowRound++

            addXP(10)

            createGlow()

            if (glowRound >= 10) {

                addXP(25)

                showToast(
                    "Glow complete! +25 bonus ✨"
                )

                screen = Screen.GAMES
            }
        }
    }

    /* ========================================================
       MEMORY TOUCH
       ======================================================== */

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
            System.currentTimeMillis()
            < memoryPreviewUntil
        ) return

        val size =
            min(w - 48f, 330f)

        val left =
            (w - size) / 2f

        val top = 110f
        val cell = size / 3f

        if (
            x < left ||
            x > left + size ||
            y < top ||
            y > top + size
        ) return

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

        if (memoryMatched[index])
            return

        if (memoryFirst == -1) {

            memoryFirst = index

        } else if (
            memorySecond == -1 &&
            index != memoryFirst
        ) {

            memorySecond = index

            if (
                memoryCards[memoryFirst]
                ==
                memoryCards[memorySecond]
            ) {

                memoryMatched[memoryFirst] = true
                memoryMatched[memorySecond] = true

                addXP(20)

                memoryFirst = -1
                memorySecond = -1

                if (memoryMatched.all { it }) {

                    addXP(40)

                    showToast(
                        "Memory complete! 🧠"
                    )
                }

            } else {

                postDelayed({

                    memoryFirst = -1
                    memorySecond = -1

                    invalidate()

                }, 650L)
            }
        }
    }

    /* ========================================================
       REACTION
       ======================================================== */

    private fun startReaction() {

        reactionState = 1
        reactionResult = 0L

        reactionDelayUntil =
            System.currentTimeMillis() +
                    Random.nextLong(
                        900L,
                        2400L
                    )

        screen = Screen.REACTION
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

        val cx = w / 2f
        val cy = h * 0.48f

        val distance =
            kotlin.math.sqrt(
                ((x - cx) * (x - cx) +
                        (y - cy) * (y - cy)).toDouble()
            )

        if (distance > 120)
            return

        if (reactionState == 0) {

            startReaction()

            return
        }

        if (reactionState == 1) {

            if (
                System.currentTimeMillis()
                >= reactionDelayUntil
            ) {

                reactionState = 2
                reactionStart =
                    System.currentTimeMillis()

            } else {

                showToast(
                    "Too early! Wait for green."
                )

                startReaction()
            }

        } else if (reactionState == 2) {

            reactionResult =
                System.currentTimeMillis() -
                        reactionStart

            addXP(
                if (reactionResult < 350)
                    30
                else
                    15
            )

            reactionState = 0
        }
    }

    /* ========================================================
       COLOR TOUCH
       ======================================================== */

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

        for (i in 0 until 4) {

            val col = i % 2
            val row = i / 2

            val l =
                35f +
                        col *
                        (w - 85f) / 2f

            val tt =
                245f +
                        row * 105f

            val r =
                l +
                        (w - 85f) / 2f -
                        15f

            val b =
                tt + 82f

            if (
                x >= l &&
                x <= r &&
                y >= tt &&
                y <= b
            ) {

                if (i == colorTarget) {

                    colorRound++

                    addXP(15)

                    if (colorRound >= 5) {

                        addXP(30)

                        showToast(
                            "Color Hunt complete! 🎨"
                        )

                        screen = Screen.GAMES

                    } else {

                        createColorRound()
                    }

                } else {

                    showToast(
                        "Try again 👀"
                    )
                }

                return
            }
        }
    }

    /* ========================================================
       NUMBER TOUCH
       ======================================================== */

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

        val size =
            min(w - 50f, 330f)

        val left =
            (w - size) / 2f

        val top = 145f
        val cell = size / 3f

        if (
            x < left ||
            x > left + size ||
            y < top ||
            y > top + size
        ) return

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

        val value =
            numberSequence[index]

        if (value == numberNext) {

            numberNext++

            addXP(5)

            if (numberNext > 9) {

                numberRound++

                addXP(35)

                if (numberRound >= 3) {

                    showToast(
                        "Number Flow complete! 🔢"
                    )

                    screen = Screen.GAMES

                } else {

                    createNumberRound()
                }
            }

        } else {

            showToast(
                "Tap $numberNext next"
            )
        }
    }

    /* ========================================================
       CALM TOUCH
       ======================================================== */

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

        calmBreath++

        if (calmBreath % 5 == 0) {

            addXP(10)

            showToast(
                "Nice breathing 🌙"
            )
        }
    }

    /* ========================================================
       DAILY TOUCH
       ======================================================== */

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

        if (y > 350f && y < 430f) {

            xp += 50
            streak++

            save()

            showToast(
                "Daily reset complete! +50 XP"
            )
        }
    }

    /* ========================================================
       PROFILE TOUCH
       ======================================================== */

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

        if (y in 315f..370f) {

            musicEnabled =
                !musicEnabled

            if (musicEnabled)
                musicToggle()
            else
                musicToggle()

            save()
        }
    }

    /* ========================================================
       START MEMORY
       ======================================================== */

    private fun startMemoryScreen() {

        startMemory()
        screen = Screen.MEMORY
    }

    /* ========================================================
       BACK
       ======================================================== */

    fun goBack(): Boolean {

        if (screen == Screen.HOME)
            return false

        screen =
            when (screen) {

                Screen.GLOW,
                Screen.MEMORY,
                Screen.REACTION,
                Screen.COLOR,
                Screen.NUMBER,
                Screen.CALM ->
                    Screen.GAMES

                Screen.MUSIC,
                Screen.DAILY,
                Screen.PROFILE ->
                    Screen.HOME

                else ->
                    Screen.HOME
            }

        invalidate()

        return true
    }
}

/* ============================================================
   BUILT-IN RELAXING MUSIC
   ============================================================

   This does NOT need an MP3 file in res/raw.
   It creates a very soft ambient tone directly with AudioTrack.
   ============================================================ */

private class RelaxMusic {

    private var audioTrack: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    private val sampleRate = 22050

    fun isPlaying(): Boolean {
        return running
    }

    @Synchronized
    fun start() {

        if (running)
            return

        running = true

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
                    bufferSize
                )
                .setTransferMode(
                    AudioTrack.MODE_STREAM
                )
                .build()

        val track = audioTrack ?: return

        track.play()

        thread =
            Thread {

                val samples =
                    ShortArray(sampleRate / 2)

                var phase1 = 0.0
                var phase2 = 0.0
                var phase3 = 0.0

                while (running) {

                    for (i in samples.indices) {

                        phase1 +=
                            2.0 *
                                    Math.PI *
                                    174.0 /
                                    sampleRate

                        phase2 +=
                            2.0 *
                                    Math.PI *
                                    220.0 /
                                    sampleRate

                        phase3 +=
                            2.0 *
                                    Math.PI *
                                    261.6 /
                                    sampleRate

                        val tone1 =
                            sin(phase1) * 0.035

                        val tone2 =
                            sin(phase2) * 0.022

                        val tone3 =
                            sin(phase3) * 0.012

                        val value =
                            (
                                (tone1 +
                                        tone2 +
                                        tone3)
                                        * Short.MAX_VALUE
                                ).toInt()

                        samples[i] =
                            value
                                .coerceIn(
                                    Short.MIN_VALUE.toInt(),
                                    Short.MAX_VALUE.toInt()
                                )
                                .toShort()
                    }

                    try {

                        track.write(
                            samples,
                            0,
                            samples.size
                        )

                    } catch (_: Exception) {
                        break
                    }
                }

            }.also {
                it.isDaemon = true
                it.start()
            }
    }

    @Synchronized
    fun stop() {

        running = false

        try {
            thread?.join(100)
        } catch (_: Exception) {
        }

        thread = null

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
