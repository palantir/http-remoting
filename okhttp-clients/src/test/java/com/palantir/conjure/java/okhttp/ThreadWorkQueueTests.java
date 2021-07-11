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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

public final class ThreadWorkQueueTests {
    private final ThreadWorkQueue<Integer> queue = new ThreadWorkQueue<>();

    @Test
    public void testPrioritizesPerThread() throws InterruptedException {
        queue.add(1);
        enqueueWithNewThread(2, 3, 4);
        enqueueWithNewThread(5, 6, 7);
        queue.add(8);
        assertThat(dequeue()).containsExactly(1, 2, 5, 8, 3, 6, 4, 7);
    }

    @Test
    public void testThrowsIfEmpty() {
        assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(queue::remove);
    }

    @Test
    public void testFifoEvenWhileTotallyDequeued() throws InterruptedException {
        queue.add(1);
        enqueueWithNewThread(2, 3);
        assertThat(queue.remove()).isEqualTo(1);
        assertThat(queue.remove()).isEqualTo(2);
        queue.add(4);
        assertThat(queue.remove()).isEqualTo(3);
        assertThat(queue.remove()).isEqualTo(4);
    }

    private List<Integer> dequeue() {
        List<Integer> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            result.add(queue.remove());
        }
        return result;
    }

    private void enqueueWithNewThread(int... numbers) throws InterruptedException {
        Thread thread = new Thread(() -> Arrays.stream(numbers).forEach(queue::add));
        thread.start();
        thread.join();
    }
}
