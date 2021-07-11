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

package com.palantir.conjure.java.client.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import com.palantir.conjure.java.serialization.ObjectMappers;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class JaxRsClientStackTraceTest extends TestBase {

    @Rule
    public final MockWebServer server1 = new MockWebServer();

    private TestService proxy;

    @BeforeEach
    public void before() throws Exception {
        proxy = JaxRsClient.create(
                TestService.class,
                AGENT,
                new HostMetricsRegistry(),
                ClientConfiguration.builder()
                        .from(createTestConfig("http://localhost:" + server1.getPort()))
                        .maxNumRetries(1)
                        .build());
    }

    @Test
    public void stack_trace_from_ioexception_should_include_call_site() throws Exception {
        server1.shutdown();

        try {
            proxy.string();
            failBecauseExceptionWasNotThrown(Exception.class);
        } catch (Exception e) {
            assertThat(e)
                    .hasStackTraceContaining("JaxRsClientStackTraceTest."
                            + "stack_trace_from_ioexception_should_include_call_site(JaxRsClientStackTraceTest.java:");
        }
    }

    @Test
    public void stack_trace_from_remote_exception_should_include_call_site() throws Exception {
        server1.enqueue(serializableError());

        try {
            proxy.string();
            failBecauseExceptionWasNotThrown(Exception.class);
        } catch (Exception e) {
            assertThat(e)
                    .hasStackTraceContaining(
                            "JaxRsClientStackTraceTest.stack_trace_from_remote_exception_should_include_call_site("
                                    + "JaxRsClientStackTraceTest.java:");
        }
    }

    private static MockResponse serializableError() throws JsonProcessingException {
        String json = ObjectMappers.newServerObjectMapper()
                .writeValueAsString(SerializableError.forException(new ServiceException(ErrorType.INTERNAL)));
        return new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody(json);
    }
}
