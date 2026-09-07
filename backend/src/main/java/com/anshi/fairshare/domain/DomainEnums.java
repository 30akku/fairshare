package com.anshi.fairshare.domain;

public final class DomainEnums {
    private DomainEnums() {}

    public enum GroupRole { OWNER, MEMBER }
    public enum SplitType { EQUAL, EXACT, PERCENTAGE }
    public enum RecurrenceFrequency { WEEKLY, MONTHLY }
}

