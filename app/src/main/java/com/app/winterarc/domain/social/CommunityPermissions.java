package com.app.winterarc.domain.social;

public record CommunityPermissions(boolean membersCanPost, boolean membersCanChat,
                                   boolean membersCanInvite) {
    public static final CommunityPermissions OPEN = new CommunityPermissions(true, true, true);
}
