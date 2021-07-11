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

package com.palantir.conjure.java.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Paths;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.Test;

public class KeepAliveSslSocketFactoryTest {

    @Test
    public void sslSocketFactory_has_keepalives_enabled() throws IOException {
        SSLSocketFactory sslSocketFactory = SslSocketFactories.createSslSocketFactory(
                SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks")));
        KeepAliveSslSocketFactory keepalive = new KeepAliveSslSocketFactory(sslSocketFactory);
        try (Socket socket = keepalive.createSocket("google.com", 443)) {
            assertThat(socket.getKeepAlive()).describedAs("keepAlives enabled").isTrue();
        }
    }
}
