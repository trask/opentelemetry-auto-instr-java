/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.context;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;

public class ParentPropagatingContext implements Context {

  public static ParentPropagatingContext create(Context parentContext, Context context) {
    return new ParentPropagatingContext(parentContext, context);
  }

  private final Context parentContext;
  private final Context context;

  private ParentPropagatingContext(Context parentContext, Context context) {
    this.parentContext = parentContext;
    this.context = context;
  }

  @Override
  public <V> V get(ContextKey<V> key) {
    return context.get(key);
  }

  @Override
  public <V> Context with(ContextKey<V> k1, V v1) {
    // TODO (trask) this could return parentContext.with(), but testing to see if instrumentation
    //  even uses this
    throw new UnsupportedOperationException();
  }

  @Override
  public Scope makeCurrent() {
    return parentContext.makeCurrent();
  }
}
