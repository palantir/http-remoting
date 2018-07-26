/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.remoting3.retrofit2;

import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.CallAdapter.Factory;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * We want to be lenient and interpret "null" or empty responses (including 204) as the empty value if the expected
 * type is a collection. Jackson can only do this for fields inside an object, but for top-level fields we have to do
 * this manually.
 * <p>
 * This class is the counterpart of {@link CoerceNullCollectionsConverterFactory} and handles coercion of 204/205
 * responses to the default values for collections (without this, the {@link Response} body would be null,
 * according to {@link retrofit2.OkHttpCall#parseResponse(okhttp3.Response)}).
 */
// TODO(dsanduleac): link to spec
final class CoerceNullCollectionsCallAdapterFactory extends CallAdapter.Factory {

    private final CallAdapter.Factory adapterDelegate;

    CoerceNullCollectionsCallAdapterFactory(Factory adapterDelegate) {
        this.adapterDelegate = adapterDelegate;
    }

    @Nullable
    @Override
    public CallAdapter<?, ?> get(
            Type returnType, Annotation[] annotations, Retrofit retrofit) {
        if (!(returnType instanceof ParameterizedType)) {
            // TODO(dsanduleac): can we relax this? Right now we only support Call / CompletableFuture
            throw new SafeIllegalStateException("Function must return a ParametrizedType",
                    SafeArg.of("type", returnType));
        }

        Type innerType = getParameterUpperBound(0, (ParameterizedType) returnType);
        Class rawType = getRawType(innerType);

        CallAdapter<?, ?> delegateAdapter = adapterDelegate.get(returnType, annotations, retrofit);

        if (delegateAdapter == null) {
            Preconditions.checkState(getRawType(returnType) == Call.class,
                    "Lacking a delegate adapter, this CallAdapter only supports a returnType of 'Call'",
                    SafeArg.of("returnType", returnType));
            // This is essentially DefaultCallAdapterFactory but we can't access it :(
            delegateAdapter = new CallAdapter<Object, Object>() {
                @Override
                public Type responseType() {
                    return innerType;
                }

                @Override
                public Object adapt(Call<Object> call) {
                    return call;
                }
            };
        }

        if (List.class.isAssignableFrom(rawType)) {
            return new DefaultingOnNullAdapter<>(delegateAdapter, Collections::emptyList);
        } else if (Set.class.isAssignableFrom(rawType)) {
            return new DefaultingOnNullAdapter<>(delegateAdapter, Collections::emptySet);
        } else if (Map.class.isAssignableFrom(rawType)) {
            return new DefaultingOnNullAdapter<>(delegateAdapter, Collections::emptyMap);
        }
        return delegateAdapter;
    }

    /**
     * An 'operator' {@link CallAdapter} that converts {@code Call<R>} into {@code Call<R>} but upon
     * executing/enqueing the call, it replaces the resulting {@link Response}'s deserialized body with the given
     * default value if the body was {@code null}.
     */
    private static final class DefaultingOnNullAdapter<R> implements CallAdapter<R, Object> {
        private final Supplier<R> defaultValue;
        private final CallAdapter delegate;

        private DefaultingOnNullAdapter(CallAdapter delegate, Supplier<R> defaultValue) {
            this.delegate = delegate;
            this.defaultValue = defaultValue;
        }

        @Override
        public Type responseType() {
            return delegate.responseType();
        }

        @Override
        public Object adapt(Call<R> call) {
            DefaultingCall<R> defaultingCall = new DefaultingCall<>(call, defaultValue);
            return delegate.adapt(defaultingCall);
        }
    }

    /**
     * A {@link retrofit2.Call} that returns a default if the result coming out of the delegate call
     * (probably {@link retrofit2.OkHttpCall}) is null.
     */
    private static final class DefaultingCall<R> implements Call<R> {
        private final Call<R> delegate;
        private final Supplier<R> defaultValue;

        private DefaultingCall(Call<R> delegate, Supplier<R> defaultValue) {
            this.delegate = delegate;
            this.defaultValue = defaultValue;
        }

        @Override
        public Response<R> execute() throws IOException {
            return adaptResponse(delegate.execute());
        }

        /**
         * If the response was successful and is empty (has no body), then return the default value.
         * Otherwise, return the response unchanged.
         */
        private Response<R> adaptResponse(Response<R> response) {
            R body = response.body();
            if (response.isSuccessful() && body == null) {
                return Response.success(defaultValue.get(), response.raw());
            }
            return response;
        }

        @Override
        public void enqueue(Callback<R> callback) {
            delegate.enqueue(new Callback<R>() {
                @Override
                public void onResponse(Call<R> call, Response<R> response) {
                    callback.onResponse(call, adaptResponse(response));
                }

                @Override
                public void onFailure(Call<R> call, Throwable throwable) {
                    callback.onFailure(call, throwable);
                }
            });
        }

        // Delegates

        @Override
        public boolean isExecuted() {
            return delegate.isExecuted();
        }

        @Override
        public void cancel() {
            delegate.cancel();
        }

        @Override
        public boolean isCanceled() {
            return delegate.isCanceled();
        }

        @SuppressWarnings({"checkstyle:NoClone", "checkstyle:SuperClone"})
        @Override
        public Call<R> clone() {
            return new DefaultingCall<>(delegate.clone(), defaultValue);
        }

        @Override
        public Request request() {
            return delegate.request();
        }
    }
}
