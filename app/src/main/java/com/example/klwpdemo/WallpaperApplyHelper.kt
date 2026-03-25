package com.example.klwpdemo

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

/** 统一处理“设为壁纸/同步到桌面”的入口，避免首页和编辑器各自走不同逻辑。 */
object WallpaperApplyHelper {
    /** 判断当前桌面是否已经启用了我们自己的动态壁纸服务。 */
    fun isDemoWallpaperActive(context: Context): Boolean {
        val wallpaperInfo = WallpaperManager.getInstance(context).wallpaperInfo ?: return false
        return wallpaperInfo.packageName == context.packageName &&
            wallpaperInfo.serviceName == DemoWallpaperService::class.java.name
    }

    /** 还没启用时拉系统确认页；已经启用时直接提示当前项目会同步到桌面。 */
    fun applyOrPrompt(
        activity: Activity,
        projectName: String
    ) {
        if (isDemoWallpaperActive(activity)) {
            Toast.makeText(
                activity,
                activity.getString(R.string.wallpaper_apply_already_active_format, projectName),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val component = ComponentName(activity, DemoWallpaperService::class.java)
        val changeIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        }
        val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)

        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            runCatching {
                activity.startActivity(changeIntent)
            }.isSuccess
        } else {
            false
        }

        if (!started) {
            runCatching {
                activity.startActivity(fallbackIntent)
            }.onFailure {
                Toast.makeText(activity, R.string.wallpaper_apply_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
