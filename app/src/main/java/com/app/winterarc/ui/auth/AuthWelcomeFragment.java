package com.app.winterarc.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.databinding.FragmentAuthWelcomeBinding;
import com.app.winterarc.ui.common.ContributionGridView;
import com.app.winterarc.ui.common.Haptics;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.PreviewData;
import com.app.winterarc.ui.common.Ui;

import java.time.LocalDate;
import java.time.temporal.WeekFields;

import dagger.hilt.android.AndroidEntryPoint;

/** Entry to the account flow: Continue with Google, or create an account / log in with email. */
@AndroidEntryPoint
public class AuthWelcomeFragment extends Fragment {
    private FragmentAuthWelcomeBinding binding;
    private AuthViewModel viewModel;
    @Nullable private AuthViewModel.State last;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentAuthWelcomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        Ui.applySystemBarPadding(binding.welcomeRoot, true, true);
        binding.heroGrid.setCompact(true);
        binding.heroGrid.setData(PreviewData.heatmap(LocalDate.now(), 20,
                        WeekFields.of(Formats.locale(requireContext())).getFirstDayOfWeek(), 5),
                ContextCompat.getColor(requireContext(), R.color.wa_primary), ContributionGridView.localeOf(requireContext()));

        binding.googleButton.google.setOnClickListener(v -> {
            Haptics.tick(v);
            viewModel.clearErrors();
            GoogleSignIn.start(requireActivity(), viewModel);
        });
        binding.createAccount.setOnClickListener(v -> {
            viewModel.clearErrors();
            Nav.go(this, R.id.signUpFragment);
        });
        binding.logIn.setOnClickListener(v -> {
            viewModel.clearErrors();
            Nav.go(this, R.id.logInFragment);
        });
        AuthUi.consent(this, binding.consent);

        viewModel.state().observe(getViewLifecycleOwner(), s -> {
            AuthUi.messages(binding.messages, s);
            AuthUi.feedback(binding.getRoot(), s, last);
            last = s;
            binding.googleButton.google.setEnabled(!s.busy() && s.googleAvailable());
            binding.googleButton.google.setVisibility(s.googleAvailable() || !s.available() ? View.VISIBLE : View.GONE);
            binding.createAccount.setEnabled(s.available() && !s.busy());
            binding.logIn.setEnabled(s.available() && !s.busy());
        });
        viewModel.signedIn().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() == null) return;
            Haptics.confirm(binding.getRoot());
            AuthUi.goHome(this);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
