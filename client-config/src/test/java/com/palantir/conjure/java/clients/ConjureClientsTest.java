/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.clients;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class ConjureClientsTest {

    private static final Map<String, String> INTENTIONALLY_EXCLUDED = ImmutableMap.<String, String>builder()
            .put("retryOnSocketException", "Not implementing this until we get a request")
            .put("taggedMetricRegistry", "Already handled by the withTaggedMetrics() method")
            .put("meshProxy", "ClientConfigurations.of sets this up automatically")
            .put("proxyCredentials", "ClientConfigurations.of sets this up automatically")
            .put("sslSocketFactory", "Not expecting users to override these")
            .put("trustManager", "Not expecting users to override these")
            .build();

    @Test
    public void check_WithClientOptions_is_in_sync_with_ClientConfiguration() {
        Set<String> ymlOptions = methodNames(ServiceConfiguration.class);
        Set<String> clientConf = methodNames(ClientConfiguration.class);
        Set<String> withMethods = methodNames(ConjureClients.WithClientOptions.class);

        Sets.SetView<String> onlyConfigurableFromJava = Sets.difference(clientConf, ymlOptions);
        List<String> unexposedOptions = onlyConfigurableFromJava.stream()
                .filter(m -> !withMethods.contains("with" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, m)))
                .filter(m -> !INTENTIONALLY_EXCLUDED.containsKey(m))
                .collect(Collectors.toList());

        assertThat(unexposedOptions)
                .describedAs("Most ClientConfiguration fields can be set from "
                        + "YAML in the ServiceConfiguration class, but any *additional* ones must be also settable "
                        + "from the ConjureClients facade, unless there's a good reason to exclude them.")
                .isEmpty();
    }

    private static Set<String> methodNames(Class<?> clazz) {
        return Arrays.stream(clazz.getMethods())
                .filter(m -> !Modifier.isStatic(m.getModifiers()) && !m.isDefault())
                .map(Method::getName)
                .collect(Collectors.toSet());
    }
}
