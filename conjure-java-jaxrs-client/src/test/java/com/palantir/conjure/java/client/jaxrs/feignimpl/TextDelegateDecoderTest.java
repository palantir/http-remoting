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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.client.jaxrs.TestBase;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import feign.FeignException;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.ExpectedException;

public final class TextDelegateDecoderTest extends TestBase {
    private static final String DELEGATE_RESPONSE = "delegate response";

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(GuavaTestServer.class, "src/test/resources/test-server.yml");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private GuavaTestServer.TestService service;
    private Map<String, Collection<String>> headers;
    private Decoder delegate;
    private Decoder textDelegateDecoder;

    @BeforeEach
    public void before() {
        delegate = mock(Decoder.class);
        headers = new HashMap<>();
        textDelegateDecoder = new TextDelegateDecoder(delegate);

        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service = JaxRsClient.create(
                GuavaTestServer.TestService.class, AGENT, new HostMetricsRegistry(), createTestConfig(endpointUri));
    }

    @Test
    public void testUsesStringDecoderWithTextPlain() throws Exception {
        headers.put(HttpHeaders.CONTENT_TYPE, ImmutableSet.of(MediaType.TEXT_PLAIN));
        Response response = Response.create(200, "OK", headers, "text response", StandardCharsets.UTF_8);
        Object decodedObject = textDelegateDecoder.decode(response, String.class);

        assertThat(decodedObject).isEqualTo("text response");
        verifyNoMoreInteractions(delegate);
    }

    @Test
    public void testCannotReturnStringWithMediaTypeJson() {
        assertThatThrownBy(() -> service.getJsonString("foo"))
                .isInstanceOf(FeignException.class)
                .hasMessageStartingWith("Unrecognized token 'foo': was expecting "
                        + "(JSON String, Number, Array, Object or token 'null', 'true' or 'false')");
    }

    @Test
    public void testUsesStringDecoderWithTextPlainAndCharset() throws Exception {
        headers.put(HttpHeaders.CONTENT_TYPE, ImmutableSet.of(MediaType.TEXT_PLAIN + "; charset=utf-8"));
        Response response = Response.create(200, "OK", headers, "text response", StandardCharsets.UTF_8);

        Object decodedObject = textDelegateDecoder.decode(response, String.class);

        assertThat(decodedObject).isEqualTo("text response");
        verifyNoMoreInteractions(delegate);
    }

    @Test
    public void testUsesStringDecoderWithTextPlainWithWeirdHeaderCapitalization() throws Exception {
        headers.put("content-TYPE", ImmutableSet.of(MediaType.TEXT_PLAIN));
        Response response = Response.create(200, "OK", headers, "text response", StandardCharsets.UTF_8);
        Object decodedObject = textDelegateDecoder.decode(response, String.class);

        assertThat(decodedObject).isEqualTo("text response");
        verifyNoMoreInteractions(delegate);
    }

    @Test
    public void testReturnsEmptyStringForNullResponseBodyWithTextPlain() throws Exception {
        headers.put(HttpHeaders.CONTENT_TYPE, ImmutableSet.of(MediaType.TEXT_PLAIN));
        Response response = Response.create(200, "OK", headers, null, StandardCharsets.UTF_8);
        Object decodedObject = textDelegateDecoder.decode(response, String.class);

        assertThat(decodedObject).isEqualTo("");
        verifyNoMoreInteractions(delegate);
    }

    @Test
    public void testUsesDelegateWithNoHeader() throws Exception {
        when(delegate.decode(any(), any())).thenReturn(DELEGATE_RESPONSE);
        Response response = Response.create(200, "OK", headers, new byte[0]);
        Object decodedObject = textDelegateDecoder.decode(response, String.class);

        assertThat(decodedObject).isEqualTo(DELEGATE_RESPONSE);
    }

    @Test
    public void testUsesDelegateWithComplexHeader() throws Exception {
        headers.put(HttpHeaders.CONTENT_TYPE, ImmutableSet.of(MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));
        when(delegate.decode(any(), any())).thenReturn(DELEGATE_RESPONSE);
        Response response = Response.create(200, "OK", headers, new byte[0]);
        Object decodedObject = textDelegateDecoder.decode(response, String.class);

        assertThat(decodedObject).isEqualTo(DELEGATE_RESPONSE);
    }

    @Test
    public void testUsesDelegateWithNonTextContentType() throws Exception {
        headers.put(HttpHeaders.CONTENT_TYPE, ImmutableSet.of(MediaType.APPLICATION_JSON));
        when(delegate.decode(any(), any())).thenReturn(DELEGATE_RESPONSE);
        Response response = Response.create(200, "OK", headers, new byte[0]);
        Object decodedObject = textDelegateDecoder.decode(response, String.class);

        assertThat(decodedObject).isEqualTo(DELEGATE_RESPONSE);
    }

    @Test
    public void testStandardClientsUseTextDelegateEncoder() {
        assertThat(service.getString("string")).isEqualTo("string");
        assertThatExceptionOfType(DecodeException.class)
                .isThrownBy(() -> service.getString(null))
                .withCauseInstanceOf(NullPointerException.class);
    }
}
