package com.app.winterarc.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentProfileBinding;
import com.app.winterarc.databinding.ItemJoinedCommunityBinding;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;
import com.app.winterarc.domain.social.Community;
import com.app.winterarc.domain.social.CommunityMember;
import com.app.winterarc.domain.social.SocialUser;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.app.winterarc.ui.home.HomeViewModel;
import com.app.winterarc.ui.social.SocialViewModel;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProfileFragment extends Fragment {
    @Inject AuthRepository auth;
    private FragmentProfileBinding binding;
    private HomeViewModel homeViewModel;
    private SocialViewModel socialViewModel;
    private SocialUser currentUser;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Ui.applySystemBarPadding(binding.profileRoot, true, false);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        socialViewModel = new ViewModelProvider(requireActivity()).get(SocialViewModel.class);
        AuthUser user = auth.getCurrentUser();
        if (user != null) binding.account.setText(user.email() != null ? user.email() : "");
        binding.editProfile.setOnClickListener(v -> editProfile());
        binding.archive.setOnClickListener(v -> Nav.go(this, R.id.archiveFragment));
        binding.settings.setOnClickListener(v -> Nav.go(this, R.id.settingsFragment));
        binding.createCommunity.setOnClickListener(v -> Nav.go(this, R.id.feedFragment, com.app.winterarc.ui.feed.FeedFragment.args(com.app.winterarc.ui.feed.FeedFragment.TAB_COMMUNITIES)));
        homeViewModel.state().observe(getViewLifecycleOwner(), home -> {
            binding.activeGoals.setText(String.valueOf(home.cards().size()));
            binding.todayDone.setText(String.valueOf(Math.max(0, home.scheduledToday() - home.remainingToday())));
        });
        socialViewModel.state().observe(getViewLifecycleOwner(), this::renderSocial);
    }

    private void renderSocial(SocialViewModel.State state) {
        currentUser = null;
        for (SocialUser user : state.users()) if (user.id().equals(state.currentUserId())) currentUser = user;
        if (currentUser != null) {
            binding.name.setText(currentUser.displayName());
            com.app.winterarc.ui.common.Avatars.bind(binding.avatar, currentUser.initial(), currentUser.avatarUrl());
            com.app.winterarc.ui.common.Avatars.banner(binding.banner, currentUser.bannerUrl());
            boolean hasBio = !currentUser.bio().isBlank();
            binding.bio.setVisibility(hasBio ? View.VISIBLE : View.GONE);
            binding.bio.setText(currentUser.bio());
        }
        int joined = 0;
        for (CommunityMember member : state.members()) if (member.userId().equals(state.currentUserId())) joined++;
        binding.communityCount.setText(String.valueOf(joined));
        binding.createdCommunities.removeAllViews();
        int created = 0;
        for (Community community : state.communities()) {
            if (!community.ownerUserId().equals(state.currentUserId())) continue;
            created++;
            ItemJoinedCommunityBinding item = ItemJoinedCommunityBinding.inflate(getLayoutInflater(), binding.createdCommunities, false);
            item.avatar.setText(community.name().substring(0, 1).toUpperCase(java.util.Locale.ROOT));
            item.title.setText(community.name());
            item.summary.setText(community.description());
            item.role.setText(R.string.community_role_owner);
            item.getRoot().setOnClickListener(v -> Nav.go(this, R.id.communityDetailFragment, Nav.community(community.id())));
            binding.createdCommunities.addView(item.getRoot());
        }
        binding.createCommunity.setText(created == 0 ? R.string.profile_create_community : R.string.community_create);
    }

    @Override public void onResume() {
        super.onResume();
        if (homeViewModel != null) homeViewModel.refresh(java.time.DayOfWeek.MONDAY);
    }

    private void editProfile() {
        if (currentUser == null || getChildFragmentManager().findFragmentByTag(EditProfileSheet.TAG) != null) return;
        new EditProfileSheet().show(getChildFragmentManager(), EditProfileSheet.TAG);
    }

    @Override public void onDestroyView() { super.onDestroyView(); binding = null; }
}
