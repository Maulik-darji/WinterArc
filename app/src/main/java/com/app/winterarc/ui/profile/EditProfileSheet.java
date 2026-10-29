package com.app.winterarc.ui.profile;

import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.databinding.SheetEditProfileBinding;
import com.app.winterarc.domain.social.SocialResult;
import com.app.winterarc.domain.social.SocialUser;
import com.app.winterarc.ui.common.Avatars;
import com.app.winterarc.ui.common.Haptics;
import com.app.winterarc.ui.common.Ui;
import com.app.winterarc.ui.social.SocialViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.snackbar.Snackbar;

/**
 * Profile photo, banner, name and bio, the same fields as the website's Edit profile. Images upload
 * as soon as they are picked (system photo picker, no storage permission needed); name and bio
 * save with the button.
 */
public class EditProfileSheet extends BottomSheetDialogFragment {
    public static final String TAG = "edit_profile";
    private static final String STATE_PENDING_KIND = "pending_kind";
    private static final int NAME_MIN = 2;
    private static final int NAME_MAX = 50;
    private static final int BIO_MAX = 160;

    @Nullable private SheetEditProfileBinding binding;
    private SocialViewModel viewModel;
    private String pendingKind = ProfileImages.AVATAR;
    private boolean filled;
    private boolean saving;
    private ActivityResultLauncher<PickVisualMediaRequest> picker;

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        if (state != null) pendingKind = state.getString(STATE_PENDING_KIND, ProfileImages.AVATAR);
        picker = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), this::onPicked);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = SheetEditProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        SheetEditProfileBinding b = binding;
        if (b == null) return;
        viewModel = new ViewModelProvider(requireActivity()).get(SocialViewModel.class);
        b.changePhoto.setOnClickListener(v -> pick(ProfileImages.AVATAR));
        b.changeBanner.setOnClickListener(v -> pick(ProfileImages.BANNER));
        b.removePhoto.setOnClickListener(v -> remove(ProfileImages.AVATAR));
        b.removeBanner.setOnClickListener(v -> remove(ProfileImages.BANNER));
        b.save.setOnClickListener(v -> save());
        TextWatcher clearErrors = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (binding == null) return;
                binding.nameLayout.setError(null);
                binding.bioLayout.setError(null);
            }
        };
        b.name.addTextChangedListener(clearErrors);
        b.bio.addTextChangedListener(clearErrors);

        viewModel.state().observe(getViewLifecycleOwner(), s -> {
            SocialUser me = null;
            for (SocialUser u : s.users()) if (u.id().equals(s.currentUserId())) me = u;
            if (me == null || binding == null) return;
            Avatars.bind(binding.avatar, me.initial(), me.avatarUrl());
            Avatars.banner(binding.banner, me.bannerUrl());
            binding.removePhoto.setVisibility(me.avatarUrl() == null ? View.GONE : View.VISIBLE);
            binding.removeBanner.setVisibility(me.bannerUrl() == null ? View.GONE : View.VISIBLE);
            binding.handleNote.setText(getString(R.string.profile_handle_note, me.handle()));
            if (!filled) {
                filled = true;
                binding.name.setText(me.displayName());
                binding.bio.setText(me.bio());
            }
        });
        viewModel.messages().observe(getViewLifecycleOwner(), event -> {
            SocialViewModel.Message m = event.peek();
            if (!m.action().startsWith("profile")) return; // other screens' results
            if (event.consume() == null || binding == null) return;
            if (m.action().equals("profile_image")) {
                setUploading(false);
                if (m.result() == SocialResult.SUCCESS) {
                    Haptics.confirm(binding.getRoot());
                } else {
                    Haptics.reject(binding.getRoot());
                    snack(m.result() == SocialResult.INVALID_INPUT ? R.string.profile_image_failed
                            : R.string.profile_image_upload_failed);
                }
            } else if (saving) {
                saving = false;
                binding.save.setEnabled(true);
                if (m.result() == SocialResult.SUCCESS) {
                    Haptics.confirm(binding.getRoot());
                    dismissAllowingStateLoss();
                } else if (m.result() == SocialResult.INVALID_INPUT) {
                    Haptics.reject(binding.getRoot());
                    binding.nameLayout.setError(getString(R.string.profile_name_error));
                } else {
                    Haptics.reject(binding.getRoot());
                    snack(R.string.feed_error_unavailable);
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog dialog) {
            dialog.getBehavior().setSkipCollapsed(true);
            dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_PENDING_KIND, pendingKind);
    }

    private void pick(String kind) {
        if (binding != null) Haptics.tick(binding.getRoot());
        pendingKind = kind;
        picker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void onPicked(@Nullable Uri uri) {
        if (uri == null || binding == null) return; // picker dismissed
        setUploading(true);
        viewModel.setProfileImage(requireContext().getApplicationContext().getContentResolver(), uri, pendingKind);
    }

    private void remove(String kind) {
        if (binding == null) return;
        Haptics.soft(binding.getRoot());
        setUploading(true);
        viewModel.removeProfileImage(kind);
    }

    private void save() {
        SheetEditProfileBinding b = binding;
        if (b == null || saving) return;
        String name = Ui.text(b.name).trim();
        String bio = Ui.text(b.bio).trim();
        int nameLength = name.codePointCount(0, name.length());
        if (nameLength < NAME_MIN || nameLength > NAME_MAX) {
            Haptics.reject(b.getRoot());
            b.nameLayout.setError(getString(R.string.profile_name_error));
            return;
        }
        if (bio.codePointCount(0, bio.length()) > BIO_MAX) {
            Haptics.reject(b.getRoot());
            b.bioLayout.setError(getString(R.string.profile_bio_error));
            return;
        }
        Ui.hideKeyboard(b.getRoot());
        saving = true;
        b.save.setEnabled(false);
        viewModel.updateProfile(name, bio);
    }

    private void setUploading(boolean on) {
        if (binding == null) return;
        binding.uploading.setVisibility(on ? View.VISIBLE : View.GONE);
        binding.changePhoto.setEnabled(!on);
        binding.changeBanner.setEnabled(!on);
        binding.removePhoto.setEnabled(!on);
        binding.removeBanner.setEnabled(!on);
    }

    private void snack(int text) {
        if (binding != null) Snackbar.make(binding.getRoot(), text, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
