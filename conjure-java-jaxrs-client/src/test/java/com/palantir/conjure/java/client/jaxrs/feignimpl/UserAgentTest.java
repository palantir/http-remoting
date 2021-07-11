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

import com.google.common.base.MoreObjects;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.service.UserAgents;
import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.client.jaxrs.TestBase;
import com.palantir.conjure.java.client.jaxrs.TestService;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import com.palantir.dialogue.Channel;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.ExpectedException;

public final class UserAgentTest extends TestBase {

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private String endpointUri;

    @BeforeEach
    public void before() {
        endpointUri = "http://localhost:" + server.getPort();
        server.enqueue(new MockResponse().setBody("\"body\""));
    }

    @Test
    public void testUserAgent_default() throws InterruptedException {
        TestService service =
                JaxRsClient.create(TestService.class, AGENT, new HostMetricsRegistry(), createTestConfig(endpointUri));
        service.string();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("User-Agent")).startsWith(UserAgents.format(AGENT));
    }

    @Test
    public void testUserAgent_usesUnknownAgentWhenProvidedWithBogusAgentString() throws InterruptedException {
        TestService service = JaxRsClient.create(
                TestService.class,
                UserAgents.tryParse("bogus version string"),
                new HostMetricsRegistry(),
                createTestConfig(endpointUri));
        service.string();

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("User-Agent")).startsWith("unknown/0.0.0");
    }

    @Test
    public void testUserAgent_augmentedByHttpRemotingAndServiceComponents() throws Exception {
        TestService service =
                JaxRsClient.create(TestService.class, AGENT, new HostMetricsRegistry(), createTestConfig(endpointUri));
        service.string();

        RecordedRequest request = server.takeRequest();

        String dialogueVersion = Channel.class.getPackage().getImplementationVersion();
        UserAgent expected = AGENT.addAgent(UserAgent.Agent.of("TestService", "0.0.0"))
                .addAgent(UserAgent.Agent.of("dialogue", MoreObjects.firstNonNull(dialogueVersion, "0.0.0")));
        assertThat(request.getHeader("User-Agent")).isEqualTo(UserAgents.format(expected));
    }
}
