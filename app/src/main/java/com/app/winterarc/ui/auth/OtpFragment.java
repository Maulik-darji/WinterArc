package com.app.winterarc.ui.auth;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentOtpBinding;
import com.app.winterarc.domain.auth.AuthError;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.PhoneNumber;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.google.android.material.snackbar.Snackbar;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/** Step 2 of sign-up: enter the 6-digit code, with resend cooldown and change-number. */
@AndroidEntryPoint
public class OtpFragment extends Fragment {

    @Inject AuthRepository auth;

    private FragmentOtpBinding binding;
    private AuthViewModel viewModel;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            viewModel.tick();
            if (viewModel.resendInSeconds() > 0) ticker.postDelayed(this, 1_000);
        }
    };
    @Nullable private AuthError shownError;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentOtpBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        PhoneNumber number = viewModel.pendingNumber();
        if (number == null || !viewModel.hasPendingCode()) {
            // Nothing to verify (e.g. restored without a request): go back to the number screen.
            Nav.up(this);
            return;
        }
        Ui.applySystemBarPadding(binding.otpContent, true, true);
        binding.toolbar.setNavigationOnClickListener(v -> changeNumber());
        binding.changeNumber.setOnClickListener(v -> changeNumber());
        binding.body.setText(getString(R.string.otp_body, number.masked()));
        binding.code.setCodeLength(AuthViewModel.CODE_LENGTH);
        binding.code.setOnCodeCompleteListener(code -> viewModel.verify(code));
        binding.verify.setOnClickListener(v -> viewModel.verify(binding.code.code()));
        binding.resend.setOnClickListener(v -> {
            binding.code.setText("");
            viewModel.resend(requireActivity());
            Snackbar.make(binding.otpRoot, R.string.otp_resent, Snackbar.LENGTH_SHORT).show();
            startTicker();
        });

        String hint = auth.testCodeHint();
        binding.testHint.setVisibility(hint != null ? View.VISIBLE : View.GONE);
        if (hint != null) binding.testHint.setText(getString(R.string.signup_test_hint, hint));

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.signedIn().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() != null) AuthNav.goHome(this);
        });

        binding.code.requestFocus();
        binding.code.post(() -> {
            if (binding == null) return;
            requireContext().getSystemService(InputMethodManager.class)
                    .showSoftInput(binding.code, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        startTicker();
    }

    @Override
    public void onPause() {
        super.onPause();
        ticker.removeCallbacks(tick);
    }

    private void startTicker() {
        ticker.removeCallbacks(tick);
        ticker.post(tick);
    }

    private void render(AuthViewModel.State s) {
        boolean busy = s.verifying() || s.sending();
        binding.verify.setEnabled(!busy);
        binding.verify.setText(s.verifying() ? R.string.otp_verifying : R.string.otp_verify);
        binding.code.setEnabled(!s.verifying());
        int seconds = s.resendInSeconds();
        binding.resend.setEnabled(seconds == 0 && !busy);
        binding.resend.setText(seconds > 0 ? getString(R.string.otp_resend_in, AuthText.countdown(seconds))
                : getString(R.string.otp_resend));

        AuthError error = s.error();
        binding.error.setVisibility(error == null ? View.GONE : View.VISIBLE);
        if (error != null) binding.error.setText(AuthText.error(error));
        if (error != null && error != shownError) {
            // Clear a rejected code first (editing resets the outline), then mark the boxes red.
            if (error == AuthError.INVALID_CODE) binding.code.setText("");
            binding.code.setError(true);
        }
        shownError = error;
    }

    private void changeNumber() {
        viewModel.clearError();
        Nav.up(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ticker.removeCallbacks(tick);
        binding = null;
    }
}
