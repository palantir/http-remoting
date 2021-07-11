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

package com.palantir.conjure.java.server.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.ClassRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NullRequestBodyTest {
    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(TestServer.class, "src/test/resources/test-server.yml");

    private WebTarget target;

    @BeforeEach
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);
    }

    @Test
    public void testEmptyRequestBody() {
        Entity<String> empty = Entity.entity(null, MediaType.APPLICATION_JSON);

        // this endpoint does not have any annotation
        Response postResponse = target.path("post").request().post(empty);
        assertThat(postResponse.getStatus()).isEqualTo(204);

        // this endpoint has the @NotNull annotation
        Response postNotNullResponse = target.path("post-not-null").request().post(empty);
        assertThat(postNotNullResponse.getStatus()).isEqualTo(422);
    }

    @Test
    public void testExplicitlyNullRequestBody() {
        Entity<String> explicitlyNull = Entity.entity("null", MediaType.APPLICATION_JSON);

        // this endpoint does not have any annotation
        Response postResponse = target.path("post").request().post(explicitlyNull);
        assertThat(postResponse.getStatus()).isEqualTo(204);

        // this endpoint has the @NotNull annotation
        Response postNotNullResponse = target.path("post-not-null").request().post(explicitlyNull);
        assertThat(postNotNullResponse.getStatus()).isEqualTo(422);
    }

    @Test
    public void testNonNullRequestBody() {
        Entity<Map<String, String>> emptyMap = Entity.entity(ImmutableMap.of(), MediaType.APPLICATION_JSON);

        // this endpoint's handler method does not throw
        Response postResponse = target.path("post").request().post(emptyMap);
        System.out.println(postResponse);
        assertThat(postResponse.getStatus()).isEqualTo(204);

        // this endpoint's handler method throws -> 500
        Response postNotNullResponse = target.path("post-not-null").request().post(emptyMap);
        assertThat(postNotNullResponse.getStatus()).isEqualTo(500);
    }

    public static class TestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration _config, final Environment env) throws Exception {
            env.jersey().register(ConjureJerseyFeature.INSTANCE);
            env.jersey().register(new TestResource());
        }
    }

    public static final class TestResource implements TestService {
        @Override
        public void postRequestBody(Map<String, String> _data) {}

        @Override
        public void postRequestBodyNotNull(Map<String, String> _data) {
            throw new RuntimeException("oh no");
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface TestService {

        @POST
        @Path("/post")
        void postRequestBody(Map<String, String> data);

        @POST
        @Path("/post-not-null")
        void postRequestBodyNotNull(@NotNull Map<String, String> data);
    }
}
