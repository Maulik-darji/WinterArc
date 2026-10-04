package com.app.winterarc.ui.chat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentChatsBinding;
import com.app.winterarc.databinding.ItemConversationBinding;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;

import java.util.List;
import java.util.Locale;

public class ChatsFragment extends Fragment {
    public static final String ARG_PERSON_NAME = "personName";
    private FragmentChatsBinding binding;

    private record Conversation(String initial, String name, String message, String time, int unread, boolean online) {}

    private final List<Conversation> conversations = List.of(
            new Conversation("M", "Maya R.", "That sunrise run was worth it!", "9:42", 2, true),
            new Conversation("A", "Arjun S.", "I’ll try the 45-minute focus block.", "Yesterday", 0, true),
            new Conversation("N", "Nina K.", "Send me your next book pick 📚", "Tue", 0, false));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentChatsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        Ui.applySystemBarPadding(binding.chatsRoot, true, false);
        render("");
        binding.search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void render(String query) {
        if (binding == null) return;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        binding.conversations.removeAllViews();
        int matches = 0;
        for (Conversation conversation : conversations) {
            if (!conversation.name().toLowerCase(Locale.ROOT).contains(needle)) continue;
            matches++;
            ItemConversationBinding item = ItemConversationBinding.inflate(getLayoutInflater(), binding.conversations, false);
            item.avatar.setText(conversation.initial());
            item.name.setText(conversation.name());
            item.message.setText(conversation.message());
            item.time.setText(conversation.time());
            item.online.setVisibility(conversation.online() ? View.VISIBLE : View.GONE);
            item.unread.setVisibility(conversation.unread() > 0 ? View.VISIBLE : View.GONE);
            if (conversation.unread() > 0) item.unread.setText(String.valueOf(conversation.unread()));
            item.getRoot().setContentDescription(getString(R.string.chat_open_cd, conversation.name()));
            item.getRoot().setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putString(ARG_PERSON_NAME, conversation.name());
                Nav.go(this, R.id.chatDetailFragment, args);
            });
            binding.conversations.addView(item.getRoot());
        }
        binding.empty.setVisibility(matches == 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
