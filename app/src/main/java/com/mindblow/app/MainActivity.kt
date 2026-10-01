package com.mindblow.app

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(5, 7, 28)
        window.navigationBarColor = Color.rgb(5, 7, 28)

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
    HOME,
    GAMES,
    FOCUS,
    COLOR,
    MEMORY,
    NUMBER,
    BUBBLE,
    CALM,
    DAILY,
    PROFILE
}

private data class Bubble(
    var x: Float,
    var y: Float,
    var radius: Float,
    var vx: Float,
    var vy: Float,
    var type: Int,
    var life: Float = 1f
)

private class MindBlowView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    private fun d(value: Float): Float = value * density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs =
        context.getSharedPreferences("mindblow_data", Context.MODE_PRIVATE)

    private var screen =
        if (prefs.getBoolean("started", false)) {
            Screen.HOME
        } else {
            Screen.SPLASH
        }

    private var startedTime = System.currentTimeMillis()
    private var animation = 0f

    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)

    private var message = ""
    private var messageUntil = 0L

    // ---------------------------------------------------------
    // FOCUS ORB
    // ---------------------------------------------------------

    private var focusHits = 0
    private var focusGoal = 10
    private var focusX = 0f
    private var focusY = 0f
    private var focusRadius = 32f
    private var focusRoundStarted = false

    // ---------------------------------------------------------
    // COLOR HUNT
    // ---------------------------------------------------------

    private val gameColors = intArrayOf(
        0xff39d9ff.toInt(),
        0xffff5f8f.toInt(),
        0xffffd34d.toInt(),
        0xff9b6cff.toInt(),
        0xff52e68b.toInt(),
        0xffff914d.toInt()
    )

    private var colorTarget = 0
    private var colorOptions = IntArray(6) { it }
    private var colorScore = 0
    private var colorRound = 0
    private var colorStartTime = 0L

    // ---------------------------------------------------------
    // MEMORY MATCH
    // ---------------------------------------------------------

    private val memorySymbols = arrayOf(
        "★", "◆", "●", "✦", "☀", "☾"
    )

    private var memoryCards = mutableListOf<Int>()
    private var memoryOpenA = -1
    private var memoryOpenB = -1
    private var memoryMatched = BooleanArray(12)
    private var memoryLocked = false
    private var memoryMoves = 0
    private var memoryPairs = 0

    // ---------------------------------------------------------
    // NUMBER FLOW
    // ---------------------------------------------------------

    private var numberPositions = mutableListOf<Pair<Float, Float>>()
    private var nextNumber = 1
    private var numberStart = 0L
    private var numberBestTime = 0L

    // ---------------------------------------------------------
    // BUBBLE BLAST
    // ---------------------------------------------------------

    private val bubbles = mutableListOf<Bubble>()
    private var bubbleScore = 0
    private var bubbleTime = 0L
    private var bubbleStarted = false

    // ---------------------------------------------------------
    // CALM FLOW
    // ---------------------------------------------------------

    private var calmStart = 0L
    private var calmPhase = 0
    private var calmCycles = 0
    private var calmLastPhase = -1

    // ---------------------------------------------------------
    // INIT
    // ---------------------------------------------------------

    init {
        isFocusable = true
        postInvalidateDelayed(16L)
    }

    // ---------------------------------------------------------
    // DRAW
    // ---------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width / density
        val h = height / density

        canvas.save()
        canvas.scale(density, density)

        animation += 0.025f

        drawBackground(canvas, w, h)

        when (screen) {
            Screen.SPLASH -> drawSplash(canvas, w, h)
            Screen.HOME -> drawHome(canvas, w, h)
            Screen.GAMES -> drawGames(canvas, w, h)
            Screen.FOCUS -> drawFocus(canvas, w, h)
            Screen.COLOR -> drawColorHunt(canvas, w, h)
            Screen.MEMORY -> drawMemory(canvas, w, h)
            Screen.NUMBER -> drawNumberFlow(canvas, w, h)
            Screen.BUBBLE -> drawBubbleBlast(canvas, w, h)
            Screen.CALM -> drawCalmFlow(canvas, w, h)
            Screen.DAILY -> drawDaily(canvas, w, h)
            Screen.PROFILE -> drawProfile(canvas, w, h)
        }

        if (messageUntil > System.currentTimeMillis()) {
            drawToast(canvas, w, h)
        }

        canvas.restore()

        updateGames(w, h)

        if (
            screen == Screen.SPLASH &&
            System.currentTimeMillis() - startedTime > 1500L
        ) {
            screen = Screen.HOME
        }

        postInvalidateDelayed(16L)
    }

    // ---------------------------------------------------------
    // BACKGROUND
    // ---------------------------------------------------------

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
            Color.rgb(4, 7, 28),
            Color.rgb(28, 5, 54),
            Shader.TileMode.CLAMP
        )

        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val t = animation

        paint.color = Color.argb(30, 25, 220, 255)
        c.drawCircle(
            w * 0.12f + sin(t) * 35f,
            h * 0.17f,
            130f,
            paint
        )

        paint.color = Color.argb(28, 130, 70, 255)
        c.drawCircle(
            w * 0.91f + cos(t * 0.7f) * 30f,
            h * 0.40f,
            145f,
            paint
        )

        paint.color = Color.argb(24, 20, 170, 255)
        c.drawCircle(
            w * 0.52f + sin(t * 0.5f) * 50f,
            h * 0.86f,
            155f,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(18, 100, 180, 255)

        val centerX = w * 0.5f
        val centerY = h * 0.53f

        for (i in 0 until 8) {
            c.drawCircle(
                centerX,
                centerY,
                40f + i * 36f + sin(t + i) * 4f,
                paint
            )
        }

        paint.style = Paint.Style.FILL
    }

    // ---------------------------------------------------------
    // TEXT
    // ---------------------------------------------------------

    private fun text(
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
        paint.typeface = Typeface.create(
            "sans-serif",
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )

        c.drawText(value, x, y, paint)
    }

    // ---------------------------------------------------------
    // CARD
    // ---------------------------------------------------------

    private fun card(
        c: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        radius: Float = 18f
    ) {
        paint.shader = null
        paint.color = Color.argb(225, 10, 17, 43)

        c.drawRoundRect(
            l,
            t,
            r,
            b,
            radius,
            radius,
            paint
        )

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(65, 85, 150, 220)

        c.drawRoundRect(
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

    // ---------------------------------------------------------
    // GRADIENT BUTTON
    // ---------------------------------------------------------

    private fun button(
        c: Canvas,
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
            0xff25d9ff.toInt(),
            0xff8658ff.toInt(),
            Shader.TileMode.CLAMP
        )

        c.drawRoundRect(
            l,
            t,
            r,
            b,
            15f,
            15f,
            paint
        )

        paint.shader = null

        text(
            c,
            label,
            (l + r) / 2f,
            (t + b) / 2f + 5f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // HEADER
    // ---------------------------------------------------------

    private fun header(
        c: Canvas,
        title: String,
        subtitle: String
    ) {
        text(
            c,
            "‹",
            18f,
            38f,
            35f,
            Color.WHITE,
            false
        )

        text(
            c,
            title,
            50f,
            32f,
            20f,
            Color.WHITE,
            true
        )

        text(
            c,
            subtitle,
            50f,
            51f,
            9f,
            0xff8995bd.toInt()
        )
    }

    // ---------------------------------------------------------
    // NAVIGATION
    // ---------------------------------------------------------

    private fun navigation(
        c: Canvas,
        w: Float,
        h: Float,
        selected: Int
    ) {
        val top = h - 66f

        card(
            c,
            7f,
            top,
            w - 7f,
            h - 7f,
            17f
        )

        val icons = arrayOf(
            "⌂",
            "◆",
            "◎",
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

            val color =
                if (i == selected) {
                    0xff50ddff.toInt()
                } else {
                    0xff747fa5.toInt()
                }

            text(
                c,
                icons[i],
                x,
                top + 25f,
                16f,
                color,
                true,
                Paint.Align.CENTER
            )

            text(
                c,
                names[i],
                x,
                top + 43f,
                7f,
                color,
                false,
                Paint.Align.CENTER
            )
        }
    }

    // ---------------------------------------------------------
    // SPLASH
    // ---------------------------------------------------------

    private fun drawSplash(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            c,
            "✦",
            w / 2f,
            h * 0.40f,
            75f,
            0xff52ddff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "MindBlow",
            w / 2f,
            h * 0.50f,
            40f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "THINK  •  PLAY  •  REFRESH",
            w / 2f,
            h * 0.55f,
            11f,
            0xffaeb9dd.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // HOME
    // ---------------------------------------------------------

    private fun drawHome(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            c,
            "MindBlow",
            18f,
            34f,
            25f,
            Color.WHITE,
            true
        )

        text(
            c,
            "Mind Games",
            18f,
            52f,
            10f,
            0xff8995bd.toInt()
        )

        card(
            c,
            w - 94f,
            12f,
            w - 12f,
            47f,
            14f
        )

        text(
            c,
            "✦ $score",
            w - 53f,
            36f,
            12f,
            0xffffd76b.toInt(),
            true,
            Paint.Align.CENTER
        )

        card(
            c,
            15f,
            70f,
            w - 15f,
            155f,
            20f
        )

        text(
            c,
            "READY FOR A QUICK RESET?",
            30f,
            96f,
            10f,
            0xff57ddff.toInt(),
            true
        )

        text(
            c,
            "Pick a mind game",
            30f,
            122f,
            22f,
            Color.WHITE,
            true
        )

        text(
            c,
            "Short challenges • instant feedback",
            30f,
            142f,
            10f,
            0xff929dc0.toInt()
        )

        button(
            c,
            "PLAY NOW",
            w - 120f,
            91f,
            w - 30f,
            132f
        )

        text(
            c,
            "Mind Games",
            18f,
            190f,
            18f,
            Color.WHITE,
            true
        )

        smallGameCard(
            c,
            "◉",
            "Focus Orb",
            "Reaction",
            15f,
            205f,
            w / 2f - 7f,
            277f
        )

        smallGameCard(
            c,
            "◆",
            "Color Hunt",
            "Visual",
            w / 2f + 7f,
            205f,
            w - 15f,
            277f
        )

        smallGameCard(
            c,
            "✦",
            "Memory Match",
            "Memory",
            15f,
            286f,
            w / 2f - 7f,
            358f
        )

        smallGameCard(
            c,
            "123",
            "Number Flow",
            "Thinking",
            w / 2f + 7f,
            286f,
            w - 15f,
            358f
        )

        smallGameCard(
            c,
            "●",
            "Bubble Blast",
            "Reaction",
            15f,
            367f,
            w / 2f - 7f,
            439f
        )

        smallGameCard(
            c,
            "≈",
            "Calm Flow",
            "Relax",
            w / 2f + 7f,
            367f,
            w - 15f,
            439f
        )

        card(
            c,
            15f,
            451f,
            w - 15f,
            505f,
            16f
        )

        text(
            c,
            "🔥 $streak day streak",
            28f,
            475f,
            12f,
            0xffffb52f.toInt(),
            true
        )

        text(
            c,
            "Keep your brain moving.",
            28f,
            494f,
            9f,
            0xff8995bb.toInt()
        )

        navigation(c, w, h, 0)
    }

    // ---------------------------------------------------------
    // GAME CARD
    // ---------------------------------------------------------

    private fun smallGameCard(
        c: Canvas,
        icon: String,
        title: String,
        sub: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {
        card(c, l, t, r, b, 15f)

        text(
            c,
            icon,
            l + 19f,
            t + 27f,
            if (icon == "123") 10f else 17f,
            0xff5ce3ff.toInt(),
            true
        )

        text(
            c,
            title,
            l + 19f,
            t + 47f,
            11f,
            Color.WHITE,
            true
        )

        text(
            c,
            sub,
            l + 19f,
            t + 61f,
            8f,
            0xff8793b7.toInt()
        )
    }

    // ---------------------------------------------------------
    // GAMES
    // ---------------------------------------------------------

    private fun drawGames(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Mind Games",
            "Choose a quick challenge"
        )

        smallGameCard(
            c,
            "◉",
            "Focus Orb",
            "Reaction & focus",
            12f,
            68f,
            w / 2f - 6f,
            132f
        )

        smallGameCard(
            c,
            "◆",
            "Color Hunt",
            "Visual attention",
            w / 2f + 6f,
            68f,
            w - 12f,
            132f
        )

        smallGameCard(
            c,
            "✦",
            "Memory Match",
            "Memory training",
            12f,
            140f,
            w / 2f - 6f,
            204f
        )

        smallGameCard(
            c,
            "123",
            "Number Flow",
            "Thinking memory",
            w / 2f + 6f,
            140f,
            w - 12f,
            204f
        )

        smallGameCard(
            c,
            "●",
            "Bubble Blast",
            "Fast reaction",
            12f,
            212f,
            w / 2f - 6f,
            276f
        )

        smallGameCard(
            c,
            "≈",
            "Calm Flow",
            "Breathing reset",
            w / 2f + 6f,
            212f,
            w - 12f,
            276f
        )

        card(
            c,
            12f,
            286f,
            w - 12f,
            337f,
            15f
        )

        text(
            c,
            "TIP",
            24f,
            306f,
            8f,
            0xff55ddff.toInt(),
            true
        )

        text(
            c,
            "Try 2–3 minutes between study sessions.",
            24f,
            324f,
            9f,
            0xff909bbd.toInt()
        )

        navigation(c, w, h, 1)
    }

    // ---------------------------------------------------------
    // FOCUS ORB
    // ---------------------------------------------------------

    private fun startFocus() {
        focusHits = 0
        focusRoundStarted = true
        moveFocusOrb(320f, 400f)
    }

    private fun moveFocusOrb(
        w: Float,
        h: Float
    ) {
        focusX = Random.nextFloat() *
                max(1f, w - 100f) + 50f

        focusY = Random.nextFloat() *
                max(1f, h - 230f) + 120f

        focusRadius =
            25f + Random.nextFloat() * 10f
    }

    private fun drawFocus(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Focus Orb",
            "Tap the glowing orb"
        )

        text(
            c,
            "$focusHits / $focusGoal",
            w - 20f,
            38f,
            13f,
            0xff58dcff.toInt(),
            true,
            Paint.Align.RIGHT
        )

        paint.shader = RadialGradient(
            focusX,
            focusY,
            70f,
            intArrayOf(
                0xffffffff.toInt(),
                0xff55eaff.toInt(),
                0x0055eaff
            ),
            floatArrayOf(
                0f,
                0.3f,
                1f
            ),
            Shader.TileMode.CLAMP
        )

        c.drawCircle(
            focusX,
            focusY,
            70f + sin(animation * 3f) * 8f,
            paint
        )

        paint.shader = null
        paint.color = 0xff59e7ff.toInt()

        c.drawCircle(
            focusX,
            focusY,
            focusRadius + sin(animation * 4f) * 3f,
            paint
        )

        text(
            c,
            "TAP",
            focusX,
            focusY + 5f,
            10f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        card(
            c,
            20f,
            h - 130f,
            w - 20f,
            h - 80f,
            15f
        )

        text(
            c,
            "Follow the light",
            w / 2f,
            h - 108f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Stay calm and react quickly",
            w / 2f,
            h - 91f,
            8f,
            0xff8995b8.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // COLOR HUNT
    // ---------------------------------------------------------

    private fun startColorGame() {
        colorScore = 0
        colorRound = 0
        nextColorRound()
    }

    private fun nextColorRound() {
        colorTarget = Random.nextInt(gameColors.size)

        val list = (0 until gameColors.size).toMutableList()
        list.shuffle()

        for (i in 0 until 6) {
            colorOptions[i] = list[i]
        }

        colorRound++
        colorStartTime = System.currentTimeMillis()
    }

    private fun drawColorHunt(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Color Hunt",
            "Find the requested color"
        )

        text(
            c,
            "ROUND $colorRound",
            w - 18f,
            32f,
            9f,
            0xff8793b8.toInt(),
            true,
            Paint.Align.RIGHT
        )

        text(
            c,
            "Find",
            w / 2f,
            103f,
            14f,
            0xffa8b3d4.toInt(),
            false,
            Paint.Align.CENTER
        )

        paint.color = gameColors[colorTarget]

        c.drawCircle(
            w / 2f,
            143f,
            31f,
            paint
        )

        text(
            c,
            "Tap the matching color",
            w / 2f,
            199f,
            12f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        val startX = 48f
        val startY = 240f
        val gapX = (w - 96f) / 2f
        val gapY = 85f

        for (i in 0 until 6) {

            val col = i % 2
            val row = i / 2

            val x =
                startX + col * gapX

            val y =
                startY + row * gapY

            card(
                c,
                x - 34f,
                y - 30f,
                x + 34f,
                y + 30f,
                16f
            )

            paint.color = gameColors[colorOptions[i]]

            c.drawCircle(
                x,
                y,
                21f,
                paint
            )
        }

        text(
            c,
            "Score  $colorScore",
            w / 2f,
            h - 105f,
            14f,
            0xffffd76b.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // MEMORY MATCH
    // ---------------------------------------------------------

    private fun startMemory() {
        val pairList = mutableListOf<Int>()

        for (i in 0 until 6) {
            pairList.add(i)
            pairList.add(i)
        }

        pairList.shuffle()

        memoryCards = pairList
        memoryOpenA = -1
        memoryOpenB = -1
        memoryMatched = BooleanArray(12)
        memoryLocked = false
        memoryMoves = 0
        memoryPairs = 0
    }

    private fun drawMemory(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Memory Match",
            "Find matching pairs"
        )

        text(
            c,
            "$memoryPairs / 6",
            w - 18f,
            35f,
            12f,
            0xff59ddff.toInt(),
            true,
            Paint.Align.RIGHT
        )

        val boardSize = min(
            w - 36f,
            330f
        )

        val left = (w - boardSize) / 2f
        val top = 78f
        val cell = boardSize / 3f

        for (i in 0 until 12) {

            val row = i / 3
            val col = i % 3

            val l = left + col * cell + 5f
            val t = top + row * cell + 5f
            val r = left + (col + 1) * cell - 5f
            val b = top + (row + 1) * cell - 5f

            val open =
                i == memoryOpenA ||
                i == memoryOpenB ||
                memoryMatched[i]

            if (open) {

                card(
                    c,
                    l,
                    t,
                    r,
                    b,
                    15f
                )

                val symbol =
                    memorySymbols[
                        memoryCards.getOrElse(i) { 0 }
                    ]

                text(
                    c,
                    symbol,
                    (l + r) / 2f,
                    (t + b) / 2f + 10f,
                    27f,
                    0xff5ce5ff.toInt(),
                    true,
                    Paint.Align.CENTER
                )

            } else {

                paint.color = 0xff111c48.toInt()

                c.drawRoundRect(
                    l,
                    t,
                    r,
                    b,
                    15f,
                    15f,
                    paint
                )

                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = 0xff26366d.toInt()

                c.drawRoundRect(
                    l,
                    t,
                    r,
                    b,
                    15f,
                    15f,
                    paint
                )

                paint.style = Paint.Style.FILL

                text(
                    c,
                    "?",
                    (l + r) / 2f,
                    (t + b) / 2f + 9f,
                    22f,
                    0xff7180ae.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            }
        }

        text(
            c,
            "Moves  $memoryMoves",
            w / 2f,
            top + boardSize + 34f,
            12f,
            0xff98a3c3.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // NUMBER FLOW
    // ---------------------------------------------------------

    private fun startNumberFlow() {
        numberPositions.clear()

        for (i in 0 until 12) {

            var x: Float
            var y: Float

            var safe = false
            var attempts = 0

            do {
                x = 40f + Random.nextFloat() * 240f
                y = 110f + Random.nextFloat() * 300f

                safe = true

                for (p in numberPositions) {
                    val dx = p.first - x
                    val dy = p.second - y

                    if (dx * dx + dy * dy < 55f * 55f) {
                        safe = false
                        break
                    }
                }

                attempts++
            } while (!safe && attempts < 100)

            numberPositions.add(Pair(x, y))
        }

        nextNumber = 1
        numberStart = System.currentTimeMillis()
        numberBestTime = 0L
    }

    private fun drawNumberFlow(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Number Flow",
            "Tap numbers in order"
        )

        text(
            c,
            "NEXT  $nextNumber",
            w - 18f,
            34f,
            12f,
            0xff5ce4ff.toInt(),
            true,
            Paint.Align.RIGHT
        )

        for (i in 0 until 12) {

            val p = numberPositions.getOrElse(
                i
            ) {
                Pair(
                    w / 2f,
                    200f
                )
            }

            val active =
                i + 1 == nextNumber

            paint.color =
                if (active) {
                    0xff4de5ff.toInt()
                } else {
                    0xff17234e.toInt()
                }

            c.drawCircle(
                p.first,
                p.second,
                26f,
                paint
            )

            if (active) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = 0xff9cf3ff.toInt()

                c.drawCircle(
                    p.first,
                    p.second,
                    31f + sin(animation * 3f) * 3f,
                    paint
                )

                paint.style = Paint.Style.FILL
            }

            text(
                c,
                (i + 1).toString(),
                p.first,
                p.second + 6f,
                13f,
                if (active) Color.BLACK else Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }

        val elapsed =
            (System.currentTimeMillis() - numberStart) / 1000f

        text(
            c,
            String.format(
                java.util.Locale.US,
                "%.1fs",
                elapsed
            ),
            w / 2f,
            h - 105f,
            15f,
            0xffffd76b.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // BUBBLE BLAST
    // ---------------------------------------------------------

    private fun startBubbleGame(
        w: Float,
        h: Float
    ) {
        bubbles.clear()

        bubbleScore = 0
        bubbleTime = System.currentTimeMillis()
        bubbleStarted = true

        for (i in 0 until 18) {
            bubbles.add(
                Bubble(
                    x = 25f + Random.nextFloat() * max(1f, w - 50f),
                    y = 100f + Random.nextFloat() * max(1f, h - 230f),
                    radius = 14f + Random.nextFloat() * 16f,
                    vx = -0.7f + Random.nextFloat() * 1.4f,
                    vy = -0.7f + Random.nextFloat() * 1.4f,
                    type = Random.nextInt(4)
                )
            )
        }
    }

    private fun drawBubbleBlast(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Bubble Blast",
            "Pop as many as you can"
        )

        val secondsLeft =
            max(
                0,
                30 - (
                    (System.currentTimeMillis() - bubbleTime) / 1000
                ).toInt()
            )

        text(
            c,
            "$bubbleScore",
            20f,
            85f,
            17f,
            0xffffd76b.toInt(),
            true
        )

        text(
            c,
            "${secondsLeft}s",
            w - 20f,
            85f,
            17f,
            0xff5ce5ff.toInt(),
            true,
            Paint.Align.RIGHT
        )

        val colors = intArrayOf(
            0xff48e3ff.toInt(),
            0xffff5f9b.toInt(),
            0xff9c68ff.toInt(),
            0xffffd34d.toInt()
        )

        for (b in bubbles) {

            paint.shader = RadialGradient(
                b.x - b.radius * 0.3f,
                b.y - b.radius * 0.3f,
                b.radius,
                intArrayOf(
                    Color.WHITE,
                    colors[b.type],
                    colors[b.type]
                ),
                floatArrayOf(
                    0f,
                    0.2f,
                    1f
                ),
                Shader.TileMode.CLAMP
            )

            c.drawCircle(
                b.x,
                b.y,
                b.radius,
                paint
            )

            paint.shader = null
        }

        text(
            c,
            "POP • POP • POP",
            w / 2f,
            h - 105f,
            13f,
            0xffaab5d5.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // CALM FLOW
    // ---------------------------------------------------------

    private fun startCalm() {
        calmStart = System.currentTimeMillis()
        calmPhase = 0
        calmCycles = 0
        calmLastPhase = -1
    }

    private fun drawCalmFlow(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Calm Flow",
            "Follow the breathing circle"
        )

        val elapsed =
            System.currentTimeMillis() - calmStart

        val cycle =
            (elapsed / 12000L).toInt()

        val within =
            elapsed % 12000L

        val phase =
            when {
                within < 4000L -> 0
                within < 8000L -> 1
                else -> 2
            }

        if (phase != calmLastPhase) {
            calmLastPhase = phase
        }

        calmCycles = cycle

        val progress =
            when (phase) {
                0 -> within / 4000f
                1 -> 1f - (within - 4000L) / 4000f
                else -> 0f
            }

        val radius =
            65f + progress * 85f

        val cx = w / 2f
        val cy = 260f

        paint.shader = RadialGradient(
            cx,
            cy,
            radius + 35f,
            intArrayOf(
                0xff73e9ff.toInt(),
                0xff6c66ff.toInt(),
                0x006c66ff
            ),
            floatArrayOf(
                0f,
                0.5f,
                1f
            ),
            Shader.TileMode.CLAMP
        )

        c.drawCircle(
            cx,
            cy,
            radius + 35f,
            paint
        )

        paint.shader = null
        paint.color = 0xff58ddff.toInt()

        c.drawCircle(
            cx,
            cy,
            radius,
            paint
        )

        val phaseText =
            when (phase) {
                0 -> "BREATHE IN"
                1 -> "BREATHE OUT"
                else -> "REST"
            }

        text(
            c,
            phaseText,
            cx,
            cy + 6f,
            15f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Follow the rhythm",
            cx,
            400f,
            19f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Slow breathing can give your attention a short reset.",
            cx,
            426f,
            9f,
            0xff919cbe.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            c,
            "Cycles  $calmCycles",
            cx,
            h - 105f,
            13f,
            0xff5ce5ff.toInt(),
            true,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // DAILY
    // ---------------------------------------------------------

    private fun drawDaily(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Daily",
            "One quick challenge"
        )

        card(
            c,
            16f,
            80f,
            w - 16f,
            245f,
            20f
        )

        text(
            c,
            "TODAY'S RESET",
            30f,
            112f,
            10f,
            0xff5ce5ff.toInt(),
            true
        )

        text(
            c,
            "Play Bubble Blast",
            30f,
            150f,
            22f,
            Color.WHITE,
            true
        )

        text(
            c,
            "Pop bubbles for 30 seconds.",
            30f,
            176f,
            10f,
            0xff929dc0.toInt()
        )

        button(
            c,
            "START",
            30f,
            194f,
            w - 30f,
            231f
        )

        text(
            c,
            "🔥 $streak day streak",
            18f,
            287f,
            15f,
            0xffffb52f.toInt(),
            true
        )

        card(
            c,
            18f,
            310f,
            w - 18f,
            375f,
            16f
        )

        text(
            c,
            "XP earned",
            30f,
            338f,
            9f,
            0xff8d98ba.toInt()
        )

        text(
            c,
            "$score XP",
            30f,
            361f,
            17f,
            Color.WHITE,
            true
        )

        navigation(c, w, h, 3)
    }

    // ---------------------------------------------------------
    // PROFILE
    // ---------------------------------------------------------

    private fun drawProfile(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Profile",
            "Your progress"
        )

        card(
            c,
            16f,
            78f,
            w - 16f,
            190f,
            20f
        )

        text(
            c,
            "✦",
            52f,
            135f,
            42f,
            0xff5ce4ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Mind Explorer",
            85f,
            117f,
            18f,
            Color.WHITE,
            true
        )

        text(
            c,
            "Level ${score / 500 + 1}",
            85f,
            142f,
            10f,
            0xff929dc0.toInt()
        )

        text(
            c,
            "$score XP",
            85f,
            166f,
            12f,
            0xffffd76b.toInt(),
            true
        )

        card(
            c,
            16f,
            207f,
            w - 16f,
            275f,
            17f
        )

        text(
            c,
            "Games completed",
            30f,
            234f,
            10f,
            0xff8d99ba.toInt()
        )

        text(
            c,
            "${score / 25}",
            30f,
            260f,
            19f,
            Color.WHITE,
            true
        )

        text(
            c,
            "🔥 Streak",
            w / 2f + 10f,
            234f,
            10f,
            0xff8d99ba.toInt()
        )

        text(
            c,
            "$streak",
            w / 2f + 10f,
            260f,
            19f,
            0xffffb52f.toInt(),
            true
        )

        card(
            c,
            16f,
            294f,
            w - 16f,
            400f,
            17f
        )

        text(
            c,
            "⚙  Settings",
            31f,
            325f,
            12f,
            Color.WHITE
        )

        text(
            c,
            "♫  Sound & Music",
            31f,
            360f,
            12f,
            Color.WHITE
        )

        text(
            c,
            "☾  Dark Theme",
            31f,
            395f,
            12f,
            Color.WHITE
        )

        navigation(c, w, h, 4)
    }

    // ---------------------------------------------------------
    // TOAST
    // ---------------------------------------------------------

    private fun drawToast(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        card(
            c,
            25f,
            h - 130f,
            w - 25f,
            h - 82f,
            16f
        )

        text(
            c,
            message,
            w / 2f,
            h - 101f,
            11f,
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
            .putBoolean("started", true)
            .putInt("score", score)
            .putInt("streak", streak)
            .apply()
    }

    // ---------------------------------------------------------
    // GAME UPDATES
    // ---------------------------------------------------------

    private fun updateGames(
        w: Float,
        h: Float
    ) {
        if (screen == Screen.BUBBLE && bubbleStarted) {

            for (b in bubbles) {

                b.x += b.vx
                b.y += b.vy

                if (b.x < b.radius || b.x > w - b.radius) {
                    b.vx *= -1f
                }

                if (b.y < 105f || b.y > h - 90f) {
                    b.vy *= -1f
                }
            }

            val elapsed =
                System.currentTimeMillis() - bubbleTime

            if (elapsed >= 30000L) {
                bubbleStarted = false

                score += bubbleScore
                save()

                showMessage(
                    "Time! +$bubbleScore XP"
                )
            }
        }
    }

    // ---------------------------------------------------------
    // TOUCH
    // ---------------------------------------------------------

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        if (event.action != MotionEvent.ACTION_UP) {
            return true
        }

        val x = event.x / density
        val y = event.y / density

        val w = width / density
        val h = height / density

        when (screen) {

            Screen.SPLASH -> {
                screen = Screen.HOME
            }

            Screen.HOME -> {
                homeTouch(x, y, w, h)
            }

            Screen.GAMES -> {
                gamesTouch(x, y, w, h)
            }

            Screen.FOCUS -> {
                focusTouch(x, y, w, h)
            }

            Screen.COLOR -> {
                colorTouch(x, y, w, h)
            }

            Screen.MEMORY -> {
                memoryTouch(x, y, w, h)
            }

            Screen.NUMBER -> {
                numberTouch(x, y, w, h)
            }

            Screen.BUBBLE -> {
                bubbleTouch(x, y, w, h)
            }

            Screen.CALM -> {
                calmTouch(x, y, w, h)
            }

            Screen.DAILY -> {
                dailyTouch(x, y, w, h)
            }

            Screen.PROFILE -> {
                profileTouch(x, y, w, h)
            }
        }

        invalidate()
        return true
    }

    // ---------------------------------------------------------
    // HOME TOUCH
    // ---------------------------------------------------------

    private fun homeTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (y > h - 75f) {

            when {
                x < w * 0.2f -> screen = Screen.HOME

                x < w * 0.4f -> {
                    screen = Screen.GAMES
                }

                x < w * 0.6f -> {
                    startFocus()
                    screen = Screen.FOCUS
                }

                x < w * 0.8f -> {
                    screen = Screen.DAILY
                }

                else -> {
                    screen = Screen.PROFILE
                }
            }

            return
        }

        if (y in 205f..277f) {

            if (x < w / 2f) {
                startFocus()
                screen = Screen.FOCUS
            } else {
                startColorGame()
                screen = Screen.COLOR
            }

            return
        }

        if (y in 286f..358f) {

            if (x < w / 2f) {
                startMemory()
                screen = Screen.MEMORY
            } else {
                startNumberFlow()
                screen = Screen.NUMBER
            }

            return
        }

        if (y in 367f..439f) {

            if (x < w / 2f) {
                startBubbleGame(w, h)
                screen = Screen.BUBBLE
            } else {
                startCalm()
                screen = Screen.CALM
            }

            return
        }

        if (y in 70f..155f) {
            startBubbleGame(w, h)
            screen = Screen.BUBBLE
        }
    }

    // ---------------------------------------------------------
    // GAMES TOUCH
    // ---------------------------------------------------------

    private fun gamesTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (y < 60f) {
            screen = Screen.HOME
            return
        }

        if (y > h - 75f) {

            when {
                x < w * 0.2f -> screen = Screen.HOME
                x < w * 0.4f -> screen = Screen.GAMES

                x < w * 0.6f -> {
                    startFocus()
                    screen = Screen.FOCUS
                }

                x < w * 0.8f -> screen = Screen.DAILY
                else -> screen = Screen.PROFILE
            }

            return
        }

        when {
            y in 68f..132f -> {
                if (x < w / 2f) {
                    startFocus()
                    screen = Screen.FOCUS
                } else {
                    startColorGame()
                    screen = Screen.COLOR
                }
            }

            y in 140f..204f -> {
                if (x < w / 2f) {
                    startMemory()
                    screen = Screen.MEMORY
                } else {
                    startNumberFlow()
                    screen = Screen.NUMBER
                }
            }

            y in 212f..276f -> {
                if (x < w / 2f) {
                    startBubbleGame(w, h)
                    screen = Screen.BUBBLE
                } else {
                    startCalm()
                    screen = Screen.CALM
                }
            }

            y in 286f..350f -> {
                screen = Screen.DAILY
            }
        }
    }

    // ---------------------------------------------------------
    // FOCUS TOUCH
    // ---------------------------------------------------------

    private fun focusTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (y < 65f) {
            screen = Screen.GAMES
            return
        }

        val dx = x - focusX
        val dy = y - focusY

        if (
            dx * dx + dy * dy <=
            (focusRadius + 20f) *
            (focusRadius + 20f)
        ) {

            focusHits++

            score += 5
            save()

            if (focusHits >= focusGoal) {

                score += 20
                save()

                showMessage(
                    "Focus complete! +20 XP"
                )

                focusHits = 0
            } else {

                showMessage(
                    "+5 XP"
                )
            }

            moveFocusOrb(w, h)
        }
    }

    // ---------------------------------------------------------
    // COLOR TOUCH
    // ---------------------------------------------------------

    private fun colorTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (y < 65f) {
            screen = Screen.GAMES
            return
        }

        val startX = 48f
        val startY = 240f
        val gapX = (w - 96f) / 2f
        val gapY = 85f

        for (i in 0 until 6) {

            val col = i % 2
            val row = i / 2

            val cx =
                startX + col * gapX

            val cy =
                startY + row * gapY

            val dx = x - cx
            val dy = y - cy

            if (
                dx * dx + dy * dy <= 34f * 34f
            ) {

                if (colorOptions[i] == colorTarget) {

                    val reaction =
                        System.currentTimeMillis() -
                                colorStartTime

                    val bonus =
                        if (reaction < 900L) 15 else 10

                    colorScore += bonus
                    score += bonus
                    save()

                    showMessage(
                        "Correct! +$bonus XP"
                    )

                    nextColorRound()

                } else {

                    showMessage(
                        "Try again"
                    )
                }

                return
            }
        }
    }

    // ---------------------------------------------------------
    // MEMORY TOUCH
    // ---------------------------------------------------------

    private fun memoryTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (y < 60f) {
            screen = Screen.GAMES
            return
        }

        if (
            memoryLocked ||
            memoryPairs >= 6
        ) {
            return
        }

        val boardSize = min(
            w - 36f,
            330f
        )

        val left = (w - boardSize) / 2f
        val top = 78f
        val cell = boardSize / 3f

        if (
            x < left ||
            x > left + boardSize ||
            y < top ||
            y > top + boardSize
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

        if (index !in 0 until 12) {
            return
        }

        if (memoryMatched[index]) {
            return
        }

        if (index == memoryOpenA) {
            return
        }

        if (memoryOpenA == -1) {

            memoryOpenA = index

        } else if (memoryOpenB == -1) {

            memoryOpenB = index
            memoryMoves++

            val a =
                memoryCards[memoryOpenA]

            val b =
                memoryCards[memoryOpenB]

            if (a == b) {

                memoryMatched[memoryOpenA] = true
                memoryMatched[memoryOpenB] = true

                memoryPairs++

                score += 15
                save()

                showMessage(
                    "Match! +15 XP"
                )

                memoryOpenA = -1
                memoryOpenB = -1

                if (memoryPairs >= 6) {

                    score += 30
                    save()

                    showMessage(
                        "Memory complete! +30 XP"
                    )
                }

            } else {

                memoryLocked = true

                postDelayed({

                    memoryOpenA = -1
                    memoryOpenB = -1
                    memoryLocked = false

                    invalidate()

                }, 650L)
            }
        }
    }

    // ---------------------------------------------------------
    // NUMBER TOUCH
    // ---------------------------------------------------------

    private fun numberTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (y < 60f) {
            screen = Screen.GAMES
            return
        }

        if (
            nextNumber < 1 ||
            nextNumber > 12
        ) {
            return
        }

        val p =
            numberPositions.getOrNull(
                nextNumber - 1
            ) ?: return

        val dx = x - p.first
        val dy = y - p.second

        if (
            dx * dx + dy * dy <=
            32f * 32f
        ) {

            nextNumber++

            if (nextNumber > 12) {

                numberBestTime =
                    System.currentTimeMillis() -
                            numberStart

                val seconds =
                    numberBestTime / 1000L

                val bonus =
                    max(
                        10L,
                        40L - seconds
                    ).toInt()

                score += bonus
                save()

                showMessage(
                    "Complete! +$bonus XP"
                )

                postDelayed({

                    if (screen == Screen.NUMBER) {
                        startNumberFlow()
                    }

                }, 900L)

            } else {

                score += 2
                save()
            }
        }
    }

    // ---------------------------------------------------------
    // BUBBLE TOUCH
    // ---------------------------------------------------------

    private fun bubbleTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (y < 60f) {
            screen = Screen.GAMES
            bubbleStarted = false
            return
        }

        if (!bubbleStarted) {
            startBubbleGame(w, h)
            return
        }

        for (i in bubbles.indices.reversed()) {

            val b = bubbles[i]

            val dx = x - b.x
            val dy = y - b.y

            if (
                dx * dx + dy * dy <=
                b.radius * b.radius
            ) {

                bubbleScore += 5

                b.x =
                    25f +
                    Random.nextFloat() *
                    max(1f, w - 50f)

                b.y =
                    110f +
                    Random.nextFloat() *
                    max(1f, h - 220f)

                b.radius =
                    13f +
                    Random.nextFloat() * 16f

                b.vx =
                    -1f +
                    Random.nextFloat() * 2f

                b.vy =
                    -1f +
                    Random.nextFloat() * 2f

                showMessage(
                    "+5"
                )

                return
            }
        }
    }

    // ---------------------------------------------------------
    // CALM TOUCH
    // ---------------------------------------------------------

    private fun calmTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (y < 60f) {
            screen = Screen.GAMES
            return
        }

        if (
            y in 170f..350f
        ) {
            showMessage(
                "Follow the breathing circle"
            )
        }
    }

    // ---------------------------------------------------------
    // DAILY TOUCH
    // ---------------------------------------------------------

    private fun dailyTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (y < 60f) {
            screen = Screen.HOME
            return
        }

        if (y in 185f..245f) {
            startBubbleGame(w, h)
            screen = Screen.BUBBLE
        }
    }

    // ---------------------------------------------------------
    // PROFILE TOUCH
    // ---------------------------------------------------------

    private fun profileTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {
        if (y < 60f) {
            screen = Screen.HOME
        }
    }

    // ---------------------------------------------------------
    // BACK
    // ---------------------------------------------------------

    fun goBack(): Boolean {

        return when (screen) {

            Screen.SPLASH -> false

            Screen.HOME -> false

            Screen.GAMES -> {
                screen = Screen.HOME
                invalidate()
                true
            }

            Screen.FOCUS,
            Screen.COLOR,
            Screen.MEMORY,
            Screen.NUMBER,
            Screen.BUBBLE,
            Screen.CALM -> {

                bubbleStarted = false
                screen = Screen.GAMES
                invalidate()
                true
            }

            Screen.DAILY -> {
                screen = Screen.HOME
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
