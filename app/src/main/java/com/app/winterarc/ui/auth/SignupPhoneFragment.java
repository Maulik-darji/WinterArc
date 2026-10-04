package com.app.winterarc.ui.auth;

import android.content.Context;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.databinding.FragmentSignupPhoneBinding;
import com.app.winterarc.domain.auth.AuthRepository;
import com.app.winterarc.domain.auth.Country;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/** Step 1 of sign-up: country code + mobile number → "Send code". */
@AndroidEntryPoint
public class SignupPhoneFragment extends Fragment {

    @Inject AuthRepository auth;

    private FragmentSignupPhoneBinding binding;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentSignupPhoneBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);
        viewModel.initCountry(defaultRegion());
        Ui.applySystemBarPadding(binding.scroll, true, true);

        binding.country.setOnClickListener(v -> pickCountry());
        binding.phone.setText(viewModel.state().getValue().input());
        binding.phone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.setInput(s.toString());
            }
        });
        binding.phone.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send();
                return true;
            }
            return false;
        });
        binding.send.setOnClickListener(v -> send());
        bindConsent();

        String hint = auth.testCodeHint();
        binding.testHint.setVisibility(hint != null ? View.VISIBLE : View.GONE);
        if (hint != null) binding.testHint.setText(getString(R.string.signup_test_hint, hint));

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.codeSent().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() != null) Nav.go(this, R.id.otpFragment);
        });
        viewModel.signedIn().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() != null) AuthNav.goHome(this);
        });
    }

    private void render(AuthViewModel.State s) {
        Context c = requireContext();
        Country country = s.country();
        binding.country.setText(AuthText.flag(country.iso()) + "  " + country.prefix());
        binding.country.setContentDescription(getString(R.string.signup_country_cd, country.prefix(),
                country.displayName(Formats.locale(c))));
        String message = null;
        if (s.phoneProblem() != null) message = AuthText.problem(c, s.phoneProblem(), country);
        else if (s.error() != null) message = getString(AuthText.error(s.error()));
        binding.error.setVisibility(message == null ? View.GONE : View.VISIBLE);
        binding.error.setText(message);
        binding.phoneLayout.setErrorEnabled(false);
        binding.unavailable.setVisibility(s.available() ? View.GONE : View.VISIBLE);
        binding.send.setEnabled(!s.sending() && s.available());
        binding.send.setText(s.sending() ? R.string.signup_sending : R.string.signup_send_code);
        binding.phone.setEnabled(!s.sending());
        binding.country.setEnabled(!s.sending());
    }

    private void send() {
        InputMethodManager imm = requireContext().getSystemService(InputMethodManager.class);
        imm.hideSoftInputFromWindow(binding.phone.getWindowToken(), 0);
        viewModel.sendCode(requireActivity());
    }

    private void pickCountry() {
        Locale locale = Formats.locale(requireContext());
        String[] items = new String[Country.ALL.size()];
        for (int i = 0; i < items.length; i++) {
            Country c = Country.ALL.get(i);
            items[i] = getString(R.string.signup_country_item, AuthText.flag(c.iso()), c.displayName(locale), c.prefix());
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.signup_country)
                .setItems(items, (d, which) -> viewModel.setCountry(Country.ALL.get(which)))
                .show();
    }

    /** SIM or network region, falling back to the device locale. */
    @Nullable
    private String defaultRegion() {
        TelephonyManager tm = requireContext().getSystemService(TelephonyManager.class);
        if (tm != null) {
            String sim = tm.getSimCountryIso();
            if (sim != null && sim.length() == 2) return sim;
            String network = tm.getNetworkCountryIso();
            if (network != null && network.length() == 2) return network;
        }
        return Formats.locale(requireContext()).getCountry();
    }

    private void bindConsent() {
        String text = getString(R.string.signup_consent);
        SpannableString span = new SpannableString(text);
        link(span, text, getString(R.string.signup_terms_link), Nav.DOC_TERMS);
        link(span, text, getString(R.string.signup_privacy_link), Nav.DOC_PRIVACY);
        binding.consent.setText(span);
        binding.consent.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void link(SpannableString span, String text, String word, String doc) {
        int start = text.indexOf(word);
        if (start < 0) return;
        span.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Nav.go(SignupPhoneFragment.this, R.id.legalFragment, Nav.doc(doc));
            }
        }, start, start + word.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
