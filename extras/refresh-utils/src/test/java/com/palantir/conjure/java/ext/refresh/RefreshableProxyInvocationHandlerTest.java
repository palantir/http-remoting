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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.reflect.Reflection;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public final class RefreshableProxyInvocationHandlerTest {

    interface Callable {
        void call();
    }

    @Mock
    private Function<Object, Callable> supplier;

    @Mock
    private Callable delegate1;

    @Mock
    private Callable delegate2;

    @BeforeEach
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testCannotConstructProxyWhenInitialRefreshableIsEmpty() throws Exception {
        Refreshable<Object> refreshable = Refreshable.empty();
        try {
            RefreshableProxyInvocationHandler.create(refreshable, supplier);
            failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        } catch (IllegalArgumentException e) {
            assertThat(e)
                    .hasMessage("Cannot construct RefreshableProxyInvocationHandler with empty initial refreshable");
        }
    }

    @Test
    public void testUsesInitialDelegateAndUpdatesDelegatesWhenRefreshableChanges() throws Exception {
        Object object1 = new Object();
        Object object2 = new Object();

        // Setup initial proxy to call out to delegate1, this fetches the initial delegate from the supplier.
        when(supplier.apply(object1)).thenReturn(delegate1);
        Refreshable<Object> refreshable = Refreshable.of(object1);
        RefreshableProxyInvocationHandler<Object, Callable> handler =
                RefreshableProxyInvocationHandler.create(refreshable, supplier);
        verify(supplier).apply(object1);
        Callable proxy = Reflection.newProxy(Callable.class, handler);

        // First call: check that delegate 1 received call and that supplier is not invoked.
        proxy.call();
        verify(delegate1).call();
        Mockito.verifyNoMoreInteractions(delegate1, delegate2, supplier);

        // Second call: still using delegate1, not invoking the supplier.
        proxy.call();
        verify(delegate1, times(2)).call();
        Mockito.verifyNoMoreInteractions(delegate1, delegate2, supplier);

        // Third call: refresh the object and make the supplier return a new delegate2.
        refreshable.set(object2);
        when(supplier.apply(object2)).thenReturn(delegate2);
        proxy.call();
        verify(delegate2).call();
        verify(supplier).apply(object2);
        Mockito.verifyNoMoreInteractions(delegate1, delegate2, supplier);

        // Fourth call: still using delegate2, not invoking the supplier.
        proxy.call();
        verify(delegate2, times(2)).call();
        Mockito.verifyNoMoreInteractions(delegate1, delegate2, supplier);
    }

    @Test
    public void testUnwrapsInvocationTargetExceptions() {
        Callable throwingCallable = () -> {
            throw new IllegalStateException("Whoops");
        };
        Refreshable<Callable> refreshable = Refreshable.of(throwingCallable);

        RefreshableProxyInvocationHandler<Callable, Callable> handler =
                RefreshableProxyInvocationHandler.create(refreshable, _tec -> throwingCallable);
        Callable proxy = Reflection.newProxy(Callable.class, handler);

        assertThatThrownBy(proxy::call)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Whoops");
    }
}
