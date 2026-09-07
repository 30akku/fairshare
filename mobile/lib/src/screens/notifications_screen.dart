import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../providers.dart';

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notifications = ref.watch(notificationsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Notifications', style: TextStyle(fontWeight: FontWeight.w800))),
      body: notifications.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(child: FilledButton.icon(
          onPressed: () => ref.invalidate(notificationsProvider), icon: const Icon(Icons.refresh), label: Text(error.toString()))),
        data: (items) => RefreshIndicator(
          onRefresh: () => ref.refresh(notificationsProvider.future),
          child: items.isEmpty
              ? ListView(children: const [SizedBox(height: 140), Icon(Icons.notifications_none, size: 64), SizedBox(height: 12), Center(child: Text('You are all caught up.'))])
              : ListView.separated(
                  padding: const EdgeInsets.all(16), itemCount: items.length, separatorBuilder: (_, __) => const Divider(),
                  itemBuilder: (_, index) {
                    final item = items[index];
                    return ListTile(
                      leading: CircleAvatar(
                        backgroundColor: item.readAt == null ? Theme.of(context).colorScheme.primaryContainer : null,
                        child: const Icon(Icons.notifications_outlined)),
                      title: Text(item.title, style: TextStyle(fontWeight: item.readAt == null ? FontWeight.w800 : FontWeight.w500)),
                      subtitle: Text('${item.body}\n${DateFormat.yMMMd().add_jm().format(item.createdAt.toLocal())}'),
                      isThreeLine: true,
                    );
                  },
                ),
        ),
      ),
    );
  }
}

