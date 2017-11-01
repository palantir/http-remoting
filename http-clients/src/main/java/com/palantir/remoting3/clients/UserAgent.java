/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting3.clients;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Constructs, validates, and formats a canonical User-Agent header. Because the http header spec
 * (https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2) requires headers to be joined on commas, individual
 * {@link Agent} header strings must never contain commas.
 */
@Value.Immutable
@ImmutablesStyle
public interface UserAgent {

    String DEFAULT_VERSION = "0.0.0";

    List<Agent> agents();

    static UserAgent of(String serviceName, String instanceId, String version) {
        Agent agent = ImmutableAgent.builder()
                .serviceName(serviceName)
                .instanceId(instanceId)
                .version(UserAgents.isValidVersion(version) ? version : DEFAULT_VERSION)
                .build();
        return ImmutableUserAgent.builder()
                .addAgents(agent)
                .build();
    }

    static UserAgent of(String serviceName, String version) {
        Agent agent = ImmutableAgent.builder()
                .serviceName(serviceName)
                .version(UserAgents.isValidVersion(version) ? version : DEFAULT_VERSION)
                .build();
        return ImmutableUserAgent.builder()
                .addAgents(agent)
                .build();
    }

    /**
     * Returns the {@link UserAgent} comprising all {@link UserAgent#agents} from the left and all {@link
     * UserAgent#agents} from the right given {@link UserAgent}s.
     */
    default UserAgent merge(UserAgent other) {
        return ImmutableUserAgent.builder()
                .from(this)
                .addAllAgents(other.agents())
                .build();
    }

    @Value.Immutable
    @ImmutablesStyle
    interface Agent {
        String serviceName();
        Optional<String> instanceId();
        String version();

        @Value.Check
        default void check() {
            checkArgument(UserAgents.isValidServiceName(serviceName()),
                    "Illegal service name format: %s", serviceName());
            if (instanceId().isPresent()) {
                checkArgument(UserAgents.isValidInstance(instanceId().get()),
                        "Illegal instance id format: %s", instanceId().get());
            }
            // Should never hit the following.
            checkArgument(UserAgents.isValidVersion(version()), "Illegal version format: %s. This is a bug", version());
        }
    }
}
