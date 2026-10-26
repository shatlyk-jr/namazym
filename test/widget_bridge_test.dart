import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:namazym/main.dart';
import 'package:namazym/widget_bridge.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('namazym/widget');
  final calls = <MethodCall>[];

  setUp(() {
    calls.clear();
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return call.method == 'getProvince'
          ? 'mr'
          : call.method == 'requestPinWidget'
              ? true
              : null;
    });
  });

  test('bridge forwards calls', () async {
    final bridge = WidgetBridge();
    expect(await bridge.getProvince(), 'mr');
    await bridge.setProvince('lb');
    expect(await bridge.requestPinWidget(), isTrue);
    expect(calls.map((c) => c.method),
        ['getProvince', 'setProvince', 'requestPinWidget']);
    expect(calls[1].arguments, 'lb');
  });

  testWidgets('app shows picker with stored province', (tester) async {
    await tester.pumpWidget(MainApp(bridge: WidgetBridge()));
    await tester.pumpAndSettle();
    expect(find.text('Hello World!'), findsOneWidget);
    expect(find.text('Mary'), findsOneWidget);
  });
}
