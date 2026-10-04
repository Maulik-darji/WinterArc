package com.app.winterarc.domain.social;

public enum MemberRole {
    OWNER("owner"), ADMIN("admin"), MEMBER("member");

    private final String key;
    MemberRole(String key) { this.key = key; }
    public String key() { return key; }
    public static MemberRole fromKey(String key) {
        for (MemberRole role : values()) if (role.key.equals(key)) return role;
        return MEMBER;
    }
}
