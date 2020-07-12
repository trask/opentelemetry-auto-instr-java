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

import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpResponse
import com.google.api.client.http.javanet.NetHttpTransport
import io.opentelemetry.auto.bootstrap.instrumentation.aiappid.AiAppId
import io.opentelemetry.auto.instrumentation.api.MoreAttributes
import io.opentelemetry.auto.test.base.HttpClientTest
import io.opentelemetry.trace.attributes.SemanticAttributes
import spock.lang.Shared

import static io.opentelemetry.trace.Span.Kind.CLIENT

abstract class AbstractGoogleHttpClientTest extends HttpClientTest {

  @Shared
  def requestFactory = new NetHttpTransport().createRequestFactory()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    doRequest(method, uri, headers, callback, false)
  }

  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback, boolean throwExceptionOnError) {
    GenericUrl genericUrl = new GenericUrl(uri)

    HttpRequest request = requestFactory.buildRequest(method, genericUrl, null)
    request.connectTimeout = CONNECT_TIMEOUT_MS

    // GenericData::putAll method converts all known http headers to List<String>
    // and lowercase all other headers
    def ci = request.getHeaders().getClassInfo()
    request.getHeaders().putAll(headers.collectEntries { name, value
      -> [(name): (ci.getFieldInfo(name) != null ? [value] : value.toLowerCase())]
    })

    request.setThrowExceptionOnExecuteError(throwExceptionOnError)

    HttpResponse response = executeRequest(request)
    callback?.call()

    return response.getStatusCode()
  }

  abstract HttpResponse executeRequest(HttpRequest request)

  @Override
  boolean testCircularRedirects() {
    // Circular redirects don't throw an exception with Google Http Client
    return false
  }

  def "error traces when exception is not thrown"() {
    given:
    def uri = server.address.resolve("/error")

    when:
    def status = doRequest(method, uri)

    then:
    status == 500
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          spanKind CLIENT
          errored true
          attributes {
            "${SemanticAttributes.NET_PEER_NAME.key()}" "localhost"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "${uri}"
            "${SemanticAttributes.HTTP_METHOD.key()}" method
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 500
            "$MoreAttributes.ERROR_MSG" "Server Error"
            "$AiAppId.SPAN_TARGET_ATTRIBUTE_NAME" AiAppId.getAppId()
            "$AiAppId.SPAN_TARGET_ATTRIBUTE_NAME" AiAppId.getAppId()
          }
        }
        server.distributedRequestSpan(it, 1, span(0))
      }
    }

    where:
    method = "GET"
  }
}
