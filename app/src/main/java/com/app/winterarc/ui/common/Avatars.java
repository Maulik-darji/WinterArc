package com.app.winterarc.ui.common;

import android.graphics.drawable.Drawable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.app.winterarc.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.transition.Transition;

/**
 * Profile photos and banners. Avatars are the existing round "initial" TextViews: when a photo is
 * set it replaces the background (circle-cropped) and the letter is hidden, so every list and screen
 * gets photos without layout changes. Recycled views are handled by Glide's view targets.
 */
public final class Avatars {
    private Avatars() {}

    public static void bind(TextView view, String initial, @Nullable String photoUrl) {
        bind(view, initial, photoUrl, R.drawable.bg_avatar);
    }

    public static void bind(TextView view, String initial, @Nullable String photoUrl, @DrawableRes int placeholder) {
        Glide.with(view).clear(view);
        view.setBackgroundResource(placeholder);
        view.setText(initial);
        if (photoUrl == null || photoUrl.isBlank()) return;
        Glide.with(view).load(photoUrl).circleCrop().into(new CustomViewTarget<TextView, Drawable>(view) {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                getView().setBackground(resource);
                getView().setText("");
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                getView().setBackgroundResource(placeholder);
                getView().setText(initial);
            }

            @Override
            protected void onResourceCleared(@Nullable Drawable placeholderDrawable) {
                getView().setBackgroundResource(placeholder);
                getView().setText(initial);
            }
        });
    }

    /** A profile banner; without one, the default gradient is shown. */
    public static void banner(ImageView view, @Nullable String bannerUrl) {
        if (bannerUrl == null || bannerUrl.isBlank()) {
            Glide.with(view).clear(view);
            view.setImageResource(R.drawable.bg_profile_banner);
            return;
        }
        Glide.with(view).load(bannerUrl).centerCrop().placeholder(R.drawable.bg_profile_banner)
                .error(R.drawable.bg_profile_banner).into(view);
    }
}
