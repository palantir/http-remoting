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

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.ClassRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class DeprecationTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(DeprecationTestServer.class, "src/test/resources/test-server.yml");

    private WebTarget target;

    @BeforeEach
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);
    }

    @Test
    public void testDeprecated() throws SecurityException {
        try (Response response = target.path("deprecated").request().get()) {
            assertThat(response.getStatus()).isEqualTo(Status.NO_CONTENT.getStatusCode());
            assertThat(response.getHeaderString("deprecation")).isEqualTo("true");
        }
    }

    @Test
    public void testUnmarked() throws SecurityException {
        try (Response response = target.path("unmarked").request().get()) {
            assertThat(response.getStatus()).isEqualTo(Status.NO_CONTENT.getStatusCode());
            assertThat(response.getHeaderString("deprecation")).isNull();
        }
    }

    public static class DeprecationTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration _config, final Environment env) {
            env.jersey().register(ConjureJerseyFeature.INSTANCE);
            env.jersey().register(new DeprecationResource());
        }
    }

    public static final class DeprecationResource implements DeprecationTestService {
        @Override
        public void deprecated() {}

        @Override
        public void unmarked() {}
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface DeprecationTestService {
        /**
         * Deprecated endpoint.
         *
         * @deprecated for testing
         */
        @GET
        @Deprecated
        @Path("/deprecated")
        void deprecated();

        @GET
        @Path("/unmarked")
        void unmarked();
    }
}
