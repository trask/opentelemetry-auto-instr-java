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
package io.opentelemetry.auto.instrumentation.opentelemetryapi.v0_4;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.matcher.ElementMatcher;

public abstract class AbstractInstrumentation extends Instrumenter.Default {
  public AbstractInstrumentation() {
    super("opentelemetry-api");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // this class was introduced in OpenTelemetry API 0.4
    return hasClassesNamed("unshaded.io.opentelemetry.internal.Obfuscated")
        // and this class was introduced in OpenTelemetry API 0.5
        .and(not(hasClassesNamed("unshaded.io.opentelemetry.metrics.LongUpDownSumObserver")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".context.ContextUtils",
      packageName + ".context.UnshadedScope",
      packageName + ".context.NoopScope",
      packageName + ".context.propagation.UnshadedContextPropagators",
      packageName + ".context.propagation.UnshadedHttpTextFormat",
      packageName + ".context.propagation.UnshadedHttpTextFormat$UnshadedSetter",
      packageName + ".context.propagation.UnshadedHttpTextFormat$UnshadedGetter",
      packageName + ".trace.Bridging",
      packageName + ".trace.Bridging$1",
      packageName + ".trace.TracingContextUtils",
      packageName + ".trace.UnshadedSpan",
      packageName + ".trace.UnshadedSpan$Builder",
      packageName + ".trace.UnshadedTracer",
      packageName + ".trace.UnshadedTracerProvider"
    };
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("unshaded.io.grpc.Context", "io.grpc.Context");
  }
}
