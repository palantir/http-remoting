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

package com.palantir.conjure.java.client.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.okhttp.NoOpHostEventsSink;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class JaxRsClientPathParamHandlingTest extends TestBase {

    @Rule
    public final MockWebServer server = new MockWebServer();

    private Service client;

    @BeforeEach
    public void before() {
        client = JaxRsClient.create(
                Service.class,
                AGENT,
                NoOpHostEventsSink.INSTANCE,
                createTestConfig("http://localhost:" + server.getPort() + "/api"));
        MockResponse mockResponse = new MockResponse().setResponseCode(200);
        server.enqueue(mockResponse);
    }

    @Path("/")
    public interface Service {

        @GET
        @Path("complex/{path:.*}")
        void complexPath(@PathParam("path") String path);

        @GET
        @Path("begin/{path}/end")
        void innerPath(@PathParam("path") String path);

        @GET
        void simple();
    }

    @Test
    public void wildcardPathParameterSlashesAreEncoded() throws Exception {
        client.complexPath("foo/bar");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /api/complex/foo%2Fbar HTTP/1.1");
    }

    @Test
    public void wildcardPathParameterAllowsEmptyString() throws Exception {
        client.complexPath("");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /api/complex/ HTTP/1.1");
    }

    @Test
    public void innerPathParameterAllowsEmptyString() throws Exception {
        client.innerPath("");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /api/begin//end HTTP/1.1");
    }

    @Test
    public void simplestPath() throws Exception {
        client.simple();
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /api HTTP/1.1");
    }
}
