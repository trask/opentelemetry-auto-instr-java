/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import com.amazonaws.services.lambda.runtime.Context
import io.opentelemetry.context.propagation.DefaultContextPropagators
import io.opentelemetry.extension.trace.propagation.B3Propagator
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.InstrumentationTestRunner
import io.opentelemetry.instrumentation.test.InstrumentationTestTrait
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import spock.lang.Shared

class TracingRequestWrapperTestBase extends InstrumentationSpecification implements InstrumentationTestTrait {

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  @Shared
  TracingRequestWrapperBase wrapper

  @Shared
  Context context

  def childSetupSpec() {
    context = Mock(Context)
    context.getFunctionName() >> "my_function"
    context.getAwsRequestId() >> "1-22-333"

    InstrumentationTestRunner.setGlobalPropagators(DefaultContextPropagators.builder()
      .addTextMapPropagator(B3Propagator.getInstance()).build())
  }

  def setLambda(handler, wrapperClass) {
    environmentVariables.set(WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY, handler)
    TracingRequestWrapper.WRAPPED_LAMBDA = WrappedLambda.fromConfiguration()
    wrapper = wrapperClass.getDeclaredConstructor().newInstance()
  }
}
