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

        window.statusBarColor = Color.rgb(5, 7, 22)
        window.navigationBarColor = Color.rgb(5, 7, 22)

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
    MEMORY,
    REACTION,
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

    private var startedAt = System.currentTimeMillis()
    private var animation = 0f

    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)

    // MEMORY
    private val memorySymbols = arrayOf(
        "★", "◆", "●", "✦", "☀", "☾"
    )

    private var memoryCards = mutableListOf<String>()
    private var memoryOpened = mutableListOf<Int>()
    private var memoryMatched = mutableSetOf<Int>()
    private var memoryPreview = false
    private var memoryPreviewUntil = 0L

    // REACTION
    private var reactionX = 0f
    private var reactionY = 0f
    private var reactionRadius = 38f
    private var reactionRunning = false
    private var reactionHits = 0
    private var reactionEnd = 0L
    private var reactionMoveAt = 0L

    // FOCUS
    private var focusX = 0f
    private var focusY = 0f
    private var focusHits = 0
    private var focusMoveAt = 0L

    // RELAX
    private var relaxProgress = 0f
    private var relaxRunning = false
    private var relaxStartedAt = 0L

    // MESSAGE
    private var message = ""
    private var messageUntil = 0L

    init {
        isFocusable = true
        postInvalidateDelayed(16L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        animation += 0.018f

        drawBackground(canvas, w, h)

        when (screen) {
            Screen.SPLASH -> drawSplash(canvas, w, h)
            Screen.WELCOME -> drawWelcome(canvas, w, h)
            Screen.HOME -> drawHome(canvas, w, h)
            Screen.GAMES -> drawGames(canvas, w, h)
            Screen.MEMORY -> drawMemory(canvas, w, h)
            Screen.REACTION -> drawReaction(canvas, w, h)
            Screen.FOCUS -> drawFocus(canvas, w, h)
            Screen.RELAX -> drawRelax(canvas, w, h)
            Screen.DAILY -> drawDaily(canvas, w, h)
            Screen.PROFILE -> drawProfile(canvas, w, h)
        }

        if (messageUntil > System.currentTimeMillis()) {
            drawToast(canvas, w, h)
        }

        if (
            screen == Screen.SPLASH &&
            System.currentTimeMillis() - startedAt > 1400L
        ) {
            screen = Screen.WELCOME
        }

        updateGames()

        postInvalidateDelayed(16L)
    }

    // =========================================================
    // BACKGROUND
    // =========================================================

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
            Color.rgb(28, 8, 55),
            Shader.TileMode.CLAMP
        )

        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val t = animation

        // Moving glow
        paint.color = Color.argb(35, 0, 220, 255)
        c.drawCircle(
            w * 0.15f + sin(t) * 45f,
            h * 0.18f + cos(t) * 30f,
            150f,
            paint
        )

        paint.color = Color.argb(32, 170, 60, 255)
        c.drawCircle(
            w * 0.85f + cos(t * 0.8f) * 45f,
            h * 0.35f + sin(t) * 35f,
            170f,
            paint
        )

        paint.color = Color.argb(25, 20, 255, 170)
        c.drawCircle(
            w * 0.50f + sin(t * 0.7f) * 50f,
            h * 0.82f,
            180f,
            paint
        )

        // Neon rings
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.argb(20, 100, 220, 255)

        for (i in 0 until 7) {
            c.drawCircle(
                w / 2f,
                h * 0.42f,
                80f + i * 48f + sin(t + i) * 6f,
                paint
            )
        }

        paint.style = Paint.Style.FILL

        // Small particles
        for (i in 0 until 18) {
            val px = ((i * 83f + sin(t * 0.7f + i) * 30f) % w)
            val py = ((i * 137f + cos(t * 0.5f + i) * 35f) % h)

            paint.color = Color.argb(
                70,
                100,
                220,
                255
            )

            c.drawCircle(
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

    // =========================================================
    // CARD
    // =========================================================

    private fun card(
        c: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        radius: Float = 22f
    ) {
        paint.shader = null
        paint.color = Color.argb(
            225,
            12,
            18,
            43
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

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.color = Color.argb(
            60,
            90,
            210,
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

        paint.style = Paint.Style.FILL
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
        paint.shader = LinearGradient(
            l,
            t,
            r,
            b,
            Color.rgb(15, 210, 255),
            Color.rgb(135, 65, 255),
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

        text(
            c,
            label,
            (l + r) / 2f,
            (t + b) / 2f + 5f,
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
        c: Canvas,
        title: String,
        subtitle: String
    ) {
        text(
            c,
            "‹",
            20f,
            51f,
            40f
        )

        text(
            c,
            title,
            58f,
            42f,
            23f,
            Color.WHITE,
            true
        )

        text(
            c,
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
        c: Canvas,
        w: Float,
        h: Float,
        selected: Int
    ) {
        val top = h - 82f

        card(
            c,
            10f,
            top,
            w - 10f,
            h - 8f,
            24f
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
                    0xff59e7ff.toInt()
                } else {
                    0xff737fa5.toInt()
                }

            text(
                c,
                icons[i],
                x,
                top + 29f,
                20f,
                color,
                true,
                Paint.Align.CENTER
            )

            text(
                c,
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
        c: Canvas,
        w: Float,
        h: Float
    ) {
        text(
            c,
            "✦",
            w / 2f,
            h * .40f,
            80f,
            0xff5de7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "MindBlow",
            w / 2f,
            h * .50f,
            42f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "RESET • FOCUS • REFRESH",
            w / 2f,
            h * .55f,
            12f,
            0xffaeb9dc.toInt(),
            false,
            Paint.Align.CENTER
        )
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
            "MindBlow",
            w / 2f,
            72f,
            31f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Your mini mental reset.",
            w / 2f,
            101f,
            14f,
            0xffaeb9d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            c,
            "✦",
            w / 2f,
            h * .43f,
            100f,
            0xff60e5ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Play. Focus. Breathe.",
            w / 2f,
            h * .60f,
            25f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Short interactive games",
            w / 2f,
            h * .65f,
            14f,
            0xffaeb9d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        text(
            c,
            "made for a quick mental refresh.",
            w / 2f,
            h * .685f,
            14f,
            0xffaeb9d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            c,
            "START MIND RESET",
            w * .12f,
            h * .77f,
            w * .88f,
            h * .85f
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
        text(
            c,
            "MindBlow",
            20f,
            40f,
            28f,
            Color.WHITE,
            true
        )

        text(
            c,
            "Your brain's little playground",
            20f,
            62f,
            11f,
            0xff9ca8cc.toInt()
        )

        card(
            c,
            w - 112f,
            18f,
            w - 18f,
            58f,
            17f
        )

        text(
            c,
            "✦ $score",
            w - 65f,
            43f,
            14f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )

        card(
            c,
            18f,
            82f,
            w - 18f,
            195f,
            24f
        )

        text(
            c,
            "READY FOR A RESET?",
            34f,
            112f,
            11f,
            0xff55e7ff.toInt(),
            true
        )

        text(
            c,
            "Refresh your mind.",
            34f,
            145f,
            23f,
            Color.WHITE,
            true
        )

        text(
            c,
            "Choose a quick challenge below.",
            34f,
            168f,
            12f,
            0xffaab6d7.toInt()
        )

        button(
            c,
            "DAILY CHALLENGE  ›",
            34f,
            178f,
            w - 34f,
            217f
        )

        text(
            c,
            "Mind Refresh Games",
            20f,
            250f,
            18f,
            Color.WHITE,
            true
        )

        gameCard(
            c,
            "🧠",
            "Mind Match",
            "Train memory",
            18f,
            265f,
            w / 2f - 8f,
            360f
        )

        gameCard(
            c,
            "⚡",
            "Quick Tap",
            "Test reaction",
            w / 2f + 8f,
            265f,
            w - 18f,
            360f
        )

        gameCard(
            c,
            "🎯",
            "Focus Dot",
            "Train attention",
            18f,
            370f,
            w / 2f - 8f,
            465f
        )

        gameCard(
            c,
            "🌊",
            "Breathe",
            "Relax your mind",
            w / 2f + 8f,
            370f,
            w - 18f,
            465f
        )

        navigation(
            c,
            w,
            h,
            0
        )
    }

    // =========================================================
    // GAME CARD
    // =========================================================

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
        card(c, l, t, r, b, 20f)

        text(
            c,
            icon,
            l + 36f,
            t + 40f,
            24f,
            0xff62e7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            title,
            l + 18f,
            t + 68f,
            15f,
            Color.WHITE,
            true
        )

        text(
            c,
            subtitle,
            l + 18f,
            t + 87f,
            10f,
            0xff9ba7cb.toInt()
        )
    }

    // =========================================================
    // GAMES
    // =========================================================

    private fun drawGames(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Mind Games",
            "Choose your mental reset"
        )

        gameCard(
            c,
            "🧠",
            "Mind Match",
            "Remember symbols",
            18f,
            95f,
            w / 2f - 8f,
            205f
        )

        gameCard(
            c,
            "⚡",
            "Quick Tap",
            "Reaction challenge",
            w / 2f + 8f,
            95f,
            w - 18f,
            205f
        )

        gameCard(
            c,
            "🎯",
            "Focus Dot",
            "Follow the target",
            18f,
            218f,
            w / 2f - 8f,
            328f
        )

        gameCard(
            c,
            "🌊",
            "Breathe",
            "Guided breathing",
            w / 2f + 8f,
            218f,
            w - 18f,
            328f
        )

        card(
            c,
            18f,
            345f,
            w - 18f,
            430f,
            21f
        )

        text(
            c,
            "⚡ QUICK RESET",
            34f,
            377f,
            12f,
            0xff5de7ff.toInt(),
            true
        )

        text(
            c,
            "Only a few minutes",
            34f,
            403f,
            18f,
            Color.WHITE,
            true
        )

        text(
            c,
            "Play • refresh • continue",
            34f,
            423f,
            11f,
            0xff9faacd.toInt()
        )

        navigation(
            c,
            w,
            h,
            1
        )
    }

    // =========================================================
    // MEMORY GAME
    // =========================================================

    private fun drawMemory(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Mind Match",
            "Remember the symbols"
        )

        text(
            c,
            "MATCHED ${memoryMatched.size / 2}/6",
            w / 2f,
            92f,
            12f,
            0xff5de7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        val size = min(
            w * .82f,
            330f
        )

        val left = (w - size) / 2f
        val top = 115f
        val cell = size / 3f

        for (i in 0 until 12) {

            val row = i / 4
            val col = i % 4

            val l = left + col * cell
            val tt = top + row * cell
            val r = l + cell - 7f
            val b = tt + cell - 7f

            val open =
                memoryPreview ||
                memoryOpened.contains(i) ||
                memoryMatched.contains(i)

            card(
                c,
                l + 3f,
                tt + 3f,
                r,
                b,
                15f
            )

            if (open) {
                text(
                    c,
                    memoryCards.getOrElse(i) { "?" },
                    (l + r) / 2f,
                    (tt + b) / 2f + 11f,
                    27f,
                    0xff62e7ff.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            } else {
                text(
                    c,
                    "?",
                    (l + r) / 2f,
                    (tt + b) / 2f + 10f,
                    25f,
                    0xff59678e.toInt(),
                    true,
                    Paint.Align.CENTER
                )
            }
        }

        text(
            c,
            if (memoryPreview)
                "Memorize the board..."
            else
                "Find matching pairs",
            w / 2f,
            top + size + 32f,
            14f,
            0xffabb7d8.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    private fun startMemory() {
        memoryCards = mutableListOf()

        for (symbol in memorySymbols) {
            memoryCards.add(symbol)
            memoryCards.add(symbol)
        }

        memoryCards.shuffle()

        memoryOpened.clear()
        memoryMatched.clear()

        memoryPreview = true
        memoryPreviewUntil =
            System.currentTimeMillis() + 2200L

        screen = Screen.MEMORY
    }

    // =========================================================
    // REACTION GAME
    // =========================================================

    private fun drawReaction(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Quick Tap",
            "Hit the glow as fast as possible"
        )

        if (!reactionRunning) {

            text(
                c,
                "REACTION TEST",
                w / 2f,
                145f,
                13f,
                0xff5de7ff.toInt(),
                true,
                Paint.Align.CENTER
            )

            text(
                c,
                "Tap the glowing orb",
                w / 2f,
                190f,
                23f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )

            text(
                c,
                "Get as many hits as possible.",
                w / 2f,
                220f,
                13f,
                0xffaab6d8.toInt(),
                false,
                Paint.Align.CENTER
            )

            button(
                c,
                "START",
                w * .20f,
                270f,
                w * .80f,
                325f
            )

        } else {

            val remaining =
                maxOf(
                    0L,
                    reactionEnd - System.currentTimeMillis()
                )

            text(
                c,
                "HITS  $reactionHits",
                25f,
                100f,
                16f,
                Color.WHITE,
                true
            )

            text(
                c,
                "${remaining / 1000 + 1}s",
                w - 25f,
                100f,
                16f,
                0xffffd66b.toInt(),
                true,
                Paint.Align.RIGHT
            )

            paint.color = 0xff52e7ff.toInt()

            c.drawCircle(
                reactionX,
                reactionY,
                reactionRadius + sin(animation * 5f) * 5f,
                paint
            )

            paint.color = Color.argb(
                70,
                255,
                255,
                255
            )

            c.drawCircle(
                reactionX - 10f,
                reactionY - 10f,
                10f,
                paint
            )

            text(
                c,
                "TAP!",
                reactionX,
                reactionY + 6f,
                13f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }
    }

    private fun startReaction() {
        reactionHits = 0
        reactionRunning = true
        reactionEnd =
            System.currentTimeMillis() + 15000L

        moveReactionTarget()
    }

    private fun moveReactionTarget() {
        val w = width.toFloat()
        val h = height.toFloat()

        reactionX = Random.nextFloat() *
                (w - 100f) + 50f

        reactionY = Random.nextFloat() *
                (h - 250f) + 150f

        reactionMoveAt =
            System.currentTimeMillis() + 900L
    }

    // =========================================================
    // FOCUS GAME
    // =========================================================

    private fun drawFocus(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Focus Dot",
            "Follow the moving target"
        )

        text(
            c,
            "HITS  $focusHits",
            24f,
            100f,
            16f,
            Color.WHITE,
            true
        )

        paint.color = 0xff895cff.toInt()

        c.drawCircle(
            focusX,
            focusY,
            35f,
            paint
        )

        paint.color = 0xff5ce7ff.toInt()

        c.drawCircle(
            focusX,
            focusY,
            18f + sin(animation * 5f) * 4f,
            paint
        )

        paint.color = Color.WHITE

        c.drawCircle(
            focusX,
            focusY,
            5f,
            paint
        )

        card(
            c,
            25f,
            h - 165f,
            w - 25f,
            h - 105f,
            18f
        )

        text(
            c,
            "Tap the glowing center",
            w / 2f,
            h - 128f,
            14f,
            0xffb8c4e4.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    private fun startFocus() {
        focusHits = 0
        moveFocusTarget()
    }

    private fun moveFocusTarget() {
        val w = width.toFloat()
        val h = height.toFloat()

        focusX =
            Random.nextFloat() *
                    (w - 100f) + 50f

        focusY =
            Random.nextFloat() *
                    (h - 280f) + 140f

        focusMoveAt =
            System.currentTimeMillis() + 1300L
    }

    // =========================================================
    // RELAX
    // =========================================================

    private fun drawRelax(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        header(
            c,
            "Breathe",
            "Slow down your mind"
        )

        val cx = w / 2f
        val cy = h * .40f

        val phase =
            (System.currentTimeMillis() -
                    relaxStartedAt) / 4000f

        val breathing =
            (sin(phase * Math.PI * 2.0) + 1.0) / 2.0

        val radius =
            65f + breathing.toFloat() * 80f

        for (i in 0 until 5) {

            paint.color = Color.argb(
                30 - i * 4,
                60,
                220,
                255
            )

            c.drawCircle(
                cx,
                cy,
                radius + i * 35f,
                paint
            )
        }

        paint.color = 0xff67e7ff.toInt()

        c.drawCircle(
            cx,
            cy,
            radius,
            paint
        )

        text(
            c,
            if (breathing < .5)
                "BREATHE IN"
            else
                "BREATHE OUT",
            cx,
            cy + 7f,
            17f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Follow the circle",
            cx,
            cy + 155f,
            15f,
            0xffb5c0df.toInt(),
            false,
            Paint.Align.CENTER
        )

        if (!relaxRunning) {
            button(
                c,
                "START BREATHING",
                w * .16f,
                h - 175f,
                w * .84f,
                h - 120f
            )
        }
    }

    private fun startRelax() {
        relaxRunning = true
        relaxStartedAt = System.currentTimeMillis()
    }

    // =========================================================
    // DAILY
    // =========================================================

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
            24f
        )

        text(
            c,
            "TODAY'S RESET",
            38f,
            128f,
            12f,
            0xff5de7ff.toInt(),
            true
        )

        text(
            c,
            "Clear your mind",
            38f,
            165f,
            24f,
            Color.WHITE,
            true
        )

        text(
            c,
            "Complete one quick challenge",
            38f,
            195f,
            13f,
            0xffabb7d8.toInt()
        )

        text(
            c,
            "and earn bonus XP.",
            38f,
            216f,
            13f,
            0xffabb7d8.toInt()
        )

        button(
            c,
            "PLAY QUICK TAP",
            38f,
            235f,
            w - 38f,
            275f
        )

        text(
            c,
            "🔥  $streak DAY STREAK",
            22f,
            330f,
            18f,
            0xffffb52e.toInt(),
            true
        )

        card(
            c,
            22f,
            355f,
            w - 22f,
            430f,
            20f
        )

        text(
            c,
            "Today's reward",
            40f,
            387f,
            14f,
            Color.WHITE,
            true
        )

        text(
            c,
            "+50 XP",
            40f,
            412f,
            20f,
            0xff5de7ff.toInt(),
            true
        )

        navigation(
            c,
            w,
            h,
            3
        )
    }

    // =========================================================
    // PROFILE
    // =========================================================

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
            85f,
            w - 18f,
            215f,
            24f
        )

        text(
            c,
            "✦",
            62f,
            160f,
            55f,
            0xff62e7ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Mind Explorer",
            105f,
            130f,
            21f,
            Color.WHITE,
            true
        )

        text(
            c,
            "LEVEL ${score / 500 + 1}",
            105f,
            157f,
            13f,
            0xff5de7ff.toInt(),
            true
        )

        text(
            c,
            "$score XP",
            105f,
            185f,
            13f,
            0xffaab6d8.toInt()
        )

        text(
            c,
            "🔥 Streak  $streak",
            35f,
            260f,
            16f,
            0xffffb52e.toInt(),
            true
        )

        text(
            c,
            "🧠 Games played  ${score / 25}",
            35f,
            295f,
            15f,
            Color.WHITE,
            true
        )

        card(
            c,
            18f,
            325f,
            w - 18f,
            445f,
            22f
        )

        text(
            c,
            "⚙  Settings",
            38f,
            365f,
            16f,
            Color.WHITE
        )

        text(
            c,
            "✨  Mind refresh progress",
            38f,
            405f,
            16f,
            Color.WHITE
        )

        text(
            c,
            "🌙  Dark mode",
            38f,
            445f,
            16f,
            Color.WHITE
        )

        navigation(
            c,
            w,
            h,
            4
        )
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
            message,
            w / 2f,
            h - 103f,
            13f,
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

    private fun save() {
        prefs.edit()
            .putInt("score", score)
            .putInt("streak", streak)
            .apply()
    }

    // =========================================================
    // GAME UPDATE
    // =========================================================

    private fun updateGames() {

        val now = System.currentTimeMillis()

        if (screen == Screen.MEMORY &&
            memoryPreview &&
            now >= memoryPreviewUntil
        ) {
            memoryPreview = false
        }

        if (screen == Screen.REACTION &&
            reactionRunning
        ) {

            if (now >= reactionEnd) {

                reactionRunning = false

                score += reactionHits * 5
                save()

                showMessage(
                    "Finished! +${reactionHits * 5} XP"
                )
            } else if (now >= reactionMoveAt) {
                moveReactionTarget()
            }
        }

        if (screen == Screen.FOCUS) {

            if (focusX == 0f ||
                now >= focusMoveAt
            ) {
                moveFocusTarget()
            }
        }
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
                if (y > h * .70f) {
                    prefs.edit()
                        .putBoolean("welcome_seen", true)
                        .apply()

                    screen = Screen.HOME
                }
            }

            Screen.HOME -> {
                homeTouch(x, y, w, h)
            }

            Screen.GAMES -> {
                gamesTouch(x, y, w, h)
            }

            Screen.MEMORY -> {
                memoryTouch(x, y, w)
            }

            Screen.REACTION -> {
                reactionTouch(x, y, w, h)
            }

            Screen.FOCUS -> {
                focusTouch(x, y, w, h)
            }

            Screen.RELAX -> {
                relaxTouch(x, y, w, h)
            }

            Screen.DAILY -> {
                dailyTouch(x, y, w, h)
            }

            Screen.PROFILE -> {
                if (y < 80f) {
                    screen = Screen.HOME
                }
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
                screen = Screen.FOCUS
                startFocus()
                return
            }

            if (x < w * .80f) {
                screen = Screen.DAILY
                return
            }

            screen = Screen.PROFILE
            return
        }

        if (y in 175f..225f) {
            screen = Screen.DAILY
            return
        }

        if (y in 265f..360f) {

            if (x < w / 2f) {
                startMemory()
            } else {
                startReaction()
                screen = Screen.REACTION
            }

            return
        }

        if (y in 370f..465f) {

            if (x < w / 2f) {
                startFocus()
                screen = Screen.FOCUS
            } else {
                startRelax()
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
                startFocus()
                screen = Screen.FOCUS
            } else if (x < w * .80f) {
                screen = Screen.DAILY
            } else {
                screen = Screen.PROFILE
            }

            return
        }

        if (y in 95f..205f) {

            if (x < w / 2f) {
                startMemory()
            } else {
                startReaction()
                screen = Screen.REACTION
            }

            return
        }

        if (y in 218f..328f) {

            if (x < w / 2f) {
                startFocus()
                screen = Screen.FOCUS
            } else {
                startRelax()
                screen = Screen.RELAX
            }
        }
    }

    // =========================================================
    // MEMORY TOUCH
    // =========================================================

    private fun memoryTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        if (memoryPreview) {
            return
        }

        val size = min(
            w * .82f,
            330f
        )

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

        if (memoryMatched.contains(index)) {
            return
        }

        if (memoryOpened.contains(index)) {
            return
        }

        if (memoryOpened.size >= 2) {
            return
        }

        memoryOpened.add(index)

        if (memoryOpened.size == 2) {

            val first = memoryOpened[0]
            val second = memoryOpened[1]

            if (
                memoryCards[first] ==
                memoryCards[second]
            ) {

                memoryMatched.add(first)
                memoryMatched.add(second)

                score += 20
                save()

                showMessage("MATCH! +20 XP")

                memoryOpened.clear()

                if (memoryMatched.size == 12) {

                    score += 50
                    streak++
                    save()

                    showMessage(
                        "Memory cleared! +50 XP"
                    )
                }

            } else {

                postDelayed({

                    memoryOpened.clear()
                    invalidate()

                }, 650L)
            }
        }
    }

    // =========================================================
    // REACTION TOUCH
    // =========================================================

    private fun reactionTouch(
        x: Float,
        y: Float,
        w: Float,
        h: Float
    ) {

        if (y < 80f) {
            reactionRunning = false
            screen = Screen.GAMES
            return
        }

        if (!reactionRunning) {

            if (y in 250f..350f) {
                startReaction()
            }

            return
        }

        val distance =
            kotlin.math.sqrt(
                (x - reactionX) *
                        (x - reactionX) +
                        (y - reactionY) *
                        (y - reactionY)
            )

        if (distance <= reactionRadius + 30f) {

            reactionHits++

            score += 2
            save()

            showMessage(
                "+2 XP  •  Nice!"
            )

            moveReactionTarget()
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

        if (y < 80f) {
            screen = Screen.GAMES
            return
        }

        val distance =
            kotlin.math.sqrt(
                (x - focusX) *
                        (x - focusX) +
                        (y - focusY) *
                        (y - focusY)
            )

        if (distance <= 55f) {

            focusHits++

            score += 5
            save()

            showMessage(
                "FOCUS HIT! +5 XP"
            )

            moveFocusTarget()
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

        if (y < 80f) {
            screen = Screen.GAMES
            relaxRunning = false
            return
        }

        if (!relaxRunning) {

            if (y > h - 200f) {
                startRelax()
            }

            return
        }

        score += 1
        save()

        showMessage(
            "Slow breath... +1 XP"
        )
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

        if (y < 80f) {
            screen = Screen.HOME
            return
        }

        if (y in 225f..285f) {
            startReaction()
            screen = Screen.REACTION
        }
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
            Screen.PROFILE -> {
                screen = Screen.HOME
            }

            Screen.MEMORY,
            Screen.REACTION,
            Screen.FOCUS,
            Screen.RELAX -> {
                reactionRunning = false
                relaxRunning = false
                screen = Screen.GAMES
            }
        }

        invalidate()
        return true
    }
}
