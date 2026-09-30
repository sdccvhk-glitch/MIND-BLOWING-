package com.mindblow.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.max
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private val bg = Color.rgb(7, 9, 22)
    private val card = Color.rgb(23, 27, 50)
    private val purple = Color.rgb(125, 75, 255)
    private val cyan = Color.rgb(0, 220, 255)
    private val pink = Color.rgb(255, 70, 170)
    private val green = Color.rgb(55, 220, 145)
    private val orange = Color.rgb(255, 165, 65)
    private val white = Color.WHITE
    private val muted = Color.rgb(165, 173, 200)

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy {
        getSharedPreferences("mindblow", Context.MODE_PRIVATE)
    }

    private var score = 0
    private var streak = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        score = prefs.getInt("score", 0)
        streak = prefs.getInt("streak", 0)
        showHome()
    }

    private fun showHome() {
        val root = baseLayout()

        val scroll = ScrollView(this)
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(22, 30, 22, 30)

        val logo = text(
            "✦ MINDBLOW",
            30f,
            white,
            Gravity.CENTER
        )
        content.addView(logo, lp(-1, 60))

        val subtitle = text(
            "REFRESH • PLAY • THINK",
            12f,
            cyan,
            Gravity.CENTER
        )
        content.addView(subtitle, lp(-1, 35))

        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.VERTICAL
        hero.setPadding(24, 22, 24, 22)
        hero.background = rounded(
            Color.rgb(35, 25, 75),
            28
        )

        val heroSmall = text(
            "TODAY'S CHALLENGE",
            12f,
            cyan,
            Gravity.LEFT
        )

        val heroTitle = text(
            "Memory Rush",
            27f,
            white,
            Gravity.LEFT
        )

        val heroDesc = text(
            "Remember the cards and beat your best score.",
            14f,
            muted,
            Gravity.LEFT
        )

        val play = Button(this)
        play.text = "PLAY NOW  →"
        play.textSize = 16f
        play.setTextColor(white)
        play.background = rounded(purple, 24)

        play.setOnClickListener {
            startMemoryGame()
        }

        hero.addView(heroSmall)
        hero.addView(heroTitle, lp(-1, 48))
        hero.addView(heroDesc, lp(-1, 45))
        hero.addView(play, lp(-1, 55))

        content.addView(hero, marginLp(-1, 225, 0, 18, 0, 0))

        val stats = LinearLayout(this)
        stats.orientation = LinearLayout.HORIZONTAL

        stats.addView(statBox("🔥", streak.toString(), "STREAK"))
        stats.addView(statBox("⭐", score.toString(), "XP"))
        stats.addView(
            statBox(
                "🏆",
                (score / 100 + 1).toString(),
                "LEVEL"
            )
        )

        content.addView(stats, marginLp(-1, 105, 0, 15, 0, 0))

        val heading = text(
            "QUICK GAMES",
            20f,
            white,
            Gravity.LEFT
        )
        content.addView(heading, marginLp(-1, 45, 5, 0, 0, 0))

        content.addView(
            gameCard(
                "🧠",
                "Memory Match",
                "Find matching pairs",
                purple
            ) {
                startMemoryGame()
            }
        )

        content.addView(
            gameCard(
                "⚡",
                "Reaction Rush",
                "Test your reaction speed",
                cyan
            ) {
                startReactionGame()
            }
        )

        content.addView(
            gameCard(
                "🎨",
                "Color Mind",
                "Match the correct color",
                pink
            ) {
                startColorGame()
            }
        )

        content.addView(
            gameCard(
                "🔢",
                "Number Flow",
                "Remember the number sequence",
                orange
            ) {
                startNumberGame()
            }
        )

        val progress = text(
            "YOUR PROGRESS",
            20f,
            white,
            Gravity.LEFT
        )

        content.addView(
            progress,
            marginLp(-1, 45, 15, 5, 0, 0)
        )

        val barBackground = LinearLayout(this)
        barBackground.setBackgroundColor(Color.rgb(40, 44, 70))

        val bar = View(this)
        bar.background = rounded(cyan, 10)

        val percent = (score % 100).coerceAtLeast(5)

        barBackground.addView(
            bar,
            LinearLayout.LayoutParams(
                0,
                12,
                percent.toFloat()
            )
        )

        content.addView(
            barBackground,
            marginLp(-1, 12, 0, 20, 0, 0)
        )

        val footer = text(
            "Keep playing to increase your XP and unlock new challenges.",
            13f,
            muted,
            Gravity.CENTER
        )

        content.addView(footer, lp(-1, 55))

        scroll.addView(content)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        root.addView(bottomNavigation())

        setContentView(root)
    }

    // ---------------------------------------------------------
    // MEMORY GAME
    // ---------------------------------------------------------

    private fun startMemoryGame() {
        val root = gameRoot("MEMORY MATCH")

        val info = text(
            "Find all matching pairs",
            16f,
            muted,
            Gravity.CENTER
        )

        root.addView(info, marginLp(-1, 45, 0, 10, 0, 0))

        val grid = GridLayout(this)
        grid.columnCount = 4
        grid.rowCount = 3

        val symbols = listOf(
            "★", "★",
            "●", "●",
            "◆", "◆",
            "♥", "♥",
            "▲", "▲",
            "☀", "☀"
        ).shuffled()

        val buttons = mutableListOf<Button>()
        var firstIndex = -1
        var locked = false
        var matches = 0

        fun finish() {
            addXP(100)
            showMessage("Amazing! +100 XP")
            handler.postDelayed({
                showHome()
            }, 1000)
        }

        symbols.forEachIndexed { index, symbol ->

            val b = Button(this)
            b.text = "?"
            b.textSize = 24f
            b.setTextColor(white)
            b.background = rounded(card, 18)

            b.setOnClickListener {

                if (locked || b.text != "?") return@setOnClickListener

                b.text = symbol
                b.setTextColor(cyan)

                if (firstIndex == -1) {
                    firstIndex = index
                    return@setOnClickListener
                }

                if (symbols[firstIndex] == symbols[index]) {

                    matches++
                    buttons[firstIndex].isEnabled = false
                    b.isEnabled = false
                    firstIndex = -1

                    if (matches == 6) {
                        finish()
                    }

                } else {

                    locked = true

                    val previous = buttons[firstIndex]

                    handler.postDelayed({

                        previous.text = "?"
                        b.text = "?"

                        previous.setTextColor(white)
                        b.setTextColor(white)

                        firstIndex = -1
                        locked = false

                    }, 650)
                }
            }

            buttons.add(b)

            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = 100
            params.columnSpec =
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            params.setMargins(6, 6, 6, 6)

            grid.addView(b, params)
        }

        root.addView(
            grid,
            marginLp(-1, 330, 10, 20, 10, 0)
        )

        root.addView(backButton())
        setContentView(root)
    }

    // ---------------------------------------------------------
    // REACTION GAME
    // ---------------------------------------------------------

    private fun startReactionGame() {
        val root = gameRoot("REACTION RUSH")

        val title = text(
            "Wait for GREEN...",
            26f,
            white,
            Gravity.CENTER
        )

        root.addView(title, marginLp(-1, 80, 0, 20, 0, 0))

        val target = Button(this)
        target.text = "WAIT"
        target.textSize = 25f
        target.setTextColor(white)
        target.background = rounded(Color.rgb(120, 35, 55), 35)

        var ready = false
        var startTime = 0L

        target.setOnClickListener {

            if (!ready) {
                showMessage("Too early! Wait for green.")
                return@setOnClickListener
            }

            val reaction = System.currentTimeMillis() - startTime
            val gained = max(10, 100 - reaction.toInt() / 5)

            addXP(gained)

            target.text = "${reaction}ms"
            title.text = "Great reaction!"

            handler.postDelayed({
                showHome()
            }, 1200)
        }

        root.addView(
            target,
            marginLp(-1, 220, 30, 30, 30, 0)
        )

        root.addView(
            text(
                "Tap only when the button turns GREEN.",
                14f,
                muted,
                Gravity.CENTER
            ),
            marginLp(-1, 50, 0, 30, 0, 0)
        )

        root.addView(backButton())

        setContentView(root)

        val delay = Random.nextLong(1500, 4000)

        handler.postDelayed({

            ready = true
            startTime = System.currentTimeMillis()

            target.text = "TAP!"
            target.background = rounded(green, 35)

        }, delay)
    }

    // ---------------------------------------------------------
    // COLOR GAME
    // ---------------------------------------------------------

    private fun startColorGame() {
        val root = gameRoot("COLOR MIND")

        var points = 0
        var round = 0

        val question = text(
            "",
            27f,
            white,
            Gravity.CENTER
        )

        val result = text(
            "",
            15f,
            muted,
            Gravity.CENTER
        )

        root.addView(question, marginLp(-1, 80, 0, 15, 0, 0))
        root.addView(result, marginLp(-1, 40, 0, 20, 0, 0))

        val grid = GridLayout(this)
        grid.columnCount = 2

        val colors = listOf(
            "RED" to Color.rgb(240, 65, 75),
            "BLUE" to Color.rgb(55, 130, 255),
            "GREEN" to Color.rgb(50, 210, 130),
            "YELLOW" to Color.rgb(245, 200, 55)
        )

        fun nextRound() {

            round++

            if (round > 8) {
                addXP(points * 10)
                showMessage("+${points * 10} XP")

                handler.postDelayed({
                    showHome()
                }, 1000)

                return
            }

            val answer = colors.random()

            question.text = "Tap: ${answer.first}"

            grid.removeAllViews()

            val choices = colors.shuffled()

            choices.forEach { choice ->

                val b = Button(this)
                b.text = choice.first
                b.textSize = 17f
                b.setTextColor(white)
                b.background = rounded(choice.second, 20)

                b.setOnClickListener {

                    if (choice.first == answer.first) {
                        points++
                        result.text = "Correct!  +10"
                    } else {
                        result.text = "Not quite!"
                    }

                    handler.postDelayed({
                        nextRound()
                    }, 400)
                }

                val p = GridLayout.LayoutParams()
                p.width = 0
                p.height = 90
                p.columnSpec =
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                p.setMargins(7, 7, 7, 7)

                grid.addView(b, p)
            }
        }

        root.addView(
            grid,
            marginLp(-1, 220, 10, 25, 10, 0)
        )

        root.addView(backButton())

        setContentView(root)

        nextRound()
    }

    // ---------------------------------------------------------
    // NUMBER FLOW
    // ---------------------------------------------------------

    private fun startNumberGame() {
        val root = gameRoot("NUMBER FLOW")

        val instruction = text(
            "Remember the sequence",
            22f,
            white,
            Gravity.CENTER
        )

        val sequenceView = text(
            "",
            32f,
            cyan,
            Gravity.CENTER
        )

        val input = TextView(this)
        input.text = ""
        input.textSize = 27f
        input.setTextColor(white)
        input.gravity = Gravity.CENTER
        input.background = rounded(card, 20)

        val button = Button(this)
        button.text = "START"
        button.textSize = 17f
        button.setTextColor(white)
        button.background = rounded(purple, 25)

        root.addView(instruction, marginLp(-1, 55, 0, 20, 0, 0))
        root.addView(sequenceView, marginLp(-1, 80, 0, 15, 0, 0))
        root.addView(input, marginLp(-1, 65, 25, 20, 25, 0))
        root.addView(button, marginLp(-1, 60, 40, 20, 40, 0))
        root.addView(backButton())

        setContentView(root)

        var level = 1
