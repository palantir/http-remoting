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

package com.palantir.conjure.java.okhttp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class LeakDetectorTest {
    private final List<Optional<RuntimeException>> leaks = new ArrayList<>();
    private final LeakDetector<String> leakDetector = new LeakDetector<>(String.class, leaks::add);

    @Test
    public void detectsLeaks() {
        String toUnregister = "won't be leaked";
        leakDetector.register(toUnregister, Optional.empty());
        Optional<RuntimeException> exception = Optional.of(new RuntimeException());
        leakDetector.register(new String("this will be leaked".toCharArray()), exception);
        System.gc();
        leakDetector.register("this will trigger detection", Optional.empty());
        assertThat(leaks).containsExactly(exception);
        leakDetector.unregister(toUnregister);
    }

    @Test
    public void canUnregister() {
        String track = UUID.randomUUID().toString();
        leakDetector.register(track, Optional.empty());
        leakDetector.unregister(track);

        System.gc();
        leakDetector.register("trigger", Optional.empty());
        assertThat(leaks).isEmpty();
    }
}
