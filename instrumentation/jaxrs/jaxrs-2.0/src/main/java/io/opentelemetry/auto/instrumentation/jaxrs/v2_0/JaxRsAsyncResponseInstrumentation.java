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
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.container.AsyncResponse;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JaxRsAsyncResponseInstrumentation extends Instrumenter.Default {

  public JaxRsAsyncResponseInstrumentation() {
    super("jaxrs");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("javax.ws.rs.container.AsyncResponse", Span.class.getName());
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("javax.ws.rs.container.AsyncResponse");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.ws.rs.container.AsyncResponse"));
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
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("resume").and(takesArgument(0, Object.class)).and(isPublic()),
        JaxRsAsyncResponseInstrumentation.class.getName() + "$AsyncResponseAdvice");
    transformers.put(
        named("resume").and(takesArgument(0, Throwable.class)).and(isPublic()),
        JaxRsAsyncResponseInstrumentation.class.getName() + "$AsyncResponseThrowableAdvice");
    transformers.put(
        named("cancel"),
        JaxRsAsyncResponseInstrumentation.class.getName() + "$AsyncResponseCancelAdvice");
    return transformers;
  }

  public static class AsyncResponseAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void stopSpan(@Advice.This final AsyncResponse asyncResponse) {

      final ContextStore<AsyncResponse, Span> contextStore =
          InstrumentationContext.get(AsyncResponse.class, Span.class);

      final Span span = contextStore.get(asyncResponse);
      if (span != null) {
        contextStore.put(asyncResponse, null);
        DECORATE.beforeFinish(span);
        span.end();
      }
    }
  }

  public static class AsyncResponseThrowableAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final AsyncResponse asyncResponse,
        @Advice.Argument(0) final Throwable throwable) {

      final ContextStore<AsyncResponse, Span> contextStore =
          InstrumentationContext.get(AsyncResponse.class, Span.class);

      final Span span = contextStore.get(asyncResponse);
      if (span != null) {
        contextStore.put(asyncResponse, null);
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
      }
    }
  }

  public static class AsyncResponseCancelAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void stopSpan(@Advice.This final AsyncResponse asyncResponse) {

      final ContextStore<AsyncResponse, Span> contextStore =
          InstrumentationContext.get(AsyncResponse.class, Span.class);

      final Span span = contextStore.get(asyncResponse);
      if (span != null) {
        contextStore.put(asyncResponse, null);
        span.setAttribute("canceled", true);
        DECORATE.beforeFinish(span);
        span.end();
      }
    }
  }
}
