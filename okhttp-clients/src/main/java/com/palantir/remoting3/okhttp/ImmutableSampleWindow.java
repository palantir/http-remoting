/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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
package com.palantir.remoting3.okhttp;

import java.util.concurrent.TimeUnit;

/**
 * Class used to track immutable samples in an AtomicReference
 */
final class ImmutableSampleWindow {
    private final long minRtt;
    private final int maxInFlight;
    private final int sampleCount;
    private final long sum;
    private final boolean didDrop;
    
    public ImmutableSampleWindow() {
        this.minRtt = Long.MAX_VALUE;
        this.maxInFlight = 0;
        this.sampleCount = 0;
        this.sum = 0;
        this.didDrop = false;
    }
    
    public ImmutableSampleWindow(long minRtt, long sum, int maxInFlight, int sampleCount, boolean didDrop) {
        this.minRtt = minRtt;
        this.sum = sum;
        this.maxInFlight = maxInFlight;
        this.sampleCount = sampleCount;
        this.didDrop = didDrop;
    }
    
    public ImmutableSampleWindow addSample(long rtt, int maxInFlight) {
        return new ImmutableSampleWindow(
                Math.min(rtt, minRtt),
                sum + rtt,
                Math.max(maxInFlight, this.maxInFlight),
                sampleCount + 1,
                didDrop);
    }
    
    public ImmutableSampleWindow addDroppedSample(int maxInFlight) {
        return new ImmutableSampleWindow(minRtt, sum, Math.max(maxInFlight, this.maxInFlight), sampleCount, true);
    }
    
    public long getCandidateRttNanos() {
        return minRtt;
    }

    public long getAverageRttNanos() {
        return sampleCount == 0 ? 1 : sum / sampleCount;
    }
    
    public int getMaxInFlight() {
        return maxInFlight;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public boolean didDrop() {
        return didDrop;
    }

    @Override
    public String toString() {
        return "ImmutableSampleWindow ["
                + "minRtt=" + TimeUnit.NANOSECONDS.toMicros(minRtt) / 1000.0 
                + ", avgRtt=" + TimeUnit.NANOSECONDS.toMicros(getAverageRttNanos()) / 1000.0
                + ", maxInFlight=" + maxInFlight 
                + ", sampleCount=" + sampleCount 
                + ", didDrop=" + didDrop + "]";
    }
}