package com.LondonX.live_wallpaper_flt.service

import android.content.res.Configuration
import android.service.wallpaper.WallpaperService
import android.text.format.DateFormat
import android.view.Surface
import android.view.SurfaceHolder
import android.view.ViewConfiguration
import com.LondonX.live_wallpaper_flt.entity.Config
import com.google.gson.Gson
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterJNI
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.renderer.FlutterRenderer
import io.flutter.embedding.engine.systemchannels.SettingsChannel
import kotlinx.coroutines.*
import java.io.File

private const val TAG = "[live_wallpaper_flt]"

class LiveWallpaperFltService : WallpaperService() {
    override fun onCreateEngine(): Engine {
        return object : Engine() {

            private val config by lazy {
                val json = File(filesDir, "live_wallpaper_config.json").readText()
                Gson().fromJson(json, Config::class.java)
                    ?: throw Exception("${TAG}invalid config file, make sure you called LiveWallpaperFlt.instance.applyConfig first.")
            }

            private val flutterEngine by lazy {
                val newEngine = FlutterEngine(this@LiveWallpaperFltService)
                newEngine.dartExecutor.executeDartEntrypoint(
                    DartExecutor.DartEntrypoint(
                        FlutterInjector.instance().flutterLoader().findAppBundlePath(),
                        config.entryFunction,
                    ),
                    listOf("is_started_from_wallpaper_service"),
                )
                return@lazy newEngine
            }

            private val scope = MainScope()
            private var refreshJob: Job? = null

            private var width: Int? = null
            private var height: Int? = null
            private var isDark = false

            override fun onCreate(surfaceHolder: SurfaceHolder?) {
                super.onCreate(surfaceHolder)
                isDark = isInDarkMode()
                applyPlatformDarkMode()
            }

            private fun surfaceAttachScope(f: Surface.() -> Unit) {
                val surface = surfaceHolder?.surface ?: return
                val flutterJNI = FlutterRenderer::class.java.getDeclaredField("flutterJNI")
                    .apply { isAccessible = true }.get(flutterEngine.renderer) as? FlutterJNI
                if (flutterJNI?.isAttached == true) {
                    f.invoke(surface)
                }
            }

            override fun onVisibilityChanged(visible: Boolean) {
                super.onVisibilityChanged(visible)
                if (visible) {
                    refreshJob = scope.launch {
                        while (true) {
                            delay(24)
                            surfaceAttachScope {
                                applyViewportMetrics()
                                if (isDark != isInDarkMode()) {
                                    isDark = isInDarkMode()
                                    applyPlatformDarkMode()
                                }
                            }
                        }
                    }
                    flutterEngine.lifecycleChannel.appIsResumed()
                    surfaceAttachScope {
                        flutterEngine.renderer.startRenderingToSurface(this, true)
                    }
                } else {
                    refreshJob?.cancel()
                    flutterEngine.lifecycleChannel.appIsPaused()
                    surfaceAttachScope {
                        flutterEngine.renderer.stopRenderingToSurface()
                    }
                    flutterEngine.lifecycleChannel.appIsInactive()
                }
            }

            override fun onDestroy() {
                refreshJob?.cancel()
                scope.cancel()
                flutterEngine.lifecycleChannel.appIsDetached()
                flutterEngine.destroy()
                super.onDestroy()
            }

            override fun onSurfaceChanged(
                holder: SurfaceHolder?, format: Int, width: Int, height: Int
            ) {
                super.onSurfaceChanged(holder, format, width, height)
                this.width = width
                this.height = height
                if (!flutterEngine.renderer.isDisplayingFlutterUi) return
                applyViewportMetrics()
            }

            private fun applyViewportMetrics() {
                val width = this.width ?: resources.displayMetrics.widthPixels
                val height = this.height ?: resources.displayMetrics.heightPixels
                val viewportMetrics = FlutterRenderer.ViewportMetrics()
                viewportMetrics.devicePixelRatio = resources.displayMetrics.density
                viewportMetrics.height = height
                viewportMetrics.width = width
                viewportMetrics.physicalTouchSlop =
                    ViewConfiguration.get(this@LiveWallpaperFltService).scaledTouchSlop
                surfaceAttachScope {
                    flutterEngine.renderer.setViewportMetrics(viewportMetrics)
                    flutterEngine.renderer.surfaceChanged(width, height)
                }
            }

            private fun isInDarkMode(): Boolean {
                return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }

            private fun applyPlatformDarkMode() {
                flutterEngine.settingsChannel.startMessage()
                    .setTextScaleFactor(resources.configuration.fontScale)
                    .setUse24HourFormat(DateFormat.is24HourFormat(this@LiveWallpaperFltService))
                    .setPlatformBrightness(
                        if (isInDarkMode()) {
                            SettingsChannel.PlatformBrightness.dark
                        } else {
                            SettingsChannel.PlatformBrightness.light
                        }
                    ).send()
            }
        }
    }
}

