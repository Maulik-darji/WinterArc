package com.app.winterarc.ui.feed;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.app.winterarc.databinding.FragmentFeedBinding;
import com.app.winterarc.ui.common.Haptics;
import com.app.winterarc.ui.common.Ui;
import com.app.winterarc.ui.community.CommunityFragment;
import com.google.android.material.tabs.TabLayout;

import dagger.hilt.android.AndroidEntryPoint;

/** Top-level Feed destination: Posts and Communities tabs, each a retained child fragment. */
@AndroidEntryPoint
public class FeedFragment extends Fragment {
    public static final String ARG_TAB = "tab";
    public static final int TAB_POSTS = 0;
    public static final int TAB_COMMUNITIES = 1;
    private static final String[] TAGS = {"posts", "communities"};
    private static final String STATE_TAB = "feed_tab";

    private FragmentFeedBinding binding;
    private int tab = -1;

    public static Bundle args(int tab) {
        Bundle b = new Bundle();
        b.putInt(ARG_TAB, tab);
        return b;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentFeedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Ui.applySystemBarPadding(binding.feedRoot, true, false);
        if (tab < 0) {
            tab = state != null ? state.getInt(STATE_TAB, TAB_POSTS)
                    : getArguments() != null ? getArguments().getInt(ARG_TAB, TAB_POSTS) : TAB_POSTS;
        }
        TabLayout.Tab selected = binding.tabs.getTabAt(tab);
        if (selected != null) selected.select();
        show(tab);
        binding.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab t) {
                Haptics.tick(binding.tabs);
                tab = t.getPosition();
                show(tab);
            }

            @Override public void onTabUnselected(TabLayout.Tab t) {}
            @Override public void onTabReselected(TabLayout.Tab t) {}
        });
    }

    private void show(int index) {
        FragmentManager fm = getChildFragmentManager();
        FragmentTransaction tx = fm.beginTransaction().setReorderingAllowed(true);
        for (int i = 0; i < TAGS.length; i++) {
            Fragment f = fm.findFragmentByTag(TAGS[i]);
            if (i == index) {
                if (f == null) tx.add(binding.feedContainer.getId(), i == TAB_POSTS ? new PostsFragment() : new CommunityFragment(), TAGS[i]);
                else tx.show(f);
            } else if (f != null) {
                tx.hide(f);
            }
        }
        tx.commitNow();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TAB, Math.max(tab, TAB_POSTS));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
