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

package com.palantir.conjure.java.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.palantir.logsafe.Preconditions;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

public final class ObjectMappersTest {
    private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper();

    @Test
    public void deserializeJdk7ModuleObject() throws IOException {
        String pathSeparator = File.pathSeparator;
        String json = "\"" + pathSeparator + "tmp" + pathSeparator + "foo.txt\"";

        assertThat(MAPPER.readValue(json, Path.class)).isEqualTo(Paths.get(":tmp:foo.txt"));
    }

    @Test
    public void serializeJdk7ModuleObject() throws JsonProcessingException {
        Path path = Paths.get(":tmp:foo.txt");
        assertThat(MAPPER.writeValueAsString(path)).isEqualTo("\":tmp:foo.txt\"");
    }

    @Test
    public void deserializeJdk8ModulePresentOptional() throws IOException {
        assertThat(MAPPER.readValue("\"Test\"", Optional.class)).isEqualTo(Optional.of("Test"));
    }

    @Test
    public void deserializeJdk8ModuleAbsentOptional() throws IOException {
        assertThat(MAPPER.readValue("null", Optional.class)).isNotPresent();
    }

    @Test
    public void serializeJdk8ModulePresentOptional() throws JsonProcessingException {
        assertThat(MAPPER.writeValueAsString(Optional.of("Test"))).isEqualTo("\"Test\"");
    }

    @Test
    public void serializeJdk8ModuleEmptyOptional() throws JsonProcessingException {
        assertThat(MAPPER.writeValueAsString(Optional.empty())).isEqualTo("null");
    }

    @Test
    public void testMappersReturnNewInstance() {
        assertThat(ObjectMappers.newClientObjectMapper()).isNotSameAs(ObjectMappers.newClientObjectMapper());
    }

    @Test
    public void testJdk8DateTimeSerialization() throws IOException {
        Duration duration = Duration.ofMinutes(60 * 50 + 1); // 50h, 1min
        assertThat(ser(duration)).isEqualTo("\"PT50H1M\"");
        assertThat(serDe(duration, Duration.class)).isEqualTo(duration);

        OffsetDateTime offsetDateTime = OffsetDateTime.of(2001, 2, 3, 4, 5, 6, 7, ZoneOffset.ofHours(-5));
        assertThat(ser(offsetDateTime)).isEqualTo("\"2001-02-03T04:05:06.000000007-05:00\"");
        assertThat(serDe(offsetDateTime, OffsetDateTime.class)).isEqualTo(offsetDateTime);

        ZonedDateTime zoneDateTime = ZonedDateTime.of(2001, 2, 3, 4, 5, 6, 7, ZoneId.of(ZoneId.SHORT_IDS.get("EST")));
        assertThat(ser(zoneDateTime)).isEqualTo("\"2001-02-03T04:05:06.000000007-05:00\"");
        assertThat(serDe(zoneDateTime, ZonedDateTime.class)).isEqualTo(zoneDateTime);

        LocalDate localDate = LocalDate.of(2001, 2, 3);
        assertThat(ser(localDate)).isEqualTo("\"2001-02-03\"");
        assertThat(serDe(localDate, LocalDate.class)).isEqualTo(localDate);
    }

    @Test
    public void testMapWithNullValues() throws IOException {
        // This is potentially a bug, see conjure-java#291
        assertThat(MAPPER.<Map<String, String>>readValue(
                        "{\"test\":null}", new TypeReference<Map<String, String>>() {}))
                .isEqualTo(Collections.singletonMap("test", null));
    }

    @Test
    public void testMapWithNullKeys() {
        assertThatThrownBy(() -> MAPPER.readValue("{null: \"test\"}", new TypeReference<Map<String, String>>() {}))
                .isInstanceOf(JsonParseException.class);
    }

    @Test
    public void testLongDeserializationFromString() throws IOException {
        assertThat(MAPPER.readValue("\"1\"", Long.class)).isEqualTo(1L);
    }

    @Test
    public void testLongTypeDeserializationFromString() throws IOException {
        assertThat(MAPPER.readValue("\"1\"", Long.TYPE)).isEqualTo(1L);
    }

    @Test
    public void testLongBeanTypeDeserializationFromString() throws IOException {
        assertThat(MAPPER.readValue("{\"value\":\"1\"}", LongBean.class)).isEqualTo(new LongBean(1L));
    }

    @Test
    public void testLongBeanTypeDeserializationFromNumber() throws IOException {
        assertThat(MAPPER.readValue("{\"value\":\"1\"}", LongBean.class)).isEqualTo(new LongBean(1L));
    }

    static final class LongBean {
        @JsonProperty
        private long value;

        LongBean() {}

        LongBean(long value) {
            setValue(value);
        }

        public long getValue() {
            return value;
        }

        public void setValue(long value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            LongBean that = (LongBean) other;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(value);
        }

        @Override
        public String toString() {
            return "LongBean{value=" + value + '}';
        }
    }

    @Test
    public void testOptionalLongTypeDeserializationFromString() throws IOException {
        assertThat(MAPPER.readValue("\"1\"", OptionalLong.class)).hasValue(1L);
    }

    @Test
    public void testOptionalLongBeanTypeDeserializationFromString() throws IOException {
        assertThat(MAPPER.readValue("{\"value\":\"1\"}", OptionalLongBean.class))
                .isEqualTo(new OptionalLongBean(OptionalLong.of(1L)));
    }

    @Test
    public void testOptionalLongBeanTypeDeserializationFromNumber() throws IOException {
        assertThat(MAPPER.readValue("{\"value\":1}", OptionalLongBean.class))
                .isEqualTo(new OptionalLongBean(OptionalLong.of(1L)));
    }

    static final class OptionalLongBean {
        @JsonProperty
        private OptionalLong value = OptionalLong.empty();

        OptionalLongBean() {}

        OptionalLongBean(OptionalLong value) {
            setValue(value);
        }

        public OptionalLong getValue() {
            return value;
        }

        public void setValue(OptionalLong value) {
            this.value = Preconditions.checkNotNull(value, "value");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            OptionalLongBean that = (OptionalLongBean) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return "OptionalLongBean{value=" + value + '}';
        }
    }

    @Test
    public void testLongDeserializationFromJsonNumber() throws IOException {
        assertThat(MAPPER.readValue("1", Long.class)).isEqualTo(1L);
    }

    @Test
    public void testOptionalLongDeserializationFromJsonNumber() throws IOException {
        assertThat(MAPPER.readValue("1", OptionalLong.class)).hasValue(1L);
    }

    @Test
    public void testLongTypeDeserializationFromJsonNumber() throws IOException {
        assertThat(MAPPER.readValue("1", Long.TYPE)).isEqualTo(1L);
    }

    @Test
    public void testLongDeserializationFromJsonNull() throws IOException {
        assertThat(MAPPER.readValue("null", Long.class)).isNull();
    }

    @Test
    public void testOptionalLongDeserializationFromJsonNull() throws IOException {
        assertThat(MAPPER.readValue("null", OptionalLong.class)).isEmpty();
    }

    @Test
    public void testLongOverflowDeserialization() {
        BigInteger large = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertThatThrownBy(() -> MAPPER.readValue("" + large, Long.TYPE))
                .isInstanceOf(InputCoercionException.class)
                .hasMessageContaining("out of range of long");
    }

    @Test
    public void testLongAsStringOverflowDeserialization() {
        BigInteger large = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertThatThrownBy(() -> MAPPER.readValue("\"" + large + "\"", Long.TYPE))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("not a valid");
    }

    @Test
    public void testOptionalLongOverflowDeserialization() {
        BigInteger large = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertThatThrownBy(() -> MAPPER.readValue("" + large, OptionalLong.class))
                .isInstanceOf(InputCoercionException.class)
                .hasMessageContaining("out of range of long");
    }

    @Test
    public void testOptionalLongAsStringOverflowDeserialization() {
        BigInteger large = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertThatThrownBy(() -> MAPPER.readValue("\"" + large + "\"", OptionalLong.class))
                .isInstanceOf(InvalidFormatException.class)
                .hasMessageContaining("not a valid");
    }

    @Test
    public void testIntegerOverflowDeserialization() {
        assertThatThrownBy(() -> MAPPER.readValue("" + Long.MAX_VALUE, Integer.TYPE))
                .isInstanceOf(InputCoercionException.class)
                .hasMessageContaining("out of range of int");
    }

    @Test
    public void testOptionalIntOverflowDeserialization() {
        assertThatThrownBy(() -> MAPPER.readValue("" + Long.MAX_VALUE, OptionalInt.class))
                .isInstanceOf(InputCoercionException.class)
                .hasMessageContaining("out of range of int");
    }

    private static String ser(Object object) throws IOException {
        return MAPPER.writeValueAsString(object);
    }

    private static <T> T serDe(Object object, Class<T> clazz) throws IOException {
        return MAPPER.readValue(ser(object), clazz);
    }
}
