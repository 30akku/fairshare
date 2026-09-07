import 'dart:async';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:uuid/uuid.dart';

import 'models.dart';

const _apiBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8080');
const _devUser = String.fromEnvironment('DEV_USER');

class ApiFailure implements Exception {
  const ApiFailure(this.message, {this.code});
  final String message;
  final String? code;
  @override String toString() => message;
}

class FairShareApi {
  FairShareApi(FirebaseAuth auth)
      : _auth = auth,
        _dio = Dio(BaseOptions(
          baseUrl: '$_apiBaseUrl/api/v1',
          connectTimeout: const Duration(seconds: 90),
          receiveTimeout: const Duration(seconds: 90),
          sendTimeout: const Duration(seconds: 30),
          headers: const {'Accept': 'application/json'},
        )) {
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) async {
        if (_devUser.isNotEmpty) {
          options.headers['X-Dev-User'] = _devUser;
        } else {
          final token = await _auth.currentUser?.getIdToken();
          if (token != null) options.headers['Authorization'] = 'Bearer $token';
        }
        handler.next(options);
      },
      onError: (error, handler) {
        final data = error.response?.data;
        if (data is Map<String, dynamic>) {
          handler.reject(DioException(
            requestOptions: error.requestOptions,
            response: error.response,
            error: ApiFailure(data['detail']?.toString() ?? 'The request failed', code: data['code']?.toString()),
            type: error.type,
          ));
        } else {
          handler.next(error);
        }
      },
    ));
  }

  final FirebaseAuth _auth;
  final Dio _dio;

  Future<List<GroupSummary>> groups() async {
    final response = await _guard(() => _dio.get<List<dynamic>>('/groups'));
    return response.data!.map((value) => GroupSummary.fromJson(value as Map<String, dynamic>)).toList();
  }

  Future<GroupSummary> createGroup(String name, String? description) async {
    final response = await _guard(() => _dio.post<Map<String, dynamic>>('/groups', data: {'name': name, 'description': description}));
    return GroupSummary.fromJson(response.data!);
  }

  Future<List<Member>> members(String groupId) async {
    final response = await _guard(() => _dio.get<List<dynamic>>('/groups/$groupId/members'));
    return response.data!.map((value) => Member.fromJson(value as Map<String, dynamic>)).toList();
  }

  Future<List<ExpenseSummary>> expenses(String groupId) async {
    final response = await _guard(() => _dio.get<Map<String, dynamic>>('/groups/$groupId/expenses'));
    return (response.data!['items'] as List).map((value) => ExpenseSummary.fromJson(value as Map<String, dynamic>)).toList();
  }

  Future<List<CurrencyBalance>> balances(String groupId) async {
    final response = await _guard(() => _dio.get<Map<String, dynamic>>('/groups/$groupId/balances'));
    return (response.data!['currencies'] as List).map((value) => CurrencyBalance.fromJson(value as Map<String, dynamic>)).toList();
  }

  Future<List<ActivityItem>> activity(String groupId) async {
    final response = await _guard(() => _dio.get<Map<String, dynamic>>('/groups/$groupId/activity'));
    return (response.data!['items'] as List).map((value) => ActivityItem.fromJson(value as Map<String, dynamic>)).toList();
  }

  Future<Map<String, dynamic>> previewExpense(String groupId, Map<String, dynamic> request) async {
    final response = await _guard(() => _dio.post<Map<String, dynamic>>('/groups/$groupId/expenses/preview', data: request));
    return response.data!;
  }

  Future<void> createExpense(String groupId, Map<String, dynamic> request) async {
    await _guard(() => _dio.post<void>('/groups/$groupId/expenses', data: request, options: Options(headers: {'Idempotency-Key': const Uuid().v4()})));
  }

  Future<void> createRecurringExpense(String groupId, Map<String, dynamic> expense, String frequency, String firstRunDate, String zoneId) async {
    await _guard(() => _dio.post<void>('/groups/$groupId/recurring-expenses', data: {
      'expense': expense, 'frequency': frequency, 'firstRunDate': firstRunDate, 'zoneId': zoneId,
    }));
  }

  Future<List<Map<String, dynamic>>> recurringExpenses(String groupId) async {
    final response = await _guard(() => _dio.get<List<dynamic>>('/groups/$groupId/recurring-expenses'));
    return response.data!.cast<Map<String, dynamic>>();
  }

  Future<void> createSettlement(String groupId, {required String fromUserId, required String toUserId,
      required String currency, required String amount, required String date, String? note}) async {
    await _guard(() => _dio.post<void>('/groups/$groupId/settlements', options: Options(headers: {'Idempotency-Key': const Uuid().v4()}), data: {
      'fromUserId': fromUserId, 'toUserId': toUserId, 'money': {'currency': currency, 'amount': amount},
      'settlementDate': date, 'note': note,
    }));
  }

  Future<String> createInvite(String groupId) async {
    final response = await _guard(() => _dio.post<Map<String, dynamic>>('/groups/$groupId/invites'));
    return response.data!['inviteUrl'] as String;
  }

  Future<void> acceptInvite(String token) async {
    await _guard(() => _dio.post<void>('/invites/$token/accept'));
  }

  Future<List<AppNotificationItem>> notifications() async {
    final response = await _guard(() => _dio.get<Map<String, dynamic>>('/notifications'));
    return (response.data!['items'] as List).map((value) => AppNotificationItem.fromJson(value as Map<String, dynamic>)).toList();
  }

  Future<void> registerDevice(String token) => _guard(() => _dio.put<void>('/devices', data: {'token': token, 'platform': 'ANDROID'})).then((_) {});

  Future<Map<String, dynamic>> me() async {
    final response = await _guard(() => _dio.get<Map<String, dynamic>>('/me'));
    return response.data!;
  }

  Future<void> updateProfile(String displayName, {String? avatarUrl, String? avatarPublicId}) async {
    await _guard(() => _dio.patch<void>('/me', data: {
      'displayName': displayName, 'avatarUrl': avatarUrl, 'avatarPublicId': avatarPublicId,
    }));
  }

  Future<Map<String, String>> uploadAvatar(File file) async {
    final signatureResponse = await _guard(() => _dio.post<Map<String, dynamic>>('/media/signature'));
    final signature = signatureResponse.data!;
    final form = FormData.fromMap({
      'file': await MultipartFile.fromFile(file.path), 'api_key': signature['apiKey'], 'timestamp': signature['timestamp'],
      'signature': signature['signature'], 'folder': signature['folder'], 'public_id': signature['publicId'], 'overwrite': 'true',
    });
    try {
      final response = await Dio().post<Map<String, dynamic>>('https://api.cloudinary.com/v1_1/${signature['cloudName']}/image/upload', data: form);
      return {'url': response.data!['secure_url'] as String, 'publicId': response.data!['public_id'] as String};
    } on DioException catch (error) {
      throw ApiFailure(error.response?.data?.toString() ?? 'Could not upload the profile image');
    }
  }

  Future<void> deleteAccount() => _guard(() => _dio.delete<void>('/me')).then((_) {});

  Future<Response<T>> _guard<T>(Future<Response<T>> Function() action) async {
    try {
      return await action();
    } on DioException catch (error) {
      if (error.error case final ApiFailure failure) throw failure;
      if (error.type == DioExceptionType.connectionTimeout || error.type == DioExceptionType.receiveTimeout) {
        throw const ApiFailure('FairShare is waking up. Wait a moment and try again.', code: 'COLD_START');
      }
      throw ApiFailure(error.message ?? 'Could not connect to FairShare');
    }
  }
}
