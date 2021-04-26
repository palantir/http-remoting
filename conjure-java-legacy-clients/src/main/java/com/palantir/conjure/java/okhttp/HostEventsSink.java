/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

/**
 * A listener for responses / exceptions coming from remote hosts when using clients.
 *
 * <p>We provide a HostMetricsRegistry implementation of this that turns these events into HostMetrics
 * for each remote host.
 * @deprecated prefer super interface {@link com.palantir.conjure.java.client.config.HostEventsSink}
 */
@Deprecated
public interface HostEventsSink extends com.palantir.conjure.java.client.config.HostEventsSink {
    @Override
    void record(String serviceName, String hostname, int port, int statusCode, long micros);

    @Override
    void recordIoException(String serviceName, String hostname, int port);

    @Override
    default HostEventCallback callback(String serviceName, String hostname, int port) {
        return new HostEventCallback() {
            @Override
            public void record(int statusCode, long micros) {
                HostEventsSink.this.record(serviceName, hostname, port, statusCode, micros);
            }

            @Override
            public void recordIoException() {
                HostEventsSink.this.recordIoException(serviceName, hostname, port);
            }
        };
    }

    /**
     * .
     * @deprecated prefer super interface
     */
    @Deprecated
    interface HostEventCallback extends com.palantir.conjure.java.client.config.HostEventsSink.HostEventCallback {

        @Override
        void record(int statusCode, long micros);

        @Override
        void recordIoException();

        static HostEventCallback from(com.palantir.conjure.java.client.config.HostEventsSink.HostEventCallback other) {
            if (other instanceof HostEventCallback) {
                return (HostEventCallback) other;
            }
            return new HostEventCallback() {
                @Override
                public void record(int statusCode, long micros) {
                    other.record(statusCode, micros);
                }

                @Override
                public void recordIoException() {
                    other.recordIoException();
                }
            };
        }
    }

    static HostEventsSink from(com.palantir.conjure.java.client.config.HostEventsSink other) {
        if (other instanceof HostEventsSink) {
            return (HostEventsSink) other;
        }
        return new HostEventsSink() {
            @Override
            public void record(String serviceName, String hostname, int port, int statusCode, long micros) {
                other.record(serviceName, hostname, port, statusCode, micros);
            }

            @Override
            public void recordIoException(String serviceName, String hostname, int port) {
                other.recordIoException(serviceName, hostname, port);
            }

            @Override
            public HostEventCallback callback(String serviceName, String hostname, int port) {
                return HostEventCallback.from(other.callback(serviceName, hostname, port));
            }
        };
    }
}
