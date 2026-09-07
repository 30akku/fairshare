import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../api.dart';
import '../providers.dart';

class GroupsScreen extends ConsumerStatefulWidget {
  const GroupsScreen({super.key});
  @override ConsumerState<GroupsScreen> createState() => _GroupsScreenState();
}

class _GroupsScreenState extends ConsumerState<GroupsScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      try { await configureMessaging(ref); } catch (_) {}
    });
  }

  @override
  Widget build(BuildContext context) {
    final groups = ref.watch(groupsProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Your groups', style: TextStyle(fontWeight: FontWeight.w800)),
        actions: [
          IconButton(onPressed: () => context.push('/notifications'), icon: const Icon(Icons.notifications_none), tooltip: 'Notifications'),
          IconButton(onPressed: () => context.push('/profile'), icon: const Icon(Icons.account_circle_outlined), tooltip: 'Profile'),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(onPressed: _createGroup, icon: const Icon(Icons.add), label: const Text('New group')),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(groupsProvider.future),
        child: groups.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => _Retry(message: error.toString(), onRetry: () => ref.invalidate(groupsProvider)),
          data: (items) => items.isEmpty
              ? ListView(children: const [SizedBox(height: 160), Icon(Icons.groups_2_outlined, size: 72), SizedBox(height: 16), Center(child: Text('Create a group to start sharing expenses.'))])
              : ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
                  itemCount: items.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, index) {
                    final group = items[index];
                    return Card(
                      child: ListTile(
                        contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
                        leading: CircleAvatar(child: Text(group.name.characters.first.toUpperCase())),
                        title: Text(group.name, style: const TextStyle(fontWeight: FontWeight.w700)),
                        subtitle: group.description == null ? const Text('Tap to view expenses') : Text(group.description!),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => context.push('/groups/${group.id}'),
                      ),
                    );
                  },
                ),
        ),
      ),
    );
  }

  Future<void> _createGroup() async {
    final name = TextEditingController();
    final description = TextEditingController();
    final create = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
      title: const Text('Create a group'),
      content: Column(mainAxisSize: MainAxisSize.min, children: [
        TextField(controller: name, autofocus: true, decoration: const InputDecoration(labelText: 'Group name')),
        const SizedBox(height: 12),
        TextField(controller: description, decoration: const InputDecoration(labelText: 'Description (optional)')),
      ]),
      actions: [TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Create'))],
    ));
    if (create != true || name.text.trim().isEmpty) return;
    try {
      final group = await ref.read(apiProvider).createGroup(name.text.trim(), description.text.trim().isEmpty ? null : description.text.trim());
      ref.invalidate(groupsProvider);
      if (mounted) context.push('/groups/${group.id}');
    } on ApiFailure catch (error) { if (mounted) _message(error.message); }
  }

  void _message(String value) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(value)));
}

class _Retry extends StatelessWidget {
  const _Retry({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;
  @override Widget build(BuildContext context) => ListView(children: [
    const SizedBox(height: 160), const Icon(Icons.cloud_off_outlined, size: 64), const SizedBox(height: 16),
    Padding(padding: const EdgeInsets.symmetric(horizontal: 32), child: Text(message, textAlign: TextAlign.center)),
    const SizedBox(height: 16), Center(child: FilledButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh), label: const Text('Try again'))),
  ]);
}

