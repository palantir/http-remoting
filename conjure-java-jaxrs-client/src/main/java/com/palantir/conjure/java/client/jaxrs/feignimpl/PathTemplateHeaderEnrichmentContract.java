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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import feign.Contract;
import feign.MethodMetadata;
import java.lang.reflect.Method;

public final class PathTemplateHeaderEnrichmentContract extends AbstractDelegatingContract {
    private static final String PATH_TEMPLATE_HEADER = "hr-path-template";
    /**
     * No longer used.
     *
     * @deprecated no longer used
     */
    @Deprecated
    public static final char OPEN_BRACE_REPLACEMENT = '\0';
    /**
     * No longer used.
     *
     * @deprecated no longer used
     */
    @Deprecated
    public static final char CLOSE_BRACE_REPLACEMENT = '\1';

    public PathTemplateHeaderEnrichmentContract(Contract delegate) {
        super(delegate);
    }

    @Override
    protected void processMetadata(Class<?> _targetType, Method _method, MethodMetadata metadata) {
        metadata.template()
                .header(
                        PATH_TEMPLATE_HEADER,
                        metadata.template().method()
                                + " "
                                + metadata.template()
                                        .url()
                                        // escape from feign string interpolation
                                        // See RequestTemplate.expand
                                        .replace("{", "{{"));
    }
}
