import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';

import '../api.dart';
import '../providers.dart';

class ProfileScreen extends ConsumerStatefulWidget {
  const ProfileScreen({super.key});
  @override ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  Map<String, dynamic>? _profile;
  bool _busy = false;
  Object? _error;

  @override void initState() { super.initState(); _load(); }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Profile', style: TextStyle(fontWeight: FontWeight.w800))),
      body: _profile == null
          ? Center(child: _error == null ? const CircularProgressIndicator() : FilledButton(onPressed: _load, child: Text(_error.toString())))
          : ListView(padding: const EdgeInsets.all(24), children: [
              Center(child: Stack(children: [
                CircleAvatar(
                  radius: 52,
                  backgroundImage: _profile!['avatarUrl'] == null ? null : NetworkImage(_profile!['avatarUrl'] as String),
                  child: _profile!['avatarUrl'] == null
                      ? Text((_profile!['displayName'] as String).characters.first.toUpperCase(), style: const TextStyle(fontSize: 36)) : null,
                ),
                Positioned(right: 0, bottom: 0, child: IconButton.filled(
                  onPressed: _busy ? null : _changePhoto, icon: const Icon(Icons.camera_alt), tooltip: 'Change photo')),
              ])),
              const SizedBox(height: 24),
              ListTile(
                leading: const Icon(Icons.person_outline), title: Text(_profile!['displayName'] as String),
                subtitle: const Text('Display name'), trailing: const Icon(Icons.edit), onTap: _editName),
              ListTile(leading: const Icon(Icons.email_outlined), title: Text(_profile!['email'] as String), subtitle: const Text('Verified email')),
              const Divider(height: 40),
              OutlinedButton.icon(
                onPressed: _busy ? null : () async { await ref.read(firebaseAuthProvider).signOut(); if (mounted) context.go('/auth'); },
                icon: const Icon(Icons.logout), label: const Text('Sign out')),
              const SizedBox(height: 12),
              TextButton.icon(
                style: TextButton.styleFrom(foregroundColor: Theme.of(context).colorScheme.error),
                onPressed: _busy ? null : _deleteAccount, icon: const Icon(Icons.delete_outline), label: const Text('Delete account')),
            ],
      ),
    );
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try { final value = await ref.read(apiProvider).me(); if (mounted) setState(() => _profile = value); }
    catch (error) { if (mounted) setState(() => _error = error); }
  }

  Future<void> _editName() async {
    final controller = TextEditingController(text: _profile!['displayName'] as String);
    final save = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
      title: const Text('Display name'), content: TextField(controller: controller, autofocus: true, maxLength: 100),
      actions: [TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Save'))]));
    if (save != true || controller.text.trim().isEmpty) return;
    await _run(() => ref.read(apiProvider).updateProfile(controller.text.trim(),
      avatarUrl: _profile!['avatarUrl'] as String?, avatarPublicId: _profile!['avatarPublicId'] as String?));
    await _load();
  }

  Future<void> _changePhoto() async {
    final image = await ImagePicker().pickImage(source: ImageSource.gallery, maxWidth: 1024, maxHeight: 1024, imageQuality: 82);
    if (image == null) return;
    await _run(() async {
      final uploaded = await ref.read(apiProvider).uploadAvatar(File(image.path));
      await ref.read(apiProvider).updateProfile(_profile!['displayName'] as String,
        avatarUrl: uploaded['url'], avatarPublicId: uploaded['publicId']);
    });
    await _load();
  }

  Future<void> _deleteAccount() async {
    final confirmed = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
      title: const Text('Delete account?'),
      content: const Text('Your personal profile will be removed. Financial history remains anonymized for other group members.'),
      actions: [TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Delete'))]));
    if (confirmed != true) return;
    await _run(() async { await ref.read(apiProvider).deleteAccount(); await ref.read(firebaseAuthProvider).signOut(); });
    if (mounted) context.go('/auth');
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try { await action(); }
    on ApiFailure catch (error) { if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message))); }
    finally { if (mounted) setState(() => _busy = false); }
  }
}

