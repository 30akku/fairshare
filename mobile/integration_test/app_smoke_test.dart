import 'package:fairshare/src/app.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  const enabled = bool.fromEnvironment('RUN_FIREBASE_TESTS');

  testWidgets('launches the configured FairShare application', (tester) async {
    await tester.pumpWidget(const ProviderScope(child: FairShareApp()));
    await tester.pumpAndSettle();
    expect(find.text('FairShare'), findsWidgets);
  }, skip: !enabled ? 'Requires configured Firebase Android test credentials' : false);
}

