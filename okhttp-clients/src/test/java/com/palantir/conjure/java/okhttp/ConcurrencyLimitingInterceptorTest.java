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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.netflix.concurrency.limits.Limiter;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public final class ConcurrencyLimitingInterceptorTest {
    private static final ConcurrencyLimitingInterceptor interceptor = new ConcurrencyLimitingInterceptor();

    @Mock
    private BufferedSource mockSource;

    @Mock
    private Interceptor.Chain chain;

    @Mock
    private Limiter.Listener listener;

    private Request request;
    private Response response;

    @BeforeEach
    public void before() {
        request = new Request.Builder()
                .url("https://localhost:1234/call")
                .tag(
                        ConcurrencyLimiterListener.class,
                        ConcurrencyLimiterListener.create().setLimiterListener(Futures.immediateFuture(listener)))
                .get()
                .build();
        response = new Response.Builder()
                .code(200)
                .request(request)
                .message("message")
                .protocol(Protocol.HTTP_2)
                .build();
        when(chain.request()).thenReturn(request);
    }

    @Test
    public void dropsIfRateLimited() throws IOException {
        Response rateLimited = response.newBuilder().code(429).build();
        when(chain.proceed(request)).thenReturn(rateLimited);
        assertThat(interceptor.intercept(chain)).isEqualTo(rateLimited);
        verify(listener).onDropped();
    }

    @Test
    public void dropsIfUnavailable() throws IOException {
        Response unavailable = response.newBuilder().code(503).build();
        when(chain.proceed(request)).thenReturn(unavailable);
        assertThat(interceptor.intercept(chain)).isEqualTo(unavailable);
        verify(listener).onDropped();
    }

    @Test
    public void ignoresIfRedirect() throws IOException {
        Response redirect = response.newBuilder().code(308).build();
        when(chain.proceed(request)).thenReturn(redirect);
        assertThat(interceptor.intercept(chain)).isEqualTo(redirect);
        verify(listener).onIgnore();
    }

    @Test
    public void ignoresIfUnsuccessful() throws IOException {
        Response unsuccessful = response.newBuilder().code(500).build();
        when(chain.proceed(request)).thenReturn(unsuccessful);
        assertThat(interceptor.intercept(chain)).isEqualTo(unsuccessful);
        verify(listener).onIgnore();
    }

    @Test
    public void ignoresIfIoException() throws IOException {
        IOException exception = new IOException();
        when(chain.proceed(request)).thenThrow(exception);
        assertThatThrownBy(() -> interceptor.intercept(chain)).isEqualTo(exception);
        verify(listener).onIgnore();
    }

    @Test
    public void wrapsResponseBody() throws IOException {
        String data = "data";
        ResponseBody body = ResponseBody.create(MediaType.parse("application/json"), data);
        when(chain.proceed(request)).thenReturn(response.newBuilder().body(body).build());
        Response wrappedResponse = interceptor.intercept(chain);
        verifyNoMoreInteractions(listener);
        assertThat(wrappedResponse.body().string()).isEqualTo(data);
        verify(listener).onSuccess();
    }

    @Test
    public void proxyHandlesExceptions() throws IOException {
        ResponseBody body = ResponseBody.create(MediaType.parse("application/json"), -1, mockSource);
        when(chain.proceed(request)).thenReturn(response.newBuilder().body(body).build());
        IOException exception = new IOException();
        when(mockSource.readByteArray()).thenThrow(exception);
        Response erroneousResponse = interceptor.intercept(chain);
        assertThatThrownBy(() -> erroneousResponse.body().source().readByteArray())
                .isEqualTo(exception);
    }

    @Test
    public void ignoresIfNoContent() throws IOException {
        Response noContent = response.newBuilder().code(204).build();
        when(chain.proceed(request)).thenReturn(noContent);
        assertThat(interceptor.intercept(chain)).isEqualTo(noContent);
        verify(listener).onIgnore();
    }

    @Test
    public void marksSuccessIfContentEmpty() throws IOException {
        Response empty = response.newBuilder()
                .code(204)
                .body(ResponseBody.create(MediaType.parse("application/json"), new byte[0]))
                .build();
        when(chain.proceed(request)).thenReturn(empty);
        assertThat(interceptor.intercept(chain)).isEqualTo(empty);
        verify(listener).onSuccess();
    }
}
