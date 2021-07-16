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

import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.api.errors.QosException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public final class QosExceptionResponseHandlerTest extends TestBase {

    private static final Request REQUEST =
            new Request.Builder().url("http://127.0.0.1").build();
    private static final QosExceptionResponseHandler handler = QosExceptionResponseHandler.INSTANCE;
    private static final URL LOCAL_URL = parseUrl("https://localhost");

    @Test
    public void test308() throws Exception {
        Response response;

        // with header
        response = response(REQUEST, 308)
                .header(HttpHeaders.LOCATION, LOCAL_URL.toString())
                .build();
        assertThat(handler.handle(response).get())
                .isInstanceOfSatisfying(
                        QosException.RetryOther.class,
                        retryOther -> assertThat(retryOther.getRedirectTo()).isEqualTo(LOCAL_URL));

        // with header
        response = response(REQUEST, 308).build();
        assertThat(handler.handle(response)).isEmpty();
    }

    @Test
    public void test429WithoutRetryAfter() throws Exception {
        Response response = responseWithCode(REQUEST, 429);

        assertThat(handler.handle(response).get())
                .isInstanceOfSatisfying(
                        QosException.Throttle.class,
                        retryAfter -> assertThat(retryAfter.getRetryAfter()).isEmpty());
    }

    @Test
    public void test429WithRetryAfter() throws Exception {
        Response response = response(REQUEST, 429).header("Retry-After", "120").build();

        assertThat(handler.handle(response).get())
                .isInstanceOfSatisfying(
                        QosException.Throttle.class,
                        retryAfter -> assertThat(retryAfter.getRetryAfter()).contains(Duration.ofMinutes(2)));
    }

    @Test
    public void test503() throws Exception {
        Response response = responseWithCode(REQUEST, 503);
        assertThat(handler.handle(response).get()).isInstanceOf(QosException.Unavailable.class);
    }

    @Test
    public void doesNotHandleOtherCodes() throws Exception {
        Response response = responseWithCode(REQUEST, 500);
        assertThat(handler.handle(response)).isEmpty();
    }

    private static URL parseUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Response.Builder response(Request request, int code) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("unused");
    }
}
