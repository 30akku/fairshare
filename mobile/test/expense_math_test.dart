import 'package:decimal/decimal.dart';
import 'package:fairshare/src/expense_math.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('multiple payer contributions must consume the full total', () {
    expect(ExpenseMath.remaining('1200.00', ['700.00', '500.00']), Decimal.zero);
    expect(ExpenseMath.remaining('1200.00', ['700.00', '499.00']), Decimal.fromInt(1));
  });

  test('percentage shares must consume exactly one hundred percent', () {
    expect(ExpenseMath.percentageRemaining(['50', '30', '20']), Decimal.zero);
    expect(ExpenseMath.percentageRemaining(['33.3333', '33.3333', '33.3333']), Decimal.parse('0.0001'));
  });

  test('invalid input is safely treated as zero while typing', () {
    expect(ExpenseMath.parse('not a number'), Decimal.zero);
    expect(ExpenseMath.parse(''), Decimal.zero);
  });
}
