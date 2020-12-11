/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static io.opentelemetry.api.trace.Span.Kind.CLIENT;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import okhttp3.Request;
import okhttp3.Response;

public class KubernetesClientTracer extends HttpClientTracer<Request, Response> {
  private static final KubernetesClientTracer TRACER = new KubernetesClientTracer();

  public static KubernetesClientTracer tracer() {
    return TRACER;
  }

  /**
   * This method is used to generate an acceptable CLIENT span (operation) name based on a given
   * KubernetesRequestDigest.
   */
  public Context startOperation(Context parentContext, Request request) {
    if (inClientSpan(parentContext)) {
      return noopContext(parentContext);
    }
    KubernetesRequestDigest digest = KubernetesRequestDigest.parse(request);
    Span span =
        tracer
            .spanBuilder(digest.toString())
            .setSpanKind(CLIENT)
            .setParent(parentContext)
            .setAttribute("namespace", digest.getResourceMeta().getNamespace())
            .setAttribute("name", digest.getResourceMeta().getName())
            .startSpan();
    // TODO (trask) no propagation implemented yet?
    return withClientSpan(parentContext, span);
  }

  @Override
  protected String method(Request httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected URI url(Request httpRequest) {
    return httpRequest.url().uri();
  }

  @Override
  protected Integer status(Response httpResponse) {
    return httpResponse.code();
  }

  @Override
  protected String requestHeader(Request request, String name) {
    return request.header(name);
  }

  @Override
  protected String responseHeader(Response response, String name) {
    return response.header(name);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.kubernetes-client";
  }
}
