import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';

class AllocationStatusLine extends StatelessWidget {
  const AllocationStatusLine({super.key, required this.label, required this.remaining});
  final String label;
  final Decimal remaining;

  @override
  Widget build(BuildContext context) {
    final complete = remaining == Decimal.zero;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(mainAxisAlignment: MainAxisAlignment.end, children: [
        Icon(complete ? Icons.check_circle : Icons.info_outline, size: 18,
          color: complete ? Colors.green : Colors.orange.shade800),
        const SizedBox(width: 6),
        Text(complete ? '$label is complete' : '$label: $remaining',
          style: TextStyle(color: complete ? Colors.green.shade800 : Colors.orange.shade900)),
      ]),
    );
  }
}

