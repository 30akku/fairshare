import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../api.dart';
import '../providers.dart';

class InviteScreen extends ConsumerStatefulWidget {
  const InviteScreen({super.key, this.token});
  final String? token;
  @override ConsumerState<InviteScreen> createState() => _InviteScreenState();
}

class _InviteScreenState extends ConsumerState<InviteScreen> {
  late final TextEditingController _token = TextEditingController(text: widget.token);
  bool _busy = false;
  @override void dispose() { _token.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Join group')),
    body: Center(child: SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 440), child: Column(children: [
        const Icon(Icons.group_add_outlined, size: 72), const SizedBox(height: 18),
        Text('You have been invited', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800)),
        const SizedBox(height: 8),
        const Text('Accept the invitation to see the group and its shared expenses.', textAlign: TextAlign.center),
        const SizedBox(height: 24),
        TextField(controller: _token, decoration: const InputDecoration(labelText: 'Invite code')),
        const SizedBox(height: 16),
        FilledButton.icon(onPressed: _busy ? null : _accept, icon: const Icon(Icons.check), label: const Text('Join group')),
      ])),
    )),
  );

  Future<void> _accept() async {
    if (_token.text.trim().isEmpty) return;
    setState(() => _busy = true);
    try {
      await ref.read(apiProvider).acceptInvite(_token.text.trim());
      ref.invalidate(groupsProvider);
      if (mounted) context.go('/groups');
    } on ApiFailure catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message)));
    } finally { if (mounted) setState(() => _busy = false); }
  }
}

