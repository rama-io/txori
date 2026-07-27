package com.rama.txori.activities

import android.app.Fragment
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.rama.txori.CsActivity
import com.rama.txori.R
import com.rama.bohio.managers.ThemeManager
import com.rama.txori.widgets.WdNavbar

class MainActivity : CsActivity() {

    private lateinit var navbar: WdNavbar
    private lateinit var editBtn: FrameLayout
    private lateinit var closeBtn: FrameLayout
    private lateinit var flashOverlay: View
    private var currentPage: WdNavbar.Page = WdNavbar.Page.HOME

    private fun fragmentForPage(page: WdNavbar.Page): Fragment =
        fragmentManager.findFragmentByTag(page.name)
            ?: when (page) {
                WdNavbar.Page.HOME -> SessionFragment()
                WdNavbar.Page.STOPWATCH -> StopwatchFragment()
                WdNavbar.Page.TIMER -> TimerFragment()
                WdNavbar.Page.ROULETTE -> RouletteFragment()
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        applyEdgeToEdgePadding(root)
        applyCurrentTheme(root)

        navbar = findViewById(R.id.navbar)
        navbar.onNavigate = { page -> navigateTo(page) }

        flashOverlay = findViewById(R.id.flash_overlay)

        val openSettingsBtn = findViewById<FrameLayout>(R.id.open_settings)
        openSettingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }

        editBtn = findViewById(R.id.edit_btn)
        editBtn.setOnClickListener {
            val fragment = fragmentManager.findFragmentByTag(currentPage.name)
            (fragment as? EditModeToggle)?.onEditButtonClicked()
        }

        closeBtn = findViewById(R.id.close_btn)
        closeBtn.setOnClickListener {
            val fragment = fragmentManager.findFragmentByTag(currentPage.name)
            (fragment as? EditModeToggle)?.onCloseButtonClicked()
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
                .add(
                    R.id.content_container,
                    fragmentForPage(WdNavbar.Page.ROULETTE),
                    WdNavbar.Page.ROULETTE.name
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

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean(
                com.rama.bohio.objects.PrefKeys.SYSTEM_PREVENT_SLEEP,
                false
            )
        ) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PAGE, currentPage.name)
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
        setEditButtonVisible(false)
        setCloseButtonVisible(false)
        (fragmentManager.findFragmentByTag(page.name) as? EditModeToggle)?.syncTopBarButtons(this)
        ThemeManager.applyTheme(this, findViewById(R.id.root))
    }

    fun setEditButtonVisible(visible: Boolean) {
        editBtn.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setCloseButtonVisible(visible: Boolean) {
        closeBtn.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private val flashHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideFlashOverlay = Runnable { flashOverlay.visibility = View.GONE }

    /** Briefly flashes the screen white — used for the "Flash screen" notification setting. */
    fun flashScreen() {
        flashOverlay.bringToFront()
        flashHandler.removeCallbacks(hideFlashOverlay)
        flashOverlay.alpha = .5f
        flashOverlay.visibility = View.VISIBLE
        flashHandler.postDelayed(hideFlashOverlay, FLASH_DURATION_MS)
    }

    companion object {
        private const val KEY_PAGE = "current_page"
        private const val FLASH_DURATION_MS = 200L
    }
}