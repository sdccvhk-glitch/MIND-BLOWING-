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
import kotlin.math.*
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var gameView: MindBlowView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(5, 6, 25)
        window.navigationBarColor = Color.rgb(7, 5, 25)

        gameView = MindBlowView(this)
        setContentView(gameView)
    }

    override fun onPause() {
        super.onPause()
        gameView.stopMusic()
    }

    override fun onResume() {
        super.onResume()
        if (gameView.musicEnabled) {
            gameView.startMusic()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!gameView.goBack()) {
            super.onBackPressed()
        }
    }
}

private enum class Screen {
    HOME,
    GAMES,
    MUSIC,
    PROFILE,

    PULSE,
    MEMORY,
    COLOR,
    REACTION,
    NUMBER,
    BREATH
}

private class MindBlowView(
    private val ctx: Context
) : View(ctx) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs =
        ctx.getSharedPreferences("mindblow_v4", Context.MODE_PRIVATE)

    private val handler = Handler(Looper.getMainLooper())

    var musicEnabled =
        prefs.getBoolean("music_enabled", true)
        private set

    private var score =
        prefs.getInt("score", 0)

    private var gamesPlayed =
        prefs.getInt("games_played", 0)

    private var screen = Screen.HOME

    private var animation = 0f

    private var toastText = ""
    private var toastUntil = 0L

    // ---------------------------------------------------------
    // PULSE HUNT
    // ---------------------------------------------------------

    private var pulseX = 0f
    private var pulseY = 0f
    private var pulseRadius = 52f
    private var pulseHits = 0
    private var pulseStart = 0L
    private var pulseTime = 25_000L

    // ---------------------------------------------------------
    // MEMORY MATRIX
    // ---------------------------------------------------------

    private var memorySequence = mutableListOf<Int>()
    private var memoryShown = true
    private var memoryStep = 0
    private var memoryRound = 1
    private var memoryShowUntil = 0L

    // ---------------------------------------------------------
    // COLOR MATCH
    // ---------------------------------------------------------

    private val colorNames = arrayOf(
        "RED",
        "BLUE",
        "GREEN",
        "YELLOW",
        "PURPLE"
    )

    private val colorValues = intArrayOf(
        Color.rgb(255, 75, 100),
        Color.rgb(70, 190, 255),
        Color.rgb(75, 230, 155),
        Color.rgb(255, 210, 75),
        Color.rgb(190, 100, 255)
    )

    private var colorTarget = 0
    private var colorRound = 1
    private var colorCorrect = 0

    // ---------------------------------------------------------
    // QUICK REACTION
    // ---------------------------------------------------------

    private var reactionReady = false
    private var reactionWaiting = true
    private var reactionStart = 0L
    private var reactionBest = 0L
    private var reactionAttempts = 0

    // ---------------------------------------------------------
    // NUMBER FLOW
    // ---------------------------------------------------------

    private var numberCurrent = 1
    private var numberTarget = 1
    private var numberRound = 1
    private var numberStarted = 0L

    // ---------------------------------------------------------
    // ZEN BREATH
    // ---------------------------------------------------------

    private var breathPhase = 0
    private var breathPhaseStart = 0L
    private var breathCycles = 0
    private var breathRunning = false

    // ---------------------------------------------------------
    // MUSIC
    // ---------------------------------------------------------

    private var audioTrack: AudioTrack? = null
    private var musicThread: Thread? = null
    private var musicRunning = false

    init {
        isFocusable = true
        startMusic()
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
            Screen.HOME -> drawHome(canvas, w, h)
            Screen.GAMES -> drawGames(canvas, w, h)
            Screen.MUSIC -> drawMusic(canvas, w, h)
            Screen.PROFILE -> drawProfile(canvas, w, h)

            Screen.PULSE -> drawPulse(canvas, w, h)
            Screen.MEMORY -> drawMemory(canvas, w, h)
            Screen.COLOR -> drawColor(canvas, w, h)
            Screen.REACTION -> drawReaction(canvas, w, h)
            Screen.NUMBER -> drawNumber(canvas, w, h)
            Screen.BREATH -> drawBreath(canvas, w, h)
        }

        if (toastUntil > System.currentTimeMillis()) {
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
            Color.rgb(4, 6, 27),
            Color.rgb(34, 7, 57),
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

        // Large animated glow
        paint.color = Color.argb(38, 30, 210, 255)

        canvas.drawCircle(
            w * 0.10f + sin(animation) * 35f,
            h * 0.22f,
            115f,
            paint
        )

        paint.color = Color.argb(35, 165, 70, 255)

        canvas.drawCircle(
            w * 0.90f + cos(animation * 0.7f) * 25f,
            h * 0.40f,
            125f,
            paint
        )

        paint.color = Color.argb(28, 40, 150, 255)

        canvas.drawCircle(
            w * 0.55f,
            h * 0.88f + sin(animation) * 20f,
            105f,
            paint
        )

        // stars
        paint.color = Color.argb(100, 100, 190, 255)

        for (i in 0 until 30) {
            val x = ((i * 83) % max(1, width)).toFloat()
            val y = ((i * 137) % max(1, height)).toFloat()

            val r = 1f + ((i % 3) * 0.6f)

            canvas.drawCircle(
                x,
                y,
                r,
                paint
            )
        }

        // orbital rings
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(28, 100, 210, 255)

        val cx = w * 0.5f
        val cy = h * 0.55f

        for (i in 1..7) {
            canvas.drawCircle(
                cx,
                cy,
                i * 46f + sin(animation + i) * 2f,
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
            235,
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
        paint.strokeWidth = 1.4f
        paint.color = Color.argb(
            80,
            80,
            170,
            235
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
            Color.rgb(40, 220, 255),
            Color.rgb(145, 65, 255),
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
            (t + b) / 2f + 6f,
            14f,
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
        title: String,
        subtitle: String = ""
    ) {
        text(
            canvas,
            title,
            18f,
            39f,
            25f,
            Color.WHITE,
            true
        )

        if (subtitle.isNotEmpty()) {
            text(
                canvas,
                subtitle,
                18f,
                59f,
                11f,
                0xff9da9d0.toInt()
            )
        }

        card(
            canvas,
            width - 94f,
            15f,
            width - 14f,
            51f,
            17f
        )

        text(
            canvas,
            "✦ $score",
            width - 54f,
            38f,
            12f,
            0xffffda69.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // NAVIGATION
    // =========================================================

    private fun navigation(
        canvas: Canvas,
        selected: Int
    ) {
        val h = height.toFloat()
        val w = width.toFloat()

        val top = h - 86f

        card(
            canvas,
            7f,
            top,
            w - 7f,
            h - 8f,
            22f
        )

        val icons = arrayOf(
            "⌂",
            "✦",
            "♫",
            "★"
        )

        val names = arrayOf(
            "HOME",
            "GAMES",
            "MUSIC",
            "PROFILE"
        )

        for (i in 0..3) {

            val x = w * (i + 0.5f) / 4f

            val active =
                i == selected

            val color =
                if (active)
                    0xff59eaff.toInt()
                else
                    0xff8d96bd.toInt()

            // BIG icons
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
                top + 62f,
                10f,
                color,
                true,
                Paint.Align.CENTER
            )

            if (active) {
                paint.color = color

                canvas.drawCircle(
                    x,
                    top + 73f,
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
        text(
            canvas,
            "MindBlow",
            18f,
            40f,
            28f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "RELAX  •  FOCUS  •  REFRESH",
            18f,
            59f,
            9f,
            0xff8e9bc4.toInt()
        )

        card(
            canvas,
            12f,
            76f,
            w - 12f,
            162f,
            22f
        )

        text(
            canvas,
            "READY FOR A RESET?",
            25f,
            98f,
            9f,
            0xff5ce8ff.toInt(),
            true
        )

        text(
            canvas,
            "Refresh your mind.",
            25f,
            122f,
            21f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Pick a challenge and take a little break.",
            25f,
            141f,
            10f,
            0xff9eabd1.toInt()
        )

        button(
            canvas,
            "START QUICK RESET",
            25f,
            146f,
            w - 25f,
            158f
        )

        // MUSIC
        text(
            canvas,
            "MIND REFRESHING MUSIC",
            18f,
            186f,
            10f,
            0xff9da9cc.toInt(),
            true
        )

        card(
            canvas,
            12f,
            194f,
            w - 12f,
            247f,
            19f
        )

        text(
            canvas,
            "♫",
            31f,
            228f,
            27f,
            0xff58e8ff.toInt(),
            true
        )

        text(
            canvas,
            "Mind Refreshing Music",
            63f,
            218f,
            13f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            if (musicEnabled)
                "Ambient sound is playing"
            else
                "Music is paused",
            63f,
            235f,
            9f,
            0xff8f9ac0.toInt()
        )

        val musicLeft = w - 100f

        button(
            canvas,
            if (musicEnabled) "MUSIC ON" else "MUSIC OFF",
            musicLeft,
            207f,
            w - 22f,
            235f
        )

        text(
            canvas,
            "MIND REFRESHING GAMES",
            18f,
            274f,
            11f,
            Color.WHITE,
            true
        )

        gameCard(
            canvas,
            12f,
            286f,
            w / 2f - 5f,
            376f,
            "◉",
            "Pulse Hunt",
            "Find the moving glow",
            0xff55e9ff.toInt()
        )

        gameCard(
            canvas,
            w / 2f + 5f,
            286f,
            w - 12f,
            376f,
            "••",
            "Memory Matrix",
            "Remember the pattern",
            0xff66eaff.toInt()
        )

        gameCard(
            canvas,
            12f,
            386f,
            w / 2f - 5f,
            476f,
            "ϟ",
            "Quick Reaction",
            "React as fast as possible",
            0xffffd84d.toInt()
        )

        gameCard(
            canvas,
            w / 2f + 5f,
            386f,
            w - 12f,
            476f,
            "●",
            "Color Match",
            "Spot the matching color",
            0xffff6eaa.toInt()
        )

        gameCard(
            canvas,
            12f,
            486f,
            w / 2f - 5f,
            576f,
            "123",
            "Number Flow",
            "Tap numbers in order",
            0xff5de5ff.toInt()
        )

        gameCard(
            canvas,
            w / 2f + 5f,
            486f,
            w - 12f,
            576f,
            "◎",
            "Zen Breath",
            "Slow down and breathe",
            0xff65e5c0.toInt()
        )

        navigation(canvas, 0)
    }

    // =========================================================
    // GAME CARD
    // =========================================================

    private fun gameCard(
        canvas: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        icon: String,
        title: String,
        sub: String,
        iconColor: Int
    ) {
        card(canvas, l, t, r, b, 18f)

        text(
            canvas,
            icon,
            l + 25f,
            t + 38f,
            25f,
            iconColor,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            title,
            l + 47f,
            t + 30f,
            13f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            sub,
            l + 47f,
            t + 48f,
            8f,
            0xff8f9bc0.toInt()
        )

        text(
            canvas,
            "PLAY ›",
            r - 12f,
            b - 11f,
            8f,
            0xff55e6ff.toInt(),
            true,
            Paint.Align.RIGHT
        )
    }

    // =========================================================
    // GAMES PAGE
    // =========================================================

    private fun drawGames(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        topBar(
            canvas,
            "Mind Games",
            "Six quick ways to refresh"
        )

        val data = arrayOf(
            arrayOf("◉", "Pulse Hunt", "Moving target"),
            arrayOf("••", "Memory Matrix", "Pattern memory"),
            arrayOf("ϟ", "Quick Reaction", "Fast response"),
            arrayOf("●", "Color Match", "Visual attention"),
            arrayOf("123", "Number Flow", "Number focus"),
            arrayOf("◎", "Zen Breath", "Calm breathing")
        )

        for (i in 0 until 6) {

            val col = i % 2
            val row = i / 2

            val l =
                if (col == 0) 12f else w / 2f + 5f

            val r =
                if (col == 0) w / 2f - 5f else w - 12f

            val t = 90f + row * 105f
            val b = t + 92f

            gameCard(
                canvas,
                l,
                t,
                r,
                b,
                data[i][0],
                data[i][1],
                data[i][2],
                0xff5de8ff.toInt()
            )
        }

        navigation(canvas, 1)
    }

    // =========================================================
    // MUSIC
    // =========================================================

    private fun drawMusic(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        topBar(
            canvas,
            "Mind Music",
            "A calm background for your break"
        )

        card(
            canvas,
            18f,
            90f,
            w - 18f,
            245f,
            25f
        )

        text(
            canvas,
            "♫",
            w / 2f,
            150f,
            72f,
            0xff5de8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Mind Refreshing",
            w / 2f,
            185f,
            23f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Ambient • Soft • Focus",
            w / 2f,
            208f,
            12f,
            0xff9aa7ce.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            canvas,
            if (musicEnabled) "PAUSE MUSIC" else "PLAY MUSIC",
            45f,
            225f,
            w - 45f,
            270f
        )

        card(
            canvas,
            18f,
            270f,
            w - 18f,
            355f,
            21f
        )

        text(
            canvas,
            "No audio file required",
            35f,
            305f,
            16f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "The relaxing ambient sound is generated",
            35f,
            328f,
            11f,
            0xff9aa6cc.toInt()
        )

        text(
            canvas,
            "directly by the app.",
            35f,
            346f,
            11f,
            0xff9aa6cc.toInt()
        )

        navigation(canvas, 2)
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
            "Profile",
            "Your MindBlow progress"
        )

        card(
            canvas,
            18f,
            90f,
            w - 18f,
            190f,
            23f
        )

        text(
            canvas,
            "✦",
            55f,
            145f,
            45f,
            0xffffd75e.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "MindBlow Explorer",
            95f,
            130f,
            18f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            "Score  $score",
            95f,
            156f,
            13f,
            0xff9eabd0.toInt()
        )

        text(
            canvas,
            "Games played  $gamesPlayed",
            95f,
            178f,
            11f,
            0xff8995bb.toInt()
        )

        card(
            canvas,
            18f,
            210f,
            w - 18f,
            300f,
            20f
        )

        text(
            canvas,
            "Music",
            35f,
            242f,
            16f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            if (musicEnabled) "Background music ON"
            else "Background music OFF",
            35f,
            267f,
            11f,
            0xff9aa7cb.toInt()
        )

        button(
            canvas,
            if (musicEnabled) "TURN OFF" else "TURN ON",
            35f,
            275f,
            w - 35f,
            295f
        )

        navigation(canvas, 3)
    }

    // =========================================================
    // PULSE HUNT
    // =========================================================

    private fun startPulse() {
        screen = Screen.PULSE
        pulseHits = 0
        pulseStart = System.currentTimeMillis()
        movePulse()
    }

    private fun movePulse() {
        val margin = 65f

        pulseX =
            Random.nextFloat() *
                    max(1f, width - margin * 2f) +
                    margin

        pulseY =
            Random.nextFloat() *
                    max(1f, height - 260f) +
                    150f
    }

    private fun drawPulse(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        gameHeader(
            canvas,
            "Pulse Hunt",
            "Tap the moving glow • 25 seconds"
        )

        val remaining =
            max(
                0L,
                pulseTime -
                        (System.currentTimeMillis() - pulseStart)
            )

        text(
            canvas,
            "${remaining / 1000}s",
            w / 2f,
            115f,
            25f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        paint.color = Color.argb(35, 70, 230, 255)

        canvas.drawCircle(
            pulseX,
            pulseY,
            pulseRadius + 25f,
            paint
        )

        paint.color = 0xff54e9ff.toInt()

        canvas.drawCircle(
            pulseX,
            pulseY,
            pulseRadius +
                    sin(animation * 5f) * 8f,
            paint
        )

        text(
            canvas,
            "$pulseHits",
            w / 2f,
            h - 135f,
            48f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "HITS",
            w / 2f,
            h - 108f,
            11f,
            0xff9da9ce.toInt(),
            true,
            Paint.Align.CENTER
        )

        if (remaining <= 0L) {
            finishGame(
                "Pulse Hunt complete! +${pulseHits * 5} XP"
            )
        }
    }

    // =========================================================
    // MEMORY MATRIX
    // =========================================================

    private fun startMemory() {
        screen = Screen.MEMORY
        memoryRound = 1
        memoryStep = 0
        createMemoryRound()
    }

    private fun createMemoryRound() {
        memorySequence.clear()

        val count =
            min(
                2 + memoryRound,
                7
            )

        repeat(count) {
            memorySequence.add(
                Random.nextInt(9)
            )
        }

        memoryShown = true
        memoryShowUntil =
            System.currentTimeMillis() + 1200L
    }

    private fun drawMemory(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        gameHeader(
            canvas,
            "Memory Matrix",
            "Remember the glowing pattern"
        )

        val size = min(w - 70f, 330f)
        val left = (w - size) / 2f
        val top = 125f
        val cell = size / 3f

        val now = System.currentTimeMillis()

        if (memoryShown && now > memoryShowUntil) {
            memoryShown = false
        }

        for (i in 0 until 9) {

            val row = i / 3
            val col = i % 3

            val l = left + col * cell + 6f
            val t = top + row * cell + 6f
            val r = left + (col + 1) * cell - 6f
            val b = top + (row + 1) * cell - 6f

            card(
                canvas,
                l,
                t,
                r,
                b,
                20f
            )

            val glowing =
                memoryShown &&
                        memorySequence.contains(i)

            if (glowing) {

                paint.color =
                    0xff58e8ff.toInt()

                canvas.drawCircle(
                    (l + r) / 2f,
                    (t + b) / 2f,
                    27f,
                    paint
                )
            }
        }

        text(
            canvas,
            if (memoryShown)
                "WATCH"
            else
                "TAP THE PATTERN",
            w / 2f,
            top + size + 45f,
            15f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Round $memoryRound",
            w / 2f,
            top + size + 70f,
            11f,
            0xff98a4ca.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // COLOR MATCH
    // =========================================================

    private fun startColor() {
        screen = Screen.COLOR
        colorRound = 1
        colorCorrect = 0
        nextColorRound()
    }

    private fun nextColorRound() {
        colorTarget =
            Random.nextInt(colorNames.size)
    }

    private fun drawColor(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        gameHeader(
            canvas,
            "Color Match",
            "Tap the button matching the word"
        )

        val targetName =
            colorNames[colorTarget]

        text(
            canvas,
            "MATCH",
            w / 2f,
            130f,
            12f,
            0xff9da9cd.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            targetName,
            w / 2f,
            175f,
            36f,
            colorValues[colorTarget],
            true,
            Paint.Align.CENTER
        )

        for (i in colorNames.indices) {

            val row = i / 2
            val col = i % 2

            val l =
                if (col == 0) 20f else w / 2f + 10f

            val r =
                if (col == 0) w / 2f - 10f else w - 20f

            val t =
                225f + row * 82f

            val b = t + 65f

            paint.color = colorValues[i]

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
                colorNames[i],
                (l + r) / 2f,
                t + 40f,
                15f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        text(
            canvas,
            "Correct: $colorCorrect / ${colorRound - 1}",
            w / 2f,
            h - 130f,
            13f,
            0xffa1add0.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // QUICK REACTION
    // =========================================================

    private fun startReaction() {
        screen = Screen.REACTION
        reactionReady = false
        reactionWaiting = true
        reactionAttempts = 0

        scheduleReaction()
    }

    private fun scheduleReaction() {
        reactionWaiting = true
        reactionReady = false

        handler.postDelayed(
            {
                if (screen == Screen.REACTION) {
                    reactionReady = true
                    reactionWaiting = false
                    reactionStart =
                        System.currentTimeMillis()
                }
            },
            Random.nextLong(1000L, 3000L)
        )
    }

    private fun drawReaction(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        gameHeader(
            canvas,
            "Quick Reaction",
            "Wait for green, then tap"
        )

        val cx = w / 2f
        val cy = h / 2f

        paint.color =
            when {
                reactionReady ->
                    0xff45e69b.toInt()

                reactionWaiting ->
                    0xffffbd4d.toInt()

                else ->
                    0xffef5870.toInt()
            }

        canvas.drawCircle(
            cx,
            cy,
            105f + sin(animation * 3f) * 4f,
            paint
        )

        text(
            canvas,
            when {
                reactionReady -> "TAP!"
                reactionWaiting -> "WAIT"
                else -> "TOO SOON"
            },
            cx,
            cy + 12f,
            25f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        if (reactionAttempts > 0) {

            text(
                canvas,
                "Best: ${reactionBest} ms",
                cx,
                h - 135f,
                15f,
                0xffa8b4d8.toInt(),
                true,
                Paint.Align.CENTER
            )
        }
    }

    // =========================================================
    // NUMBER FLOW
    // =========================================================

    private fun startNumber() {
        screen = Screen.NUMBER
        numberRound = 1
        numberCurrent = 1
        numberStarted = System.currentTimeMillis()
        generateNumberBoard()
    }

    private val numberPositions =
        Array(12) { Pair(0f, 0f) }

    private fun generateNumberBoard() {

        val used = mutableSetOf<Int>()

        for (i in 0 until 12) {

            var n: Int

            do {
                n = Random.nextInt(1, 100)
            } while (used.contains(n))

            used.add(n)

            numberPositions[i] =
                Pair(
                    Random.nextFloat(),
                    Random.nextFloat()
                )
        }

        numberTarget =
            Random.nextInt(1, 100)
    }

    private fun drawNumber(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        gameHeader(
            canvas,
            "Number Flow",
            "Find numbers in the correct order"
        )

        text(
            canvas,
            "Find  $numberTarget",
            w / 2f,
            105f,
            24f,
            0xff5de8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        val minX = 65f
        val maxX = w - 65f
        val minY = 165f
        val maxY = h - 170f

        for (i in 0 until 12) {

            val x =
                minX +
                        numberPositions[i].first *
                        (maxX - minX)

            val y =
                minY +
                        numberPositions[i].second *
                        (maxY - minY)

            card(
                canvas,
                x - 35f,
                y - 30f,
                x + 35f,
                y + 30f,
                15f
            )

            text(
                canvas,
                numberPositions[i].first
                    .toString()
                    .substringAfter(".")
                    .take(2),
                x,
                y + 6f,
                13f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        text(
            canvas,
            "Tap the target number",
            w / 2f,
            h - 125f,
            13f,
            0xff9ca8ca.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // ZEN BREATH
    // =========================================================

    private fun startBreath() {
        screen = Screen.BREATH
        breathRunning = true
        breathCycles = 0
        breathPhase = 0
        breathPhaseStart = System.currentTimeMillis()
    }

    private fun drawBreath(
        canvas: Canvas,
        w: Float,
        h: Float
    ) {
        gameHeader(
            canvas,
            "Zen Breath",
            "Follow the circle slowly"
        )

        val elapsed =
            System.currentTimeMillis() -
                    breathPhaseStart

        if (elapsed > 4000L) {

            breathPhase =
                (breathPhase + 1) % 4

            breathPhaseStart =
                System.currentTimeMillis()

            if (breathPhase == 0) {
                breathCycles++
            }
        }

        val progress =
            (elapsed.coerceIn(0L, 4000L) / 4000f)

        val radius =
            when (breathPhase) {
                0 -> 75f + progress * 75f
                1 -> 150f
                2 -> 150f - progress * 75f
                else -> 75f
            }

        val cx = w / 2f
        val cy = h / 2f

        paint.color = Color.argb(
            30,
            75,
            235,
            220
        )

        canvas.drawCircle(
            cx,
            cy,
            radius + 28f,
            paint
        )

        paint.color =
            0xff62e6cf.toInt()

        canvas.drawCircle(
            cx,
            cy,
            radius,
            paint
        )

        val phaseText =
            when (breathPhase) {
                0 -> "BREATHE IN"
                1 -> "HOLD"
                2 -> "BREATHE OUT"
                else -> "REST"
            }

        text(
            canvas,
            phaseText,
            cx,
            cy + 9f,
            20f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            canvas,
            "Cycles  $breathCycles",
            cx,
            h - 135f,
            14f,
            0xffa4b1d3.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // GAME HEADER
    // =========================================================

    private fun gameHeader(
        canvas: Canvas,
        title: String,
        subtitle: String
    ) {
        text(
            canvas,
            "‹",
            18f,
            45f,
            40f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            title,
            58f,
            39f,
            22f,
            Color.WHITE,
            true
        )

        text(
            canvas,
            subtitle,
            58f,
            59f,
            9f,
            0xff96a2c8.toInt()
        )

        card(
            canvas,
            width - 92f,
            15f,
            width - 12f,
            48f,
            16f
        )

        text(
            canvas,
            "✦ $score",
            width - 52f,
            37f,
            11f,
            0xffffd966.toInt(),
            true,
            Paint.Align.CENTER
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
        val l = 30f
        val r = w - 30f
        val t = h - 145f
        val b = h - 95f

        card(
            canvas,
            l,
            t,
            r,
            b,
            20f
        )

        text(
            canvas,
            toastText,
            w / 2f,
            t + 31f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    private fun toast(message: String) {
        toastText = message
        toastUntil =
            System.currentTimeMillis() + 1800L
    }

    // =========================================================
    // FINISH GAME
    // =========================================================

    private var finishing = false

    private fun finishGame(message: String) {

        if (finishing) return

        finishing = true

        score += 25
        gamesPlayed++

        prefs.edit()
            .putInt("score", score)
            .putInt("games_played", gamesPlayed)
            .apply()

        toast(message)

        handler.postDelayed(
            {
                finishing = false
                screen = Screen.GAMES
            },
            1200L
        )
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

        when (screen) {

            Screen.HOME ->
                homeTouch(x, y)

            Screen.GAMES ->
                gamesTouch(x, y)

            Screen.MUSIC ->
                musicTouch(x, y)

            Screen.PROFILE ->
                profileTouch(x, y)

            Screen.PULSE ->
                pulseTouch(x, y)

            Screen.MEMORY ->
                memoryTouch(x, y)

            Screen.COLOR ->
                colorTouch(x, y)

            Screen.REACTION ->
                reactionTouch(x, y)

            Screen.NUMBER ->
                numberTouch(x, y)

            Screen.BREATH ->
                breathTouch(x, y)
        }

        return true
    }

    // =========================================================
    // HOME TOUCH
    // =========================================================

    private fun homeTouch(
        x: Float,
        y: Float
    ) {
        if (bottomTouch(x, y)) return

        val w = width.toFloat()

        if (y in 194f..247f) {
            toggleMusic()
            return
        }

        if (y in 286f..576f) {

            val col =
                if (x < w / 2f)
                    0
                else
                    1

            val row =
                ((y - 286f) / 100f)
                    .toInt()

            when (row * 2 + col) {
                0 -> startPulse()
                1 -> startMemory()
                2 -> startReaction()
                3 -> startColor()
                4 -> startNumber()
                5 -> startBreath()
            }
        }
    }

    // =========================================================
    // GAMES TOUCH
    // =========================================================

    private fun gamesTouch(
        x: Float,
        y: Float
    ) {
        if (bottomTouch(x, y)) return

        if (y < 70f && x < 100f) {
            screen = Screen.HOME
            return
        }

        if (y < 720f && y >= 90f) {

            val row =
                ((y - 90f) / 105f).toInt()

            val col =
                if (x < width / 2f) 0 else 1

            val index =
                row * 2 + col

            when (index) {
                0 -> startPulse()
                1 -> startMemory()
                2 -> startReaction()
                3 -> startColor()
                4 -> startNumber()
                5 -> startBreath()
            }
        }
    }

    // =========================================================
    // MUSIC TOUCH
    // =========================================================

    private fun musicTouch(
        x: Float,
        y: Float
    ) {
        if (bottomTouch(x, y)) return

        if (y in 210f..285f) {
            toggleMusic()
        }
    }

    // =========================================================
    // PROFILE TOUCH
    // =========================================================

    private fun profileTouch(
        x: Float,
        y: Float
    ) {
        if (bottomTouch(x, y)) return

        if (y in 260f..320f) {
            toggleMusic()
        }
    }

    // =========================================================
    // PULSE TOUCH
    // =========================================================

    private fun pulseTouch(
        x: Float,
        y: Float
    ) {
        if (x < 80f && y < 80f) {
            screen = Screen.GAMES
            return
        }

        val distance =
            hypot(
                x - pulseX,
                y - pulseY
            )

        if (distance <= pulseRadius + 35f) {

            pulseHits++

            score += 5

            prefs.edit()
                .putInt("score", score)
                .apply()

            movePulse()
        }
    }

    // =========================================================
    // MEMORY TOUCH
    // =========================================================

    private fun memoryTouch(
        x: Float,
        y: Float
    ) {
        if (x < 80f && y < 80f) {
            screen = Screen.GAMES
            return
        }

        if (memoryShown) return

        val size = min(width - 70f, 330f)
        val left = (width - size) / 2f
        val top = 125f
        val cell = size / 3f

        if (
            x < left ||
            x > left + size ||
            y < top ||
            y > top + size
        ) return

        val col =
            ((x - left) / cell).toInt()

        val row =
            ((y - top) / cell).toInt()

        val index =
            row * 3 + col

        if (memorySequence.getOrNull(memoryStep) == index) {

            memoryStep++

            if (memoryStep >= memorySequence.size) {

                score += 25

                prefs.edit()
                    .putInt("score", score)
                    .apply()

                memoryRound++

                if (memoryRound > 5) {

                    finishGame(
                        "Memory Matrix complete! +25 XP"
                    )

                } else {

                    toast("Great memory! Round $memoryRound")

                    memoryStep = 0

                    createMemoryRound()
                }
            }

        } else {

            toast("Pattern missed — try again")

            memoryStep = 0
            createMemoryRound()
        }
    }

    // =========================================================
    // COLOR TOUCH
    // =========================================================

    private fun colorTouch(
        x: Float,
        y: Float
    ) {
        if (x < 80f && y < 80f) {
            screen = Screen.GAMES
            return
        }

        for (i in colorNames.indices) {

            val row = i / 2
            val col = i % 2

            val l =
                if (col == 0) 20f else width / 2f + 10f

            val r =
                if (col == 0)
                    width / 2f - 10f
                else
                    width - 20f

            val t =
                225f + row * 82f

            val b = t + 65f

            if (
                x >= l &&
                x <= r &&
                y >= t &&
                y <= b
            ) {

                if (i == colorTarget) {

                    colorCorrect++
                    colorRound++

                    score += 10

                    prefs.edit()
                        .putInt("score", score)
                        .apply()

                    if (colorRound > 10) {

                        finishGame(
                            "Color Match complete! +25 XP"
                        )

                    } else {
                        nextColorRound()
                        toast("Correct!")
                    }

                } else {

                    toast("Not that color — focus!")

                    nextColorRound()
                }

                return
            }
        }
    }

    // =========================================================
    // REACTION TOUCH
    // =========================================================

    private fun reactionTouch(
        x: Float,
        y: Float
    ) {
        if (x < 80f && y < 80f) {
            screen = Screen.GAMES
            return
        }

        if (reactionReady) {

            val reaction =
                System.currentTimeMillis() -
                        reactionStart

            reactionAttempts++

            if (
                reactionBest == 0L ||
                reaction < reactionBest
            ) {
                reactionBest = reaction
            }

            score += 15

            prefs.edit()
                .putInt("score", score)
                .apply()

            if (reactionAttempts >= 3) {

                finishGame(
                    "Reaction complete! Best ${reactionBest}ms"
                )

            } else {

                toast("$reaction ms")

                scheduleReaction()
            }

        } else if (reactionWaiting) {

            reactionReady = false
            reactionWaiting = false

            toast("Too soon!")

            handler.postDelayed(
                {
                    if (screen == Screen.REACTION) {
                        scheduleReaction()
                    }
                },
                900L
            )
        }
    }

    // =========================================================
    // NUMBER TOUCH
    // =========================================================

    private fun numberTouch(
        x: Float,
        y: Float
    ) {
        if (x < 80f && y < 80f) {
            screen = Screen.GAMES
            return
        }

        val minX = 65f
        val maxX = width - 65f
        val minY = 165f
        val maxY = height - 170f

        for (i in 0 until 12) {

            val px =
                minX +
                        numberPositions[i].first *
                        (maxX - minX)

            val py =
                minY +
                        numberPositions[i].second *
                        (maxY - minY)

            if (
                hypot(
                    x - px,
                    y - py
                ) < 45f
            ) {

                val shown =
                    numberPositions[i].first
                        .toString()
                        .substringAfter(".")
                        .take(2)
                        .toIntOrNull()
                        ?: -1

                if (shown == numberTarget) {

                    score += 10

                    numberRound++

                    prefs.edit()
                        .putInt("score", score)
                        .apply()

                    if (numberRound > 7) {

                        finishGame(
                            "Number Flow complete! +25 XP"
                        )

                    } else {

                        numberTarget =
                            Random.nextInt(1, 100)

                        generateNumberBoard()

                        toast("Nice! Next number")
                    }

                } else {

                    toast("Find $numberTarget")
                }

                return
            }
        }
    }

    // =========================================================
    // BREATH TOUCH
    // =========================================================

    private fun breathTouch(
        x: Float,
        y: Float
    ) {
        if (x < 80f && y < 80f) {
            screen = Screen.GAMES
            return
        }

        if (breathCycles >= 3) {
            finishGame(
                "Zen session complete! +25 XP"
            )
        }
    }

    // =========================================================
    // BOTTOM NAV TOUCH
    // =========================================================

    private fun bottomTouch(
        x: Float,
        y: Float
    ): Boolean {

        if (y < height - 100f) {
            return false
        }

        val index =
            (x / (width / 4f))
                .toInt()
                .coerceIn(0, 3)

        screen =
            when (index) {
                0 -> Screen.HOME
                1 -> Screen.GAMES
                2 -> Screen.MUSIC
                else -> Screen.PROFILE
            }

        return true
    }

    // =========================================================
    // MUSIC ENGINE
    // =========================================================

    fun startMusic() {

        if (!musicEnabled) return

        if (musicRunning) return

        musicRunning = true

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

                    track.play()

                    val buffer =
                        ShortArray(2048)

                    var sampleIndex = 0L

                    val notes =
                        doubleArrayOf(
                            220.0,
                            261.63,
                            329.63,
                            392.0,
                            329.63,
                            293.66,
                            261.63,
                            329.63
                        )

                    while (musicRunning) {

                        for (i in buffer.indices) {

                            val time =
                                sampleIndex.toDouble() /
                                        sampleRate

                            val noteIndex =
                                ((time / 2.8) %
                                        notes.size)
                                    .toInt()

                            val freq =
                                notes[noteIndex]

                            val slow =
                                sin(
                                    2.0 *
                                            Math.PI *
                                            0.08 *
                                            time
                                )

                            val wave =
                                sin(
                                    2.0 *
                                            Math.PI *
                                            freq *
                                            time
                                )

                            val harmonic =
                                sin(
                                    2.0 *
                                            Math.PI *
                                            freq *
                                            0.5 *
                                            time
                                )

                            val fade =
                                0.045 +
                                        0.018 *
                                        ((slow + 1.0) / 2.0)

                            val value =
                                (
                                    (wave * 0.65 +
                                            harmonic * 0.2) *
                                            fade *
                                            Short.MAX_VALUE
                                    ).toInt()
                                        .coerceIn(
                                            Short.MIN_VALUE.toInt(),
                                            Short.MAX_VALUE.toInt()
                                        )

                            buffer[i] =
                                value.toShort()

                            sampleIndex++
                        }

                        track.write(
                            buffer,
                            0,
                            buffer.size
                        )
                    }

                    track.stop()
                    track.release()

                } catch (_: Exception) {
                }
            }

        musicThread?.start()
    }

    fun stopMusic() {

        musicRunning = false

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        } catch (_: Exception) {
        }

        audioTrack = null
        musicThread = null
    }

    private fun toggleMusic() {

        musicEnabled = !musicEnabled

        prefs.edit()
            .putBoolean(
                "music_enabled",
                musicEnabled
            )
            .apply()

        if (musicEnabled) {

            startMusic()

            toast("Mind music ON")

        } else {

            stopMusic()

            toast("Mind music OFF")
        }
    }

    // =========================================================
    // BACK
    // =========================================================

    fun goBack(): Boolean {

        when (screen) {

            Screen.HOME -> return false

            Screen.GAMES,
            Screen.MUSIC,
            Screen.PROFILE -> {
                screen = Screen.HOME
            }

            Screen.PULSE,
            Screen.MEMORY,
            Screen.COLOR,
            Screen.REACTION,
            Screen.NUMBER,
            Screen.BREATH -> {
                screen = Screen.GAMES
            }
        }

        return true
    }
}
