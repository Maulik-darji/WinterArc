package com.app.winterarc.ui.social;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;
import com.app.winterarc.domain.repository.SocialRepository;
import com.app.winterarc.domain.social.Community;
import com.app.winterarc.domain.social.CommunityMember;
import com.app.winterarc.domain.social.CommunityMessage;
import com.app.winterarc.domain.social.CommunityPermissions;
import com.app.winterarc.domain.social.CommunityPost;
import com.app.winterarc.domain.social.Follow;
import com.app.winterarc.domain.social.MemberRole;
import com.app.winterarc.domain.social.SocialResult;
import com.app.winterarc.domain.social.SocialUser;
import com.app.winterarc.ui.common.Combiner;
import com.app.winterarc.ui.common.Event;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class SocialViewModel extends ViewModel {
    public record State(String currentUserId, List<SocialUser> users, List<Community> communities,
                        List<CommunityMember> members, List<CommunityPost> posts, List<Follow> follows) {}
    public record Message(String action, SocialResult result) {}

    private final SocialRepository repository;
    private final AppExecutors executors;
    private final String currentUserId;
    private final Combiner<State> state;
    private final MutableLiveData<Event<Message>> messages = new MutableLiveData<>();
    private volatile List<SocialUser> users;
    private volatile List<Community> communities;
    private volatile List<CommunityMember> members;
    private volatile List<CommunityPost> posts;
    private volatile List<Follow> follows;

    @Inject
    public SocialViewModel(SocialRepository repository, AuthRepository auth, AppExecutors executors) {
        this.repository = repository;
        this.executors = executors;
        AuthUser current = auth.getCurrentUser();
        currentUserId = current == null ? "local-user" : current.uid();
        state = new Combiner<>(executors, this::compute);
        state.watch(repository.observeUsers(), value -> users = value);
        state.watch(repository.observeCommunities(), value -> communities = value);
        state.watch(repository.observeMembers(), value -> members = value);
        state.watch(repository.observePosts(), value -> posts = value);
        state.watch(repository.observeFollows(), value -> follows = value);
        executors.io().execute(() -> repository.ensureSeeded(currentUserId));
    }

    public LiveData<State> state() { return state; }
    public LiveData<Event<Message>> messages() { return messages; }
    public LiveData<List<CommunityMessage>> messages(String communityId) { return repository.observeMessages(communityId); }
    public String currentUserId() { return currentUserId; }

    private State compute() {
        if (users == null || communities == null || members == null || posts == null || follows == null) return null;
        return new State(currentUserId, List.copyOf(users), List.copyOf(communities), List.copyOf(members),
                List.copyOf(posts), List.copyOf(follows));
    }

    public void toggleFollow(String targetId) { run("follow", () -> repository.toggleFollow(currentUserId, targetId)); }
    public void join(String communityId) { run("join", () -> repository.joinCommunity(currentUserId, communityId)); }
    public void leave(String communityId) { run("leave", () -> repository.leaveCommunity(currentUserId, communityId)); }
    public void createCommunity(String name, String description) { run("create_community", () -> repository.createCommunity(currentUserId, name, description)); }
    public void createPost(String communityId, String body) { run("create_post", () -> repository.createPost(currentUserId, communityId, body)); }
    public void toggleLike(String postId) { run("like", () -> repository.toggleLike(currentUserId, postId)); }
    public void sendMessage(String communityId, String body) { run("send_message", () -> repository.sendMessage(currentUserId, communityId, body)); }
    public void updatePermissions(String communityId, CommunityPermissions permissions) {
        run("permissions", () -> repository.updatePermissions(currentUserId, communityId, permissions));
    }
    public void setRole(String communityId, String targetId, MemberRole role) {
        run("role", () -> repository.setRole(currentUserId, communityId, targetId, role));
    }
    public void updateProfile(String name, String bio) { run("profile", () -> repository.updateProfile(currentUserId, name, bio)); }

    private void run(String action, Operation operation) {
        executors.io().execute(() -> {
            SocialResult result;
            try { result = operation.run(); } catch (RuntimeException error) { result = SocialResult.INVALID_INPUT; }
            SocialResult finalResult = result;
            executors.main().execute(() -> messages.setValue(new Event<>(new Message(action, finalResult))));
        });
    }

    private interface Operation { SocialResult run(); }
}
