package com.app.winterarc.ui.feed;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.feed.FeedPost;
import com.app.winterarc.domain.feed.FeedRepository;
import com.app.winterarc.domain.feed.FeedResult;
import com.app.winterarc.domain.repository.SocialRepository;
import com.app.winterarc.domain.social.Follow;
import com.app.winterarc.domain.social.SocialUser;
import com.app.winterarc.ui.common.Combiner;
import com.app.winterarc.ui.common.Event;
import com.app.winterarc.ui.social.SocialViewModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/** Timeline state and post actions; activity-scoped so the timeline and composer share it. */
@HiltViewModel
public class FeedViewModel extends ViewModel {
    public record State(String viewerId, List<FeedPost> posts, Map<String, SocialUser> users, Set<String> following) {}

    public record Message(Action action, FeedResult result) {}

    public enum Action { CREATE, EDIT, DELETE, LIKE }

    private final FeedRepository feed;
    private final AppExecutors executors;
    private final String viewerId;
    private final Combiner<State> state;
    private final MutableLiveData<Event<Message>> messages = new MutableLiveData<>();
    private volatile List<FeedPost> posts;
    private volatile List<SocialUser> users;
    private volatile List<Follow> follows;

    @Inject
    public FeedViewModel(FeedRepository feed, SocialRepository social, AuthRepository auth, AppExecutors executors) {
        this.feed = feed;
        this.executors = executors;
        viewerId = SocialViewModel.ensureCurrentProfile(auth, social, executors);
        state = new Combiner<>(executors, this::compute);
        state.watch(feed.observeTimeline(viewerId), value -> posts = value);
        state.watch(social.observeUsers(), value -> users = value);
        state.watch(social.observeFollows(), value -> follows = value);
    }

    public LiveData<State> state() { return state; }
    public LiveData<Event<Message>> messages() { return messages; }
    public String viewerId() { return viewerId; }
    public LiveData<FeedPost> post(String postId) { return feed.observePost(postId, viewerId); }
    /** Pull posts made elsewhere (the website, other phones). */
    public void refresh() { feed.refresh(); }
    public void startLive() { feed.startLive(); }
    public void stopLive() { feed.stopLive(); }

    private State compute() {
        if (posts == null || users == null || follows == null) return null;
        Map<String, SocialUser> byId = new HashMap<>();
        for (SocialUser u : users) byId.put(u.id(), u);
        Set<String> following = new HashSet<>();
        for (Follow f : follows) if (f.followerUserId().equals(viewerId)) following.add(f.followedUserId());
        return new State(viewerId, List.copyOf(posts), byId, following);
    }

    public void createPost(String body) { run(Action.CREATE, () -> feed.createPost(viewerId, body)); }
    public void editPost(String postId, String body) { run(Action.EDIT, () -> feed.editPost(viewerId, postId, body)); }
    public void deletePost(String postId) { run(Action.DELETE, () -> feed.deletePost(viewerId, postId)); }
    public void toggleLike(String postId) { run(Action.LIKE, () -> feed.toggleLike(viewerId, postId)); }

    private void run(Action action, Supplier<FeedResult> operation) {
        executors.network().execute(() -> {
            FeedResult result;
            try { result = operation.get(); } catch (RuntimeException error) { result = FeedResult.INVALID_INPUT; }
            FeedResult finalResult = result;
            executors.main().execute(() -> messages.setValue(new Event<>(new Message(action, finalResult))));
        });
    }
}
