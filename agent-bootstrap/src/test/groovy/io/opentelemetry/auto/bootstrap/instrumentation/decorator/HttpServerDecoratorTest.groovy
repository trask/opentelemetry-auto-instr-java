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

package io.opentelemetry.auto.bootstrap.instrumentation.decorator

import io.opentelemetry.auto.config.Config
import io.opentelemetry.auto.instrumentation.api.MoreAttributes
import io.opentelemetry.trace.Span
import io.opentelemetry.trace.SpanContext
import io.opentelemetry.trace.attributes.SemanticAttributes

import static io.opentelemetry.auto.test.utils.ConfigUtils.withConfigOverride

class HttpServerDecoratorTest extends ServerDecoratorTest {

  def "test onRequest"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onRequest(span, req)

    then:
    if (req) {
      1 * span.getContext() >> SpanContext.getInvalid()
      1 * span.setAttribute(SemanticAttributes.HTTP_METHOD.key(), "test-method")
      1 * span.setAttribute(SemanticAttributes.HTTP_URL.key(), url)
    }
    0 * _

    where:
    req                                                                    | url
    null                                                                   | _
    [method: "test-method", url: URI.create("http://test-url?some=query")] | "http://test-url/?some=query"
    [method: "test-method", url: URI.create("http://a:80/")]               | "http://a/"
    [method: "test-method", url: URI.create("https://10.0.0.1:443")]       | "https://10.0.0.1/"
    [method: "test-method", url: URI.create("https://localhost:0/1/")]     | "https://localhost/1/"
    [method: "test-method", url: URI.create("http://123:8080/some/path")]  | "http://123:8080/some/path"
  }

  def "test url handling for #url"() {
    setup:
    def decorator = newDecorator()

    when:
    withConfigOverride(Config.HTTP_SERVER_TAG_QUERY_STRING, "$tagQueryString") {
      decorator.onRequest(span, req)
    }

    then:
    1 * span.getContext() >> SpanContext.getInvalid()
    if (expectedUrl) {
      1 * span.setAttribute(SemanticAttributes.HTTP_URL.key(), expectedUrl)
    }
    if (expectedUrl && tagQueryString) {
      1 * span.setAttribute(MoreAttributes.HTTP_QUERY, expectedQuery)
      1 * span.setAttribute(MoreAttributes.HTTP_FRAGMENT, expectedFragment)
    }
    1 * span.setAttribute(SemanticAttributes.HTTP_METHOD.key(), null)
    0 * _

    where:
    tagQueryString | url                                                    | expectedUrl                                      | expectedQuery       | expectedFragment
    false          | null                                                   | null                                             | null                | null
    false          | ""                                                     | "/"                                              | ""                  | null
    false          | "/path?query"                                          | "/path?query"                                    | ""                  | null
    false          | "https://host:0"                                       | "https://host/"                                  | ""                  | null
    false          | "https://host/path"                                    | "https://host/path"                              | ""                  | null
    false          | "http://host:99/path?query#fragment"                   | "http://host:99/path?query#fragment"             | ""                  | null
    true           | null                                                   | null                                             | null                | null
    true           | ""                                                     | "/"                                              | null                | null
    true           | "/path?encoded+%28query%29%3F?"                        | "/path?encoded+(query)??"                        | "encoded+(query)??" | null
    true           | "https://host:0"                                       | "https://host/"                                  | null                | null
    true           | "https://host/path"                                    | "https://host/path"                              | null                | null
    true           | "http://host:99/path?query#enc+%28fragment%29%3F"      | "http://host:99/path?query#enc+(fragment)?"      | "query"             | "enc+(fragment)?"
    true           | "http://host:99/path?query#enc+%28fragment%29%3F?tail" | "http://host:99/path?query#enc+(fragment)??tail" | "query"             | "enc+(fragment)??tail"

    req = [url: url == null ? null : new URI(url)]
  }

  def "test onConnection"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onConnection(span, conn)

    then:
    if (conn) {
      1 * span.setAttribute(SemanticAttributes.NET_PEER_PORT.key(), 555)
      if (ipv4) {
        1 * span.setAttribute(SemanticAttributes.NET_PEER_IP.key(), "10.0.0.1")
      } else if (ipv4 != null) {
        1 * span.setAttribute(SemanticAttributes.NET_PEER_IP.key(), "3ffe:1900:4545:3:200:f8ff:fe21:67cf")
      } else {
        1 * span.setAttribute(SemanticAttributes.NET_PEER_IP.key(), null)
      }
    }
    0 * _

    where:
    ipv4  | conn
    null  | null
    null  | [ip: null, port: 555]
    true  | [ip: "10.0.0.1", port: 555]
    false | [ip: "3ffe:1900:4545:3:200:f8ff:fe21:67cf", port: 555]
  }

  def "test onResponse"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onResponse(span, resp)

    then:
    if (status) {
      1 * span.setAttribute(SemanticAttributes.HTTP_STATUS_CODE.key(), status)
      1 * span.setStatus(HttpStatusConverter.statusFromHttpStatus(status))
    }
    0 * _

    where:
    status | resp
    200    | [status: 200]
    399    | [status: 399]
    400    | [status: 400]
    404    | [status: 404]
    499    | [status: 499]
    500    | [status: 500]
    600    | [status: 600]
    null   | [status: null]
    null   | null
  }

  def "test assert null span"() {
    setup:
    def decorator = newDecorator()

    when:
    decorator.onRequest((Span) null, null)

    then:
    thrown(AssertionError)

    when:
    decorator.onResponse((Span) null, null)

    then:
    thrown(AssertionError)
  }

  @Override
  def newDecorator() {
    return new HttpServerDecorator<Map, Map, Map>() {

      @Override
      protected String method(Map m) {
        return m.method
      }

      @Override
      protected URI url(Map m) {
        return m.url
      }

      @Override
      protected String peerHostIP(Map m) {
        return m.ip
      }

      @Override
      protected Integer peerPort(Map m) {
        return m.port
      }

      @Override
      protected Integer status(Map m) {
        return m.status
      }
    }
  }
}
