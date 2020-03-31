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
package io.opentelemetry.auto.instrumentation.servlet.dispatcher;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.servlet.dispatcher.RequestDispatcherDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.servlet.dispatcher.RequestDispatcherDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequest;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class RequestDispatcherInstrumentation extends Instrumenter.Default {
  public RequestDispatcherInstrumentation() {
    super("servlet", "servlet-dispatcher");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("javax.servlet.RequestDispatcher");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.servlet.RequestDispatcher"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".RequestDispatcherDecorator",
    };
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("javax.servlet.RequestDispatcher", String.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("forward")
            .or(named("include"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
            .and(isPublic()),
        getClass().getName() + "$RequestDispatcherAdvice");
  }

  public static class RequestDispatcherAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope start(
        @Advice.Origin("#m") final String method,
        @Advice.This final RequestDispatcher dispatcher,
        @Advice.Local("_originalServletSpan") Object originalServletSpan,
        @Advice.Argument(0) final ServletRequest request) {
      final Span parentSpan = TRACER.getCurrentSpan();
      if (!parentSpan.getContext().isValid()) {
        // Don't want to generate a new top-level span, and nothing to propagate via SPAN_ATTRIBUTE
        return null;
      }

      Span span = null;
      if (Config.get().isExperimentalControllerAndViewSpansEnabled()) {
        span = TRACER.spanBuilder("servlet." + method).startSpan();
        DECORATE.afterStart(span);

        final String target =
            InstrumentationContext.get(RequestDispatcher.class, String.class).get(dispatcher);
        span.setAttribute("dispatcher.target", target);
      }

      // save the original servlet span before overwriting the request attribute, so that it can be
      // restored on method exit
      originalServletSpan = request.getAttribute(SPAN_ATTRIBUTE);

      // this tells the dispatched servlet what to use as the parent for its work
      if (span != null) {
        request.setAttribute(SPAN_ATTRIBUTE, span);
        return new SpanWithScope(span, currentContextWith(span));
      } else {
        request.setAttribute(SPAN_ATTRIBUTE, parentSpan);
        return null;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stop(
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Local("_originalServletSpan") final Object originalServletSpan,
        @Advice.Argument(0) final ServletRequest request,
        @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }

      // restore the original servlet span
      // since spanWithScope is non-null here, originalServletSpan must have been set with the prior
      // servlet span (as opposed to remaining unset)
      request.setAttribute(SPAN_ATTRIBUTE, originalServletSpan);

      final Span span = spanWithScope.getSpan();
      DECORATE.onError(span, throwable);
      DECORATE.beforeFinish(span);

      span.end();
      spanWithScope.closeScope();
    }
  }
}
