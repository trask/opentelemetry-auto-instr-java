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
package io.opentelemetry.auto.instrumentation.apachehttpasyncclient;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator.DEFAULT_SPAN_NAME;
import static io.opentelemetry.auto.instrumentation.apachehttpasyncclient.ApacheHttpAsyncClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.apachehttpasyncclient.ApacheHttpAsyncClientDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.apachehttpasyncclient.HttpHeadersInjectAdapter.SETTER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.aiappid.AiAppId;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.io.IOException;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;

@AutoService(Instrumenter.class)
public class ApacheHttpAsyncClientInstrumentation extends Instrumenter.Default {

  public ApacheHttpAsyncClientInstrumentation() {
    super("httpasyncclient", "apache-httpasyncclient");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("org.apache.http.nio.client.HttpAsyncClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.http.nio.client.HttpAsyncClient"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".HttpHeadersInjectAdapter",
      getClass().getName() + "$DelegatingRequestProducer",
      getClass().getName() + "$TraceContinuedFutureCallback",
      packageName + ".ApacheHttpAsyncClientDecorator"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("execute"))
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.apache.http.nio.protocol.HttpAsyncRequestProducer")))
            .and(takesArgument(1, named("org.apache.http.nio.protocol.HttpAsyncResponseConsumer")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext")))
            .and(takesArgument(3, named("org.apache.http.concurrent.FutureCallback"))),
        ApacheHttpAsyncClientInstrumentation.class.getName() + "$ClientAdvice");
  }

  public static class ClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Span methodEnter(
        @Advice.Argument(value = 0, readOnly = false) HttpAsyncRequestProducer requestProducer,
        @Advice.Argument(value = 1, readOnly = false) HttpAsyncResponseConsumer responseConsumer,
        @Advice.Argument(2) final HttpContext context,
        @Advice.Argument(value = 3, readOnly = false) FutureCallback<?> futureCallback) {

      final Span parentSpan = TRACER.getCurrentSpan();
      final Span clientSpan = TRACER.spanBuilder(DEFAULT_SPAN_NAME).setSpanKind(CLIENT).startSpan();
      DECORATE.afterStart(clientSpan);

      requestProducer = new DelegatingRequestProducer(clientSpan, requestProducer);
      responseConsumer = new DelegatingResponseConsumer(clientSpan, responseConsumer);
      futureCallback =
          new TraceContinuedFutureCallback(parentSpan, clientSpan, context, futureCallback);

      return clientSpan;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final Span span,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
      }
    }
  }

  public static class DelegatingRequestProducer implements HttpAsyncRequestProducer {
    final Span span;
    final HttpAsyncRequestProducer delegate;

    public DelegatingRequestProducer(final Span span, final HttpAsyncRequestProducer delegate) {
      this.span = span;
      this.delegate = delegate;
    }

    @Override
    public HttpHost getTarget() {
      return delegate.getTarget();
    }

    @Override
    public HttpRequest generateRequest() throws IOException, HttpException {
      final HttpRequest request = delegate.generateRequest();
      span.updateName(DECORATE.spanNameForRequest(request));
      DECORATE.onRequest(span, request);

      final Context context = withSpan(span, Context.current());
      OpenTelemetry.getPropagators().getHttpTextFormat().inject(context, request, SETTER);

      return request;
    }

    @Override
    public void produceContent(final ContentEncoder encoder, final IOControl ioctrl)
        throws IOException {
      delegate.produceContent(encoder, ioctrl);
    }

    @Override
    public void requestCompleted(final HttpContext context) {
      delegate.requestCompleted(context);
    }

    @Override
    public void failed(final Exception ex) {
      delegate.failed(ex);
    }

    @Override
    public boolean isRepeatable() {
      return delegate.isRepeatable();
    }

    @Override
    public void resetRequest() throws IOException {
      delegate.resetRequest();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }

  public static class DelegatingResponseConsumer implements HttpAsyncResponseConsumer {
    final Span span;
    final HttpAsyncResponseConsumer delegate;

    public DelegatingResponseConsumer(final Span span, final HttpAsyncResponseConsumer delegate) {
      this.span = span;
      this.delegate = delegate;
    }

    @Override
    public void responseReceived(final HttpResponse response) throws IOException, HttpException {
      final Header aiRequestContextHeader = response.getFirstHeader(AiAppId.RESPONSE_HEADER_NAME);
      if (aiRequestContextHeader != null) {
        HttpClientDecorator.setTargetAppId(span, aiRequestContextHeader.getValue());
      }
      if (delegate != null) {
        delegate.responseReceived(response);
      }
    }

    @Override
    public void consumeContent(final ContentDecoder decoder, final IOControl ioctrl)
        throws IOException {
      if (delegate != null) {
        delegate.consumeContent(decoder, ioctrl);
      }
    }

    @Override
    public void responseCompleted(final HttpContext context) {
      if (delegate != null) {
        delegate.responseCompleted(context);
      }
    }

    @Override
    public void failed(final Exception ex) {
      if (delegate != null) {
        delegate.failed(ex);
      }
    }

    @Override
    public Exception getException() {
      if (delegate != null) {
        return delegate.getException();
      } else {
        return null;
      }
    }

    @Override
    public Object getResult() {
      if (delegate != null) {
        return delegate.getResult();
      } else {
        return null;
      }
    }

    @Override
    public boolean isDone() {
      if (delegate != null) {
        return delegate.isDone();
      } else {
        return true;
      }
    }

    @Override
    public void close() throws IOException {
      if (delegate != null) {
        delegate.close();
      }
    }

    @Override
    public boolean cancel() {
      if (delegate != null) {
        return delegate.cancel();
      } else {
        return true;
      }
    }
  }

  public static class TraceContinuedFutureCallback<T> implements FutureCallback<T> {
    private final Span parentSpan;
    private final Span clientSpan;
    private final HttpContext context;
    private final FutureCallback<T> delegate;

    public TraceContinuedFutureCallback(
        final Span parentSpan,
        final Span clientSpan,
        final HttpContext context,
        final FutureCallback<T> delegate) {
      this.parentSpan = parentSpan;
      this.clientSpan = clientSpan;
      this.context = context;
      // Note: this can be null in real life, so we have to handle this carefully
      this.delegate = delegate;
    }

    @Override
    public void completed(final T result) {
      DECORATE.onResponse(clientSpan, context);
      DECORATE.beforeFinish(clientSpan);
      clientSpan.end(); // end span before calling delegate

      if (parentSpan == null) {
        completeDelegate(result);
      } else {
        try (final Scope scope = currentContextWith(parentSpan)) {
          completeDelegate(result);
        }
      }
    }

    @Override
    public void failed(final Exception ex) {
      DECORATE.onResponse(clientSpan, context);
      DECORATE.onError(clientSpan, ex);
      DECORATE.beforeFinish(clientSpan);
      clientSpan.end(); // end span before calling delegate

      if (parentSpan == null) {
        failDelegate(ex);
      } else {
        try (final Scope scope = currentContextWith(parentSpan)) {
          failDelegate(ex);
        }
      }
    }

    @Override
    public void cancelled() {
      DECORATE.onResponse(clientSpan, context);
      DECORATE.beforeFinish(clientSpan);
      clientSpan.end(); // end span before calling delegate

      if (parentSpan == null) {
        cancelDelegate();
      } else {
        try (final Scope scope = currentContextWith(parentSpan)) {
          cancelDelegate();
        }
      }
    }

    private void completeDelegate(final T result) {
      if (delegate != null) {
        delegate.completed(result);
      }
    }

    private void failDelegate(final Exception ex) {
      if (delegate != null) {
        delegate.failed(ex);
      }
    }

    private void cancelDelegate() {
      if (delegate != null) {
        delegate.cancelled();
      }
    }
  }
}
