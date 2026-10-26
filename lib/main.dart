import 'dart:io' show Platform;

import 'package:flutter/material.dart';

import 'widget_bridge.dart';

void main() {
  runApp(const MainApp());
}

class MainApp extends StatelessWidget {
  const MainApp({super.key, this.bridge});

  /// Injectable for tests.
  final WidgetBridge? bridge;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text('Hello World!'),
              if (bridge != null || Platform.isAndroid)
                WidgetSettings(bridge: bridge ?? WidgetBridge()),
            ],
          ),
        ),
      ),
    );
  }
}

/// Location picker and "add widget" button for the Android home-screen widget.
class WidgetSettings extends StatefulWidget {
  const WidgetSettings({super.key, required this.bridge});

  final WidgetBridge bridge;

  @override
  State<WidgetSettings> createState() => _WidgetSettingsState();
}

class _WidgetSettingsState extends State<WidgetSettings> {
  String _province = 'ag';

  @override
  void initState() {
    super.initState();
    widget.bridge.getProvince().then((p) {
      if (mounted) setState(() => _province = p);
    });
  }

  Future<void> _select(String? code) async {
    if (code == null) return;
    setState(() => _province = code);
    await widget.bridge.setProvince(code);
  }

  Future<void> _pin() async {
    final ok = await widget.bridge.requestPinWidget();
    if (!ok && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Long-press the home screen and add the widget.'),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          DropdownButton<String>(
            value: _province,
            items: [
              for (final e in widgetProvinces.entries)
                DropdownMenuItem(value: e.key, child: Text(e.value)),
            ],
            onChanged: _select,
          ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: _pin,
            child: const Text('Add prayer-times widget'),
          ),
        ],
      ),
    );
  }
}
