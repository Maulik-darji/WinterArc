package com.app.winterarc.ui.feed;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentComposePostBinding;
import com.app.winterarc.domain.feed.FeedResult;
import com.app.winterarc.domain.feed.FeedRules;
import com.app.winterarc.ui.common.Haptics;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import dagger.hilt.android.AndroidEntryPoint;

/** Write a new post, or edit one of your own (when a postId argument is given). */
@AndroidEntryPoint
public class ComposePostFragment extends Fragment {
    /** Show the remaining count once this close to the limit, like X. */
    private static final int COUNTER_THRESHOLD = 40;

    private FragmentComposePostBinding binding;
    private FeedViewModel viewModel;
    @Nullable private String editingId;
    private String original = "";
    private boolean loaded;
    private boolean sending;
    private final OnBackPressedCallback discardGuard = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            confirmDiscard();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentComposePostBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Ui.applySystemBarPadding(binding.composeRoot, true, true);
        viewModel = new ViewModelProvider(requireActivity()).get(FeedViewModel.class);
        editingId = getArguments() == null ? null : getArguments().getString(Nav.ARG_POST_ID);
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), discardGuard);

        binding.title.setText(editingId == null ? R.string.feed_compose_title : R.string.feed_edit_title);
        binding.post.setText(editingId == null ? R.string.feed_compose_send : R.string.feed_save);
        binding.close.setOnClickListener(v -> {
            if (discardGuard.isEnabled()) confirmDiscard(); else close();
        });
        binding.post.setOnClickListener(v -> send());
        binding.body.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateCounter(); }
        });
        updateCounter();

        viewModel.state().observe(getViewLifecycleOwner(), s -> {
            String me = s.viewerId();
            FeedFormat.avatar(binding.avatar, s.users(), me);
        });
        if (editingId != null) {
            viewModel.post(editingId).observe(getViewLifecycleOwner(), post -> {
                if (post == null) {
                    if (!sending) close(); // deleted elsewhere
                    return;
                }
                if (loaded) return;
                loaded = true;
                original = post.body();
                binding.body.setText(post.body());
                binding.body.setSelection(binding.body.length());
            });
        }
        viewModel.messages().observe(getViewLifecycleOwner(), event -> {
            FeedViewModel.Message m = event.peek();
            if (m.action() != FeedViewModel.Action.CREATE && m.action() != FeedViewModel.Action.EDIT) return;
            if (event.consume() == null || !sending) return;
            sending = false;
            if (m.result() == FeedResult.SUCCESS) {
                Haptics.confirm(binding.post);
                close();
            } else {
                Haptics.reject(binding.post);
                updateCounter();
                Snackbar.make(binding.composeRoot, PostActions.errorText(m.result()), Snackbar.LENGTH_SHORT).show();
            }
        });

        binding.body.requestFocus();
        binding.body.post(() -> {
            if (binding == null) return;
            InputMethodManager imm = requireContext().getSystemService(InputMethodManager.class);
            if (imm != null) imm.showSoftInput(binding.body, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void updateCounter() {
        String text = binding.body.getText().toString();
        int remaining = FeedRules.remaining(text, FeedRules.POST_MAX);
        boolean valid = FeedRules.clean(text, FeedRules.POST_MAX) != null;
        boolean changed = !text.trim().equals(original.trim());
        binding.post.setEnabled(valid && changed && !sending);
        binding.counter.setVisibility(remaining <= COUNTER_THRESHOLD ? View.VISIBLE : View.GONE);
        binding.counter.setText(String.valueOf(remaining));
        binding.counter.setContentDescription(getString(R.string.feed_remaining_cd, remaining));
        binding.counter.setTextColor(remaining < 0
                ? MaterialColors.getColor(binding.counter, androidx.appcompat.R.attr.colorError)
                : MaterialColors.getColor(binding.counter, com.google.android.material.R.attr.colorOnSurfaceVariant));
        discardGuard.setEnabled(changed && !text.trim().isEmpty());
    }

    private void send() {
        String text = binding.body.getText().toString();
        if (FeedRules.clean(text, FeedRules.POST_MAX) == null) {
            Haptics.reject(binding.post);
            return;
        }
        sending = true;
        binding.post.setEnabled(false);
        if (editingId == null) viewModel.createPost(text); else viewModel.editPost(editingId, text);
    }

    private void confirmDiscard() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.feed_compose_discard)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_discard, (d, w) -> close())
                .show();
    }

    private void close() {
        if (binding != null) Ui.hideKeyboard(binding.body);
        discardGuard.setEnabled(false);
        Nav.up(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
