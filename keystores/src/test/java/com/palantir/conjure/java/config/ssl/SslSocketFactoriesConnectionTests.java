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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.google.common.base.Throwables;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.rules.Timeout;

public final class SslSocketFactoriesConnectionTests {

    private enum ClientAuth {
        WITH_CLIENT_AUTH,
        NO_CLIENT_AUTH,
    }

    @Rule
    public Timeout testTimeout = Timeout.seconds(5);

    @Test
    public void testSslNoClientAuthenticationJks() {
        SslConfiguration serverConfig = SslConfiguration.of(
                TestConstants.CA_TRUST_STORE_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);

        SslConfiguration clientConfig = SslConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.NO_CLIENT_AUTH);
    }

    @Test
    public void testSslNoClientAuthenticationX509() {
        SslConfiguration serverConfig = SslConfiguration.of(
                TestConstants.CA_TRUST_STORE_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);

        SslConfiguration clientConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_PEM_CERT_PATH)
                .trustStoreType(SslConfiguration.StoreType.PEM)
                .build();

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.NO_CLIENT_AUTH);
    }

    @Test
    public void testSslNoClientAuthenticationPkcs12() {
        SslConfiguration serverConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_P12_PATH)
                .keyStoreType(TestConstants.SERVER_KEY_STORE_P12_TYPE)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_P12_PASSWORD)
                .build();

        SslConfiguration clientConfig = SslConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.NO_CLIENT_AUTH);
    }

    @Test
    public void testSslNoClientAuthenticationFailsWithoutProperClientTrustStore() {
        SslConfiguration serverConfig = SslConfiguration.of(
                TestConstants.CA_TRUST_STORE_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);

        // bad configuration: client trust store does not contain any certificates
        // that can verify the server certificate
        SslConfiguration clientConfig = SslConfiguration.of(TestConstants.CLIENT_KEY_STORE_JKS_PATH);

        assertThatThrownBy(() -> {
                    runSslConnectionTest(serverConfig, clientConfig, ClientAuth.NO_CLIENT_AUTH);
                    fail("fail");
                })
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(SSLHandshakeException.class)
                .hasMessageContaining("PKIX path building failed");
    }

    @Test
    public void testSslWithNoClientAuthenticationWorksWithoutProperServerTrustStore() {
        // if no client authentication is present, doesn't matter that server trust store
        // cannot verify any certificates
        SslConfiguration serverConfig = SslConfiguration.of(
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);

        SslConfiguration clientConfig = SslConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.NO_CLIENT_AUTH);
    }

    @Test
    public void testSslNoClientAuthenticationFailsWithMultipleKeyStoreSpecified() {
        // specify that server key alias should be used
        SslConfiguration serverConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.MULTIPLE_KEY_STORE_JKS_PATH)
                .keyStorePassword(TestConstants.MULTIPLE_KEY_STORE_JKS_PASSWORD)
                .keyStoreKeyAlias(TestConstants.MULTIPLE_KEY_STORE_SERVER_ALIAS)
                .build();

        SslConfiguration clientConfig = SslConfiguration.of(TestConstants.SERVER_KEY_STORE_JKS_PATH);

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.NO_CLIENT_AUTH);
    }

    @Test
    public void testSslWithClientAuthenticationJks() {
        SslConfiguration serverConfig = SslConfiguration.of(
                TestConstants.CA_TRUST_STORE_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);

        SslConfiguration clientConfig = SslConfiguration.of(
                TestConstants.CA_TRUST_STORE_PATH,
                TestConstants.CLIENT_KEY_STORE_JKS_PATH,
                TestConstants.CLIENT_KEY_STORE_JKS_PASSWORD);

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.WITH_CLIENT_AUTH);
    }

    @Test
    public void testSslWithClientAuthenticationIntermediateCa() {
        SslConfiguration serverConfig = SslConfiguration.of(
                TestConstants.CA_TRUST_STORE_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);

        SslConfiguration clientConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.CHILD_KEY_CERT_CHAIN_PEM_PATH)
                .keyStorePassword("")
                .keyStoreType(SslConfiguration.StoreType.PEM)
                .build();

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.WITH_CLIENT_AUTH);
    }

    @Test
    public void testSslWithClientAuthenticationPkcs12() {
        SslConfiguration serverConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_P12_PATH)
                .keyStoreType(TestConstants.SERVER_KEY_STORE_P12_TYPE)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_P12_PASSWORD)
                .build();

        SslConfiguration clientConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.CLIENT_KEY_STORE_P12_PATH)
                .keyStoreType(TestConstants.CLIENT_KEY_STORE_P12_TYPE)
                .keyStorePassword(TestConstants.CLIENT_KEY_STORE_P12_PASSWORD)
                .build();

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.WITH_CLIENT_AUTH);
    }

    @Test
    public void testSslWithClientAuthenticationMixed() {
        SslConfiguration serverConfig = SslConfiguration.of(
                TestConstants.CA_TRUST_STORE_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);

        SslConfiguration clientConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.CLIENT_KEY_STORE_P12_PATH)
                .keyStoreType(TestConstants.CLIENT_KEY_STORE_P12_TYPE)
                .keyStorePassword(TestConstants.CLIENT_KEY_STORE_P12_PASSWORD)
                .build();

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.WITH_CLIENT_AUTH);
    }

    @Test
    public void testSslWithClientAuthenticationFailsWithoutClientKeyStore() {
        SslConfiguration serverConfig = SslConfiguration.of(
                TestConstants.CA_TRUST_STORE_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);

        // bad configuration: client does not specify a key store for a connection
        // that requires client authentication
        SslConfiguration clientConfig = SslConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);

        assertThatThrownBy(() -> {
                    runSslConnectionTest(serverConfig, clientConfig, ClientAuth.WITH_CLIENT_AUTH);
                })
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(SSLHandshakeException.class)
                .hasMessageContaining("bad_certificate");
    }

    @Test
    public void testSslWithClientAuthenticationFailsWithoutProperServerTrustStore() {
        // bad configuration: server trust store does not contain any certificates
        // that can verify the client certificate
        SslConfiguration serverConfig = SslConfiguration.of(
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);

        SslConfiguration clientConfig = SslConfiguration.of(
                TestConstants.CA_TRUST_STORE_PATH,
                TestConstants.CLIENT_KEY_STORE_JKS_PATH,
                TestConstants.CLIENT_KEY_STORE_JKS_PASSWORD);

        assertThatThrownBy(() -> {
                    runSslConnectionTest(serverConfig, clientConfig, ClientAuth.WITH_CLIENT_AUTH);
                })
                .isInstanceOfSatisfying(RuntimeException.class, ex -> {
                    if (System.getProperty("java.version").startsWith("1.8")) {
                        assertThat(ex)
                                .hasCauseInstanceOf(SSLHandshakeException.class)
                                .hasMessageContaining("bad_certificate");
                    } else {
                        assertThat(ex.getCause()).isInstanceOfAny(SSLException.class, SSLHandshakeException.class);
                        assertThat(ex.getMessage())
                                .satisfiesAnyOf(
                                        message -> assertThat(message).contains("readHandshakeRecord"),
                                        message -> assertThat(message).contains("certificate_unknown"));
                    }
                });
    }

    private void runSslConnectionTest(
            SslConfiguration serverConfig, SslConfiguration clientConfig, ClientAuth clientAuth) {
        String message = UUID.randomUUID().toString();

        SSLContext sslContext = SslSocketFactories.createSslContext(serverConfig);
        SSLServerSocketFactory factory = sslContext.getServerSocketFactory();

        try (SSLServerSocket sslServerSocket = (SSLServerSocket) factory.createServerSocket(0)) {
            Thread serverThread = createSslServerThread(sslServerSocket, clientAuth, message);
            serverThread.start();

            SSLSocketFactory sslSocketFactory = SslSocketFactories.createSslSocketFactory(clientConfig);
            verifySslConnection(sslSocketFactory, sslServerSocket.getLocalPort(), message);
        } catch (IOException ex) {
            Throwables.propagate(ex);
        }
    }

    /**
     * Verify that an SSL connection can be established. Creates an SSL socket with the provided
     * {@link SSLSocketFactory} that connect to localhost on the specified port and waits for expectedMessage to be sent
     * by the server.
     */
    private void verifySslConnection(SSLSocketFactory factory, int port, String expectedMessage) {
        try (Socket clientSocket = factory.createSocket("localhost", port)) {
            BufferedReader in =
                    new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));

            String fromServer;
            while ((fromServer = in.readLine()) != null) {
                assertThat(fromServer).isEqualTo(expectedMessage);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return a {@link Thread} that accepts an incoming connection on the provided {@link SSLServerSocket}. If a
     * connection is established, the specified message is sent to the client and the socket is closed.
     */
    private Thread createSslServerThread(
            final SSLServerSocket sslServerSocket, final ClientAuth clientAuth, final String message) {
        Runnable serverThread = new Runnable() {
            @Override
            public void run() {
                try {
                    sslServerSocket.setNeedClientAuth(clientAuth == ClientAuth.WITH_CLIENT_AUTH);
                    sslServerSocket.setReuseAddress(true);
                    Socket clientSocket = sslServerSocket.accept();

                    OutputStreamWriter streamWriter =
                            new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8);

                    PrintWriter out = new PrintWriter(streamWriter, true);
                    out.println(message);
                    clientSocket.close();
                } catch (IOException e) {
                    Throwables.propagate(e);
                }
            }
        };

        return new Thread(serverThread);
    }
}
