package com.LondonX.live_wallpaper_flt

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import com.LondonX.live_wallpaper_flt.service.LiveWallpaperFltService
import com.google.gson.Gson
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File

/** LiveWallpaperFltPlugin */
class LiveWallpaperFltPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    var activity: Activity? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "live_wallpaper_flt")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "applyConfig" -> {
                val json = Gson().toJson(call.arguments)
                applyConfig(json)
                result.success(true)
            }
            "requestSetWallpaper" -> {
                activityScope {
                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    intent.putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this, LiveWallpaperFltService::class.java)
                    )
//                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    this.startActivity(intent)
                    result.success(true)
                }
            }
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun applyConfig(json: String) {
        activityScope {
            File(filesDir, "live_wallpaper_config.json").writeText(json)
        }
    }

    private fun activityScope(f: Activity.() -> Unit) {
        f.invoke(activity ?: return)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivity() {
        activity = null
    }
}
