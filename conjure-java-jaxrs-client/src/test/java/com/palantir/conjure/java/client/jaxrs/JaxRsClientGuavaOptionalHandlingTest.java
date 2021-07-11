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

package com.palantir.conjure.java.client.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class JaxRsClientGuavaOptionalHandlingTest extends TestBase {

    @Rule
    public final MockWebServer server = new MockWebServer();

    private Service proxy;

    @BeforeEach
    public void before() {
        proxy = JaxRsClient.create(
                Service.class,
                AGENT,
                new HostMetricsRegistry(),
                createTestConfig("http://localhost:" + server.getPort()));
        server.enqueue(new MockResponse().setBody("\"foo\""));
    }

    @Path("/")
    public interface CannotDecorateInterface {
        @GET
        @Path("{opt}/foo/{req}")
        String path(@PathParam("opt") com.google.common.base.Optional<String> opt, @PathParam("req") String req);
    }

    @Path("/")
    public interface Service {
        @GET
        @Path("foo/{req}")
        String path(@PathParam("req") String req);

        @GET
        @Path("foo")
        String query(@QueryParam("opt") com.google.common.base.Optional<String> opt, @QueryParam("req") String req);

        @GET
        @Path("foo")
        String header(@HeaderParam("opt") com.google.common.base.Optional<String> opt, @HeaderParam("req") String req);
    }

    @Test
    public void testCannotDecorateInterfaceWithOptionalPathParam() {
        try {
            JaxRsClient.create(
                    CannotDecorateInterface.class,
                    AGENT,
                    new HostMetricsRegistry(),
                    createTestConfig("http://localhost:" + server.getPort()));
            fail("fail");
        } catch (RuntimeException e) {
            assertThat(e.getMessage())
                    .isEqualTo("Cannot use Guava Optionals with PathParams. (Class: com.palantir.conjure"
                            + ".java.client.jaxrs.JaxRsClientGuavaOptionalHandlingTest$CannotDecorateInterface,"
                            + " Method: path, Param: arg0)");
        }
    }

    @Test
    public void testRegularPathParam() throws Exception {
        proxy.path("str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getPath()).isEqualTo("/foo/str2");
    }

    @Test
    public void testAbsentQuery() throws Exception {
        proxy.query(com.google.common.base.Optional.absent(), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /foo?req=str2 HTTP/1.1");
    }

    @Test
    public void testEmptyStringQuery() throws Exception {
        proxy.query(com.google.common.base.Optional.of(""), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /foo?opt=&req=str2 HTTP/1.1");
    }

    @Test
    public void testStringQuery() throws Exception {
        proxy.query(com.google.common.base.Optional.of("str"), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /foo?opt=str&req=str2 HTTP/1.1");
    }

    @Test
    public void testAbsentHeader() throws Exception {
        proxy.header(com.google.common.base.Optional.absent(), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt")).isEmpty();
        assertThat(takeRequest.getHeader("req")).isEqualTo("str2");
    }

    @Test
    public void testEmptyStringHeader() throws Exception {
        proxy.header(com.google.common.base.Optional.of(""), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt")).isEmpty();
        assertThat(takeRequest.getHeader("req")).isEqualTo("str2");
    }

    @Test
    public void testStringHeader() throws Exception {
        proxy.header(com.google.common.base.Optional.of("str"), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt")).isEqualTo("str");
        assertThat(takeRequest.getHeader("req")).isEqualTo("str2");
    }
}
