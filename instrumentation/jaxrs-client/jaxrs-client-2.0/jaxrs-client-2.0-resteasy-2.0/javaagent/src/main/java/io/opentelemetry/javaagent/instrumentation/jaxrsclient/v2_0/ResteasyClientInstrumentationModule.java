/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.ResteasyClientTracer.tracer;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.resteasy.client.jaxrs.internal.ClientInvocation;

/**
 * Unlike other supported JAX-RS Client implementations, Resteasy's one is very simple and passes
 * all requests through single point. Both sync ADN async! This allows for easy instrumentation and
 * proper scope handling.
 *
 * <p>This specific instrumentation will not conflict with {@link JaxRsClientInstrumentationModule},
 * because {@link JaxRsClientTracer} used by the latter checks against double client spans.
 */
@AutoService(InstrumentationModule.class)
public class ResteasyClientInstrumentationModule extends InstrumentationModule {

  public ResteasyClientInstrumentationModule() {
    super("jaxrs-client", "jaxrs-client-2.0", "resteasy-client", "resteasy-client-2.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ResteasyClientConnectionErrorInstrumentation());
  }

  private static final class ResteasyClientConnectionErrorInstrumentation
      implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("org.jboss.resteasy.client.jaxrs.internal.ClientInvocation");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod().and(isPublic()).and(named("invoke")).and(takesArguments(0)),
          ResteasyClientInstrumentationModule.class.getName() + "$InvokeAdvice");
    }
  }

  public static class InvokeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This ClientInvocation invocation,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      context = tracer().startOperation(currentContext(), invocation);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Response response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      tracer().endMaybeExceptionally(context, response, throwable);
    }
  }
}
