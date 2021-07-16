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

package com.palantir.conjure.java.server.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.serialization.ObjectMappers;
import javax.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

public final class JsonExceptionMapperTest {

    private final JsonExceptionMapper<RuntimeException> mapper =
            new JsonExceptionMapper<RuntimeException>(ConjureJerseyFeature.NoOpListener.INSTANCE) {
                @Override
                ErrorType getErrorType(RuntimeException _exception) {
                    return ErrorType.INVALID_ARGUMENT;
                }
            };

    private final ObjectMapper objectMapper =
            ObjectMappers.newServerObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    public void testExpectedSerializedError() throws Exception {
        Response response = mapper.toResponse(new ServiceException(ErrorType.NOT_FOUND));
        String entity = objectMapper.writeValueAsString(response.getEntity());
        assertThat(entity).contains("\"errorCode\" : \"INVALID_ARGUMENT\"");
        assertThat(entity).contains("\"errorName\" : \"Default:InvalidArgument\"");
        assertThat(entity).contains("\"errorInstanceId\" : ");
    }

    @Test
    public void testDoesNotPropagateExceptionMessage() throws Exception {
        Response response = new RuntimeExceptionMapper(ConjureJerseyFeature.NoOpListener.INSTANCE)
                .toResponse(new NullPointerException("secret"));
        String entity = objectMapper.writeValueAsString(response.getEntity());
        assertThat(entity).doesNotContain("secret");
    }
}
