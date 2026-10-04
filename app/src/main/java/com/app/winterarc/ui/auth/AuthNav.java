package com.app.winterarc.ui.auth;

import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.app.winterarc.R;

/** Leaves the sign-up flow for Home, clearing it from the back stack. */
final class AuthNav {
    private AuthNav() {}

    static void goHome(Fragment from) {
        NavHostFragment.findNavController(from).navigate(R.id.homeFragment, null, new NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .setEnterAnim(R.anim.wa_pop_enter)
                .setExitAnim(R.anim.wa_exit)
                .build());
    }
}
