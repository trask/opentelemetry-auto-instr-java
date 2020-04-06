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
package io.opentelemetry.auto.instrumentation.servlet.v3_0;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.servlet.v3_0.Servlet3Decorator.TRACER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import javax.servlet.AsyncContext;
import javax.servlet.ServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class AsyncContextInstrumentation extends Instrumenter.Default {

  public AsyncContextInstrumentation() {
    super("servlet-3.0", "servlet");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("javax.servlet.AsyncContext");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.servlet.AsyncContext"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".Servlet3Decorator"};
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(named("dispatch")),
        AsyncContextInstrumentation.class.getName() + "$DispatchAdvice");
  }

  /**
   * When a request is dispatched, we want new request to have propagation headers from its parent
   * request. The parent request's span is later closed by {@code
   * TagSettingAsyncListener#onStartAsync}
   */
  public static class DispatchAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static boolean enter(
        @Advice.This final AsyncContext context, @Advice.AllArguments final Object[] args) {
      final int depth = CallDepthThreadLocalMap.incrementCallDepth(AsyncContext.class);
      if (depth > 0) {
        return false;
      }

      final ServletRequest request = context.getRequest();

      final Span currentSpan = TRACER.getCurrentSpan();
      if (currentSpan.getContext().isValid()) {
        // this tells the dispatched servlet to use the current span as the parent for its work
        // (if the currentSpan is not valid for some reason, the original servlet span should still
        // be present in the same request attribute, and so that will be used)
        //
        // the original servlet span stored in the same request attribute does not need to be saved
        // and restored on method exit, because dispatch() hands off control of the request
        // processing, and nothing can be done with the request anymore after this
        request.setAttribute(SPAN_ATTRIBUTE, currentSpan);
      }

      return true;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter final boolean topLevel) {
      if (topLevel) {
        CallDepthThreadLocalMap.reset(AsyncContext.class);
      }
    }
  }
}
