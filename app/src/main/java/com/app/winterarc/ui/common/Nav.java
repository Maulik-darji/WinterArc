package com.app.winterarc.ui.common;

import android.os.Bundle;

import androidx.annotation.IdRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.app.winterarc.R;

/** Navigation helpers and argument keys (kept in sync with res/navigation/nav_graph.xml). */
public final class Nav {
    public static final String ARG_GOAL_ID = "goalId";
    public static final String ARG_DOC = "doc";
    public static final String ARG_COMMUNITY_ID = "communityId";
    public static final String ARG_USER_ID = "userId";
    public static final String DOC_PRIVACY = "privacy";
    public static final String DOC_TERMS = "terms";

    private Nav() {}

    private static final NavOptions ANIMATED = new NavOptions.Builder()
            .setEnterAnim(R.anim.wa_enter)
            .setExitAnim(R.anim.wa_exit)
            .setPopEnterAnim(R.anim.wa_pop_enter)
            .setPopExitAnim(R.anim.wa_pop_exit)
            .setLaunchSingleTop(true)
            .build();

    public static void go(Fragment from, @IdRes int destination, Bundle args) {
        NavController nav = NavHostFragment.findNavController(from);
        // Ignore double taps that would navigate twice from the same screen.
        if (nav.getCurrentDestination() != null && nav.getCurrentDestination().getId() == destination
                && args == null) return;
        nav.navigate(destination, args, ANIMATED);
    }

    public static void go(Fragment from, @IdRes int destination) {
        go(from, destination, null);
    }

    public static Bundle goal(long goalId) {
        Bundle b = new Bundle();
        b.putLong(ARG_GOAL_ID, goalId);
        return b;
    }

    public static Bundle doc(String doc) {
        Bundle b = new Bundle();
        b.putString(ARG_DOC, doc);
        return b;
    }

    public static Bundle community(String communityId) {
        Bundle b = new Bundle();
        b.putString(ARG_COMMUNITY_ID, communityId);
        return b;
    }

    public static Bundle user(String userId) {
        Bundle b = new Bundle();
        b.putString(ARG_USER_ID, userId);
        return b;
    }

    public static void up(Fragment from) {
        NavHostFragment.findNavController(from).navigateUp();
    }
}
