/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.javaagent.instrumentation.netty.v3_8.client.NettyHttpClientTracer.tracer;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.ChannelTraceContext;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class HttpClientResponseTracingHandler extends SimpleChannelUpstreamHandler {

  private final ContextStore<Channel, ChannelTraceContext> contextStore;

  public HttpClientResponseTracingHandler(ContextStore<Channel, ChannelTraceContext> contextStore) {
    this.contextStore = contextStore;
  }

  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent msg) {
    ChannelTraceContext channelTraceContext =
        contextStore.putIfAbsent(ctx.getChannel(), ChannelTraceContext.Factory.INSTANCE);

    HttpClientOperation operation = channelTraceContext.getOperation();
    if (operation == null) {
      ctx.sendUpstream(msg);
      return;
    }
    if (msg.getMessage() instanceof HttpResponse) {
      tracer().end(operation, (HttpResponse) msg.getMessage());
    }
    // We want the callback in the scope of the parent, not the client span
    try (Scope ignored = operation.makeParentCurrent()) {
      ctx.sendUpstream(msg);
    }
  }
}
