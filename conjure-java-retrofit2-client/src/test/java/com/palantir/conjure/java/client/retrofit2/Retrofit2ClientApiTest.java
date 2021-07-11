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

package com.palantir.conjure.java.client.retrofit2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.palantir.conjure.java.api.errors.QosException.Throttle;
import com.palantir.conjure.java.api.errors.QosException.Unavailable;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration.ServerQoS;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.logsafe.exceptions.SafeNullPointerException;
import com.palantir.logsafe.testing.Assertions;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Response;

public final class Retrofit2ClientApiTest extends TestBase {
    @Rule
    public final MockWebServer server = new MockWebServer();

    private static final SerializableError ERROR = SerializableError.builder()
            .errorCode("errorCode")
            .errorName("errorName")
            .build();

    private HttpUrl url;
    private TestService service;

    @BeforeEach
    public void before() {
        url = server.url("/");
        service = Retrofit2Client.create(
                TestService.class, AGENT, new HostMetricsRegistry(), createTestConfig(url.toString()));
    }

    @Test
    public void testGuavaOptionalParamStringHandling() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getGuavaOptionalString(guavaOptional("p"), guavaOptional("q"))
                        .execute()
                        .body())
                .isEqualTo(guavaOptional("pong"));
        assertThat(server.takeRequest().getPath()).isEqualTo("/getGuavaOptionalString/p/?queryString=q");

        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getGuavaOptionalString(guavaOptional("p"), guavaEmptyOptional())
                        .execute()
                        .body())
                .isEqualTo(guavaOptional("pong"));
        assertThat(server.takeRequest().getPath()).isEqualTo("/getGuavaOptionalString/p/");

        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getGuavaOptionalString(guavaEmptyOptional(), guavaOptional("q"))
                        .execute()
                        .body())
                .isEqualTo(guavaOptional("pong"));
        assertThat(server.takeRequest().getPath()).isEqualTo("/getGuavaOptionalString//?queryString=q");

        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getGuavaOptionalString(guavaEmptyOptional(), guavaEmptyOptional())
                        .execute()
                        .body())
                .isEqualTo(guavaOptional("pong"));
        assertThat(server.takeRequest().getPath()).isEqualTo("/getGuavaOptionalString//");
    }

    @Test
    public void testOptionalParamStringHandling() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getJava8OptionalString(java8Optional("p"), java8Optional("q"))
                        .execute()
                        .body())
                .isEqualTo(java8Optional("pong"));
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalString/p/?queryString=q");

        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getJava8OptionalString(java8Optional("p"), java8EmptyOptional())
                        .execute()
                        .body())
                .isEqualTo(java8Optional("pong"));
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalString/p/");

        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getJava8OptionalString(java8EmptyOptional(), java8Optional("q"))
                        .execute()
                        .body())
                .isEqualTo(java8Optional("pong"));
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalString//?queryString=q");

        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getJava8OptionalString(java8EmptyOptional(), java8EmptyOptional())
                        .execute()
                        .body())
                .isEqualTo(java8Optional("pong"));
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalString//");
    }

    @Test
    public void testEmptyGuavaOptionalResponseStringHandling() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(204));
        assertThat(service.getGuavaOptionalString(guavaEmptyOptional(), guavaEmptyOptional())
                        .execute()
                        .body())
                .isEqualTo(guavaEmptyOptional());
        assertThat(server.takeRequest().getPath()).isEqualTo("/getGuavaOptionalString//");
    }

    @Test
    public void testEmptyOptionalResponseStringHandling() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(204));
        assertThat(service.getJava8OptionalString(java8EmptyOptional(), java8EmptyOptional())
                        .execute()
                        .body())
                .isEqualTo(java8EmptyOptional());
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalString//");

        server.enqueue(new MockResponse().setResponseCode(204));
        assertThat(service.getJava8OptionalInt().execute().body()).isEqualTo(OptionalInt.empty());
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalInt");

        server.enqueue(new MockResponse().setResponseCode(204));
        assertThat(service.getJava8OptionalLong().execute().body()).isEqualTo(OptionalLong.empty());
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalLong");

        server.enqueue(new MockResponse().setResponseCode(204));
        assertThat(service.getJava8OptionalDouble().execute().body()).isEqualTo(OptionalDouble.empty());
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalDouble");
    }

    @Test
    public void testNullGuavaOptionalResponseStringHandling() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setBody("null"));
        assertThat(service.getGuavaOptionalString(guavaEmptyOptional(), guavaEmptyOptional())
                        .execute()
                        .body())
                .isEqualTo(guavaEmptyOptional());
        assertThat(server.takeRequest().getPath()).isEqualTo("/getGuavaOptionalString//");
    }

    @Test
    public void testNullOptionalResponseStringHandling() throws IOException, InterruptedException {
        server.enqueue(new MockResponse().setBody("null"));
        assertThat(service.getJava8OptionalString(java8EmptyOptional(), java8EmptyOptional())
                        .execute()
                        .body())
                .isEqualTo(java8EmptyOptional());
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalString//");

        server.enqueue(new MockResponse().setBody("null"));
        assertThat(service.getJava8OptionalInt().execute().body()).isEqualTo(OptionalInt.empty());
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalInt");

        server.enqueue(new MockResponse().setBody("null"));
        assertThat(service.getJava8OptionalLong().execute().body()).isEqualTo(OptionalLong.empty());
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalLong");

        server.enqueue(new MockResponse().setBody("null"));
        assertThat(service.getJava8OptionalDouble().execute().body()).isEqualTo(OptionalDouble.empty());
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalDouble");
    }

    @Test
    public void testOptionalComplexReturnValues() throws IOException, InterruptedException {
        LocalDate date = LocalDate.of(2001, 2, 3);
        String dateString = "\"2001-02-03\"";

        server.enqueue(new MockResponse().setBody(dateString));
        assertThat(service.getComplexGuavaType(guavaOptional(date)).execute().body())
                .isEqualTo(guavaOptional(date));
        assertThat(server.takeRequest().getBody().readUtf8()).isEqualTo(dateString);

        server.enqueue(new MockResponse().setBody(dateString));
        assertThat(service.getComplexJava8Type(java8Optional(date)).execute().body())
                .isEqualTo(java8Optional(date));
        assertThat(server.takeRequest().getBody().readUtf8()).isEqualTo(dateString);
    }

    @Test
    public void should_reject_body_containing_json_null() {
        server.enqueue(new MockResponse().setBody("null"));
        Assertions.assertThatLoggableExceptionThrownBy(
                        () -> service.getRelative().execute().body())
                .hasMessage("Unexpected null body")
                .isInstanceOf(SafeNullPointerException.class);
    }

    @Test
    public void should_reject_body_containing_empty_string() {
        server.enqueue(new MockResponse().setBody(""));
        assertThatThrownBy(() -> service.getRelative().execute().body()).isInstanceOf(SafeNullPointerException.class);
    }

    @Test
    public void testCborReturnValues() throws IOException {
        LocalDate date = LocalDate.of(2001, 2, 3);
        byte[] bytes = ObjectMappers.newCborServerObjectMapper().writeValueAsBytes(Optional.of(date));
        try (Buffer buffer = new Buffer()) {
            buffer.write(bytes);
            server.enqueue(new MockResponse().setBody(buffer).addHeader("Content-Type", "application/cbor"));
            assertThat(service.getComplexCborType().execute().body()).isEqualTo(java8Optional(date));
        }
    }

    @Test
    public void testCborRequests() throws IOException, InterruptedException {
        LocalDate date = LocalDate.of(2001, 2, 3);

        server.enqueue(new MockResponse());
        service.makeCborRequest(date).execute();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getBody().readByteArray())
                .isEqualTo(ObjectMappers.newCborClientObjectMapper().writeValueAsBytes(date));
    }

    @Test
    public void makeFutureRequest_completable() {
        makeFutureRequest(() -> service.makeCompletableFutureRequest());
    }

    @Test
    public void makeFutureRequest_listenable() {
        makeFutureRequest(() -> service.makeListenableFutureRequest());
    }

    private void makeFutureRequest(Supplier<Future<String>> futureSupplier) {
        String value = "value";
        server.enqueue(new MockResponse().setBody("\"" + value + "\""));
        Future<String> future = futureSupplier.get();
        assertThat(Futures.getUnchecked(future)).isEqualTo("value");
    }

    @Test
    public void makeFutureRequestError_completable() throws JsonProcessingException {
        makeFutureRequestError(() -> service.makeCompletableFutureRequest());
    }

    @Test
    public void makeFutureRequestError_listenable() throws JsonProcessingException {
        makeFutureRequestError(() -> service.makeListenableFutureRequest());
    }

    private void makeFutureRequestError(Supplier<Future<String>> futureSupplier) throws JsonProcessingException {
        SerializableError error = SerializableError.builder()
                .errorCode("errorCode")
                .errorName("errorName")
                .build();

        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody(ObjectMappers.newClientObjectMapper().writeValueAsString(error)));

        assertThatThrownBy(() -> Futures.getUnchecked(futureSupplier.get()))
                .isInstanceOf(UncheckedExecutionException.class)
                .hasCauseInstanceOf(RemoteException.class)
                .satisfies(e ->
                        assertThat(((RemoteException) e.getCause()).getError()).isEqualTo(error));
    }

    @Test
    public void connectionFailureWithFuture_completable() {
        connectionFailureWithFuture(() -> service.makeCompletableFutureRequest());
    }

    @Test
    public void connectionFailureWithFuture_listenable() {
        connectionFailureWithFuture(() -> service.makeListenableFutureRequest());
    }

    private void connectionFailureWithFuture(Supplier<Future<String>> futureSupplier) {
        service = Retrofit2Client.create(
                TestService.class,
                AGENT,
                new HostMetricsRegistry(),
                ClientConfiguration.builder()
                        .from(createTestConfig("https://service.invalid"))
                        .connectTimeout(Duration.ofMillis(10))
                        .build());

        assertThatExceptionOfType(UncheckedExecutionException.class)
                .isThrownBy(() -> Futures.getUnchecked(futureSupplier.get()));
    }

    @Test
    public void future_should_throw_RemoteException_for_server_serializable_errors_listenable() throws Exception {
        future_should_throw_RemoteException_for_server_serializable_errors(() -> service.makeListenableFutureRequest());
    }

    @Test
    public void future_should_throw_RemoteException_for_server_serializable_errors_completable() throws Exception {
        future_should_throw_RemoteException_for_server_serializable_errors(
                () -> service.makeCompletableFutureRequest());
    }

    private void future_should_throw_RemoteException_for_server_serializable_errors(
            Supplier<Future<String>> futureSupplier) throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody(ObjectMappers.newClientObjectMapper().writeValueAsString(ERROR)));

        Future<String> future = futureSupplier.get();

        try {
            Futures.getUnchecked(future);
            failBecauseExceptionWasNotThrown(CompletionException.class);
        } catch (UncheckedExecutionException e) {
            assertThat(e.getCause()).isInstanceOf(RemoteException.class);
            RemoteException remoteException = (RemoteException) e.getCause();
            assertThat(remoteException.getError()).isEqualTo(ERROR);
        }
    }

    @Test
    public void future_should_throw_normal_IoException_for_client_side_errors_completable() {
        future_should_throw_normal_IoException_for_client_side_errors(() -> service.makeCompletableFutureRequest());
    }

    @Test
    public void future_should_throw_normal_IoException_for_client_side_errors_listenable() {
        future_should_throw_normal_IoException_for_client_side_errors(() -> service.makeListenableFutureRequest());
    }

    private void future_should_throw_normal_IoException_for_client_side_errors(
            Supplier<Future<String>> futureSupplier) {
        service = Retrofit2Client.create(
                TestService.class,
                AGENT,
                new HostMetricsRegistry(),
                ClientConfiguration.builder()
                        .from(createTestConfig("https://service.invalid"))
                        .connectTimeout(Duration.ofMillis(10))
                        .build());

        assertThatThrownBy(() -> Futures.getUnchecked(futureSupplier.get()))
                .isInstanceOf(UncheckedExecutionException.class)
                .hasCauseInstanceOf(IOException.class)
                .hasMessageContaining("Failed to complete the request due to an IOException");
    }

    @Test
    public void sync_retrofit_call_should_throw_RemoteException_for_server_serializable_errors() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody(ObjectMappers.newClientObjectMapper().writeValueAsString(ERROR)));

        Call<String> call = service.getRelative();

        try {
            call.execute();
            failBecauseExceptionWasNotThrown(RemoteException.class);
        } catch (RemoteException e) {
            assertThat(e.getError()).isEqualTo(ERROR);
        }
    }

    @Ignore("TODO(rfink): Async Retrofit calls should produce RemoteException, Issue #625")
    @Test
    public void async_retrofit_call_should_throw_RemoteException_for_server_serializable_errors() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody(ObjectMappers.newClientObjectMapper().writeValueAsString(ERROR)));

        CountDownLatch assertionsPassed = new CountDownLatch(1);
        Call<String> call = service.getRelative();
        call.enqueue(new retrofit2.Callback<String>() {
            @Override
            public void onResponse(Call<String> _call, Response<String> _response) {
                failBecauseExceptionWasNotThrown(RemoteException.class);
            }

            @Override
            public void onFailure(Call<String> _call, Throwable throwable) {
                assertThat(throwable).isInstanceOf(RemoteException.class);
                assertThat(((RemoteException) throwable).getError()).isEqualTo(ERROR);
                assertionsPassed.countDown(); // if you delete this countdown latch then this test will vacuously pass.
            }
        });
        assertThat(assertionsPassed.await(1, TimeUnit.SECONDS))
                .as("Callback was executed")
                .isTrue();
    }

    @Test
    public void serializes_java_optional_headers() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("\"body\""));
        assertThat(Futures.getUnchecked(service.getJavaOptionalHeader(Optional.of("value"))))
                .isEqualTo("body");
        assertThat(server.takeRequest().getHeader("Optional-Header")).isEqualTo("value");
    }

    @Test
    public void serializes_guava_optional_headers() throws InterruptedException {
        server.enqueue(new MockResponse().setBody("\"body\""));
        assertThat(Futures.getUnchecked(service.getGuavaOptionalHeader(guavaOptional("value"))))
                .isEqualTo("body");
        assertThat(server.takeRequest().getHeader("Optional-Header")).isEqualTo("value");
    }

    @Test
    public void listenableFuture_propagates_503_correctly() {
        tweakClientConfiguration(builder -> builder.serverQoS(ServerQoS.PROPAGATE_429_and_503_TO_CALLER));
        server.enqueue(new MockResponse().setResponseCode(503));
        assertThatCode(() -> Futures.getUnchecked(service.getResponseBody()))
                .hasCauseExactlyInstanceOf(Unavailable.class);
    }

    @Test
    public void call_execute_propagates_503_correctly() {
        tweakClientConfiguration(builder -> builder.serverQoS(ServerQoS.PROPAGATE_429_and_503_TO_CALLER));
        server.enqueue(new MockResponse().setResponseCode(503));
        assertThatExceptionOfType(Unavailable.class)
                .isThrownBy(() -> service.getCallOfResponseBody().execute());
    }

    @Test
    public void listenableFuture_propagates_429_correctly() {
        tweakClientConfiguration(builder -> builder.serverQoS(ServerQoS.PROPAGATE_429_and_503_TO_CALLER));
        server.enqueue(new MockResponse().setResponseCode(429));
        assertThatCode(() -> Futures.getUnchecked(service.getResponseBody())).hasCauseExactlyInstanceOf(Throttle.class);
    }

    @Test
    public void call_execute_propagates_429_correctly() {
        tweakClientConfiguration(builder -> builder.serverQoS(ServerQoS.PROPAGATE_429_and_503_TO_CALLER));
        server.enqueue(new MockResponse().setResponseCode(429));
        assertThatExceptionOfType(Throttle.class)
                .isThrownBy(() -> service.getCallOfResponseBody().execute());
    }

    private void tweakClientConfiguration(Consumer<ClientConfiguration.Builder> configuration) {
        ClientConfiguration.Builder builder = ClientConfiguration.builder().from(createTestConfig(url.toString()));
        configuration.accept(builder);
        service = Retrofit2Client.create(TestService.class, AGENT, new HostMetricsRegistry(), builder.build());
    }

    private static <T> com.google.common.base.Optional<T> guavaOptional(T value) {
        return com.google.common.base.Optional.of(value);
    }

    private static <T> com.google.common.base.Optional<T> guavaEmptyOptional() {
        return com.google.common.base.Optional.absent();
    }

    private static <T> Optional<T> java8Optional(T value) {
        return Optional.of(value);
    }

    private static <T> Optional<T> java8EmptyOptional() {
        return Optional.empty();
    }
}
