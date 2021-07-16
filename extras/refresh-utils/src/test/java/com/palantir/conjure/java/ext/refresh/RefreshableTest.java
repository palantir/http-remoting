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

package com.palantir.conjure.java.ext.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.concurrent.TimeUnit;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.jupiter.api.Test;

public final class RefreshableTest {

    private static final Object O1 = new Object();
    private static final Object O2 = new Object();

    @Test
    public void testInitialRefreshableIsEmpty() {
        assertThat(Refreshable.empty().getAndClear()).isEmpty();
    }

    @Test
    public void testGetterClearsValue() {
        Refreshable<Object> refreshable = Refreshable.of(O1);
        assertThat(refreshable.getAndClear()).contains(O1);
        assertThat(refreshable.getAndClear()).isEmpty();
    }

    @Test
    public void testWhenRefreshableIsEmpty_setterSetsNewValue() {
        Refreshable<Object> refreshable = Refreshable.empty();
        assertThat(refreshable.set(O1)).isEmpty();
        assertThat(refreshable.getAndClear()).contains(O1);
    }

    @Test
    public void testWhenRefreshableIsNonEmpty_setterSetsNewValue() {
        Refreshable<Object> refreshable = Refreshable.of(O1);
        assertThat(refreshable.set(O2)).contains(O1);
        assertThat(refreshable.getAndClear()).contains(O2);
    }

    @Test
    public void testRefreshableFromObservable() throws InterruptedException {
        Object o1 = new Object();
        Object o2 = new Object();

        // Observable that emits o1 for the first 10 seconds, and then o2 after 10 seconds
        DeterministicScheduler executor = new DeterministicScheduler();
        Observable<Object> observable = Observable.interval(1, TimeUnit.SECONDS, Schedulers.from(executor))
                .flatMapIterable(e -> e < 10 ? ImmutableList.of(o1) : ImmutableList.of(o2));

        Refreshable<Object> refreshable = Refreshable.empty();
        Disposable disposable = observable
                .distinctUntilChanged() // filters duplicates, i.e., Refreshable only sees distinct values.
                .subscribe(refreshable::set);

        executor.tick(1, TimeUnit.SECONDS);
        assertThat(refreshable.getAndClear()).contains(o1);
        assertThat(refreshable.getAndClear()).isEmpty();
        executor.tick(2, TimeUnit.SECONDS);
        assertThat(refreshable.getAndClear()).isEmpty(); // empty since observable is distinctUntilChanged()
        executor.tick(11, TimeUnit.SECONDS);
        assertThat(refreshable.getAndClear()).contains(o2);
        assertThat(refreshable.getAndClear()).isEmpty();
        executor.tick(12, TimeUnit.SECONDS);
        assertThat(refreshable.getAndClear()).isEmpty();

        disposable.dispose();
    }
}
