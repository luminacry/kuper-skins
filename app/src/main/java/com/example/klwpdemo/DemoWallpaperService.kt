package com.example.klwpdemo

import android.annotation.TargetApi
import android.app.WallpaperColors
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.example.klwpdemo.project.ProjectSessionManager
import com.example.klwpdemo.runtime.DemoWallpaperRuntime
import com.example.klwpdemo.runtime.RuntimeViewport

/** 真正提供动态壁纸绘制入口的系统 Service。 */
class DemoWallpaperService : WallpaperService() {

    /** 为系统返回壁纸引擎实例。 */
    override fun onCreateEngine(): Engine = DemoEngine()

    /** 负责驱动帧循环、触摸输入和壁纸绘制的引擎实现。 */
    private inner class DemoEngine : Engine() {
        /** 约 30fps 的刷新间隔。 */
        private val frameDelayMs = 33L
        /** 壁纸运行时，负责生成文档并执行渲染。 */
        private val runtime = DemoWallpaperRuntime(this@DemoWallpaperService)
        /** 当前壁纸可用视口，供会话层回源加载项目时复用。 */
        private var currentViewport = RuntimeViewport(width = 0, height = 0)

        /** 主线程调度器，用于驱动持续刷新。 */
        private val handler = Handler(Looper.getMainLooper())
        /** 可见时重复执行的绘制任务。 */
        private val drawRunner = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) {
                    handler.postDelayed(this, frameDelayMs)
                }
            }
        }

        /** 当前壁纸是否处于可见状态。 */
        private var visible = false
        /** 编辑器修改活动项目时，让壁纸服务马上吃到最新文档。 */
        private val activeProjectListener = ProjectSessionManager.ActiveProjectListener { project ->
            project ?: return@ActiveProjectListener
            runtime.setBaseDocument(project.document)
            if (visible) {
                drawFrame()
            }
        }

        /** 初始化引擎时打开触摸和偏移量监听。 */
        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(true)
            ProjectSessionManager.registerActiveProjectListener(activeProjectListener)
        }

        /** 根据可见性决定是否继续帧循环。 */
        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(drawRunner)
            if (visible) {
                syncRuntimeDocumentFromSession()
                drawFrame()
                handler.postDelayed(drawRunner, frameDelayMs)
            }
        }

        /** 画布尺寸变化后刷新视口。 */
        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            currentViewport = RuntimeViewport(width = width, height = height)
            runtime.updateViewport(width, height)
            syncRuntimeDocumentFromSession()
            drawFrame()
        }

        /** 接收桌面滑屏偏移量，驱动视差效果。 */
        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(
                xOffset,
                yOffset,
                xOffsetStep,
                yOffsetStep,
                xPixelOffset,
                yPixelOffset
            )
            runtime.updateOffset(xOffset)
            drawFrame()
        }

        /** 触摸时记录采样，用于生成波纹动画。 */
        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                runtime.registerTouch(event.x, event.y, event.eventTime)
                drawFrame()
            }
        }

        /** Surface 销毁时停止刷新。 */
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunner)
        }

        /** 引擎销毁时清理回调。 */
        override fun onDestroy() {
            handler.removeCallbacks(drawRunner)
            ProjectSessionManager.unregisterActiveProjectListener(activeProjectListener)
            super.onDestroy()
        }

        /** 返回系统用于提取壁纸主色调的颜色样本。 */
        @TargetApi(Build.VERSION_CODES.O_MR1)
        override fun onComputeColors(): WallpaperColors {
            return WallpaperColors(
                Color.valueOf(Color.parseColor("#10203B")),
                Color.valueOf(Color.parseColor("#224870")),
                Color.valueOf(Color.parseColor("#F7C948"))
            )
        }

        /** 尝试锁定 Surface 并绘制当前帧。 */
        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            val surface = holder.surface ?: return
            if (!surface.isValid) {
                return
            }
            syncRuntimeDocumentFromSession()

            var canvas: Canvas? = null
            try {
                canvas = holder.lockHardwareCanvas()
                val frameCanvas = canvas ?: return
                runtime.render(
                    canvas = frameCanvas,
                    uptimeMillis = SystemClock.uptimeMillis(),
                    wallClockMillis = System.currentTimeMillis()
                )
            } catch (_: RuntimeException) {
                // Surface could disappear between validation and lock.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: RuntimeException) {
                        // Surface was released while drawing.
                    }
                }
            }
        }

        /** 壁纸端优先吃活动项目快照，内存没有时再从本地项目文件回源。 */
        private fun syncRuntimeDocumentFromSession() {
            val project = if (currentViewport.isReady) {
                ProjectSessionManager.resolveActiveProject(
                    context = this@DemoWallpaperService,
                    viewport = currentViewport
                )
            } else {
                ProjectSessionManager.resolveActiveProject(this@DemoWallpaperService)
            }
            project?.let { runtime.setBaseDocument(it.document) }
        }
    }
}
