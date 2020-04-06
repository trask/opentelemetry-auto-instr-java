/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.auto.instrumentation.jaxrs.v2_0.JaxRsAnnotationsDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jaxrs.v2_0.JaxRsAnnotationsDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.hasSuperMethod;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import java.util.Map;
import javax.ws.rs.container.AsyncResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JaxRsAnnotationsInstrumentation extends Instrumenter.Default {

  private static final String JAX_ENDPOINT_OPERATION_NAME = "jax-rs.request";

  public JaxRsAnnotationsInstrumentation() {
    super("jaxrs");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("javax.ws.rs.container.AsyncResponse", Span.class.getName());
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("javax.ws.rs.Path");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(
        isAnnotatedWith(named("javax.ws.rs.Path"))
            .<TypeDescription>or(declaresMethod(isAnnotatedWith(named("javax.ws.rs.Path")))));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.tooling.ClassHierarchyIterable",
      "io.opentelemetry.auto.tooling.ClassHierarchyIterable$ClassIterator",
      packageName + ".JaxRsAnnotationsDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(
                hasSuperMethod(
                    isAnnotatedWith(
                        named("javax.ws.rs.Path")
                            .or(named("javax.ws.rs.DELETE"))
                            .or(named("javax.ws.rs.GET"))
                            .or(named("javax.ws.rs.HEAD"))
                            .or(named("javax.ws.rs.OPTIONS"))
                            .or(named("javax.ws.rs.POST"))
                            .or(named("javax.ws.rs.PUT"))))),
        JaxRsAnnotationsInstrumentation.class.getName() + "$JaxRsAnnotationsAdvice");
  }

  public static class JaxRsAnnotationsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope nameSpan(
        @Advice.This final Object target, @Advice.Origin final Method method) {
      // Rename the parent span according to the path represented by these annotations.
      final Span parent = TRACER.getCurrentSpan();

      if (!Config.get().isExperimentalControllerAndViewSpansEnabled()) {
        DECORATE.onJaxRsSpan(null, parent, target.getClass(), method);
        return new SpanWithScope(DefaultSpan.getInvalid(), null);
      }

      final Span span = TRACER.spanBuilder(JAX_ENDPOINT_OPERATION_NAME).startSpan();
      DECORATE.onJaxRsSpan(span, parent, target.getClass(), method);
      DECORATE.afterStart(span);

      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Thrown final Throwable throwable,
        @Advice.AllArguments final Object[] args) {
      final Span span = spanWithScope.getSpan();
      if (throwable != null) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
        spanWithScope.closeScope();
        return;
      }

      AsyncResponse asyncResponse = null;
      for (final Object arg : args) {
        if (arg instanceof AsyncResponse) {
          asyncResponse = (AsyncResponse) arg;
          break;
        }
      }
      if (asyncResponse != null && asyncResponse.isSuspended()) {
        InstrumentationContext.get(AsyncResponse.class, Span.class).put(asyncResponse, span);
      } else {
        DECORATE.beforeFinish(span);
        span.end();
      }
      spanWithScope.closeScope();
    }
  }
}
