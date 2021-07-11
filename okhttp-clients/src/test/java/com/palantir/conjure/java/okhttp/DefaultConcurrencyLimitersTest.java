/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.conjure.java.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.AIMDLimit;
import com.palantir.conjure.java.okhttp.ConcurrencyLimiters.ConcurrencyLimiter;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public final class DefaultConcurrencyLimitersTest {
    private static final ConcurrencyLimiters.Key KEY =
            ImmutableKey.builder().hostname("").build();
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private final ConcurrencyLimiters limiters = new ConcurrencyLimiters(
            Executors.newSingleThreadScheduledExecutor(
                    new ThreadFactoryBuilder().setNameFormat("listener-reviver").build()),
            new DefaultTaggedMetricRegistry(),
            TIMEOUT,
            DefaultConcurrencyLimitersTest.class,
            true);

    @Test
    public void testTimeout() {
        Instant start = Instant.now();
        Thread exhauster = exhaust();
        Futures.getUnchecked(limiters.acquireLimiterInternal(KEY).acquire());
        Instant end = Instant.now();
        exhauster.interrupt();
        assertThat(Duration.between(start, end)).isGreaterThanOrEqualTo(TIMEOUT);
    }

    @Test
    public void testAimdLimiterDoesNotApplyTimeBasedLimits() {
        AIMDLimit limit = AIMDLimit.newBuilder()
                .timeout(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
                .build();
        int initialLimit = limit.getLimit();
        limit.onSample(0, Long.MAX_VALUE, 0, false);
        int resultLimit = limit.getLimit();
        assertThat(resultLimit).isEqualTo(initialLimit);
    }

    @Test
    public void testConcurrencyLimitersLimitDoesNotApplyTimeBasedLimits() {
        Limit limit = limiters.newLimit();

        int initialLimit = limit.getLimit();
        for (int i = 0; i < 15; i++) {
            limit.onSample(TimeUnit.SECONDS.toNanos(i), TimeUnit.SECONDS.toNanos(300), 0, false);
            int resultLimit = limit.getLimit();
            assertThat(resultLimit).isEqualTo(initialLimit);
        }
    }

    @Test
    public void testConcurrencyLimitersFuturesCanBeCancelled() {
        List<Limiter.Listener> acquired = new ArrayList<>();
        List<ListenableFuture<Limiter.Listener>> waitingFutures = new ArrayList<>();
        ConcurrencyLimiter limiter = limiters.acquireLimiterInternal(KEY);
        while (waitingFutures.size() < 2) {
            ListenableFuture<Limiter.Listener> listener = limiter.acquire();
            if (listener.isDone()) {
                acquired.add(Futures.getUnchecked(listener));
            } else {
                waitingFutures.add(listener);
            }
        }
        waitingFutures.get(0).cancel(true);
        ListenableFuture<Limiter.Listener> toBeCompleted = waitingFutures.get(1);
        assertThat(toBeCompleted).isNotDone();
        acquired.get(0).onIgnore();
        assertThat(toBeCompleted).isDone();
    }

    private Thread exhaust() {
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    limiters.acquireLimiterInternal(KEY).acquire().get();
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        thread.start();
        // wait until the other thread blocks
        while (!thread.getState().equals(Thread.State.WAITING)) {
            Thread.yield();
        }
        return thread;
    }
}
