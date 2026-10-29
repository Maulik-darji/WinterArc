package com.app.winterarc.ui.auth;

import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.app.winterarc.R;
import com.app.winterarc.databinding.ViewAuthMessagesBinding;
import com.app.winterarc.domain.auth.Credentials;
import com.app.winterarc.ui.common.Haptics;
import com.app.winterarc.ui.common.Nav;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Set;

/** Helpers shared by the welcome, sign-up and log-in screens. */
final class AuthUi {
    private AuthUi() {}

    /** Leaves the account flow for Home, clearing it from the back stack. */
    static void goHome(Fragment from) {
        NavHostFragment.findNavController(from).navigate(R.id.homeFragment, null, new NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .setEnterAnim(R.anim.wa_pop_enter)
                .setExitAnim(R.anim.wa_exit)
                .build());
    }

    static String text(@Nullable EditText field) {
        return field == null || field.getText() == null ? "" : field.getText().toString();
    }

    /** Shows the first matching problem under the field (null clears it). */
    static void fieldError(TextInputLayout layout, Set<Credentials.Problem> problems, Credentials.Problem... kinds) {
        for (Credentials.Problem k : kinds) {
            if (problems.contains(k)) {
                layout.setError(layout.getContext().getString(AuthText.problem(k)));
                return;
            }
        }
        layout.setError(null);
    }

    static void messages(ViewAuthMessagesBinding b, AuthViewModel.State s) {
        b.error.setVisibility(s.error() == null ? View.GONE : View.VISIBLE);
        if (s.error() != null) b.error.setText(AuthText.error(s.error()));
        b.unavailable.setVisibility(s.available() ? View.GONE : View.VISIBLE);
        b.testHint.setVisibility(s.testHint() == null ? View.GONE : View.VISIBLE);
        b.testHint.setText(s.testHint());
    }

    /** Clears provider errors as soon as the user edits any field. */
    static void clearOnEdit(AuthViewModel vm, EditText... fields) {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { vm.clearErrors(); }
        };
        for (EditText f : fields) f.addTextChangedListener(watcher);
    }

    /** Haptic "no" when a new error appears. */
    static void feedback(View anchor, AuthViewModel.State s, @Nullable AuthViewModel.State previous) {
        boolean hadError = previous != null && (previous.error() != null || !previous.problems().isEmpty());
        boolean hasError = s.error() != null || !s.problems().isEmpty();
        if (hasError && !hadError) Haptics.reject(anchor);
    }

    static void consent(Fragment fragment, TextView view) {
        String text = fragment.getString(R.string.auth_consent);
        SpannableString span = new SpannableString(text);
        link(fragment, span, text, fragment.getString(R.string.auth_terms_link), Nav.DOC_TERMS);
        link(fragment, span, text, fragment.getString(R.string.auth_privacy_link), Nav.DOC_PRIVACY);
        view.setText(span);
        view.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private static void link(Fragment fragment, SpannableString span, String text, String word, String doc) {
        int start = text.indexOf(word);
        if (start < 0) return;
        span.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Nav.go(fragment, R.id.legalFragment, Nav.doc(doc));
            }
        }, start, start + word.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
