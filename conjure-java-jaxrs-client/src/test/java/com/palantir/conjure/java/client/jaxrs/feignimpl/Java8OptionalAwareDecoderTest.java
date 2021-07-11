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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableMap;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.client.jaxrs.TestBase;
import com.palantir.conjure.java.client.jaxrs.feignimpl.Java8TestServer.TestService;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.ExpectedException;

public final class Java8OptionalAwareDecoderTest extends TestBase {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(Java8TestServer.class, "src/test/resources/test-server.yml");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Java8TestServer.TestService service;

    @BeforeEach
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service =
                JaxRsClient.create(TestService.class, AGENT, new HostMetricsRegistry(), createTestConfig(endpointUri));
    }

    @Test
    public void testOptional() {
        assertThat(service.getOptional("something")).hasValue(ImmutableMap.of("something", "something"));
        assertThat(service.getOptional(null)).isEmpty();
    }

    @Test
    public void testNonOptional() {
        assertThat(service.getNonOptional("something")).isEqualTo(ImmutableMap.of("something", "something"));
        assertThat(service.getNonOptional(null)).isEmpty();
    }

    @Test
    public void testThrowsNotFound() {
        assertThatThrownBy(() -> service.getThrowsNotFound(null)).isInstanceOfSatisfying(RemoteException.class, e -> {
            assertThat(e.getMessage()).contains("RemoteException: NOT_FOUND (Default:NotFound)");
            assertThat(e.getError().errorCode()).isEqualTo("NOT_FOUND");
        });
    }

    @Test
    public void testThrowsNotAuthorized() {
        assertThatThrownBy(() -> service.getThrowsNotAuthorized(null))
                .isInstanceOfSatisfying(RemoteException.class, e -> {
                    assertThat(e.getMessage()).contains("RemoteException: UNAUTHORIZED (Default:Unauthorized)");
                    assertThat(e.getError().errorCode()).isEqualTo("UNAUTHORIZED");
                });
    }

    @Test
    public void testOptionalThrowsNotAuthorized() {
        assertThatThrownBy(() -> service.getOptionalThrowsNotAuthorized(null))
                .isInstanceOfSatisfying(RemoteException.class, e -> {
                    assertThat(e.getMessage()).contains("RemoteException: UNAUTHORIZED (Default:Unauthorized)");
                    assertThat(e.getError().errorCode()).isEqualTo("UNAUTHORIZED");
                });
    }

    @Test
    public void testThrowsFordidden() {
        assertThatThrownBy(() -> service.getThrowsForbidden(null)).isInstanceOfSatisfying(RemoteException.class, e -> {
            assertThat(e.getMessage()).contains("RemoteException: PERMISSION_DENIED (Default:PermissionDenied)");
            assertThat(e.getError().errorCode()).isEqualTo("PERMISSION_DENIED");
        });
    }

    @Test
    public void testOptionalThrowsFordidden() {
        assertThatThrownBy(() -> service.getOptionalThrowsForbidden(null))
                .isInstanceOfSatisfying(RemoteException.class, e -> {
                    assertThat(e.getMessage())
                            .contains("RemoteException: PERMISSION_DENIED (Default:PermissionDenied)");
                    assertThat(e.getError().errorCode()).isEqualTo("PERMISSION_DENIED");
                });
    }

    @Test
    public void testOptionalString() {
        assertThat(service.getOptionalString(null)).isEmpty();
        assertThat(service.getOptionalString("foo")).hasValue("foo");
    }

    @Test
    public void testComplexType() {
        Java8ComplexType value = new Java8ComplexType(
                Optional.of(new Java8ComplexType(Optional.empty(), Optional.empty(), Paths.get("bar"))),
                Optional.of("baz"),
                Paths.get("foo"));
        // Hint: set breakpoint in Feign's SynchronousMethodHandler#executeAndDecode to inspect serialized parameter.
        assertThat(service.getJava8ComplexType(value)).isEqualTo(value);
    }
}
