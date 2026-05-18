package com.rama.txori.activities

import android.app.Fragment
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import com.rama.txori.CsActivity
import com.rama.txori.R
import com.rama.txori.managers.FontManager
import com.rama.txori.managers.ThemeManager
import com.rama.txori.widgets.WdNavbar

class MainActivity : CsActivity() {

    private lateinit var navbar: WdNavbar
    private var currentPage: WdNavbar.Page = WdNavbar.Page.HOME
    private var isScreenLocked = false

    private fun fragmentForPage(page: WdNavbar.Page): Fragment =
        fragmentManager.findFragmentByTag(page.name)
            ?: when (page) {
                WdNavbar.Page.HOME -> HomeFragment()
                WdNavbar.Page.STOPWATCH -> StopwatchFragment()
                WdNavbar.Page.TIMER -> TimerFragment()
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        applyEdgeToEdgePadding(root)
        applyCurrentTheme(root)

        navbar = findViewById(R.id.navbar)
        navbar.onNavigate = { page -> navigateTo(page) }

        // Restore lock state after rotation
        if (savedInstanceState != null) {
            isScreenLocked = savedInstanceState.getBoolean(KEY_LOCK, false)
            if (isScreenLocked) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val lockView = findViewById<View>(R.id.lock_view)
        val lockIcon = findViewById<ImageView>(R.id.lock_icon)
        lockView.setOnClickListener {
            isScreenLocked = !isScreenLocked
            if (isScreenLocked) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                lockIcon.setImageResource(R.drawable.icon_lock_solid)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                lockIcon.setImageResource(R.drawable.icon_lock_open_solid)
            }

            Toast.makeText(
                this,
                if (isScreenLocked) getString(R.string.screen_stay_awake) else getString(R.string.screen_can_turn_off),
                Toast.LENGTH_SHORT
            ).show()
        }

        if (savedInstanceState == null) {
            // First launch: add all fragments up front.
            fragmentManager.beginTransaction()
                .add(
                    R.id.content_container,
                    fragmentForPage(WdNavbar.Page.TIMER),
                    WdNavbar.Page.TIMER.name
                )
                .add(
                    R.id.content_container,
                    fragmentForPage(WdNavbar.Page.STOPWATCH),
                    WdNavbar.Page.STOPWATCH.name
                )
                .add(
                    R.id.content_container,
                    fragmentForPage(WdNavbar.Page.HOME),
                    WdNavbar.Page.HOME.name
                )
                .commit()
            fragmentManager.executePendingTransactions()

            navigateTo(WdNavbar.Page.HOME)

            findViewById<View>(R.id.root).post {
                ThemeManager.applyTheme(this, findViewById(R.id.root))
            }
        } else {
            currentPage = savedInstanceState
                .getString(KEY_PAGE)
                ?.let { WdNavbar.Page.valueOf(it) }
                ?: WdNavbar.Page.HOME
            navigateTo(currentPage)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PAGE, currentPage.name)
        outState.putBoolean(KEY_LOCK, isScreenLocked)
    }

    fun navigateTo(page: WdNavbar.Page) {
        val tx = fragmentManager.beginTransaction()
        WdNavbar.Page.entries.forEach { p ->
            fragmentManager.findFragmentByTag(p.name)?.let { fragment ->
                if (p == page) tx.show(fragment) else tx.hide(fragment)
            }
        }
        tx.commit()
        fragmentManager.executePendingTransactions()

        currentPage = page
        navbar.setActivePage(page)
        ThemeManager.applyTheme(this, findViewById(R.id.root))
    }

    fun setNavbarVisible(visible: Boolean) {
        navbar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    companion object {
        private const val KEY_PAGE = "current_page"
        private const val KEY_LOCK = "screen_locked"
    }
}