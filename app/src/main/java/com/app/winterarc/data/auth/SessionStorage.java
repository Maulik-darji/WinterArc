package com.app.winterarc.data.auth;

import androidx.annotation.Nullable;

/** Where the signed-in session (tokens and user) is kept between launches. */
public interface SessionStorage {
    @Nullable
    String read();

    void write(String value);

    void clear();
}
