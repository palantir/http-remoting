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

package com.palantir.conjure.java.server.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import com.codahale.metrics.MetricRegistry;
import com.google.common.net.HttpHeaders;
import io.dropwizard.jersey.DropwizardResourceConfig;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.jupiter.api.Test;

public final class Java8OptionalMessageBodyWriterTest extends JerseyTest {

    @Override
    protected Application configure() {
        forceSet(TestProperties.CONTAINER_PORT, "0");
        return DropwizardResourceConfig.forTesting(new MetricRegistry())
                .register(ConjureJerseyFeature.INSTANCE)
                .register(new EmptyOptionalTo204ExceptionMapper())
                .register(OptionalReturnResource.class);
    }

    @Test
    public void presentOptionalsReturnTheirValue() throws Exception {
        assertThat(target("/optional-return/").queryParam("id", "woo").request().get(String.class))
                .isEqualTo("woo");

        assertThat(target("/optional-return/int")
                        .queryParam("id", "123")
                        .request()
                        .get(String.class))
                .isEqualTo("123");

        assertThat(target("/optional-return/long")
                        .queryParam("id", "1234567890123")
                        .request()
                        .get(String.class))
                .isEqualTo("1234567890123");

        assertThat(target("/optional-return/double")
                        .queryParam("id", "123.456")
                        .request()
                        .get(String.class))
                .isEqualTo("123.456");

        try (Response binaryResponse = target("/optional-return/binary")
                .queryParam("id", "woo")
                .request()
                .get()) {
            assertThat(binaryResponse.getHeaderString(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/octet-stream");
            assertThat(binaryResponse.readEntity(String.class)).isEqualTo("woo");
        }
    }

    @Test
    public void absentOptionalsThrowANotFound() throws Exception {
        Response response = target("/optional-return/").request().get();
        assertThat(response.getStatus()).isEqualTo(204);

        response = target("/optional-return/int").request().get();
        assertThat(response.getStatus()).isEqualTo(204);

        response = target("/optional-return/long").request().get();
        assertThat(response.getStatus()).isEqualTo(204);

        response = target("/optional-return/double").request().get();
        assertThat(response.getStatus()).isEqualTo(204);

        response = target("/optional-return/binary").request().get();
        assertThat(response.getStatus()).isEqualTo(204);
    }

    @Path("/optional-return/")
    @Produces(MediaType.TEXT_PLAIN)
    public static final class OptionalReturnResource {
        @GET
        public Optional<String> showWithQueryParam(@QueryParam("id") String id) {
            return Optional.ofNullable(id);
        }

        @POST
        public Optional<String> showWithFormParam(@FormParam("id") String id) {
            return Optional.ofNullable(id);
        }

        @Path("int")
        @GET
        public OptionalInt showOptIntWithQueryParam(@QueryParam("id") Integer id) {
            return id != null ? OptionalInt.of(id) : OptionalInt.empty();
        }

        @Path("long")
        @GET
        public OptionalLong showOptLongWithQueryParam(@QueryParam("id") Long id) {
            return id != null ? OptionalLong.of(id) : OptionalLong.empty();
        }

        @Path("double")
        @GET
        public OptionalDouble showOptDoubleWithQueryParam(@QueryParam("id") Double id) {
            return id != null ? OptionalDouble.of(id) : OptionalDouble.empty();
        }

        @Path("binary")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        @GET
        public Optional<StreamingOutput> showOptionalBinaryWithQueryParam(@QueryParam("id") String id) {
            return Optional.ofNullable(id)
                    .map(str -> str.getBytes(StandardCharsets.UTF_8))
                    .map(bytes -> output -> output.write(bytes));
        }
    }
}
