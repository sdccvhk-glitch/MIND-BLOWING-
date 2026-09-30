package com.mindblow.app

import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val bgColor = Color.rgb(8, 11, 26)
    private val cardColor = Color.rgb(25, 31, 56)
    private val purple = Color.rgb(124, 77, 255)
    private val cyan = Color.rgb(0, 229, 255)
    private val white = Color.WHITE
    private val secondary = Color.rgb(169, 176, 199)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun showHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 20)
            setBackgroundColor(bgColor)
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 30)
        }

        val logo = TextView(this).apply {
            text = "✦ MINDBLOW"
            textSize = 28f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, 15, 0, 8)
        }
        content.addView(logo)

        val subtitle = TextView(this).apply {
            text = "Relax • Focus • Feel Better"
            textSize = 15f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 25)
        }
        content.addView(subtitle)

        val greeting = TextView(this).apply {
            text = "Refresh your mind ✨"
            textSize = 25f
            setTextColor(white)
            setPadding(0, 10, 0, 6)
        }
        content.addView(greeting)

        val description = TextView(this).apply {
            text = "Choose a short activity and take a break from your day."
            textSize = 15f
            setTextColor(secondary)
            setPadding(0, 0, 0, 20)
        }
        content.addView(description)

        val daily = createCard(
            "🌟  Daily Challenge",
            "A fresh challenge waiting for you",
            purple
        ) {
            showMessage("Daily Challenge coming next!")
        }
        content.addView(daily)

        val modesTitle = TextView(this).apply {
            text = "Mind Modes"
            textSize = 21f
            setTextColor(white)
            setPadding(0, 25, 0, 12)
        }
        content.addView(modesTitle)

        content.addView(
            createCard("🧩  Puzzle", "Train your problem solving", purple) {
                showMessage("Puzzle mode coming next!")
            }
        )

        content.addView(
            createCard("🧠  Memory", "Improve your memory", cyan) {
                showMessage("Memory mode coming next!")
            }
        )

        content.addView(
            createCard("🎯  Focus", "Build your concentration", Color.rgb(255, 170, 60)) {
                showMessage("Focus mode coming next!")
            }
        )

        content.addView(
            createCard("🌿  Relax", "Slow down and breathe", Color.rgb(70, 210, 150)) {
                showMessage("Relax mode coming next!")
            }
        )

        val statsTitle = TextView(this).apply {
            text = "Your Progress"
            textSize = 21f
            setTextColor(white)
            setPadding(0, 25, 0, 12)
        }
        content.addView(statsTitle)

        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        stats.addView(
            createStat("🔥", "0", "Streak")
        )

        stats.addView(
            createStat("⭐", "0", "XP")
        )

        stats.addView(
            createStat("🏆", "0", "Badges")
        )

        content.addView(stats)

        val play = Button(this).apply {
            text = "PLAY NOW  →"
            textSize = 17f
            setTextColor(white)
            background = roundedBackground(purple, 28)
            setPadding(20, 5, 20, 5)

            setOnClickListener {
                showMessage("Choose a Mind Mode above!")
            }
        }

        val playParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            60
        )
        playParams.setMargins(0, 30, 0, 10)
        content.addView(play, playParams)

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 4)
            setBackgroundColor(cardColor)
        }

        navigation.addView(
            navButton("⌂\nHome") {
                showHome()
            }
        )

        navigation.addView(
            navButton("🏆\nProgress") {
                showMessage("Progress screen coming next!")
            }
        )

        navigation.addView(
            navButton("👤\nProfile") {
                showMessage("Profile screen coming next!")
            }
        )

        navigation.addView(
            navButton("⚙\nSettings") {
                showMessage("Settings screen coming next!")
            }
        )

        root.addView(
            navigation,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                75
            )
        )

        setContentView(root)
    }

    private fun createCard(
        title: String,
        subtitle: String,
        accent: Int,
        action: () -> Unit
    ): LinearLayout {

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 18, 22, 18)
            background = roundedBackground(cardColor, 22)
            isClickable = true
            elevation = 8f

            setOnClickListener {
                action()
            }
        }

        val titleView = TextView(this).apply {
            text = title
            textSize = 19f
            setTextColor(white)
        }

        val subtitleView = TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(secondary)
            setPadding(0, 7, 0, 0)
        }

        card.addView(titleView)
        card.addView(subtitleView)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            88
        )
        params.setMargins(0, 8, 0, 8)
        card.layoutParams = params

        return card
    }

    private fun createStat(
        icon: String,
        number: String,
        label: String
    ): LinearLayout {

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(10, 12, 10, 12)
            background = roundedBackground(cardColor, 18)
        }

        val iconView = TextView(this).apply {
            text = icon
            textSize = 22f
            gravity = Gravity.CENTER
       
