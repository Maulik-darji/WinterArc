package com.app.winterarc.ui.community;

import android.os.Bundle;
import com.app.winterarc.ui.common.Haptics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentCommunityBinding;
import com.app.winterarc.databinding.ItemCommunityCardBinding;
import com.app.winterarc.databinding.ItemJoinedCommunityBinding;
import com.app.winterarc.domain.social.Community;
import com.app.winterarc.domain.social.CommunityMember;
import com.app.winterarc.domain.social.MemberRole;
import com.app.winterarc.domain.social.SocialResult;
import com.app.winterarc.domain.social.SocialUser;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.app.winterarc.ui.social.SocialViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.HashMap;
import java.util.Map;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CommunityFragment extends Fragment {
    private FragmentCommunityBinding binding;
    private SocialViewModel viewModel;
    private SocialViewModel.State currentState;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentCommunityBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(requireActivity()).get(SocialViewModel.class);
        binding.createCommunity.setOnClickListener(v -> openCreateCommunity());
        binding.communityTabs.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) Haptics.tick(group);
            if (!checked) return;
            boolean mine = id == R.id.tab_my_communities;
            binding.discoverPanel.setVisibility(mine ? View.GONE : View.VISIBLE);
            binding.myCommunitiesPanel.setVisibility(mine ? View.VISIBLE : View.GONE);
            binding.scroll.scrollTo(0, 0);
        });
        viewModel.state().observe(getViewLifecycleOwner(), social -> {
            currentState = social;
            render(social);
        });
        viewModel.messages().observe(getViewLifecycleOwner(), event -> {
            SocialViewModel.Message message = event.consume();
            if (message == null) return;
            int text = message.result() == SocialResult.PERMISSION_DENIED ? R.string.community_action_denied
                    : message.result() == SocialResult.SUCCESS && message.action().equals("create_community") ? R.string.community_created
                    : message.result() == SocialResult.SUCCESS && message.action().equals("create_post") ? R.string.community_posted
                    : message.result() == SocialResult.SUCCESS && message.action().equals("join") ? R.string.community_join_success
                    : message.result() == SocialResult.SUCCESS ? 0 : R.string.community_action_failed;
            if (text != 0) Snackbar.make(binding.communityRoot, text, Snackbar.LENGTH_SHORT)
                    .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation)).show();
            if (message.result() == SocialResult.SUCCESS && message.action().equals("create_community")) {
                binding.tabMyCommunities.setChecked(true);
            }
        });
    }

    private void render(SocialViewModel.State state) {
        renderDiscover(state);
        renderJoined(state);
    }

    private void renderDiscover(SocialViewModel.State state) {
        binding.communities.removeAllViews();
        for (Community community : state.communities()) {
            ItemCommunityCardBinding card = ItemCommunityCardBinding.inflate(getLayoutInflater(), binding.communities, false);
            CommunityMember mine = membership(state, community.id(), state.currentUserId());
            int members = memberCount(state, community.id());
            card.avatar.setText(community.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT));
            card.title.setText(community.name());
            card.members.setText(getString(R.string.community_members, String.valueOf(members)));
            card.join.setText(mine == null ? R.string.community_join : R.string.community_open);
            card.join.setIconResource(mine == null ? 0 : R.drawable.ic_check);
            card.join.setOnClickListener(v -> {
                Haptics.confirm(v);
                if (mine == null) viewModel.join(community.id()); else openCommunity(community.id());
            });
            card.getRoot().setOnClickListener(v -> openCommunity(community.id()));
            binding.communities.addView(card.getRoot());
        }
        binding.discoverEmpty.setVisibility(state.communities().isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void renderJoined(SocialViewModel.State state) {
        binding.joinedCommunities.removeAllViews();
        int count = 0;
        for (Community community : state.communities()) {
            CommunityMember member = membership(state, community.id(), state.currentUserId());
            if (member == null) continue;
            count++;
            ItemJoinedCommunityBinding item = ItemJoinedCommunityBinding.inflate(
                    getLayoutInflater(), binding.joinedCommunities, false);
            item.avatar.setText(community.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT));
            item.title.setText(community.name());
            item.summary.setText(community.description());
            item.role.setText(roleText(member.role()));
            item.getRoot().setOnClickListener(v -> openCommunity(community.id()));
            binding.joinedCommunities.addView(item.getRoot());
        }
        binding.joinedEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private void openCreateCommunity() {
        LinearLayout fields = new LinearLayout(requireContext());
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Ui.dp(requireContext(), 20);
        fields.setPadding(padding, 0, padding, 0);
        EditText name = new EditText(requireContext());
        name.setHint(R.string.community_name_hint);
        name.setSingleLine(true);
        EditText description = new EditText(requireContext());
        description.setHint(R.string.community_description_hint);
        description.setMinLines(3);
        fields.addView(name);
        fields.addView(description);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.community_create_title)
                .setView(fields)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.community_create, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (name.getText().toString().trim().length() < 3) { name.setError(getString(R.string.community_name_hint)); return; }
                    viewModel.createCommunity(name.getText().toString(), description.getText().toString());
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void openCommunity(String id) { Nav.go(this, R.id.communityDetailFragment, Nav.community(id)); }

    private int roleText(MemberRole role) {
        return role == MemberRole.OWNER ? R.string.community_role_owner
                : role == MemberRole.ADMIN ? R.string.community_role_admin : R.string.community_role_member;
    }

    static Map<String, SocialUser> users(SocialViewModel.State state) {
        Map<String, SocialUser> result = new HashMap<>();
        for (SocialUser user : state.users()) result.put(user.id(), user);
        return result;
    }

    static Map<String, Community> communities(SocialViewModel.State state) {
        Map<String, Community> result = new HashMap<>();
        for (Community community : state.communities()) result.put(community.id(), community);
        return result;
    }

    static CommunityMember membership(SocialViewModel.State state, String communityId, String userId) {
        for (CommunityMember member : state.members()) {
            if (member.communityId().equals(communityId) && member.userId().equals(userId)) return member;
        }
        return null;
    }

    static int memberCount(SocialViewModel.State state, String communityId) {
        int count = 0;
        for (CommunityMember member : state.members()) if (member.communityId().equals(communityId)) count++;
        return count;
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
