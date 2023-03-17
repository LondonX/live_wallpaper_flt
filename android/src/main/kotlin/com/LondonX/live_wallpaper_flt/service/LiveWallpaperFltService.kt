package com.LondonX.live_wallpaper_flt.service

import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.text.format.DateFormat
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
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onCreateEngine(): Engine {
        return object : Engine() {
            private val config by lazy {
                val json = File(filesDir, "live_wallpaper_config.json").readText()
                Gson().fromJson(json, Config::class.java)
                    ?: throw Exception("${TAG}invalid config file, make sure you called LiveWallpaperFlt.instance.applyConfig first.")
            }
            private val flutterEngine = FlutterEngine(this@LiveWallpaperFltService)

            private val scope = CoroutineScope(Dispatchers.Main)
            private var refreshJob: Job? = null

            private var width: Int? = null
            private var height: Int? = null
            private var isDark = false

            override fun onCreate(surfaceHolder: SurfaceHolder?) {
                super.onCreate(surfaceHolder)
                flutterEngine.dartExecutor.executeDartEntrypoint(
                    DartExecutor.DartEntrypoint(
                        FlutterInjector.instance().flutterLoader().findAppBundlePath(),
                        config.entryFunction,
                    ),
                    listOf("is_started_from_wallpaper_service"),
                )
                isDark = isInDarkMode()
                applyPlatformDarkMode()
            }

            private fun enginScope(f: FlutterEngine.() -> Unit) {
                uiHandler.post {
                    f.invoke(flutterEngine)
                }
            }

            private fun FlutterEngine.isAttached(): Boolean {
                surfaceHolder?.surface ?: return false
                val flutterJNI = FlutterRenderer::class.java.getDeclaredField("flutterJNI")
                    .apply { isAccessible = true }.get(renderer) as? FlutterJNI
                return flutterJNI?.isAttached == true
            }

            override fun onVisibilityChanged(visible: Boolean) {
                super.onVisibilityChanged(visible)
                if (visible) {
                    refreshJob = scope.launch {
                        while (true) {
                            delay(24)
                            if (flutterEngine.isAttached()) {
                                applyViewportMetrics()
                                if (isDark != isInDarkMode()) {
                                    isDark = isInDarkMode()
                                    applyPlatformDarkMode()
                                }
                            }
                        }
                    }
                    enginScope {
                        lifecycleChannel.appIsResumed()
                        if (flutterEngine.isAttached()) {
                            renderer.startRenderingToSurface(surfaceHolder.surface!!, true)
                        }
                    }
                } else {
                    refreshJob?.cancel()
                    enginScope {
                        lifecycleChannel.appIsPaused()
                        if (flutterEngine.isAttached()) {
                            renderer.stopRenderingToSurface()
                        }
                        lifecycleChannel.appIsInactive()
                    }
                }
            }

            override fun onDestroy() {
                refreshJob?.cancel()
                enginScope {
                    lifecycleChannel.appIsDetached()
                    destroy()
                }
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
                enginScope {
                    if (flutterEngine.isAttached()) {
                        renderer.setViewportMetrics(viewportMetrics)
                        renderer.surfaceChanged(width, height)
                    }
                }
            }

            private fun isInDarkMode(): Boolean {
                return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }

            private fun applyPlatformDarkMode() {
                enginScope {
                    settingsChannel.startMessage()
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
}

