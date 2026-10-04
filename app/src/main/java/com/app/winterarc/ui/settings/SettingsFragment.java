package com.app.winterarc.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.app.winterarc.BuildConfig;
import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentSettingsBinding;
import com.app.winterarc.databinding.ItemSettingsRowBinding;
import com.app.winterarc.domain.repository.ThemeMode;
import com.app.winterarc.reminders.Notifications;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;
    private boolean rendering;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        Ui.applySystemBarPadding(binding.settingsContent, true, false);
        Ui.applySystemBarPadding(binding.scroll, false, true);
        binding.toolbar.setNavigationOnClickListener(v -> Nav.up(this));

        binding.themeGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked || rendering) return;
            viewModel.setTheme(id == R.id.theme_light ? ThemeMode.LIGHT : id == R.id.theme_dark ? ThemeMode.DARK : ThemeMode.SYSTEM);
        });
        binding.haptics.setOnCheckedChangeListener((b, checked) -> { if (!rendering) viewModel.setHaptics(checked); });
        binding.reminders.setOnCheckedChangeListener((b, checked) -> { if (!rendering) viewModel.setReminders(checked); });
        binding.openNotificationSettings.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName())));

        row(binding.rowPrivacy, R.drawable.ic_shield, getString(R.string.settings_privacy), null,
                v -> Nav.go(this, R.id.legalFragment, Nav.doc(Nav.DOC_PRIVACY)));
        row(binding.rowTerms, R.drawable.ic_info, getString(R.string.settings_terms), null,
                v -> Nav.go(this, R.id.legalFragment, Nav.doc(Nav.DOC_TERMS)));
        row(binding.rowOnboarding, R.drawable.ic_grid, getString(R.string.settings_onboarding), null, v -> {
            viewModel.replayOnboarding();
            NavHostFragment.findNavController(this).navigate(R.id.onboardingFragment, null,
                    new NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build());
        });
        row(binding.rowVersion, R.drawable.ic_calendar, getString(R.string.app_name),
                getString(R.string.settings_version, BuildConfig.VERSION_NAME), null);

        binding.demoSection.setVisibility(viewModel.demoAvailable() ? View.VISIBLE : View.GONE);
        if (viewModel.demoAvailable()) {
            row(binding.rowDemoLoad, R.drawable.ic_celebration, getString(R.string.settings_demo_load),
                    getString(R.string.settings_demo_load_desc), v -> confirm(viewModel::loadDemo));
            row(binding.rowDemoClear, R.drawable.ic_delete, getString(R.string.settings_demo_clear), null,
                    v -> confirm(viewModel::clearData));
        }

        viewModel.account().observe(getViewLifecycleOwner(), user -> {
            binding.accountSection.setVisibility(user == null ? View.GONE : View.VISIBLE);
            if (user == null) return;
            row(binding.rowAccount, R.drawable.ic_shield, getString(R.string.settings_signed_in_as, user.phoneE164()), null, null);
            row(binding.rowSignOut, R.drawable.ic_arrow_back, getString(R.string.settings_sign_out), null, v -> confirmSignOut());
        });
        viewModel.preferences().observe(getViewLifecycleOwner(), p -> {
            rendering = true;
            binding.themeGroup.check(p.themeMode() == ThemeMode.LIGHT ? R.id.theme_light
                    : p.themeMode() == ThemeMode.DARK ? R.id.theme_dark : R.id.theme_system);
            binding.haptics.setChecked(p.hapticsEnabled());
            binding.reminders.setChecked(p.remindersEnabled());
            rendering = false;
        });
        viewModel.messages().observe(getViewLifecycleOwner(), e -> {
            SettingsViewModel.Message m = e.consume();
            if (m == null) return;
            Snackbar.make(binding.settingsRoot, m == SettingsViewModel.Message.DEMO_LOADED
                    ? R.string.settings_demo_loaded : R.string.settings_demo_cleared, Snackbar.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.blocked.setVisibility(Notifications.canPost(requireContext()) ? View.GONE : View.VISIBLE);
    }

    private void confirmSignOut() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_sign_out_title)
                .setMessage(R.string.settings_sign_out_body)
                .setPositiveButton(R.string.settings_sign_out, (d, w) -> {
                    viewModel.signOut();
                    NavHostFragment.findNavController(this).navigate(R.id.signupPhoneFragment, null,
                            new NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build());
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirm(Runnable action) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.settings_demo_confirm)
                .setPositiveButton(R.string.action_continue, (d, w) -> action.run())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void row(ItemSettingsRowBinding row, int icon, String title, @Nullable String summary,
                     @Nullable View.OnClickListener click) {
        row.icon.setImageResource(icon);
        row.title.setText(title);
        row.summary.setText(summary);
        row.summary.setVisibility(summary == null ? View.GONE : View.VISIBLE);
        row.getRoot().setOnClickListener(click);
        row.getRoot().setClickable(click != null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
