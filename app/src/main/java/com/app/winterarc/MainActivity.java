package com.app.winterarc;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.repository.PreferencesRepository;
import com.app.winterarc.reminders.Notifications;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Haptics;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/** Single activity hosting the navigation graph. */
@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    @Inject PreferencesRepository preferences;
    @Inject AuthRepository auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        // Fully transparent system bars in both themes; icon colours follow the active theme.
        EdgeToEdge.enable(this,
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NavController nav = navController();
        NavGraph graph = nav.getNavInflater().inflate(R.navigation.nav_graph);
        // Preferences are read synchronously (in-memory SharedPreferences), so the start
        // destination is decided before the first frame with no flicker. After recreation or
        // process death, setGraph restores the saved back stack.
        graph.setStartDestination(startDestination());
        nav.setGraph(graph, null);
        setUpBottomNavigation(nav);
        if (savedInstanceState == null) openGoalFromIntent(getIntent());
    }

    private void setUpBottomNavigation(NavController nav) {
        View root = findViewById(R.id.main_root);
        View bar = findViewById(R.id.bottom_bar);
        BottomNavigationView bottom = findViewById(R.id.bottom_navigation);
        // Icons carry their own colours (outline / filled / primary create button).
        bottom.setItemIconTintList(null);

        // While the bar is shown it owns the bottom system inset; screens above it get none, so
        // their own bottom padding doesn't stack on top of the bar.
        View host = findViewById(R.id.nav_host);
        ViewCompat.setOnApplyWindowInsetsListener(host, (v, insets) -> {
            if (bar.getVisibility() != View.VISIBLE) return insets;
            Insets system = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            return new WindowInsetsCompat.Builder(insets)
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(system.left, system.top, system.right, 0))
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.NONE)
                    .build();
        });

        bottom.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_new_goal) {
                Haptics.tick(bottom);
                openGoalEditor(nav);
                return false;
            }
            int destination = id == R.id.navigation_home ? R.id.homeFragment
                    : id == R.id.navigation_feed ? R.id.feedFragment
                    : id == R.id.navigation_chats ? R.id.chatsFragment
                    : R.id.profileFragment;
            if (nav.getCurrentDestination() != null && nav.getCurrentDestination().getId() == destination) return true;
            Haptics.segment(bottom);
            nav.navigate(destination, null, new NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(R.id.homeFragment, false)
                    .setEnterAnim(R.anim.wa_fade_in)
                    .setExitAnim(R.anim.wa_fade_out)
                    .setPopEnterAnim(R.anim.wa_fade_in)
                    .setPopExitAnim(R.anim.wa_fade_out)
                    .build());
            return true;
        });
        bottom.setOnItemReselectedListener(item -> Haptics.tick(bottom));

        nav.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            boolean topLevel = id == R.id.homeFragment || id == R.id.feedFragment
                    || id == R.id.chatsFragment || id == R.id.profileFragment;
            int visibility = topLevel ? View.VISIBLE : View.GONE;
            if (bar.getVisibility() != visibility) {
                bar.setVisibility(visibility);
                ViewCompat.requestApplyInsets(root);
            }
            if (id == R.id.homeFragment) bottom.getMenu().findItem(R.id.navigation_home).setChecked(true);
            else if (id == R.id.feedFragment) bottom.getMenu().findItem(R.id.navigation_feed).setChecked(true);
            else if (id == R.id.chatsFragment) bottom.getMenu().findItem(R.id.navigation_chats).setChecked(true);
            else if (id == R.id.profileFragment) bottom.getMenu().findItem(R.id.navigation_profile).setChecked(true);
        });
    }

    private void openGoalEditor(NavController nav) {
        nav.navigate(R.id.goalEditorFragment, null, new NavOptions.Builder()
                .setEnterAnim(R.anim.wa_enter)
                .setExitAnim(R.anim.wa_exit)
                .setPopEnterAnim(R.anim.wa_pop_enter)
                .setPopExitAnim(R.anim.wa_pop_exit)
                .build());
    }

    /** Onboarding first, then sign-up with a mobile number, then the dashboard. */
    private int startDestination() {
        if (!preferences.get().onboardingCompleted()) return R.id.onboardingFragment;
        if (auth.getCurrentUser() == null) return R.id.authWelcomeFragment;
        return R.id.homeFragment;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        openGoalFromIntent(intent);
    }

    /** Reminder notifications deep-link straight to the goal. */
    private void openGoalFromIntent(Intent intent) {
        long goalId = intent == null ? -1 : intent.getLongExtra(Notifications.EXTRA_GOAL_ID, -1);
        if (goalId <= 0 || startDestination() != R.id.homeFragment) return;
        intent.removeExtra(Notifications.EXTRA_GOAL_ID);
        navController().navigate(R.id.goalDetailFragment, Nav.goal(goalId));
    }

    private NavController navController() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        return host.getNavController();
    }
}
