package com.app.winterarc.ui.archive;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.app.winterarc.R;
import com.app.winterarc.core.format.Formats;
import com.app.winterarc.databinding.FragmentArchiveBinding;
import com.app.winterarc.databinding.ItemArchivedGoalBinding;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.ui.common.GoalVisuals;
import com.app.winterarc.ui.common.Nav;
import com.app.winterarc.ui.common.Ui;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import dagger.hilt.android.AndroidEntryPoint;

/** Completed and archived goals, with all history preserved. Restore or delete permanently. */
@AndroidEntryPoint
public class ArchiveFragment extends Fragment {
    private FragmentArchiveBinding binding;
    private ArchiveViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = FragmentArchiveBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this).get(ArchiveViewModel.class);
        Ui.applySystemBarPadding(binding.archiveContent, true, false);
        Ui.applySystemBarPadding(binding.list, false, true);
        binding.toolbar.setNavigationOnClickListener(v -> Nav.up(this));
        Adapter adapter = new Adapter();
        binding.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.list.setAdapter(adapter);
        viewModel.goals().observe(getViewLifecycleOwner(), goals -> {
            binding.empty.setVisibility(goals.isEmpty() ? View.VISIBLE : View.GONE);
            adapter.submitList(goals);
        });
        viewModel.restored().observe(getViewLifecycleOwner(), e -> {
            if (e.consume() != null) Snackbar.make(binding.archiveRoot, R.string.archive_restored, Snackbar.LENGTH_SHORT).show();
        });
    }

    private void confirmDelete(Goal goal) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.archive_delete_title)
                .setMessage(getString(R.string.archive_delete_body, goal.title()))
                .setPositiveButton(R.string.action_delete, (d, w) -> viewModel.delete(goal.id()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private static final DiffUtil.ItemCallback<Goal> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Goal a, @NonNull Goal b) {
            return a.id() == b.id();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Goal a, @NonNull Goal b) {
            return a.equals(b);
        }
    };

    private final class Adapter extends ListAdapter<Goal, Adapter.Holder> {
        Adapter() {
            super(DIFF);
        }

        final class Holder extends RecyclerView.ViewHolder {
            final ItemArchivedGoalBinding b;

            Holder(ItemArchivedGoalBinding b) {
                super(b.getRoot());
                this.b = b;
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemArchivedGoalBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Goal goal = getItem(position);
            Context c = h.itemView.getContext();
            int color = GoalVisuals.color(c, goal.color());
            h.b.icon.setImageResource(GoalVisuals.icon(goal.icon()));
            h.b.icon.setImageTintList(ColorStateList.valueOf(color));
            h.b.icon.setBackgroundTintList(ColorStateList.valueOf(GoalVisuals.container(c, goal.color())));
            h.b.title.setText(goal.title());
            String date = goal.endDate() == null ? "" : Formats.date(c, goal.endDate());
            h.b.status.setText(c.getString(goal.status() == GoalStatus.COMPLETED
                    ? R.string.archive_status_completed : R.string.archive_status_archived, date));
            h.b.card.setOnClickListener(v -> Nav.go(ArchiveFragment.this, R.id.goalDetailFragment, Nav.goal(goal.id())));
            h.b.restore.setOnClickListener(v -> viewModel.restore(goal.id()));
            h.b.delete.setOnClickListener(v -> confirmDelete(goal));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
