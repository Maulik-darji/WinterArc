package com.app.winterarc.ui.feed;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.winterarc.R;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.databinding.FragmentPostDetailBinding;
import com.app.winterarc.databinding.ViewTextInputBinding;
import com.app.winterarc.domain.feed.CommentThreads;
import com.app.winterarc.domain.feed.FeedPost;
import com.app.winterarc.domain.feed.FeedResult;
import com.app.winterarc.domain.feed.FeedRules;
import com.app.winterarc.domain.feed.PostComment;
import com.app.winterarc.ui.common.Haptics;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/** A post with its threaded replies. Anyone can reply; authors can edit or delete their own. */
@AndroidEntryPoint
public class PostDetailFragment extends Fragment implements PostDetailAdapter.Listener {
    public static final String ARG_FOCUS_REPLY = "focusReply";

    @Inject TimeProvider time;

    private FragmentPostDetailBinding binding;
    private PostDetailViewModel viewModel;
    private PostDetailAdapter adapter;
    private PostDetailViewModel.State current;
    /** The comment being replied to; null replies to the post itself. */
    @Nullable private String replyToId;
    private boolean sending;
    /** Thread size when a top-level comment was sent; scroll to the end once the thread grows. */
    private int scrollWhenThreadExceeds = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentPostDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Ui.applySystemBarPadding(binding.postRoot, true, false);
        Ui.applySystemBarPadding(binding.composer, false, true);
        viewModel = new ViewModelProvider(this).get(PostDetailViewModel.class);
        binding.toolbar.setNavigationOnClickListener(v -> Nav.up(this));

        adapter = new PostDetailAdapter(this, time);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);

        binding.replyInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(FeedRules.COMMENT_MAX)});
        binding.replyInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateSend(); }
        });
        binding.send.setOnClickListener(v -> send());
        binding.cancelReply.setOnClickListener(v -> {
            Haptics.soft(v);
            setReplyTarget(null);
        });

        viewModel.state().observe(getViewLifecycleOwner(), s -> {
            current = s;
            boolean scroll = scrollWhenThreadExceeds >= 0 && s.thread().size() > scrollWhenThreadExceeds;
            if (scroll) scrollWhenThreadExceeds = -1;
            adapter.submit(s, () -> {
                if (scroll && binding != null) binding.list.smoothScrollToPosition(adapter.getItemCount() - 1);
            });
            binding.composer.setVisibility(s.post() == null ? View.GONE : View.VISIBLE);
            if (replyToId != null && find(replyToId) == null) setReplyTarget(null); // target was deleted
            else renderReplyTarget();
        });
        viewModel.messages().observe(getViewLifecycleOwner(), event -> {
            PostDetailViewModel.Message m = event.consume();
            if (m != null) onResult(m);
        });

        if (state == null && getArguments() != null && getArguments().getBoolean(ARG_FOCUS_REPLY)) focusComposer();
        updateSend();
    }

    private void onResult(PostDetailViewModel.Message m) {
        if (m.result() != FeedResult.SUCCESS) {
            if (m.action() == PostDetailViewModel.Action.COMMENT) {
                sending = false;
                scrollWhenThreadExceeds = -1;
                updateSend();
            }
            Haptics.reject(binding.list);
            snack(PostActions.errorText(m.result()));
            return;
        }
        switch (m.action()) {
            case COMMENT:
                sending = false;
                binding.replyInput.setText("");
                setReplyTarget(null);
                Ui.hideKeyboard(binding.replyInput);
                binding.replyInput.clearFocus();
                Haptics.confirm(binding.send);
                break;
            case EDIT_COMMENT:
                snack(R.string.comment_saved);
                break;
            case DELETE_COMMENT:
                snack(R.string.comment_removed);
                break;
            case DELETE_POST:
                Nav.up(this);
                break;
            default:
                break;
        }
    }

    private void send() {
        String text = binding.replyInput.getText() == null ? "" : binding.replyInput.getText().toString();
        if (sending || FeedRules.clean(text, FeedRules.COMMENT_MAX) == null) {
            Haptics.reject(binding.send);
            return;
        }
        sending = true;
        if (replyToId == null && current != null) scrollWhenThreadExceeds = current.thread().size();
        updateSend();
        viewModel.comment(replyToId, text);
    }

    private void updateSend() {
        CharSequence text = binding.replyInput.getText();
        binding.send.setEnabled(!sending && text != null
                && FeedRules.clean(text.toString(), FeedRules.COMMENT_MAX) != null);
    }

    private void setReplyTarget(@Nullable String commentId) {
        replyToId = commentId;
        renderReplyTarget();
    }

    private void renderReplyTarget() {
        PostComment target = replyToId == null ? null : find(replyToId);
        if (target == null || current == null) {
            binding.replyingRow.setVisibility(View.GONE);
            binding.replyLayout.setHint(R.string.comment_hint);
            return;
        }
        String handle = FeedFormat.handle(requireContext(), current.users(), target.authorId());
        binding.replyingRow.setVisibility(View.VISIBLE);
        binding.replyingText.setText(getString(R.string.comment_replying_to, handle));
        binding.replyLayout.setHint(getString(R.string.comment_reply_hint, handle));
    }

    @Nullable
    private PostComment find(String commentId) {
        if (current == null) return null;
        for (CommentThreads.Node n : current.thread()) {
            if (n.comment().id().equals(commentId) && !n.comment().deleted()) return n.comment();
        }
        return null;
    }

    private void focusComposer() {
        binding.replyInput.requestFocus();
        binding.replyInput.post(() -> {
            if (binding == null) return;
            InputMethodManager imm = requireContext().getSystemService(InputMethodManager.class);
            if (imm != null) imm.showSoftInput(binding.replyInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    @Override
    public void openProfile(String userId) {
        Haptics.tick(binding.list);
        Nav.go(this, R.id.userProfileFragment, Nav.user(userId));
    }

    // Post actions

    @Override
    public void replyToPost() {
        Haptics.tick(binding.list);
        setReplyTarget(null);
        focusComposer();
    }

    @Override
    public void likePost(View button) {
        if (current == null || current.post() == null) return;
        Haptics.toggle(button, !current.post().likedByMe());
        viewModel.toggleLike();
    }

    @Override
    public void sharePost() {
        FeedPost post = current == null ? null : current.post();
        if (post == null) return;
        PostActions.share(this, FeedFormat.name(requireContext(), current.users(), post.authorId()), post.body());
    }

    @Override
    public void postMore(View anchor) {
        FeedPost post = current == null ? null : current.post();
        if (post == null) return;
        Haptics.tick(anchor);
        PostActions.showOwnerMenu(anchor, R.string.feed_edit_post, R.string.feed_delete_post,
                () -> Nav.go(this, R.id.composePostFragment, Nav.post(post.id())),
                () -> PostActions.confirm(requireContext(), R.string.feed_delete_post_title,
                        R.string.feed_delete_post_body, R.string.comment_delete, () -> {
                            Haptics.confirm(anchor);
                            viewModel.deletePost();
                        }));
    }

    // Comment actions

    @Override
    public void replyTo(CommentThreads.Node node) {
        Haptics.tick(binding.list);
        setReplyTarget(node.comment().id());
        focusComposer();
    }

    @Override
    public void commentMore(View anchor, CommentThreads.Node node) {
        PostComment comment = node.comment();
        Haptics.tick(anchor);
        PostActions.showOwnerMenu(anchor, R.string.comment_edit, R.string.comment_delete,
                () -> editComment(comment),
                () -> PostActions.confirm(requireContext(), R.string.comment_delete_title,
                        R.string.comment_delete_body, R.string.comment_delete, () -> {
                            Haptics.confirm(anchor);
                            viewModel.deleteComment(comment.id());
                        }));
    }

    private void editComment(PostComment comment) {
        ViewTextInputBinding field = ViewTextInputBinding.inflate(getLayoutInflater());
        field.inputLayout.setCounterMaxLength(FeedRules.COMMENT_MAX);
        field.input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(FeedRules.COMMENT_MAX)});
        field.input.setText(comment.body());
        field.input.setSelection(comment.body().length());
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.comment_edit_title)
                .setView(field.getRoot())
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = field.input.getText() == null ? "" : field.input.getText().toString();
            if (FeedRules.clean(text, FeedRules.COMMENT_MAX) == null) {
                Haptics.reject(v);
                field.inputLayout.setError(getString(R.string.feed_error_invalid));
                return;
            }
            Haptics.confirm(v);
            viewModel.editComment(comment.id(), text);
            dialog.dismiss();
        }));
        dialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refresh(); // replies may have been added on the website meanwhile
        viewModel.startLive(); // and new ones appear as they are posted
    }

    @Override
    public void onPause() {
        super.onPause();
        viewModel.stopLive();
    }

    private void snack(int text) {
        if (text != 0 && binding != null) {
            Snackbar.make(binding.postRoot, text, Snackbar.LENGTH_SHORT).setAnchorView(binding.composer).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
