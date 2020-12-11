/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.httpclients;

import static io.opentelemetry.instrumentation.spring.httpclients.RestTemplateTracer.tracer;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Wraps RestTemplate requests in a span. Adds the current span context to request headers. */
public final class RestTemplateInterceptor implements ClientHttpRequestInterceptor {

  private final Tracer tracer;

  public RestTemplateInterceptor(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    Context context = tracer().startOperation(Context.current(), request, request.getHeaders());
    try (Scope ignored = context.makeCurrent()) {
      ClientHttpResponse response = execution.execute(request, body);
      tracer().end(context, response);
      return response;
    }
  }
}
