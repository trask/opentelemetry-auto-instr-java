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
package io.opentelemetry.auto.instrumentation.opentelemetryapi.v0_2_0.trace;

import io.opentelemetry.OpenTelemetry;
import lombok.extern.slf4j.Slf4j;
import unshaded.io.opentelemetry.context.NoopScope;
import unshaded.io.opentelemetry.context.Scope;
import unshaded.io.opentelemetry.context.propagation.BinaryFormat;
import unshaded.io.opentelemetry.context.propagation.HttpTextFormat;
import unshaded.io.opentelemetry.trace.DefaultSpan;
import unshaded.io.opentelemetry.trace.Span;
import unshaded.io.opentelemetry.trace.SpanContext;
import unshaded.io.opentelemetry.trace.Tracer;

@Slf4j
class UnshadedTracer implements Tracer {

  private final io.opentelemetry.trace.Tracer shadedTracer;

  UnshadedTracer(final io.opentelemetry.trace.Tracer shadedTracer) {
    this.shadedTracer = shadedTracer;
  }

  @Override
  public Span getCurrentSpan() {
    return new UnshadedSpan(shadedTracer.getCurrentSpan());
  }

  @Override
  public Scope withSpan(final Span span) {
    if (span instanceof UnshadedSpan) {
      return new UnshadedScope(shadedTracer.withSpan(((UnshadedSpan) span).getShadedSpan()));
    } else {
      log.debug("unexpected span: {}", span);
      return NoopScope.getInstance();
    }
  }

  @Override
  public Span.Builder spanBuilder(final String spanName) {
    return new UnshadedSpan.Builder(shadedTracer.spanBuilder(spanName));
  }

  @Override
  public BinaryFormat<SpanContext> getBinaryFormat() {
    // this is gone in 0.2.4, will come back later, so for now returning no-op implementation
    return NoopBinaryFormat.INSTANCE;
  }

  @Override
  public HttpTextFormat<SpanContext> getHttpTextFormat() {
    return new UnshadedHttpTextFormat(OpenTelemetry.getPropagators().getHttpTextFormat());
  }

  private static class NoopBinaryFormat implements BinaryFormat<SpanContext> {
    static final NoopBinaryFormat INSTANCE = new NoopBinaryFormat();

    @Override
    public byte[] toByteArray(final SpanContext spanContext) {
      return new byte[0];
    }

    @Override
    public SpanContext fromByteArray(final byte[] bytes) {
      return DefaultSpan.getInvalid().getContext();
    }
  }
}
