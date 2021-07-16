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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.client.jaxrs.TestBase;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class TextEncoderTest extends TestBase {

    @Rule
    public final MockWebServer server = new MockWebServer();

    private Service service;

    @BeforeEach
    public void before() {
        service = JaxRsClient.create(
                Service.class,
                AGENT,
                new HostMetricsRegistry(),
                createTestConfig("http://localhost:" + server.getPort()));
        server.enqueue(new MockResponse().setBody("{}"));
    }

    @Test
    public void testTextEncoder_doesNotPerformJsonEscapting() throws InterruptedException {
        String testString = "\"string\"";
        service.post(testString);

        RecordedRequest request = server.takeRequest();
        assertThat(request.getBody().readUtf8()).isEqualTo(testString);
    }

    @Path("/")
    public interface Service {
        @POST
        @Consumes(MediaType.TEXT_PLAIN)
        Object post(String test);
    }
}
