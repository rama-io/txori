package com.rama.txori.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.rama.txori.CsActivity
import com.rama.txori.R
import com.rama.bohio.R as BohioR

class AboutActivity : CsActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.view_about)

        val root = findViewById<View>(R.id.about_root)
        applyEdgeToEdgePadding(root)
        applyCurrentTheme(root)

        val closeButton = findViewById<Button>(R.id.close_button)
        closeButton.setOnClickListener {
            finish()
        }

        val discordButton = findViewById<Button>(R.id.discord_button)
        discordButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/zFFupY8PFE"))
            startActivity(intent)
        }

        val version = packageManager.getPackageInfo(packageName, 0).versionCode
        val nameView = findViewById<TextView>(R.id.name_version)
        nameView.text = getString(BohioR.string.name_version, getString(R.string.app_name), version)
    }
}