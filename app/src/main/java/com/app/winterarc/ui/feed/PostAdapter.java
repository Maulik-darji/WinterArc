package com.app.winterarc.ui.feed;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.app.winterarc.R;
import com.app.winterarc.core.time.TimeProvider;
import com.app.winterarc.databinding.ItemFeedPostBinding;
import com.app.winterarc.domain.feed.FeedPost;
import com.app.winterarc.domain.social.SocialUser;

import java.util.Map;

/** Timeline rows. Author info is resolved at bind time from the latest user map. */
final class PostAdapter extends ListAdapter<FeedPost, PostAdapter.Holder> {
    interface Listener {
        void open(FeedPost post);
        void openProfile(String userId);
        void reply(FeedPost post);
        void like(View button, FeedPost post);
        void share(FeedPost post);
        void more(View anchor, FeedPost post);
    }

    private final Listener listener;
    private final TimeProvider time;
    private Map<String, SocialUser> users = Map.of();
    private String viewerId = "";

    PostAdapter(Listener listener, TimeProvider time) {
        super(DIFF);
        this.listener = listener;
        this.time = time;
    }

    void setContext(String viewerId, Map<String, SocialUser> users) {
        boolean changed = !users.equals(this.users);
        this.viewerId = viewerId;
        this.users = users;
        if (changed) notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemFeedPostBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FeedPost post = getItem(position);
        ItemFeedPostBinding b = holder.binding;
        Context context = b.getRoot().getContext();
        String name = FeedFormat.name(context, users, post.authorId());
        String when = FeedFormat.relative(context, post.createdAt(), time.now(), time.zone());
        FeedFormat.avatar(b.avatar, users, post.authorId());
        b.name.setText(name);
        b.meta.setText(FeedFormat.meta(context, FeedFormat.handle(context, users, post.authorId()), when, post.isEdited()));
        b.body.setText(post.body());
        PostActions.bindReply(b.reply, post.replyCount(), true);
        PostActions.bindLike(b.like, post.likedByMe(), post.likeCount(), true);
        boolean mine = post.authorId().equals(viewerId);
        b.more.setVisibility(mine ? View.VISIBLE : View.INVISIBLE);
        b.more.setEnabled(mine);
        b.row.setContentDescription(context.getString(R.string.feed_post_cd, name,
                FeedFormat.handle(context, users, post.authorId()), when, post.body()));
        b.row.setOnClickListener(v -> listener.open(post));
        View.OnClickListener profile = v -> listener.openProfile(post.authorId());
        b.avatar.setOnClickListener(profile);
        b.name.setOnClickListener(profile);
        ViewCompat.replaceAccessibilityAction(b.row, AccessibilityActionCompat.ACTION_LONG_CLICK,
                context.getString(R.string.feed_view_profile, name), (view, args) -> {
                    listener.openProfile(post.authorId());
                    return true;
                });
        b.reply.setOnClickListener(v -> listener.reply(post));
        b.like.setOnClickListener(v -> listener.like(v, post));
        b.share.setOnClickListener(v -> listener.share(post));
        b.more.setOnClickListener(v -> listener.more(v, post));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemFeedPostBinding binding;

        Holder(ItemFeedPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static final DiffUtil.ItemCallback<FeedPost> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull FeedPost a, @NonNull FeedPost b) {
            return a.id().equals(b.id());
        }

        @Override
        public boolean areContentsTheSame(@NonNull FeedPost a, @NonNull FeedPost b) {
            return a.equals(b);
        }
    };
}
