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

package com.palantir.conjure.java;

import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.logsafe.UnsafeArg;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QosExceptionResponseMapper {

    private static final Logger log = LoggerFactory.getLogger(QosExceptionResponseMapper.class);

    private QosExceptionResponseMapper() {}

    public static Optional<QosException> mapResponseCodeHeaderStream(
            int code, Function<String, Stream<String>> headerFn) {
        return mapResponseCode(
                code, header -> headerFn.apply(header).findFirst().orElse(null));
    }

    public static Optional<QosException> mapResponseCode(int code, Function<String, String> headerFn) {
        switch (code) {
            case 308:
                return map308(headerFn);
            case 429:
                return Optional.of(map429(headerFn));
            case 503:
                return Optional.of(map503());
        }

        return Optional.empty();
    }

    private static Optional<QosException> map308(Function<String, String> headerFn) {
        String locationHeader = headerFn.apply(HttpHeaders.LOCATION);
        if (locationHeader == null) {
            log.error("Retrieved HTTP status code 308 without Location header, cannot perform "
                    + "redirect. This appears to be a server-side protocol violation.");
            return Optional.empty();
        }

        try {
            return Optional.of(QosException.retryOther(new URL(locationHeader)));
        } catch (MalformedURLException e) {
            log.error(
                    "Failed to parse location header, not performing redirect",
                    UnsafeArg.of("locationHeader", locationHeader),
                    e);
            return Optional.empty();
        }
    }

    private static QosException map429(Function<String, String> headerFn) {
        String duration = headerFn.apply(HttpHeaders.RETRY_AFTER);
        if (duration != null) {
            return QosException.throttle(Duration.ofSeconds(Long.parseLong(duration)));
        }
        return QosException.throttle();
    }

    private static QosException map503() {
        return QosException.unavailable();
    }
}
