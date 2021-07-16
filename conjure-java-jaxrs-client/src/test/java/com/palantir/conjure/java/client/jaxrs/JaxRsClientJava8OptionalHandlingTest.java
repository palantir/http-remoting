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
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class JaxRsClientJava8OptionalHandlingTest extends TestBase {

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
        String path(@PathParam("opt") Optional<String> opt, @PathParam("req") String req);
    }

    @Path("/")
    public interface Service {
        @GET
        @Path("foo/{req}")
        String path(@PathParam("req") String req);

        @GET
        @Path("foo")
        String query(@QueryParam("opt") Optional<String> opt, @QueryParam("req") String req);

        @GET
        @Path("foo")
        String header(@HeaderParam("opt") Optional<String> opt, @HeaderParam("req") String req);

        @GET
        @Path("foo/int")
        String queryInt(@QueryParam("opt") OptionalInt opt, @QueryParam("req") String req);

        @GET
        @Path("foo/int")
        String headerInt(@HeaderParam("opt") OptionalInt opt, @HeaderParam("req") String req);

        @GET
        @Path("foo/double")
        String queryDouble(@QueryParam("opt") OptionalDouble opt, @QueryParam("req") String req);

        @GET
        @Path("foo/double")
        String headerDouble(@HeaderParam("opt") OptionalDouble opt, @HeaderParam("req") String req);

        @GET
        @Path("foo/long")
        String queryLong(@QueryParam("opt") OptionalLong opt, @QueryParam("req") String req);

        @GET
        @Path("foo/long")
        String headerLong(@HeaderParam("opt") OptionalLong opt, @HeaderParam("req") String req);
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
                    .isEqualTo("Cannot use Java8 Optionals with PathParams. (Class: com.palantir.conjure."
                            + "java.client.jaxrs.JaxRsClientJava8OptionalHandlingTest$CannotDecorateInterface,"
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
        proxy.query(Optional.empty(), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /foo?req=str2 HTTP/1.1");
    }

    @Test
    public void testEmptyStringQuery() throws Exception {
        proxy.query(Optional.of(""), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /foo?opt=&req=str2 HTTP/1.1");
    }

    @Test
    public void testStringQuery() throws Exception {
        proxy.query(Optional.of("str"), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /foo?opt=str&req=str2 HTTP/1.1");
    }

    @Test
    public void testAbsentHeader() throws Exception {
        proxy.header(Optional.empty(), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt")).isEmpty();
        assertThat(takeRequest.getHeader("req")).isEqualTo("str2");
    }

    @Test
    public void testEmptyStringHeader() throws Exception {
        proxy.header(Optional.of(""), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt")).isEmpty();
        assertThat(takeRequest.getHeader("req")).isEqualTo("str2");
    }

    @Test
    public void testStringHeader() throws Exception {
        proxy.header(Optional.of("str"), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt")).isEqualTo("str");
        assertThat(takeRequest.getHeader("req")).isEqualTo("str2");
    }

    @Ignore("TODO(rfink): Add support for header encoding")
    @Test
    public void testStringHeader_withNonAsciiCharacters() throws Exception {
        proxy.header(Optional.of("ü"), "ø");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt")).isEqualTo("ü");
        assertThat(takeRequest.getHeader("req")).isEqualTo("ø");
    }

    @Test
    public void testAbsentIntQuery() throws Exception {
        proxy.queryInt(OptionalInt.empty(), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /foo/int?req=str2 HTTP/1.1");
    }

    @Test
    public void testIntQuery() throws Exception {
        proxy.queryInt(OptionalInt.of(1234), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /foo/int?opt=1234&req=str2 HTTP/1.1");
    }

    @Test
    public void testAbsentIntHeader() throws Exception {
        proxy.headerInt(OptionalInt.empty(), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt")).isEmpty();
        assertThat(takeRequest.getHeader("req")).isEqualTo("str2");
    }

    @Test
    public void testIntHeader() throws Exception {
        proxy.headerInt(OptionalInt.of(1234), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt")).isEqualTo("1234");
        assertThat(takeRequest.getHeader("req")).isEqualTo("str2");
    }

    @Test
    public void testAbsentDoubleQuery() throws Exception {
        proxy.queryDouble(OptionalDouble.empty(), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /foo/double?req=str2 HTTP/1.1");
    }

    @Test
    public void testDoubleQuery() throws Exception {
        proxy.queryDouble(OptionalDouble.of(1234.567), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /foo/double?opt=1234.567&req=str2 HTTP/1.1");
    }

    @Test
    public void testAbsentDoubleHeader() throws Exception {
        proxy.headerDouble(OptionalDouble.empty(), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt")).isEmpty();
        assertThat(takeRequest.getHeader("req")).isEqualTo("str2");
    }

    @Test
    public void testDoubleHeader() throws Exception {
        proxy.headerDouble(OptionalDouble.of(1234.567), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt")).isEqualTo("1234.567");
        assertThat(takeRequest.getHeader("req")).isEqualTo("str2");
    }

    @Test
    public void testAbsentLongQuery() throws Exception {
        proxy.queryLong(OptionalLong.empty(), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /foo/long?req=str2 HTTP/1.1");
    }

    @Test
    public void testLongQuery() throws Exception {
        proxy.queryLong(OptionalLong.of(12345678901234L), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /foo/long?opt=12345678901234&req=str2 HTTP/1.1");
    }

    @Test
    public void testAbsentLongHeader() throws Exception {
        proxy.headerLong(OptionalLong.empty(), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt")).isEmpty();
        assertThat(takeRequest.getHeader("req")).isEqualTo("str2");
    }

    @Test
    public void testLongHeader() throws Exception {
        proxy.headerLong(OptionalLong.of(12345678901234L), "str2");
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getHeader("opt")).isEqualTo("12345678901234");
        assertThat(takeRequest.getHeader("req")).isEqualTo("str2");
    }
}
