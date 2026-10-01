package com.mindblow.app

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var view: MindBlowView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(5, 7, 24)
        window.navigationBarColor = Color.rgb(5, 7, 24)

        view = MindBlowView(this)
        setContentView(view)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!view.goBack()) {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        view.releaseMusic()
        super.onDestroy()
    }
}

private enum class Screen {
    WELCOME,
    HOME,
    GAMES,
    PUZZLE,
    MEMORY,
    REACTION,
    COLORS,
    NUMBERS,
    BREATHE,
    MUSIC
}

private class MindBlowView(
    private val ctx: Context
) : View(ctx) {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs =
        ctx.getSharedPreferences("mindblow", Context.MODE_PRIVATE)

    private val handler = Handler(Looper.getMainLooper())

    private var screen =
        if (prefs.getBoolean("started", false)) {
            Screen.HOME
        } else {
            Screen.WELCOME
        }

    private var time = 0f

    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)

    private var toastText = ""
    private var toastUntil = 0L

    // Music
    private var mediaPlayer: MediaPlayer? = null
    private var musicPlaying = false

    // Puzzle
    private var puzzleTarget = Random.nextInt(16)

    // Memory
    private val memorySymbols = arrayOf(
        "★", "◆", "●",
        "✦", "☀", "☾",
        "✿", "❖", "♣"
    )

    private var memoryBoard =
        MutableList(9) { it }.apply { shuffle() }

    private var memoryFirst = -1
    private var memorySecond = -1
    private var memoryPreview = false

    // Reaction
    private var reactionReady = false
    private var reactionStart = 0L
    private var reactionWaiting = false

    // Color
    private var colorCorrect = 0
    private var colorRound = 0

    // Number
    private var numberSequence = ""
    private var numberInput = ""
    private var numberRound = 1
    private var showingNumber = false

    // Breathing
    private var breathStart = 0L

    init {
        isFocusable = true
        startAnimation()
    }

    private fun startAnimation() {
        postInvalidateDelayed(16L)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)

        val w = width.toFloat()
        val h = height.toFloat()

        time += 0.018f

        drawBackground(c, w, h)

        when (screen) {
            Screen.WELCOME -> drawWelcome(c, w, h)
            Screen.HOME -> drawHome(c, w, h)
            Screen.GAMES -> drawGames(c, w, h)
            Screen.PUZZLE -> drawPuzzle(c, w, h)
            Screen.MEMORY -> drawMemory(c, w, h)
            Screen.REACTION -> drawReaction(c, w, h)
            Screen.COLORS -> drawColors(c, w, h)
            Screen.NUMBERS -> drawNumbers(c, w, h)
            Screen.BREATHE -> drawBreathe(c, w, h)
            Screen.MUSIC -> drawMusic(c, w, h)
        }

        if (toastUntil > System.currentTimeMillis()) {
            drawToast(c, w, h)
        }

        postInvalidateDelayed(16L)
    }

    // =========================================================
    // BACKGROUND
    // =========================================================

    private fun drawBackground(c: Canvas, w: Float, h: Float) {

        p.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            Color.rgb(4, 7, 28),
            Color.rgb(31, 8, 58),
            Shader.TileMode.CLAMP
        )

        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        val t = time

        p.color = Color.argb(45, 0, 220, 255)
        c.drawCircle(
            w * 0.16f + sin(t) * 45f,
            h * 0.20f + cos(t) * 30f,
            130f,
            p
        )

        p.color = Color.argb(35, 170, 70, 255)
        c.drawCircle(
            w * 0.85f + cos(t * 0.7f) * 45f,
            h * 0.38f + sin(t) * 35f,
            150f,
            p
        )

        p.color = Color.argb(28, 20, 255, 180)
        c.drawCircle(
            w * 0.50f + sin(t * 0.5f) * 40f,
            h * 0.80f,
            160f,
            p
        )

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = Color.argb(20, 100, 220, 255)

        for (i in 0 until 7) {
            c.drawCircle(
                w / 2f,
                h * 0.43f,
                70f + i * 48f + sin(t + i) * 4f,
                p
            )
        }

        p.style = Paint.Style.FILL
    }

    // =========================================================
    // TEXT
    // =========================================================

    private fun text(
        c: Canvas,
        s: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int = Color.WHITE,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        p.shader = null
        p.color = color
        p.textSize = size
        p.textAlign = align
        p.typeface = Typeface.create(
            "sans-serif",
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )
        c.drawText(s, x, y, p)
    }

    // =========================================================
    // CARD
    // =========================================================

    private fun card(
        c: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        radius: Float = 20f
    ) {
        p.shader = null
        p.color = Color.argb(230, 12, 18, 45)
        c.drawRoundRect(l, t, r, b, radius, radius, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = Color.argb(65, 80, 180, 255)
        c.drawRoundRect(l, t, r, b, radius, radius, p)

        p.style = Paint.Style.FILL
    }

    // =========================================================
    // BUTTON
    // =========================================================

    private fun button(
        c: Canvas,
        label: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {
        p.shader = LinearGradient(
            l,
            t,
            r,
            b,
            Color.rgb(25, 215, 255),
            Color.rgb(135, 65, 255),
            Shader.TileMode.CLAMP
        )

        c.drawRoundRect(l, t, r, b, 18f, 18f, p)

        p.shader = null

        text(
            c,
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
        c: Canvas,
        title: String,
        subtitle: String
    ) {
        text(c, "‹", 20f, 48f, 40f)
        text(c, title, 58f, 40f, 23f, Color.WHITE, true)
        text(c, subtitle, 58f, 62f, 11f, 0xff9ba8d0.toInt())
    }

    // =========================================================
    // BOTTOM NAV
    // =========================================================

    private fun navigation(
        c: Canvas,
        w: Float,
        h: Float,
        selected: Int
    ) {
        val top = h - 78f

        card(c, 10f, top, w - 10f, h - 8f, 22f)

        val icons = arrayOf("⌂", "◆", "♫", "★")
        val labels = arrayOf("Home", "Games", "Music", "Profile")

        for (i in 0..3) {

            val x = w * (i + 0.5f) / 4f

            val color =
                if (i == selected) {
                    0xff5ce8ff.toInt()
                } else {
                    0xff7885aa.toInt()
                }

            text(
                c,
                icons[i],
                x,
                top + 30f,
                22f,
                color,
                true,
                Paint.Align.CENTER
            )

            text(
                c,
                labels[i],
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
    // WELCOME
    // =========================================================

    private fun drawWelcome(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            c,
            "✦",
            w / 2f,
            h * 0.38f,
            82f,
            0xff62e8ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "MINDBLOW",
            w / 2f,
            h * 0.49f,
            38f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "RESET • FOCUS • REFRESH",
            w / 2f,
            h * 0.55f,
            12f,
            0xffaeb9dc.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            c,
            "Six tiny challenges for your mind.",
            w / 2f,
            h * 0.63f,
            16f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        button(
            c,
            "START MINDBLOW",
            w * .14f,
            h * .74f,
            w * .86f,
            h * .83f
        )
    }

    // =========================================================
    // HOME
    // =========================================================

    private fun drawHome(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        text(c, "MindBlow", 20f, 40f, 28f, Color.WHITE, true)

        text(
            c,
            "Your daily mental reset",
            20f,
            62f,
            12f,
            0xff9ca8cc.toInt()
        )

        card(c, w - 125f, 17f, w - 18f, 58f, 17f)

        text(
            c,
            "✦ $score",
            w - 71f,
            43f,
            15f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )

        card(c, 18f, 82f, w - 18f, 178f, 23f)

        text(c, "READY FOR A RESET?", 34f, 112f, 11f, 0xff5ce7ff.toInt(), true)

        text(
            c,
            "Refresh your mind.",
            34f,
            143f,
            23f,
            Color.WHITE,
            true
        )

        text(
            c,
            "Pick a challenge below.",
            34f,
            163f,
            12f,
            0xffaab5d6.toInt()
        )

        text(
            c,
            "6 GAMES",
            w - 42f,
            112f,
            11f,
            0xffaeb9d8.toInt(),
            true,
            Paint.Align.RIGHT
        )

        text(
            c,
            "STREAK $streak",
            w - 42f,
            135f,
            11f,
            0xffffbd45.toInt(),
            true,
            Paint.Align.RIGHT
        )

        text(
            c,
            "Mind Refreshing Games",
            20f,
            214f,
            19f,
            Color.WHITE,
            true
        )

        gameCard(c, "✦", "Puzzle", "Find the glow", 18f, 230f, w / 2f - 8f, 325f)
        gameCard(c, "◉", "Memory", "Train recall", w / 2f + 8f, 230f, w - 18f, 325f)

        gameCard(c, "⚡", "Reaction", "React fast", 18f, 335f, w / 2f - 8f, 430f)
        gameCard(c, "●", "Colors", "Spot the odd one", w / 2f + 8f, 335f, w - 18f, 430f)

        gameCard(c, "123", "Number Flow", "Remember digits", 18f, 440f, w / 2f - 8f, 535f)
        gameCard(c, "◌", "Breathe", "Calm your mind", w / 2f + 8f, 440f, w - 18f, 535f)

        navigation(c, w, h, 0)
    }

    private fun gameCard(
        c: Canvas,
        icon: String,
        title: String,
        subtitle: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {
        card(c, l, t, r, b, 19f)

        text(
            c,
            icon,
            l + 35f,
            t + 42f,
            23f,
            0xff5ce7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(c, title, l + 17f, t + 68f, 15f, Color.WHITE, true)
        text(c, subtitle, l + 17f, t + 86f, 10f, 0xff9da8cc.toInt())
    }

    // =========================================================
    // GAMES
    // =========================================================

    private fun drawGames(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(c, "Mind Games", "Choose a quick challenge")

        gameCard(c, "✦", "Puzzle", "Find the glow", 18f, 90f, w / 2f - 8f, 185f)
        gameCard(c, "◉", "Memory", "Train recall", w / 2f + 8f, 90f, w - 18f, 185f)

        gameCard(c, "⚡", "Reaction", "React fast", 18f, 198f, w / 2f - 8f, 293f)
        gameCard(c, "●", "Colors", "Spot odd color", w / 2f + 8f, 198f, w - 18f, 293f)

        gameCard(c, "123", "Number Flow", "Remember digits", 18f, 306f, w / 2f - 8f, 401f)
        gameCard(c, "◌", "Breathe", "Slow your mind", w / 2f + 8f, 306f, w - 18f, 401f)

        card(c, 18f, 418f, w - 18f, 488f, 20f)

        text(
            c,
            "Each game gives XP",
            34f,
            449f,
            15f,
            Color.WHITE,
            true
        )

        text(
            c,
            "Play • relax • improve your score",
            34f,
            472f,
            11f,
            0xff9faace.toInt()
        )

        navigation(c, w, h, 1)
    }

    // =========================================================
    // PUZZLE
    // =========================================================

    private fun drawPuzzle(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(c, "Glow Hunt", "Tap the glowing tile")

        val size = min(w * .82f, 330f)
        val left = (w - size) / 2f
        val top = 100f
        val cell = size / 4f

        for (i in 0 until 16) {

            val row = i / 4
            val col = i % 4

            val l = left + col * cell + 4f
            val tt = top + row * cell + 4f
            val r = left + (col + 1) * cell - 4f
            val b = top + (row + 1) * cell - 4f

            card(c, l, tt, r, b, 12f)

            if (i == puzzleTarget) {

                p.color = 0xff4de8ff.toInt()

                c.drawCircle(
                    (l + r) / 2f,
                    (tt + b) / 2f,
                    16f + sin(time * 4f) * 5f,
                    p
                )
            }
        }

        text(
            c,
            "Find it before the glow moves!",
            w / 2f,
            top + size + 35f,
            14f,
            0xffb5c0df.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // MEMORY
    // =========================================================

    private fun drawMemory(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(c, "Memory Matrix", "Remember the symbols")

        val size = min(w * .82f, 320f)
        val left = (w - size) / 2f
        val top = 100f
        val cell = size / 3f

        for (i in 0 until 9) {

            val row = i / 3
            val col = i % 3

            val l = left + col * cell + 5f
            val tt = top + row * cell + 5f
            val r = left + (col + 1) * cell - 5f
            val b = top + (row + 1) * cell - 5f

            card(c, l, tt, r, b, 15f)

            val revealed =
                memoryPreview ||
                i == memoryFirst ||
                i == memorySecond

            text(
                c,
                if (revealed) memorySymbols[memoryBoard[i]] else "?",
                (l + r) / 2f,
                (tt + b) / 2f + 11f,
                28f,
                0xff62e5ff.toInt(),
                true,
                Paint.Align.CENTER
            )
        }

        text(
            c,
            if (memoryPreview) "MEMORIZE..." else "MATCH TWO CARDS",
            w / 2f,
            top + size + 35f,
            14f,
            0xffb4bfdf.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // REACTION
    // =========================================================

    private fun drawReaction(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(c, "Reaction", "Tap when the circle turns green")

        val cx = w / 2f
        val cy = 270f

        if (reactionWaiting) {

            p.color = 0xffff4f68.toInt()

            c.drawCircle(cx, cy, 105f + sin(time * 3f) * 5f, p)

            text(
                c,
                "WAIT...",
                cx,
                cy + 9f,
                25f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )

        } else if (reactionReady) {

            p.color = 0xff46f5a1.toInt()
            c.drawCircle(cx, cy, 115f + sin(time * 4f) * 7f, p)

            text(
                c,
                "TAP!",
                cx,
                cy + 10f,
                28f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )

        } else {

            p.color = 0xff5be5ff.toInt()
            c.drawCircle(cx, cy, 100f, p)

            text(
                c,
                "START",
                cx,
                cy + 9f,
                23f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        text(
            c,
            "Fast reactions earn +20 XP",
            cx,
            430f,
            14f,
            0xffaeb9d9.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // COLORS
    // =========================================================

    private fun drawColors(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(c, "Color Focus", "Find the different tile")

        val colors = intArrayOf(
            Color.rgb(55, 220, 255),
            Color.rgb(255, 75, 145),
            Color.rgb(155, 85, 255),
            Color.rgb(75, 240, 160)
        )

        val correct = colorCorrect % 4
        val odd = (colorRound + 1) % 9

        val size = min(w * .82f, 320f)
        val left = (w - size) / 2f
        val top = 115f
        val cell = size / 3f

        for (i in 0 until 9) {

            val row = i / 3
            val col = i % 3

            val l = left + col * cell + 6f
            val tt = top + row * cell + 6f
            val r = left + (col + 1) * cell - 6f
            val b = top + (row + 1) * cell - 6f

            val colorIndex = correct

            p.color =
                if (i == odd) {
                    colors[colorIndex].let {
                        Color.rgb(
                            min(255, Color.red(it) + 55),
                            min(255, Color.green(it) + 25),
                            min(255, Color.blue(it) + 25)
                        )
                    }
                } else {
                    colors[colorIndex]
                }

            c.drawRoundRect(l, tt, r, b, 18f, 18f, p)
        }

        text(
            c,
            "Round ${colorRound + 1}",
            w / 2f,
            top + size + 35f,
            15f,
            0xffb5c0df.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    // =========================================================
    // NUMBER FLOW
    // =========================================================

    private fun drawNumbers(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(c, "Number Flow", "Remember the sequence")

        text(
            c,
            "ROUND $numberRound",
            w / 2f,
            125f,
            13f,
            0xff5ce7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        if (showingNumber) {

            text(
                c,
                numberSequence,
                w / 2f,
                235f,
                38f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )

            text(
                c,
                "MEMORIZE",
                w / 2f,
                280f,
                13f,
                0xffaab7da.toInt(),
                true,
                Paint.Align.CENTER
            )

        } else {

            card(c, 35f, 170f, w - 35f, 245f, 20f)

            text(
                c,
                if (numberInput.isEmpty()) "TYPE THE NUMBER" else numberInput,
                w / 2f,
                217f,
                25f,
                0xff62e5ff.toInt(),
                true,
                Paint.Align.CENTER
            )

            button(
                c,
                "CHECK",
                55f,
                280f,
                w - 55f,
                335f
            )
        }

        val buttons = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

        val bw = (w - 80f) / 3f

        for (i in buttons.indices) {

            val row = i / 3
            val col = i % 3

            val l = 20f + col * (bw + 10f)
            val t = 365f + row * 53f

            card(c, l, t, l + bw, t + 46f, 14f)

            text(
                c,
                buttons[i],
                l + bw / 2f,
                t + 31f,
                18f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }
    }

    // =========================================================
    // BREATHE
    // =========================================================

    private fun drawBreathe(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(c, "Breathe", "A tiny calm moment")

        val elapsed =
            ((System.currentTimeMillis() - breathStart) % 12000L).toFloat()

        val phase = elapsed / 12000f

        val radius =
            if (phase < .5f) {
                65f + phase * 2f * 90f
            } else {
                155f - (phase - .5f) * 2f * 90f
            }

        p.color = Color.argb(35, 80, 220, 255)
        c.drawCircle(w / 2f, 265f, radius + 35f, p)

        p.color = 0xff57e5ff.toInt()
        c.drawCircle(w / 2f, 265f, radius, p)

        val instruction =
            if (phase < .5f) "BREATHE IN" else "BREATHE OUT"

        text(
            c,
            instruction,
            w / 2f,
            272f,
            22f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Follow the circle slowly.",
            w / 2f,
            405f,
            14f,
            0xffb0bbda.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            c,
            "I FEEL CALMER  +10 XP",
            45f,
            450f,
            w - 45f,
            505f
        )
    }

    // =========================================================
    // MUSIC
    // =========================================================

    private fun drawMusic(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(c, "Mind Music", "Relaxing background audio")

        card(c, 20f, 100f, w - 20f, 285f, 25f)

        text(
            c,
            "♫",
            w / 2f,
            180f,
            70f,
            0xff61e6ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Mind Refreshing",
            w / 2f,
            225f,
            24f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            if (musicPlaying) "Music is playing" else "Music is paused",
            w / 2f,
            250f,
            13f,
            0xffaeb9d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            c,
            if (musicPlaying) "PAUSE MUSIC" else "PLAY MUSIC",
            45f,
            310f,
            w - 45f,
            365f
        )

        text(
            c,
            "Tip: add mind_refreshing.mp3",
            w / 2f,
            415f,
            13f,
            0xff8f9cbe.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            c,
            "inside app/src/main/res/raw/",
            w / 2f,
            438f,
            12f,
            0xff7885aa.toInt(),
            false,
            Paint.Align.CENTER
        )

        navigation(c, w, h, 2)
    }

    // =========================================================
    // TOAST
    // =========================================================

    private fun drawToast(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        card(
            c,
            25f,
            h - 135f,
            w - 25f,
            h - 82f,
            18f
        )

        text(
            c,
            toastText,
            w / 2f,
            h - 103f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    private fun toast(message: String) {
        toastText = message
        toastUntil = System.currentTimeMillis() + 1400L
    }

    private fun save() {
        prefs.edit()
            .putBoolean("started", true)
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

            Screen.WELCOME -> {
                if (y > h * .68f) {
                    prefs.edit().putBoolean("started", true).apply()
                    screen = Screen.HOME
                }
            }

            Screen.HOME -> homeTouch(x, y, w, h)

            Screen.GAMES -> gamesTouch(x, y, w, h)

            Screen.PUZZLE -> puzzleTouch(x, y, w, h)

            Screen.MEMORY -> memoryTouch(x, y, w, h)

            Screen.REACTION -> reactionTouch(x, y, w, h)

            Screen.COLORS -> colorTouch(x, y, w, h)

            Screen.NUMBERS -> numberTouch(x, y, w, h)

            Screen.BREATHE -> breatheTouch(y, w, h)

            Screen.MUSIC -> musicTouch(x, y, w, h)
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

            if (x < w * .25f) {
                return
            }

            if (x < w * .50f) {
                screen = Screen.GAMES
                return
            }

            if (x < w * .75f) {
                screen = Screen.MUSIC
                return
            }

            return
        }

        if (y in 230f..325f) {
            screen =
                if (x < w / 2f) Screen.PUZZLE
                else {
                    startMemory()
                    Screen.MEMORY
                }
            return
        }

        if (y in 335f..430f) {
            screen =
                if (x < w / 2f) {
                    startReaction()
                    Screen.REACTION
                } else {
                    startColors()
                    Screen.COLORS
                }
            return
        }

        if (y in 440f..550f) {
            screen =
                if (x < w / 2f) {
                    startNumbers()
                    Screen.NUMBERS
                } else {
                    breathStart = System.currentTimeMillis()
                    Screen.BREATHE
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

            if (x < w * .25f) {
                screen = Screen.HOME
            } else if (x < w * .50f) {
                screen = Screen.GAMES
            } else if (x < w * .75f) {
                screen = Screen.MUSIC
            }

            return
        }

        if (y in 90f..185f) {

            screen =
                if (x < w / 2f) {
                    Screen.PUZZLE
                } else {
                    startMemory()
                    Screen.MEMORY
                }

            return
        }

        if (y in 198f..293f) {

            screen =
                if (x < w / 2f) {
                    startReaction()
                    Screen.REACTION
                } else {
                    startColors()
                    Screen.COLORS
                }

            return
        }

        if (y in 306f..401f) {

            screen =
                if (x < w / 2f) {
                    startNumbers()
                    Screen.NUMBERS
                } else {
                    breathStart = System.currentTimeMillis()
                    Screen.BREATHE
                }
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

        val size = min(w * .82f, 330f)
        val left = (w - size) / 2f
        val top = 100f
        val cell = size / 4f

        if (
            x >= left &&
            x <= left + size &&
            y >= top &&
            y <= top + size
        ) {

            val col = ((x - left) / cell).toInt()
            val row = ((y - top) / cell).toInt()

            val index = row * 4 + col

            if (index == puzzleTarget) {

                score += 25
                save()

                puzzleTarget = Random.nextInt(16)

                toast("Perfect! +25 XP")

            } else {
                toast("Almost! Find the glow.")
            }
        }
    }

    // =========================================================
    // MEMORY
    // =========================================================

    private fun startMemory() {

        memoryBoard =
            MutableList(9) { it }.apply { shuffle() }

        memoryFirst = -1
        memorySecond = -1
        memoryPreview = true

        handler.postDelayed({
            memoryPreview = false
            invalidate()
        }, 2200L)
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

        if (memoryPreview) return

        val size = min(w * .82f, 320f)
        val left = (w - size) / 2f
        val top = 100f
        val cell = size / 3f

        if (
            x < left ||
            x > left + size ||
            y < top ||
            y > top + size
        ) {
            return
        }

        val col = ((x - left) / cell).toInt().coerceIn(0, 2)
        val row = ((y - top) / cell).toInt().coerceIn(0, 2)

        val index = row * 3 + col

        if (memoryFirst == -1) {

            memoryFirst = index

        } else if (
            memorySecond == -1 &&
            index != memoryFirst
        ) {

            memorySecond = index

            if (
                memoryBoard[memoryFirst] ==
                memoryBoard[memorySecond]
            ) {

                score += 20
                save()

                toast("MATCH! +20 XP")

                handler.postDelayed({
                    memoryFirst = -1
                    memorySecond = -1
                    invalidate()
                }, 500L)

            } else {

                toast("Try again")

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
        reactionWaiting = true

        val delay = Random.nextLong(1000L, 3500L)

        handler.postDelayed({

            if (screen == Screen.REACTION) {
                reactionWaiting = false
                reactionReady = true
                reactionStart = System.currentTimeMillis()
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

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        if (reactionWaiting) {
            toast("Too early!")
            reactionWaiting = false
            reactionReady = false
            return
        }

        if (reactionReady) {

            val reaction =
                System.currentTimeMillis() - reactionStart

            score += 20
            save()

            toast("${reaction}ms • +20 XP")

            reactionReady = false

            handler.postDelayed({
                if (screen == Screen.REACTION) {
                    startReaction()
                    invalidate()
                }
            }, 900L)

        } else {
            startReaction()
        }
    }

    // =========================================================
    // COLORS
    // =========================================================

    private fun startColors() {
        colorRound = 0
        colorCorrect = Random.nextInt(4)
    }

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

        val size = min(w * .82f, 320f)
        val left = (w - size) / 2f
        val top = 115f
        val cell = size / 3f

        if (
            x < left ||
            x > left + size ||
            y < top ||
            y > top + size
        ) {
            return
        }

        val col = ((x - left) / cell).toInt().coerceIn(0, 2)
        val row = ((y - top) / cell).toInt().coerceIn(0, 2)

        val index = row * 3 + col
        val odd = (colorRound + 1) % 9

        if (index == odd) {

            score += 15
            colorRound++
            colorCorrect = Random.nextInt(4)
            save()

            toast("Great eye! +15 XP")

        } else {
            toast("Look closely...")
        }
    }

    // =========================================================
    // NUMBER FLOW
    // =========================================================

    private fun startNumbers() {

        numberRound = 1
        numberInput = ""
        createNumber()
    }

    private fun createNumber() {

        val length = min(3 + numberRound, 7)

        numberSequence = buildString {
            repeat(length) {
                append(Random.nextInt(0, 10))
            }
        }

        showingNumber = true
        numberInput = ""

        handler.postDelayed({

            if (screen == Screen.NUMBERS) {
                showingNumber = false
                invalidate()
            }

        }, 1800L)
    }

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

        if (showingNumber) return

        if (
            y >= 365f &&
            y <= 600f
        ) {

            val bw = (w - 80f) / 3f

            val col =
                ((x - 20f) / (bw + 10f)).toInt()

            val row =
                ((y - 365f) / 53f).toInt()

            if (
                col in 0..2 &&
                row in 0..3
            ) {

                val index = row * 3 + col

                val digit =
                    if (index < 9) {
                        (index + 1).toString()
                    } else {
                        "0"
                    }

                numberInput += digit
                invalidate()
                return
            }
        }

        if (y in 280f..350f) {

            if (numberInput == numberSequence) {

                score += 25
                numberRound++

                save()

                toast("Excellent! +25 XP")

                handler.postDelayed({
                    if (screen == Screen.NUMBERS) {
                        createNumber()
                        invalidate()
                    }
                }, 600L)

            } else {

                toast("Not quite. Try again.")
                numberInput = ""
            }
        }
    }

    // =========================================================
    // BREATHE TOUCH
    // =========================================================

    private fun breatheTouch(
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        if (y in 430f..530f) {

            score += 10
            streak++

            save()

            toast("Nice reset! +10 XP")
        }
    }

    // =========================================================
    // MUSIC
    // =========================================================

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

        if (y > h - 90f) {

            if (x < w * .25f) {
                screen = Screen.HOME
            } else if (x < w * .50f) {
                screen = Screen.GAMES
            } else if (x < w * .75f) {
                screen = Screen.MUSIC
            }

            return
        }

        if (y in 300f..390f) {
            toggleMusic()
        }
    }

    private fun toggleMusic() {

        if (musicPlaying) {

            mediaPlayer?.pause()
            musicPlaying = false
            toast("Music paused")
            return
        }

        if (mediaPlayer == null) {

            val resourceId =
                ctx.resources.getIdentifier(
                    "mind_refreshing",
                    "raw",
                    ctx.packageName
                )

            if (resourceId == 0) {

                toast("Add mind_refreshing.mp3 to res/raw")

                return
            }

            mediaPlayer =
                MediaPlayer.create(ctx, resourceId)

            mediaPlayer?.isLooping = true
        }

        mediaPlayer?.start()
        musicPlaying = true

        toast("Mind music playing")
    }

    fun releaseMusic() {

        mediaPlayer?.release()
        mediaPlayer = null
        musicPlaying = false
    }

    // =========================================================
    // BACK
    // =========================================================

    fun goBack(): Boolean {

        return if (screen == Screen.HOME || screen == Screen.WELCOME) {

            false

        } else {

            screen = Screen.HOME
            invalidate()
            true
        }
    }
}
