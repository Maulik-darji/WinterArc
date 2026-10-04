package com.app.winterarc.ui.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.databinding.ItemGoalCardBinding;
import com.app.winterarc.databinding.ItemHomeHeaderBinding;
import com.app.winterarc.databinding.ItemSectionBinding;
import com.app.winterarc.domain.engine.DayKind;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.ProgressEntry;
import com.app.winterarc.ui.common.ContributionGridView;
import com.app.winterarc.ui.common.GoalVisuals;

import java.util.Objects;

/** Header, section titles and goal cards for the dashboard. */
public class HomeAdapter extends ListAdapter<HomeAdapter.Item, RecyclerView.ViewHolder> {

    public interface Callbacks {
        void onOpen(Goal goal);

        void onComplete(Goal goal);

        void onResume(Goal goal);
    }

    /** A row: exactly one of header, section or card is set. */
    public record Item(int type, HomeViewModel.HomeState header, HomeViewModel.Section section,
                       HomeViewModel.GoalCard card) {
        static final int HEADER = 0;
        static final int SECTION = 1;
        static final int CARD = 2;

        static Item header(HomeViewModel.HomeState s) { return new Item(HEADER, s, null, null); }
        static Item section(HomeViewModel.Section s) { return new Item(SECTION, null, s, null); }
        static Item card(HomeViewModel.GoalCard c) { return new Item(CARD, null, null, c); }

        long stableId() {
            if (type == HEADER) return -1;
            if (type == SECTION) return -10 - section.ordinal();
            return card.goal().id();
        }
    }

    private static final DiffUtil.ItemCallback<Item> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Item a, @NonNull Item b) {
            return a.type() == b.type() && a.stableId() == b.stableId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Item a, @NonNull Item b) {
            return Objects.equals(a, b);
        }
    };

    private final Callbacks callbacks;

    public HomeAdapter(Callbacks callbacks) {
        super(DIFF);
        this.callbacks = callbacks;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).type();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == Item.HEADER) return new HeaderHolder(ItemHomeHeaderBinding.inflate(inflater, parent, false));
        if (viewType == Item.SECTION) return new SectionHolder(ItemSectionBinding.inflate(inflater, parent, false));
        return new CardHolder(ItemGoalCardBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Item item = getItem(position);
        if (holder instanceof HeaderHolder) ((HeaderHolder) holder).bind(item.header());
        else if (holder instanceof SectionHolder) ((SectionHolder) holder).bind(item.section());
        else ((CardHolder) holder).bind(item.card());
    }

    static String greeting(Context c, HomeViewModel.Greeting g) {
        switch (g) {
            case MORNING: return c.getString(R.string.home_greeting_morning);
            case AFTERNOON: return c.getString(R.string.home_greeting_afternoon);
            default: return c.getString(R.string.home_greeting_evening);
        }
    }

    static final class HeaderHolder extends RecyclerView.ViewHolder {
        private final ItemHomeHeaderBinding b;

        HeaderHolder(ItemHomeHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(HomeViewModel.HomeState s) {
            Context c = itemView.getContext();
            b.greeting.setText(greeting(c, s.greeting()));
            b.date.setText(Formats.longDate(c, s.today()));
            String summary;
            if (s.scheduledToday() == 0) {
                summary = c.getString(R.string.home_summary_none_today);
            } else if (s.remainingToday() == 0) {
                summary = c.getString(R.string.home_summary_all_done);
            } else {
                summary = c.getResources().getQuantityString(R.plurals.home_summary_remaining, s.remainingToday(), s.remainingToday());
            }
            b.summary.setText(summary);
        }
    }

    static final class SectionHolder extends RecyclerView.ViewHolder {
        private final ItemSectionBinding b;

        SectionHolder(ItemSectionBinding b) {
            super(b.getRoot());
            this.b = b;
        }

        void bind(HomeViewModel.Section section) {
            int res = section == HomeViewModel.Section.TODAY ? R.string.home_section_today
                    : section == HomeViewModel.Section.REST ? R.string.home_section_rest : R.string.home_section_paused;
            b.sectionTitle.setText(res);
        }
    }

    final class CardHolder extends RecyclerView.ViewHolder {
        private final ItemGoalCardBinding b;

        CardHolder(ItemGoalCardBinding b) {
            super(b.getRoot());
            this.b = b;
            b.grid.setCompact(true);
        }

        void bind(HomeViewModel.GoalCard card) {
            Context c = itemView.getContext();
            Goal goal = card.goal();
            int color = GoalVisuals.color(c, goal.color());
            b.icon.setImageResource(GoalVisuals.icon(goal.icon()));
            b.icon.setImageTintList(ColorStateList.valueOf(color));
            b.icon.setBackgroundTintList(ColorStateList.valueOf(GoalVisuals.container(c, goal.color())));
            b.title.setText(goal.title());

            String target = Formats.amountWithUnit(c, goal.currentTarget(), goal.unit(), goal.customUnitLabel());
            boolean today = card.section() == HomeViewModel.Section.TODAY;
            b.target.setText(c.getString(today ? R.string.card_target_today : R.string.card_target_next, target));
            b.target.setCompoundDrawablesRelativeWithIntrinsicBounds(GoalVisuals.modeIcon(goal.trackingMode()), 0, 0, 0);
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(b.target, ColorStateList.valueOf(color));

            bindAction(card, c, color);

            b.grid.setData(card.preview(), color, ContributionGridView.localeOf(c));
            b.grid.setContentDescription(ContributionGridView.summary(c, goal.title(), card.preview()));

            b.streak.setText(c.getResources().getQuantityString(R.plurals.streak_days, card.currentStreak(), card.currentStreak()));
            if (goal.isProgression()) {
                String fin = Formats.amountWithUnit(c, goal.plan().finalTarget(), goal.unit(), goal.customUnitLabel());
                b.progressText.setText(c.getString(R.string.card_progress_to_final, Formats.percent(c, card.progressionFraction()), fin));
                b.progressText.setVisibility(View.VISIBLE);
                b.progress.setVisibility(View.VISIBLE);
                b.progress.setIndicatorColor(color);
                b.progress.setProgressCompat(Math.round(card.progressionFraction() * 100), false);
            } else {
                b.progressText.setVisibility(View.GONE);
                b.progress.setVisibility(View.GONE);
            }
            b.banner.setVisibility(card.milestonePending() ? View.VISIBLE : View.GONE);
            b.banner.setText(R.string.card_milestone_ready);

            b.card.setOnClickListener(v -> callbacks.onOpen(goal));
            String status = c.getString(GoalVisuals.dayKind(card.todayKind()));
            b.card.setContentDescription(c.getString(R.string.card_cd_open, goal.title(), b.target.getText(), status));
        }

        private void bindAction(HomeViewModel.GoalCard card, Context c, int color) {
            Goal goal = card.goal();
            b.action.setOnClickListener(null);
            b.action.setVisibility(View.VISIBLE);
            b.action.setEnabled(true);
            b.action.setIconTint(null);
            if (card.section() == HomeViewModel.Section.PAUSED) {
                b.action.setText(R.string.card_resume);
                b.action.setIconResource(R.drawable.ic_play);
                b.action.setOnClickListener(v -> callbacks.onResume(goal));
                return;
            }
            DayKind kind = card.todayKind();
            if (kind.isCompletion()) {
                b.action.setText(R.string.card_completed);
                b.action.setIconResource(R.drawable.ic_check);
                b.action.setIconTint(ColorStateList.valueOf(color));
                b.action.setEnabled(false);
            } else if (kind == DayKind.SKIPPED) {
                b.action.setText(R.string.card_skipped);
                b.action.setIconResource(R.drawable.ic_bedtime);
                b.action.setEnabled(false);
            } else if (card.section() == HomeViewModel.Section.TODAY) {
                ProgressEntry entry = card.todayEntry();
                b.action.setText(kind == DayKind.PARTIAL && entry != null
                        ? c.getString(R.string.card_partial, Formats.amount(c, entry.actualValue()))
                        : c.getString(R.string.card_complete));
                b.action.setIconResource(R.drawable.ic_check);
                b.action.setContentDescription(c.getString(R.string.card_cd_complete,
                        Formats.amountWithUnit(c, goal.currentTarget(), goal.unit(), goal.customUnitLabel()), goal.title()));
                b.action.setOnClickListener(v -> callbacks.onComplete(goal));
                return;
            } else {
                b.action.setVisibility(View.GONE);
            }
            b.action.setContentDescription(null);
        }
    }

    /** Builds the flat row list from state. */
    static java.util.List<Item> rows(HomeViewModel.HomeState s) {
        java.util.List<Item> rows = new java.util.ArrayList<>();
        rows.add(Item.header(s));
        HomeViewModel.Section current = null;
        for (HomeViewModel.GoalCard card : s.cards()) {
            if (card.section() != current) {
                current = card.section();
                rows.add(Item.section(current));
            }
            rows.add(Item.card(card));
        }
        return rows;
    }
}
