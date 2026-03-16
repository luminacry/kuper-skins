package com.example.klwpdemo

import android.app.WallpaperColors
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.text.format.DateFormat
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.util.Locale
import kotlin.math.min
import kotlin.math.sin

class DemoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = DemoEngine()

    private inner class DemoEngine : Engine() {
        private val frameDelayMs = 33L
        private val rippleDurationMs = 650L

        private val handler = Handler(Looper.getMainLooper())
        private val drawRunner = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) {
                    handler.postDelayed(this, frameDelayMs)
                }
            }
        }

        private val backgroundPaint = Paint()
        private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7C948")
        }
        private val orbCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 54f
            isFakeBoldText = true
            isSubpixelText = true
        }
        private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C7D2FE")
            textSize = 28f
            isSubpixelText = true
        }
        private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = Color.WHITE
        }
        private val hazePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(36, 255, 255, 255)
        }
        private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(55, 255, 255, 255)
        }
        private val bottomPanel = RectF()

        private var visible = false
        private var width = 0
        private var height = 0
        private var xOffset = 0.5f
        private var touchX = -1f
        private var touchY = -1f
        private var rippleStartMillis = -1L

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(true)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(drawRunner)
            if (visible) {
                drawFrame()
                handler.postDelayed(drawRunner, frameDelayMs)
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            backgroundPaint.shader = createBackgroundShader(width, height)
            drawFrame()
        }

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
            this.xOffset = xOffset
            drawFrame()
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            val action = event.actionMasked
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                touchX = event.x
                touchY = event.y
                rippleStartMillis = SystemClock.uptimeMillis()
                drawFrame()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            visible = false
            handler.removeCallbacks(drawRunner)
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawRunner)
            super.onDestroy()
        }

        override fun onComputeColors(): WallpaperColors? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                WallpaperColors(
                    Color.valueOf(Color.parseColor("#10203B")),
                    Color.valueOf(Color.parseColor("#224870")),
                    Color.valueOf(Color.parseColor("#F7C948"))
                )
            } else {
                super.onComputeColors()
            }
        }

        private fun createBackgroundShader(width: Int, height: Int): Shader {
            return LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                intArrayOf(
                    Color.parseColor("#0B132B"),
                    Color.parseColor("#1C2541"),
                    Color.parseColor("#224870")
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }

        private fun drawFrame() {
            val holder = surfaceHolder ?: return
            val surface = holder.surface ?: return
            if (!surface.isValid) {
                return
            }

            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }

                if (canvas == null) {
                    return
                }

                val now = SystemClock.uptimeMillis()
                val pulse = ((sin(now / 450.0) + 1.0) * 0.5).toFloat()
                val orbitY = height * 0.44f + sin(now / 1200.0).toFloat() * height * 0.03f
                val orbitRadius = min(width, height) * (0.11f + pulse * 0.025f)
                val orbitX = lerp(width * 0.18f, width * 0.82f, xOffset)

                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
                canvas.drawCircle(width * 0.22f, height * 0.18f, width * 0.19f, hazePaint)
                canvas.drawCircle(width * 0.78f, height * 0.76f, width * 0.25f, hazePaint)

                bottomPanel.set(
                    width * 0.06f,
                    height * 0.68f,
                    width * 0.94f,
                    height * 0.92f
                )
                canvas.drawRoundRect(bottomPanel, 28f, 28f, panelPaint)

                canvas.drawCircle(orbitX, orbitY, orbitRadius, orbPaint)
                canvas.drawCircle(orbitX, orbitY, orbitRadius * 0.28f, orbCorePaint)

                if (touchX >= 0f && touchY >= 0f && rippleStartMillis > 0L) {
                    val rippleProgress =
                        (SystemClock.uptimeMillis() - rippleStartMillis) / rippleDurationMs.toFloat()
                    if (rippleProgress <= 1f) {
                        ripplePaint.alpha = ((1f - rippleProgress) * 180).toInt()
                        canvas.drawCircle(
                            touchX,
                            touchY,
                            rippleProgress * min(width, height) * 0.18f,
                            ripplePaint
                        )
                    }
                }

                val textLeft = width * 0.11f
                canvas.drawText(
                    "KLWP minimal live wallpaper demo",
                    textLeft,
                    height * 0.76f,
                    textPaint
                )

                val timeText = DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString()
                canvas.drawText("Time  $timeText", textLeft, height * 0.82f, detailPaint)
                canvas.drawText(
                    String.format(Locale.US, "Home offset  %.2f", xOffset),
                    textLeft,
                    height * 0.86f,
                    detailPaint
                )
                canvas.drawText(
                    "Swipe home pages and tap anywhere to test input.",
                    textLeft,
                    height * 0.90f,
                    detailPaint
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

        private fun lerp(start: Float, end: Float, amount: Float): Float {
            return start + (end - start) * amount
        }
    }
}
