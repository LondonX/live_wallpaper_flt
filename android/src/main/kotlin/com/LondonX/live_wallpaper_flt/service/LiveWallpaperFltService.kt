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
            override fun onCreate(surfaceHolder: SurfaceHolder?) {
                super.onCreate(surfaceHolder)
                Log.i(TAG, "onCreate: ")
            }

            override fun onVisibilityChanged(visible: Boolean) {
                super.onVisibilityChanged(visible)
                Log.i(TAG, "onVisibilityChanged: $visible")
                if (visible) {
                    val surface = surfaceHolder?.surface ?: return
                    flutterEngine.renderer.startRenderingToSurface(surface, true)
                } else {
                    flutterEngine.renderer.stopRenderingToSurface()
                }
            }

            override fun onDestroy() {
                Log.i(TAG, "onDestroy: ")
                flutterEngine.renderer.stopRenderingToSurface()
                super.onDestroy()
            }

            override fun onSurfaceChanged(
                holder: SurfaceHolder?, format: Int, width: Int, height: Int
            ) {
                super.onSurfaceChanged(holder, format, width, height)
                Log.i(TAG, "onSurfaceChanged: ${width}x$height")
                val surface = holder?.surface ?: return

                //TODO apply viewportMetrics调用时间
                val viewportMetrics = FlutterRenderer.ViewportMetrics()
                viewportMetrics.devicePixelRatio = resources.displayMetrics.density
                viewportMetrics.height = resources.displayMetrics.heightPixels
                viewportMetrics.width = resources.displayMetrics.widthPixels
                viewportMetrics.physicalTouchSlop =
                    ViewConfiguration.get(this@LiveWallpaperFltService).scaledTouchSlop
                flutterEngine.renderer.setViewportMetrics(viewportMetrics)

                flutterEngine.renderer.startRenderingToSurface(surface, true)
                flutterEngine.renderer.surfaceChanged(width, height)
            }
        }
    }
}

