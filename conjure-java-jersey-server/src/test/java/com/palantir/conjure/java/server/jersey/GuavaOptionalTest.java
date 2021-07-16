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

public final class GuavaOptionalTest {

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
    public void testOptionalPresent() throws SecurityException {
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

    public static class OptionalTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration _config, final Environment env) {
            env.jersey().register(ConjureJerseyFeature.INSTANCE);
            env.jersey().register(new OptionalTestResource());
        }
    }

    public static final class OptionalTestResource implements OptionalTestService {
        @Override
        public com.google.common.base.Optional<String> getOptional(@Nullable String value) {
            if (Strings.isNullOrEmpty(value)) {
                return com.google.common.base.Optional.absent();
            } else {
                return com.google.common.base.Optional.of(value + value);
            }
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface OptionalTestService {
        @GET
        @Path("/optional")
        com.google.common.base.Optional<String> getOptional(@QueryParam("value") @Nullable String value);
    }
}
