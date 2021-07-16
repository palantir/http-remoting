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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.service.UserAgents;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public final class UserAgentInterceptorTest {

    private static final UserAgent USER_AGENT = UserAgent.of(UserAgent.Agent.of("test", "1.2.3"));
    private static final UserAgentInterceptor interceptor = UserAgentInterceptor.of(USER_AGENT);

    @Rule
    public final MockWebServer server = new MockWebServer();

    @BeforeEach
    public void before() {
        server.enqueue(new MockResponse().setBody("\"foo\""));
    }

    @Test
    public void testUserAgent_default() throws IOException {
        Interceptor.Chain chain = mock(Interceptor.Chain.class);
        Request request = new Request.Builder().url("http://url").build();
        when(chain.request()).thenReturn(request);
        interceptor.intercept(chain);

        verify(chain).request();
        ArgumentCaptor<Request> argument = ArgumentCaptor.forClass(Request.class);
        verify(chain).proceed(argument.capture());
        assertThat(argument.getValue().header("User-Agent")).isEqualTo(UserAgents.format(USER_AGENT));
    }
}
