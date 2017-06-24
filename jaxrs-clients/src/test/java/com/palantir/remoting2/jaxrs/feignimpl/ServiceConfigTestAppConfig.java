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

package com.palantir.remoting2.jaxrs.feignimpl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.palantir.remoting.api.config.service.ServiceConfiguration;
import com.palantir.remoting.api.config.service.ServicesConfigBlock;
import io.dropwizard.Configuration;

/**
 * Configuration class for the {@link ServiceConfigTestServer}.
 */
public final class ServiceConfigTestAppConfig extends Configuration {

    @JsonProperty("auth")
    private ServiceConfiguration authConfig;

    @JsonProperty("serviceDiscovery")
    private ServicesConfigBlock serviceDiscoveryConfig;

    public ServiceConfiguration getAuthConfiguration() {
        return this.authConfig;
    }

    public ServicesConfigBlock getServiceDiscoveryConfiguration() {
        return this.serviceDiscoveryConfig;
    }
}
