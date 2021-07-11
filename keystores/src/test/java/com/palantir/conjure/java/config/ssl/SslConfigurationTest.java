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

package com.palantir.conjure.java.config.ssl;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.serialization.ObjectMappers;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

public final class SslConfigurationTest {

    @Test
    public void testSerDe() throws Exception {
        SslConfiguration serialized = SslConfiguration.builder()
                .trustStorePath(Paths.get("truststore.jks"))
                .trustStoreType(SslConfiguration.StoreType.JKS)
                .keyStorePath(Paths.get("keystore.jks"))
                .keyStoreType(SslConfiguration.StoreType.JKS)
                .keyStoreKeyAlias("alias")
                .keyStorePassword("password")
                .build();
        String deserializedCamelCase = "{\"trustStorePath\":\"truststore.jks\",\"trustStoreType\":\"JKS\","
                + "\"keyStorePath\":\"keystore.jks\",\"keyStorePassword\":\"password\","
                + "\"keyStoreType\":\"JKS\",\"keyStoreKeyAlias\":\"alias\"}";
        String deserializedKebabCase = "{\"trust-store-path\":\"truststore.jks\",\"trust-store-type\":\"JKS\","
                + "\"key-store-path\":\"keystore.jks\",\"key-store-password\":\"password\","
                + "\"key-store-type\":\"JKS\",\"key-store-key-alias\":\"alias\"}";

        assertThat(ObjectMappers.newClientObjectMapper().writeValueAsString(serialized))
                .isEqualTo(deserializedCamelCase);
        assertThat(ObjectMappers.newClientObjectMapper().readValue(deserializedCamelCase, SslConfiguration.class))
                .isEqualTo(serialized);
        assertThat(ObjectMappers.newClientObjectMapper().readValue(deserializedKebabCase, SslConfiguration.class))
                .isEqualTo(serialized);
    }

    @Test
    public void serDe_optional() throws Exception {
        SslConfiguration serialized = SslConfiguration.of(Paths.get("trustStore.jks"));
        String deserializedCamelCase = "{\"trustStorePath\":\"trustStore.jks\",\"trustStoreType\":\"JKS\","
                + "\"keyStorePath\":null,\"keyStorePassword\":null,\"keyStoreType\":\"JKS\",\"keyStoreKeyAlias\":null}";
        String deserializedKebabCase = "{\"trust-store-path\":\"trustStore.jks\",\"trust-store-type\":\"JKS\","
                + "\"key-store-path\":null,\"key-store-password\":null,\"key-store-type\":\"JKS\","
                + "\"key-store-key-alias\":null}";

        assertThat(ObjectMappers.newClientObjectMapper().writeValueAsString(serialized))
                .isEqualTo(deserializedCamelCase);
        assertThat(ObjectMappers.newClientObjectMapper().readValue(deserializedCamelCase, SslConfiguration.class))
                .isEqualTo(serialized);
        assertThat(ObjectMappers.newClientObjectMapper().readValue(deserializedKebabCase, SslConfiguration.class))
                .isEqualTo(serialized);
    }
}
