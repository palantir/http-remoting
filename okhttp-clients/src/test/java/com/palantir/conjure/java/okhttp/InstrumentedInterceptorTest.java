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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Timer;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.util.Collection;
import okhttp3.Call;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public final class InstrumentedInterceptorTest {

    private static final int PORT = 8080;
    private static final String URL_A = "https://hosta:" + PORT;
    private static final String URL_B = "https://hostb:" + PORT;
    private static final Request REQUEST_A = new Request.Builder().url(URL_A).build();
    private static final Request REQUEST_B = new Request.Builder().url(URL_B).build();

    @Mock
    private Interceptor.Chain chain;

    @Mock
    private Call call;

    private TaggedMetricRegistry registry;
    private InstrumentedInterceptor interceptor;
    private HostMetricsRegistry hostMetrics;

    @BeforeEach
    public void before() {
        registry = new DefaultTaggedMetricRegistry();
        hostMetrics = new HostMetricsRegistry();
        interceptor = new InstrumentedInterceptor(ClientMetrics.of(registry), hostMetrics, "client");
    }

    @Test
    public void testResponseFamilyMetrics() throws IOException {
        successfulRequest(REQUEST_A);
        interceptor.intercept(chain);

        HostMetrics hostA = hostMetrics("hosta", PORT);
        assertThat(hostA.get2xx().getCount()).isEqualTo(1);

        successfulRequest(REQUEST_B);
        interceptor.intercept(chain);

        HostMetrics hostB = hostMetrics("hostb", PORT);
        assertThat(hostB.get2xx().getCount()).isEqualTo(1);
    }

    @Test
    public void testResponseMetricRegistered() throws IOException {
        MetricName name = MetricName.builder()
                .safeName("client.response")
                .putSafeTags("service-name", "client")
                .putSafeTags("libraryName", "conjure-java-runtime")
                .putSafeTags("libraryVersion", "unknown")
                .build();
        Timer timer = registry.timer(name);

        assertThat(timer.getCount()).isZero();

        successfulRequest(REQUEST_A);
        interceptor.intercept(chain);

        assertThat(timer.getCount()).isEqualTo(1);
    }

    @Test
    public void testIoExceptionRecorded() throws IOException {
        failedRequest(REQUEST_A, false);

        assertThat(hostMetrics.getMetrics()).isEmpty();

        assertThatExceptionOfType(IOException.class).isThrownBy(() -> interceptor.intercept(chain));

        HostMetrics metrics = Iterables.getOnlyElement(hostMetrics.getMetrics());
        assertThat(metrics.getIoExceptions().getCount()).isEqualTo(1);
    }

    @Test
    public void testIoExceptionNotRecordedWhenCanceled() throws IOException {
        failedRequest(REQUEST_A, true);

        assertThat(hostMetrics.getMetrics()).isEmpty();

        assertThatExceptionOfType(IOException.class).isThrownBy(() -> interceptor.intercept(chain));

        assertThat(hostMetrics.getMetrics()).isEmpty();
    }

    private HostMetrics hostMetrics(String hostname, int port) {
        Collection<HostMetrics> matching = Collections2.filter(
                hostMetrics.getMetrics(), metrics -> metrics.hostname().equals(hostname) && metrics.port() == port);
        return Iterables.getOnlyElement(matching);
    }

    private void successfulRequest(Request request) throws IOException {
        Response response = new Response.Builder()
                .request(request)
                .message("")
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .build();
        when(chain.request()).thenReturn(request);
        when(chain.proceed(request)).thenReturn(response);
    }

    private void failedRequest(Request request, boolean isCanceled) throws IOException {
        when(chain.request()).thenReturn(request);
        when(chain.proceed(request)).thenThrow(IOException.class);
        when(chain.call()).thenReturn(call);
        when(call.isCanceled()).thenReturn(isCanceled);
    }
}
