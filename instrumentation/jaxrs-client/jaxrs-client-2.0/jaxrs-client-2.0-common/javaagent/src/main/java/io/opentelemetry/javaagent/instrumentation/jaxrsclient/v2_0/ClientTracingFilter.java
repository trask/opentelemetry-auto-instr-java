/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrsclient.v2_0.JaxRsClientTracer.tracer;

import io.opentelemetry.context.Context;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;

@Priority(Priorities.HEADER_DECORATOR)
public class ClientTracingFilter implements ClientRequestFilter, ClientResponseFilter {
  public static final String CONTEXT_PROPERTY_NAME = "io.opentelemetry.auto.jax-rs-client.context";

  @Override
  public void filter(ClientRequestContext requestContext) {
    Context context = tracer().startOperation(Context.current(), requestContext);
    requestContext.setProperty(CONTEXT_PROPERTY_NAME, context);
  }

  @Override
  public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) {
    Object operationObj = requestContext.getProperty(CONTEXT_PROPERTY_NAME);
    if (operationObj instanceof Context) {
      tracer().end((Context) operationObj, responseContext);
    }
  }
}
