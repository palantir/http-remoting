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

import com.palantir.tracing.Tracer;
import com.palantir.tracing.api.TraceHttpHeaders;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.ClassRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public final class TracerTest {

    private static final ch.qos.logback.classic.Logger log =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TracerTest.class);

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(TracingTestServer.class, "src/test/resources/test-server.yml");

    private WebTarget target;
    private ch.qos.logback.classic.Level previousLoggerLevel;

    @BeforeEach
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);
        previousLoggerLevel = log.getLevel();
    }

    @AfterEach
    public void after() throws Exception {
        log.setLevel(previousLoggerLevel);
    }

    @Test
    public void testTracingFilterIsApplied() {
        Response response = target.path("/trace")
                .request()
                .header(TraceHttpHeaders.TRACE_ID, "traceId")
                .header(TraceHttpHeaders.PARENT_SPAN_ID, "parentSpanId")
                .header(TraceHttpHeaders.SPAN_ID, "spanId")
                .get();
        assertThat(response.getStatus()).isEqualTo(Status.OK.getStatusCode());
        assertThat(response.readEntity(String.class)).isEqualTo("traceId");
        assertThat(response.getHeaderString(TraceHttpHeaders.TRACE_ID)).isEqualTo("traceId");
        assertThat(response.getHeaderString(TraceHttpHeaders.SPAN_ID)).isNull();
        assertThat(response.getHeaderString(TraceHttpHeaders.PARENT_SPAN_ID)).isNull();
    }

    @Test
    public void testLogAppenderCanAccessTraceId() throws Exception {
        // Augment logger with custom appender whose output we can read
        ch.qos.logback.classic.LoggerContext lc =
                (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.encoder.PatternLayoutEncoder ple =
                new ch.qos.logback.classic.encoder.PatternLayoutEncoder();
        ple.setPattern("traceId: %X{traceId} %-5level [%thread]: %message%n");
        ple.setContext(lc);
        ple.start();
        ch.qos.logback.core.OutputStreamAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.OutputStreamAppender<>();
        appender.setEncoder(ple);
        appender.setContext(lc);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        appender.setOutputStream(byteStream);
        appender.start();
        log.addAppender(appender);

        // Invoke server and observe servers log messages; note that the server uses the same logger at INFO.
        log.setLevel(ch.qos.logback.classic.Level.INFO);
        target.path("trace")
                .request()
                .header(TraceHttpHeaders.TRACE_ID, "myTraceId")
                .get();
        assertThat(byteStream.toString(StandardCharsets.UTF_8.name())).startsWith("traceId: myTraceId");
    }

    public static class TracingTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration _config, final Environment env) throws Exception {
            env.jersey().register(ConjureJerseyFeature.INSTANCE);
            env.jersey().register(new TracingTestResource());
        }
    }

    public static final class TracingTestResource implements TracingTestService {
        @Override
        public String getTraceOperation() {
            log.info("foo");
            return Tracer.getTraceId();
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface TracingTestService {
        @GET
        @Path("/trace")
        String getTraceOperation();
    }
}
