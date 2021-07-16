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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.palantir.conjure.java.serialization.ObjectMappers;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public final class KeyStoresTests {

    @TempDir
    public File tempFolder ;

    @Test
    public void testCreateTrustStoreFromCertificateFile() throws GeneralSecurityException {
        KeyStore trustStore = KeyStores.createTrustStoreFromCertificates(TestConstants.CA_DER_CERT_PATH);

        assertThat(trustStore.size()).isEqualTo(1);
        assertThat(trustStore
                        .getCertificate(
                                TestConstants.CA_DER_CERT_PATH.getFileName().toString() + "-0")
                        .toString())
                .contains("CN=testCA");
    }

    @Test
    public void testCertificatesEqualityFromSameFile() throws GeneralSecurityException {
        String certName = TestConstants.CA_DER_CERT_PATH.getFileName().toString() + "-0";

        KeyStore trustStore = KeyStores.createTrustStoreFromCertificates(TestConstants.CA_DER_CERT_PATH);
        assertThat(trustStore.size()).isEqualTo(1);
        Certificate certificate = trustStore.getCertificate(certName);

        KeyStore secondTrustStore = KeyStores.createTrustStoreFromCertificates(TestConstants.CA_DER_CERT_PATH);
        assertThat(secondTrustStore.size()).isEqualTo(1);
        Certificate secondCertificate = secondTrustStore.getCertificate(certName);
        assertThat(certificate).isSameAs(secondCertificate);
    }

    @Test
    public void testCreateTrustStoreFromCertificateDirectory() throws GeneralSecurityException, IOException {
        File certFolder = tempFolder.newFolder();
        Files.copy(
                TestConstants.CA_DER_CERT_PATH.toFile(),
                certFolder.toPath().resolve("ca.der").toFile());
        Files.copy(
                TestConstants.SERVER_CERT_PEM_PATH.toFile(),
                certFolder.toPath().resolve("server.crt").toFile());
        Files.copy(
                TestConstants.CLIENT_CERT_PEM_PATH.toFile(),
                certFolder.toPath().resolve("client.cer").toFile());
        KeyStore trustStore = KeyStores.createTrustStoreFromCertificates(certFolder.toPath());

        assertThat(trustStore.size()).isEqualTo(3);
        assertThat(trustStore.getCertificate("ca.der-0").toString()).contains("CN=testCA");
        assertThat(trustStore.getCertificate("server.crt-0").toString()).contains("CN=localhost");
        assertThat(trustStore.getCertificate("client.cer-0").toString()).contains("CN=client");
    }

    @Test
    public void testCreateTrustStoreFromEmptyDirectory() throws GeneralSecurityException, IOException {
        File certFolder = tempFolder.newFolder();
        KeyStore trustStore = KeyStores.createTrustStoreFromCertificates(certFolder.toPath());

        assertThat(trustStore.size()).isZero();
    }

    @Test
    public void testCreateTrustStoreFromMultiCertificateFile() throws IOException, KeyStoreException {
        File certFolder = tempFolder.newFolder();
        Path caCer = certFolder.toPath().resolve("ca.cer");
        java.nio.file.Files.copy(TestConstants.SERVER_CERT_PEM_PATH, caCer);
        java.nio.file.Files.write(
                caCer, java.nio.file.Files.readAllBytes(TestConstants.SERVER_CERT_PEM_PATH), StandardOpenOption.APPEND);
        KeyStore trustStore = KeyStores.createTrustStoreFromCertificates(caCer);

        assertThat(trustStore.size()).isEqualTo(2);
    }

    @Test
    public void testCreateTrustStoreFromDirectoryIgnoresHiddenFiles() throws IOException, GeneralSecurityException {
        File certFolder = tempFolder.newFolder();
        Files.copy(
                TestConstants.CA_DER_CERT_PATH.toFile(),
                certFolder.toPath().resolve(".hidden_file").toFile());
        KeyStore trustStore = KeyStores.createTrustStoreFromCertificates(certFolder.toPath());

        assertThat(trustStore.size()).isZero();
    }

    @Test
    public void testCreateTrustStoreFromDirectoryFailsWithNonCertFiles() throws IOException {
        File certFolder = tempFolder.newFolder();
        File tempCertFile = certFolder.toPath().resolve("crl.pkcs1").toFile();
        Files.copy(TestConstants.COMBINED_CRL_PATH.toFile(), tempCertFile);

        assertThatThrownBy(() -> KeyStores.createTrustStoreFromCertificates(certFolder.toPath()))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(CertificateParsingException.class)
                .hasMessageContaining(String.format(
                        "Could not read file at \"%s\" as an X.509 certificate", tempCertFile.getAbsolutePath()));
    }

    @Test
    public void testCreateTrustStoreFromDirectoryFailsWithDirectories() throws IOException {
        File certFolder = tempFolder.newFolder();
        File tempDirFile = certFolder.toPath().resolve("childDir").toFile();
        boolean childDir = tempDirFile.mkdir();
        assertThat(childDir).isTrue();

        assertThatThrownBy(() -> KeyStores.createTrustStoreFromCertificates(certFolder.toPath()))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(CertificateException.class)
                .hasMessageContaining(String.format(
                        "Could not read file at \"%s\" as an X.509 certificate", tempDirFile.getAbsolutePath()));
    }

    @Test
    public void createTrustStoreFromCertificatesFromCertificatesByAlias() throws Exception {
        String cert = Files.toString(TestConstants.SERVER_CERT_PEM_PATH.toFile(), StandardCharsets.UTF_8);
        KeyStore trustStore =
                KeyStores.createTrustStoreFromCertificates(ImmutableMap.of("server.crt", PemX509Certificate.of(cert)));

        assertThat(trustStore.getCertificate("server.crt-0").toString()).contains("CN=localhost");
    }

    @Test
    public void createTrustStoreFromCertificatesFromCertificatesByAliasInvalidCert() throws Exception {
        String cert = Files.toString(TestConstants.COMBINED_CRL_PATH.toFile(), StandardCharsets.UTF_8);

        assertThatThrownBy(() -> KeyStores.createTrustStoreFromCertificates(
                        ImmutableMap.of("invalid.crt", PemX509Certificate.of(cert))))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(CertificateParsingException.class)
                .hasMessageContaining("Could not read certificate alias \"invalid.crt\" as an X.509 certificate");
    }

    @Test
    public void testCreateKeyStoreFromPemFile() throws GeneralSecurityException {
        KeyStore keyStore = KeyStores.createKeyStoreFromCombinedPems(TestConstants.SERVER_KEY_CERT_COMBINED_PEM_PATH);

        assertThat(keyStore.size()).isEqualTo(1);
        assertThat(keyStore.getCertificate(TestConstants.SERVER_KEY_CERT_COMBINED_PEM_PATH
                                .getFileName()
                                .toString())
                        .toString())
                .contains("CN=testCA");
        assertThat(keyStore.getKey(
                                TestConstants.SERVER_KEY_CERT_COMBINED_PEM_PATH
                                        .getFileName()
                                        .toString(),
                                null)
                        .getFormat())
                .isEqualTo("PKCS#8");
    }

    @Test
    public void testCreateKeyStoreFromKeyDirectory() throws GeneralSecurityException, IOException {
        File keyFolder = tempFolder.newFolder();
        Files.copy(
                TestConstants.SERVER_KEY_CERT_COMBINED_PEM_PATH.toFile(),
                keyFolder.toPath().resolve("server.pkcs1").toFile());
        Files.copy(
                TestConstants.CLIENT_KEY_CERT_COMBINED_PEM_PATH.toFile(),
                keyFolder.toPath().resolve("client.pkcs1").toFile());
        KeyStore keyStore = KeyStores.createKeyStoreFromCombinedPems(keyFolder.toPath());

        assertThat(keyStore.size()).isEqualTo(2);
        assertThat(keyStore.getCertificate("server.pkcs1").toString()).contains("CN=localhost");
        assertThat(keyStore.getKey("server.pkcs1", null).getFormat()).isEqualTo("PKCS#8");
        assertThat(keyStore.getCertificate("client.pkcs1").toString()).contains("CN=client");
        assertThat(keyStore.getKey("client.pkcs1", null).getFormat()).isEqualTo("PKCS#8");
    }

    @Test
    public void testCreateKeyStoreFromEmptyDirectory() throws GeneralSecurityException, IOException {
        File keyFolder = tempFolder.newFolder();
        KeyStore trustStore = KeyStores.createKeyStoreFromCombinedPems(keyFolder.toPath());

        assertThat(trustStore.size()).isZero();
    }

    @Test
    public void testCreateKeyStoreFromDirectoryFailsWithNonKeyFiles() throws IOException, GeneralSecurityException {
        File keyFolder = tempFolder.newFolder();
        File tempCertFile = keyFolder.toPath().resolve("server.cer").toFile();
        Files.copy(TestConstants.SERVER_CERT_PEM_PATH.toFile(), tempCertFile);

        assertThatThrownBy(() -> KeyStores.createKeyStoreFromCombinedPems(keyFolder.toPath()))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(GeneralSecurityException.class)
                .hasMessageContaining(String.format(
                        "Failed to read private key from file at \"%s\"", tempCertFile.getAbsolutePath()));
    }

    @Test
    public void testCreateKeyStoreFromPemDirectories() throws GeneralSecurityException, IOException {
        File keyFolder = tempFolder.newFolder();
        File certFolder = tempFolder.newFolder();
        Files.copy(
                TestConstants.SERVER_KEY_PEM_PATH.toFile(),
                keyFolder.toPath().resolve("server.key").toFile());
        Files.copy(
                TestConstants.SERVER_CERT_PEM_PATH.toFile(),
                certFolder.toPath().resolve("server.cer").toFile());

        KeyStore keyStore =
                KeyStores.createKeyStoreFromPemDirectories(keyFolder.toPath(), ".key", certFolder.toPath(), ".cer");

        assertThat(keyStore.size()).isEqualTo(1);
        assertThat(keyStore.getCertificate("server").toString()).contains("CN=localhost");
        assertThat(keyStore.getKey("server", null).getFormat()).isEqualTo("PKCS#8");
    }

    @Test
    public void testCreateKeyStoreFromPemDirectoriesFailsIfCertMissing() throws IOException {
        File keyFolder = tempFolder.newFolder();
        File certFolder = tempFolder.newFolder();
        Files.copy(
                TestConstants.SERVER_KEY_PEM_PATH.toFile(),
                keyFolder.toPath().resolve("server.key").toFile());

        assertThatThrownBy(() -> KeyStores.createKeyStoreFromPemDirectories(
                        keyFolder.toPath(), ".key", certFolder.toPath(), ".cer"))
                .hasCauseInstanceOf(NoSuchFileException.class)
                .hasMessageContaining(String.format(
                        "Failed to read certificates from file at \"%s\"",
                        certFolder.toPath().resolve("server.cer").toString()));
    }

    @Test
    public void testCreateKeyStoreFromPemDirectoriesFailsIfArgIsNotDirectory() throws IOException {
        File folder = tempFolder.newFolder();
        File file = tempFolder.newFile();

        assertThatThrownBy(() ->
                        KeyStores.createKeyStoreFromPemDirectories(file.toPath(), ".key", folder.toPath(), ".cer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.format(
                        "keyDirPath is not a directory: \"%s\"", file.toPath().toString()));

        assertThatThrownBy(() ->
                        KeyStores.createKeyStoreFromPemDirectories(folder.toPath(), ".key", file.toPath(), ".cer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.format(
                        "certDirPath is not a directory: \"%s\"", file.toPath().toString()));
    }

    @Test
    public void testReadingPkcs1PrivateKey() throws InvalidKeySpecException, NoSuchAlgorithmException {
        RSAPrivateKeySpec privateKeySpec = KeyStores.parsePkcs1PrivateKey(TestConstants.PRIVATE_KEY_DER);
        assertKey((RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec));
    }

    @Test
    public void testReadingPkcs8PrivateKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        PKCS8EncodedKeySpec privateKeySpec = KeyStores.parsePkcs8PrivateKey(TestConstants.PKCS8_PRIVATE_KEY_DER);
        assertKey((RSAPrivateCrtKey) KeyFactory.getInstance("RSA").generatePrivate(privateKeySpec));
    }

    @Test
    public void testReadingPkcs1PrivateKeyString() throws GeneralSecurityException {
        PrivateKey privateKey = KeyStores.getPrivateKeyFromString(TestConstants.RSA_PRIVATE_KEY_PEM);
        assertKey((RSAPrivateCrtKey) privateKey);
    }

    @Test
    public void testReadingPkcs8PrivateKeyString() throws GeneralSecurityException {
        PrivateKey privateKey = KeyStores.getPrivateKeyFromString(TestConstants.PKCS8_PRIVATE_KEY_PEM);
        assertKey((RSAPrivateCrtKey) privateKey);
    }

    @Test
    public void testMismatchedTags() {
        assertThatThrownBy(() -> KeyStores.getPrivateKeyFromString(
                        "-----BEGIN PRIVATE KEY-----\n-----END RSA PRIVATE KEY-----\n"))
                .isInstanceOf(GeneralSecurityException.class)
                .hasMessageStartingWith("unable to find valid RSA key in the provided string");
    }

    @Test
    public void testPemX509CertificateDeserializationFromString() throws IOException {
        ObjectMapper mapper = ObjectMappers.newServerObjectMapper();
        String cert = Files.asCharSource(TestConstants.SERVER_CERT_PEM_PATH.toFile(), StandardCharsets.UTF_8)
                .read();
        byte[] json = mapper.writeValueAsBytes(cert);
        assertThat(mapper.readValue(json, PemX509Certificate.class)).isEqualTo(PemX509Certificate.of(cert));
    }

    @Test
    public void testPemX509CertificateDeserializationFromJsonObject() throws IOException {
        ObjectMapper mapper = ObjectMappers.newServerObjectMapper();
        String cert = Files.asCharSource(TestConstants.SERVER_CERT_PEM_PATH.toFile(), StandardCharsets.UTF_8)
                .read();
        byte[] json = mapper.writeValueAsBytes(ImmutableMap.of("pemCertificate", cert));
        assertThat(mapper.readValue(json, PemX509Certificate.class)).isEqualTo(PemX509Certificate.of(cert));
    }

    @Test
    public void testPemX509CertificateRoundTripSerde() throws IOException {
        ObjectMapper mapper = ObjectMappers.newServerObjectMapper();
        PemX509Certificate expected = PemX509Certificate.of(
                Files.asCharSource(TestConstants.SERVER_CERT_PEM_PATH.toFile(), StandardCharsets.UTF_8)
                        .read());
        byte[] json = mapper.writeValueAsBytes(expected);
        PemX509Certificate actual = mapper.readValue(json, PemX509Certificate.class);
        assertThat(actual).isEqualTo(expected);
    }

    private void assertKey(RSAPrivateCrtKey privateKey) {
        assertThat(privateKey.getModulus()).isEqualTo(TestConstants.MODULUS);
        assertThat(privateKey.getPrivateExponent()).isEqualTo(TestConstants.PRIVATE_EXPONENT);
        assertThat(privateKey.getPublicExponent()).isEqualTo(TestConstants.PUBLIC_EXPONENT);
        assertThat(privateKey.getPrimeP()).isEqualTo(TestConstants.PRIME_P);
        assertThat(privateKey.getPrimeQ()).isEqualTo(TestConstants.PRIME_Q);
        assertThat(privateKey.getPrimeExponentP()).isEqualTo(TestConstants.EXPONENT_P);
        assertThat(privateKey.getPrimeExponentQ()).isEqualTo(TestConstants.EXPONENT_Q);
        assertThat(privateKey.getCrtCoefficient()).isEqualTo(TestConstants.CTR_COEFFICIENT);
    }
}
