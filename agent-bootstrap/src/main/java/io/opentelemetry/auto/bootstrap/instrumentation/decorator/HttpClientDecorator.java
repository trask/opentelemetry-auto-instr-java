/*
 * Copyright The OpenTelemetry Authors
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
package io.opentelemetry.auto.bootstrap.instrumentation.decorator;

import io.opentelemetry.auto.bootstrap.instrumentation.aiappid.AiAppId;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class HttpClientDecorator<REQUEST, RESPONSE> extends ClientDecorator {

  public static final String DEFAULT_SPAN_NAME = "HTTP request";

  protected abstract String method(REQUEST request);

  protected abstract URI url(REQUEST request) throws URISyntaxException;

  protected abstract Integer status(RESPONSE response);

  protected String getAiAppIdResponseHeader(final RESPONSE response) {
    return null;
  }

  public Span getOrCreateSpan(REQUEST request, Tracer tracer) {
    return getOrCreateSpan(spanNameForRequest(request), tracer);
  }

  public String spanNameForRequest(final REQUEST request) {
    if (request == null) {
      return DEFAULT_SPAN_NAME;
    }
    final String method = method(request);
    return method != null ? "HTTP " + method : DEFAULT_SPAN_NAME;
  }

  public Span onRequest(final Span span, final REQUEST request) {
    assert span != null;
    if (request != null) {
      span.setAttribute(Tags.HTTP_METHOD, method(request));

      // Copy of HttpServerDecorator url handling
      try {
        final URI url = url(request);
        if (url != null) {
          final StringBuilder urlBuilder = new StringBuilder();
          if (url.getScheme() != null) {
            urlBuilder.append(url.getScheme());
            urlBuilder.append("://");
          }
          if (url.getHost() != null) {
            urlBuilder.append(url.getHost());
            span.setAttribute(MoreTags.NET_PEER_NAME, url.getHost());
            if (url.getPort() > 0) {
              span.setAttribute(MoreTags.NET_PEER_PORT, url.getPort());
              if (url.getPort() != 80 && url.getPort() != 443) {
                urlBuilder.append(":");
                urlBuilder.append(url.getPort());
              }
            }
          }
          final String path = url.getPath();
          if (path.isEmpty()) {
            urlBuilder.append("/");
          } else {
            urlBuilder.append(path);
          }
          final String query = url.getQuery();
          if (query != null) {
            urlBuilder.append("?").append(query);
          }
          final String fragment = url.getFragment();
          if (fragment != null) {
            urlBuilder.append("#").append(fragment);
          }

          span.setAttribute(Tags.HTTP_URL, urlBuilder.toString());

          if (Config.get().isHttpClientTagQueryString()) {
            span.setAttribute(MoreTags.HTTP_QUERY, query);
            span.setAttribute(MoreTags.HTTP_FRAGMENT, fragment);
          }
        }
      } catch (final Exception e) {
        log.debug("Error tagging url", e);
      }
    }
    return span;
  }

  public Span onResponse(final Span span, final RESPONSE response) {
    assert span != null;
    if (response != null) {
      final Integer status = status(response);
      if (status != null) {
        span.setAttribute(Tags.HTTP_STATUS, status);

        if (Config.get().getHttpClientErrorStatuses().get(status)) {
          span.setStatus(Status.UNKNOWN);
        }
      }
      final String responseHeader = getAiAppIdResponseHeader(response);
      setTargetAppId(span, responseHeader);
    }
    return span;
  }

  public static void setTargetAppId(final Span span, final String responseHeader) {
    if (responseHeader == null) {
      return;
    }
    final int index = responseHeader.indexOf('=');
    if (index == -1) {
      return;
    }
    final String targetAppId = responseHeader.substring(index + 1);
    span.setAttribute(AiAppId.SPAN_TARGET_ATTRIBUTE_NAME, targetAppId);
  }
}
