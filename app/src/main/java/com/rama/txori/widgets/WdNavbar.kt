package com.rama.txori.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.rama.txori.R

class WdNavbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    enum class Page { HOME, STOPWATCH, TIMER, ABOUT }

    private val homeBtn: FrameLayout
    private val stopwatchBtn: FrameLayout
    private val timerBtn: FrameLayout
    private val aboutBtn: FrameLayout
    private val selectedColor = resources.getColor(R.color.button_1_selected)
    private val inactiveColor = resources.getColor(R.color.button_1)

    var onNavigate: ((Page) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.wd_navbar, this, true)

        homeBtn = findViewById(R.id.home_nav)
        stopwatchBtn = findViewById(R.id.stopwatch_nav)
        timerBtn = findViewById(R.id.timer_nav)
        aboutBtn = findViewById(R.id.about_nav)

        homeBtn.setOnClickListener { onNavigate?.invoke(Page.HOME) }
        stopwatchBtn.setOnClickListener { onNavigate?.invoke(Page.STOPWATCH) }
        timerBtn.setOnClickListener { onNavigate?.invoke(Page.TIMER) }
        aboutBtn.setOnClickListener { onNavigate?.invoke(Page.ABOUT) }
    }

    fun setActivePage(page: Page) {
        val allButtons = listOf(homeBtn, stopwatchBtn, timerBtn, aboutBtn)
        val activeBtn = when (page) {
            Page.HOME -> homeBtn
            Page.STOPWATCH -> stopwatchBtn
            Page.TIMER -> timerBtn
            Page.ABOUT -> aboutBtn
        }
        allButtons.forEach { btn ->
            val isSelected = btn === activeBtn
            btn.isEnabled = !isSelected
            btn.setBackgroundColor(
                if (isSelected) selectedColor else inactiveColor
            )
        }
    }
}
