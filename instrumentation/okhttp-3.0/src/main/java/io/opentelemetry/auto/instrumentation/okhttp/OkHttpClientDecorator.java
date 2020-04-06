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
package io.opentelemetry.auto.instrumentation.okhttp;

import io.opentelemetry.auto.bootstrap.instrumentation.aiappid.AiAppId;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientDecorator;
import java.net.URI;
import okhttp3.Request;
import okhttp3.Response;

public class OkHttpClientDecorator extends HttpClientDecorator<Request, Response> {
  public static final OkHttpClientDecorator DECORATE = new OkHttpClientDecorator();

  @Override
  protected String service() {
    return null;
  }

  @Override
  protected String getComponentName() {
    return "okhttp";
  }

  @Override
  protected String method(final Request httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected URI url(final Request httpRequest) {
    return httpRequest.url().uri();
  }

  @Override
  protected String hostname(final Request httpRequest) {
    return httpRequest.url().host();
  }

  @Override
  protected Integer port(final Request httpRequest) {
    return httpRequest.url().port();
  }

  @Override
  protected Integer status(final Response httpResponse) {
    return httpResponse.code();
  }

  @Override
  protected String getAiAppIdResponseHeader(final Response httpResponse) {
    return httpResponse.headers().get(AiAppId.RESPONSE_HEADER_NAME);
  }
}
