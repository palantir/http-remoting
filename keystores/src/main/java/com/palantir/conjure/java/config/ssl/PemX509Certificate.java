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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.palantir.logsafe.Preconditions;
import org.immutables.value.Value;

@Value.Immutable
@ImmutablesStyle
@JsonSerialize(as = ImmutablePemX509Certificate.class)
public abstract class PemX509Certificate {

    /**
     * A X509.1 certificate or certificate chain in PEM format encoded in UTF-8.
     *
     * <p>The certificates must be delimited by the the begin and end {@code CERTIFICATE} markers.
     */
    public abstract String pemCertificate();

    @JsonCreator(mode = Mode.DELEGATING)
    public static PemX509Certificate of(String pemCertificate) {
        return ImmutablePemX509Certificate.builder()
                .pemCertificate(pemCertificate)
                .build();
    }

    // Exists for backcompat, PemX509Certificate may be deserialized from either a String or JSON object.
    @JsonCreator(mode = Mode.DELEGATING)
    private static PemX509Certificate of(ImmutablePemX509Certificate immutablePemX509Certificate) {
        return Preconditions.checkNotNull(immutablePemX509Certificate, "PemX509Certificate is required");
    }
}
