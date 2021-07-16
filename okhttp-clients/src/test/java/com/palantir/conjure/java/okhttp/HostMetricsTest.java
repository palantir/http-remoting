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

package com.palantir.conjure.java.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public final class HostMetricsTest {

    private static final String SERVICE_NAME = "serviceName";
    private static final String HOSTNAME = "hostname";
    private static final int PORT = 8080;
    private static final long firstInstant = 0;
    private static final long secondInstant = 1;

    private DefaultHostMetrics hostMetrics;

    @Mock
    private Clock clock;

    @BeforeEach
    public void before() {
        when(clock.millis()).thenReturn(firstInstant, secondInstant);
        hostMetrics = new DefaultHostMetrics(SERVICE_NAME, HOSTNAME, PORT, clock);
    }

    @Test
    public void testUpdateMetricUpdatesMeter_1xx() {
        testUpdateMetricUpdatesMeter(100, hostMetrics.get1xx());
    }

    @Test
    public void testUpdateMetricUpdatesMeter_2xx() {
        testUpdateMetricUpdatesMeter(200, hostMetrics.get2xx());
    }

    @Test
    public void testUpdateMetricUpdatesMeter_3xx() {
        testUpdateMetricUpdatesMeter(300, hostMetrics.get3xx());
    }

    @Test
    public void testUpdateMetricUpdatesMeter_4xx() {
        testUpdateMetricUpdatesMeter(400, hostMetrics.get4xx());
    }

    @Test
    public void testUpdateMetricUpdatesMeter_5xx() {
        testUpdateMetricUpdatesMeter(500, hostMetrics.get5xx());
    }

    @Test
    public void testUpdateMetricUpdatesMeter_429() {
        testUpdateMetricUpdatesMeter(429, hostMetrics.getQos());
    }

    @Test
    public void testUpdateMetricUpdatesMeter_503() {
        testUpdateMetricUpdatesMeter(503, hostMetrics.getQos());
    }

    @Test
    public void testUpdateMetricUpdatesMeter_other() {
        testUpdateMetricUpdatesMeter(600, hostMetrics.getOther());
    }

    private void testUpdateMetricUpdatesMeter(int statusCode, Timer timer) {
        assertThat(timer.getCount()).isZero();
        assertThat(timer.getSnapshot().getMin()).isZero();

        hostMetrics.record(statusCode, 1);

        assertThat(timer.getCount()).isEqualTo(1);
        assertThat(timer.getSnapshot().getMin()).isEqualTo(1_000);
    }

    @Test
    public void testIoExceptionUpdatesMeter() {
        Meter ioExceptions = hostMetrics.getIoExceptions();
        assertThat(ioExceptions.getCount()).isZero();

        hostMetrics.recordIoException();

        assertThat(ioExceptions.getCount()).isEqualTo(1);
    }

    @Test
    public void testRecordUpdatesInstant() {
        Instant previousUpdate = hostMetrics.lastUpdate();
        hostMetrics.record(200, 100);

        assertThat(hostMetrics.lastUpdate()).isAfter(previousUpdate);
    }

    @Test
    public void testRecordIoExceptionUpdatesInstant() {
        Instant previousUpdate = hostMetrics.lastUpdate();
        hostMetrics.recordIoException();

        assertThat(hostMetrics.lastUpdate()).isAfter(previousUpdate);
    }
}
