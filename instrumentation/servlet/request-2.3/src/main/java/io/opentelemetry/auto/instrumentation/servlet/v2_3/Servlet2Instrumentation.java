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
package io.opentelemetry.auto.instrumentation.servlet.v2_3;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class Servlet2Instrumentation extends Instrumenter.Default {

  public Servlet2Instrumentation() {
    super("servlet-2.3", "servlet");
  }

  // this is required to make sure servlet 2 instrumentation won't apply to servlet 3
  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    return not(hasClassesNamed("javax.servlet.AsyncEvent", "javax.servlet.AsyncListener"));
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(
        named("javax.servlet.FilterChain").or(named("javax.servlet.http.HttpServlet")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".Servlet2Decorator",
      packageName + ".HttpServletRequestExtractAdapter",
      packageName + ".StatusSavingHttpServletResponseWrapper",
    };
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap(
        "javax.servlet.http.HttpServletResponse", "javax.servlet.http.HttpServletRequest");
  }

  /**
   * Here we are instrumenting the public method for HttpServlet. This should ensure that this
   * advice is always called before HttpServletInstrumentation which is instrumenting the protected
   * method.
   */
  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        named("doFilter")
            .or(named("service"))
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")))
            .and(isPublic()),
        packageName + ".Servlet2Advice");
  }
}
