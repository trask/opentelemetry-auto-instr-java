/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.spi.TracerCustomizer;
import io.opentelemetry.sdk.trace.TracerSdkManagement;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

@AutoService(TracerCustomizer.class)
public class AgentTestingSdkCustomizer implements TracerCustomizer {

  static final AgentTestingSpanProcessor spanProcessor =
      new AgentTestingSpanProcessor(
          SimpleSpanProcessor.builder(AgentTestingExporterFactory.exporter).build());

  static void reset() {
    spanProcessor.forceFlushCalled = false;
  }

  @Override
  public void configure(TracerSdkManagement tracerManagement) {
    tracerManagement.addSpanProcessor(spanProcessor);
  }
}
