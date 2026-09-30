package com.mindblow.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.animation.ValueAnimator
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : Activity() {

    // =========================================================
    // COLORS
    // =========================================================

    private val bgColor = Color.rgb(8, 10, 24)
    private val cardColor = Color.rgb(25, 28, 52)

    private val white = Color.WHITE
    private val cyan = Color.rgb(70, 220, 255)
    private val purple = Color.rgb(150, 90, 255)
    private val pink = Color.rgb(255, 80, 180)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showHome()
    }

    // =========================================================
    // HOME SCREEN
    // =========================================================

    private fun showHome() {

        val frame = animatedRoot()
        val root = contentRoot(frame)

        val title = text(
            "MIND",
            42f,
            white,
            Gravity.CENTER
        )

        title.typeface = Typeface.DEFAULT_BOLD

        val title2 = text(
            "BLOWING",
            29f,
            cyan,
            Gravity.CENTER
        )

        title2.typeface = Typeface.DEFAULT_BOLD

        val subtitle = text(
            "Refresh your mind • Challenge your brain",
            15f,
            Color.LTGRAY,
            Gravity.CENTER
        )

        root.addView(
            title,
            lp(-1, 55, 20, 45, 20, 0)
        )

        root.addView(
            title2,
            lp(-1, 45, 20, 0, 20, 0)
        )

        root.addView(
            subtitle,
            lp(-1, 45, 20, 5, 20, 25)
        )

        val numberButton = gameButton(
            "🔢  NUMBER FLOW",
            "Remember the sequence"
        ) {
            startNumberGame()
        }

        val memoryButton = gameButton(
            "🧩  MEMORY TILES",
            "Find the highlighted tile"
        ) {
            startMemoryGame()
        }

        val reactionButton = gameButton(
            "⚡  REACTION TAP",
            "Test your reaction speed"
        ) {
            startReactionGame()
        }

        root.addView(
            numberButton,
            lp(-1, 85, 25, 10, 25, 0)
        )

        root.addView(
            memoryButton,
            lp(-1, 85, 25, 12, 25, 0)
        )

        root.addView(
            reactionButton,
            lp(-1, 85, 25, 12, 25, 0)
        )

        val footer = text(
            "Choose a challenge and refresh your mind ✨",
            14f,
            Color.GRAY,
            Gravity.CENTER
        )

        root.addView(
            footer,
            lp(-1, 50, 20, 25, 20, 20)
        )

        setContentView(frame)
    }

    // =========================================================
    // NUMBER FLOW
    // =========================================================

    private fun startNumberGame() {

        val frame = animatedRoot()
        val root = contentRoot(frame)

        addGameTitle(root, "NUMBER FLOW")

        val instruction = text(
            "Memorize the number",
            21f,
            white,
            Gravity.CENTER
        )

        val sequence = text(
            "READY?",
            36f,
            cyan,
            Gravity.CENTER
        )

        val input = EditText(this)

        input.hint = "Enter number"
        input.textSize = 22f
        input.setTextColor(white)
        input.setHintTextColor(Color.GRAY)
        input.gravity = Gravity.CENTER
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.background = rounded(cardColor, 20f)

        val button = Button(this)

        button.text = "START"
        button.textSize = 16f
        button.setTextColor(white)
        button.background = rounded(purple, 25f)

        root.addView(
            instruction,
            lp(-1, 50, 20, 20, 20, 10)
        )

        root.addView(
            sequence,
            lp(-1, 70, 20, 10, 20, 20)
        )

        root.addView(
            input,
            lp(-1, 65, 30, 10, 30, 15)
        )

        root.addView(
            button,
            lp(-1, 60, 45, 10, 45, 10)
        )

        root.addView(backButton())

        setContentView(frame)

        var level = 1
        var answer = ""

        button.setOnClickListener {

            if (button.text.toString() == "START") {

                input.setText("")
                input.isEnabled = false

                button.text = "SHOWING..."

                answer = buildNumber(level)

                sequence.text = answer

                window.decorView.postDelayed({

                    sequence.text = "••••"

                    input.isEnabled = true

                    button.text = "CHECK"

                    input.requestFocus()

                }, 1600)

            } else {

                if (input.text.toString() == answer) {

                    ToastMessage(
                        "Correct! Level $level 🎉"
                    )

                    level++

                    input.setText("")

                    sequence.text = "READY?"

                    button.text = "START"

                } else {

                    ToastMessage(
                        "Wrong! Try again."
                    )

                    level = 1

                    input.setText("")

                    sequence.text = "READY?"

                    button.text = "START"
                }
            }
        }
    }

    private fun buildNumber(level: Int): String {

        val length = (3 + level).coerceAtMost(9)

        val result = StringBuilder()

        repeat(length) {

            result.append(
                Random.nextInt(0, 10)
            )
        }

        return result.toString()
    }

    // =========================================================
    // MEMORY TILES
    // =========================================================

    private fun startMemoryGame() {

        val frame = animatedRoot()
        val root = contentRoot(frame)

        addGameTitle(root, "MEMORY TILES")

        val info = text(
            "Remember the highlighted tile",
            19f,
            white,
            Gravity.CENTER
        )

        root.addView(
            info,
            lp(-1, 50, 20, 15, 20, 15)
        )

        val grid = GridLayout(this)

        grid.columnCount = 3
        grid.rowCount = 3

        val buttons = ArrayList<Button>()

        var target = Random.nextInt(0, 9)

        for (i in 0 until 9) {

            val tile = Button(this)

            tile.text = "?"
            tile.textSize = 20f
            tile.setTextColor(white)
            tile.background = rounded(
                cardColor,
                18f
            )

            val params = GridLayout.LayoutParams()

            params.width = 0
            params.height = 90

            params.columnSpec =
                GridLayout.spec(
                    GridLayout.UNDEFINED,
                    1f
                )

            params.setMargins(
                6,
                6,
                6,
                6
            )

            grid.addView(
                tile,
                params
            )

            buttons.add(tile)

            tile.setOnClickListener {

                if (i == target) {

                    ToastMessage(
                        "Correct! 🧠"
                    )

                    target = Random.nextInt(0, 9)

                    buttons.forEach {

                        it.background = rounded(
                            cardColor,
                            18f
                        )

                        it.text = "?"
                    }

                    buttons[target].background =
                        rounded(purple, 18f)

                    window.decorView.postDelayed({

                        buttons[target].background =
                            rounded(cardColor, 18f)

                    }, 700)

                } else {

                    ToastMessage(
                        "Wrong tile!"
                    )
                }
            }
        }

        root.addView(
            grid,
            lp(-1, 330, 15, 10, 15, 10)
        )

        buttons[target].background =
            rounded(purple, 18f)

        window.decorView.postDelayed({

            buttons[target].background =
                rounded(cardColor, 18f)

        }, 900)

        root.addView(backButton())

        setContentView(frame)
    }

    // =========================================================
    // REACTION GAME
    // =========================================================

    private fun startReactionGame() {

        val frame = animatedRoot()
        val root = contentRoot(frame)

        addGameTitle(root, "REACTION TAP")

        val info = text(
            "Wait for the button to change...",
            19f,
            white,
            Gravity.CENTER
        )

        val tap = Button(this)

        tap.text = "WAIT..."
        tap.textSize = 25f
        tap.setTextColor(white)
        tap.background = rounded(
            cardColor,
            30f
        )

        root.addView(
            info,
            lp(-1, 55, 20, 30, 20, 25)
        )

        root.addView(
            tap,
            lp(-1, 180, 40, 20, 40, 25)
        )

        root.addView(backButton())

        setContentView(frame)

        tap.isEnabled = false

        val delay = Random.nextLong(
            1500,
            4000
        )

        window.decorView.postDelayed({

            tap.isEnabled = true

            tap.text = "TAP NOW!"

            tap.background =
                rounded(pink, 30f)

            val startTime =
                System.currentTimeMillis()

            tap.setOnClickListener {

                val reaction =
                    System.currentTimeMillis() -
                            startTime

                tap.text = "$reaction ms"

                info.text =
                    "Your reaction time"

                ToastMessage(
                    "$reaction ms ⚡"
                )
            }

        }, delay)
    }

    // =========================================================
    // ANIMATED ROOT
    // =========================================================

    private fun animatedRoot(): FrameLayout {

        val frame = FrameLayout(this)

        val backgroundView =
            AnimatedBackground()

        val backgroundParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

        frame.addView(
            backgroundView,
            backgroundParams
        )

        val scroll =
            ScrollView(this)

        val scrollParams =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

        frame.addView(
            scroll,
            scrollParams
        )

        return frame
    }

    private fun contentRoot(
        frame: FrameLayout
    ): LinearLayout {

        val root =
            LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.gravity =
            Gravity.CENTER_HORIZONTAL

        root.setPadding(
            10,
            10,
            10,
            25
        )

        val scroll =
            frame.getChildAt(1) as ScrollView

        scroll.addView(
            root,
            ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
            )
        )

        return root
    }

    // =========================================================
    // GAME TITLE
    // =========================================================

    private fun addGameTitle(
        root: LinearLayout,
        title: String
    ) {

        val titleView = text(
            title,
            29f,
            white,
            Gravity.CENTER
        )

        titleView.typeface =
            Typeface.DEFAULT_BOLD

        root.addView(
            titleView,
            lp(-1, 60, 20, 35, 20, 10)
        )
    }

    // =========================================================
    // GAME BUTTON
    // =========================================================

    private fun gameButton(
        title: String,
        description: String,
        action: () -> Unit
    ): LinearLayout {

        val box =
            LinearLayout(this)

        box.orientation =
            LinearLayout.VERTICAL

        box.gravity =
            Gravity.CENTER_VERTICAL

        box.setPadding(
            22,
            8,
            22,
            8
        )

        box.background =
            rounded(cardColor, 25f)

        val titleView = text(
            title,
            19f,
            white,
            Gravity.CENTER_VERTICAL
        )

        titleView.typeface =
            Typeface.DEFAULT_BOLD

        val descView = text(
            description,
            13f,
            Color.LTGRAY,
            Gravity.CENTER_VERTICAL
        )

        box.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                35
            )
        )

        box.addView(
            descView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                30
            )
        )

        box.setOnClickListener {
            action()
        }

        return box
    }

    // =========================================================
    // BACK BUTTON
    // =========================================================

    private fun backButton(): Button {

        val button =
            Button(this)

        button.text =
            "← BACK"

        button.textSize =
            16f

        button.setTextColor(
            white
        )

        button.background =
            rounded(
                cardColor,
                25f
            )

        button.setOnClickListener {

            showHome()
        }

        button.layoutParams =
            lp(
                -1,
                60,
                35,
                25,
                35,
                25
            )

        return button
    }

    // =========================================================
    // TEXT
    // =========================================================

    private fun text(
        value: String,
        size: Float,
        color: Int,
        gravityValue: Int
    ): TextView {

        val view =
            TextView(this)

        view.text =
            value

        view.textSize =
            size

        view.setTextColor(
            color
        )

        view.gravity =
            gravityValue

        return view
    }

    // =========================================================
    // ROUNDED BACKGROUND
    // =========================================================

    private fun rounded(
        color: Int,
        radius: Float
    ): GradientDrawable {

        val drawable =
            GradientDrawable()

        drawable.setColor(
            color
        )

        drawable.cornerRadius =
            radius

        return drawable
    }

    // =========================================================
    // LAYOUT PARAMS
    // =========================================================

    private fun lp(
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): LinearLayout.LayoutParams {

        val params =
            LinearLayout.LayoutParams(
                if (width == -1) {
                    LinearLayout.LayoutParams.MATCH_PARENT
                } else {
                    width
                },
                height
            )

        params.setMargins(
            left,
            top,
            right,
            bottom
        )

        return params
    }

    // =========================================================
    // TOAST
    // =========================================================

    private fun ToastMessage(
        message: String
    ) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    // =========================================================
    // ANIMATED BACKGROUND
    // =========================================================

    private inner class AnimatedBackground :
        View(this) {

        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private var time = 0f

        private val animator =
            ValueAnimator.ofFloat(
                0f,
                360f
            )

        init {

            animator.duration =
                12000L

            animator.repeatCount =
                ValueAnimator.INFINITE

            animator.addUpdateListener {

                time =
                    it.animatedValue as Float

                invalidate()
            }

            animator.start()
        }

        override fun onDraw(
            canvas: Canvas
        ) {

            super.onDraw(canvas)

            val w =
                width.toFloat()

            val h =
                height.toFloat()

            paint.shader =
                LinearGradient(
                    0f,
                    0f,
                    w,
                    h,
                    Color.rgb(
                        8,
                        10,
                        24
                    ),
                    Color.rgb(
                        25,
                        8,
                        45
                    ),
                    Shader.TileMode.CLAMP
                )

            canvas.drawRect(
                0f,
                0f,
                w,
                h,
                paint
            )

            paint.shader =
                null

            drawOrb(
                canvas,
                w * 0.15f +
                        sin(
                            Math.toRadians(
                                time.toDouble()
                            )
                        ).toFloat() * 70f,
                h * 0.20f,
                160f,
                Color.argb(
                    60,
                    70,
                    220,
                    255
                )
            )

            drawOrb(
                canvas,
                w * 0.80f +
                        sin(
                            Math.toRadians(
                                (time * 1.4).toDouble()
                            )
                        ).toFloat() * 90f,
                h * 0.38f,
                190f,
                Color.argb(
                    50,
                    150,
                    90,
                    255
                )
            )

            drawOrb(
                canvas,
                w * 0.35f +
 
