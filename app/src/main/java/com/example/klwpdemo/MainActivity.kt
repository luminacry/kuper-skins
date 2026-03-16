package com.example.klwpdemo

import android.app.Activity
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val openPreviewButton = findViewById<Button>(R.id.button_open_preview)
        val openChooserButton = findViewById<Button>(R.id.button_open_chooser)

        openPreviewButton.setOnClickListener { openDirectPreview() }
        openChooserButton.setOnClickListener { openWallpaperChooser() }
    }

    private fun openDirectPreview() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            openWallpaperChooser()
            return
        }

        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this@MainActivity, DemoWallpaperService::class.java)
            )
        }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                R.string.direct_preview_unavailable,
                Toast.LENGTH_SHORT
            ).show()
            openWallpaperChooser()
        }
    }

    private fun openWallpaperChooser() {
        try {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                R.string.chooser_unavailable,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
