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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.net.HostAndPort;
import com.google.common.net.HttpHeaders;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class MeshProxyInterceptorTest {

    @Captor
    private ArgumentCaptor<Request> request;

    @Mock
    private Interceptor.Chain chain;

    private MeshProxyInterceptor interceptor;

    @BeforeEach
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        interceptor = new MeshProxyInterceptor(HostAndPort.fromString("localhost:456"));
    }

    @Test
    public void intercept_portInAuthority() throws Exception {
        when(chain.request())
                .thenReturn(new Request.Builder()
                        .url("https://foo.com:123/foo/bar?baz=norf")
                        .method("GET", null)
                        .build());

        interceptor.intercept(chain);

        verify(chain).proceed(request.capture());
        assertThat(request.getValue().url()).isEqualTo(HttpUrl.parse("https://localhost:456/foo/bar?baz=norf"));
        assertThat(request.getValue().header(HttpHeaders.HOST)).isEqualTo("foo.com:123");
    }

    @Test
    public void intercept_portNotInAuthority() throws Exception {
        when(chain.request())
                .thenReturn(new Request.Builder()
                        .url("https://foo.com/foo/bar?baz=norf")
                        .method("GET", null)
                        .build());

        interceptor.intercept(chain);

        verify(chain).proceed(request.capture());
        assertThat(request.getValue().url()).isEqualTo(HttpUrl.parse("https://localhost:456/foo/bar?baz=norf"));
        assertThat(request.getValue().header(HttpHeaders.HOST)).isEqualTo("foo.com");
    }

    @Test
    public void intercept_stripsUserinfoFromAuthority() throws Exception {
        when(chain.request())
                .thenReturn(new Request.Builder()
                        .url("https://user:pass@foo.com/foo/bar?baz=norf")
                        .method("GET", null)
                        .build());

        interceptor.intercept(chain);

        verify(chain).proceed(request.capture());
        assertThat(request.getValue().url())
                .isEqualTo(HttpUrl.parse("https://user:pass@localhost:456/foo/bar?baz=norf"));
        assertThat(request.getValue().header(HttpHeaders.HOST)).isEqualTo("foo.com");
    }
}
