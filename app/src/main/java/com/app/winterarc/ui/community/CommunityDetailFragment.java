package com.app.winterarc.ui.community;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentCommunityDetailBinding;
import com.app.winterarc.domain.social.Community;
import com.app.winterarc.domain.social.CommunityAction;
import com.app.winterarc.domain.social.CommunityMember;
import com.app.winterarc.domain.social.CommunityMessage;
import com.app.winterarc.domain.social.MemberRole;
import com.app.winterarc.domain.social.SocialPolicy;
import com.app.winterarc.domain.social.SocialResult;
import com.app.winterarc.domain.social.SocialUser;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.app.winterarc.ui.social.SocialViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CommunityDetailFragment extends Fragment {
    private FragmentCommunityDetailBinding binding;
    private SocialViewModel viewModel;
    private SocialViewModel.State social;
    private List<CommunityMessage> messageList = Collections.emptyList();
    private String communityId;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentCommunityDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        communityId = requireArguments().getString(Nav.ARG_COMMUNITY_ID, "");
        Ui.applySystemBarPadding(binding.communityDetailRoot, true, false);
        Ui.applySystemBarPadding(binding.composer, false, true);
        binding.toolbar.setNavigationOnClickListener(v -> Nav.up(this));
        viewModel = new ViewModelProvider(requireActivity()).get(SocialViewModel.class);
        viewModel.state().observe(getViewLifecycleOwner(), value -> { social = value; render(); });
        viewModel.messages(communityId).observe(getViewLifecycleOwner(), value -> {
            messageList = value == null ? Collections.emptyList() : value;
            renderMessages();
        });
        viewModel.messages().observe(getViewLifecycleOwner(), event -> {
            SocialViewModel.Message message = event.consume();
            if (message == null || message.result() == SocialResult.SUCCESS) return;
            Snackbar.make(binding.communityDetailRoot,
                    message.result() == SocialResult.PERMISSION_DENIED ? R.string.community_action_denied
                            : R.string.community_action_failed, Snackbar.LENGTH_SHORT).show();
        });
        binding.send.setOnClickListener(v -> send());
        binding.message.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEND) return false;
            send(); return true;
        });
    }

    private void render() {
        if (binding == null || social == null) return;
        Community community = null;
        for (Community candidate : social.communities()) if (candidate.id().equals(communityId)) community = candidate;
        if (community == null) { Nav.up(this); return; }
        Community finalCommunity = community;
        binding.toolbar.setTitle(community.name());
        binding.name.setText(community.name());
        binding.avatar.setText(community.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT));
        binding.description.setText(community.description());
        binding.memberCount.setText(getString(R.string.community_members_count,
                CommunityFragment.memberCount(social, communityId)));
        CommunityMember membership = CommunityFragment.membership(social, communityId, social.currentUserId());
        boolean member = membership != null;
        boolean owner = member && membership.role() == MemberRole.OWNER;
        binding.manage.setVisibility(owner ? View.VISIBLE : View.GONE);
        binding.manage.setOnClickListener(v -> Nav.go(this, R.id.communitySettingsFragment, Nav.community(communityId)));
        binding.membershipAction.setVisibility(owner ? View.GONE : View.VISIBLE);
        binding.membershipAction.setText(member ? R.string.community_leave : R.string.community_join);
        binding.membershipAction.setOnClickListener(v -> {
            if (!member) viewModel.join(communityId);
            else new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(getString(R.string.community_leave_confirm, finalCommunity.name()))
                    .setPositiveButton(R.string.community_leave, (d, w) -> viewModel.leave(communityId))
                    .setNegativeButton(R.string.action_cancel, null).show();
        });
        boolean canChat = member && SocialPolicy.can(membership.role(), community.permissions(), CommunityAction.CHAT);
        binding.scroll.setVisibility(member ? View.VISIBLE : View.GONE);
        binding.accessMessage.setVisibility(canChat ? View.GONE : View.VISIBLE);
        binding.accessMessage.setText(!member ? R.string.community_chat_join : R.string.community_chat_disabled);
        binding.composer.setVisibility(canChat ? View.VISIBLE : View.GONE);
        renderMessages();
    }

    private void renderMessages() {
        if (binding == null || social == null || binding.scroll.getVisibility() != View.VISIBLE) return;
        Map<String, SocialUser> users = CommunityFragment.users(social);
        binding.messages.removeAllViews();
        TextView intro = bubble(getString(R.string.community_chat_welcome), false, true);
        binding.messages.addView(intro);
        for (CommunityMessage message : messageList) {
            SocialUser sender = users.get(message.senderUserId());
            boolean mine = message.senderUserId().equals(social.currentUserId());
            String text = mine ? message.body() : (sender == null ? "Member" : sender.displayName()) + "\n" + message.body();
            binding.messages.addView(bubble(text, mine, false));
        }
        binding.scroll.post(() -> binding.scroll.fullScroll(View.FOCUS_DOWN));
    }

    private TextView bubble(String text, boolean sent, boolean system) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(system ? 12 : 16);
        view.setMaxWidth(Ui.dp(requireContext(), system ? 320 : 286));
        int h = Ui.dp(requireContext(), system ? 8 : 16), v = Ui.dp(requireContext(), system ? 6 : 12);
        view.setPadding(h, v, h, v);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(requireContext(), system ? 12 : 18));
        int color = system ? R.color.wa_surface_container : sent ? R.color.wa_primary_container : R.color.wa_surface_variant;
        int textColor = system ? R.color.wa_on_surface_variant : sent ? R.color.wa_on_primary_container : R.color.wa_on_surface;
        bg.setColor(ContextCompat.getColor(requireContext(), color));
        view.setTextColor(ContextCompat.getColor(requireContext(), textColor));
        view.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = system ? Gravity.CENTER_HORIZONTAL : sent ? Gravity.END : Gravity.START;
        params.bottomMargin = Ui.dp(requireContext(), 10);
        view.setLayoutParams(params);
        return view;
    }

    private void send() {
        String text = binding.message.getText() == null ? "" : binding.message.getText().toString().trim();
        if (text.isEmpty()) return;
        viewModel.sendMessage(communityId, text);
        binding.message.setText("");
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
