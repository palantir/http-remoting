/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.util.concurrent.SettableFuture;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link OkHttpClient} that executes {@link okhttp3.Call}s as {@link RemotingOkHttpCall}s in order to retry a class
 * of retryable error states.
 */
final class RemotingOkHttpClient extends ForwardingOkHttpClient {
    private static final Logger log = LoggerFactory.getLogger(RemotingOkHttpClient.class);

    private static final int MAX_NUM_RELOCATIONS = 20;

    private final Supplier<BackoffStrategy> backoffStrategyFactory;
    private final UrlSelector urls;
    private final ScheduledExecutorService schedulingExecutor;
    private final ExecutorService executionExecutor;
    private final ConcurrencyLimiters concurrencyLimiters;

    RemotingOkHttpClient(
            OkHttpClient delegate,
            Supplier<BackoffStrategy> backoffStrategy,
            UrlSelector urls,
            ScheduledExecutorService schedulingExecutor,
            ExecutorService executionExecutor,
            ConcurrencyLimiters concurrencyLimiters) {
        super(delegate);
        this.backoffStrategyFactory = backoffStrategy;
        this.urls = urls;
        this.schedulingExecutor = schedulingExecutor;
        this.executionExecutor = executionExecutor;
        this.concurrencyLimiters = concurrencyLimiters;
    }

    @Override
    public RemotingOkHttpCall newCall(Request request) {
        return newCallWithMutableState(addRateLimitIdTag(request), backoffStrategyFactory.get(), MAX_NUM_RELOCATIONS);
    }

    @Override
    public Builder newBuilder() {
        log.warn("Attempting to copy RemotingOkHttpClient. Some of the functionality like rate limiting and qos will "
                + "not be available to the new client", new SafeRuntimeException("stacktrace"));
        return super.newBuilder();
    }

    RemotingOkHttpCall newCallWithMutableState(
            Request request, BackoffStrategy backoffStrategy, int maxNumRelocations) {
        return new RemotingOkHttpCall(
                getDelegate().newCall(request),
                backoffStrategy,
                urls,
                this,
                schedulingExecutor,
                executionExecutor,
                concurrencyLimiters.acquireLimiter(request),
                maxNumRelocations);
    }

    private Request addRateLimitIdTag(Request request) {
        return request.newBuilder()
                .tag(ConcurrencyLimiterListener.class, ConcurrencyLimiterListener.of(SettableFuture.create()))
                .build();
    }
}
