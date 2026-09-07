import 'package:firebase_auth/firebase_auth.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'api.dart';
import 'models.dart';

final firebaseAuthProvider = Provider<FirebaseAuth>((ref) => FirebaseAuth.instance);
final authStateProvider = StreamProvider<User?>((ref) => ref.watch(firebaseAuthProvider).authStateChanges());
final apiProvider = Provider<FairShareApi>((ref) => FairShareApi(ref.watch(firebaseAuthProvider)));

final groupsProvider = FutureProvider<List<GroupSummary>>((ref) => ref.watch(apiProvider).groups());
final membersProvider = FutureProvider.family<List<Member>, String>((ref, groupId) => ref.watch(apiProvider).members(groupId));
final expensesProvider = FutureProvider.family<List<ExpenseSummary>, String>((ref, groupId) => ref.watch(apiProvider).expenses(groupId));
final balancesProvider = FutureProvider.family<List<CurrencyBalance>, String>((ref, groupId) => ref.watch(apiProvider).balances(groupId));
final activityProvider = FutureProvider.family<List<ActivityItem>, String>((ref, groupId) => ref.watch(apiProvider).activity(groupId));
final recurringProvider = FutureProvider.family<List<Map<String, dynamic>>, String>((ref, groupId) => ref.watch(apiProvider).recurringExpenses(groupId));
final notificationsProvider = FutureProvider<List<AppNotificationItem>>((ref) => ref.watch(apiProvider).notifications());

Future<void> configureMessaging(WidgetRef ref) async {
  final messaging = FirebaseMessaging.instance;
  await messaging.requestPermission();
  final token = await messaging.getToken();
  if (token != null) await ref.read(apiProvider).registerDevice(token);
  messaging.onTokenRefresh.listen((value) => ref.read(apiProvider).registerDevice(value));
}
