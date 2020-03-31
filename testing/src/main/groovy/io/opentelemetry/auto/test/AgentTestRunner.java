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
package io.opentelemetry.auto.test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.aiappid.AiAppId;
import io.opentelemetry.auto.test.asserts.ListWriterAssert;
import io.opentelemetry.auto.tooling.AgentInstaller;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.auto.tooling.matcher.AdditionalLibraryIgnoresMatcher;
import io.opentelemetry.auto.util.test.AgentSpecification;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.trace.Tracer;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.model.SpecMetadata;

/**
 * A spock test runner which automatically applies instrumentation and exposes a global trace
 * writer.
 *
 * <p>To use, write a regular spock test, but extend this class instead of {@link
 * spock.lang.Specification}. <br>
 * This will cause the following to occur before test startup:
 *
 * <ul>
 *   <li>All {@link Instrumenter}s on the test classpath will be applied. Matching preloaded classes
 *       will be retransformed.
 *   <li>{@link AgentTestRunner#TEST_WRITER} will be registerd with the global tracer and available
 *       in an initialized state.
 * </ul>
 */
@RunWith(SpockRunner.class)
@SpecMetadata(filename = "AgentTestRunner.java", line = 0)
@Slf4j
public abstract class AgentTestRunner extends AgentSpecification {
  private static final long TIMEOUT_MILLIS = 10 * 1000;
  /**
   * For test runs, agent's global tracer will report to this list writer.
   *
   * <p>Before the start of each test the reported traces will be reset.
   */
  public static final ListWriter TEST_WRITER;

  protected static final Tracer TEST_TRACER;

  private static final ElementMatcher.Junction<TypeDescription> GLOBAL_LIBRARIES_IGNORES_MATCHER =
      AdditionalLibraryIgnoresMatcher.additionalLibraryIgnoresMatcher();

  protected static final Set<String> TRANSFORMED_CLASSES_NAMES = Sets.newConcurrentHashSet();
  protected static final Set<TypeDescription> TRANSFORMED_CLASSES_TYPES =
      Sets.newConcurrentHashSet();
  private static final AtomicInteger INSTRUMENTATION_ERROR_COUNT = new AtomicInteger(0);
  private static final TestRunnerListener TEST_LISTENER = new TestRunnerListener();

  private static final Instrumentation INSTRUMENTATION;
  private static volatile ClassFileTransformer activeTransformer = null;

  static {
    INSTRUMENTATION = ByteBuddyAgent.getInstrumentation();

    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.WARN);
    ((Logger) LoggerFactory.getLogger("io.opentelemetry.auto")).setLevel(Level.DEBUG);

    TEST_WRITER = new ListWriter();
    OpenTelemetrySdk.getTracerProvider().addSpanProcessor(TEST_WRITER);
    TEST_TRACER = OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto");

    AiAppId.setSupplier(
        new AiAppId.Supplier() {
          @Override
          public String get() {
            return "4321";
          }
        });
  }

  protected static Tracer getTestTracer() {
    return TEST_TRACER;
  }

  protected static ListWriter getTestWriter() {
    return TEST_WRITER;
  }

  /**
   * Invoked when Bytebuddy encounters an instrumentation error. Fails the test by default.
   *
   * <p>Override to skip specific expected errors.
   *
   * @return true if the test should fail because of this error.
   */
  protected boolean onInstrumentationError(
      final String typeName,
      final ClassLoader classLoader,
      final JavaModule module,
      final boolean loaded,
      final Throwable throwable) {
    log.error(
        "Unexpected instrumentation error when instrumenting {} on {}",
        typeName,
        classLoader,
        throwable);
    return true;
  }

  /**
   * @param className name of the class being loaded
   * @param classLoader classloader class is being defined on
   * @return true if the class under load should be transformed for this test.
   */
  protected boolean shouldTransformClass(final String className, final ClassLoader classLoader) {
    return true;
  }

  @BeforeClass
  public static synchronized void agentSetup() {
    if (null != activeTransformer) {
      throw new IllegalStateException("transformer already in place: " + activeTransformer);
    }
    assert ServiceLoader.load(Instrumenter.class, AgentTestRunner.class.getClassLoader())
            .iterator()
            .hasNext()
        : "No instrumentation found";
    activeTransformer = AgentInstaller.installBytebuddyAgent(INSTRUMENTATION, true, TEST_LISTENER);
  }

  /**
   * Normally {@code @BeforeClass} is run only on static methods, but spock allows us to run it on
   * instance methods. Note: this means there is a 'special' instance of test class that is not used
   * to run any tests, but instead is just used to run this method once.
   */
  @BeforeClass
  public void setupBeforeTests() {
    TEST_LISTENER.activateTest(this);
  }

  @Before
  public void beforeTest() {
    assert !getTestTracer().getCurrentSpan().getContext().isValid()
        : "Span is active before test has started: " + getTestTracer().getCurrentSpan();
    log.debug("Starting test: '{}'", getSpecificationContext().getCurrentIteration().getName());
    TEST_WRITER.clear();
  }

  /** See comment for {@code #setupBeforeTests} above. */
  @AfterClass
  public void cleanUpAfterTests() {
    TEST_LISTENER.deactivateTest(this);
  }

  @AfterClass
  public static synchronized void agentCleanup() {
    if (null != activeTransformer) {
      INSTRUMENTATION.removeTransformer(activeTransformer);
      activeTransformer = null;
    }
    // Cleanup before assertion.
    assert INSTRUMENTATION_ERROR_COUNT.get() == 0
        : INSTRUMENTATION_ERROR_COUNT.get() + " Instrumentation errors during test";

    final List<TypeDescription> ignoredClassesTransformed = new ArrayList<>();
    for (final TypeDescription type : TRANSFORMED_CLASSES_TYPES) {
      if (GLOBAL_LIBRARIES_IGNORES_MATCHER.matches(type)) {
        ignoredClassesTransformed.add(type);
      }
    }
    assert ignoredClassesTransformed.isEmpty()
        : "Transformed classes match global libraries ignore matcher: " + ignoredClassesTransformed;
  }

  public static void assertTraces(
      final int size,
      @ClosureParams(
              value = SimpleType.class,
              options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
          @DelegatesTo(value = ListWriterAssert.class, strategy = Closure.DELEGATE_FIRST)
          final Closure spec) {
    ListWriterAssert.assertTraces(
        TEST_WRITER, size, Predicates.<List<SpanData>>alwaysFalse(), spec);
  }

  public static void assertTracesWithFilter(
      final int size,
      final Predicate<List<SpanData>> excludes,
      @ClosureParams(
              value = SimpleType.class,
              options = "io.opentelemetry.auto.test.asserts.ListWriterAssert")
          @DelegatesTo(value = ListWriterAssert.class, strategy = Closure.DELEGATE_FIRST)
          final Closure spec) {
    ListWriterAssert.assertTraces(TEST_WRITER, size, excludes, spec);
  }

  public static class TestRunnerListener implements AgentBuilder.Listener {
    private static final List<AgentTestRunner> activeTests = new CopyOnWriteArrayList<>();

    public void activateTest(final AgentTestRunner testRunner) {
      activeTests.add(testRunner);
    }

    public void deactivateTest(final AgentTestRunner testRunner) {
      activeTests.remove(testRunner);
    }

    @Override
    public void onDiscovery(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {
      for (final AgentTestRunner testRunner : activeTests) {
        if (!testRunner.shouldTransformClass(typeName, classLoader)) {
          throw new AbortTransformationException(
              "Aborting transform for class name = " + typeName + ", loader = " + classLoader);
        }
      }
    }

    @Override
    public void onTransformation(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded,
        final DynamicType dynamicType) {
      TRANSFORMED_CLASSES_NAMES.add(typeDescription.getActualName());
      TRANSFORMED_CLASSES_TYPES.add(typeDescription);
    }

    @Override
    public void onIgnored(
        final TypeDescription typeDescription,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {}

    @Override
    public void onError(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded,
        final Throwable throwable) {
      if (!(throwable instanceof AbortTransformationException)) {
        for (final AgentTestRunner testRunner : activeTests) {
          if (testRunner.onInstrumentationError(typeName, classLoader, module, loaded, throwable)) {
            INSTRUMENTATION_ERROR_COUNT.incrementAndGet();
            break;
          }
        }
      }
    }

    @Override
    public void onComplete(
        final String typeName,
        final ClassLoader classLoader,
        final JavaModule module,
        final boolean loaded) {}

    /** Used to signal that a transformation was intentionally aborted and is not an error. */
    public static class AbortTransformationException extends RuntimeException {
      public AbortTransformationException() {
        super();
      }

      public AbortTransformationException(final String message) {
        super(message);
      }
    }
  }
}
