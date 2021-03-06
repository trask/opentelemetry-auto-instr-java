/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.collector;

import io.opentelemetry.javaagent.instrumentation.api.WeakMap;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import java.util.Collections;
import java.util.WeakHashMap;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;

/**
 * This class is a ByteBuddy build plugin that is responsible for generating actual implementation
 * of the {@link InstrumentationModule#getMuzzleReferenceMatcher()} method.
 *
 * <p>This class is used in the gradle build scripts, referenced by each instrumentation module.
 */
public class MuzzleCodeGenerationPlugin implements Plugin {
  static {
    // prevent WeakMap from logging warning while plugin is running
    WeakMap.Provider.registerIfAbsent(
        new WeakMap.Implementation() {
          @Override
          public <K, V> WeakMap<K, V> get() {
            return new WeakMap.MapAdapter<>(Collections.synchronizedMap(new WeakHashMap<>()));
          }
        });
  }

  private static final TypeDescription instrumentationModuleType =
      new TypeDescription.ForLoadedType(InstrumentationModule.class);

  @Override
  public boolean matches(TypeDescription target) {
    if (target.isAbstract()) {
      return false;
    }
    // AutoService annotation is not retained at runtime. Check for InstrumentationModule supertype
    boolean isInstrumentationModule = false;
    TypeDefinition instrumentation = target.getSuperClass();
    while (instrumentation != null) {
      if (instrumentation.equals(instrumentationModuleType)) {
        isInstrumentationModule = true;
        break;
      }
      instrumentation = instrumentation.getSuperClass();
    }
    return isInstrumentationModule;
  }

  @Override
  public DynamicType.Builder<?> apply(
      DynamicType.Builder<?> builder,
      TypeDescription typeDescription,
      ClassFileLocator classFileLocator) {
    return builder.visit(new MuzzleCodeGenerator());
  }

  @Override
  public void close() {}
}
