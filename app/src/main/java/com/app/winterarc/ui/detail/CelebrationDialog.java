package com.app.winterarc.ui.detail;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.databinding.DialogCelebrationBinding;
import com.app.winterarc.databinding.ItemOptionCardBinding;
import com.app.winterarc.domain.engine.SafetyAdvisor;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalUnit;
import com.app.winterarc.domain.model.ProgressionPlan;
import com.app.winterarc.ui.common.DomainText;
import com.app.winterarc.ui.common.Ui;
import com.app.winterarc.ui.editor.MilestoneOptions;

/**
 * Full-screen milestone celebration: "You reached 50 km. What would you like to do next?"
 * Maintain, continue progressing, or mark complete. Nothing is reset or discarded.
 */
public class CelebrationDialog extends DialogFragment {
    private static final String KEY_PAGE = "page";
    private static final String KEY_FINAL = "final";
    private static final String KEY_HOP = "hop";

    private DialogCelebrationBinding binding;
    private GoalDetailViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, R.style.Theme_WinterArc_Celebration);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) WindowCompat.setDecorFitsSystemWindows(window, false);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog() == null ? null : getDialog().getWindow();
        if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = DialogCelebrationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(requireParentFragment()).get(GoalDetailViewModel.class);
        GoalDetailViewModel.DetailState s = viewModel.state().getValue();
        if (s == null || s.goal().plan() == null) {
            dismissAllowingStateLoss();
            return;
        }
        Goal goal = s.goal();
        ProgressionPlan plan = goal.plan();
        Context c = requireContext();
        Ui.applySystemBarPadding(binding.celebrationRoot, true, true);
        String reached = Formats.amountWithUnit(c, plan.finalTarget(), goal.unit(), goal.customUnitLabel());

        binding.title.setText(getString(R.string.celebration_title, reached));
        binding.goalTitle.setText(goal.title());
        binding.trophy.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(c, R.color.wa_on_primary_container)));

        option(binding.optMaintain, R.drawable.ic_repeat, getString(R.string.celebration_maintain),
                getString(R.string.celebration_maintain_desc, reached), v -> {
                    viewModel.maintain();
                    dismiss();
                });
        option(binding.optContinue, R.drawable.ic_trending_up, getString(R.string.celebration_continue),
                getString(R.string.celebration_continue_desc), v -> showPage(1));
        option(binding.optComplete, R.drawable.ic_flag, getString(R.string.celebration_complete),
                getString(R.string.celebration_complete_desc), v -> {
                    viewModel.markComplete();
                    dismiss();
                });
        binding.later.setOnClickListener(v -> dismiss());

        // Continue form
        binding.newFinal.configure(goal.unit(), goal.customUnitLabel());
        binding.newHop.configure(goal.unit(), goal.customUnitLabel());
        Amount suggestedFinal = state != null ? new Amount(state.getLong(KEY_FINAL))
                : plan.finalTarget().plus(Amount.max(plan.hop(), Amount.whole(5)));
        Amount suggestedHop = state != null ? new Amount(state.getLong(KEY_HOP)) : plan.hop();
        binding.newFinal.setOnAmountChangedListener(a -> updateContinueForm(goal, plan));
        binding.newHop.setOnAmountChangedListener(a -> updateContinueForm(goal, plan));
        binding.newFinal.setAmount(suggestedFinal);
        binding.newHop.setAmount(suggestedHop);
        updateContinueForm(goal, plan);
        binding.continueBack.setOnClickListener(v -> showPage(0));
        binding.continueSave.setOnClickListener(v -> {
            viewModel.continueProgressing(binding.newFinal.getAmount(), binding.newHop.getAmount());
            dismiss();
        });

        showPage(state != null ? state.getInt(KEY_PAGE) : 0);
        if (state == null) {
            binding.confetti.post(() -> binding.confetti.play());
            Ui.confirmHaptic(binding.getRoot(), viewModel.hapticsEnabled());
            binding.title.announceForAccessibility(binding.title.getText());
        }
    }

    private void updateContinueForm(Goal goal, ProgressionPlan plan) {
        Context c = requireContext();
        Amount newFinal = binding.newFinal.getAmount();
        Amount hop = binding.newHop.getAmount();
        boolean valid = newFinal.greaterThan(plan.finalTarget()) && hop.isPositive();
        binding.continueSave.setEnabled(valid);
        if (!newFinal.greaterThan(plan.finalTarget())) {
            binding.newFinalMessage.setText(getString(R.string.continue_error,
                    Formats.amountWithUnit(c, plan.finalTarget(), goal.unit(), goal.customUnitLabel())));
            binding.newFinalMessage.setTextColor(ContextCompat.getColor(c, R.color.wa_error));
        } else {
            int message = R.string.milestone_msg_custom;
            if (goal.unit() == GoalUnit.KILOMETERS && goal.activityType().isEndurance()) {
                for (MilestoneOptions.Option o : MilestoneOptions.above(Amount.ZERO)) {
                    if (!o.isCustom() && o.value().equals(newFinal)) message = o.message();
                }
            }
            binding.newFinalMessage.setText(message);
            binding.newFinalMessage.setTextColor(ContextCompat.getColor(c, R.color.wa_on_surface_variant));
        }
        SafetyAdvisor.Assessment safety = SafetyAdvisor.assess(goal.activityType(), true,
                goal.unit().toKilometers(plan.finalTarget()), goal.unit().toKilometers(newFinal),
                goal.unit().toKilometers(hop), goal.schedule().isEveryDay());
        boolean show = safety.level() != SafetyAdvisor.Level.NONE;
        binding.safety.getRoot().setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) binding.safety.safetyReasons.setText(DomainText.safetyReasons(c, safety));
    }

    private void option(ItemOptionCardBinding card, int icon, String name, String desc, View.OnClickListener click) {
        card.icon.setImageResource(icon);
        card.name.setText(name);
        card.desc.setText(desc);
        card.getRoot().setOnClickListener(click);
    }

    private void showPage(int page) {
        binding.flipper.setDisplayedChild(page);
        View heading = page == 0 ? binding.title : binding.continueForm;
        heading.post(() -> heading.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (binding == null) return;
        out.putInt(KEY_PAGE, binding.flipper.getDisplayedChild());
        out.putLong(KEY_FINAL, binding.newFinal.getAmount().milli());
        out.putLong(KEY_HOP, binding.newHop.getAmount().milli());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
