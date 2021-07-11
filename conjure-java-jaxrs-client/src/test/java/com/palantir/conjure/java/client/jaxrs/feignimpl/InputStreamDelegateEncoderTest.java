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
import static org.mockito.Mockito.verify;

import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.client.jaxrs.TestBase;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import feign.RequestTemplate;
import feign.codec.Encoder;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.ClassRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public final class InputStreamDelegateEncoderTest extends TestBase {
    @Mock
    private Encoder delegate;

    private final RequestTemplate requestTemplate = new RequestTemplate();

    private Encoder inputStreamDelegateEncoder;

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(GuavaTestServer.class, "src/test/resources/test-server.yml");

    private GuavaTestServer.TestService service;

    @BeforeEach
    public void before() {
        inputStreamDelegateEncoder = new InputStreamDelegateEncoder(delegate);

        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service = JaxRsClient.create(
                GuavaTestServer.TestService.class, AGENT, new HostMetricsRegistry(), createTestConfig(endpointUri));
    }

    @Test
    public void testEncodesAsInputStream() throws Exception {
        byte[] object = bytes("data");

        inputStreamDelegateEncoder.encode(new ByteArrayInputStream(object), InputStream.class, requestTemplate);
        assertThat(requestTemplate.body()).isEqualTo(object);
    }

    @Test
    public void testUsesDelegateWithNonInputStreamBodyType() throws Exception {
        String data = "data";

        inputStreamDelegateEncoder.encode(data, String.class, requestTemplate);
        verify(delegate).encode(data, String.class, requestTemplate);
    }

    @Test
    public void testStandardClientsUseByteArrayDelegateEncoder() {
        String data = "bytes";
        assertThat(service.readInputStream(new ByteArrayInputStream(bytes(data))))
                .isEqualTo(data);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
