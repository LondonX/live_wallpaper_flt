package com.LondonX.live_wallpaper_flt.service

import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import android.view.ViewConfiguration
import com.LondonX.live_wallpaper_flt.entity.Config
import com.google.gson.Gson
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.renderer.FlutterRenderer
import kotlinx.coroutines.*
import java.io.File

private const val TAG = "[live_wallpaper_flt]"

class LiveWallpaperFltService : WallpaperService() {
    private val config by lazy {
        val json = File(filesDir, "live_wallpaper_config.json").readText()
        Gson().fromJson(json, Config::class.java)
            ?: throw Exception("${TAG}invalid config file, make sure you called LiveWallpaperFlt.instance.applyConfig first.")
    }

    private val flutterEngine by lazy {
        FlutterEngine(this).apply {
            dartExecutor.executeDartEntrypoint(
                DartExecutor.DartEntrypoint(
                    FlutterInjector.instance().flutterLoader().findAppBundlePath(),
                    config.entryFunction,
                )
            )
        }
    }

    override fun onCreateEngine(): Engine {
        return object : Engine() {
            private val scope = MainScope()
            private var refreshJob: Job? = null

            override fun onCreate(surfaceHolder: SurfaceHolder?) {
                super.onCreate(surfaceHolder)
            }

            override fun onVisibilityChanged(visible: Boolean) {
                super.onVisibilityChanged(visible)
                if (visible) {
                    refreshJob = scope.launch {
                        while (true) {
                            delay(24)
                            applyViewportMetrics()
                        }
                    }
                    flutterEngine.lifecycleChannel.appIsResumed()
                    flutterEngine.renderer.startRenderingToSurface(surfaceHolder.surface, true)
                } else {
                    refreshJob?.cancel()
                    flutterEngine.lifecycleChannel.appIsPaused()
                    flutterEngine.renderer.stopRenderingToSurface()
                    flutterEngine.lifecycleChannel.appIsInactive()
                }
            }

            override fun onDestroy() {
                scope.cancel()
                flutterEngine.lifecycleChannel.appIsDetached()
                flutterEngine.renderer.stopRenderingToSurface()
                flutterEngine.destroy()
                super.onDestroy()
            }

            override fun onSurfaceChanged(
                holder: SurfaceHolder?, format: Int, width: Int, height: Int
            ) {
                super.onSurfaceChanged(holder, format, width, height)
                if (!flutterEngine.renderer.isDisplayingFlutterUi) return
                applyViewportMetrics(width, height)
            }

            private fun applyViewportMetrics(
                width: Int = resources.displayMetrics.widthPixels,
                height: Int = resources.displayMetrics.heightPixels,
            ) {
                val viewportMetrics = FlutterRenderer.ViewportMetrics()
                viewportMetrics.devicePixelRatio = resources.displayMetrics.density
                viewportMetrics.height = height
                viewportMetrics.width = width
                viewportMetrics.physicalTouchSlop =
                    ViewConfiguration.get(this@LiveWallpaperFltService).scaledTouchSlop
                flutterEngine.renderer.setViewportMetrics(viewportMetrics)
                flutterEngine.renderer.surfaceChanged(width, height)
            }
        }
    }
}

