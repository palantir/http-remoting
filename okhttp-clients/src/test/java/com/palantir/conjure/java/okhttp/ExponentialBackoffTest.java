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

import com.google.common.util.concurrent.AtomicDouble;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public final class ExponentialBackoffTest {
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    @Test
    public void testNoRetry() {
        AtomicDouble random = new AtomicDouble();
        ExponentialBackoff backoff = new ExponentialBackoff(0, ONE_SECOND, random::get);

        assertThat(backoff.nextBackoff()).isEmpty();
    }

    @Test
    public void testRetriesCorrectNumberOfTimesAndFindsRandomBackoffWithInExponentialInterval() {
        AtomicDouble random = new AtomicDouble();
        ExponentialBackoff backoff = new ExponentialBackoff(3, ONE_SECOND, random::get);

        random.set(1.0);
        assertThat(backoff.nextBackoff()).contains(ONE_SECOND.multipliedBy(2));

        random.set(1.0);
        assertThat(backoff.nextBackoff()).contains(ONE_SECOND.multipliedBy(4));

        random.set(0.5);
        assertThat(backoff.nextBackoff()).contains(ONE_SECOND.multipliedBy(4 /* 8 * 0.5 (exp * jitter), see above */));

        assertThat(backoff.nextBackoff()).isEmpty();
    }
}
