package com.app.winterarc.ui.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.databinding.SheetSkipBinding;
import com.app.winterarc.domain.model.SkipReason;
import com.app.winterarc.ui.common.GoalVisuals;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;

/** Skip today with a reason. Supportive copy: a planned rest never breaks a streak. */
public class SkipSheet extends BottomSheetDialogFragment {
    private SheetSkipBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = SheetSkipBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        GoalDetailViewModel viewModel = new ViewModelProvider(requireParentFragment()).get(GoalDetailViewModel.class);
        for (SkipReason reason : SkipReason.values()) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_choice_chip, binding.reasons, false);
            chip.setId(View.generateViewId());
            chip.setTag(reason);
            chip.setText(GoalVisuals.skipReason(reason));
            chip.setChecked(reason == SkipReason.REST && state == null);
            binding.reasons.addView(chip);
        }
        binding.confirm.setOnClickListener(v -> {
            SkipReason reason = SkipReason.REST;
            int checked = binding.reasons.getCheckedChipId();
            if (checked != View.NO_ID) reason = (SkipReason) binding.reasons.findViewById(checked).getTag();
            String note = binding.note.getText() == null ? null : binding.note.getText().toString();
            viewModel.skip(reason, note);
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
