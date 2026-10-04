package com.app.winterarc.ui.community;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentCommunitySettingsBinding;
import com.app.winterarc.databinding.ItemCommunityMemberBinding;
import com.app.winterarc.domain.social.Community;
import com.app.winterarc.domain.social.CommunityMember;
import com.app.winterarc.domain.social.CommunityPermissions;
import com.app.winterarc.domain.social.MemberRole;
import com.app.winterarc.domain.social.SocialResult;
import com.app.winterarc.domain.social.SocialUser;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.app.winterarc.ui.social.SocialViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CommunitySettingsFragment extends Fragment {
    private FragmentCommunitySettingsBinding binding;
    private SocialViewModel viewModel;
    private String communityId;
    private Community current;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentCommunitySettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        communityId = requireArguments().getString(Nav.ARG_COMMUNITY_ID, "");
        Ui.applySystemBarPadding(binding.settingsRoot, true, true);
        binding.toolbar.setNavigationOnClickListener(v -> Nav.up(this));
        viewModel = new ViewModelProvider(requireActivity()).get(SocialViewModel.class);
        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        binding.savePermissions.setOnClickListener(v -> viewModel.updatePermissions(communityId,
                new CommunityPermissions(binding.canPost.isChecked(), binding.canChat.isChecked(), binding.canInvite.isChecked())));
        viewModel.messages().observe(getViewLifecycleOwner(), event -> {
            SocialViewModel.Message message = event.consume();
            if (message == null) return;
            int text = message.result() != SocialResult.SUCCESS ? R.string.community_action_failed
                    : message.action().equals("permissions") ? R.string.community_permissions_saved
                    : message.action().equals("role") ? R.string.community_role_updated : 0;
            if (text != 0) Snackbar.make(binding.settingsRoot, text, Snackbar.LENGTH_SHORT).show();
        });
    }

    private void render(SocialViewModel.State state) {
        current = null;
        for (Community c : state.communities()) if (c.id().equals(communityId)) current = c;
        CommunityMember me = CommunityFragment.membership(state, communityId, state.currentUserId());
        if (current == null || me == null || me.role() != MemberRole.OWNER) { Nav.up(this); return; }
        binding.toolbar.setTitle(current.name());
        binding.canPost.setChecked(current.permissions().membersCanPost());
        binding.canChat.setChecked(current.permissions().membersCanChat());
        binding.canInvite.setChecked(current.permissions().membersCanInvite());
        Map<String, SocialUser> users = CommunityFragment.users(state);
        binding.members.removeAllViews();
        for (CommunityMember member : state.members()) {
            if (!member.communityId().equals(communityId)) continue;
            SocialUser user = users.get(member.userId());
            if (user == null) continue;
            ItemCommunityMemberBinding item = ItemCommunityMemberBinding.inflate(getLayoutInflater(), binding.members, false);
            item.avatar.setText(user.initial());
            item.name.setText(user.displayName());
            item.role.setText(roleText(member.role()));
            boolean changeable = member.role() != MemberRole.OWNER;
            item.changeRole.setVisibility(changeable ? View.VISIBLE : View.GONE);
            item.changeRole.setText(member.role() == MemberRole.ADMIN ? R.string.community_make_member : R.string.community_make_admin);
            item.changeRole.setOnClickListener(v -> viewModel.setRole(communityId, member.userId(),
                    member.role() == MemberRole.ADMIN ? MemberRole.MEMBER : MemberRole.ADMIN));
            binding.members.addView(item.getRoot());
        }
    }

    private int roleText(MemberRole role) {
        return role == MemberRole.OWNER ? R.string.community_role_owner
                : role == MemberRole.ADMIN ? R.string.community_role_admin : R.string.community_role_member;
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
