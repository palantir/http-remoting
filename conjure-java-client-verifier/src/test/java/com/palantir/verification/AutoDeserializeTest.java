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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.verification.server.AutoDeserializeConfirmService;
import com.palantir.conjure.verification.server.AutoDeserializeService;
import com.palantir.conjure.verification.server.EndpointName;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class AutoDeserializeTest {

    @ClassRule
    public static final VerificationServerRule server = new VerificationServerRule();

    private static final Logger log = LoggerFactory.getLogger(AutoDeserializeTest.class);
    private static final AutoDeserializeService testServiceJersey =
            VerificationClients.autoDeserializeServiceJersey(server);
    private static final AutoDeserializeConfirmService confirmService = VerificationClients.confirmService(server);

    @Parameterized.Parameter(0)
    public EndpointName endpointName;

    @Parameterized.Parameter(1)
    public int index;

    @Parameterized.Parameter(2)
    public boolean shouldSucceed;

    @Parameterized.Parameter(3)
    public String jsonString;

    @Parameterized.Parameters(name = "{0}({3}) -> should succeed {2}")
    public static Collection<Object[]> data() {
        List<Object[]> objects = new ArrayList<>();
        Cases.TEST_CASES.getAutoDeserialize().forEach((endpointName, positiveAndNegativeTestCases) -> {
            int positiveSize = positiveAndNegativeTestCases.getPositive().size();
            int negativeSize = positiveAndNegativeTestCases.getNegative().size();

            IntStream.range(0, positiveSize)
                    .forEach(i -> objects.add(new Object[] {
                        endpointName,
                        i,
                        true,
                        positiveAndNegativeTestCases.getPositive().get(i)
                    }));

            IntStream.range(0, negativeSize)
                    .forEach(i -> objects.add(new Object[] {
                        endpointName,
                        positiveSize + i,
                        false,
                        positiveAndNegativeTestCases.getNegative().get(i)
                    }));
        });
        return objects;
    }

    @Test
    @SuppressWarnings("IllegalThrows")
    public void runTestCaseJersey() throws Error, NoSuchMethodException {
        boolean shouldIgnore = Cases.shouldIgnoreJersey(endpointName, jsonString);
        Method method = testServiceJersey.getClass().getMethod(endpointName.get(), int.class);
        System.out.println(String.format(
                "[%s%s test case %s]: %s(%s), expected client to %s",
                shouldIgnore ? "ignored " : "",
                shouldSucceed ? "positive" : "negative",
                index,
                endpointName,
                jsonString,
                shouldSucceed ? "succeed" : "fail"));

        Optional<Error> expectationFailure = shouldSucceed ? expectSuccessJersey(method) : expectFailureJersey(method);

        if (shouldIgnore) {
            assertThat(expectationFailure)
                    .describedAs(
                            "The test passed but the test case was ignored - remove this from ignored-test-cases.yml")
                    .isNotEmpty();
        }

        Assume.assumeFalse(shouldIgnore);

        if (expectationFailure.isPresent()) {
            throw expectationFailure.get();
        }
    }

    private Optional<Error> expectSuccessJersey(Method method) {
        try {
            Object resultFromServer = method.invoke(testServiceJersey, index);
            log.info("Received result for endpoint {} and index {}: {}", endpointName, index, resultFromServer);
            confirmService.confirm(endpointName, index, resultFromServer);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(new AssertionError("Expected call to succeed, but caught exception", e));
        }
    }

    private Optional<Error> expectFailureJersey(Method method) {
        try {
            Object result = method.invoke(testServiceJersey, index);
            return Optional.of(new AssertionError(
                    String.format("Result should have caused an exception but deserialized to: %s", result)));
        } catch (Exception e) {
            return Optional.empty(); // we expected the method to throw and it did, so this expectation was satisifed
        }
    }
}
