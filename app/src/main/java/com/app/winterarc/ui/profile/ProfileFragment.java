package com.app.winterarc.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentProfileBinding;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.AuthUser;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.app.winterarc.ui.home.HomeViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProfileFragment extends Fragment {
    private static final String PROFILE_PREFS = "winterarc_profile";
    private static final String KEY_DISPLAY_NAME = "display_name";

    @Inject AuthRepository auth;

    private FragmentProfileBinding binding;
    private HomeViewModel homeViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Ui.applySystemBarPadding(binding.profileRoot, true, false);
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        AuthUser user = auth.getCurrentUser();
        if (user != null) binding.phone.setText(maskPhone(user.phoneE164()));
        String savedName = requireContext().getSharedPreferences(PROFILE_PREFS, android.content.Context.MODE_PRIVATE)
                .getString(KEY_DISPLAY_NAME, getString(R.string.profile_default_name));
        showName(savedName);

        binding.editProfile.setOnClickListener(v -> editName());
        binding.archive.setOnClickListener(v -> Nav.go(this, R.id.archiveFragment));
        binding.settings.setOnClickListener(v -> Nav.go(this, R.id.settingsFragment));
        homeViewModel.state().observe(getViewLifecycleOwner(), home -> {
            binding.activeGoals.setText(String.valueOf(home.cards().size()));
            binding.todayDone.setText(String.valueOf(Math.max(0, home.scheduledToday() - home.remainingToday())));
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (homeViewModel != null) homeViewModel.refresh(java.time.DayOfWeek.MONDAY);
    }

    private void editName() {
        EditText input = new EditText(requireContext());
        int padding = Ui.dp(requireContext(), 20);
        input.setPadding(padding, padding / 2, padding, padding / 2);
        input.setHint(R.string.profile_name_hint);
        input.setSingleLine(true);
        input.setText(binding.name.getText());
        input.setSelection(input.length());
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_edit_title)
                .setView(input)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    requireContext().getSharedPreferences(PROFILE_PREFS, android.content.Context.MODE_PRIVATE)
                            .edit().putString(KEY_DISPLAY_NAME, name).apply();
                    showName(name);
                })
                .show();
    }

    private void showName(String name) {
        binding.name.setText(name);
        binding.avatar.setText(name.substring(0, 1).toUpperCase(java.util.Locale.ROOT));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return getString(R.string.profile_phone_hidden);
        return "•••• " + phone.substring(phone.length() - 4);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
