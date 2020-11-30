/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.extension.annotations.WithSpan;
import io.opentelemetry.api.trace.Span.Kind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WithSpanTracer extends BaseTracer {
  private static final WithSpanTracer TRACER = new WithSpanTracer();

  public static WithSpanTracer tracer() {
    return TRACER;
  }

  private static final Logger log = LoggerFactory.getLogger(WithSpanTracer.class);

  public io.opentelemetry.api.trace.Span startSpan(
      Context context,
      WithSpan applicationAnnotation,
      Method method,
      io.opentelemetry.api.trace.Span.Kind kind) {

    if (kind == io.opentelemetry.api.trace.Span.Kind.SERVER
        && getCurrentServerSpan(context) != null) {
      return io.opentelemetry.api.trace.Span.getInvalid();
    }
    if (kind == io.opentelemetry.api.trace.Span.Kind.CLIENT
        && context.get(CONTEXT_CLIENT_SPAN_KEY) != null) {
      return io.opentelemetry.api.trace.Span.getInvalid();
    }
    return startSpan(spanNameForMethodWithAnnotation(applicationAnnotation, method), kind);
  }

  /**
   * Creates new scoped context, based on the given context, with the given span.
   *
   * <p>Attaches new context to the request to avoid creating duplicate server spans.
   */
  public Scope startScope(
      Context context,
      io.opentelemetry.api.trace.Span span,
      io.opentelemetry.api.trace.Span.Kind kind) {

    if (kind == io.opentelemetry.api.trace.Span.Kind.SERVER) {
      return context.with(CONTEXT_SERVER_SPAN_KEY, span).with(span).makeCurrent();
    }
    if (kind == io.opentelemetry.api.trace.Span.Kind.CLIENT) {
      return context.with(CONTEXT_CLIENT_SPAN_KEY, span).with(span).makeCurrent();
    }
    return context.with(span).makeCurrent();
  }

  /**
   * This method is used to generate an acceptable span (operation) name based on a given method
   * reference. It first checks for existence of {@link WithSpan} annotation. If it is present, then
   * tries to derive name from its {@code value} attribute. Otherwise delegates to {@link
   * #spanNameForMethod(Method)}.
   */
  public String spanNameForMethodWithAnnotation(WithSpan applicationAnnotation, Method method) {
    if (applicationAnnotation != null && !applicationAnnotation.value().isEmpty()) {
      return applicationAnnotation.value();
    }
    return spanNameForMethod(method);
  }

  public Kind extractSpanKind(WithSpan applicationAnnotation) {
    Span.Kind applicationKind =
        applicationAnnotation != null ? applicationAnnotation.kind() : Span.Kind.INTERNAL;
    return toAgentOrNull(applicationKind);
  }

  public static Kind toAgentOrNull(Span.Kind applicationSpanKind) {
    try {
      return Kind.valueOf(applicationSpanKind.name());
    } catch (IllegalArgumentException e) {
      log.debug("unexpected span kind: {}", applicationSpanKind.name());
      return Kind.INTERNAL;
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.opentelemetry-annotations";
  }
}
