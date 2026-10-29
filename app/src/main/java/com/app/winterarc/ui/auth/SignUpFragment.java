package com.app.winterarc.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentSignUpBinding;
import com.app.winterarc.domain.auth.Credentials.Problem;
import com.app.winterarc.ui.common.Haptics;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;

import dagger.hilt.android.AndroidEntryPoint;

/** Name, email, password and confirm password (both with show/hide toggles). */
@AndroidEntryPoint
public class SignUpFragment extends Fragment {
    private FragmentSignUpBinding binding;
    private AuthViewModel viewModel;
    @Nullable private AuthViewModel.State last;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentSignUpBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        Ui.applySystemBarPadding(binding.signUpRoot, true, true);
        binding.toolbar.setNavigationOnClickListener(v -> Nav.up(this));
        binding.submit.setOnClickListener(v -> submit());
        binding.confirm.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            submit();
            return true;
        });
        binding.googleButton.google.setOnClickListener(v -> {
            Haptics.tick(v);
            GoogleSignIn.start(requireActivity(), viewModel);
        });
        binding.switchMode.setOnClickListener(v -> {
            viewModel.clearErrors();
            NavHostFragment.findNavController(this).navigate(R.id.logInFragment, null,
                    new NavOptions.Builder().setPopUpTo(R.id.signUpFragment, true).build());
        });
        AuthUi.clearOnEdit(viewModel, binding.name, binding.email, binding.password, binding.confirm);

        viewModel.state().observe(getViewLifecycleOwner(), s -> {
            AuthUi.fieldError(binding.nameLayout, s.problems(), Problem.NAME_EMPTY, Problem.NAME_TOO_LONG);
            AuthUi.fieldError(binding.emailLayout, s.problems(), Problem.EMAIL_EMPTY, Problem.EMAIL_INVALID);
            AuthUi.fieldError(binding.passwordLayout, s.problems(), Problem.PASSWORD_EMPTY, Problem.PASSWORD_TOO_SHORT,
                    Problem.PASSWORD_TOO_LONG, Problem.PASSWORD_NEEDS_LETTER, Problem.PASSWORD_NEEDS_NUMBER);
            AuthUi.fieldError(binding.confirmLayout, s.problems(), Problem.CONFIRM_EMPTY, Problem.PASSWORDS_DIFFER);
            AuthUi.messages(binding.messages, s);
            AuthUi.feedback(binding.getRoot(), s, last);
            last = s;
            binding.submit.setEnabled(!s.busy() && s.available());
            binding.submit.setText(s.busy() ? R.string.auth_working : R.string.auth_create_account);
            binding.googleButton.google.setEnabled(!s.busy() && s.googleAvailable());
            binding.googleButton.getRoot().setVisibility(s.googleAvailable() ? View.VISIBLE : View.GONE);
        });
        viewModel.confirmEmail().observe(getViewLifecycleOwner(), e -> {
            String email = e.consume();
            if (email == null) return;
            Haptics.confirm(binding.getRoot());
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.auth_confirm_title)
                    .setMessage(getString(R.string.auth_confirm_body, email))
                    .setPositiveButton(R.string.auth_log_in, (d, w) -> binding.switchMode.performClick())
                    .show();
        });
        viewModel.signedIn().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() == null) return;
            Haptics.confirm(binding.getRoot());
            AuthUi.goHome(this);
        });
    }

    private void submit() {
        Ui.hideKeyboard(binding.getRoot());
        viewModel.signUp(AuthUi.text(binding.name), AuthUi.text(binding.email), AuthUi.text(binding.password),
                AuthUi.text(binding.confirm));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
