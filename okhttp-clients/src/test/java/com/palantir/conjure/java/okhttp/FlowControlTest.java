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

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.RateLimiter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.concurrency.limits.Limiter;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is a simulation of the flow control primitives used by this library, in order to allow the developer to
 * try different strategies.
 *
 * <p>It is run in CI, but only to prevent code breakages - this is in general an expensive test which should be run as
 * a dev tool. If you want to run for dev purposes, please increase REQUESTS_PER_THREAD.
 */
public final class FlowControlTest {
    private static final Logger log = LoggerFactory.getLogger(FlowControlTest.class);
    private static final ConcurrencyLimiters.Key KEY =
            ImmutableKey.builder().hostname("").build();
    private static final Duration GRACE = Duration.ofMinutes(2);
    private static final int REQUESTS_PER_THREAD = 50;
    private static ListeningExecutorService executorService;

    private final ConcurrencyLimiters limiters = new ConcurrencyLimiters(
            Executors.newSingleThreadScheduledExecutor(
                    new ThreadFactoryBuilder().setNameFormat("listener-reviver").build()),
            new DefaultTaggedMetricRegistry(),
            FlowControlTest.class,
            true);

    @BeforeAll
    public static void beforeClass() {
        executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    }

    @AfterAll
    public static void afterClass() {
        executorService.shutdown();
    }

    @Test
    public void test16ThreadsRateLimit20() throws ExecutionException, InterruptedException {
        Meter rate = new Meter();
        Histogram avgRetries = new Histogram(new ExponentiallyDecayingReservoir());
        int rateLimit = 20;
        List<ListenableFuture<?>> tasks = createWorkers(rate, avgRetries, 16, rateLimit, Duration.ofMillis(50))
                .map(executorService::submit)
                .collect(Collectors.toList());
        ListenableFuture<?> task = Futures.allAsList(tasks);
        Instant start = Instant.now();
        while (!task.isDone()) {
            sleep(1000);
            log.info("Average rate is {}, 1 minute rate is {}", rate.getMeanRate(), rate.getOneMinuteRate());
            log.info(
                    "Average number of retries is {}, max is {}",
                    avgRetries.getSnapshot().getMean(),
                    avgRetries.getSnapshot().getMax());
            if (Duration.between(start, Instant.now()).compareTo(GRACE) > 0) {
                assertThat(rate.getMeanRate()).isGreaterThan(0.75 * rateLimit);
            }
        }
        task.get();
    }

    private Stream<Worker> createWorkers(
            Meter rate, Histogram avgRetries, int numThreads, int rateLimit, Duration delay) {
        RateLimiter rateLimiter = RateLimiter.create(rateLimit);
        return IntStream.range(0, numThreads)
                .mapToObj(_unused -> new Worker(
                        () -> new ExponentialBackoff(4, Duration.ofMillis(250)),
                        limiters,
                        delay,
                        rateLimiter,
                        rate,
                        avgRetries));
    }

    private static final class Worker implements Runnable {
        private final Supplier<BackoffStrategy> backoffFactory;
        private final ConcurrencyLimiters limiters;
        private final Duration successDuration;
        private final RateLimiter rateLimiter;
        private final Meter meter;
        private final Histogram avgRetries;

        private BackoffStrategy backoff;
        private int numRetries = 0;

        private Worker(
                Supplier<BackoffStrategy> backoffFactory,
                ConcurrencyLimiters limiters,
                Duration successDuration,
                RateLimiter rateLimiter,
                Meter meter,
                Histogram avgRetries) {
            this.backoffFactory = backoffFactory;
            this.limiters = limiters;
            this.successDuration = successDuration;
            this.rateLimiter = rateLimiter;
            this.meter = meter;
            this.avgRetries = avgRetries;
        }

        @Override
        public void run() {
            for (int i = 0; i < REQUESTS_PER_THREAD; ) {
                Limiter.Listener listener = Futures.getUnchecked(
                        limiters.acquireLimiterInternal(KEY).acquire());
                boolean gotRateLimited = !rateLimiter.tryAcquire(Duration.ofMillis(100));
                if (!gotRateLimited) {
                    meter.mark();
                    sleep(successDuration.toMillis());
                    listener.onSuccess();
                    avgRetries.update(numRetries);
                    numRetries = 0;
                    backoff = null;
                    i++;
                } else {
                    initializeBackoff();
                    Optional<Duration> sleep = backoff.nextBackoff();
                    numRetries++;
                    if (!sleep.isPresent()) {
                        listener.onIgnore();
                        throw new RuntimeException("Failed on request " + i);
                    } else {
                        sleep(1);
                        listener.onDropped();
                        sleep(sleep.get().toMillis());
                    }
                }
            }
        }

        private void initializeBackoff() {
            if (backoff != null) {
                return;
            }
            backoff = backoffFactory.get();
        }
    }

    private static void sleep(long duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
