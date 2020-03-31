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
package io.opentelemetry.auto.instrumentation.apachehttpclient.v2_0;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.aiappid.AiAppId;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator;
import io.opentelemetry.trace.Tracer;
import java.net.URI;
import java.net.URISyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.URIException;

@Slf4j
public class CommonsHttpClientDecorator extends HttpClientDecorator<HttpMethod, HttpMethod> {
  public static final CommonsHttpClientDecorator DECORATE = new CommonsHttpClientDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.apache-httpclient-2.0");

  @Override
  protected String getComponentName() {
    return "apache-httpclient";
  }

  @Override
  protected String method(final HttpMethod httpMethod) {
    return httpMethod.getName();
  }

  @Override
  protected URI url(final HttpMethod httpMethod) throws URISyntaxException {
    try {
      //  org.apache.commons.httpclient.URI -> java.net.URI
      return new URI(httpMethod.getURI().toString());
    } catch (final URIException e) {
      throw new URISyntaxException("", e.getMessage());
    }
  }

  @Override
  protected String hostname(final HttpMethod httpMethod) {
    try {
      return httpMethod.getURI().getHost();
    } catch (final URIException e) {
      return null;
    }
  }

  @Override
  protected Integer port(final HttpMethod httpMethod) {
    try {
      return httpMethod.getURI().getPort();
    } catch (final URIException e) {
      return null;
    }
  }

  @Override
  protected Integer status(final HttpMethod httpMethod) {
    final StatusLine statusLine = httpMethod.getStatusLine();
    return statusLine == null ? null : statusLine.getStatusCode();
  }

  @Override
  protected String getAiAppIdResponseHeader(final HttpMethod httpMethod) {
    final Header header = httpMethod.getResponseHeader(AiAppId.RESPONSE_HEADER_NAME);
    return header == null ? null : header.getValue();
  }
}
