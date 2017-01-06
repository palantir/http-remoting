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

package com.palantir.remoting1.ext.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

public final class ObjectMappers {

    private ObjectMappers() {}

    /**
     * Returns a newly allocated {@link ObjectMapper} that is configured with the Guava module and the JDK 7 module.
     */
    public static ObjectMapper guavaJdk7() {
        return new ObjectMapper()
                .registerModule(new GuavaModule())
                .registerModule(new ShimJdk7Module())
                .registerModule(new Jdk8Module())
                .registerModule(new AfterburnerModule());
    }
}
