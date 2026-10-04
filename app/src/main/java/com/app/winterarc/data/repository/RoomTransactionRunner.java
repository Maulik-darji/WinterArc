package com.app.winterarc.data.repository;

import com.app.winterarc.data.db.WinterArcDatabase;
import com.app.winterarc.domain.repository.TransactionRunner;

import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RoomTransactionRunner implements TransactionRunner {
    private final WinterArcDatabase db;

    @Inject
    public RoomTransactionRunner(WinterArcDatabase db) {
        this.db = db;
    }

    @Override
    public <T> T run(Supplier<T> block) {
        return db.runInTransaction(block::get);
    }
}
