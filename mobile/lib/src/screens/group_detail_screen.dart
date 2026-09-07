import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:share_plus/share_plus.dart';

import '../api.dart';
import '../models.dart';
import '../providers.dart';

class GroupDetailScreen extends ConsumerWidget {
  const GroupDetailScreen({super.key, required this.groupId});
  final String groupId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return DefaultTabController(
      length: 5,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Group', style: TextStyle(fontWeight: FontWeight.w800)),
          actions: [IconButton(onPressed: () => _shareInvite(context, ref), icon: const Icon(Icons.person_add_alt), tooltip: 'Invite members')],
          bottom: const TabBar(isScrollable: true, tabs: [
            Tab(text: 'Expenses'), Tab(text: 'Balances'), Tab(text: 'Activity'), Tab(text: 'Members'), Tab(text: 'Recurring'),
          ]),
        ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: () async {
            final created = await context.push<bool>('/groups/$groupId/expenses/new');
            if (created == true) {
              ref.invalidate(expensesProvider(groupId)); ref.invalidate(balancesProvider(groupId));
              ref.invalidate(activityProvider(groupId)); ref.invalidate(recurringProvider(groupId));
            }
          },
          icon: const Icon(Icons.receipt_long), label: const Text('Add expense'),
        ),
        body: TabBarView(children: [
          _ExpensesTab(groupId: groupId), _BalancesTab(groupId: groupId), _ActivityTab(groupId: groupId),
          _MembersTab(groupId: groupId), _RecurringTab(groupId: groupId),
        ]),
      ),
    );
  }

  Future<void> _shareInvite(BuildContext context, WidgetRef ref) async {
    try {
      final url = await ref.read(apiProvider).createInvite(groupId);
      await SharePlus.instance.share(ShareParams(text: 'Join my FairShare group: $url'));
    } on ApiFailure catch (error) {
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }
}

class _ExpensesTab extends ConsumerWidget {
  const _ExpensesTab({required this.groupId}); final String groupId;
  @override Widget build(BuildContext context, WidgetRef ref) {
    final value = ref.watch(expensesProvider(groupId));
    return value.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => _ErrorState(error: error, retry: () => ref.invalidate(expensesProvider(groupId))),
      data: (items) => RefreshIndicator(
        onRefresh: () => ref.refresh(expensesProvider(groupId).future),
        child: items.isEmpty ? ListView(children: const [SizedBox(height: 120), Icon(Icons.receipt_long_outlined, size: 60), SizedBox(height: 12), Center(child: Text('No expenses yet.'))])
          : ListView.separated(padding: const EdgeInsets.fromLTRB(16, 12, 16, 96), itemCount: items.length,
            separatorBuilder: (_, __) => const Divider(height: 1), itemBuilder: (_, index) {
              final item = items[index];
              return ListTile(contentPadding: const EdgeInsets.symmetric(vertical: 6),
                leading: const CircleAvatar(child: Icon(Icons.receipt_outlined)),
                title: Text(item.description, style: const TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text('${DateFormat.yMMMd().format(item.expenseDate)} · ${item.splitType.toLowerCase()} split'),
                trailing: Text('${item.currency} ${item.total}', style: const TextStyle(fontWeight: FontWeight.w800)));
            }),
      ),
    );
  }
}

class _BalancesTab extends ConsumerWidget {
  const _BalancesTab({required this.groupId}); final String groupId;
  @override Widget build(BuildContext context, WidgetRef ref) {
    final value = ref.watch(balancesProvider(groupId));
    final members = ref.watch(membersProvider(groupId)).asData?.value ?? const <Member>[];
    return value.when(loading: () => const Center(child: CircularProgressIndicator()),
      error: (error, _) => _ErrorState(error: error, retry: () => ref.invalidate(balancesProvider(groupId))),
      data: (currencies) => RefreshIndicator(onRefresh: () => ref.refresh(balancesProvider(groupId).future),
        child: currencies.isEmpty ? ListView(children: const [SizedBox(height: 130), Center(child: Text('Balances will appear after the first expense.'))])
          : ListView(padding: const EdgeInsets.fromLTRB(16, 12, 16, 96), children: currencies.map((balance) => Card(
            margin: const EdgeInsets.only(bottom: 12), child: Padding(padding: const EdgeInsets.all(16), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(balance.currency, style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)), const SizedBox(height: 8),
              ...balance.members.map((member) { final net = double.tryParse(member.netAmount) ?? 0;
                return ListTile(contentPadding: EdgeInsets.zero, dense: true, title: Text(member.displayName),
                  trailing: Text(net == 0 ? 'settled' : '${net > 0 ? '+' : ''}${member.netAmount}',
                    style: TextStyle(fontWeight: FontWeight.w700, color: net > 0 ? Colors.green.shade700 : net < 0 ? Colors.red.shade700 : null))); }),
              if (balance.suggestions.isNotEmpty) const Divider(),
              ...balance.suggestions.map((suggestion) => ListTile(contentPadding: EdgeInsets.zero,
                title: Text('${_name(members, suggestion.fromUserId)} pays ${_name(members, suggestion.toUserId)}'),
                subtitle: Text('${balance.currency} ${suggestion.amount}'),
                trailing: TextButton(onPressed: () => _settle(context, ref, balance.currency, suggestion, members), child: const Text('Record')))),
            ]))) ).toList()),
      ));
  }

  String _name(List<Member> members, String id) {
    for (final member in members) {
      if (member.userId == id) return member.displayName;
    }
    return 'Member';
  }

  Future<void> _settle(BuildContext context, WidgetRef ref, String currency, SettlementSuggestion suggestion, List<Member> members) async {
    final amount = TextEditingController(text: suggestion.amount); final note = TextEditingController();
    final accepted = await showDialog<bool>(context: context, builder: (context) => AlertDialog(title: const Text('Record payment'),
      content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('${_name(members, suggestion.fromUserId)} pays ${_name(members, suggestion.toUserId)}'), const SizedBox(height: 12),
        TextField(controller: amount, keyboardType: const TextInputType.numberWithOptions(decimal: true), decoration: InputDecoration(labelText: 'Amount ($currency)')),
        const SizedBox(height: 12), TextField(controller: note, decoration: const InputDecoration(labelText: 'Note (optional)')),
      ]), actions: [TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Record'))]));
    if (accepted != true) return;
    try {
      await ref.read(apiProvider).createSettlement(groupId, fromUserId: suggestion.fromUserId, toUserId: suggestion.toUserId,
        currency: currency, amount: amount.text.trim(), date: DateFormat('yyyy-MM-dd').format(DateTime.now()), note: note.text.trim());
      ref.invalidate(balancesProvider(groupId)); ref.invalidate(activityProvider(groupId));
    } on ApiFailure catch (error) { if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message))); }
  }
}

class _ActivityTab extends ConsumerWidget {
  const _ActivityTab({required this.groupId}); final String groupId;
  @override Widget build(BuildContext context, WidgetRef ref) => ref.watch(activityProvider(groupId)).when(
    loading: () => const Center(child: CircularProgressIndicator()), error: (error, _) => _ErrorState(error: error, retry: () => ref.invalidate(activityProvider(groupId))),
    data: (items) => ListView.builder(padding: const EdgeInsets.fromLTRB(16, 12, 16, 96), itemCount: items.length, itemBuilder: (_, index) {
      final item = items[index]; return ListTile(leading: const Icon(Icons.history), title: Text(item.summary),
        subtitle: Text(DateFormat.yMMMd().add_jm().format(item.createdAt.toLocal()))); }));
}

class _MembersTab extends ConsumerWidget {
  const _MembersTab({required this.groupId}); final String groupId;
  @override Widget build(BuildContext context, WidgetRef ref) => ref.watch(membersProvider(groupId)).when(
    loading: () => const Center(child: CircularProgressIndicator()), error: (error, _) => _ErrorState(error: error, retry: () => ref.invalidate(membersProvider(groupId))),
    data: (items) => ListView.builder(padding: const EdgeInsets.fromLTRB(16, 12, 16, 96), itemCount: items.length, itemBuilder: (_, index) {
      final item = items[index]; return ListTile(leading: CircleAvatar(child: Text(item.displayName.characters.first.toUpperCase())),
        title: Text(item.displayName), subtitle: Text(item.email), trailing: item.role == 'OWNER' ? const Chip(label: Text('Owner')) : null); }));
}

class _RecurringTab extends ConsumerWidget {
  const _RecurringTab({required this.groupId}); final String groupId;
  @override Widget build(BuildContext context, WidgetRef ref) => ref.watch(recurringProvider(groupId)).when(
    loading: () => const Center(child: CircularProgressIndicator()), error: (error, _) => _ErrorState(error: error, retry: () => ref.invalidate(recurringProvider(groupId))),
    data: (items) => items.isEmpty ? const Center(child: Text('Create a recurring schedule from the expense form.'))
      : ListView.builder(padding: const EdgeInsets.fromLTRB(16, 12, 16, 96), itemCount: items.length, itemBuilder: (_, index) {
        final item = items[index]; return Card(margin: const EdgeInsets.only(bottom: 10), child: ListTile(
          leading: const Icon(Icons.event_repeat), title: Text(item['description'] as String),
          subtitle: Text('${item['frequency']} · next ${item['nextRunDate']}'),
          trailing: Text('${item['currency']} ${item['total']}'))); }));
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.error, required this.retry}); final Object error; final VoidCallback retry;
  @override Widget build(BuildContext context) => Center(child: Padding(padding: const EdgeInsets.all(24), child: Column(mainAxisSize: MainAxisSize.min, children: [
    const Icon(Icons.cloud_off_outlined, size: 48), const SizedBox(height: 12), Text(error.toString(), textAlign: TextAlign.center),
    const SizedBox(height: 12), FilledButton.icon(onPressed: retry, icon: const Icon(Icons.refresh), label: const Text('Try again'))])));
}
