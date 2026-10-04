package com.app.winterarc.ui.common;

import androidx.annotation.Nullable;

/** A one-shot UI event (snackbar, navigation) delivered through LiveData exactly once. */
public final class Event<T> {
    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    @Nullable
    public T consume() {
        if (handled) return null;
        handled = true;
        return content;
    }

    public T peek() {
        return content;
    }
}
