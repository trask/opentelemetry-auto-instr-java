/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */
package io.opentelemetry.auto.bootstrap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class StartupProfiler {

  private static final boolean ENABLED = Boolean.getBoolean("ota.startup.profiler.enabled");

  private static final int INTERVAL = Integer.getInteger("ota.startup.profiler.interval", 20);

  private static final String OUTPUT_FILE = System.getProperty("ota.startup.profiler.outputFile");

  public static void startIfConfigured() {
    if (ENABLED) {
      if (OUTPUT_FILE == null) {
        start(new PrintWriter(System.out));
      } else {
        final File file = new File(OUTPUT_FILE);
        try {
          start(new PrintWriter(new FileWriter(file)));
        } catch (final IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  public static void start(final PrintWriter out) {
    Executors.newSingleThreadScheduledExecutor()
        .scheduleAtFixedRate(new ThreadDump(out), INTERVAL, INTERVAL, TimeUnit.MILLISECONDS);
  }

  private static class ThreadDump implements Runnable {

    private final PrintWriter out;

    private ThreadDump(final PrintWriter out) {
      this.out = out;
    }

    @Override
    public void run() {
      final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
      final ThreadInfo[] threadInfos =
          threadBean.getThreadInfo(
              threadBean.getAllThreadIds(), threadBean.isObjectMonitorUsageSupported(), false);
      boolean first = true;
      for (final ThreadInfo threadInfo : threadInfos) {
        if (captureThread(threadInfo)) {
          if (first) {
            out.println("------ " + ManagementFactory.getRuntimeMXBean().getUptime() + " ------");
            out.println();
            first = false;
          }
          write(threadInfo);
        }
      }
      out.flush();
    }

    private boolean captureThread(final ThreadInfo threadInfo) {
      for (final StackTraceElement ste : threadInfo.getStackTrace()) {
        if (ste.getClassName().startsWith("net.bytebuddy.")) {
          return true;
        }
      }
      return false;
    }

    private void write(final ThreadInfo threadInfo) {
      out.println(
          "\""
              + threadInfo.getThreadName().replace("\"", "\\\"")
              + "\" #"
              + threadInfo.getThreadId());
      out.println("   java.lang.Thread.State: " + threadInfo.getThreadState());
      for (final StackTraceElement ste : threadInfo.getStackTrace()) {
        out.println("        at " + ste);
      }
      out.println();
    }
  }
}
