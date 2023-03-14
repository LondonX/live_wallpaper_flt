import 'package:flutter/services.dart';

class LiveWallpaperFlt {
  static final instance = LiveWallpaperFlt._();

  static bool isStartFromService(List<String> mainArgs) {
    return mainArgs.contains("is_started_from_wallpaper_service");
  }

  LiveWallpaperFlt._();

  final _channel = const MethodChannel('live_wallpaper_flt');

  Future<void> applyConfig({
    String entryFunction = "main",
  }) async {
    final arguments = {
      "entryFunction": entryFunction,
    };
    await _channel.invokeMethod("applyConfig", arguments);
  }

  Future<void> requestSetWallpaper() async {
    await _channel.invokeMethod("requestSetWallpaper");
  }
}
