package com.app.winterarc.domain.auth;

/** A signed-in account. {@code uid} is the provider's stable id, used later for cloud sync. */
public record AuthUser(String uid, String phoneE164) {}
