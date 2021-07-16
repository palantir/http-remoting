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

package com.palantir.conjure.java.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.logsafe.SafeArg;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public final class RemoteExceptionResponseHandlerTest {

    private static final String message = "hello";
    private static final int STATUS_500 = 500;

    private static final ObjectMapper SERVER_MAPPER = ObjectMappers.newServerObjectMapper();

    private static final Request request =
            new Request.Builder().url("http://url").build();
    private static final ServiceException SERVICE_EXCEPTION =
            new ServiceException(ErrorType.FAILED_PRECONDITION, SafeArg.of("key", "value"));
    private static final String SERIALIZED_EXCEPTION = createServiceException(SERVICE_EXCEPTION);

    private static String createServiceException(ServiceException exception) {
        try {
            return SERVER_MAPPER.writeValueAsString(SerializableError.forException(exception));
        } catch (JsonProcessingException e) {
            fail("failed to serialize");
            return "";
        }
    }

    private static final RemoteExceptionResponseHandler handler = RemoteExceptionResponseHandler.INSTANCE;

    @Test
    public void doesNotProduceExceptionOn101Or2xx() throws Exception {
        assertThat(handler.handle(response(200, MediaType.APPLICATION_JSON, SERIALIZED_EXCEPTION)))
                .isEmpty();
        assertThat(handler.handle(response(101, MediaType.APPLICATION_JSON, SERIALIZED_EXCEPTION)))
                .isEmpty();
    }

    @Test
    public void handlesWebApplicationExceptions() {
        testEncodingAndDecodingWebException(ClientErrorException.class, Response.Status.NOT_ACCEPTABLE);
        testEncodingAndDecodingWebException(ServerErrorException.class, Response.Status.BAD_GATEWAY);
        testEncodingAndDecodingWebException(WebApplicationException.class, Response.Status.NOT_MODIFIED);
    }

    private static void testEncodingAndDecodingWebException(
            Class<? extends WebApplicationException> exceptionClass, Response.Status status) {
        WebApplicationException exceptionToProcess;
        try {
            exceptionToProcess = exceptionClass
                    .getConstructor(String.class, Response.Status.class)
                    .newInstance(message, status);
        } catch (InstantiationException
                | IllegalAccessException
                | IllegalArgumentException
                | InvocationTargetException
                | NoSuchMethodException
                | SecurityException e) {
            throw new RuntimeException(e);
        }

        RemoteException exception = encodeAndDecode(exceptionToProcess).get();
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getStatus()).isEqualTo(status.getStatusCode());
        assertThat(exception.getError().errorCode()).isEqualTo(exceptionClass.getName());
        assertThat(exception.getMessage())
                .startsWith("RemoteException: " + exceptionClass.getName() + " (" + message + ")");
    }

    @Test
    public void extractsRemoteExceptionForAllErrorCodes() throws Exception {
        for (int code : ImmutableList.of(300, 400, 404, 500)) {
            RemoteException exception = decode(MediaType.APPLICATION_JSON, code, SERIALIZED_EXCEPTION)
                    .get();
            assertThat(exception.getCause()).isNull();
            assertThat(exception.getStatus()).isEqualTo(code);
            assertThat(exception.getError().errorCode())
                    .isEqualTo(ErrorType.FAILED_PRECONDITION.code().name());
            assertThat(exception.getError().errorName()).isEqualTo(ErrorType.FAILED_PRECONDITION.name());
            assertThat(exception.getMessage())
                    .isEqualTo("RemoteException: "
                            + ErrorType.FAILED_PRECONDITION.code().name()
                            + " ("
                            + ErrorType.FAILED_PRECONDITION.name()
                            + ") with instance ID "
                            + SERVICE_EXCEPTION.getErrorInstanceId());
        }
    }

    @Test
    public void handlesNotAuthorizedException() throws Exception {
        NotAuthorizedException originalException = new NotAuthorizedException(message, Response.Status.UNAUTHORIZED);

        RemoteException exception = encodeAndDecode(originalException).get();
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getStatus()).isEqualTo(Response.Status.UNAUTHORIZED.getStatusCode());
        assertThat(exception.getError().errorCode()).isEqualTo(NotAuthorizedException.class.getName());
        assertThat(exception.getError().errorName()).isEqualTo(message);
        assertThat(exception.getMessage())
                .isEqualTo("RemoteException: javax.ws.rs.NotAuthorizedException ("
                        + message
                        + ") with instance ID "
                        + exception.getError().errorInstanceId());
    }

    @Test
    public void testSpecificException() {
        RemoteException exception =
                encodeAndDecode(new IllegalArgumentException("msg")).get();
        assertThat(exception).isInstanceOf(RemoteException.class);
        assertThat(exception.getMessage()).startsWith("RemoteException: java.lang.IllegalArgumentException (msg)");
    }

    @Test
    public void doesNotHandleNonJsonMediaTypes() {
        assertThat(decode(MediaType.TEXT_PLAIN, STATUS_500, SERIALIZED_EXCEPTION))
                .isEmpty();
        assertThat(decode(MediaType.TEXT_HTML, STATUS_500, SERIALIZED_EXCEPTION))
                .isEmpty();
        assertThat(decode(MediaType.MULTIPART_FORM_DATA, STATUS_500, SERIALIZED_EXCEPTION))
                .isEmpty();
    }

    @Test
    public void doesNotHandleUnparseableBody() {
        assertThat(decode(MediaType.APPLICATION_JSON, STATUS_500, "not json")).isEmpty();
    }

    @Test
    public void doesNotHandleNullBody() {
        assertThat(decode(MediaType.APPLICATION_JSON, STATUS_500, null)).isEmpty();
    }

    private static Optional<RemoteException> encodeAndDecode(Exception exception) {
        Preconditions.checkArgument(!(exception instanceof ServiceException), "Use SerializableError#forException");
        Object error = SerializableError.builder()
                .errorCode(exception.getClass().getName())
                .errorName(exception.getMessage())
                .build();
        String json;
        try {
            json = SERVER_MAPPER.writeValueAsString(error);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        int status = (exception instanceof WebApplicationException)
                ? ((WebApplicationException) exception).getResponse().getStatus()
                : 400;
        return decode(MediaType.APPLICATION_JSON, status, json);
    }

    private static Optional<RemoteException> decode(String contentType, int status, @CheckForNull String body) {
        return handler.handle(response(status, contentType, body));
    }

    private static okhttp3.Response response(int code, String mediaType, @CheckForNull String body) {
        okhttp3.Response.Builder response = new okhttp3.Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("unused")
                .header(HttpHeaders.CONTENT_TYPE, mediaType);
        if (body != null) {
            response.body(ResponseBody.create(okhttp3.MediaType.parse(mediaType.toString()), body));
        }
        return response.build();
    }
}
