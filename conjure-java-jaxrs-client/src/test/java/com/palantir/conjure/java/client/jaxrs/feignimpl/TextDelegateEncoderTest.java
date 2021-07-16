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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.net.HttpHeaders;
import feign.RequestTemplate;
import feign.codec.Encoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class TextDelegateEncoderTest {

    private RequestTemplate requestTemplate;
    private Map<String, Collection<String>> headers;
    private Encoder delegate;
    private Encoder textDelegateEncoder;

    @BeforeEach
    public void before() {
        delegate = mock(Encoder.class);
        headers = new HashMap<>();
        textDelegateEncoder = new TextDelegateEncoder(delegate);
        requestTemplate = new RequestTemplate();
    }

    @Test
    public void usesDelegateWhenHeaderIsAbsent() {
        requestTemplate.headers(headers);
        textDelegateEncoder.encode(null, null, requestTemplate);
        verify(delegate).encode(null, null, requestTemplate);
    }

    @Test
    public void usesDelegateWhenContentTypeIsNotTextPlain() {
        headers.put(HttpHeaders.CONTENT_TYPE, Arrays.asList(MediaType.APPLICATION_JSON));
        requestTemplate.headers(headers);
        textDelegateEncoder.encode(null, null, requestTemplate);
        verify(delegate).encode(null, null, requestTemplate);
    }

    @Test
    public void doesNotUseDelegateWhenContentTypeIsTextPlain() {
        headers.put(HttpHeaders.CONTENT_TYPE, Arrays.asList(MediaType.TEXT_PLAIN));
        requestTemplate.headers(headers);
        textDelegateEncoder.encode(null, null, requestTemplate);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    public void doesNotUseDelegateWhenContentTypeHeaderHasArbitraryCase() {
        headers.put("Content-TYPE", Arrays.asList(MediaType.TEXT_PLAIN));
        requestTemplate.headers(headers);
        textDelegateEncoder.encode(null, null, requestTemplate);
        verifyNoMoreInteractions(delegate);
    }
}
