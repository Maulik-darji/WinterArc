package com.app.winterarc.ui.detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.databinding.SheetLogProgressBinding;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.EntryStatus;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.ProgressEntry;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** Log today's total, partial or more than the target, with an optional note. */
public class LogProgressSheet extends BottomSheetDialogFragment {
    private static final String KEY_VALUE = "value";
    private SheetLogProgressBinding binding;
    private GoalDetailViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = SheetLogProgressBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(requireParentFragment()).get(GoalDetailViewModel.class);
        GoalDetailViewModel.DetailState s = viewModel.state().getValue();
        if (s == null) {
            dismissAllowingStateLoss();
            return;
        }
        Goal goal = s.goal();
        ProgressEntry entry = s.todayEntry();
        Amount target = entry != null && entry.status() != EntryStatus.NOTE_ONLY ? entry.scheduledTarget() : goal.currentTarget();
        binding.subtitle.setText(getString(R.string.log_subtitle,
                Formats.amountWithUnit(requireContext(), target, goal.unit(), goal.customUnitLabel())));
        binding.picker.configure(goal.unit(), goal.customUnitLabel());

        Amount initial;
        if (state != null && state.containsKey(KEY_VALUE)) {
            initial = new Amount(state.getLong(KEY_VALUE));
        } else if (entry != null && entry.actualValue().isPositive()) {
            initial = entry.actualValue();
        } else {
            initial = target;
        }
        binding.picker.setOnAmountChangedListener(a -> updateHint(a, target));
        binding.picker.setAmount(initial);
        if (state == null && entry != null && entry.note() != null) binding.note.setText(entry.note());
        updateHint(initial, target);

        binding.save.setOnClickListener(v -> {
            String note = binding.note.getText() == null ? null : binding.note.getText().toString();
            viewModel.logValue(binding.picker.getAmount(), note);
            dismiss();
        });
    }

    private void updateHint(Amount value, Amount target) {
        boolean completes = value.isPositive() && value.atLeast(target);
        binding.hint.setText(completes ? R.string.log_will_complete : R.string.log_will_partial);
        binding.save.setEnabled(value.isPositive());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (binding != null) out.putLong(KEY_VALUE, binding.picker.getAmount().milli());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
