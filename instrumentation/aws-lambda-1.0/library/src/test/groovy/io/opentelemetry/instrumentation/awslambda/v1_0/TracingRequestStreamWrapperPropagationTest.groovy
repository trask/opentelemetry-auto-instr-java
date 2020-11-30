/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambda.v1_0

import static io.opentelemetry.api.trace.Span.Kind.SERVER

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.trace.attributes.SemanticAttributes
import io.opentelemetry.context.propagation.DefaultContextPropagators
import io.opentelemetry.extension.trace.propagation.B3Propagator
import io.opentelemetry.instrumentation.test.InstrumentationSpecification
import io.opentelemetry.instrumentation.test.InstrumentationTestRunner
import io.opentelemetry.instrumentation.test.InstrumentationTestTrait
import java.nio.charset.Charset
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import spock.lang.Shared

class TracingRequestStreamWrapperPropagationTest extends InstrumentationSpecification implements InstrumentationTestTrait {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()

  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  static class TestRequestHandler implements RequestStreamHandler {

    @Override
    void handleRequest(InputStream input, OutputStream output, Context context) {

      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output))

      JsonNode root = OBJECT_MAPPER.readTree(input)
      String body = root.get("body").asText()
      if (body == "hello") {
        writer.write("world")
        writer.flush()
        writer.close()
      } else {
        throw new IllegalArgumentException("bad argument")
      }
    }
  }

  @Shared
  TracingRequestStreamWrapper wrapper

  def childSetup() {
    InstrumentationTestRunner.setGlobalPropagators(DefaultContextPropagators.builder()
      .addTextMapPropagator(B3Propagator.getInstance()).build())
    environmentVariables.set(WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY, "io.opentelemetry.instrumentation.awslambda.v1_0.TracingRequestStreamWrapperPropagationTest\$TestRequestHandler::handleRequest")
    TracingRequestStreamWrapper.WRAPPED_LAMBDA = WrappedLambda.fromConfiguration()
    wrapper = new TracingRequestStreamWrapper()
  }

  def cleanup() {
    environmentVariables.clear(WrappedLambda.OTEL_LAMBDA_HANDLER_ENV_KEY)
  }

  def "handler traced with trace propagation"() {
    when:
    String content =
      "{" +
        "\"headers\" : {" +
        "\"X-B3-TraceId\": \"4fd0b6131f19f39af59518d127b0cafe\", \"X-B3-SpanId\": \"0000000000000456\", \"X-B3-Sampled\": \"true\"" +
        "}," +
        "\"body\" : \"hello\"" +
        "}"
    def context = Mock(Context)
    context.getFunctionName() >> "my_function"
    context.getAwsRequestId() >> "1-22-333"
    def input = new ByteArrayInputStream(content.getBytes(Charset.defaultCharset()))
    def output = new ByteArrayOutputStream()

    wrapper.handleRequest(input, output, context)

    then:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          parentSpanId("0000000000000456")
          traceId("4fd0b6131f19f39af59518d127b0cafe")
          name("my_function")
          kind SERVER
          attributes {
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
      }
    }
  }

  def "handler traced with exception and trace propagation"() {
    when:
    String content =
      "{" +
        "\"headers\" : {" +
        "\"X-B3-TraceId\": \"4fd0b6131f19f39af59518d127b0cafe\", \"X-B3-SpanId\": \"0000000000000456\", \"X-B3-Sampled\": \"true\"" +
        "}," +
        "\"body\" : \"bye\"" +
        "}"
    def context = Mock(Context)
    context.getFunctionName() >> "my_function"
    context.getAwsRequestId() >> "1-22-333"
    def input = new ByteArrayInputStream(content.getBytes(Charset.defaultCharset()))
    def output = new ByteArrayOutputStream()

    def thrown
    try {
      wrapper.handleRequest(input, output, context)
    } catch (Throwable t) {
      thrown = t
    }

    then:
    thrown != null
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          parentSpanId("0000000000000456")
          traceId("4fd0b6131f19f39af59518d127b0cafe")
          name("my_function")
          kind SERVER
          errored true
          errorEvent(IllegalArgumentException, "bad argument")
          attributes {
            "${SemanticAttributes.FAAS_EXECUTION.key}" "1-22-333"
          }
        }
      }
    }
  }

}
