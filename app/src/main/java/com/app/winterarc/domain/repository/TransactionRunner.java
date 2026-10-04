package com.app.winterarc.domain.repository;

import androidx.annotation.WorkerThread;

import java.util.function.Supplier;

/** Runs a block atomically. The Room implementation wraps a database transaction. */
public interface TransactionRunner {
    @WorkerThread
    <T> T run(Supplier<T> block);
}
