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
package io.opentelemetry.auto.instrumentation.rmi.context.client;

import static io.opentelemetry.auto.instrumentation.rmi.context.ContextPayload.TRACER;
import static io.opentelemetry.auto.instrumentation.rmi.context.ContextPropagator.PROPAGATOR;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.rmi.server.ObjID;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import sun.rmi.transport.Connection;

/**
 * Main entry point for transferring context between RMI service.
 *
 * <p>It injects into StreamRemoteCall constructor used for invoking remote tasks and performs a
 * backwards compatible check to ensure if the other side is prepared to receive context propagation
 * messages then if successful sends a context propagation message
 *
 * <p>Context propagation consist of a Serialized HashMap with all data set by usual context
 * injection, which includes things like sampling priority, trace and parent id
 *
 * <p>As well as optional baggage items
 *
 * <p>On the other side of the communication a special Dispatcher is created when a message with
 * CONTEXT_CALL_ID is received.
 *
 * <p>If the server is not instrumented first call will gracefully fail just like any other unknown
 * call. With small caveat that this first call needs to *not* have any parameters, since those will
 * not be read from connection and instead will be interpreted as another remote instruction, but
 * that instruction will essentially be garbage data and will cause the parsing loop to throw
 * exception and shutdown the connection which we do not want
 */
@AutoService(Instrumenter.class)
public class RmiClientContextInstrumentation extends Instrumenter.Default {

  public RmiClientContextInstrumentation() {
    super("rmi", "rmi-context-propagator");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return extendsClass(named("sun.rmi.transport.StreamRemoteCall"));
  }

  @Override
  public Map<String, String> contextStore() {
    // caching if a connection can support enhanced format
    return singletonMap("sun.rmi.transport.Connection", "java.lang.Boolean");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.instrumentation.rmi.context.ContextPayload$InjectAdapter",
      "io.opentelemetry.auto.instrumentation.rmi.context.ContextPayload$ExtractAdapter",
      "io.opentelemetry.auto.instrumentation.rmi.context.ContextPayload",
      "io.opentelemetry.auto.instrumentation.rmi.context.ContextPropagator"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor()
            .and(takesArgument(0, named("sun.rmi.transport.Connection")))
            .and(takesArgument(1, named("java.rmi.server.ObjID"))),
        getClass().getName() + "$StreamRemoteCallConstructorAdvice");
  }

  public static class StreamRemoteCallConstructorAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) final Connection c, @Advice.Argument(1) final ObjID id) {
      if (!c.isReusable()) {
        return;
      }
      if (PROPAGATOR.isRMIInternalObject(id)) {
        return;
      }
      final Span activeSpan = TRACER.getCurrentSpan();
      if (!activeSpan.getContext().isValid()) {
        return;
      }

      final ContextStore<Connection, Boolean> knownConnections =
          InstrumentationContext.get(Connection.class, Boolean.class);

      PROPAGATOR.attemptToPropagateContext(knownConnections, c, activeSpan);
    }
  }
}
