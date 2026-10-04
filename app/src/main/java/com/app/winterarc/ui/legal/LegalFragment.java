package com.app.winterarc.ui.legal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentLegalBinding;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;

/** Privacy policy or terms/disclaimer, bundled as string resources so they work offline. */
public class LegalFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return FragmentLegalBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        FragmentLegalBinding b = FragmentLegalBinding.bind(view);
        Ui.applySystemBarPadding(b.legalRoot, true, false);
        Ui.applySystemBarPadding(b.scroll, false, true);
        boolean terms = Nav.DOC_TERMS.equals(requireArguments().getString(Nav.ARG_DOC));
        b.toolbar.setTitle(terms ? R.string.terms_title : R.string.privacy_title);
        b.toolbar.setNavigationOnClickListener(v -> Nav.up(this));
        b.body.setText(terms ? R.string.terms_body : R.string.privacy_body);
    }
}
