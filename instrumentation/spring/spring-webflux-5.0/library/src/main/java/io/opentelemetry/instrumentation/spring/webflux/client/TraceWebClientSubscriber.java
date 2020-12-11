/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import static io.opentelemetry.instrumentation.spring.webflux.client.SpringWebfluxHttpClientTracer.tracer;

import io.opentelemetry.context.Scope;
import org.reactivestreams.Subscription;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.CoreSubscriber;

/**
 * Based on Spring Sleuth's Reactor instrumentation.
 * https://github.com/spring-cloud/spring-cloud-sleuth/blob/master/spring-cloud-sleuth-core/src/main/java/org/springframework/cloud/sleuth/instrument/web/client/TraceWebClientBeanPostProcessor.java
 */
public final class TraceWebClientSubscriber implements CoreSubscriber<ClientResponse> {

  final CoreSubscriber<? super ClientResponse> actual;

  final reactor.util.context.Context context;

  private final io.opentelemetry.context.Context tracingContext;

  public TraceWebClientSubscriber(
      CoreSubscriber<? super ClientResponse> actual,
      io.opentelemetry.context.Context tracingContext) {
    this.actual = actual;
    this.tracingContext = tracingContext;
    this.context = actual.currentContext();
  }

  @Override
  public void onSubscribe(Subscription subscription) {
    this.actual.onSubscribe(subscription);
  }

  @Override
  public void onNext(ClientResponse response) {
    tracer().end(tracingContext, response);
    // TODO (trask) this should use parentContext
    try (Scope ignored = tracingContext.makeCurrent()) {
      actual.onNext(response);
    }
  }

  @Override
  public void onError(Throwable t) {
    tracer().endExceptionally(tracingContext, t);
    // TODO (trask) this should use parentContext
    try (Scope ignored = tracingContext.makeCurrent()) {
      actual.onError(t);
    }
  }

  @Override
  public void onComplete() {
    // TODO (trask) this should use parentContext
    try (Scope ignored = tracingContext.makeCurrent()) {
      actual.onComplete();
    }
  }

  @Override
  public reactor.util.context.Context currentContext() {
    return this.context;
  }
}
