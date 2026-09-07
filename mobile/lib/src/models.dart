class GroupSummary {
  const GroupSummary({required this.id, required this.name, this.description, required this.ownerId});
  final String id;
  final String name;
  final String? description;
  final String ownerId;

  factory GroupSummary.fromJson(Map<String, dynamic> json) => GroupSummary(
        id: json['id'] as String,
        name: json['name'] as String,
        description: json['description'] as String?,
        ownerId: json['ownerId'] as String,
      );
}

class Member {
  const Member({required this.userId, required this.displayName, required this.email, this.avatarUrl, required this.role});
  final String userId;
  final String displayName;
  final String email;
  final String? avatarUrl;
  final String role;

  factory Member.fromJson(Map<String, dynamic> json) => Member(
        userId: json['userId'] as String,
        displayName: json['displayName'] as String,
        email: json['email'] as String,
        avatarUrl: json['avatarUrl'] as String?,
        role: json['role'] as String,
      );
}

class ExpenseSummary {
  const ExpenseSummary({required this.id, required this.description, required this.currency, required this.total,
    required this.expenseDate, required this.splitType, required this.version});
  final String id;
  final String description;
  final String currency;
  final String total;
  final DateTime expenseDate;
  final String splitType;
  final int version;

  factory ExpenseSummary.fromJson(Map<String, dynamic> json) => ExpenseSummary(
        id: json['id'] as String,
        description: json['description'] as String,
        currency: json['currency'] as String,
        total: json['total'] as String,
        expenseDate: DateTime.parse(json['expenseDate'] as String),
        splitType: json['splitType'] as String,
        version: json['version'] as int,
      );
}

class CurrencyBalance {
  const CurrencyBalance({required this.currency, required this.members, required this.suggestions});
  final String currency;
  final List<MemberNet> members;
  final List<SettlementSuggestion> suggestions;

  factory CurrencyBalance.fromJson(Map<String, dynamic> json) => CurrencyBalance(
        currency: json['currency'] as String,
        members: (json['members'] as List).map((value) => MemberNet.fromJson(value as Map<String, dynamic>)).toList(),
        suggestions: (json['suggestions'] as List).map((value) => SettlementSuggestion.fromJson(value as Map<String, dynamic>)).toList(),
      );
}

class MemberNet {
  const MemberNet({required this.userId, required this.displayName, required this.netAmount});
  final String userId;
  final String displayName;
  final String netAmount;
  factory MemberNet.fromJson(Map<String, dynamic> json) => MemberNet(
    userId: json['userId'] as String, displayName: json['displayName'] as String, netAmount: json['netAmount'] as String);
}

class SettlementSuggestion {
  const SettlementSuggestion({required this.fromUserId, required this.toUserId, required this.amount});
  final String fromUserId;
  final String toUserId;
  final String amount;
  factory SettlementSuggestion.fromJson(Map<String, dynamic> json) => SettlementSuggestion(
    fromUserId: json['fromUserId'] as String, toUserId: json['toUserId'] as String, amount: json['amount'] as String);
}

class ActivityItem {
  const ActivityItem({required this.id, required this.actorName, required this.summary, required this.createdAt});
  final String id;
  final String actorName;
  final String summary;
  final DateTime createdAt;
  factory ActivityItem.fromJson(Map<String, dynamic> json) => ActivityItem(
    id: json['id'] as String, actorName: json['actorName'] as String,
    summary: json['summary'] as String, createdAt: DateTime.parse(json['createdAt'] as String));
}

class AppNotificationItem {
  const AppNotificationItem({required this.id, required this.title, required this.body, this.readAt, required this.createdAt});
  final String id;
  final String title;
  final String body;
  final DateTime? readAt;
  final DateTime createdAt;
  factory AppNotificationItem.fromJson(Map<String, dynamic> json) => AppNotificationItem(
    id: json['id'] as String, title: json['title'] as String, body: json['body'] as String,
    readAt: json['readAt'] == null ? null : DateTime.parse(json['readAt'] as String),
    createdAt: DateTime.parse(json['createdAt'] as String));
}

