/*-
 * ========================LICENSE_START=================================
 * Bucket4j
 * %%
 * Copyright (C) 2015 - 2022 Vladimir Bukhtoyarov
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package io.github.bucket4j.distributed.proxy;

import io.github.bucket4j.BucketExceptions;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Defines the strategy for request execution.
 */
public interface ExecutionStrategy {

    <T> T execute(Supplier<T> supplier);

    <T> CompletableFuture<T>  executeAsync(Supplier<CompletableFuture<T>> supplier);

    /**
     * This execution strategy always communicates with backend in current thread
     */
    ExecutionStrategy SAME_TREAD = new ExecutionStrategy() {
        @Override
        public <T> T execute(Supplier<T> supplier) {
            return supplier.get();
        }

        @Override
        public <T> CompletableFuture<T> executeAsync(Supplier<CompletableFuture<T>> supplier) {
            return supplier.get();
        }
    };

    /**
     * This execution strategy always communicates with backend in specified executor
     */
    static ExecutionStrategy background(Executor executor) {
        return new ExecutionStrategy() {
            @Override
            public <T> T execute(Supplier<T> supplier) {
                try {
                    return CompletableFuture.supplyAsync(supplier, executor).get();
                } catch (Throwable e) {
                    throw BucketExceptions.executionException(e);
                }
            }

            @Override
            public <T> CompletableFuture<T> executeAsync(Supplier<CompletableFuture<T>> supplier) {
                CompletableFuture<CompletableFuture<T>> futureToFuture = CompletableFuture.supplyAsync(supplier, executor);

                CompletableFuture<T> resultFuture = new CompletableFuture<>();
                futureToFuture.whenComplete((CompletableFuture<T> mediateFuture, Throwable error) -> {
                    if (error != null) {
                        resultFuture.completeExceptionally(error);
                    } else {
                        mediateFuture.whenComplete((T result, Throwable err) -> {
                            if (err != null) {
                                resultFuture.completeExceptionally(err);
                            } else {
                                resultFuture.complete(result);
                            }
                        });
                    }
                });

                return resultFuture;
            }
        };
    }

    /**
     * This execution strategy always communicates with backend in specified executor
     */
    static ExecutionStrategy backgroundTimeBounded(Executor executor, Duration timeout) {
        long timeoutNanos = timeout.toNanos();
        if (timeoutNanos <= 0) {
            throw new IllegalStateException("timeout should be positive");
        }
        return new ExecutionStrategy() {
            @Override
            public <T> T execute(Supplier<T> supplier) {
                try {
                    return CompletableFuture.supplyAsync(supplier, executor)
                            .get(timeoutNanos, TimeUnit.NANOSECONDS);
                } catch (Throwable e) {
                    throw BucketExceptions.executionException(e);
                }
            }

            @Override
            public <T> CompletableFuture<T> executeAsync(Supplier<CompletableFuture<T>> supplier) {
                CompletableFuture<CompletableFuture<T>> futureToFuture = CompletableFuture.supplyAsync(supplier, executor);

                CompletableFuture<T> resultFuture = new CompletableFuture<>();
                futureToFuture.whenComplete((CompletableFuture<T> mediateFuture, Throwable error) -> {
                    if (error != null) {
                        resultFuture.completeExceptionally(error);
                    } else {
                        mediateFuture.whenComplete((T result, Throwable err) -> {
                            if (err != null) {
                                resultFuture.completeExceptionally(err);
                            } else {
                                resultFuture.complete(result);
                            }
                        });
                    }
                });

                return resultFuture.orTimeout(timeoutNanos, TimeUnit.NANOSECONDS);
            }
        };
    }

}
