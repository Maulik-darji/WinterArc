package com.app.winterarc.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.winterarc.R;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.databinding.FragmentPostsBinding;
import com.app.winterarc.domain.feed.FeedPost;
import com.app.winterarc.ui.common.Haptics;
import com.app.winterarc.ui.common.Nav;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/** The Posts tab: an X-style timeline with For you / Following and a compose button. */
@AndroidEntryPoint
public class PostsFragment extends Fragment implements PostAdapter.Listener {
    @Inject TimeProvider time;

    private FragmentPostsBinding binding;
    private FeedViewModel viewModel;
    private PostAdapter adapter;
    private FeedViewModel.State current;
    private boolean followingOnly;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentPostsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(requireActivity()).get(FeedViewModel.class);
        adapter = new PostAdapter(this, time);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        MaterialDividerItemDecoration divider = new MaterialDividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL);
        divider.setLastItemDecorated(false);
        binding.list.addItemDecoration(divider);

        binding.filters.setOnCheckedStateChangeListener((group, ids) -> {
            Haptics.tick(group);
            followingOnly = !ids.isEmpty() && ids.get(0) == R.id.filter_following;
            render();
            binding.list.scrollToPosition(0);
        });
        View.OnClickListener compose = v -> Haptics.tickThen(v, () -> Nav.go(this, R.id.composePostFragment));
        binding.compose.setOnClickListener(compose);
        binding.emptyCompose.setOnClickListener(compose);

        viewModel.state().observe(getViewLifecycleOwner(), s -> {
            boolean grew = current != null && s.posts().size() > current.posts().size()
                    && !s.posts().isEmpty() && s.posts().get(0).authorId().equals(s.viewerId());
            current = s;
            render();
            if (grew) binding.list.scrollToPosition(0);
        });
        viewModel.messages().observe(getViewLifecycleOwner(), event -> {
            FeedViewModel.Message m = event.consume();
            if (m == null) return;
            int text = m.result() == com.app.winterarc.domain.feed.FeedResult.SUCCESS
                    ? (m.action() == FeedViewModel.Action.DELETE ? R.string.feed_post_deleted : 0)
                    : PostActions.errorText(m.result());
            if (text != 0) Snackbar.make(binding.postsRoot, text, Snackbar.LENGTH_SHORT).setAnchorView(binding.compose).show();
        });
    }

    private void render() {
        if (current == null) return;
        List<FeedPost> shown = new ArrayList<>();
        for (FeedPost p : current.posts()) {
            if (!followingOnly || current.following().contains(p.authorId())) shown.add(p);
        }
        adapter.setContext(current.viewerId(), current.users());
        adapter.submitList(shown);
        boolean empty = shown.isEmpty();
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.list.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.emptyTitle.setText(followingOnly ? R.string.feed_filter_following : R.string.feed_empty_title);
        binding.emptyBody.setText(followingOnly ? R.string.feed_following_empty : R.string.feed_empty_body);
        binding.emptyCompose.setVisibility(followingOnly ? View.GONE : View.VISIBLE);
    }

    @Override
    public void open(FeedPost post) {
        Nav.go(this, R.id.postDetailFragment, Nav.post(post.id()));
    }

    @Override
    public void openProfile(String userId) {
        Haptics.tick(binding.list);
        Nav.go(this, R.id.userProfileFragment, Nav.user(userId));
    }

    @Override
    public void reply(FeedPost post) {
        Bundle args = Nav.post(post.id());
        args.putBoolean(PostDetailFragment.ARG_FOCUS_REPLY, true);
        Haptics.tick(binding.list);
        Nav.go(this, R.id.postDetailFragment, args);
    }

    @Override
    public void like(View button, FeedPost post) {
        Haptics.toggle(button, !post.likedByMe());
        viewModel.toggleLike(post.id());
    }

    @Override
    public void share(FeedPost post) {
        if (current == null) return;
        PostActions.share(this, FeedFormat.name(requireContext(), current.users(), post.authorId()), post.body());
    }

    @Override
    public void more(View anchor, FeedPost post) {
        Haptics.tick(anchor);
        PostActions.showOwnerMenu(anchor, R.string.feed_edit_post, R.string.feed_delete_post,
                () -> Nav.go(this, R.id.composePostFragment, Nav.post(post.id())),
                () -> PostActions.confirm(requireContext(), R.string.feed_delete_post_title,
                        R.string.feed_delete_post_body, R.string.comment_delete, () -> {
                            Haptics.confirm(binding.list);
                            viewModel.deletePost(post.id());
                        }));
    }

    /** While the timeline is on screen, pick up posts from the website and other devices. */
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (binding == null) return;
            viewModel.refresh();
            binding.getRoot().postDelayed(this, REFRESH_MS);
        }
    };
    /** Safety net only; Realtime delivers changes as they happen. */
    private static final long REFRESH_MS = 60_000;

    private boolean live;

    /** Live updates (and the fallback poll) run only while the timeline is actually on screen. */
    private void setLive(boolean on) {
        if (binding == null || on == live) return;
        live = on;
        binding.getRoot().removeCallbacks(poll);
        if (on) {
            viewModel.startLive();
            binding.getRoot().post(poll);
        } else {
            viewModel.stopLive();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setLive(!isHidden());
    }

    @Override
    public void onPause() {
        super.onPause();
        setLive(false);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        setLive(!hidden && isResumed());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
