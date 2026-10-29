package com.app.winterarc.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentLogInBinding;
import com.app.winterarc.domain.auth.Credentials.Problem;
import com.app.winterarc.ui.common.Haptics;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import dagger.hilt.android.AndroidEntryPoint;

/** Email + password (with show/hide) and "Forgot password?". */
@AndroidEntryPoint
public class LogInFragment extends Fragment {
    private FragmentLogInBinding binding;
    private AuthViewModel viewModel;
    @Nullable private AuthViewModel.State last;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentLogInBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        Ui.applySystemBarPadding(binding.logInRoot, true, true);
        binding.toolbar.setNavigationOnClickListener(v -> Nav.up(this));
        binding.submit.setOnClickListener(v -> submit());
        binding.password.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            submit();
            return true;
        });
        binding.forgot.setOnClickListener(v -> showReset());
        binding.googleButton.google.setOnClickListener(v -> {
            Haptics.tick(v);
            GoogleSignIn.start(requireActivity(), viewModel);
        });
        binding.switchMode.setOnClickListener(v -> {
            viewModel.clearErrors();
            NavHostFragment.findNavController(this).navigate(R.id.signUpFragment, null,
                    new NavOptions.Builder().setPopUpTo(R.id.logInFragment, true).build());
        });
        AuthUi.clearOnEdit(viewModel, binding.email, binding.password);

        viewModel.state().observe(getViewLifecycleOwner(), s -> {
            AuthUi.fieldError(binding.emailLayout, s.problems(), Problem.EMAIL_EMPTY, Problem.EMAIL_INVALID);
            AuthUi.fieldError(binding.passwordLayout, s.problems(), Problem.PASSWORD_EMPTY);
            AuthUi.messages(binding.messages, s);
            AuthUi.feedback(binding.getRoot(), s, last);
            last = s;
            binding.submit.setEnabled(!s.busy() && s.available());
            binding.submit.setText(s.busy() ? R.string.auth_working : R.string.auth_log_in);
            binding.googleButton.google.setEnabled(!s.busy() && s.googleAvailable());
            binding.googleButton.getRoot().setVisibility(s.googleAvailable() ? View.VISIBLE : View.GONE);
        });
        viewModel.signedIn().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() == null) return;
            Haptics.confirm(binding.getRoot());
            AuthUi.goHome(this);
        });
        viewModel.resetSent().observe(getViewLifecycleOwner(), e -> {
            String email = e.consume();
            if (email == null) return;
            Haptics.confirm(binding.getRoot());
            Snackbar.make(binding.logInRoot, getString(R.string.auth_reset_sent, email), Snackbar.LENGTH_LONG).show();
        });
    }

    private void submit() {
        Ui.hideKeyboard(binding.getRoot());
        viewModel.logIn(AuthUi.text(binding.email), AuthUi.text(binding.password));
    }

    private void showReset() {
        TextInputLayout layout = (TextInputLayout) getLayoutInflater().inflate(R.layout.view_email_input, null, false);
        EditText input = layout.getEditText();
        input.setText(AuthUi.text(binding.email));
        FrameLayout container = new FrameLayout(requireContext());
        int pad = Ui.dp(requireContext(), 24);
        container.setPadding(pad, Ui.dp(requireContext(), 8), pad, 0);
        container.addView(layout);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.auth_reset_title)
                .setMessage(R.string.auth_reset_body)
                .setView(container)
                .setPositiveButton(R.string.auth_reset_send, (d, w) -> viewModel.sendReset(AuthUi.text(input)))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
