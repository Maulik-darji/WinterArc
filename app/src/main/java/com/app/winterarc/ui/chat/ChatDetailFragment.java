package com.app.winterarc.ui.chat;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentChatDetailBinding;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;

public class ChatDetailFragment extends Fragment {
    private FragmentChatDetailBinding binding;
    private String personName;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentChatDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        personName = requireArguments().getString(ChatsFragment.ARG_PERSON_NAME, "Connection");
        Ui.applySystemBarPadding(binding.chatRoot, true, false);
        Ui.applySystemBarPadding(binding.composer, false, true);
        binding.toolbar.setTitle(personName);
        binding.toolbar.setSubtitle(R.string.chat_online);
        binding.toolbar.setNavigationOnClickListener(v -> Nav.up(this));

        addBubble(getString(R.string.chat_start_note), false, true);
        addBubble(greetingFor(personName), false, false);
        binding.send.setOnClickListener(v -> send());
        binding.message.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEND) return false;
            send();
            return true;
        });
    }

    private String greetingFor(String name) {
        if (name.startsWith("Maya")) return "Hey! Are you still aiming for your morning goal tomorrow?";
        if (name.startsWith("Arjun")) return "Nice to connect. How did your focus session go today?";
        return "I just added another book to my list. How is your current goal going?";
    }

    private void send() {
        String message = binding.message.getText() == null ? "" : binding.message.getText().toString().trim();
        if (message.isEmpty()) return;
        addBubble(message, true, false);
        binding.message.setText("");
        binding.scroll.post(() -> binding.scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addBubble(String text, boolean sent, boolean system) {
        TextView bubble = new TextView(requireContext());
        bubble.setText(text);
        bubble.setTextSize(system ? 12 : 16);
        bubble.setMaxWidth(Ui.dp(requireContext(), system ? 320 : 286));
        int horizontal = Ui.dp(requireContext(), system ? 8 : 16);
        int vertical = Ui.dp(requireContext(), system ? 6 : 12);
        bubble.setPadding(horizontal, vertical, horizontal, vertical);

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(Ui.dp(requireContext(), system ? 12 : 18));
        if (system) {
            background.setColor(ContextCompat.getColor(requireContext(), R.color.wa_surface_container));
            bubble.setTextColor(ContextCompat.getColor(requireContext(), R.color.wa_on_surface_variant));
        } else if (sent) {
            background.setColor(ContextCompat.getColor(requireContext(), R.color.wa_primary_container));
            bubble.setTextColor(ContextCompat.getColor(requireContext(), R.color.wa_on_primary_container));
        } else {
            background.setColor(ContextCompat.getColor(requireContext(), R.color.wa_surface_variant));
            bubble.setTextColor(ContextCompat.getColor(requireContext(), R.color.wa_on_surface));
        }
        bubble.setBackground(background);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = system ? Gravity.CENTER_HORIZONTAL : sent ? Gravity.END : Gravity.START;
        params.bottomMargin = Ui.dp(requireContext(), 10);
        bubble.setLayoutParams(params);
        binding.messages.addView(bubble);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
