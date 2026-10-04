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
import com.app.winterarc.databinding.FragmentUserProfileBinding;
import com.app.winterarc.databinding.ItemJoinedCommunityBinding;
import com.app.winterarc.domain.social.Community;
import com.app.winterarc.domain.social.Follow;
import com.app.winterarc.domain.social.SocialUser;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.app.winterarc.ui.social.SocialViewModel;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class UserProfileFragment extends Fragment {
    private FragmentUserProfileBinding binding;
    private SocialViewModel viewModel;
    private String userId;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentUserProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        userId = requireArguments().getString(Nav.ARG_USER_ID, "");
        Ui.applySystemBarPadding(binding.userProfileRoot, true, true);
        binding.toolbar.setNavigationOnClickListener(v -> Nav.up(this));
        viewModel = new ViewModelProvider(requireActivity()).get(SocialViewModel.class);
        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        binding.follow.setOnClickListener(v -> viewModel.toggleFollow(userId));
    }

    private void render(SocialViewModel.State state) {
        SocialUser target = null;
        for (SocialUser user : state.users()) if (user.id().equals(userId)) target = user;
        if (target == null) { Nav.up(this); return; }
        binding.avatar.setText(target.initial());
        binding.name.setText(target.displayName());
        binding.handle.setText(target.handle());
        binding.bio.setText(target.bio());
        int followers = 0, following = 0;
        boolean mine = userId.equals(state.currentUserId()), followedByMe = false;
        for (Follow follow : state.follows()) {
            if (follow.followedUserId().equals(userId)) followers++;
            if (follow.followerUserId().equals(userId)) following++;
            if (follow.followerUserId().equals(state.currentUserId()) && follow.followedUserId().equals(userId)) followedByMe = true;
        }
        binding.follow.setVisibility(mine ? View.GONE : View.VISIBLE);
        binding.follow.setText(followedByMe ? R.string.community_following : R.string.community_follow);
        binding.followers.setText(followers + "\n" + getString(R.string.user_followers));
        binding.following.setText(following + "\n" + getString(R.string.user_following));
        binding.communities.removeAllViews();
        int created = 0;
        for (Community community : state.communities()) {
            if (!community.ownerUserId().equals(userId)) continue;
            created++;
            ItemJoinedCommunityBinding item = ItemJoinedCommunityBinding.inflate(getLayoutInflater(), binding.communities, false);
            item.avatar.setText(community.name().substring(0, 1));
            item.title.setText(community.name());
            item.summary.setText(community.description());
            item.role.setText(R.string.community_role_owner);
            item.getRoot().setOnClickListener(v -> Nav.go(this, R.id.communityDetailFragment, Nav.community(community.id())));
            binding.communities.addView(item.getRoot());
        }
        binding.empty.setVisibility(created == 0 ? View.VISIBLE : View.GONE);
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
