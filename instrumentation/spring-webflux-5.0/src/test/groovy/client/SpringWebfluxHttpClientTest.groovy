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
package client

import io.opentelemetry.auto.bootstrap.instrumentation.aiappid.AiAppId
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.asserts.TraceAssert
import io.opentelemetry.auto.test.base.HttpClientTest
import org.springframework.http.HttpMethod
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import spock.lang.Timeout

import static io.opentelemetry.trace.Span.Kind.CLIENT

@Timeout(5)
class SpringWebfluxHttpClientTest extends HttpClientTest {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    ClientResponse response = WebClient.builder().build().method(HttpMethod.resolve(method))
        .uri(uri)
        .headers { h -> headers.forEach({ key, value -> h.add(key, value) }) }
        .exchange()
        .doAfterSuccessOrError { res, ex ->
          callback?.call()
        }
        .block()

    response.statusCode().value()
  }

  @Override
  // parent spanRef must be cast otherwise it breaks debugging classloading (junit loads it early)
  void clientSpan(TraceAssert trace, int index, Object parentSpan, String method = "GET", boolean tagQueryString = false, URI uri = server.address.resolve("/success"), Integer status = 200, Throwable exception = null) {
    super.clientSpan(trace, index, parentSpan, method, tagQueryString, uri, status, exception)
    if (!exception) {
      trace.span(index + 1) {
        childOf(trace.span(index))
        operationName "HTTP $method"
        spanKind CLIENT
        errored exception != null
        tags {
          "$MoreTags.NET_PEER_NAME" "localhost"
          "$MoreTags.NET_PEER_PORT" uri.port
          "$MoreTags.NET_PEER_IP" { it == null || it == "127.0.0.1" } // Optional
          "$Tags.HTTP_URL" { it == "${uri}" || it == "${removeFragment(uri)}" }
          "$Tags.HTTP_METHOD" method
          if (status) {
            "$Tags.HTTP_STATUS" status
          }
          if (tagQueryString) {
            "$MoreTags.HTTP_QUERY" uri.query
            "$MoreTags.HTTP_FRAGMENT" { it == null || it == uri.fragment } // Optional
          }
          if (exception) {
            errorTags(exception.class, exception.message)
          }
          if (!exception && uri.host != "www.google.com") {
            "$AiAppId.SPAN_TARGET_ATTRIBUTE_NAME" AiAppId.getAppId()
          }
        }
      }
    }
  }

  @Override
  int extraClientSpans() {
    // has netty-client span inside of spring-webflux-client
    return 1
  }

  boolean testRedirects() {
    false
  }

  boolean testConnectionFailure() {
    false
  }


  boolean testRemoteConnection() {
    // FIXME: figure out how to configure timeouts.
    false
  }
}
