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

package com.palantir.conjure.java.client.retrofit2;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import java.util.Arrays;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public final class Retrofit2ClientQueryParamHandlingTest extends TestBase {

    @Rule
    public final MockWebServer server = new MockWebServer();

    private Service proxy;

    @BeforeEach
    public void before() {
        HttpUrl url = server.url("/");
        proxy = Retrofit2Client.create(
                Service.class, AGENT, new HostMetricsRegistry(), createTestConfig(url.toString()));
        MockResponse mockResponse = new MockResponse().setResponseCode(204);
        server.enqueue(mockResponse);
    }

    public interface Service {
        @GET("/queryList")
        Call<Void> queryList(@Query("req") List<String> req);
    }

    @Test
    public void testList() throws Exception {
        proxy.queryList(Arrays.asList("str1", "str2")).execute();
        RecordedRequest takeRequest = server.takeRequest();
        assertThat(takeRequest.getRequestLine()).isEqualTo("GET /queryList?req=str1&req=str2 HTTP/1.1");
    }
}
