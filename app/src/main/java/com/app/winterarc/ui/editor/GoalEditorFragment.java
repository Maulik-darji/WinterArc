package com.app.winterarc.ui.editor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.databinding.EditorStepTargetBinding;
import com.app.winterarc.databinding.FragmentGoalEditorBinding;
import com.app.winterarc.databinding.ItemActivityCardBinding;
import com.app.winterarc.databinding.ItemMilestoneOptionBinding;
import com.app.winterarc.databinding.ItemModeCardBinding;
import com.app.winterarc.databinding.ItemReviewRowBinding;
import com.app.winterarc.domain.engine.GoalValidator;
import com.app.winterarc.domain.engine.ProgressionEngine;
import com.app.winterarc.domain.engine.SafetyAdvisor;
import com.app.winterarc.domain.model.ActivityType;
import com.app.winterarc.domain.model.Amount;
import com.app.winterarc.domain.model.GoalColor;
import com.app.winterarc.domain.model.GoalDraft;
import com.app.winterarc.domain.model.GoalIcon;
import com.app.winterarc.domain.model.GoalUnit;
import com.app.winterarc.domain.model.ProgressionPlan;
import com.app.winterarc.domain.model.TrackingMode;
import com.app.winterarc.domain.model.WeeklySchedule;
import com.app.winterarc.domain.repository.PreferencesRepository;
import com.app.winterarc.ui.common.DomainText;
import com.app.winterarc.ui.common.GoalVisuals;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/** Six-step goal creation wizard; also used to edit an existing goal (opens on the review step). */
@AndroidEntryPoint
public class GoalEditorFragment extends Fragment {

    @Inject PreferencesRepository preferences;

    private FragmentGoalEditorBinding binding;
    private GoalEditorViewModel viewModel;
    private final Map<ActivityType, ItemActivityCardBinding> activityCards = new EnumMap<>(ActivityType.class);
    private final Map<DayOfWeek, MaterialButton> dayButtons = new EnumMap<>(DayOfWeek.class);
    private MilestoneAdapter milestoneAdapter;
    private GoalUnit chipsForUnitsOf;
    private ActivityType chipsForActivity;
    /** Suppresses listener callbacks while the UI is being rendered from state. */
    private boolean rendering;
    private ActivityResultLauncher<String> notificationPermission;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            preferences.setNotificationPermissionAsked();
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentGoalEditorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this).get(GoalEditorViewModel.class);
        Ui.applySystemBarPadding(binding.editorRoot, true, true);
        binding.toolbar.setTitle(viewModel.isEditing() ? R.string.editor_title_edit : R.string.editor_title_new);
        binding.toolbar.setNavigationOnClickListener(v -> attemptExit());
        binding.progress.setMax(GoalEditorViewModel.STEP_COUNT);

        setUpActivityStep();
        setUpModeStep();
        setUpTargetStep();
        setUpDetailsStep();
        setUpScheduleStep();
        setUpReviewStep();

        binding.back.setOnClickListener(v -> {
            if (!viewModel.back()) attemptExit();
        });
        binding.next.setOnClickListener(v -> {
            GoalEditorViewModel.UiState s = viewModel.state().getValue();
            if (s == null) return;
            if (s.step() == GoalEditorViewModel.STEP_REVIEW) {
                viewModel.save();
            } else {
                if (s.step() == GoalEditorViewModel.STEP_TARGET) {
                    viewModel.setTitleIfBlank(getString(GoalVisuals.activityName(s.draft().activityType)));
                }
                viewModel.next();
            }
        });
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!viewModel.back()) attemptExit();
            }
        });

        viewModel.state().observe(getViewLifecycleOwner(), this::render);
        viewModel.saved().observe(getViewLifecycleOwner(), event -> {
            Long id = event.consume();
            if (id == null) return;
            if (viewModel.isEditing()) {
                Nav.up(this);
            } else {
                NavOptions options = new NavOptions.Builder()
                        .setPopUpTo(R.id.goalEditorFragment, true)
                        .setEnterAnim(R.anim.wa_enter).setExitAnim(R.anim.wa_exit)
                        .setPopEnterAnim(R.anim.wa_pop_enter).setPopExitAnim(R.anim.wa_pop_exit)
                        .build();
                NavHostFragment.findNavController(this).navigate(R.id.goalDetailFragment, Nav.goal(id), options);
            }
        });
        viewModel.confirmSafety().observe(getViewLifecycleOwner(), event -> {
            if (event.consume() == null) return;
            GoalEditorViewModel.UiState s = viewModel.state().getValue();
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.safety_confirm_title)
                    .setMessage(DomainText.safetyReasons(requireContext(), s.safety()) + "\n\n" + getString(R.string.safety_footer))
                    .setPositiveButton(R.string.safety_confirm_action, (d, w) -> viewModel.acknowledgeSafety())
                    .setNegativeButton(R.string.safety_adjust_action, (d, w) -> viewModel.jumpTo(GoalEditorViewModel.STEP_TARGET))
                    .show();
        });
    }

    private void attemptExit() {
        GoalEditorViewModel.UiState s = viewModel.state().getValue();
        boolean dirty = s != null && !s.editing() && s.step() > GoalEditorViewModel.STEP_ACTIVITY;
        if (!dirty) {
            Nav.up(this);
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.editor_discard_title)
                .setMessage(R.string.editor_discard_body)
                .setPositiveButton(R.string.editor_discard, (d, w) -> Nav.up(this))
                .setNegativeButton(R.string.editor_keep_editing, null)
                .show();
    }

    // ---- Step setup -------------------------------------------------------------------------

    private void setUpActivityStep() {
        activityCards.put(ActivityType.WALKING, binding.stepActivity.walking);
        activityCards.put(ActivityType.RUNNING, binding.stepActivity.running);
        activityCards.put(ActivityType.READING, binding.stepActivity.reading);
        activityCards.put(ActivityType.MEDITATION, binding.stepActivity.meditation);
        activityCards.put(ActivityType.CODING, binding.stepActivity.coding);
        activityCards.put(ActivityType.CUSTOM, binding.stepActivity.custom);
        for (Map.Entry<ActivityType, ItemActivityCardBinding> e : activityCards.entrySet()) {
            ActivityType type = e.getKey();
            ItemActivityCardBinding card = e.getValue();
            card.name.setText(GoalVisuals.activityName(type));
            int desc = type == ActivityType.WALKING ? R.string.activity_walking_desc
                    : type == ActivityType.RUNNING ? R.string.activity_running_desc
                    : type == ActivityType.CUSTOM ? R.string.activity_custom_desc : 0;
            if (desc != 0) card.desc.setText(desc); else card.desc.setVisibility(View.GONE);
            card.icon.setImageResource(GoalVisuals.icon(type.defaultIcon()));
            int color = GoalVisuals.color(requireContext(), type.defaultColor());
            card.icon.setImageTintList(ColorStateList.valueOf(color));
            card.icon.setBackgroundTintList(ColorStateList.valueOf(GoalVisuals.container(requireContext(), type.defaultColor())));
            card.getRoot().setOnClickListener(v -> {
                viewModel.selectActivity(type);
                if (!viewModel.isEditing()) viewModel.next();
            });
        }
    }

    private void setUpModeStep() {
        Context c = requireContext();
        bindModeCard(binding.stepMode.consistency, TrackingMode.CONSISTENCY, R.string.mode_consistency_desc,
                R.string.mode_consistency_example, ContextCompat.getColor(c, R.color.wa_primary));
        binding.stepMode.consistency.bars.setFlat(10, 10, ContextCompat.getColor(c, R.color.wa_primary));
        bindModeCard(binding.stepMode.progression, TrackingMode.PROGRESSION, R.string.mode_progression_desc,
                R.string.mode_progression_example, ContextCompat.getColor(c, R.color.wa_tertiary));
        binding.stepMode.progression.bars.setRising(10, 10, ContextCompat.getColor(c, R.color.wa_tertiary));
    }

    private void bindModeCard(ItemModeCardBinding card, TrackingMode mode, int desc, int example, int color) {
        card.name.setText(GoalVisuals.modeName(mode));
        card.desc.setText(desc);
        card.example.setText(example);
        card.icon.setImageResource(GoalVisuals.modeIcon(mode));
        card.icon.setImageTintList(ColorStateList.valueOf(color));
        card.getRoot().setOnClickListener(v -> {
            viewModel.selectMode(mode);
            if (!viewModel.isEditing()) viewModel.next();
        });
    }

    private void setUpTargetStep() {
        EditorStepTargetBinding t = binding.stepTarget;
        t.consistencyPicker.setOnAmountChangedListener(a -> { if (!rendering) viewModel.setConsistencyTarget(a); });
        t.startPicker.setOnAmountChangedListener(a -> { if (!rendering) viewModel.setStartTarget(a); });
        t.finalPicker.setOnAmountChangedListener(a -> { if (!rendering) viewModel.setFinalTarget(a); });
        t.hopPicker.setOnAmountChangedListener(a -> { if (!rendering) viewModel.setHop(a); });
        t.unitChips.setOnCheckedStateChangeListener((group, ids) -> {
            if (rendering || ids.isEmpty()) return;
            Object tag = group.findViewById(ids.get(0)).getTag();
            if (tag instanceof GoalUnit) viewModel.setUnit((GoalUnit) tag);
        });
        t.customUnit.addTextChangedListener(watcher(text -> viewModel.setCustomUnitLabel(text)));

        milestoneAdapter = new MilestoneAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false);
        t.milestones.setLayoutManager(lm);
        t.milestones.setAdapter(milestoneAdapter);
        new LinearSnapHelper().attachToRecyclerView(t.milestones);
    }

    private void setUpDetailsStep() {
        binding.stepDetails.title.addTextChangedListener(watcher(text -> viewModel.setTitle(text)));
        binding.stepDetails.description.addTextChangedListener(watcher(text -> viewModel.setDescription(text)));
        Context c = requireContext();
        for (GoalIcon icon : GoalIcon.values()) {
            Chip chip = (Chip) LayoutInflater.from(c).inflate(R.layout.item_choice_chip, binding.stepDetails.icons, false);
            chip.setId(View.generateViewId());
            chip.setTag(icon);
            chip.setChipIconResource(GoalVisuals.icon(icon));
            chip.setContentDescription(icon.key());
            chip.setOnClickListener(v -> viewModel.setIcon(icon));
            binding.stepDetails.icons.addView(chip);
        }
        for (GoalColor color : GoalColor.values()) {
            Chip chip = (Chip) LayoutInflater.from(c).inflate(R.layout.item_choice_chip, binding.stepDetails.colors, false);
            chip.setId(View.generateViewId());
            chip.setTag(color);
            int value = GoalVisuals.color(c, color);
            chip.setChipIconResource(R.drawable.ic_color_dot);
            chip.setChipIconTint(ColorStateList.valueOf(value));
            chip.setContentDescription(color.key());
            chip.setOnClickListener(v -> viewModel.setColor(color));
            binding.stepDetails.colors.addView(chip);
        }
    }

    private void setUpScheduleStep() {
        Context c = requireContext();
        Locale locale = Formats.locale(c);
        LinearLayout days = binding.stepSchedule.days;
        for (DayOfWeek day : DomainText.orderedDays(locale)) {
            MaterialButton button = (MaterialButton) LayoutInflater.from(c).inflate(R.layout.item_day_toggle, days, false);
            button.setText(day.getDisplayName(TextStyle.NARROW, locale));
            button.setContentDescription(day.getDisplayName(TextStyle.FULL, locale));
            button.addOnCheckedChangeListener((b, checked) -> { if (!rendering) viewModel.toggleDay(day, checked); });
            dayButtons.put(day, button);
            days.addView(button);
        }
        binding.stepSchedule.everyDay.setOnClickListener(v -> viewModel.setSchedule(WeeklySchedule.EVERY_DAY));
        binding.stepSchedule.weekdays.setOnClickListener(v -> viewModel.setSchedule(WeeklySchedule.of(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)));
        binding.stepSchedule.reminderSwitch.setOnCheckedChangeListener((b, checked) -> {
            if (rendering) return;
            viewModel.setReminderEnabled(checked);
            if (checked) requestNotificationPermissionIfNeeded();
        });
        binding.stepSchedule.reminderTime.setOnClickListener(v -> pickTime());
        binding.stepSchedule.startDate.setOnClickListener(v -> pickDate());
    }

    private void setUpReviewStep() {
        bindReviewRow(binding.stepReview.rowMode, R.string.editor_review_mode, GoalEditorViewModel.STEP_MODE);
        bindReviewRow(binding.stepReview.rowTarget, R.string.editor_review_target, GoalEditorViewModel.STEP_TARGET);
        bindReviewRow(binding.stepReview.rowSchedule, R.string.editor_review_schedule, GoalEditorViewModel.STEP_SCHEDULE);
        bindReviewRow(binding.stepReview.rowReminder, R.string.editor_review_reminder, GoalEditorViewModel.STEP_SCHEDULE);
        bindReviewRow(binding.stepReview.rowStart, R.string.editor_review_start, GoalEditorViewModel.STEP_SCHEDULE);
        binding.stepReview.reviewHeader.setOnClickListener(v -> viewModel.jumpTo(GoalEditorViewModel.STEP_DETAILS));
    }

    private void bindReviewRow(ItemReviewRowBinding row, int label, int step) {
        row.label.setText(label);
        row.getRoot().setOnClickListener(v -> viewModel.jumpTo(step));
    }

    // ---- Rendering --------------------------------------------------------------------------

    private void render(GoalEditorViewModel.UiState s) {
        rendering = true;
        try {
            GoalDraft d = s.draft();
            int step = s.step();
            if (binding.flipper.getDisplayedChild() != step) {
                binding.flipper.setDisplayedChild(step);
                binding.scroll.scrollTo(0, 0);
                binding.flipper.getChildAt(step).sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
            }
            binding.progress.setProgressCompat(step + 1, !Ui.reducedMotion(requireContext()));
            binding.stepLabel.setText(getString(R.string.editor_step_of, step + 1, GoalEditorViewModel.STEP_COUNT));
            binding.back.setVisibility(step == GoalEditorViewModel.STEP_ACTIVITY || (s.editing() && step == GoalEditorViewModel.STEP_REVIEW)
                    ? View.INVISIBLE : View.VISIBLE);
            boolean review = step == GoalEditorViewModel.STEP_REVIEW;
            binding.next.setText(review ? (s.editing() ? R.string.editor_save_changes : R.string.editor_create)
                    : s.editing() ? R.string.action_done : R.string.action_next);
            binding.next.setEnabled(!s.saving() && !s.loading());

            for (Map.Entry<ActivityType, ItemActivityCardBinding> e : activityCards.entrySet()) {
                boolean checked = e.getKey() == d.activityType;
                e.getValue().getRoot().setChecked(checked);
                e.getValue().check.setVisibility(checked ? View.VISIBLE : View.INVISIBLE);
            }
            boolean consistency = d.trackingMode == TrackingMode.CONSISTENCY;
            binding.stepMode.consistency.getRoot().setChecked(consistency);
            binding.stepMode.consistency.check.setVisibility(consistency ? View.VISIBLE : View.INVISIBLE);
            binding.stepMode.progression.getRoot().setChecked(!consistency);
            binding.stepMode.progression.check.setVisibility(!consistency ? View.VISIBLE : View.INVISIBLE);

            renderTarget(s);
            renderDetails(s);
            renderSchedule(s);
            renderReview(s);
        } finally {
            rendering = false;
        }
    }

    private void renderTarget(GoalEditorViewModel.UiState s) {
        GoalDraft d = s.draft();
        EditorStepTargetBinding t = binding.stepTarget;
        Context c = requireContext();
        boolean progression = d.trackingMode == TrackingMode.PROGRESSION;
        t.targetHeading.setText(progression ? R.string.editor_step_target_progression : R.string.editor_step_target_consistency);
        rebuildUnitChips(d);
        t.customUnitLayout.setVisibility(d.unit == GoalUnit.CUSTOM ? View.VISIBLE : View.GONE);
        setTextIfChanged(t.customUnit, d.customUnitLabel);
        t.customUnitLayout.setError(s.errors().contains(GoalValidator.Error.CUSTOM_UNIT_BLANK)
                ? getString(R.string.error_custom_unit_blank) : null);

        t.consistencySection.setVisibility(progression ? View.GONE : View.VISIBLE);
        t.progressionSection.setVisibility(progression ? View.VISIBLE : View.GONE);
        String label = d.customUnitLabel;
        configure(t.consistencyPicker, d.unit, label, d.consistencyTarget);
        configure(t.startPicker, d.unit, label, d.startTarget);
        configure(t.hopPicker, d.unit, label, d.hop);
        configure(t.finalPicker, d.unit, label, d.finalTarget);

        // Milestone carousel for walking and running in kilometres.
        boolean carousel = progression && d.activityType.isEndurance() && d.unit == GoalUnit.KILOMETERS;
        t.milestones.setVisibility(carousel ? View.VISIBLE : View.GONE);
        t.milestoneMessage.setVisibility(carousel ? View.VISIBLE : View.GONE);
        if (carousel) {
            milestoneAdapter.submit(MilestoneOptions.above(d.startTarget), d.finalTarget, s.customFinal());
            MilestoneOptions.Option selected = milestoneAdapter.selectedOption();
            t.milestoneMessage.setText(selected != null ? selected.message() : R.string.milestone_msg_custom);
            t.finalPickerCard.setVisibility(selected != null && selected.isCustom() ? View.VISIBLE : View.GONE);
        } else {
            t.finalPickerCard.setVisibility(View.VISIBLE);
        }

        // Plan preview
        ProgressionPlan plan = d.plan();
        boolean validPlan = plan != null && ProgressionEngine.isValid(plan)
                && !s.errors().contains(GoalValidator.Error.TOO_MANY_STEPS)
                && GoalValidator.validateTargets(d).isEmpty();
        t.planPreview.setVisibility(progression && validPlan ? View.VISIBLE : View.GONE);
        if (progression && validPlan) {
            List<Amount> steps = ProgressionEngine.steps(plan);
            t.planBars.setSteps(steps, steps.size(), GoalVisuals.color(c, d.color));
            t.planSteps.setText(DomainText.steps(c, steps, d.unit, label));
            t.planCount.setText(getResources().getQuantityString(R.plurals.editor_plan_steps, steps.size(), steps.size()));
        }

        List<GoalValidator.Error> targetErrors = new ArrayList<>();
        for (GoalValidator.Error e : s.errors()) {
            if (GoalEditorViewModel.stepOf(e) == GoalEditorViewModel.STEP_TARGET && e != GoalValidator.Error.CUSTOM_UNIT_BLANK) {
                targetErrors.add(e);
            }
        }
        t.targetError.setVisibility(targetErrors.isEmpty() ? View.GONE : View.VISIBLE);
        t.targetError.setText(DomainText.errors(c, new java.util.LinkedHashSet<>(targetErrors)));
        renderSafety(t.safety.getRoot(), t.safety.safetyReasons, s.safety());
    }

    private void renderSafety(View card, android.widget.TextView reasons, SafetyAdvisor.Assessment safety) {
        boolean show = safety.level() != SafetyAdvisor.Level.NONE;
        card.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) reasons.setText(DomainText.safetyReasons(requireContext(), safety));
    }

    private void configure(com.app.winterarc.ui.common.AmountPickerView picker, GoalUnit unit, String label, Amount value) {
        picker.configure(unit, label);
        if (!picker.getAmount().equals(value)) picker.setAmount(value);
    }

    private void rebuildUnitChips(GoalDraft d) {
        if (d.activityType == chipsForActivity && d.unit == chipsForUnitsOf) return;
        chipsForActivity = d.activityType;
        chipsForUnitsOf = d.unit;
        binding.stepTarget.unitChips.removeAllViews();
        for (GoalUnit unit : unitsFor(d.activityType)) {
            Chip chip = (Chip) LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_choice_chip, binding.stepTarget.unitChips, false);
            chip.setId(View.generateViewId());
            chip.setTag(unit);
            chip.setText(Formats.unitName(requireContext(), unit));
            chip.setChecked(unit == d.unit);
            binding.stepTarget.unitChips.addView(chip);
        }
    }

    private static GoalUnit[] unitsFor(ActivityType type) {
        switch (type) {
            case WALKING:
            case RUNNING:
                return new GoalUnit[]{GoalUnit.KILOMETERS, GoalUnit.MILES, GoalUnit.MINUTES};
            case READING:
                return new GoalUnit[]{GoalUnit.PAGES, GoalUnit.MINUTES, GoalUnit.SESSIONS};
            case MEDITATION:
                return new GoalUnit[]{GoalUnit.MINUTES, GoalUnit.SESSIONS};
            case CODING:
                return new GoalUnit[]{GoalUnit.SESSIONS, GoalUnit.MINUTES};
            default:
                return GoalUnit.values();
        }
    }

    private void renderDetails(GoalEditorViewModel.UiState s) {
        GoalDraft d = s.draft();
        setTextIfChanged(binding.stepDetails.title, d.title);
        setTextIfChanged(binding.stepDetails.description, d.description);
        binding.stepDetails.titleLayout.setError(errorFor(s, GoalValidator.Error.TITLE_BLANK, GoalValidator.Error.TITLE_TOO_LONG));
        binding.stepDetails.descriptionLayout.setError(errorFor(s, GoalValidator.Error.DESCRIPTION_TOO_LONG));
        int goalColor = GoalVisuals.color(requireContext(), d.color);
        for (int i = 0; i < binding.stepDetails.icons.getChildCount(); i++) {
            Chip chip = (Chip) binding.stepDetails.icons.getChildAt(i);
            boolean checked = chip.getTag() == d.icon;
            chip.setChecked(checked);
            chip.setChipIconTint(ColorStateList.valueOf(checked ? goalColor
                    : ContextCompat.getColor(requireContext(), R.color.wa_on_surface_variant)));
        }
        for (int i = 0; i < binding.stepDetails.colors.getChildCount(); i++) {
            Chip chip = (Chip) binding.stepDetails.colors.getChildAt(i);
            chip.setChecked(chip.getTag() == d.color);
        }
    }

    @Nullable
    private String errorFor(GoalEditorViewModel.UiState s, GoalValidator.Error... errors) {
        for (GoalValidator.Error e : errors) if (s.errors().contains(e)) return getString(DomainText.error(e));
        return null;
    }

    private void renderSchedule(GoalEditorViewModel.UiState s) {
        GoalDraft d = s.draft();
        Context c = requireContext();
        for (Map.Entry<DayOfWeek, MaterialButton> e : dayButtons.entrySet()) {
            e.getValue().setChecked(d.schedule.contains(e.getKey()));
        }
        binding.stepSchedule.daysError.setVisibility(s.errors().contains(GoalValidator.Error.NO_ACTIVE_DAYS) ? View.VISIBLE : View.GONE);
        binding.stepSchedule.restHint.setVisibility(d.activityType.isEndurance() && d.schedule.isEveryDay() ? View.VISIBLE : View.GONE);
        binding.stepSchedule.reminderSwitch.setChecked(d.reminder.enabled());
        binding.stepSchedule.reminderTime.setVisibility(d.reminder.enabled() ? View.VISIBLE : View.GONE);
        binding.stepSchedule.reminderTime.setText(DomainText.time(c, d.reminder.time()));
        binding.stepSchedule.startDate.setText(Formats.date(c, d.startDate));
        binding.stepSchedule.startDate.setEnabled(!s.editing());
    }

    private void renderReview(GoalEditorViewModel.UiState s) {
        GoalDraft d = s.draft();
        Context c = requireContext();
        int color = GoalVisuals.color(c, d.color);
        binding.stepReview.reviewIcon.setImageResource(GoalVisuals.icon(d.icon));
        binding.stepReview.reviewIcon.setImageTintList(ColorStateList.valueOf(color));
        binding.stepReview.reviewIcon.setBackgroundTintList(ColorStateList.valueOf(GoalVisuals.container(c, d.color)));
        binding.stepReview.reviewTitle.setText(d.title.isBlank() ? getString(GoalVisuals.activityName(d.activityType)) : d.title);
        binding.stepReview.reviewActivity.setText(GoalVisuals.activityName(d.activityType));
        binding.stepReview.rowMode.value.setText(GoalVisuals.modeName(d.trackingMode));
        ProgressionPlan plan = d.plan();
        boolean planOk = plan != null && ProgressionEngine.isValid(plan);
        binding.stepReview.rowTarget.label.setText(plan != null ? R.string.editor_review_plan : R.string.editor_review_target);
        binding.stepReview.rowTarget.value.setText(plan != null
                ? (planOk ? DomainText.plan(c, plan, d.unit, d.customUnitLabel) : getString(R.string.error_final_greater))
                : Formats.amountWithUnit(c, d.consistencyTarget, d.unit, d.customUnitLabel));
        binding.stepReview.rowSchedule.value.setText(DomainText.schedule(c, d.schedule));
        binding.stepReview.rowReminder.value.setText(DomainText.reminder(c, d.reminder));
        binding.stepReview.rowStart.value.setText(Formats.date(c, d.startDate));
        binding.stepReview.rowStart.getRoot().setVisibility(s.editing() ? View.GONE : View.VISIBLE);
        binding.stepReview.historyNote.setVisibility(s.editing() ? View.VISIBLE : View.GONE);
        renderSafety(binding.stepReview.safety.getRoot(), binding.stepReview.safety.safetyReasons, s.safety());
        binding.stepReview.reviewHeader.setBackgroundColor(ColorUtils.setAlphaComponent(color, 0));
    }

    // ---- Pickers & permissions --------------------------------------------------------------

    private void pickTime() {
        LocalTime current = viewModel.state().getValue().draft().reminder.time();
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(android.text.format.DateFormat.is24HourFormat(requireContext()) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(current.getHour())
                .setMinute(current.getMinute())
                .setTitleText(R.string.editor_reminder_time)
                .build();
        picker.addOnPositiveButtonClickListener(v -> viewModel.setReminderTime(LocalTime.of(picker.getHour(), picker.getMinute())));
        picker.show(getChildFragmentManager(), "time");
    }

    private void pickDate() {
        LocalDate current = viewModel.state().getValue().draft().startDate;
        long todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(R.string.editor_start_date)
                .setSelection(current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
                .setCalendarConstraints(new CalendarConstraints.Builder()
                        .setValidator(DateValidatorPointForward.from(todayUtc - 60L * 86_400_000L)).build())
                .build();
        // The picker returns UTC midnight of the chosen calendar day.
        picker.addOnPositiveButtonClickListener(millis ->
                viewModel.setStartDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()));
        picker.show(getChildFragmentManager(), "date");
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    // ---- Helpers ----------------------------------------------------------------------------

    private interface TextConsumer {
        void accept(String text);
    }

    private TextWatcher watcher(TextConsumer consumer) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (!rendering) consumer.accept(s.toString());
            }
        };
    }

    private static void setTextIfChanged(android.widget.EditText field, String value) {
        String v = value == null ? "" : value;
        if (!field.getText().toString().equals(v)) {
            field.setText(v);
            field.setSelection(v.length());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        activityCards.clear();
        dayButtons.clear();
        chipsForActivity = null;
        chipsForUnitsOf = null;
        binding = null;
    }

    // ---- Milestone carousel -----------------------------------------------------------------

    private final class MilestoneAdapter extends RecyclerView.Adapter<MilestoneAdapter.Holder> {
        private List<MilestoneOptions.Option> options = new ArrayList<>();
        private int selected = -1;

        void submit(List<MilestoneOptions.Option> newOptions, Amount finalTarget, boolean custom) {
            int newSelected = custom ? newOptions.size() - 1 : MilestoneOptions.indexOf(newOptions, finalTarget);
            boolean changed = !newOptions.equals(options);
            int previous = selected;
            options = newOptions;
            selected = newSelected;
            if (changed) {
                notifyDataSetChanged();
            } else if (previous != selected) {
                if (previous >= 0) notifyItemChanged(previous);
                notifyItemChanged(selected);
            }
        }

        @Nullable
        MilestoneOptions.Option selectedOption() {
            return selected >= 0 && selected < options.size() ? options.get(selected) : null;
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemMilestoneOptionBinding b;

            Holder(ItemMilestoneOptionBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemMilestoneOptionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            MilestoneOptions.Option o = options.get(position);
            Context c = h.itemView.getContext();
            if (o.isCustom()) {
                h.b.value.setText(R.string.editor_final_custom);
                h.b.unit.setText(R.string.unit_km);
            } else {
                h.b.value.setText(Formats.amount(c, o.value()));
                h.b.unit.setText(R.string.unit_km);
            }
            boolean checked = position == selected;
            h.b.getRoot().setChecked(checked);
            String spoken = o.isCustom() ? c.getString(R.string.editor_final_custom)
                    : Formats.amountWithUnit(c, o.value(), GoalUnit.KILOMETERS, null);
            h.b.getRoot().setContentDescription(c.getString(R.string.milestone_picker_cd, spoken, c.getString(o.message())));
            h.b.getRoot().setOnClickListener(v -> {
                int pos = h.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                MilestoneOptions.Option chosen = options.get(pos);
                if (chosen.isCustom()) viewModel.chooseCustomFinal(); else viewModel.chooseSuggestedFinal(chosen.value());
                if (!Ui.reducedMotion(c)) binding.stepTarget.milestones.smoothScrollToPosition(pos);
            });
        }

        @Override
        public int getItemCount() {
            return options.size();
        }
    }
}
