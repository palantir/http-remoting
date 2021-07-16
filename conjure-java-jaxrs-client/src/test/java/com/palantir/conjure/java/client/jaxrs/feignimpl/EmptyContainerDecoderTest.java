/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.serialization.ObjectMappers;
import feign.Response;
import feign.codec.Decoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import javax.annotation.Generated;
import javax.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

public class EmptyContainerDecoderTest {

    private static final ObjectMapper mapper = ObjectMappers.newClientObjectMapper();
    private static final Response HTTP_204 = Response.create(204, "No Content", Collections.emptyMap(), new byte[] {});
    private final Decoder delegate = mock(Decoder.class);
    private final EmptyContainerDecoder emptyContainerDecoder = new EmptyContainerDecoder(mapper, delegate);

    @Test
    public void http_200_uses_delegate_decoder() throws IOException {
        when(delegate.decode(any(), eq(String.class))).thenReturn("text response");
        Response http200 = Response.create(
                200,
                "OK",
                ImmutableMap.of(HttpHeaders.CONTENT_TYPE, ImmutableSet.of(MediaType.TEXT_PLAIN)),
                "text response",
                StandardCharsets.UTF_8);

        emptyContainerDecoder.decode(http200, String.class);
        verify(delegate, times(1)).decode(any(), any());
    }

    @Test
    public void prove_jackson_doesnt_work_out_of_the_box() throws IOException {
        assertThat(mapper.readValue("null", Alias1.class)).isNull(); // we want `Alias1.of(Optional.empty())`
        assertThat(mapper.readValue("null", Alias3.class)).isNull();
    }

    @Test
    public void capture_jackson_optional_handling_behaviour() throws IOException {
        assertThat(mapper.readValue("null", com.google.common.base.Optional.class))
                .isEqualTo(com.google.common.base.Optional.absent());
        assertThat(mapper.readValue("null", OptionalInt.class)).isEqualTo(OptionalInt.empty());
        assertThat(mapper.readValue("null", OptionalDouble.class)).isEqualTo(OptionalDouble.empty());
        assertThat(mapper.readValue("null", OptionalLong.class)).isEqualTo(OptionalLong.empty());
    }

    @Test
    public void capture_jackson_collection_behavior() throws IOException {
        assertThat(mapper.readValue("null", List.class)).isNull();
        assertThat(mapper.readValue("null", Map.class)).isNull();
        assertThat(mapper.readValue("null", Set.class)).isNull();
    }

    @Test
    public void http_204_turns_empty_body_into_alias_of_OptionalEmpty() throws IOException {
        assertThat(emptyContainerDecoder.decode(HTTP_204, Alias1.class)).isEqualTo(Alias1.of(Optional.empty()));
    }

    @Test
    public void http_204_turns_empty_body_into_alias_of_OptionalInt() throws IOException {
        assertThat(emptyContainerDecoder.decode(HTTP_204, Alias2.class)).isEqualTo(Alias2.of(OptionalInt.empty()));
    }

    @Test
    public void http_204_turns_empty_body_into_alias_of_OptionalDouble() throws IOException {
        assertThat(emptyContainerDecoder.decode(HTTP_204, Alias3.class)).isEqualTo(Alias3.of(OptionalDouble.empty()));
    }

    @Test
    public void http_204_can_handle_alias_of_alias_of_optional_string() throws IOException {
        assertThat(emptyContainerDecoder.decode(HTTP_204, AliasAlias1.class))
                .isEqualTo(AliasAlias1.of(Alias1.of(Optional.empty())));
    }

    @Test
    public void http_204_reuses_the_same_instance_each_time() throws IOException {
        Object first = emptyContainerDecoder.decode(HTTP_204, Alias1.class);
        Object second = emptyContainerDecoder.decode(HTTP_204, Alias1.class);
        assertThat(first).isSameAs(second);
    }

    @Generated("com.palantir.conjure.java.types.AliasGenerator")
    private static final class Alias1 {
        private final Optional<String> value;

        private Alias1(Optional<String> value) {
            Objects.requireNonNull(value, "value cannot be null");
            this.value = value;
        }

        @JsonValue
        public Optional<String> get() {
            return value;
        }

        @Override
        public String toString() {
            return value.toString();
        }

        @Override
        public boolean equals(Object other) {
            return this == other || (other instanceof Alias1 && this.value.equals(((Alias1) other).value));
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @JsonCreator
        public static Alias1 of(Optional<String> value) {
            return new Alias1(value);
        }
    }

    @Generated("com.palantir.conjure.java.types.AliasGenerator")
    private static final class Alias2 {
        private final OptionalInt value;

        private Alias2(OptionalInt value) {
            Objects.requireNonNull(value, "value cannot be null");
            this.value = value;
        }

        @JsonValue
        public OptionalInt get() {
            return value;
        }

        @Override
        public String toString() {
            return value.toString();
        }

        @Override
        public boolean equals(Object other) {
            return this == other || (other instanceof Alias2 && this.value.equals(((Alias2) other).value));
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @JsonCreator
        public static Alias2 of(OptionalInt value) {
            return new Alias2(value);
        }
    }

    @Generated("com.palantir.conjure.java.types.AliasGenerator")
    private static final class Alias3 {
        private final OptionalDouble value;

        private Alias3(OptionalDouble value) {
            Objects.requireNonNull(value, "value cannot be null");
            this.value = value;
        }

        @JsonValue
        public OptionalDouble get() {
            return value;
        }

        @Override
        public String toString() {
            return value.toString();
        }

        @Override
        public boolean equals(Object other) {
            return this == other || (other instanceof Alias3 && this.value.equals(((Alias3) other).value));
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @JsonCreator
        public static Alias3 of(OptionalDouble value) {
            return new Alias3(value);
        }
    }

    @Generated("com.palantir.conjure.java.types.AliasGenerator")
    private static final class AliasAlias1 {
        private final Alias1 value;

        private AliasAlias1(Alias1 value) {
            Objects.requireNonNull(value, "value cannot be null");
            this.value = value;
        }

        @JsonValue
        public Alias1 get() {
            return value;
        }

        @Override
        public String toString() {
            return value.toString();
        }

        @Override
        public boolean equals(Object other) {
            return this == other || (other instanceof AliasAlias1 && this.value.equals(((AliasAlias1) other).value));
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @JsonCreator
        public static AliasAlias1 of(Alias1 value) {
            return new AliasAlias1(value);
        }
    }
}
