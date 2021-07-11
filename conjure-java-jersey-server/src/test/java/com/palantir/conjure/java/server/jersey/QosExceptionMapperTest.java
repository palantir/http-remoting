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

package com.palantir.conjure.java.server.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.errors.QosException;
import java.net.URL;
import java.time.Duration;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import org.junit.jupiter.api.Test;

public final class QosExceptionMapperTest {

    private static final ExceptionMapper<QosException> mapper =
            new QosExceptionMapper(ConjureJerseyFeature.NoOpListener.INSTANCE);

    @Test
    public void testThrottle_withoutDuration() throws Exception {
        QosException exception = QosException.throttle();
        Response response = mapper.toResponse(exception);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeaders()).isEmpty();
    }

    @Test
    public void testThrottle_withDuration() throws Exception {
        QosException exception = QosException.throttle(Duration.ofMinutes(2));
        Response response = mapper.toResponse(exception);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeaders()).containsEntry("Retry-After", ImmutableList.of("120"));
    }

    @Test
    public void testRetryOther() throws Exception {
        QosException exception = QosException.retryOther(new URL("http://foo"));
        Response response = mapper.toResponse(exception);
        assertThat(response.getStatus()).isEqualTo(308);
        assertThat(response.getHeaders()).containsEntry("Location", ImmutableList.of("http://foo"));
    }

    @Test
    public void testUnavailable() throws Exception {
        QosException exception = QosException.unavailable();
        Response response = mapper.toResponse(exception);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeaders()).isEmpty();
    }
}
