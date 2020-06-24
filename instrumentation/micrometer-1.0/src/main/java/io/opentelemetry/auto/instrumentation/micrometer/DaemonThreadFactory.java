/*
 * Copyright The OpenTelemetry Authors
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

import java.util.concurrent.ThreadFactory;

// this is a copy of io.opentelemetry.auto.common.exec.DaemonThreadFactory
// so that instrumentation doesn't need to use io.micrometer.core.instrument.util.NamedThreadFactory
// which wasn't introduced until micrometer 1.0.12
public final class DaemonThreadFactory implements ThreadFactory {

  private final String threadName;

  /**
   * Constructs a new {@code DaemonThreadFactory} with a null ContextClassLoader.
   *
   * @param threadName used to prefix all thread names.
   */
  public DaemonThreadFactory(final String threadName) {
    this.threadName = threadName;
  }

  @Override
  public Thread newThread(final Runnable r) {
    final Thread thread = new Thread(r, threadName);
    thread.setDaemon(true);
    thread.setContextClassLoader(null);
    return thread;
  }
}
