import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'providers.dart';
import 'screens/auth_screen.dart';
import 'screens/expense_form_screen.dart';
import 'screens/group_detail_screen.dart';
import 'screens/groups_screen.dart';
import 'screens/invite_screen.dart';
import 'screens/notifications_screen.dart';
import 'screens/profile_screen.dart';

final routerProvider = Provider<GoRouter>((ref) {
  final auth = ref.watch(authStateProvider);
  return GoRouter(
    initialLocation: '/groups',
    redirect: (context, state) {
      final signedIn = auth.asData?.value != null;
      final onAuth = state.matchedLocation == '/auth';
      if (auth.isLoading) return null;
      if (!signedIn && !onAuth) return '/auth?next=${Uri.encodeComponent(state.uri.toString())}';
      if (signedIn && onAuth) return state.uri.queryParameters['next'] ?? '/groups';
      return null;
    },
    routes: [
      GoRoute(path: '/auth', builder: (_, __) => const AuthScreen()),
      GoRoute(path: '/groups', builder: (_, __) => const GroupsScreen()),
      GoRoute(path: '/groups/:groupId', builder: (_, state) => GroupDetailScreen(groupId: state.pathParameters['groupId']!)),
      GoRoute(path: '/groups/:groupId/expenses/new', builder: (_, state) => ExpenseFormScreen(groupId: state.pathParameters['groupId']!)),
      GoRoute(path: '/notifications', builder: (_, __) => const NotificationsScreen()),
      GoRoute(path: '/profile', builder: (_, __) => const ProfileScreen()),
      GoRoute(path: '/invite', builder: (_, state) => InviteScreen(token: state.uri.queryParameters['token'])),
      GoRoute(path: '/fairshare/invite', redirect: (_, state) => '/invite?token=${Uri.encodeQueryComponent(state.uri.queryParameters['token'] ?? '')}'),
    ],
  );
});

class FairShareApp extends ConsumerWidget {
  const FairShareApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MaterialApp.router(
      title: 'FairShare',
      debugShowCheckedModeBanner: false,
      routerConfig: ref.watch(routerProvider),
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF235347), brightness: Brightness.light),
        scaffoldBackgroundColor: const Color(0xFFF7F8F2),
        cardTheme: const CardThemeData(elevation: 0, margin: EdgeInsets.zero),
        inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder(), filled: true, fillColor: Colors.white),
        appBarTheme: const AppBarTheme(backgroundColor: Color(0xFFF7F8F2), surfaceTintColor: Colors.transparent),
      ),
    );
  }
}
