package com.app.winterarc.ui.detail;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.databinding.FragmentGoalDetailBinding;
import com.app.winterarc.databinding.ItemStatTileBinding;
import com.app.winterarc.domain.engine.DayKind;
import com.app.winterarc.domain.engine.GoalTimeline;
import com.app.winterarc.domain.engine.StatsCalculator;
import com.app.winterarc.domain.model.ContentType;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.model.MotivationalContent;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.ui.common.ContributionGridView;
import com.app.winterarc.ui.common.DomainText;
import com.app.winterarc.ui.common.GoalVisuals;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.time.temporal.WeekFields;

import dagger.hilt.android.AndroidEntryPoint;

/** "Did you complete today's 3 km target?" plus progression, history grid, stats and inspiration. */
@AndroidEntryPoint
public class GoalDetailFragment extends Fragment implements DayDetailSheet.Host {
    private static final String TAG_LOG = "log";
    private static final String TAG_SKIP = "skip";
    private static final String TAG_DAY = "day";
    private static final String TAG_CELEBRATION = "celebration";

    private FragmentGoalDetailBinding binding;
    private GoalDetailViewModel viewModel;
    @Nullable private GoalDetailViewModel.DetailState current;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentGoalDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this).get(GoalDetailViewModel.class);
        Ui.applySystemBarPadding(binding.detailContent, true, false);
        Ui.applySystemBarPadding(binding.scroll, false, true);
        binding.toolbar.setNavigationOnClickListener(v -> Nav.up(this));
        binding.toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit) Nav.go(this, R.id.goalEditorFragment, Nav.goal(viewModel.goalId()));
            else if (id == R.id.action_stats) Nav.go(this, R.id.statsFragment, Nav.goal(viewModel.goalId()));
            else if (id == R.id.action_pause) confirmPause();
            else if (id == R.id.action_resume) viewModel.resume();
            else if (id == R.id.action_archive) confirmArchive();
            else if (id == R.id.action_restore) viewModel.restore();
            else return false;
            return true;
        });

        binding.btnComplete.setOnClickListener(v -> {
            if (current != null && current.goal().status() == GoalStatus.PAUSED) viewModel.resume();
            else viewModel.complete();
        });
        binding.btnLog.setOnClickListener(v -> showSheet(new LogProgressSheet(), TAG_LOG));
        binding.btnSkip.setOnClickListener(v -> showSheet(new SkipSheet(), TAG_SKIP));
        binding.btnNote.setOnClickListener(v -> showNoteDialog());
        binding.btnUndo.setOnClickListener(v -> viewModel.undoToday());
        binding.btnAllStats.setOnClickListener(v -> Nav.go(this, R.id.statsFragment, Nav.goal(viewModel.goalId())));
        binding.milestoneBanner.setOnClickListener(v -> viewModel.openCelebration());
        binding.grid.setOnDayClickListener(day -> showSheet(DayDetailSheet.newInstance(day.date()), TAG_DAY));

        // Size the grid to the available width once laid out.
        binding.gridContainer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) configureGrid();
        });

        viewModel.state().observe(getViewLifecycleOwner(), s -> {
            current = s;
            render(s);
            viewModel.maybeAutoCelebrate(s);
        });
        viewModel.notFound().observe(getViewLifecycleOwner(), missing -> {
            if (!Boolean.TRUE.equals(missing)) return;
            binding.loading.setVisibility(View.GONE);
            binding.scroll.setVisibility(View.GONE);
            binding.notFound.setVisibility(View.VISIBLE);
            binding.toolbar.getMenu().clear();
        });
        viewModel.messages().observe(getViewLifecycleOwner(), e -> {
            GoalDetailViewModel.Message m = e.consume();
            if (m != null) showMessage(m);
        });
        viewModel.celebrate().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() == null || getChildFragmentManager().findFragmentByTag(TAG_CELEBRATION) != null) return;
            new CelebrationDialog().show(getChildFragmentManager(), TAG_CELEBRATION);
        });
        viewModel.closeScreen().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() != null) Nav.up(this);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        configureGrid();
    }

    private void configureGrid() {
        if (binding == null) return;
        int width = binding.gridContainer.getWidth() - binding.gridContainer.getPaddingLeft()
                - binding.gridContainer.getPaddingRight();
        int weeks = width > 0 ? Math.min(53, ContributionGridView.weeksThatFit(requireContext(), width)) : 20;
        viewModel.configureGrid(weeks, WeekFields.of(Formats.locale(requireContext())).getFirstDayOfWeek());
    }

    private void showSheet(androidx.fragment.app.DialogFragment sheet, String tag) {
        if (getChildFragmentManager().findFragmentByTag(tag) != null || current == null) return;
        sheet.show(getChildFragmentManager(), tag);
    }

    // ---- Rendering --------------------------------------------------------------------------

    private void render(GoalDetailViewModel.DetailState s) {
        Context c = requireContext();
        Goal goal = s.goal();
        int color = GoalVisuals.color(c, goal.color());
        binding.loading.setVisibility(View.GONE);
        binding.scroll.setVisibility(View.VISIBLE);

        binding.icon.setImageResource(GoalVisuals.icon(goal.icon()));
        binding.icon.setImageTintList(ColorStateList.valueOf(color));
        binding.icon.setBackgroundTintList(ColorStateList.valueOf(GoalVisuals.container(c, goal.color())));
        binding.title.setText(goal.title());
        binding.subtitle.setText(getString(R.string.detail_subtitle, getString(GoalVisuals.modeName(goal.trackingMode())),
                DomainText.schedule(c, goal.schedule())));
        binding.subtitle.setCompoundDrawablesRelativeWithIntrinsicBounds(GoalVisuals.modeIcon(goal.trackingMode()), 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(binding.subtitle, ColorStateList.valueOf(color));
        binding.description.setText(goal.description());
        binding.description.setVisibility(goal.description() == null ? View.GONE : View.VISIBLE);
        binding.milestoneBanner.setVisibility(s.pendingMilestone() != null ? View.VISIBLE : View.GONE);

        renderMenu(goal);
        renderCheckIn(s, color);
        renderProgression(s, color);

        binding.grid.setData(s.heatmap(), color, ContributionGridView.localeOf(c));
        binding.legend.setGoalColor(color);
        renderStats(s);
        renderInspiration(s.content());
        binding.safetyFooter.setVisibility(goal.activityType().isEndurance() ? View.VISIBLE : View.GONE);
        binding.btnComplete.setBackgroundTintList(ColorStateList.valueOf(color));
        binding.btnComplete.setTextColor(ContextCompat.getColor(c, R.color.wa_surface));
        binding.btnComplete.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(c, R.color.wa_surface)));
    }

    private void renderMenu(Goal goal) {
        Menu menu = binding.toolbar.getMenu();
        boolean open = goal.status().isOpen();
        menu.findItem(R.id.action_edit).setVisible(open);
        menu.findItem(R.id.action_pause).setVisible(goal.status() == GoalStatus.ACTIVE);
        menu.findItem(R.id.action_resume).setVisible(goal.status() == GoalStatus.PAUSED);
        menu.findItem(R.id.action_archive).setVisible(open);
        menu.findItem(R.id.action_restore).setVisible(!open);
    }

    private void renderCheckIn(GoalDetailViewModel.DetailState s, int color) {
        Context c = requireContext();
        Goal goal = s.goal();
        ProgressEntry entry = s.todayEntry();
        String targetText = Formats.amountWithUnit(c, entry != null && entry.status() != com.app.winterarc.domain.model.EntryStatus.NOTE_ONLY
                ? entry.scheduledTarget() : goal.currentTarget(), goal.unit(), goal.customUnitLabel());
        binding.checkinTarget.setText(targetText);
        binding.checkinTarget.setTextColor(color);
        binding.checkinQuestion.setText(getString(R.string.detail_question, targetText));

        boolean complete = true, log = true, skip = true, undo = false, note = true;
        int statusTitle = 0;
        String statusBody = null;
        int statusIcon = 0;
        binding.btnComplete.setText(R.string.detail_action_complete);
        binding.btnComplete.setIconResource(R.drawable.ic_check);

        if (!goal.status().isOpen()) {
            complete = log = skip = note = false;
            statusTitle = goal.status() == GoalStatus.COMPLETED ? R.string.stats_milestone_completed : R.string.archive_title;
            statusBody = getString(R.string.detail_closed_body);
            statusIcon = R.drawable.ic_archive;
        } else if (goal.status() == GoalStatus.PAUSED) {
            log = skip = note = false;
            binding.btnComplete.setText(R.string.card_resume);
            binding.btnComplete.setIconResource(R.drawable.ic_play);
            statusTitle = R.string.detail_paused_title;
            statusBody = getString(R.string.detail_paused_body);
            statusIcon = R.drawable.ic_pause;
        } else {
            DayKind kind = s.todayKind();
            switch (kind) {
                case BEFORE_START:
                    complete = log = skip = note = false;
                    binding.checkinQuestion.setText(getString(R.string.detail_question_before_start,
                            Formats.date(c, goal.startDate())));
                    break;
                case NOT_SCHEDULED:
                    statusBody = getString(R.string.detail_question_unscheduled);
                    statusIcon = R.drawable.ic_bedtime;
                    skip = false;
                    break;
                case COMPLETED:
                case EXCEEDED:
                    complete = skip = false;
                    undo = true;
                    statusTitle = R.string.detail_done_title;
                    statusBody = getString(R.string.detail_done_body, amount(goal, entry.actualValue()), targetText);
                    statusIcon = R.drawable.ic_check;
                    break;
                case PARTIAL:
                    undo = true;
                    statusTitle = R.string.detail_partial_title;
                    statusBody = getString(R.string.detail_partial_body, amount(goal, entry.actualValue()), targetText);
                    statusIcon = R.drawable.ic_tune;
                    break;
                case SKIPPED:
                    skip = false;
                    undo = true;
                    statusTitle = R.string.detail_skipped_title;
                    statusBody = getString(R.string.detail_skipped_body);
                    statusIcon = R.drawable.ic_bedtime;
                    break;
                default:
                    break;
            }
        }
        boolean showStatus = statusBody != null;
        binding.checkinStatus.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        binding.checkinStatusTitle.setVisibility(statusTitle != 0 ? View.VISIBLE : View.GONE);
        if (statusTitle != 0) binding.checkinStatusTitle.setText(statusTitle);
        binding.checkinStatusBody.setText(statusBody);
        if (statusIcon != 0) {
            binding.checkinStatusIcon.setImageResource(statusIcon);
            binding.checkinStatusIcon.setImageTintList(ColorStateList.valueOf(color));
        }
        binding.btnComplete.setVisibility(complete ? View.VISIBLE : View.GONE);
        binding.btnLog.setVisibility(log ? View.VISIBLE : View.GONE);
        binding.btnSkip.setVisibility(skip ? View.VISIBLE : View.GONE);
        binding.btnUndo.setVisibility(undo ? View.VISIBLE : View.GONE);
        binding.btnNote.setVisibility(note ? View.VISIBLE : View.GONE);
        boolean hasNote = entry != null && entry.note() != null && !entry.note().isBlank();
        binding.btnNote.setText(hasNote ? R.string.detail_action_edit_note : R.string.detail_action_note);
        binding.checkinNote.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        if (hasNote) binding.checkinNote.setText(entry.note());
    }

    private String amount(Goal goal, com.app.winterarc.domain.model.Amount value) {
        return Formats.amountWithUnit(requireContext(), value, goal.unit(), goal.customUnitLabel());
    }

    private void renderProgression(GoalDetailViewModel.DetailState s, int color) {
        GoalDetailViewModel.ProgressionInfo p = s.progression();
        Goal goal = s.goal();
        boolean show = p != null;
        binding.progressionTitle.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.progressionCard.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        Context c = requireContext();
        boolean reached = s.pendingMilestone() != null;
        binding.progCurrentLabel.setText(reached ? R.string.detail_current_target : R.string.detail_next_target);
        binding.progCurrent.setText(amount(goal, goal.currentTarget()));
        binding.progFinal.setText(amount(goal, goal.plan().finalTarget()));
        TextViewCompat.setCompoundDrawableTintList(binding.progFinal, ColorStateList.valueOf(color));
        binding.progBars.setSteps(p.steps(), reached ? p.totalSteps() : p.level() - 1, color);
        binding.progBar.setIndicatorColor(color);
        binding.progBar.setProgressCompat(Math.round(p.fraction() * 100), !Ui.reducedMotion(c));
        binding.progBar.setContentDescription(getString(R.string.card_progress_to_final,
                Formats.percent(c, p.fraction()), amount(goal, goal.plan().finalTarget())));
        binding.progLevel.setText(getString(R.string.detail_level, p.level(), p.totalSteps()));
        binding.progRemaining.setText(reached ? getString(R.string.card_milestone_ready)
                : getResources().getQuantityString(R.plurals.detail_remaining, p.remaining(), p.remaining(),
                amount(goal, goal.plan().finalTarget())));
        binding.progCurrentGroup.setContentDescription(binding.progCurrentLabel.getText() + ": " + binding.progCurrent.getText());
        binding.progFinalGroup.setContentDescription(getString(R.string.detail_final_target) + ": " + binding.progFinal.getText());
    }

    private void renderStats(GoalDetailViewModel.DetailState s) {
        Context c = requireContext();
        StatsCalculator.GoalStats st = s.stats();
        Goal goal = s.goal();
        tile(binding.stats.statCurrent, days(st.currentStreak()), R.string.stat_current_streak);
        tile(binding.stats.statLongest, days(st.longestStreak()), R.string.stat_longest_streak);
        tile(binding.stats.statCompleted, String.valueOf(st.completedDays()), R.string.stat_completed_days);
        tile(binding.stats.statRate, Formats.percent(c, st.completionRate()), R.string.stat_completion_rate);
        tile(binding.stats.statTotal, amount(goal, st.totalValue()), R.string.stat_total);
        if (s.progression() != null) {
            tile(binding.stats.statExtra, Formats.percent(c, s.progression().fraction()), R.string.stat_toward_final);
        } else {
            tile(binding.stats.statExtra, String.valueOf(st.exceededDays()), R.string.stat_exceeded);
        }
    }

    private String days(int count) {
        return getResources().getQuantityString(R.plurals.stat_days_value, count, count);
    }

    public static void tile(ItemStatTileBinding tile, String value, @StringRes int label) {
        tile.value.setText(value);
        tile.label.setText(label);
        tile.getRoot().setContentDescription(tile.label.getText() + ": " + value);
    }

    private void renderInspiration(@Nullable MotivationalContent content) {
        boolean show = content != null;
        binding.inspirationTitle.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.inspirationCard.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        int icon = content.contentType() == ContentType.QUOTE ? R.drawable.ic_quote
                : content.contentType() == ContentType.FACT ? R.drawable.ic_science : R.drawable.ic_flame;
        binding.inspirationIcon.setImageResource(icon);
        binding.inspirationText.setText(content.message());
        boolean hasAttribution = content.attribution() != null;
        binding.inspirationAttribution.setVisibility(hasAttribution ? View.VISIBLE : View.GONE);
        if (hasAttribution) binding.inspirationAttribution.setText(getString(R.string.quote_attribution, content.attribution()));
        boolean hasSource = content.isAttributed();
        binding.inspirationSource.setVisibility(hasSource ? View.VISIBLE : View.GONE);
        if (hasSource) {
            binding.inspirationSource.setText(getString(R.string.source_label, content.sourceName()));
            binding.inspirationSource.setContentDescription(getString(R.string.source_open_cd, content.sourceName()));
            binding.inspirationSource.setOnClickListener(v -> openUrl(content.sourceUrl()));
        }
    }

    private void openUrl(String url) {
        if (url == null || !url.startsWith("https://")) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Snackbar.make(binding.detailRoot, R.string.error_generic, Snackbar.LENGTH_SHORT).show();
        }
    }

    // ---- Dialogs & messages -----------------------------------------------------------------

    private void showNoteDialog() {
        Context c = requireContext();
        TextInputLayout layout = (TextInputLayout) LayoutInflater.from(c).inflate(R.layout.view_note_input, null, false);
        TextInputEditText input = (TextInputEditText) layout.getEditText();
        ProgressEntry entry = current == null ? null : current.todayEntry();
        if (entry != null && entry.note() != null) input.setText(entry.note());
        FrameLayout container = new FrameLayout(c);
        int pad = Ui.dp(c, 24);
        container.setPadding(pad, Ui.dp(c, 8), pad, 0);
        container.addView(layout);
        new MaterialAlertDialogBuilder(c)
                .setTitle(R.string.note_title)
                .setView(container)
                .setPositiveButton(R.string.action_save, (d, w) ->
                        viewModel.saveNote(input.getText() == null ? "" : input.getText().toString()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
        input.requestFocus();
    }

    private void confirmPause() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.menu_pause)
                .setMessage(R.string.pause_confirm_body)
                .setPositiveButton(R.string.menu_pause, (d, w) -> viewModel.pause())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmArchive() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.archive_confirm_title)
                .setMessage(R.string.archive_confirm_body)
                .setPositiveButton(R.string.menu_archive_goal, (d, w) -> viewModel.archive())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showMessage(GoalDetailViewModel.Message m) {
        String target = current == null ? "" : amount(current.goal(), current.goal().currentTarget());
        String text;
        boolean offerUndo = false;
        switch (m) {
            case COMPLETED:
                Ui.confirmHaptic(binding.getRoot(), viewModel.hapticsEnabled());
                text = getString(R.string.detail_completed_snack, current == null ? "" : current.goal().title());
                if (current != null && current.goal().isProgression()) {
                    text = text + " " + getString(R.string.detail_next_up, target);
                }
                offerUndo = true;
                break;
            case PARTIAL: text = getString(R.string.detail_partial_snack); offerUndo = true; break;
            case SKIPPED: text = getString(R.string.detail_skipped_snack); offerUndo = true; break;
            case UNDONE: text = getString(R.string.detail_undone_snack); break;
            case NOTE_SAVED: text = getString(R.string.detail_note_saved); break;
            case PAUSED: text = getString(R.string.detail_paused_body); break;
            case RESUMED: text = getString(R.string.detail_resumed_snack); break;
            case RESTORED: text = getString(R.string.archive_restored); break;
            case MAINTAINED: text = getString(R.string.celebration_maintained_snack, target); break;
            case CONTINUED:
                text = getString(R.string.celebration_continued_snack,
                        current == null || viewModel.lastContinuedFinal() == null
                                ? "" : amount(current.goal(), viewModel.lastContinuedFinal()));
                break;
            case GOAL_COMPLETED: text = getString(R.string.celebration_completed_snack); break;
            default: text = getString(R.string.error_generic); break;
        }
        Snackbar bar = Snackbar.make(binding.detailRoot, text, Snackbar.LENGTH_LONG);
        if (offerUndo) bar.setAction(R.string.action_undo, v -> viewModel.undoToday());
        bar.show();
    }

    // ---- DayDetailSheet.Host ----------------------------------------------------------------

    @Nullable
    @Override
    public Goal hostGoal() {
        return current == null ? null : current.goal();
    }

    @Nullable
    @Override
    public GoalTimeline hostTimeline() {
        return current == null ? null : current.timeline();
    }

    @Override
    public LocalDate hostToday() {
        return current == null ? LocalDate.now() : current.today();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
