/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.net.HttpHeaders;
import com.google.common.reflect.TypeToken;
import com.palantir.conjure.java.api.errors.QosException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.Duration;
import javax.annotation.Nullable;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Response;
import retrofit2.Retrofit;

public final class QosExceptionThrowingCallAdapterFactoryTest {
    private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];
    private static final ResponseBody NO_CONTENT_RESPONSE_BODY = new ResponseBody() {
        @Nullable
        @Override
        public MediaType contentType() {
            return null;
        }

        @Override
        public long contentLength() {
            return 0;
        }

        @Override
        public BufferedSource source() {
            throw new RuntimeException("No source provided");
        }
    };

    private final CallAdapter.Factory delegateFactory = mock(CallAdapter.Factory.class);
    private final CallAdapter<Void, Void> delegateAdapter = mock(CallAdapter.class);
    private final Call<Void> call = mock(Call.class);

    private final Type type = new TypeToken<Void>() {}.getType();
    private final ArgumentCaptor<Call<Void>> argument = ArgumentCaptor.forClass(Call.class);
    private final HttpUrl url = HttpUrl.get("https://google.com");

    private final CallAdapter.Factory factory = new QosExceptionThrowingCallAdapterFactory(delegateFactory);
    private Retrofit retrofit;

    @BeforeEach
    public void before() {
        retrofit = new Retrofit.Builder()
                .baseUrl(url)
                .addCallAdapterFactory(factory)
                .build();
        when(delegateFactory.get(type, NO_ANNOTATIONS, retrofit))
                .thenAnswer((Answer<CallAdapter<Void, Void>>) _invocation -> delegateAdapter);
        when(delegateAdapter.adapt(argument.capture())).thenReturn(null);
    }

    @Test
    public void http_429_throw_qos_throttle() throws IOException {
        when(call.execute()).thenReturn(Response.error(429, NO_CONTENT_RESPONSE_BODY));
        CallAdapter<Void, Void> adapter = (CallAdapter<Void, Void>) factory.get(type, NO_ANNOTATIONS, retrofit);
        adapter.adapt(call);
        assertThatThrownBy(() -> argument.getValue().execute())
                .isInstanceOfSatisfying(QosException.Throttle.class, e -> assertThat(e.getRetryAfter())
                        .isEmpty());
    }

    @Test
    public void http_429_throw_qos_throttle_with_retry_after() throws IOException {
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).get().build();
        okhttp3.Response response = new okhttp3.Response.Builder()
                .code(429)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .message("test")
                .build();
        when(call.execute()).thenReturn(Response.error(NO_CONTENT_RESPONSE_BODY, response));
        CallAdapter<Void, Void> adapter = (CallAdapter<Void, Void>) factory.get(type, NO_ANNOTATIONS, retrofit);
        adapter.adapt(call);
        assertThatThrownBy(() -> argument.getValue().execute())
                .isInstanceOfSatisfying(QosException.Throttle.class, e -> assertThat(e.getRetryAfter())
                        .contains(Duration.ofSeconds(5)));
    }

    @Test
    public void http_503_throw_qos_unavailable() throws IOException {
        when(call.execute()).thenReturn(Response.error(503, NO_CONTENT_RESPONSE_BODY));
        CallAdapter<Void, Void> adapter = (CallAdapter<Void, Void>) factory.get(type, NO_ANNOTATIONS, retrofit);
        adapter.adapt(call);
        assertThatThrownBy(() -> argument.getValue().execute()).isInstanceOf(QosException.Unavailable.class);
    }
}
