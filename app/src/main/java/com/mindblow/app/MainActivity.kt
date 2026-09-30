package com.mindblow.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val backgroundColor = Color.rgb(8, 11, 26)
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

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(backgroundColor)

        val scrollView = ScrollView(this)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(24, 30, 24, 30)

        val logo = TextView(this)
        logo.text = "✦ MINDBLOW"
        logo.textSize = 28f
        logo.setTextColor(white)
        logo.gravity = Gravity.CENTER

        content.addView(
            logo,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                60
            )
        )

        val subtitle = TextView(this)
        subtitle.text = "Relax • Focus • Feel Better"
        subtitle.textSize = 15f
        subtitle.setTextColor(cyan)
        subtitle.gravity = Gravity.CENTER

        content.addView(
            subtitle,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                45
            )
        )

        val title = TextView(this)
        title.text = "Refresh your mind ✨"
        title.textSize = 25f
        title.setTextColor(white)
        title.setPadding(0, 20, 0, 5)

        content.addView(title)

        val description = TextView(this)
        description.text =
            "Choose a short activity and take a break from your day."
        description.textSize = 15f
        description.setTextColor(secondary)

        content.addView(description)

        content.addView(
            createCard(
                "🌟  Daily Challenge",
                "A fresh challenge waiting for you",
                purple
            ) {
                showMessage("Daily Challenge coming soon!")
            }
        )

        val modes = TextView(this)
        modes.text = "Mind Modes"
        modes.textSize = 21f
        modes.setTextColor(white)
        modes.setPadding(0, 25, 0, 10)

        content.addView(modes)

        content.addView(
            createCard(
                "🧩  Puzzle",
                "Train your problem solving",
                purple
            ) {
                showMessage("Puzzle mode coming soon!")
            }
        )

        content.addView(
            createCard(
                "🧠  Memory",
                "Improve your memory",
                cyan
            ) {
                showMessage("Memory mode coming soon!")
            }
        )

        content.addView(
            createCard(
                "🎯  Focus",
                "Build your concentration",
                Color.rgb(255, 170, 60)
            ) {
                showMessage("Focus mode coming soon!")
            }
        )

        content.addView(
            createCard(
                "🌿  Relax",
                "Slow down and breathe",
                Color.rgb(70, 210, 150)
            ) {
                showMessage("Relax mode coming soon!")
            }
        )

        val progressTitle = TextView(this)
        progressTitle.text = "Your Progress"
        progressTitle.textSize = 21f
        progressTitle.setTextColor(white)
        progressTitle.setPadding(0, 25, 0, 10)

        content.addView(progressTitle)

        val stats = LinearLayout(this)
        stats.orientation = LinearLayout.HORIZONTAL

        stats.addView(createStat("🔥", "0", "Streak"))
        stats.addView(createStat("⭐", "0", "XP"))
        stats.addView(createStat("🏆", "0", "Badges"))

        content.addView(stats)

        val playButton = Button(this)
        playButton.text = "PLAY NOW  →"
        playButton.textSize = 17f
        playButton.setTextColor(white)
        playButton.background = roundedBackground(purple, 28)

        playButton.setOnClickListener {
            showMessage("Choose a Mind Mode!")
        }

        val playParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            60
        )
        playParams.setMargins(0, 30, 0, 10)

        content.addView(playButton, playParams)

        scrollView.addView(content)

        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val navigation = LinearLayout(this)
        navigation.orientation = LinearLayout.HORIZONTAL
        navigation.gravity = Gravity.CENTER
        navigation.setBackgroundColor(cardColor)

        navigation.addView(
            navButton("⌂\nHome") {
                showHome()
            }
        )

        navigation.addView(
            navButton("🏆\nProgress") {
                showMessage("Progress coming soon!")
            }
        )

        navigation.addView(
            navButton("👤\nProfile") {
                showMessage("Profile coming soon!")
            }
        )

        navigation.addView(
            navButton("⚙\nSettings") {
                showMessage("Settings coming soon!")
            }
        )

        root.addView(
            navigation,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                70
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

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(22, 15, 22, 15)
        card.background = roundedBackground(cardColor, 22)
        card.elevation = 6f

        val titleView = TextView(this)
        titleView.text = title
        titleView.textSize = 19f
        titleView.setTextColor(white)

        val subtitleView = TextView(this)
        subtitleView.text = subtitle
        subtitleView.textSize = 13f
        subtitleView.setTextColor(secondary)
        subtitleView.setPadding(0, 6, 0, 0)

        card.addView(titleView)
        card.addView(subtitleView)

        card.setOnClickListener {
            action()
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            85
        )
        params.setMargins(0, 7, 0, 7)

        card.layoutParams = params

        return card
    }

    private fun createStat(
        icon: String,
        number: String,
        label: String
    ): LinearLayout {

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setPadding(5, 8, 5, 8)
        box.background = roundedBackground(cardColor, 18)

        val iconView = TextView(this)
        iconView.text = icon
        iconView.textSize = 22f
        iconView.gravity = Gravity.CENTER

        val numberView = TextView(this)
        numberView.text = number
        numberView.textSize = 19f
        numberView.setTextColor(white)
        numberView.gravity = Gravity.CENTER

        val labelView = TextView(this)
        labelView.text = label
        labelView.textSize = 12f
        labelView.setTextColor(secondary)
        labelView.gravity = Gravity.CENTER

        box.addView(iconView)
        box.addView(numberView)
        box.addView(labelView)

        val params = LinearLayout.LayoutParams(
            0,
            95,
            1f
        )
        params.setMargins(4, 0, 4, 0)

        box.layoutParams = params

        return box
    }

    private fun navButton(
        text: String,
        action: () -> Unit
    ): TextView {

        val button = TextView(this)

        button.text = text
        button.textSize = 12f
        button.setTextColor(white)
        button.gravity = Gravity.CENTER
        button.setPadding(5, 5, 5, 5)

        button.setOnClickListener {
            action()
        }

        button.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.MATCH_PARENT,
            1f
        )

        return button
    }

    private fun roundedBackground(
        color: Int,
        radius: Int
    ): GradientDrawable {

        val drawable = GradientDrawable()
        drawable.setColor(color)
        drawable.cornerRadius = radius.toFloat()

        return drawable
    }

    private fun showMessage(message: String) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }
}
