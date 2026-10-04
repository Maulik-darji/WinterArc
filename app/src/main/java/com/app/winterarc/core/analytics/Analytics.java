package com.app.winterarc.core.analytics;

/**
 * Provider-independent analytics. Events carry only coarse, non-identifying properties (enums and
 * counts): never titles, notes, free text or precise timestamps. Release builds bind a no-op
 * until a provider is deliberately configured.
 */
public interface Analytics {
    void track(AnalyticsEvent event);

    Analytics NO_OP = event -> { };
}
