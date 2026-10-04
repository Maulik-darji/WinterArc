package com.app.winterarc.ui.onboarding;

import androidx.lifecycle.ViewModel;

import com.app.winterarc.core.analytics.Analytics;
import com.app.winterarc.core.analytics.AnalyticsEvent;
import com.app.winterarc.domain.repository.PreferencesRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class OnboardingViewModel extends ViewModel {
    private final PreferencesRepository preferences;
    private final Analytics analytics;

    @Inject
    public OnboardingViewModel(PreferencesRepository preferences, Analytics analytics) {
        this.preferences = preferences;
        this.analytics = analytics;
    }

    public void complete() {
        if (!preferences.get().onboardingCompleted()) analytics.track(AnalyticsEvent.of(AnalyticsEvent.ONBOARDING_COMPLETED));
        preferences.setOnboardingCompleted(true);
    }
}
