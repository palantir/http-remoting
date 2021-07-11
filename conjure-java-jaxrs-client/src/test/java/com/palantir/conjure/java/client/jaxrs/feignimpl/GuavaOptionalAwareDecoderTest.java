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
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.nio.file.Paths;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.rules.ExpectedException;

public final class GuavaOptionalAwareDecoderTest extends TestBase {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(GuavaTestServer.class, "src/test/resources/test-server.yml");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private GuavaTestServer.TestService service;

    @BeforeEach
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service = JaxRsClient.create(
                GuavaTestServer.TestService.class, AGENT, new HostMetricsRegistry(), createTestConfig(endpointUri));
    }

    @Test
    public void testOptional() {
        assertThat(service.getOptional("something"))
                .isEqualTo(com.google.common.base.Optional.of(ImmutableMap.of("something", "something")));
        assertThat(service.getOptional(null))
                .isEqualTo(com.google.common.base.Optional.<ImmutableMap<String, String>>absent());
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
    public void testOptionalThrowsForbbbden() {
        assertThatThrownBy(() -> service.getOptionalThrowsForbidden(null))
                .isInstanceOfSatisfying(RemoteException.class, e -> {
                    assertThat(e.getMessage())
                            .contains("RemoteException: PERMISSION_DENIED (Default:PermissionDenied)");
                    assertThat(e.getError().errorCode()).isEqualTo("PERMISSION_DENIED");
                });
    }

    @Test
    public void testOptionalString() {
        assertThat(service.getOptionalString(null)).isEqualTo(com.google.common.base.Optional.absent());
        assertThat(service.getOptionalString("foo")).isEqualTo(com.google.common.base.Optional.of("foo"));
    }

    @Test
    public void testComplexType() {
        GuavaOptionalComplexType value = new GuavaOptionalComplexType(
                com.google.common.base.Optional.of(new GuavaOptionalComplexType(
                        com.google.common.base.Optional.absent(),
                        com.google.common.base.Optional.absent(),
                        Paths.get("bar"))),
                com.google.common.base.Optional.of("baz"),
                Paths.get("foo"));
        // Hint: set breakpoint in Feign's SynchronousMethodHandler#executeAndDecode to inspect serialized parameter.
        assertThat(service.getGuavaComplexType(value)).isEqualTo(value);
    }

    @Test
    public void testCborResponse() {
        GuavaOptionalComplexType value = new GuavaOptionalComplexType(
                com.google.common.base.Optional.of(new GuavaOptionalComplexType(
                        com.google.common.base.Optional.absent(),
                        com.google.common.base.Optional.absent(),
                        Paths.get("bar"))),
                com.google.common.base.Optional.of("baz"),
                Paths.get("foo"));
        assertThat(service.getCborResponse(value)).isEqualTo(value);
    }

    @Test
    public void testCborRequest() {
        GuavaOptionalComplexType value = new GuavaOptionalComplexType(
                com.google.common.base.Optional.of(new GuavaOptionalComplexType(
                        com.google.common.base.Optional.absent(),
                        com.google.common.base.Optional.absent(),
                        Paths.get("bar"))),
                com.google.common.base.Optional.of("baz"),
                Paths.get("foo"));
        assertThat(service.postCborRequest(value)).isEqualTo(value);
    }
}
