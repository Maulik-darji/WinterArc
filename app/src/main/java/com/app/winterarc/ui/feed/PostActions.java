package com.app.winterarc.ui.feed;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.Menu;
import android.view.View;

import androidx.annotation.StringRes;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.app.winterarc.R;
import com.app.winterarc.domain.feed.FeedResult;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Post and comment actions shared by the timeline and the post screen. */
final class PostActions {
    private PostActions() {}

    static void bindLike(MaterialButton button, boolean liked, int count, boolean showCount) {
        Context context = button.getContext();
        int color = liked ? ContextCompat.getColor(context, R.color.wa_like)
                : MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurfaceVariant);
        button.setIconResource(liked ? R.drawable.ic_ph_heart_fill : R.drawable.ic_ph_heart);
        button.setIconTint(ColorStateList.valueOf(color));
        button.setTextColor(color);
        button.setText(showCount ? FeedFormat.count(count) : "");
        button.setContentDescription(context.getString(liked ? R.string.feed_unlike_cd : R.string.feed_like_cd, count));
    }

    static void bindReply(MaterialButton button, int count, boolean showCount) {
        button.setText(showCount ? FeedFormat.count(count) : "");
        button.setContentDescription(button.getContext().getString(R.string.feed_reply_cd, count));
    }

    static void share(Fragment fragment, String author, String body) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, fragment.getString(R.string.feed_share_text, author, body));
        fragment.startActivity(Intent.createChooser(send, fragment.getString(R.string.feed_share_cd)));
    }

    /** Overflow for content the viewer wrote: Edit and Delete. */
    static void showOwnerMenu(View anchor, @StringRes int editLabel, @StringRes int deleteLabel,
                              Runnable onEdit, Runnable onDelete) {
        PopupMenu menu = new PopupMenu(anchor.getContext(), anchor);
        menu.getMenu().add(Menu.NONE, 1, 0, editLabel).setIcon(R.drawable.ic_ph_pencil);
        menu.getMenu().add(Menu.NONE, 2, 1, deleteLabel).setIcon(R.drawable.ic_ph_trash);
        menu.setForceShowIcon(true);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) onEdit.run(); else onDelete.run();
            return true;
        });
        menu.show();
    }

    static void confirm(Context context, @StringRes int title, @StringRes int body, @StringRes int action, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(body)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(action, (d, w) -> onConfirm.run())
                .show();
    }

    /** Snackbar text for a failed action, or 0 on success. */
    @StringRes
    static int errorText(FeedResult result) {
        switch (result) {
            case SUCCESS: return 0;
            case PERMISSION_DENIED: return R.string.feed_error_denied;
            case NOT_FOUND: return R.string.feed_error_missing;
            case UNAVAILABLE: return R.string.feed_error_unavailable;
            default: return R.string.feed_error_invalid;
        }
    }
}
