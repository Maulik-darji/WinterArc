package com.app.winterarc.ui.onboarding;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.app.winterarc.R;
import com.app.winterarc.databinding.FragmentOnboardingBinding;
import com.app.winterarc.databinding.ItemOnboardingPageBinding;
import com.app.winterarc.databinding.ViewModeExampleBinding;
import com.app.winterarc.domain.model.GoalColor;
import com.app.winterarc.domain.model.GoalIcon;
import com.app.winterarc.ui.common.ContributionGridView;
import com.app.winterarc.ui.common.GoalVisuals;
import com.app.winterarc.ui.common.PreviewData;
import com.app.winterarc.ui.common.Ui;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.card.MaterialCardView;
import android.widget.ImageView;

import java.time.LocalDate;
import java.time.temporal.WeekFields;

import dagger.hilt.android.AndroidEntryPoint;

/** Three-page introduction to the value proposition. Skippable; shown only on first launch. */
@AndroidEntryPoint
public class OnboardingFragment extends Fragment {
    @javax.inject.Inject com.app.winterarc.domain.auth.AuthRepository auth;

    private static final int[] TITLES = {R.string.onboarding_1_title, R.string.onboarding_2_title, R.string.onboarding_3_title};
    private static final int[] BODIES = {R.string.onboarding_1_body, R.string.onboarding_2_body, R.string.onboarding_3_body};

    private FragmentOnboardingBinding binding;
    private OnboardingViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentOnboardingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this).get(OnboardingViewModel.class);
        Ui.applySystemBarPadding(binding.onboardingRoot, true, true);
        binding.pager.setAdapter(new PagesAdapter());
        new TabLayoutMediator(binding.dots, binding.pager, (tab, position) -> { }).attach();
        binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                boolean last = position == TITLES.length - 1;
                binding.next.setText(last ? R.string.onboarding_get_started : R.string.action_next);
                binding.skip.setVisibility(last ? View.INVISIBLE : View.VISIBLE);
                binding.pager.setContentDescription(getString(R.string.onboarding_page_indicator, position + 1, TITLES.length));
            }
        });
        binding.skip.setOnClickListener(v -> finish());
        binding.next.setOnClickListener(v -> {
            int page = binding.pager.getCurrentItem();
            if (page < TITLES.length - 1) {
                binding.pager.setCurrentItem(page + 1, !Ui.reducedMotion(requireContext()));
            } else {
                finish();
            }
        });
    }

    private void finish() {
        viewModel.complete();
        NavHostFragment.findNavController(this).navigate(auth.getCurrentUser() == null
                ? R.id.action_onboarding_to_signup : R.id.action_onboarding_to_home);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private final class PagesAdapter extends RecyclerView.Adapter<PagesAdapter.Holder> {
        final class Holder extends RecyclerView.ViewHolder {
            final ItemOnboardingPageBinding b;

            Holder(ItemOnboardingPageBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemOnboardingPageBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.b.title.setText(TITLES[position]);
            holder.b.body.setText(BODIES[position]);
            holder.b.illustration.removeAllViews();
            holder.b.illustration.addView(illustration(holder.b.illustration, position));
        }

        @Override
        public int getItemCount() {
            return TITLES.length;
        }
    }

    private View illustration(ViewGroup parent, int page) {
        android.content.Context c = parent.getContext();
        FrameLayout.LayoutParams center = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER);
        if (page == 0) {
            // A small constellation of goal icons.
            LinearLayout grid = new LinearLayout(c);
            grid.setOrientation(LinearLayout.VERTICAL);
            GoalIcon[][] rows = {{GoalIcon.WALK, GoalIcon.RUN, GoalIcon.BOOK}, {GoalIcon.MINDFUL, GoalIcon.CODE, GoalIcon.BIKE}};
            GoalColor[] colors = GoalColor.values();
            int k = 0;
            for (GoalIcon[] row : rows) {
                LinearLayout line = new LinearLayout(c);
                line.setGravity(android.view.Gravity.CENTER);
                for (GoalIcon icon : row) {
                    GoalColor color = colors[k++ % colors.length];
                    MaterialCardView card = new MaterialCardView(c);
                    card.setCardBackgroundColor(GoalVisuals.container(c, color));
                    card.setStrokeWidth(0);
                    card.setRadius(Ui.dp(c, 24));
                    ImageView image = new ImageView(c);
                    image.setImageResource(GoalVisuals.icon(icon));
                    image.setImageTintList(android.content.res.ColorStateList.valueOf(GoalVisuals.color(c, color)));
                    int pad = Ui.dp(c, 22);
                    image.setPadding(pad, pad, pad, pad);
                    card.addView(image, Ui.dp(c, 80), Ui.dp(c, 80));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.setMargins(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
                    line.addView(card, lp);
                }
                grid.addView(line);
            }
            grid.setLayoutParams(center);
            return grid;
        }
        if (page == 1) {
            ViewModeExampleBinding b = ViewModeExampleBinding.inflate(LayoutInflater.from(c), parent, false);
            b.consistencyBars.setFlat(7, 7, ContextCompat.getColor(c, R.color.wa_primary));
            b.progressionBars.setRising(10, 6, ContextCompat.getColor(c, R.color.wa_tertiary));
            b.getRoot().setLayoutParams(center);
            return b.getRoot();
        }
        ContributionGridView grid = new ContributionGridView(c);
        grid.setCompact(true);
        grid.setData(PreviewData.heatmap(LocalDate.now(), 18,
                        WeekFields.of(ContributionGridView.localeOf(c)).getFirstDayOfWeek(), 21),
                ContextCompat.getColor(c, R.color.wa_primary), ContributionGridView.localeOf(c));
        grid.setLayoutParams(center);
        return grid;
    }
}
