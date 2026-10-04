package com.app.winterarc.ui.archive;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.app.winterarc.core.AppExecutors;
import com.app.winterarc.domain.model.Goal;
import com.app.winterarc.domain.model.GoalStatus;
import com.app.winterarc.domain.repository.GoalRepository;
import com.app.winterarc.domain.usecase.GoalUseCases;
import com.app.winterarc.ui.common.Event;

import java.util.EnumSet;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ArchiveViewModel extends ViewModel {
    private final LiveData<List<Goal>> goals;
    private final GoalUseCases useCases;
    private final AppExecutors executors;
    private final MutableLiveData<Event<Boolean>> restored = new MutableLiveData<>();

    @Inject
    public ArchiveViewModel(GoalRepository repository, GoalUseCases useCases, AppExecutors executors) {
        this.goals = repository.observeGoals(EnumSet.of(GoalStatus.COMPLETED, GoalStatus.ARCHIVED));
        this.useCases = useCases;
        this.executors = executors;
    }

    public LiveData<List<Goal>> goals() {
        return goals;
    }

    public LiveData<Event<Boolean>> restored() {
        return restored;
    }

    public void restore(long goalId) {
        executors.io().execute(() -> {
            useCases.restore(goalId);
            executors.main().execute(() -> restored.setValue(new Event<>(true)));
        });
    }

    public void delete(long goalId) {
        executors.io().execute(() -> useCases.delete(goalId));
    }
}
