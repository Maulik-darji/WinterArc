package com.app.winterarc.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.databinding.FragmentHomeBinding;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.ui.common.ContributionGridView;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.PreviewData;
import com.app.winterarc.ui.common.Ui;
import com.google.android.material.snackbar.Snackbar;

import java.time.LocalDate;
import java.time.temporal.WeekFields;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HomeFragment extends Fragment implements HomeAdapter.Callbacks {
    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private HomeAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        Ui.applySystemBarPadding(binding.content, true, false);

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_archive) {
                Nav.go(this, R.id.archiveFragment);
                return true;
            }
            if (item.getItemId() == R.id.action_settings) {
                Nav.go(this, R.id.settingsFragment);
                return true;
            }
            return false;
        });

        adapter = new HomeAdapter(this);
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        binding.list.setItemAnimator(Ui.reducedMotion(requireContext()) ? null : binding.list.getItemAnimator());
        binding.emptyCta.setOnClickListener(v -> Nav.go(this, R.id.goalEditorFragment));
        binding.emptyGrid.setCompact(true);
        binding.emptyGrid.setData(PreviewData.heatmap(LocalDate.now(), 17, firstDay(), 9),
                ContextCompat.getColor(requireContext(), R.color.wa_primary), ContributionGridView.localeOf(requireContext()));

        viewModel.state().observe(getViewLifecycleOwner(), s -> {
            binding.loading.setVisibility(View.GONE);
            boolean empty = s.cards().isEmpty();
            binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
            binding.list.setVisibility(empty ? View.GONE : View.VISIBLE);
            binding.emptyGreeting.setText(HomeAdapter.greeting(requireContext(), s.greeting()));
            binding.emptyDate.setText(Formats.longDate(requireContext(), s.today()));
            adapter.submitList(HomeAdapter.rows(s));
        });
        viewModel.messages().observe(getViewLifecycleOwner(), event -> {
            HomeViewModel.Message m = event.consume();
            if (m == null) return;
            if (m.kind() == HomeViewModel.Message.ERROR) {
                Snackbar.make(binding.homeRoot, R.string.error_generic, Snackbar.LENGTH_LONG).show();
                return;
            }
            Ui.confirmHaptic(binding.homeRoot, viewModel.hapticsEnabled());
            if (m.kind() == HomeViewModel.Message.MILESTONE) {
                // The celebration lives on the goal screen.
                Nav.go(this, R.id.goalDetailFragment, Nav.goal(m.undoGoalId()));
                return;
            }
            Snackbar.make(binding.homeRoot, getString(R.string.detail_completed_snack, m.goalTitle()), Snackbar.LENGTH_LONG)
                    .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                    .setAction(R.string.action_undo, v -> viewModel.undo(m.undoGoalId()))
                    .show();
        });
    }

    private java.time.DayOfWeek firstDay() {
        return WeekFields.of(Formats.locale(requireContext())).getFirstDayOfWeek();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refresh(firstDay());
    }

    @Override
    public void onOpen(Goal goal) {
        Nav.go(this, R.id.goalDetailFragment, Nav.goal(goal.id()));
    }

    @Override
    public void onComplete(Goal goal) {
        viewModel.complete(goal);
    }

    @Override
    public void onResume(Goal goal) {
        viewModel.resume(goal.id());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
