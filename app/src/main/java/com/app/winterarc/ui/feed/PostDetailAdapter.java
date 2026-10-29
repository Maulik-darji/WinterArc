package com.app.winterarc.ui.feed;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.app.winterarc.R;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.databinding.ItemCommentBinding;
import com.app.winterarc.databinding.ItemFeedMessageBinding;
import com.app.winterarc.databinding.ItemPostDetailBinding;
import com.app.winterarc.domain.feed.CommentThreads;
import com.app.winterarc.domain.feed.FeedPost;
import com.app.winterarc.domain.feed.PostComment;
import com.app.winterarc.domain.social.SocialUser;
import com.app.winterarc.ui.common.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The post header followed by its comment thread (or an empty / deleted message). */
final class PostDetailAdapter extends ListAdapter<PostDetailAdapter.Item, RecyclerView.ViewHolder> {
    interface Listener {
        void openProfile(String userId);
        void replyToPost();
        void likePost(View button);
        void sharePost();
        void postMore(View anchor);
        void replyTo(CommentThreads.Node node);
        void commentMore(View anchor, CommentThreads.Node node);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_COMMENT = 1;
    private static final int TYPE_MESSAGE = 2;

    record Item(int type, String key, @Nullable FeedPost post, @Nullable CommentThreads.Node node, int message) {}

    private final Listener listener;
    private final TimeProvider time;
    private Map<String, SocialUser> users = Map.of();
    private String viewerId = "";

    PostDetailAdapter(Listener listener, TimeProvider time) {
        super(DIFF);
        this.listener = listener;
        this.time = time;
    }

    /** @param onCommitted runs once the new rows are applied (positions are then up to date) */
    void submit(PostDetailViewModel.State state, Runnable onCommitted) {
        boolean usersChanged = !state.users().equals(users);
        users = state.users();
        viewerId = state.viewerId();
        List<Item> items = new ArrayList<>();
        if (state.post() == null) {
            items.add(new Item(TYPE_MESSAGE, "missing", null, null, R.string.feed_post_missing));
        } else {
            items.add(new Item(TYPE_HEADER, "header", state.post(), null, 0));
            for (CommentThreads.Node node : state.thread()) {
                items.add(new Item(TYPE_COMMENT, node.comment().id(), null, node, 0));
            }
            if (state.thread().isEmpty()) items.add(new Item(TYPE_MESSAGE, "empty", null, null, R.string.comments_empty));
        }
        submitList(items, onCommitted);
        if (usersChanged) notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) return new HeaderHolder(ItemPostDetailBinding.inflate(inflater, parent, false));
        if (viewType == TYPE_COMMENT) return new CommentHolder(ItemCommentBinding.inflate(inflater, parent, false));
        return new MessageHolder(ItemFeedMessageBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Item item = getItem(position);
        if (holder instanceof HeaderHolder h) bindHeader(h.binding, Objects.requireNonNull(item.post()));
        else if (holder instanceof CommentHolder c) bindComment(c.binding, Objects.requireNonNull(item.node()));
        else ((MessageHolder) holder).binding.message.setText(item.message());
    }

    private void bindHeader(ItemPostDetailBinding b, FeedPost post) {
        Context context = b.getRoot().getContext();
        FeedFormat.avatar(b.avatar, users, post.authorId());
        b.name.setText(FeedFormat.name(context, users, post.authorId()));
        b.handle.setText(FeedFormat.handle(context, users, post.authorId()));
        b.body.setText(post.body());
        String stamp = FeedFormat.exact(post.createdAt(), time.zone());
        if (post.isEdited()) stamp += " " + context.getString(R.string.feed_dot_separator) + " " + context.getString(R.string.feed_edited);
        b.timestamp.setText(stamp);
        String counts = context.getResources().getQuantityString(R.plurals.feed_replies_label, post.replyCount(), post.replyCount())
                + "&nbsp;&nbsp;&nbsp;"
                + context.getResources().getQuantityString(R.plurals.feed_likes_label, post.likeCount(), post.likeCount());
        b.counts.setText(HtmlCompat.fromHtml(counts, HtmlCompat.FROM_HTML_MODE_LEGACY));
        PostActions.bindReply(b.reply, post.replyCount(), false);
        PostActions.bindLike(b.like, post.likedByMe(), post.likeCount(), false);
        boolean mine = post.authorId().equals(viewerId);
        b.more.setVisibility(mine ? View.VISIBLE : View.INVISIBLE);
        b.more.setEnabled(mine);
        View.OnClickListener profile = v -> listener.openProfile(post.authorId());
        b.avatar.setOnClickListener(profile);
        b.name.setOnClickListener(profile);
        b.handle.setOnClickListener(profile);
        b.reply.setOnClickListener(v -> listener.replyToPost());
        b.like.setOnClickListener(listener::likePost);
        b.share.setOnClickListener(v -> listener.sharePost());
        b.more.setOnClickListener(listener::postMore);
    }

    private void bindComment(ItemCommentBinding b, CommentThreads.Node node) {
        Context context = b.getRoot().getContext();
        PostComment c = node.comment();
        int start = Ui.dp(context, ThreadLinesView.GUTTER_DP + node.depth() * ThreadLinesView.INDENT_DP);
        b.content.setPaddingRelative(start, b.content.getPaddingTop(), b.content.getPaddingEnd(), 0);
        b.lines.bind(node.depth(), node.continues(), node.hasChildren());

        boolean mine = !c.deleted() && c.authorId().equals(viewerId);
        String when = FeedFormat.relative(context, c.createdAt(), time.now(), time.zone());
        String name = FeedFormat.name(context, users, c.authorId());
        b.more.setVisibility(mine ? View.VISIBLE : View.INVISIBLE);
        b.more.setEnabled(mine);
        b.more.setOnClickListener(v -> listener.commentMore(v, node));
        b.getRoot().setOnLongClickListener(mine ? v -> { listener.commentMore(b.more, node); return true; } : null);
        b.getRoot().setLongClickable(mine);

        if (c.deleted()) {
            com.app.winterarc.ui.common.Avatars.bind(b.avatar, "", null);
            b.avatar.setAlpha(0.35f);
            b.avatar.setOnClickListener(null);
            b.avatar.setClickable(false);
            b.name.setOnClickListener(null);
            b.header.setVisibility(View.GONE);
            b.replyingTo.setVisibility(View.GONE);
            b.body.setText(R.string.comment_deleted);
            b.body.setTypeface(null, Typeface.ITALIC);
            b.body.setTextColor(com.google.android.material.color.MaterialColors.getColor(b.body,
                    com.google.android.material.R.attr.colorOnSurfaceVariant));
            b.reply.setVisibility(View.GONE);
            b.deletedSpacer.setVisibility(View.VISIBLE);
            b.commentRoot.setContentDescription(context.getString(R.string.comment_deleted));
            return;
        }
        b.avatar.setAlpha(1f);
        FeedFormat.avatar(b.avatar, users, c.authorId());
        View.OnClickListener profile = v -> listener.openProfile(c.authorId());
        b.avatar.setOnClickListener(profile);
        b.name.setOnClickListener(profile);
        b.header.setVisibility(View.VISIBLE);
        b.name.setText(name);
        b.meta.setText(FeedFormat.meta(context, FeedFormat.handle(context, users, c.authorId()), when, c.isEdited()));
        if (node.replyingTo() != null) {
            b.replyingTo.setVisibility(View.VISIBLE);
            b.replyingTo.setText(context.getString(R.string.comment_replying_to,
                    FeedFormat.handle(context, users, node.replyingTo())));
        } else {
            b.replyingTo.setVisibility(View.GONE);
        }
        b.body.setText(c.body());
        b.body.setTypeface(null, Typeface.NORMAL);
        b.body.setTextColor(com.google.android.material.color.MaterialColors.getColor(b.body,
                com.google.android.material.R.attr.colorOnSurface));
        b.reply.setVisibility(View.VISIBLE);
        b.deletedSpacer.setVisibility(View.GONE);
        b.reply.setOnClickListener(v -> listener.replyTo(node));
        String description = context.getString(R.string.comment_cd, name, when, c.body());
        if (node.depth() > 0) description = context.getString(R.string.comment_thread_cd, node.depth() + 1) + ". " + description;
        b.commentRoot.setContentDescription(description);
        ViewCompat.replaceAccessibilityAction(b.commentRoot, AccessibilityActionCompat.ACTION_CLICK,
                context.getString(R.string.feed_view_profile, name), (view, args) -> {
                    listener.openProfile(c.authorId());
                    return true;
                });
    }

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemPostDetailBinding binding;
        HeaderHolder(ItemPostDetailBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }

    static final class CommentHolder extends RecyclerView.ViewHolder {
        final ItemCommentBinding binding;
        CommentHolder(ItemCommentBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }

    static final class MessageHolder extends RecyclerView.ViewHolder {
        final ItemFeedMessageBinding binding;
        MessageHolder(ItemFeedMessageBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }

    private static final DiffUtil.ItemCallback<Item> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Item a, @NonNull Item b) {
            return a.type() == b.type() && a.key().equals(b.key());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Item a, @NonNull Item b) {
            if (a.node() != null && b.node() != null) {
                CommentThreads.Node x = a.node();
                CommentThreads.Node y = b.node();
                return x.comment().equals(y.comment()) && x.depth() == y.depth()
                        && Arrays.equals(x.continues(), y.continues()) && x.hasChildren() == y.hasChildren()
                        && Objects.equals(x.replyingTo(), y.replyingTo());
            }
            return Objects.equals(a.post(), b.post()) && a.message() == b.message();
        }
    };
}
