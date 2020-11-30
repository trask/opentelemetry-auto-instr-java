/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.instrumentation.test.InstrumentationTestRunner;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParentContextExtractorTest {

  @Test
  public void shouldExtractCaseInsensitiveHeaders() {
    // given
    Map<String, String> headers =
        ImmutableMap.of(
            "X-b3-traceId",
            "4fd0b6131f19f39af59518d127b0cafe",
            "x-b3-spanid",
            "0000000000000456",
            "X-B3-Sampled",
            "true");
    InstrumentationTestRunner.setGlobalPropagators(
        DefaultContextPropagators.builder()
            .addTextMapPropagator(B3Propagator.getInstance())
            .build());

    // when
    Context context = ParentContextExtractor.fromHttpHeaders(headers);
    // then
    Span span = Span.fromContext(context);
    SpanContext spanContext = span.getSpanContext();
    assertThat(spanContext.isValid()).isTrue();
    assertThat(spanContext.isValid()).isTrue();
    assertThat(spanContext.getSpanIdAsHexString()).isEqualTo("0000000000000456");
    assertThat(spanContext.getTraceIdAsHexString()).isEqualTo("4fd0b6131f19f39af59518d127b0cafe");
  }
}
