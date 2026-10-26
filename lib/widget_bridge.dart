import 'package:flutter/services.dart';

/// Locations available in `times.db` (`pray_times.province`) and their display names.
const Map<String, String> widgetProvinces = {
  'ag': 'Ahal',
  'bn': 'Balkan',
  'dz': 'Daşoguz',
  'lb': 'Lebap',
  'mr': 'Mary',
};

/// Bridge to the native Android prayer-times home-screen widget.
class WidgetBridge {
  WidgetBridge([MethodChannel? channel])
      : _channel = channel ?? const MethodChannel('namazym/widget');

  final MethodChannel _channel;

  /// Province code currently used by widgets that have no own override.
  Future<String> getProvince() async =>
      (await _channel.invokeMethod<String>('getProvince')) ?? 'ag';

  /// Sets the location for every placed widget and refreshes them.
  Future<void> setProvince(String code) =>
      _channel.invokeMethod<void>('setProvince', code);

  /// Asks the launcher to pin the widget; false if the launcher does not support it.
  Future<bool> requestPinWidget() async =>
      (await _channel.invokeMethod<bool>('requestPinWidget')) ?? false;
}
