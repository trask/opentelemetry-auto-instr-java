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
package io.opentelemetry.auto.config

import io.opentelemetry.auto.util.test.AgentSpecification
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import org.junit.contrib.java.lang.system.RestoreSystemProperties

import static io.opentelemetry.auto.config.Config.CONFIGURATION_FILE
import static io.opentelemetry.auto.config.Config.HTTP_CLIENT_ERROR_STATUSES
import static io.opentelemetry.auto.config.Config.HTTP_SERVER_ERROR_STATUSES
import static io.opentelemetry.auto.config.Config.PREFIX
import static io.opentelemetry.auto.config.Config.RUNTIME_CONTEXT_FIELD_INJECTION
import static io.opentelemetry.auto.config.Config.TRACE_ENABLED
import static io.opentelemetry.auto.config.Config.TRACE_METHODS

class ConfigTest extends AgentSpecification {
  @Rule
  public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties()
  @Rule
  public final EnvironmentVariables environmentVariables = new EnvironmentVariables()

  private static final TRACE_ENABLED_ENV = "OTA_TRACE_ENABLED"
  private static final TRACE_METHODS_ENV = "OTA_TRACE_METHODS"

  def "verify defaults"() {
    when:
    Config config = provider()

    then:
    config.traceEnabled == true
    config.httpServerErrorStatuses == (500..599).toSet()
    config.httpClientErrorStatuses == (400..599).toSet()
    config.runtimeContextFieldInjection == true
    config.toString().contains("traceEnabled=true")

    where:
    provider << [{ new Config() }, { Config.get() }, {
      def props = new Properties()
      props.setProperty("something", "unused")
      Config.get(props)
    }]
  }

  def "specify overrides via properties"() {
    setup:
    def prop = new Properties()
    prop.setProperty(TRACE_ENABLED, "false")
    prop.setProperty(TRACE_METHODS, "mypackage.MyClass[myMethod]")
    prop.setProperty(HTTP_SERVER_ERROR_STATUSES, "123-456,457,124-125,122")
    prop.setProperty(HTTP_CLIENT_ERROR_STATUSES, "111")
    prop.setProperty(RUNTIME_CONTEXT_FIELD_INJECTION, "false")

    when:
    Config config = Config.get(prop)

    then:
    config.traceEnabled == false
    config.traceMethods == "mypackage.MyClass[myMethod]"
    config.httpServerErrorStatuses == (122..457).toSet()
    config.httpClientErrorStatuses == (111..111).toSet()
    config.runtimeContextFieldInjection == false
  }

  def "specify overrides via system properties"() {
    setup:
    System.setProperty(PREFIX + TRACE_ENABLED, "false")
    System.setProperty(PREFIX + TRACE_METHODS, "mypackage.MyClass[myMethod]")
    System.setProperty(PREFIX + HTTP_SERVER_ERROR_STATUSES, "123-456,457,124-125,122")
    System.setProperty(PREFIX + HTTP_CLIENT_ERROR_STATUSES, "111")
    System.setProperty(PREFIX + RUNTIME_CONTEXT_FIELD_INJECTION, "false")

    when:
    Config config = new Config()

    then:
    config.traceEnabled == false
    config.traceMethods == "mypackage.MyClass[myMethod]"
    config.httpServerErrorStatuses == (122..457).toSet()
    config.httpClientErrorStatuses == (111..111).toSet()
    config.runtimeContextFieldInjection == false
  }

  def "specify overrides via env vars"() {
    setup:
    environmentVariables.set(TRACE_ENABLED_ENV, "false")
    environmentVariables.set(TRACE_METHODS_ENV, "mypackage.MyClass[myMethod]")

    when:
    def config = new Config()

    then:
    config.traceEnabled == false
    config.traceMethods == "mypackage.MyClass[myMethod]"
  }

  def "sys props override env vars"() {
    setup:
    environmentVariables.set(TRACE_METHODS_ENV, "mypackage.MyClass[myMethod]")

    System.setProperty(PREFIX + TRACE_METHODS, "mypackage2.MyClass2[myMethod2]")

    when:
    def config = new Config()

    then:
    config.traceMethods == "mypackage2.MyClass2[myMethod2]"
  }

  def "default when configured incorrectly"() {
    setup:
    System.setProperty(PREFIX + TRACE_ENABLED, " ")
    System.setProperty(PREFIX + TRACE_METHODS, " ")
    System.setProperty(PREFIX + HTTP_SERVER_ERROR_STATUSES, "1111")
    System.setProperty(PREFIX + HTTP_CLIENT_ERROR_STATUSES, "1:1")

    when:
    def config = new Config()

    then:
    config.traceEnabled == true
    config.traceMethods == " "
    config.httpServerErrorStatuses == (500..599).toSet()
    config.httpClientErrorStatuses == (400..599).toSet()
  }

  def "sys props override properties"() {
    setup:
    Properties properties = new Properties()
    properties.setProperty(TRACE_ENABLED, "false")
    properties.setProperty(TRACE_METHODS, "mypackage.MyClass[myMethod]")
    properties.setProperty(HTTP_SERVER_ERROR_STATUSES, "123-456,457,124-125,122")
    properties.setProperty(HTTP_CLIENT_ERROR_STATUSES, "111")

    when:
    def config = Config.get(properties)

    then:
    config.traceEnabled == false
    config.traceMethods == "mypackage.MyClass[myMethod]"
    config.httpServerErrorStatuses == (122..457).toSet()
    config.httpClientErrorStatuses == (111..111).toSet()
  }

  def "override null properties"() {
    when:
    def config = Config.get(null)

    then:
    config.traceEnabled == true
  }

  def "override empty properties"() {
    setup:
    Properties properties = new Properties()

    when:
    def config = Config.get(properties)

    then:
    config.traceEnabled == true
  }

  def "override non empty properties"() {
    setup:
    Properties properties = new Properties()
    properties.setProperty("foo", "bar")

    when:
    def config = Config.get(properties)

    then:
    config.traceEnabled == true
  }

  def "verify integration config"() {
    setup:
    environmentVariables.set("OTA_INTEGRATION_ORDER_ENABLED", "false")
    environmentVariables.set("OTA_INTEGRATION_TEST_ENV_ENABLED", "true")
    environmentVariables.set("OTA_INTEGRATION_DISABLED_ENV_ENABLED", "false")

    System.setProperty("ota.integration.order.enabled", "true")
    System.setProperty("ota.integration.test-prop.enabled", "true")
    System.setProperty("ota.integration.disabled-prop.enabled", "false")

    expect:
    Config.get().isIntegrationEnabled(integrationNames, defaultEnabled) == expected

    where:
    names                          | defaultEnabled | expected
    []                             | true           | true
    []                             | false          | false
    ["invalid"]                    | true           | true
    ["invalid"]                    | false          | false
    ["test-prop"]                  | false          | true
    ["test-env"]                   | false          | true
    ["disabled-prop"]              | true           | false
    ["disabled-env"]               | true           | false
    ["other", "test-prop"]         | false          | true
    ["other", "test-env"]          | false          | true
    ["order"]                      | false          | true
    ["test-prop", "disabled-prop"] | false          | true
    ["disabled-env", "test-env"]   | false          | true
    ["test-prop", "disabled-prop"] | true           | false
    ["disabled-env", "test-env"]   | true           | false

    integrationNames = new TreeSet<>(names)
  }

  def "test getFloatSettingFromEnvironment(#name)"() {
    setup:
    environmentVariables.set("OTA_ENV_ZERO_TEST", "0.0")
    environmentVariables.set("OTA_ENV_FLOAT_TEST", "1.0")
    environmentVariables.set("OTA_FLOAT_TEST", "0.2")

    System.setProperty("ota.prop.zero.test", "0")
    System.setProperty("ota.prop.float.test", "0.3")
    System.setProperty("ota.float.test", "0.4")
    System.setProperty("ota.garbage.test", "garbage")
    System.setProperty("ota.negative.test", "-1")

    expect:
    Config.get().getFloatSettingFromEnvironment(name, defaultValue) == (float) expected

    where:
    name              | expected
    "env.zero.test"   | 0.0
    "prop.zero.test"  | 0
    "env.float.test"  | 1.0
    "prop.float.test" | 0.3
    "float.test"      | 0.4
    "negative.test"   | -1.0
    "garbage.test"    | 10.0
    "default.test"    | 10.0

    defaultValue = 10.0
  }

  def "verify integer range configs on tracer"() {
    setup:
    System.setProperty(PREFIX + HTTP_SERVER_ERROR_STATUSES, value)
    System.setProperty(PREFIX + HTTP_CLIENT_ERROR_STATUSES, value)
    def props = new Properties()
    props.setProperty(HTTP_CLIENT_ERROR_STATUSES, value)
    props.setProperty(HTTP_SERVER_ERROR_STATUSES, value)

    when:
    def config = new Config()
    def propConfig = Config.get(props)

    then:
    if (expected) {
      assert config.httpServerErrorStatuses == expected.toSet()
      assert config.httpClientErrorStatuses == expected.toSet()
      assert propConfig.httpServerErrorStatuses == expected.toSet()
      assert propConfig.httpClientErrorStatuses == expected.toSet()
    } else {
      assert config.httpServerErrorStatuses == Config.DEFAULT_HTTP_SERVER_ERROR_STATUSES
      assert config.httpClientErrorStatuses == Config.DEFAULT_HTTP_CLIENT_ERROR_STATUSES
      assert propConfig.httpServerErrorStatuses == Config.DEFAULT_HTTP_SERVER_ERROR_STATUSES
      assert propConfig.httpClientErrorStatuses == Config.DEFAULT_HTTP_CLIENT_ERROR_STATUSES
    }

    where:
    value               | expected // null means default value
    "1"                 | null
    "a"                 | null
    ""                  | null
    "1000"              | null
    "100-200-300"       | null
    "500"               | [500]
    "100,999"           | [100, 999]
    "999-888"           | 888..999
    "400-403,405-407"   | [400, 401, 402, 403, 405, 406, 407]
    " 400 - 403 , 405 " | [400, 401, 402, 403, 405]
  }

  def "verify fallback to properties file"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/java-tracer.properties")

    when:
    def config = new Config()

    then:
    config.traceMethods == "mypackage.MyClass[myMethod]"

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
  }

  def "verify fallback to properties file has lower priority than system property"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/java-tracer.properties")
    System.setProperty(PREFIX + TRACE_METHODS, "mypackage2.MyClass2[myMethod2]")

    when:
    def config = new Config()

    then:
    config.traceMethods == "mypackage2.MyClass2[myMethod2]"

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
    System.clearProperty(PREFIX + TRACE_METHODS)
  }

  def "verify fallback to properties file has lower priority than env var"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/java-tracer.properties")
    environmentVariables.set("OTA_TRACE_METHODS", "mypackage2.MyClass2[myMethod2]")

    when:
    def config = new Config()

    then:
    config.traceMethods == "mypackage2.MyClass2[myMethod2]"

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
    System.clearProperty(PREFIX + TRACE_METHODS)
    environmentVariables.clear("OTA_TRACE_METHODS")
  }

  def "verify fallback to properties file that does not exist does not crash app"() {
    setup:
    System.setProperty(PREFIX + CONFIGURATION_FILE, "src/test/resources/do-not-exist.properties")

    when:
    def config = new Config()

    then:
    config.traceEnabled == true

    cleanup:
    System.clearProperty(PREFIX + CONFIGURATION_FILE)
  }
}
