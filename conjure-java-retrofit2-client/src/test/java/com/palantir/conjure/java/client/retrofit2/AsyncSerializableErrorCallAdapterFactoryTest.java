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

import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.retrofit2.AsyncSerializableErrorCallAdapterFactory.CompletableFutureBodyCallAdapter;
import com.palantir.conjure.java.client.retrofit2.AsyncSerializableErrorCallAdapterFactory.ListenableFutureBodyCallAdapter;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AsyncSerializableErrorCallAdapterFactoryTest {
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Response<String> response;

    @Mock
    private Call<String> call;

    @Captor
    private ArgumentCaptor<Callback<String>> callbackCaptor;

    @Test
    public void testResponseNotLeakedIfCancelled_completable() {
        CompletableFutureBodyCallAdapter<String> adapter = new CompletableFutureBodyCallAdapter<>(String.class);
        CompletableFuture<String> result = adapter.adapt(call);
        verify(call).enqueue(callbackCaptor.capture());
        Callback<String> callback = callbackCaptor.getValue();
        result.cancel(true);
        callback.onResponse(call, response);
        verify(response.raw().body()).close();
    }

    @Test
    public void testResponseNotLeakedIfCancelled_listenable() {
        ListenableFutureBodyCallAdapter<String> adapter = new ListenableFutureBodyCallAdapter<>(String.class);
        ListenableFuture<String> result = adapter.adapt(call);
        verify(call).enqueue(callbackCaptor.capture());
        Callback<String> callback = callbackCaptor.getValue();
        result.cancel(true);
        callback.onResponse(call, response);
        verify(response.raw().body()).close();
    }
}
