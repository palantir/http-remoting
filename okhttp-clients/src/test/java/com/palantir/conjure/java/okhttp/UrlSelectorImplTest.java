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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.testing.Assertions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public final class UrlSelectorImplTest extends TestBase {

    @Mock
    Clock clock;

    @BeforeEach
    public void before() {
        when(clock.instant()).thenReturn(Instant.EPOCH);
    }

    @Test
    public void mustSpecifyAtLeastOneUrl() {
        assertThatThrownBy(() -> UrlSelectorImpl.create(set(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Must specify at least one URL");
    }

    @Test
    public void baseUrlsMustBeCanonical() {
        for (String url : new String[] {"user:pass@foo.com/path", ""}) {
            Assertions.assertThatLoggableExceptionThrownBy(() -> UrlSelectorImpl.create(list(url), false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasLogMessage("Not a valid URL")
                    .hasExactlyArgs(UnsafeArg.of("url", url));
        }

        for (String url : new String[] {
            "http://user:pass@foo.com/path", "http://foo.com/path?bar",
        }) {
            Assertions.assertThatLoggableExceptionThrownBy(() -> UrlSelectorImpl.create(list(url), false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasLogMessage("Base URLs must be 'canonical' and consist of schema, host, port, and path only")
                    .hasExactlyArgs(UnsafeArg.of("url", url));
        }

        for (String url : new String[] {
            "http://foo.com/path", "http://foo.com:80/path", "http://foo.com:8080",
        }) {
            UrlSelectorImpl.create(list(url), false);
        }
    }

    @Test
    public void testRedirectTo_succeedsWhenRequestedBaseUrlPathIsPrefixOfCurrentPath() {
        String url1 = "http://foo/a";
        String url2 = "https://bar:8080/a/b/c";
        List<String> baseUrls = list(url1, url2);
        UrlSelectorImpl selector = UrlSelectorImpl.create(baseUrls, false);

        // Redirect to self is OK.
        assertThat(selector.redirectTo(parse(url1), url1)).contains(parse(url1));
        assertThat(selector.redirectTo(parse(url1 + "/123?abc"), url1)).contains(parse(url1 + "/123?abc"));

        // Redirect to other is OK when current path is a prefix of the requested base URL.
        // Note: url2 -->url1 redirects across schema+host+port
        assertThat(selector.redirectTo(parse(url2), url1)).contains(parse(url1 + "/b/c"));
        assertThat(selector.redirectTo(parse(url2 + "/123?abc"), url1)).contains(parse(url1 + "/b/c/123?abc"));
    }

    @Test
    public void testRedirectTo_updatesCurrentPointer() {
        UrlSelectorImpl selector = UrlSelectorImpl.create(list("http://foo/a", "http://bar/a"), false);
        HttpUrl current = HttpUrl.parse("http://baz/a/b/path");
        String redirectTo = "http://bar/a";

        assertThat(selector.redirectTo(current, redirectTo)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToCurrent(current)).contains(HttpUrl.parse("http://bar/a/b/path"));
    }

    @Test
    public void testRedirectTo_findsMatchesWithCaseInsensitiveHostNames() {
        String baseUrl = "http://foo/a";
        UrlSelectorImpl selector = UrlSelectorImpl.create(list(baseUrl), false);

        assertThat(selector.redirectTo(parse(baseUrl), "http://FOO/a")).contains(parse(baseUrl));
    }

    @Test
    public void testRedirectTo_doesNotFindMatchesForCaseSentitivePaths() {
        String baseUrl = "http://foo/a";
        UrlSelectorImpl selector = UrlSelectorImpl.create(list(baseUrl), false);

        assertThat(selector.redirectTo(parse(baseUrl), "http://foo/A")).isEmpty();
    }

    @Test
    public void testRedirectTo_failsWhenRequestedBaseUrlPathIsNotPrefixOfCurrentPath() {
        String url1 = "http://foo/a";
        String url2 = "https://bar:8080/a/b/c";
        UrlSelectorImpl selector = UrlSelectorImpl.create(list(url1, url2), false);

        assertThat(selector.redirectTo(parse(url1), url2)).isEmpty();
    }

    @Test
    public void testIsBaseUrlFor() {
        // Negative cases
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/a"), parse("https://foo/a")))
                .isFalse();
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/a"), parse("http://bar/a")))
                .isFalse();
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/a"), parse("http://foo:8080/a")))
                .isFalse();
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/b"), parse("http://foo/a")))
                .isFalse();

        // Positive cases: schema, host, port must be equal, path must be a prefix
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/a"), parse("http://foo/a")))
                .isTrue();
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/a"), parse("http://foo/a/")))
                .isTrue();
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo/a"), parse("http://foo/a/b")))
                .isTrue();
        assertThat(UrlSelectorImpl.isBaseUrlFor(parse("http://foo"), parse("http://foo/a")))
                .isTrue();
    }

    @Test
    public void testRedirectToNext_updatesCurrentPointer() {
        UrlSelectorImpl selector = UrlSelectorImpl.create(list("http://foo/a", "http://bar/a"), false);
        HttpUrl current = HttpUrl.parse("http://baz/a/b/path");

        assertThat(selector.redirectToNext(current)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToCurrent(current)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNext(current)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToCurrent(current)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToNext(current)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToCurrent(current)).contains(HttpUrl.parse("http://bar/a/b/path"));
    }

    @Test
    public void testRedirectToNext_isAFunctionOfArgument_updatesCurrent() {
        UrlSelectorImpl selector = UrlSelectorImpl.create(list("http://foo/a", "http://bar/a"), false);

        HttpUrl baseIsFoo = HttpUrl.parse("http://foo/a/b/path");
        HttpUrl baseIsBar = HttpUrl.parse("http://bar/a/b/path");
        HttpUrl baseIsBaz = HttpUrl.parse("http://baz/a/b/path");

        // calling twice with the same argument gives the same result, i.e. move forward by one
        assertThat(selector.redirectToNext(baseIsFoo)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNext(baseIsFoo)).contains(HttpUrl.parse("http://bar/a/b/path"));
        // ...and bar is now the current
        assertThat(selector.redirectToCurrent(baseIsBaz)).contains(HttpUrl.parse("http://bar/a/b/path"));

        // bar goes back to foo
        assertThat(selector.redirectToNext(baseIsBar)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToCurrent(baseIsBaz)).contains(HttpUrl.parse("http://foo/a/b/path"));

        // baz goes to the next after the current
        // current is foo, so we expect bar
        assertThat(selector.redirectToNext(baseIsBaz)).contains(HttpUrl.parse("http://bar/a/b/path"));
    }

    @Test
    public void testRedirectToNextRoundRobin() {
        UrlSelectorImpl selector = UrlSelectorImpl.create(list("http://foo/a", "http://bar/a"), false);
        HttpUrl current = HttpUrl.parse("http://baz/a/b/path");
        String redirectTo = "http://bar/a";

        assertThat(selector.redirectTo(current, redirectTo)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNextRoundRobin(current)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToNextRoundRobin(current)).contains(HttpUrl.parse("http://bar/a/b/path"));
    }

    @Test
    public void testMarkUrlAsFailed_withoutCooldown() {
        UrlSelectorImpl selector = UrlSelectorImpl.create(list("http://foo/a", "http://bar/a"), false);
        HttpUrl current = HttpUrl.parse("http://baz/a/b/path");

        selector.markAsFailed(HttpUrl.parse("http://bar/a/b/path"));

        assertThat(selector.redirectToNextRoundRobin(current)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNextRoundRobin(current)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToNextRoundRobin(current)).contains(HttpUrl.parse("http://bar/a/b/path"));
    }

    @Test
    public void testMarkUrlAsFailed_roundRobin_withCooldown() {
        Duration failedUrlCooldown = Duration.ofMillis(100);

        UrlSelectorImpl selector = UrlSelectorImpl.createWithFailedUrlCooldown(
                list("http://foo/a", "http://bar/a"), false, failedUrlCooldown, clock);
        HttpUrl requestUrl = HttpUrl.parse("http://ignored/a/b/path");

        selector.markAsFailed(HttpUrl.parse("http://bar/a/b/path"));

        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));

        when(clock.instant()).thenReturn(Instant.EPOCH.plus(failedUrlCooldown));

        // we're intentionally only trying 'bar' once as we're not confident it's healthy yet - waiting for
        // markAsSucceeded
        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));

        selector.markAsSucceeded(HttpUrl.parse("http://bar/a/b/path"));

        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
    }

    @Test
    public void testAllUrlsFailed_roundRobin_withCooldown() {
        Duration failedUrlCooldown = Duration.ofMillis(100);

        UrlSelectorImpl selector = UrlSelectorImpl.createWithFailedUrlCooldown(
                list("http://foo/a", "http://bar/a"), false, failedUrlCooldown, clock);
        HttpUrl requestUrl = HttpUrl.parse("http://ignored/a/b/path");

        selector.markAsFailed(HttpUrl.parse("http://foo/a/b/path"));
        selector.markAsFailed(HttpUrl.parse("http://bar/a/b/path"));

        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));

        selector.markAsSucceeded(HttpUrl.parse("http://bar/a/b/path"));

        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNextRoundRobin(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
    }

    @Test
    public void testMarkUrlAsFailed_pinUntilError_withCooldown() {
        Duration failedUrlCooldown = Duration.ofMillis(100);

        UrlSelectorImpl selector = UrlSelectorImpl.createWithFailedUrlCooldown(
                list("http://foo/a", "http://bar/a"), false, failedUrlCooldown, clock);
        HttpUrl requestUrl = HttpUrl.parse("http://ignored/a/b/path");

        selector.markAsFailed(HttpUrl.parse("http://bar/a/b/path"));

        assertThat(selector.redirectToCurrent(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToCurrent(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));

        when(clock.instant()).thenReturn(Instant.EPOCH.plus(failedUrlCooldown));

        assertThat(selector.redirectToCurrent(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToCurrent(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));
        // The timer has passed, but because it's still failed, 'bar' will only be tried once
        assertThat(selector.redirectToNext(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNext(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToNext(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));

        selector.markAsSucceeded(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNext(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToCurrent(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToCurrent(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
    }

    @Test
    public void testAllUrlsFailed_pinUntilError_withCooldown() {
        Duration failedUrlCooldown = Duration.ofMillis(100);

        UrlSelectorImpl selector = UrlSelectorImpl.createWithFailedUrlCooldown(
                list("http://foo/a", "http://bar/a"), false, failedUrlCooldown, clock);
        HttpUrl requestUrl = HttpUrl.parse("http://ignored/a/b/path");

        selector.markAsFailed(HttpUrl.parse("http://foo/a/b/path"));
        selector.markAsFailed(HttpUrl.parse("http://bar/a/b/path"));

        assertThat(selector.redirectToCurrent(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToCurrent(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToCurrent(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToCurrent(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToNext(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNext(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));
        assertThat(selector.redirectToNext(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNext(requestUrl)).contains(HttpUrl.parse("http://foo/a/b/path"));

        selector.markAsSucceeded(HttpUrl.parse("http://bar/a/b/path"));

        assertThat(selector.redirectToCurrent(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToCurrent(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNext(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
        assertThat(selector.redirectToNext(requestUrl)).contains(HttpUrl.parse("http://bar/a/b/path"));
    }

    @Test
    public void testWorksWithWebSockets() {
        Request wsRequest = new Request.Builder().url("wss://foo/a").build();
        UrlSelectorImpl selector = UrlSelectorImpl.create(ImmutableList.of("wss://foo/", "wss://bar/"), false);

        // Silently replace web socket URLs with HTTP URLs. See https://github.com/square/okhttp/issues/1652.
        assertThat(selector.redirectToNext(wsRequest.url())).isEqualTo(Optional.of(parse("https://bar/a")));
    }

    private static HttpUrl parse(String url) {
        return HttpUrl.parse(url);
    }
}
