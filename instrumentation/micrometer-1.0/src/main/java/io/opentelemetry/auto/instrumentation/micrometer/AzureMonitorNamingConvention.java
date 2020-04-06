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
package io.opentelemetry.auto.instrumentation.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.lang.Nullable;
import java.util.regex.Pattern;

/**
 * Naming convention to push metrics to Azure Monitor.
 *
 * @author Dhaval Doshi
 * @since 1.1.0
 */
public class AzureMonitorNamingConvention implements NamingConvention {
  private static final Pattern NAME_AND_TAG_KEY_PATTERN = Pattern.compile("[^a-zA-Z0-9\\-]");

  private final NamingConvention delegate;

  public AzureMonitorNamingConvention() {
    this(NamingConvention.snakeCase);
  }

  public AzureMonitorNamingConvention(final NamingConvention delegate) {
    this.delegate = delegate;
  }

  /** Trimming takes place in App Insights core SDK. */
  @Override
  public String name(final String name, final Meter.Type type, @Nullable final String baseUnit) {
    return NAME_AND_TAG_KEY_PATTERN.matcher(delegate.name(name, type, baseUnit)).replaceAll("_");
  }

  @Override
  public String tagKey(final String key) {
    return NAME_AND_TAG_KEY_PATTERN.matcher(delegate.tagKey(key)).replaceAll("_");
  }
}
