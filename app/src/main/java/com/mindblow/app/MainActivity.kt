package com.mindblow.app

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.media.MediaPlayer
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {

    private lateinit var view: MindBlowView
    private var music: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(5, 7, 22)
        window.navigationBarColor = Color.rgb(5, 7, 22)

        view = MindBlowView(this)
        setContentView(view)
    }

    fun toggleMusic() {
        try {
            if (music == null) {
                val id = resources.getIdentifier(
                    "mind_refresh",
                    "raw",
                    packageName
                )

                if (id == 0) {
                    view.message("Add mind_refresh.mp3 to res/raw")
                    return
                }

                music = MediaPlayer.create(this, id)
                music?.isLooping = true
                music?.start()
                view.musicOn = true
            } else {
                if (music!!.isPlaying) {
                    music!!.pause()
                    view.musicOn = false
                } else {
                    music!!.start()
                    view.musicOn = true
                }
            }
        } catch (_: Exception) {
            view.message("Music unavailable")
        }

        view.invalidate()
    }

    override fun onPause() {
        super.onPause()
        music?.pause()
    }

    override fun onDestroy() {
        music?.release()
        music = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!view.goBack()) {
            super.onBackPressed()
        }
    }
}

private enum class Screen {
    SPLASH, HOME, GAMES, PUZZLE, MEMORY,
    REACTION, COLOR, NUMBER, BREATH, SONGS, PROFILE
}

private class MindBlowView(
    private val ctx: Context
) : View(ctx) {

    private val activity = ctx as MainActivity
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private val prefs =
        ctx.getSharedPreferences("mindblow", Context.MODE_PRIVATE)

    private var screen =
        if (prefs.getBoolean("started", false))
            Screen.HOME
        else
            Screen.SPLASH

    private var startTime = System.currentTimeMillis()
    private var time = 0f

    private var score = prefs.getInt("score", 0)
    private var streak = prefs.getInt("streak", 0)

    var musicOn = false

    private var target = Random.nextInt(16)

    private var memory = MutableList(8) { it / 2 }
        .apply { shuffle() }

    private var first = -1
    private var second = -1
    private var memoryBusy = false

    private var reactionTarget = false
    private var reactionStart = 0L
    private var reactionBest = prefs.getInt("reaction", 9999)

    private var colorTarget = Random.nextInt(4)

    private var numberSequence = ""
    private var numberMessage = "Tap START"
    private var numberRound = 1

    private var breathPhase = 0

    private var toastText = ""
    private var toastUntil = 0L

    init {
        isFocusable = true
        postInvalidateDelayed(16)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)

        val w = width.toFloat()
        val h = height.toFloat()

        time += 0.025f

        background(c, w, h)

        when (screen) {
            Screen.SPLASH -> splash(c, w, h)
            Screen.HOME -> home(c, w, h)
            Screen.GAMES -> games(c, w, h)
            Screen.PUZZLE -> puzzle(c, w, h)
            Screen.MEMORY -> memoryGame(c, w, h)
            Screen.REACTION -> reaction(c, w, h)
            Screen.COLOR -> colorGame(c, w, h)
            Screen.NUMBER -> numberGame(c, w, h)
            Screen.BREATH -> breath(c, w, h)
            Screen.SONGS -> songs(c, w, h)
            Screen.PROFILE -> profile(c, w, h)
        }

        if (toastUntil > System.currentTimeMillis()) {
            toast(c, w, h)
        }

        if (
            screen == Screen.SPLASH &&
            System.currentTimeMillis() - startTime > 1400
        ) {
            screen = Screen.HOME
            prefs.edit().putBoolean("started", true).apply()
        }

        postInvalidateDelayed(16)
    }

    // ---------------------------------------------------------
    // BACKGROUND
    // ---------------------------------------------------------

    private fun background(c: Canvas, w: Float, h: Float) {

        p.shader = LinearGradient(
            0f,
            0f,
            w,
            h,
            Color.rgb(4, 8, 28),
            Color.rgb(35, 5, 60),
            Shader.TileMode.CLAMP
        )

        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        val x1 = w * .18f + sin(time) * 50f
        val y1 = h * .20f + cos(time) * 35f

        val x2 = w * .82f + cos(time * .7f) * 45f
        val y2 = h * .38f + sin(time) * 40f

        p.color = Color.argb(40, 30, 220, 255)
        c.drawCircle(x1, y1, 150f, p)

        p.color = Color.argb(35, 150, 70, 255)
        c.drawCircle(x2, y2, 170f, p)

        p.color = Color.argb(25, 30, 255, 180)
        c.drawCircle(
            w * .5f + sin(time * .6f) * 45f,
            h * .82f,
            180f,
            p
        )

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = Color.argb(22, 100, 220, 255)

        for (i in 0..6) {
            c.drawCircle(
                w / 2f,
                h * .43f,
                65f + i * 55f + sin(time + i) * 6f,
                p
            )
        }

        p.style = Paint.Style.FILL
    }

    // ---------------------------------------------------------
    // TEXT
    // ---------------------------------------------------------

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

    // ---------------------------------------------------------
    // CARD
    // ---------------------------------------------------------

    private fun card(
        c: Canvas,
        l: Float,
        t: Float,
        r: Float,
        b: Float,
        radius: Float = 20f
    ) {
        p.shader = null
        p.color = Color.argb(225, 12, 18, 45)
        c.drawRoundRect(l, t, r, b, radius, radius, p)

        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f
        p.color = Color.argb(65, 90, 170, 230)
        c.drawRoundRect(l, t, r, b, radius, radius, p)
        p.style = Paint.Style.FILL
    }

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
            Color.rgb(20, 215, 255),
            Color.rgb(135, 65, 255),
            Shader.TileMode.CLAMP
        )

        c.drawRoundRect(l, t, r, b, 18f, 18f, p)
        p.shader = null

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

    private fun header(
        c: Canvas,
        title: String,
        subtitle: String
    ) {
        text(c, "‹", 22f, 50f, 38f)
        text(c, title, 58f, 42f, 23f, Color.WHITE, true)
        text(c, subtitle, 58f, 63f, 11f, 0xff9da8cf.toInt())
    }

    // ---------------------------------------------------------
    // NAVIGATION
    // ---------------------------------------------------------

    private fun nav(
        c: Canvas,
        w: Float,
        h: Float,
        selected: Int
    ) {
        val top = h - 82f

        card(c, 10f, top, w - 10f, h - 8f, 23f)

        val icons = arrayOf("⌂", "◆", "◎", "★", "●")
        val names = arrayOf(
            "Home",
            "Games",
            "Focus",
            "Daily",
            "Profile"
        )

        for (i in 0..4) {
            val x = w * (i + .5f) / 5f
            val col =
                if (i == selected)
                    0xff55e6ff.toInt()
                else
                    0xff7883a8.toInt()

            text(c, icons[i], x, top + 29f, 20f,
                col, true, Paint.Align.CENTER)

            text(c, names[i], x, top + 51f, 9f,
                col, false, Paint.Align.CENTER)
        }
    }

    // ---------------------------------------------------------
    // SPLASH
    // ---------------------------------------------------------

    private fun splash(c: Canvas, w: Float, h: Float) {
        text(
            c,
            "✦",
            w / 2f,
            h * .40f,
            80f,
            0xff62e8ff.toInt(),
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
            "RELAX  •  FOCUS  •  REFRESH",
            w / 2f,
            h * .55f,
            13f,
            0xffb7c2e5.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // HOME
    // ---------------------------------------------------------

    private fun home(c: Canvas, w: Float, h: Float) {

        text(c, "MindBlow", 20f, 40f, 28f, Color.WHITE, true)

        text(
            c,
            "Refresh your mind • Level ${score / 500 + 1}",
            20f,
            63f,
            11f,
            0xff9ca8cc.toInt()
        )

        card(c, w - 115f, 18f, w - 18f, 58f, 16f)

        text(
            c,
            "✦ $score",
            w - 66f,
            44f,
            14f,
            0xffffd66b.toInt(),
            true,
            Paint.Align.CENTER
        )

        card(c, 18f, 82f, w - 18f, 205f, 24f)

        text(c, "YOUR MINI RESET", 35f, 112f,
            11f, 0xff55e6ff.toInt(), true)

        text(c, "How do you feel?", 35f, 145f,
            22f, Color.WHITE, true)

        text(c, "Choose a quick activity and reset your focus.",
            35f, 169f, 12f, 0xffaab5d6.toInt())

        button(
            c,
            "🎧  MIND REFRESHING SONGS",
            34f,
            178f,
            w - 34f,
            215f
        )

        text(c, "QUICK GAMES", 20f, 248f,
            18f, Color.WHITE, true)

        gameCard(c, "✦", "Glow Hunt",
            "Find the light", 18f, 265f,
            w / 2f - 8f, 350f)

        gameCard(c, "◉", "Memory",
            "Train recall", w / 2f + 8f, 265f,
            w - 18f, 350f)

        gameCard(c, "⚡", "Reaction",
            "React fast", 18f, 360f,
            w / 2f - 8f, 445f)

        gameCard(c, "◈", "Color Mind",
            "Beat the trick", w / 2f + 8f, 360f,
            w - 18f, 445f)

        nav(c, w, h, 0)
    }

    private fun gameCard(
        c: Canvas,
        icon: String,
        title: String,
        sub: String,
        l: Float,
        t: Float,
        r: Float,
        b: Float
    ) {
        card(c, l, t, r, b, 19f)

        text(c, icon, l + 32f, t + 38f,
            25f, 0xff5ce7ff.toInt(), true,
            Paint.Align.CENTER)

        text(c, title, l + 18f, t + 63f,
            15f, Color.WHITE, true)

        text(c, sub, l + 18f, t + 81f,
            10f, 0xff9da8cc.toInt())
    }

    // ---------------------------------------------------------
    // GAMES
    // ---------------------------------------------------------

    private fun games(c: Canvas, w: Float, h: Float) {

        header(c, "Mind Games", "Six quick ways to refresh")

        gameCard(c, "✦", "Glow Hunt",
            "Find the glowing tile",
            18f, 90f, w / 2f - 8f, 180f)

        gameCard(c, "◉", "Memory",
            "Remember symbols",
            w / 2f + 8f, 90f, w - 18f, 180f)

        gameCard(c, "⚡", "Reaction",
            "React as fast as possible",
            18f, 192f, w / 2f - 8f, 282f)

        gameCard(c, "◆", "Color Mind",
            "Choose the correct color",
            w / 2f + 8f, 192f, w - 18f, 282f)

        gameCard(c, "123", "Number Flow",
            "Remember the numbers",
            18f, 294f, w / 2f - 8f, 384f)

        gameCard(c, "☯", "Breath Reset",
            "Follow the breathing rhythm",
            w / 2f + 8f, 294f, w - 18f, 384f)

        button(
            c,
            "🎧  MIND REFRESHING SONGS",
            18f,
            405f,
            w - 18f,
            455f
        )

        nav(c, w, h, 1)
    }

    // ---------------------------------------------------------
    // PUZZLE
    // ---------------------------------------------------------

    private fun puzzle(c: Canvas, w: Float, h: Float) {

        header(c, "Glow Hunt", "Find the glowing tile")

        val size = min(w * .78f, 310f)
        val left = (w - size) / 2f
        val top = 105f
        val cell = size / 4f

        for (i in 0 until 16) {

            val row = i / 4
            val col = i % 4

            val l = left + col * cell + 4f
            val t = top + row * cell + 4f
            val r = left + (col + 1) * cell - 4f
            val b = top + (row + 1) * cell - 4f

            card(c, l, t, r, b, 13f)

            if (i == target) {
                p.color = 0xff48e7ff.toInt()
                c.drawCircle(
                    (l + r) / 2f,
                    (t + b) / 2f,
                    16f + sin(time * 4f) * 5f,
                    p
                )
            }
        }

        text(c, "Tap the glowing tile",
            w / 2f, top + size + 35f,
            14f, 0xffb5bfdf.toInt(),
            false, Paint.Align.CENTER)
    }

    // ---------------------------------------------------------
    // MEMORY
    // ---------------------------------------------------------

    private fun memoryGame(c: Canvas, w: Float, h: Float) {

        header(c, "Memory", "Find matching pairs")

        val size = min(w * .82f, 330f)
        val left = (w - size) / 2f
        val top = 95f
        val cell = size / 4f

        val icons = arrayOf(
            "★", "◆", "●", "✦"
        )

        for (i in 0 until 8) {

            val row = i / 4
            val col = i % 4

            val l = left + col * cell + 5f
            val t = top + row * cell + 5f
            val r = left + (col + 1) * cell - 5f
            val b = top + (row + 1) * cell - 5f

            card(c, l, t, r, b, 15f)

            val reveal =
                i == first ||
                i == second

            text(
                c,
                if (reveal) icons[memory[i]]
                else "?",
                (l + r) / 2f,
                (t + b) / 2f + 10f,
                28f,
                0xff62e8ff.toInt(),
                true,
                Paint.Align.CENTER
            )
        }

        text(
            c,
            "Match the pairs",
            w / 2f,
            top + cell * 2f + 30f,
            14f,
            0xffb5bfdf.toInt(),
            false,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // REACTION
    // ---------------------------------------------------------

    private fun reaction(c: Canvas, w: Float, h: Float) {

        header(c, "Reaction", "Tap when the circle turns green")

        val ready = reactionTarget

        p.color =
            if (ready)
                0xff35e98b.toInt()
            else
                0xff7c54ff.toInt()

        c.drawCircle(
            w / 2f,
            h * .40f,
            105f + sin(time * 2f) * 8f,
            p
        )

        text(
            c,
            if (ready) "TAP!" else "WAIT...",
            w / 2f,
            h * .40f + 12f,
            30f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            if (reactionBest < 9999)
                "Best: ${reactionBest} ms"
            else
                "No record yet",
            w / 2f,
            h * .60f,
            15f,
            0xffaeb8d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            c,
            if (reactionTarget) "TAP NOW!" else "START",
            w * .20f,
            h * .68f,
            w * .80f,
            h * .77f
        )
    }

    // ---------------------------------------------------------
    // COLOR
    // ---------------------------------------------------------

    private fun colorGame(c: Canvas, w: Float, h: Float) {

        header(c, "Color Mind", "Choose the matching color")

        val names = arrayOf(
            "CYAN",
            "PURPLE",
            "GREEN",
            "ORANGE"
        )

        val colors = intArrayOf(
            0xff35dfff.toInt(),
            0xff9b62ff.toInt(),
            0xff39e68b.toInt(),
            0xffffa83d.toInt()
        )

        val shown = Random.nextInt(4)

        text(
            c,
            names[shown],
            w / 2f,
            190f,
            40f,
            colors[shown],
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Tap the color that matches the word",
            w / 2f,
            235f,
            13f,
            0xffaeb8d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        for (i in 0 until 4) {

            val col = i % 2
            val row = i / 2

            val l = 35f + col * (w - 70f) / 2f
            val t = 275f + row * 85f
            val r = l + (w - 85f) / 2f
            val b = t + 65f

            p.color = colors[i]
            c.drawRoundRect(l, t, r, b, 18f, 18f, p)

            text(
                c,
                names[i],
                (l + r) / 2f,
                t + 40f,
                13f,
                Color.WHITE,
                true,
                Paint.Align.CENTER
            )
        }
    }

    // ---------------------------------------------------------
    // NUMBER
    // ---------------------------------------------------------

    private fun numberGame(c: Canvas, w: Float, h: Float) {

        header(c, "Number Flow", "Train your short-term memory")

        text(
            c,
            "ROUND $numberRound",
            w / 2f,
            125f,
            13f,
            0xff55e6ff.toInt(),
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            if (numberSequence.isEmpty())
                numberMessage
            else
                numberSequence,
            w / 2f,
            220f,
            34f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Remember the sequence",
            w / 2f,
            275f,
            14f,
            0xffaeb8d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            c,
            "START ROUND",
            w * .18f,
            330f,
            w * .82f,
            390f
        )
    }

    // ---------------------------------------------------------
    // BREATH
    // ---------------------------------------------------------

    private fun breath(c: Canvas, w: Float, h: Float) {

        header(c, "Breath Reset", "Slow breathing exercise")

        val phase = (sin(time * .8f) + 1f) / 2f
        val radius = 65f + phase * 65f

        p.color = Color.argb(45, 70, 220, 255)
        c.drawCircle(w / 2f, 245f, radius + 25f, p)

        p.color = 0xff5fe7ff.toInt()
        c.drawCircle(w / 2f, 245f, radius, p)

        text(
            c,
            if (phase < .5f) "BREATHE IN" else "BREATHE OUT",
            w / 2f,
            252f,
            18f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )

        text(
            c,
            "Follow the circle",
            w / 2f,
            380f,
            15f,
            0xffaeb8d8.toInt(),
            false,
            Paint.Align.CENTER
        )

        button(
            c,
            "RESET",
            w * .25f,
            420f,
            w * .75f,
            475f
        )
    }

    // ---------------------------------------------------------
    // SONGS
    // ---------------------------------------------------------

    private fun songs(c: Canvas, w: Float, h: Float) {

        header(c, "Mind Refreshing Songs",
            "Relax • focus • breathe")

        card(c, 18f, 90f, w - 18f, 190f, 24f)

        text(c, "🎧", 55f, 145f,
            38f, 0xff63e8ff.toInt(), true)

        text(c, "Mind Refresh",
            105f, 130f,
            20f, Color.WHITE, true)

        text(c, "Calm background sound",
            105f, 156f,
            12f, 0xffaeb8d8.toInt())

        button(
            c,
            if (musicOn) "PAUSE MUSIC" else "PLAY MUSIC",
            105f,
            165f,
            w - 35f,
            205f
        )

        card(c, 18f, 215f, w - 18f, 295f, 20f)

        text(c, "🌊  Calm Waves",
            38f, 250f,
            16f, Color.WHITE, true)

        text(c, "Use your own audio in res/raw",
            38f, 274f,
            11f, 0xff9da8cc.toInt())

        card(c, 18f, 315f, w - 18f, 395f, 20f)

        text(c, "🌙  Sleep & Relax",
            38f, 350f,
            16f, Color.WHITE, true)

        text(c, "Soft sounds for quiet moments",
            38f, 374f,
            11f, 0xff9da8cc.toInt())

        nav(c, w, h, 0)
    }

    // ---------------------------------------------------------
    // PROFILE
    // ---------------------------------------------------------

    private fun profile(c: Canvas, w: Float, h: Float) {

        header(c, "Profile", "Your MindBlow progress")

        card(c, 18f, 90f, w - 18f, 215f, 24f)

        text(c, "✦", 65f, 160f,
            52f, 0xff68ddff.toInt(),
            true, Paint.Align.CENTER)

        text(c, "Mind Explorer",
            110f, 135f,
            21f, Color.WHITE, true)

        text(c, "Level ${score / 500 + 1}",
            110f, 163f,
            13f, 0xffaeb8d8.toInt())

        text(c, "$score XP",
            110f, 190f,
            13f, 0xff8f9bc4.toInt())

        text(c, "🔥  $streak day streak",
            25f, 255f,
            18f, 0xffffb52e.toInt(), true)

        text(c, "Best reaction: ${
            if (reactionBest < 9999) "$reactionBest ms"
            else "--"
        }",
            25f, 290f,
            15f, Color.WHITE)

        button(
            c,
            if (musicOn) "♫  MUSIC ON" else "♫  MUSIC OFF",
            25f,
            330f,
            w - 25f,
            385f
        )

        nav(c, w, h, 4)
    }

    // ---------------------------------------------------------
    // TOUCH
    // ---------------------------------------------------------

    override fun onTouchEvent(e: MotionEvent): Boolean {

        if (e.action != MotionEvent.ACTION_UP) {
            return true
        }

        val x = e.x
        val y = e.y
        val w = width.toFloat()
        val h = height.toFloat()

        when (screen) {

            Screen.SPLASH -> Unit

            Screen.HOME -> homeTouch(x, y, w, h)

            Screen.GAMES -> gamesTouch(x, y, w, h)

            Screen.PUZZLE -> puzzleTouch(x, y, w)

            Screen.MEMORY -> memoryTouch(x, y, w)

            Screen.REACTION -> reactionTouch(x, y, w, h)

            Screen.COLOR -> colorTouch(x, y, w, h)

            Screen.NUMBER -> numberTouch(y, w)

            Screen.BREATH -> breathTouch(y)

            Screen.SONGS -> songsTouch(y, w)

            Screen.PROFILE -> profileTouch(y, w)
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

        if (y > h - 100f) {

            if (x < w * .20f) {
                screen = Screen.HOME
            } else if (x < w * .40f) {
                screen = Screen.GAMES
            } else if (x > w * .80f) {
                screen = Screen.PROFILE
            }

            return
        }

        if (y in 175f..225f) {
            screen = Screen.SONGS
            return
        }

        if (y in 265f..350f) {
            screen =
                if (x < w / 2f)
                    Screen.PUZZLE
                else
                    Screen.MEMORY
            return
        }

        if (y in 360f..450f) {
            screen =
                if (x < w / 2f)
                    Screen.REACTION
                else
                    Screen.COLOR
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

        if (y < 75f) {
            screen = Screen.HOME
            return
        }

        if (y > h - 100f) {

            if (x < w * .40f) {
                screen = Screen.HOME
            } else if (x > w * .80f) {
                screen = Screen.PROFILE
            }

            return
        }

        if (y in 90f..180f) {
            screen =
                if (x < w / 2f)
                    Screen.PUZZLE
                else
                    Screen.MEMORY
            return
        }

        if (y in 192f..282f) {
            screen =
                if (x < w / 2f)
                    Screen.REACTION
                else
                    Screen.COLOR
            return
        }

        if (y in 294f..384f) {
            screen =
                if (x < w / 2f)
                    Screen.NUMBER
                else
                    Screen.BREATH
            return
        }

        if (y in 400f..470f) {
            screen = Screen.SONGS
        }
    }

    // ---------------------------------------------------------
    // PUZZLE TOUCH
    // ---------------------------------------------------------

    private fun puzzleTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        val size = min(w * .78f, 310f)
        val left = (w - size) / 2f
        val top = 105f
        val cell = size / 4f

        if (
            x < left ||
            x > left + size ||
            y < top ||
            y > top + size
        ) return

        val col = ((x - left) / cell).toInt()
        val row = ((y - top) / cell).toInt()
        val hit = row * 4 + col

        if (hit == target) {

            score += 25
            save()

            target = Random.nextInt(16)

            message("+25 XP • Great!")
        } else {
            message("Try the glowing tile")
        }
    }

    // ---------------------------------------------------------
    // MEMORY TOUCH
    // ---------------------------------------------------------

    private fun memoryTouch(
        x: Float,
        y: Float,
        w: Float
    ) {

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        if (memoryBusy) return

        val size = min(w * .82f, 330f)
        val left = (w - size) / 2f
        val top = 95f
        val cell = size / 4f

        if (
            x < left ||
            x > left + size ||
            y < top ||
            y > top + cell * 2
        ) return

        val col = ((x - left) / cell).toInt()
        val row = ((y - top) / cell).toInt()

        val index = row * 4 + col

        if (index !in 0..7) return

        if (first == -1) {
            first = index
            return
        }

        if (second != -1 || index == first) return

        second = index

        if (memory[first] == memory[second]) {

            score += 20
            save()
            message("MATCH! +20 XP")

            postDelayed({
                first = -1
                second = -1
                invalidate()
            }, 500)

        } else {

            message("Not a match")

            memoryBusy = true

            postDelayed({
                first = -1
                second = -1
                memoryBusy = false
                invalidate()
            }, 700)
        }
    }

    // ---------------------------------------------------------
    // REACTION TOUCH
    // ---------------------------------------------------------

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

        if (!reactionTarget) {

            reactionTarget = true
            reactionStart = System.currentTimeMillis()

            postDelayed({
                if (screen == Screen.REACTION) {
                    reactionTarget = false
                    message("Too slow — try again!")
                    invalidate()
                }
            }, 1800)

        } else {

            val result =
                System.currentTimeMillis() - reactionStart

            if (result < reactionBest) {
                reactionBest = result.toInt()
                prefs.edit()
                    .putInt("reaction", reactionBest)
                    .apply()
            }

            score += 30
            save()

            reactionTarget = false

            message("${result} ms • +30 XP")
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

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        if (y < 270f) return

        val col = if (x < w / 2f) 0 else 1
        val row = if (y < 360f) 0 else 1
        val selected = row * 2 + col

        if (selected == colorTarget) {

            score += 20
            save()

            colorTarget = Random.nextInt(4)

            message("Correct! +20 XP")
        } else {
            message("Look carefully!")
        }
    }

    // ---------------------------------------------------------
    // NUMBER TOUCH
    // ---------------------------------------------------------

    private fun numberTouch(
        y: Float,
        w: Float
    ) {

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        if (y in 320f..410f) {

            val length = min(
                3 + numberRound,
                8
            )

            numberSequence = buildString {
                repeat(length) {
                    append(Random.nextInt(0, 10))
                }
            }

            numberMessage = "MEMORIZE"
            message("Remember: $numberSequence")

            postDelayed({

                if (screen == Screen.NUMBER) {
                    numberSequence = ""
                    numberMessage = "Now type it mentally"
                    invalidate()
                }

            }, 1800)
        }
    }

    // ---------------------------------------------------------
    // BREATH TOUCH
    // ---------------------------------------------------------

    private fun breathTouch(y: Float) {

        if (y < 75f) {
            screen = Screen.GAMES
            return
        }

        if (y in 410f..490f) {
            breathPhase = 0
            message("Relax • follow the circle")
        }
    }

    // ---------------------------------------------------------
    // SONGS TOUCH
    // ---------------------------------------------------------

    private fun songsTouch(
        y: Float,
        w: Float
    ) {

        if (y < 75f) {
            screen = Screen.HOME
            return
        }

        if (y in 155f..220f) {
            activity.toggleMusic()
        }
    }

    // ---------------------------------------------------------
    // PROFILE TOUCH
    // ---------------------------------------------------------

    private fun profileTouch(
        y: Float,
        w: Float
    ) {

        if (y < 75f) {
            screen = Screen.HOME
            return
        }

        if (y in 320f..400f) {
            activity.toggleMusic()
        }
    }

    // ---------------------------------------------------------
    // SAVE
    // ---------------------------------------------------------

    private fun save() {
        prefs.edit()
            .putInt("score", score)
            .putInt("streak", streak)
            .apply()
    }

    // ---------------------------------------------------------
    // MESSAGE
    // ---------------------------------------------------------

    fun message(s: String) {
        toastText = s
        toastUntil = System.currentTimeMillis() + 1400
        invalidate()
    }

    private fun toast(
        c: Canvas,
        w: Float,
        h: Float
    ) {
        card(
            c,
            25f,
            h - 140f,
            w - 25f,
            h - 88f,
            18f
        )

        text(
            c,
            toastText,
            w / 2f,
            h - 108f,
            13f,
            Color.WHITE,
            true,
            Paint.Align.CENTER
        )
    }

    // ---------------------------------------------------------
    // BACK
    // ---------------------------------------------------------

    fun goBack(): Boolean {

        if (screen == Screen.HOME ||
            screen == Screen.SPLASH
        ) {
            return false
        }

        screen = Screen.HOME
        invalidate()
        return true
    }
}
