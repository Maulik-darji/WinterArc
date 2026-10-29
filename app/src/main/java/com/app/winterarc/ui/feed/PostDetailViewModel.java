package com.app.winterarc.ui.feed;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.feed.CommentThreads;
import com.app.winterarc.domain.feed.FeedPost;
import com.app.winterarc.domain.feed.FeedRepository;
import com.app.winterarc.domain.feed.FeedResult;
import com.app.winterarc.domain.feed.PostComment;
import com.app.winterarc.domain.repository.SocialRepository;
import com.app.winterarc.domain.social.SocialUser;
import com.app.winterarc.ui.common.Combiner;
import com.app.winterarc.ui.common.Event;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.social.SocialViewModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/** One post with its threaded comments, plus every action available on that screen. */
@HiltViewModel
public class PostDetailViewModel extends ViewModel {
    /** {@code post} is null once the post has been deleted. */
    public record State(String viewerId, @Nullable FeedPost post, List<CommentThreads.Node> thread,
                        Map<String, SocialUser> users) {}

    public record Message(Action action, FeedResult result) {}

    public enum Action { COMMENT, EDIT_COMMENT, DELETE_COMMENT, LIKE, EDIT_POST, DELETE_POST }

    private final FeedRepository feed;
    private final AppExecutors executors;
    private final String viewerId;
    private final String postId;
    private final Combiner<State> state;
    private final MutableLiveData<Event<Message>> messages = new MutableLiveData<>();
    private volatile boolean postLoaded;
    private volatile FeedPost post;
    private volatile List<PostComment> comments;
    private volatile List<SocialUser> users;

    @Inject
    public PostDetailViewModel(SavedStateHandle handle, FeedRepository feed, SocialRepository social,
                               AuthRepository auth, AppExecutors executors) {
        this.feed = feed;
        this.executors = executors;
        String id = handle.get(Nav.ARG_POST_ID);
        postId = id == null ? "" : id;
        viewerId = SocialViewModel.ensureCurrentProfile(auth, social, executors);
        state = new Combiner<>(executors, this::compute);
        state.watch(feed.observePost(postId, viewerId), value -> { post = value; postLoaded = true; });
        state.watch(feed.observeComments(postId), value -> comments = value);
        state.watch(social.observeUsers(), value -> users = value);
    }

    public LiveData<State> state() { return state; }
    public LiveData<Event<Message>> messages() { return messages; }
    public String viewerId() { return viewerId; }
    public void refresh() { feed.refresh(); }
    public void startLive() { feed.startLive(); }
    public void stopLive() { feed.stopLive(); }

    private State compute() {
        if (!postLoaded || comments == null || users == null) return null;
        Map<String, SocialUser> byId = new HashMap<>();
        for (SocialUser u : users) byId.put(u.id(), u);
        return new State(viewerId, post, CommentThreads.build(comments), byId);
    }

    public void comment(@Nullable String parentCommentId, String body) {
        run(Action.COMMENT, () -> feed.addComment(viewerId, postId, parentCommentId, body));
    }
    public void editComment(String commentId, String body) { run(Action.EDIT_COMMENT, () -> feed.editComment(viewerId, commentId, body)); }
    public void deleteComment(String commentId) { run(Action.DELETE_COMMENT, () -> feed.deleteComment(viewerId, commentId)); }
    public void toggleLike() { run(Action.LIKE, () -> feed.toggleLike(viewerId, postId)); }
    public void deletePost() { run(Action.DELETE_POST, () -> feed.deletePost(viewerId, postId)); }

    private void run(Action action, Supplier<FeedResult> operation) {
        executors.network().execute(() -> {
            FeedResult result;
            try { result = operation.get(); } catch (RuntimeException error) { result = FeedResult.INVALID_INPUT; }
            FeedResult finalResult = result;
            executors.main().execute(() -> messages.setValue(new Event<>(new Message(action, finalResult))));
        });
    }
}
