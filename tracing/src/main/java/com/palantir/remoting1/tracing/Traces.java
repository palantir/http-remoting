/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting1.tracing;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The singleton entry point for handling Zipkin-style traces and spans. Provides functionality for starting and
 * completing spans, and for registering subscribers to span completion events.
 * <p>
 * This class is thread-safe.
 */
public final class Traces {

    public interface Headers {
        String TRACE_ID = "X-B3-TraceId";
        String PARENT_SPAN_ID = "X-B3-ParentSpanId";
        String SPAN_ID = "X-B3-SpanId";
    }

    // Stack of trace states; thread-local (and thus thread-safe).
    private static final ThreadLocal<Deque<TraceState>> STATE = new ThreadLocal<Deque<TraceState>>() {
        @Override
        protected Deque<TraceState> initialValue() {
            return new ArrayDeque<>();
        }
    };

    // Thread-safe set implementation
    private static final Set<Subscriber> SUBSCRIBERS = Sets.newConcurrentHashSet();

    /**
     * Package-local state copy mechanism for thread inheritance.
     */
    static Deque<TraceState> getCopyOfState() {
        return new ArrayDeque<>(STATE.get());
    }

    /**
     * Package-local state forcing mechanism for thread inheritance.
     */
    static void forceState(Deque<TraceState> stateToForce) {
        STATE.get().remove();
        STATE.get().addAll(stateToForce);
    }

    public static Optional<TraceState> getTrace() {
        Deque<TraceState> stack = STATE.get();
        return stack.isEmpty() ? Optional.<TraceState>absent() : Optional.of(stack.peek());
    }

    public static void setTrace(TraceState state) {
        STATE.remove();
        STATE.get().push(state);
    }

    /**
     * Derives a new call trace from the currently known call trace labeled with the provided operation.
     */
    public static TraceState startSpan(String operation) {
        Optional<TraceState> prevState = getTrace();

        TraceState.Builder newStateBuilder = TraceState.builder()
                .operation(operation);

        if (prevState.isPresent()) {
            newStateBuilder.traceId(prevState.get().getTraceId())
                    .parentSpanId(prevState.get().getSpanId()); // span -> parent
        }

        TraceState newState = newStateBuilder.build();
        STATE.get().push(newState);
        return newState;
    }

    /**
     * Completes and returns the current span (if it exists) and notifies all {@link #SUBSCRIBERS subscribers} about the
     * completed span.
     */
    public static Optional<Span> completeSpan() {
        Deque<TraceState> stack = STATE.get();
        if (stack.isEmpty()) {
            return Optional.absent();
        } else {
            TraceState state = stack.pop();
            Span span = Span.builder()
                    .traceId(state.getTraceId())
                    .spanId(state.getSpanId())
                    .parentSpanId(state.getParentSpanId())
                    .operation(state.getOperation())
                    .startTimeMs(state.getStartTimeMs())
                    .durationNs(System.nanoTime() - state.getStartClockNs())
                    .build();

            // notify subscribers
            for (Subscriber subscriber : SUBSCRIBERS) {
                subscriber.consume(span);
            }

            return Optional.of(span);
        }
    }

    /**
     * Wraps the provided executor service to make submitted tasks traceable.
     */
    public static ExecutorService wrap(ExecutorService executorService) {
        return new TracingAwareExecutorService(executorService);
    }

    /**
     * Wraps the provided scheduled executor service to make submitted tasks traceable.
     */
    public static ScheduledExecutorService wrap(ScheduledExecutorService executorService) {
        return new TracingAwareScheduledExecutorService(executorService);
    }

    /**
     * Subscribes the given span consumer to all "span completed" events. Subscribers are expected to be "cheap", i.e.,
     * do all non-trivial work (logging, sending network messages, etc) asynchronously.
     */
    public static void subscribe(Subscriber subscriber) {
        SUBSCRIBERS.add(subscriber);
    }

    /** The inverse of {@link #subscribe}. */
    public static void unsubscribe(Subscriber subscriber) {
        SUBSCRIBERS.remove(subscriber);
    }

    /**
     * Represents the event receiver for trace completion events. Implementations are invoked synchronously on the
     * primary execution thread, and as a result must execute quickly.
     */
    public interface Subscriber {
        void consume(Span span);
    }

    private Traces() {}
}
