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

import com.google.common.base.Strings;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.ClassRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class Java8OptionalTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(OptionalTestServer.class, "src/test/resources/test-server.yml");

    private WebTarget target;

    @BeforeEach
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);
    }

    @Test
    public void testOptionalPresent() throws NoSuchMethodException, SecurityException {
        Response response =
                target.path("optional").queryParam("value", "val").request().get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("valval");
    }

    @Test
    public void testOptionalAbsent() {
        Response response = target.path("optional").request().get();
        assertThat(response.getStatus()).isEqualTo(Status.NO_CONTENT.getStatusCode());
    }

    @Test
    public void testQueryParam_optionalPresent() throws NoSuchMethodException, SecurityException {
        Response response = target.path("optional/string")
                .queryParam("value", "val")
                .request()
                .get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("val");
    }

    @Test
    public void testQueryParam_optionalEmpty() {
        Response response = target.path("optional/string").request().get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("default");
    }

    @Test
    public void testQueryParam_optionalIntPresent() throws NoSuchMethodException, SecurityException {
        Response response =
                target.path("optional/int").queryParam("value", "10").request().get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("10");
    }

    @Test
    public void testQueryParam_optionalIntEmpty() {
        Response response = target.path("optional/int").request().get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("0");
    }

    @Test
    public void testQueryParam_optionalIntInvalid() {
        Response response =
                target.path("optional/int").queryParam("value", "foo").request().get();
        assertThat(response.getStatus()).isEqualTo(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void testQueryParam_optionalDoublePresent() throws NoSuchMethodException, SecurityException {
        Response response = target.path("optional/double")
                .queryParam("value", "1.5")
                .request()
                .get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("1.5");
    }

    @Test
    public void testQueryParam_optionalDoubleEmpty() {
        Response response = target.path("optional/double").request().get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("0.0");
    }

    @Test
    public void testQueryParam_optionalDoubleInvalid() {
        Response response = target.path("optional/double")
                .queryParam("value", "foo")
                .request()
                .get();
        assertThat(response.getStatus()).isEqualTo(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    public void testQueryParam_optionalLongPresent() throws NoSuchMethodException, SecurityException {
        Response response = target.path("optional/long")
                .queryParam("value", "100")
                .request()
                .get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("100");
    }

    @Test
    public void testQueryParam_optionalLongEmpty() {
        Response response = target.path("optional/long").request().get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("0");
    }

    @Test
    public void testQueryParam_optionalLongInvalid() {
        Response response = target.path("optional/long")
                .queryParam("value", "foo")
                .request()
                .get();
        assertThat(response.getStatus()).isEqualTo(Status.BAD_REQUEST.getStatusCode());
    }

    public static class OptionalTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration _config, final Environment env) throws Exception {
            env.jersey().register(ConjureJerseyFeature.INSTANCE);
            env.jersey().register(new EmptyOptionalTo204ExceptionMapper());
            env.jersey().register(new OptionalTestResource());
        }
    }

    public static final class OptionalTestResource implements OptionalTestService {
        @Override
        public Optional<String> getOptional(@Nullable String value) {
            if (Strings.isNullOrEmpty(value)) {
                return Optional.empty();
            } else {
                return Optional.of(value + value);
            }
        }

        @Override
        public String getWithOptionalQueryParam(Optional<String> string) {
            return string.orElse("default");
        }

        @Override
        public int getWithOptionalIntQueryParam(OptionalInt value) {
            return value.orElse(0);
        }

        @Override
        public double getWithOptionalDoubleQueryParam(OptionalDouble value) {
            return value.orElse(0.0);
        }

        @Override
        public long getWithOptionalLongQueryParam(OptionalLong value) {
            return value.orElse(0L);
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface OptionalTestService {
        @GET
        @Path("/optional")
        Optional<String> getOptional(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/optional/string")
        String getWithOptionalQueryParam(@QueryParam("value") Optional<String> string);

        @GET
        @Path("/optional/int")
        int getWithOptionalIntQueryParam(@QueryParam("value") OptionalInt value);

        @GET
        @Path("/optional/double")
        double getWithOptionalDoubleQueryParam(@QueryParam("value") OptionalDouble value);

        @GET
        @Path("/optional/long")
        long getWithOptionalLongQueryParam(@QueryParam("value") OptionalLong value);
    }
}
