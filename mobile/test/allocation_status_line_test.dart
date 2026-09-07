import 'package:decimal/decimal.dart';
import 'package:fairshare/src/widgets/allocation_status_line.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows a completed allocation', (tester) async {
    await tester.pumpWidget(MaterialApp(home: Scaffold(
      body: AllocationStatusLine(label: 'Payer total', remaining: Decimal.zero))));
    expect(find.text('Payer total is complete'), findsOneWidget);
    expect(find.byIcon(Icons.check_circle), findsOneWidget);
  });

  testWidgets('shows the remaining percentage while editing', (tester) async {
    await tester.pumpWidget(MaterialApp(home: Scaffold(
      body: AllocationStatusLine(label: 'Percentage remaining', remaining: Decimal.parse('0.0001')))));
    expect(find.text('Percentage remaining: 0.0001'), findsOneWidget);
    expect(find.byIcon(Icons.info_outline), findsOneWidget);
  });
}
