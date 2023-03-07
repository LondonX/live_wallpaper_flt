import 'package:flutter/services.dart';

class LiveWallpaperFlt {
  static final instance = LiveWallpaperFlt._();

  LiveWallpaperFlt._();

  final _channel = const MethodChannel('live_wallpaper_flt');

  Future<void> applyConfig({
    required String entryFunction,
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
