/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.Uninterruptibles;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import io.undertow.Undertow;
import io.undertow.server.handlers.BlockingHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

public class OkHttpClientsRealServerTest extends TestBase {

    private static final int PORT = 5346;
    private final HostMetricsRegistry hostEventsSink = new HostMetricsRegistry();

    @Test
    public void testInterruptionPreventsAdditionalRequests() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch completionLatch = new CountDownLatch(1);
        Undertow server = Undertow.builder()
                .addHttpListener(PORT, null)
                .setHandler(new BlockingHandler(exchange -> {
                    requests.incrementAndGet();
                    exchange.setStatusCode(503);
                }))
                .build();
        String url = "http://localhost:" + PORT;
        ExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        server.start();
        try {
            OkHttpClient client = OkHttpClients.withStableUrisAndBackoff(
                    ClientConfiguration.builder()
                            .from(createTestConfig(url))
                            .maxNumRetries(10)
                            .backoffSlotSize(Duration.ofSeconds(3))
                            .build(),
                    OkHttpClientsTest.class,
                    () -> new ReproducibleExponentialBackoff(10, Duration.ofSeconds(3)));
            Future<?> future = executorService.submit((Callable<Void>) () -> {
                try {
                    client.newCall(new Request.Builder().url(url).build())
                            .execute()
                            .close();
                } finally {
                    completionLatch.countDown();
                }
                return null;
            });
            // Wait long enough that we're confident the client is retrying
            Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(1));
            future.cancel(true);
            assertThat(completionLatch.await(1, TimeUnit.SECONDS))
                    .as("Expected the client invocation to terminate quickly upon interruption")
                    .isTrue();
            // Provide enough time to capture a retry if the interrupted request is retried.
            Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(4));
        } finally {
            server.stop();
            assertThat(MoreExecutors.shutdownAndAwaitTermination(executorService, Duration.ofSeconds(5)))
                    .isTrue();
        }
        assertThat(requests)
                .as("Expected a single request to be cancelled before a scheduled retry that is never executed")
                .hasValue(1);
    }

    @Test
    public void testInterruptionPreventsAdditionalRequests_afterRetry() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch completionLatch = new CountDownLatch(1);
        Undertow server = Undertow.builder()
                .addHttpListener(PORT, null)
                .setHandler(new BlockingHandler(exchange -> {
                    requests.incrementAndGet();
                    exchange.setStatusCode(503);
                }))
                .build();
        String url = "http://localhost:" + PORT;
        ExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        server.start();
        try {
            OkHttpClient client = OkHttpClients.withStableUrisAndBackoff(
                    ClientConfiguration.builder()
                            .from(createTestConfig(url))
                            .maxNumRetries(10)
                            .backoffSlotSize(Duration.ofSeconds(2))
                            .build(),
                    OkHttpClientsTest.class,
                    () -> new ReproducibleExponentialBackoff(10, Duration.ofSeconds(2)));
            Future<?> future = executorService.submit((Callable<Void>) () -> {
                try {
                    client.newCall(new Request.Builder().url(url).build())
                            .execute()
                            .close();
                } finally {
                    completionLatch.countDown();
                }
                return null;
            });
            // Wait long enough that we're confident the client is retrying
            Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(7));
            future.cancel(true);
            System.err.println("CANCEL");
            assertThat(completionLatch.await(1, TimeUnit.SECONDS))
                    .as("Expected the client invocation to terminate quickly upon interruption")
                    .isTrue();
            // Provide enough time to capture a retry if the interrupted request is retried.
            Uninterruptibles.sleepUninterruptibly(Duration.ofSeconds(10));
        } finally {
            server.stop();
            assertThat(MoreExecutors.shutdownAndAwaitTermination(executorService, Duration.ofSeconds(5)))
                    .isTrue();
        }
        assertThat(requests)
                .as("Expected a single request to be cancelled before a scheduled retry that is never executed")
                .hasValue(2);
    }

    @Test
    public void testClientInstancesShareConnections() throws Exception {
        SslConfiguration serverSslConfig = SslConfiguration.of(
                Paths.get("src/test/resources/trustStore.jks"),
                Paths.get("src/test/resources/keyStore.jks"),
                "keystore");
        Set<String> sslSessionIds = ConcurrentHashMap.newKeySet();
        Undertow server = Undertow.builder()
                .addHttpsListener(PORT, null, SslSocketFactories.createSslContext(serverSslConfig))
                .setHandler(exchange -> {
                    byte[] sessionId =
                            exchange.getConnection().getSslSessionInfo().getSessionId();
                    sslSessionIds.add(new String(Base64.getEncoder().encode(sessionId), StandardCharsets.UTF_8));
                })
                .build();
        String url = "https://localhost:" + PORT;
        ClientConfiguration config = createTestConfig(url);
        OkHttpClient client1 = OkHttpClients.create(config, AGENT, hostEventsSink, OkHttpClientsRealServerTest.class);
        OkHttpClient client2 = OkHttpClients.create(config, AGENT, hostEventsSink, OkHttpClientsRealServerTest.class);
        server.start();
        try {
            try (Response response = client1.newCall(
                            new Request.Builder().url(url).get().build())
                    .execute()) {
                assertThat(response.code()).isEqualTo(200);
            }
            try (Response response = client2.newCall(
                            new Request.Builder().url(url).get().build())
                    .execute()) {
                assertThat(response.code()).isEqualTo(200);
            }
        } finally {
            server.stop();
        }
        assertThat(sslSessionIds)
                .as("Expected the same session to be reused between clients")
                .hasSize(1);
    }

    private static final class ReproducibleExponentialBackoff implements BackoffStrategy {

        private final ExponentialBackoff delegate;

        ReproducibleExponentialBackoff(int maxRetries, Duration backoffSlotSize) {
            this.delegate = new ExponentialBackoff(maxRetries, backoffSlotSize, () -> 1D);
        }

        @Override
        public Optional<Duration> nextBackoff() {
            return delegate.nextBackoff();
        }
    }
}
