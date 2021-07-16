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

package com.palantir.conjure.java.client.retrofit2;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.config.service.BasicCredentials;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.jupiter.api.Test;

public final class Retrofit2ClientProxyTest extends TestBase {

    @Rule
    public final MockWebServer server = new MockWebServer();

    @Rule
    public final MockWebServer proxyServer = new MockWebServer();

    @Test
    public void testDirectVersusProxyConnection() throws Exception {
        server.enqueue(new MockResponse().setBody("\"server\""));
        proxyServer.enqueue(new MockResponse().setBody("\"proxyServer\""));

        TestService directService = Retrofit2Client.create(
                TestService.class,
                AGENT,
                new HostMetricsRegistry(),
                createTestConfig("http://localhost:" + server.getPort()));
        ClientConfiguration proxiedConfig = ClientConfiguration.builder()
                .from(createTestConfig("http://localhost:" + server.getPort()))
                .proxy(createProxySelector("localhost", proxyServer.getPort()))
                .build();
        TestService proxiedService =
                Retrofit2Client.create(TestService.class, AGENT, new HostMetricsRegistry(), proxiedConfig);

        assertThat(directService.get().execute().body()).isEqualTo("server");
        assertThat(proxiedService.get().execute().body()).isEqualTo("proxyServer");
        RecordedRequest proxyRequest = proxyServer.takeRequest();
        assertThat(proxyRequest.getHeader("Host")).isEqualTo("localhost:" + server.getPort());
    }

    @Test
    public void testAuthenticatedProxy() throws Exception {
        proxyServer.enqueue(new MockResponse().setResponseCode(407)); // indicates authenticated proxy
        proxyServer.enqueue(new MockResponse().setBody("\"proxyServer\""));

        ClientConfiguration proxiedConfig = ClientConfiguration.builder()
                .from(createTestConfig("http://localhost:" + server.getPort()))
                .proxy(createProxySelector("localhost", proxyServer.getPort()))
                .proxyCredentials(BasicCredentials.of("fakeUser", "fakePassword"))
                .build();
        TestService proxiedService =
                Retrofit2Client.create(TestService.class, AGENT, new HostMetricsRegistry(), proxiedConfig);

        assertThat(proxiedService.get().execute().body()).isEqualTo("proxyServer");
        RecordedRequest firstRequest = proxyServer.takeRequest();
        assertThat(firstRequest.getHeader("Proxy-Authorization")).isNull();
        RecordedRequest secondRequest = proxyServer.takeRequest();
        assertThat(secondRequest.getHeader("Proxy-Authorization")).isEqualTo("Basic ZmFrZVVzZXI6ZmFrZVBhc3N3b3Jk");
    }

    private static ProxySelector createProxySelector(String host, int port) {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI _uri) {
                InetSocketAddress addr = new InetSocketAddress(host, port);
                return ImmutableList.of(new Proxy(Proxy.Type.HTTP, addr));
            }

            @Override
            public void connectFailed(URI _uri, SocketAddress _sa, IOException _ioe) {}
        };
    }
}
