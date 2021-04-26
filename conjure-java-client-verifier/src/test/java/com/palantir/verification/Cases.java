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

package com.palantir.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.palantir.conjure.verification.server.ClientTestCases;
import com.palantir.conjure.verification.server.EndpointName;
import com.palantir.conjure.verification.server.IgnoredClientTestCases;
import com.palantir.conjure.verification.server.IgnoredTestCases;
import com.palantir.conjure.verification.server.TestCases;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import javax.annotation.Nullable;

public final class Cases {

    public static final ClientTestCases TEST_CASES = deserializeTestCases(new File("build/test-cases/test-cases.json"));
    private static final IgnoredClientTestCases IGNORED_TEST_CASES_JERSEY =
            deserializeIgnoredClientTestCases(new File("src/test/resources/ignored-test-cases.jersey.yml"));

    private Cases() {}

    private static ClientTestCases deserializeTestCases(File file) {
        try {
            return new ObjectMapper()
                    .registerModule(new Jdk8Module())
                    .readValue(file, TestCases.class)
                    .getClient();
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format("Unable to read %s, you may need to run ./gradlew copyTestCases", file), e);
        }
    }

    private static IgnoredClientTestCases deserializeIgnoredClientTestCases(File file) {
        try {
            return new ObjectMapper(new YAMLFactory())
                    .registerModule(new Jdk8Module())
                    .readValue(file, IgnoredTestCases.class)
                    .getClient();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to read %s", file), e);
        }
    }

    public static boolean shouldIgnoreJersey(EndpointName endpointName, String json) {
        return setContains(IGNORED_TEST_CASES_JERSEY.getAutoDeserialize().get(endpointName), json)
                || setContains(
                        IGNORED_TEST_CASES_JERSEY.getSingleHeaderService().get(endpointName), json)
                || setContains(
                        IGNORED_TEST_CASES_JERSEY.getSinglePathParamService().get(endpointName), json)
                || setContains(
                        IGNORED_TEST_CASES_JERSEY.getSingleQueryParamService().get(endpointName), json);
    }

    private static boolean setContains(@Nullable Set<String> set, String item) {
        if (set == null) {
            return false;
        }

        return set.contains(item);
    }
}
