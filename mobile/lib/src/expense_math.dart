import 'package:decimal/decimal.dart';

class ExpenseMath {
  const ExpenseMath._();

  static Decimal parse(String? value) {
    try { return Decimal.parse(value?.trim().isNotEmpty == true ? value!.trim() : '0'); }
    catch (_) { return Decimal.zero; }
  }

  static Decimal remaining(String total, Iterable<String?> allocations) {
    return parse(total) - allocations.fold(Decimal.zero, (sum, value) => sum + parse(value));
  }

  static Decimal percentageRemaining(Iterable<String?> percentages) {
    return Decimal.fromInt(100) - percentages.fold(Decimal.zero, (sum, value) => sum + parse(value));
  }
}

