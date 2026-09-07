import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_sign_in/google_sign_in.dart';

import '../providers.dart';

class AuthScreen extends ConsumerStatefulWidget {
  const AuthScreen({super.key});
  @override ConsumerState<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends ConsumerState<AuthScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _register = false;
  bool _busy = false;

  @override
  void dispose() { _email.dispose(); _password.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                const Icon(Icons.balance_rounded, size: 72, color: Color(0xFF235347)),
                const SizedBox(height: 20),
                Text('FairShare', textAlign: TextAlign.center, style: Theme.of(context).textTheme.displaySmall?.copyWith(fontWeight: FontWeight.w800)),
                const SizedBox(height: 8),
                Text('Shared expenses, clearly settled.', textAlign: TextAlign.center, style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 36),
                TextField(controller: _email, keyboardType: TextInputType.emailAddress, autofillHints: const [AutofillHints.email],
                  decoration: const InputDecoration(labelText: 'Email', prefixIcon: Icon(Icons.email_outlined))),
                const SizedBox(height: 12),
                TextField(controller: _password, obscureText: true, autofillHints: const [AutofillHints.password],
                  decoration: const InputDecoration(labelText: 'Password', prefixIcon: Icon(Icons.lock_outline))),
                const SizedBox(height: 16),
                FilledButton(onPressed: _busy ? null : _emailAction, child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 14), child: Text(_register ? 'Create account' : 'Sign in'))),
                const SizedBox(height: 10),
                OutlinedButton.icon(onPressed: _busy ? null : _googleAction, icon: const Icon(Icons.account_circle_outlined), label: const Text('Continue with Google')),
                TextButton(onPressed: _busy ? null : () => setState(() => _register = !_register),
                  child: Text(_register ? 'Already have an account? Sign in' : 'New here? Create an account')),
                TextButton(onPressed: _busy ? null : _resetPassword, child: const Text('Forgot password?')),
              ]),
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _emailAction() async {
    await _run(() async {
      final auth = ref.read(firebaseAuthProvider);
      if (_register) {
        final credential = await auth.createUserWithEmailAndPassword(email: _email.text.trim(), password: _password.text);
        await credential.user?.sendEmailVerification();
        if (mounted) _message('Verification email sent. Verify it, then sign in.');
        await auth.signOut();
      } else {
        final credential = await auth.signInWithEmailAndPassword(email: _email.text.trim(), password: _password.text);
        if (credential.user?.emailVerified != true) {
          await credential.user?.sendEmailVerification();
          await auth.signOut();
          throw FirebaseAuthException(code: 'email-not-verified', message: 'Verify your email first. We sent a new link.');
        }
      }
    });
  }

  Future<void> _googleAction() async {
    await _run(() async {
      await GoogleSignIn.instance.initialize();
      final account = await GoogleSignIn.instance.authenticate();
      final authentication = account.authentication;
      final credential = GoogleAuthProvider.credential(idToken: authentication.idToken);
      await ref.read(firebaseAuthProvider).signInWithCredential(credential);
    });
  }

  Future<void> _resetPassword() async {
    if (_email.text.trim().isEmpty) { _message('Enter your email first.'); return; }
    await _run(() async {
      await ref.read(firebaseAuthProvider).sendPasswordResetEmail(email: _email.text.trim());
      if (mounted) _message('Password reset email sent.');
    });
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try { await action(); }
    on FirebaseAuthException catch (error) { if (mounted) _message(error.message ?? 'Authentication failed.'); }
    catch (error) { if (mounted) _message(error.toString()); }
    finally { if (mounted) setState(() => _busy = false); }
  }

  void _message(String text) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
}

