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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.client.jaxrs.TestBase;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class SlashEncodingContractTest extends TestBase {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(Server.class, "src/test/resources/test-server.yml");

    private static final Service resource = mock(Service.class);
    private static final String PATH_PARAM = "slash/path";
    private static final String QUERY_PARAM = "slash/query";

    @Rule
    public final MockWebServer server = new MockWebServer();

    private Service jerseyProxy;
    private Service inMemoryProxy;

    @BeforeEach
    public void before() {
        jerseyProxy = JaxRsClient.create(
                Service.class,
                AGENT,
                new HostMetricsRegistry(),
                createTestConfig("http://localhost:" + APP.getLocalPort()));
        inMemoryProxy = JaxRsClient.create(
                Service.class,
                AGENT,
                new HostMetricsRegistry(),
                createTestConfig("http://localhost:" + server.getPort()));
        server.enqueue(new MockResponse().setBody("\"foo\""));
    }

    @Test
    public void testJerseyDeocodesPathAndQueryParams() {
        when(resource.encoded(any(), any())).thenReturn("Hello, world");
        jerseyProxy.encoded(PATH_PARAM, QUERY_PARAM);
        verify(resource).encoded(PATH_PARAM, QUERY_PARAM);
    }

    @Test
    public void testPathAndQueryParamsAreEncoded() throws InterruptedException {
        inMemoryProxy.encoded(PATH_PARAM, QUERY_PARAM);
        RecordedRequest request = server.takeRequest();
        // See https://www.ietf.org/rfc/rfc3986.txt section 3.4: Query
        // The characters slash ("/") and question mark ("?") may represent data within the query component.
        assertThat(request.getPath()).isEqualTo("/path/slash%2Fpath?query=slash/query");
    }

    @Path("/")
    public interface Service {
        @GET
        @Path("path/{path}")
        String encoded(@PathParam("path") String path, @QueryParam("query") String query);
    }

    public static class Server extends Application<Configuration> {
        @Override
        public final void run(Configuration _config, final Environment env) throws Exception {
            env.jersey().register(resource);
        }
    }
}
