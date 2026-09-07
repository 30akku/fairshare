import 'package:decimal/decimal.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../api.dart';
import '../expense_math.dart';
import '../models.dart';
import '../providers.dart';
import '../widgets/allocation_status_line.dart';

enum SplitMode { equal, exact, percentage }

class ExpenseFormScreen extends ConsumerStatefulWidget {
  const ExpenseFormScreen({super.key, required this.groupId});
  final String groupId;
  @override ConsumerState<ExpenseFormScreen> createState() => _ExpenseFormScreenState();
}

class _ExpenseFormScreenState extends ConsumerState<ExpenseFormScreen> {
  final _description = TextEditingController();
  final _total = TextEditingController();
  final Map<String, TextEditingController> _payerAmounts = {};
  final Map<String, TextEditingController> _splitValues = {};
  final Set<String> _payers = {};
  final Set<String> _participants = {};
  String _currency = 'INR';
  SplitMode _mode = SplitMode.equal;
  DateTime _date = DateTime.now();
  bool _recurring = false;
  String _frequency = 'MONTHLY';
  bool _initialized = false;
  bool _busy = false;

  @override void dispose() {
    _description.dispose(); _total.dispose();
    for (final controller in [..._payerAmounts.values, ..._splitValues.values]) { controller.dispose(); }
    super.dispose();
  }

  @override Widget build(BuildContext context) {
    final membersValue = ref.watch(membersProvider(widget.groupId));
    return Scaffold(appBar: AppBar(title: const Text('Add expense', style: TextStyle(fontWeight: FontWeight.w800))),
      body: membersValue.when(loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: Text(error.toString())), data: (members) {
          _initialize(members);
          return ListView(padding: const EdgeInsets.fromLTRB(16, 12, 16, 120), children: [
            TextField(controller: _description, maxLength: 200, decoration: const InputDecoration(labelText: 'What was this for?', prefixIcon: Icon(Icons.edit_outlined))),
            Row(children: [
              SizedBox(width: 110, child: DropdownButtonFormField<String>(initialValue: _currency,
                items: const ['INR', 'USD', 'EUR', 'GBP', 'JPY'].map((value) => DropdownMenuItem(value: value, child: Text(value))).toList(),
                onChanged: (value) => setState(() => _currency = value!), decoration: const InputDecoration(labelText: 'Currency'))),
              const SizedBox(width: 10), Expanded(child: TextField(controller: _total, onChanged: (_) => setState(() {}),
                keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: const InputDecoration(labelText: 'Total amount'))),
            ]), const SizedBox(height: 12),
            ListTile(contentPadding: EdgeInsets.zero, leading: const Icon(Icons.calendar_today_outlined), title: const Text('Expense date'),
              trailing: TextButton(onPressed: _pickDate, child: Text(DateFormat.yMMMd().format(_date)))),
            const Divider(height: 32),
            _heading('Who paid?', Icons.payments_outlined), const SizedBox(height: 6),
            ...members.map((member) => _payerTile(member)),
            AllocationStatusLine(label: 'Payer total', remaining: _remainingPayer()),
            const Divider(height: 32),
            _heading('How should it be split?', Icons.call_split), const SizedBox(height: 10),
            SegmentedButton<SplitMode>(segments: const [
              ButtonSegment(value: SplitMode.equal, label: Text('Equal')),
              ButtonSegment(value: SplitMode.exact, label: Text('Exact')),
              ButtonSegment(value: SplitMode.percentage, label: Text('%')),
            ], selected: {_mode}, onSelectionChanged: (value) => setState(() => _mode = value.first)),
            const SizedBox(height: 10), ...members.map((member) => _participantTile(member)),
            if (_mode != SplitMode.equal) AllocationStatusLine(
              label: _mode == SplitMode.percentage ? 'Percentage remaining' : 'Split remaining', remaining: _remainingSplit()),
            const Divider(height: 32),
            SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('Repeat this expense'), subtitle: const Text('Automatically create future copies'),
              value: _recurring, onChanged: (value) => setState(() => _recurring = value)),
            if (_recurring) DropdownButtonFormField<String>(initialValue: _frequency,
              items: const [DropdownMenuItem(value: 'WEEKLY', child: Text('Every week')), DropdownMenuItem(value: 'MONTHLY', child: Text('Every month'))],
              onChanged: (value) => setState(() => _frequency = value!), decoration: const InputDecoration(labelText: 'Frequency')),
            const SizedBox(height: 24),
            OutlinedButton.icon(onPressed: _busy ? null : () => _preview(members), icon: const Icon(Icons.visibility_outlined), label: const Text('Preview shares')),
            const SizedBox(height: 10),
            FilledButton.icon(onPressed: _busy ? null : () => _save(members), icon: _busy ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.check),
              label: Padding(padding: const EdgeInsets.symmetric(vertical: 14), child: Text(_recurring ? 'Create schedule' : 'Save expense'))),
          ]);
        }));
  }

  void _initialize(List<Member> members) {
    if (_initialized || members.isEmpty) return;
    _initialized = true;
    for (final member in members) {
      _payerAmounts[member.userId] = TextEditingController(); _splitValues[member.userId] = TextEditingController();
      _participants.add(member.userId);
    }
    _payers.add(members.first.userId);
  }

  Widget _heading(String label, IconData icon) => Row(children: [Icon(icon), const SizedBox(width: 8), Text(label, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800))]);

  Widget _payerTile(Member member) {
    final selected = _payers.contains(member.userId);
    return Row(children: [Expanded(child: CheckboxListTile(contentPadding: EdgeInsets.zero, controlAffinity: ListTileControlAffinity.leading,
      title: Text(member.displayName), value: selected, onChanged: (value) => setState(() { if (value == true) { _payers.add(member.userId); } else { _payers.remove(member.userId); } }))),
      if (selected) SizedBox(width: 120, child: TextField(controller: _payerAmounts[member.userId], onChanged: (_) => setState(() {}),
        keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: const InputDecoration(labelText: 'Paid'))),
    ]);
  }

  Widget _participantTile(Member member) {
    final selected = _participants.contains(member.userId);
    return Row(children: [Expanded(child: CheckboxListTile(contentPadding: EdgeInsets.zero, controlAffinity: ListTileControlAffinity.leading,
      title: Text(member.displayName), value: selected, onChanged: (value) => setState(() { if (value == true) { _participants.add(member.userId); } else { _participants.remove(member.userId); } }))),
      if (selected && _mode != SplitMode.equal) SizedBox(width: 120, child: TextField(controller: _splitValues[member.userId], onChanged: (_) => setState(() {}),
        keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: InputDecoration(labelText: _mode == SplitMode.percentage ? 'Percent' : 'Owes', suffixText: _mode == SplitMode.percentage ? '%' : null))),
    ]);
  }

  Decimal _remainingPayer() => ExpenseMath.remaining(_total.text, _payers.map((id) => _payerAmounts[id]?.text));
  Decimal _remainingSplit() => _mode == SplitMode.percentage
      ? ExpenseMath.percentageRemaining(_participants.map((id) => _splitValues[id]?.text))
      : ExpenseMath.remaining(_total.text, _participants.map((id) => _splitValues[id]?.text));
  Decimal _decimal(String? text) => ExpenseMath.parse(text);

  Map<String, dynamic> _request() => {
    'description': _description.text.trim(), 'total': {'currency': _currency, 'amount': _total.text.trim()},
    'payers': _payers.map((id) => {'userId': id, 'amount': _payerAmounts[id]!.text.trim()}).toList(),
    'splitType': _mode.name.toUpperCase(),
    'splits': _participants.map((id) => {'userId': id,
      if (_mode == SplitMode.exact) 'amount': _splitValues[id]!.text.trim(),
      if (_mode == SplitMode.percentage) 'percentage': _splitValues[id]!.text.trim()}).toList(),
    'expenseDate': DateFormat('yyyy-MM-dd').format(_date),
  };

  Future<void> _preview(List<Member> members) async {
    if (!_valid()) return;
    await _run(() async {
      final preview = await ref.read(apiProvider).previewExpense(widget.groupId, _request());
      if (!mounted) return;
      await showModalBottomSheet<void>(context: context, showDragHandle: true, builder: (context) => Padding(padding: const EdgeInsets.fromLTRB(24, 8, 24, 32),
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('Share preview', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)), const SizedBox(height: 12),
          ...(preview['shares'] as List).map((value) { final item = value as Map<String, dynamic>; return ListTile(contentPadding: EdgeInsets.zero,
            title: Text(item['displayName'] as String), trailing: Text('$_currency ${item['amount']}', style: const TextStyle(fontWeight: FontWeight.w700))); }),
        ])));
    });
  }

  Future<void> _save(List<Member> members) async {
    if (!_valid()) return;
    await _run(() async {
      final request = _request();
      await ref.read(apiProvider).previewExpense(widget.groupId, request);
      if (_recurring) {
        await ref.read(apiProvider).createRecurringExpense(widget.groupId, request, _frequency,
          DateFormat('yyyy-MM-dd').format(_date), 'Asia/Kolkata');
      } else {
        await ref.read(apiProvider).createExpense(widget.groupId, request);
      }
      if (mounted) Navigator.pop(context, true);
    });
  }

  bool _valid() {
    String? message;
    if (_description.text.trim().isEmpty) message = 'Add a description.';
    else if (_decimal(_total.text) <= Decimal.zero) message = 'Enter a positive total.';
    else if (_payers.isEmpty || _remainingPayer() != Decimal.zero) message = 'Payer contributions must equal the total.';
    else if (_participants.isEmpty) message = 'Select at least one participant.';
    else if (_mode != SplitMode.equal && _remainingSplit() != Decimal.zero) message = _mode == SplitMode.percentage ? 'Percentages must total 100.' : 'Exact shares must equal the total.';
    if (message != null) { ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message))); return false; }
    return true;
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try { await action(); } on ApiFailure catch (error) { if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message))); }
    finally { if (mounted) setState(() => _busy = false); }
  }

  Future<void> _pickDate() async {
    final value = await showDatePicker(context: context, firstDate: DateTime(2000), lastDate: DateTime.now(), initialDate: _date);
    if (value != null) setState(() => _date = value);
  }
}
