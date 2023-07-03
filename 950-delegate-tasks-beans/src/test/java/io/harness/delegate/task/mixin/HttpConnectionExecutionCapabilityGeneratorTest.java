/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.mixin;

import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.HttpCapabilityDetailsLevel.DOMAIN;
import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.HttpCapabilityDetailsLevel.PATH;
import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.HttpCapabilityDetailsLevel.QUERY;
import static io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.beans.KeyValuePair;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.expression.MaskingExpressionEvaluator;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HttpConnectionExecutionCapabilityGeneratorTest extends CategoryTest {
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testFetchCapabilityBasis() {
    HttpConnectionExecutionCapability noPortPath = buildHttpConnectionExecutionCapability("http://domain", null);
    assertThat(noPortPath.getUrl()).isNull();
    assertThat(noPortPath.fetchCapabilityBasis()).isEqualTo("http://domain");

    HttpConnectionExecutionCapability noPort = buildHttpConnectionExecutionCapability("http://domain/path", null);
    assertThat(noPort.getUrl()).isNull();
    assertThat(noPort.fetchCapabilityBasis()).isEqualTo("http://domain/path");

    HttpConnectionExecutionCapability all = buildHttpConnectionExecutionCapability("http://domain:80/path", null);
    assertThat(all.getUrl()).isNull();
    assertThat(all.fetchCapabilityBasis()).isEqualTo("http://domain:80/path");

    HttpConnectionExecutionCapability user = buildHttpConnectionExecutionCapability("http://user@domain:80/path", null);
    assertThat(user.getUrl()).isNull();
    assertThat(user.fetchCapabilityBasis()).isEqualTo("http://domain:80/path");

    HttpConnectionExecutionCapability userPass =
        buildHttpConnectionExecutionCapability("http://user:pass@domain", null);
    assertThat(userPass.getUrl()).isNull();
    assertThat(userPass.fetchCapabilityBasis()).isEqualTo("http://domain");

    HttpConnectionExecutionCapability bad = buildHttpConnectionExecutionCapability("http://domain.$$$", null);
    assertThat(bad.getUrl()).isNotNull();
    assertThat(bad.fetchCapabilityBasis()).isEqualTo("http://domain.$$$");

    HttpConnectionExecutionCapability expression = buildHttpConnectionExecutionCapability("http://${expression}", null);
    assertThat(expression.getUrl()).isNotNull();
    assertThat(expression.fetchCapabilityBasis()).isEqualTo("http://${expression}");
    assertThat(expression.getQuery()).isNull();

    HttpConnectionExecutionCapability withQuery =
        buildHttpConnectionExecutionCapability("http://35.239.148.216:8080/api/v1/query?query=up", null);
    assertThat(withQuery.getQuery()).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testBuildHttpConnectionExecutionCapability_defaultHttpCapabilityDetailsLevel() {
    HttpConnectionExecutionCapability capability =
        buildHttpConnectionExecutionCapability("http://domain.com:8080/path/1/2/3?q1=1&q2=2", null);
    assertThat(capability.getHost()).isEqualTo("domain.com");
    assertThat(capability.getScheme()).isEqualTo("http");
    assertThat(capability.getPort()).isEqualTo(8080);
    assertThat(capability.getPath()).isEqualTo("path/1/2/3");
    assertThat(capability.getUrl()).isNull();
    assertThat(capability.getQuery()).isNull();
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testBuildHttpConnectionExecutionCapabilityWithIgnoreResponseCode() {
    HttpConnectionExecutionCapability capability =
        buildHttpConnectionExecutionCapability("http://domain.com:8080/path/1/2/3?q1=1&q2=2", PATH, null);
    capability.setIgnoreResponseCode(true);
    assertThat(capability.getHost()).isEqualTo("domain.com");
    assertThat(capability.getScheme()).isEqualTo("http");
    assertThat(capability.getPort()).isEqualTo(8080);
    assertThat(capability.getPath()).isEqualTo("path/1/2/3");
    assertThat(capability.getUrl()).isNull();
    assertThat(capability.getQuery()).isNull();
    assertThat(capability.getHeaders()).isNull();
    assertThat(capability.isIgnoreResponseCode()).isTrue();
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testBuildHttpConnectionExecutionCapabilityWithIgnoreResponseCodeLevelQuery() {
    HttpConnectionExecutionCapability capability =
        buildHttpConnectionExecutionCapability("http://domain.com:8080/path/1/2/3?q1=1&q2=2", QUERY, null);
    capability.setIgnoreResponseCode(true);
    assertThat(capability.getHost()).isEqualTo("domain.com");
    assertThat(capability.getScheme()).isEqualTo("http");
    assertThat(capability.getPort()).isEqualTo(8080);
    assertThat(capability.getPath()).isEqualTo("path/1/2/3");
    assertThat(capability.getUrl()).isNull();
    assertThat(capability.getQuery()).isEqualTo("q1=1&q2=2");
    assertThat(capability.getHeaders()).isNull();
    assertThat(capability.isIgnoreResponseCode()).isTrue();
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testBuildHttpConnectionExecutionCapabilityWithIgnoreResponseCodeLevelQueryWithHeaders() {
    HttpConnectionExecutionCapability capability =
        buildHttpConnectionExecutionCapability("http://domain.com:8080/path/1/2/3?q1=1&q2=2",
            Collections.singletonList(KeyValuePair.builder().key("x-api-key").value("1234").build()), QUERY, null);
    capability.setIgnoreResponseCode(true);
    assertThat(capability.getHost()).isEqualTo("domain.com");
    assertThat(capability.getScheme()).isEqualTo("http");
    assertThat(capability.getPort()).isEqualTo(8080);
    assertThat(capability.getPath()).isEqualTo("path/1/2/3");
    assertThat(capability.getUrl()).isNull();
    assertThat(capability.getQuery()).isEqualTo("q1=1&q2=2");
    assertThat(capability.isIgnoreResponseCode()).isTrue();
    assertThat(capability.getHeaders().get(0).getKey()).isEqualTo("x-api-key");
    assertThat(capability.getHeaders().get(0).getValue()).isEqualTo("1234");
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testBuildHttpConnectionExecutionCapability_withHttpCapabilityDetailsLevelDomain() {
    HttpConnectionExecutionCapability capability =
        buildHttpConnectionExecutionCapability("http://domain.com:8080/path/1/2/3?q1=1&q2=2", DOMAIN, null);
    assertThat(capability.getHost()).isEqualTo("domain.com");
    assertThat(capability.getScheme()).isEqualTo("http");
    assertThat(capability.getPort()).isEqualTo(8080);
    assertThat(capability.getPath()).isNull();
    assertThat(capability.getUrl()).isNull();
    assertThat(capability.getQuery()).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testBuildHttpConnectionExecutionCapability_withHttpCapabilityDetailsLevelPath() {
    HttpConnectionExecutionCapability capability =
        buildHttpConnectionExecutionCapability("http://domain.com:8080/path/1/2/3?q1=1&q2=2", PATH, null);
    assertThat(capability.getHost()).isEqualTo("domain.com");
    assertThat(capability.getScheme()).isEqualTo("http");
    assertThat(capability.getPort()).isEqualTo(8080);
    assertThat(capability.getPath()).isEqualTo("path/1/2/3");
    assertThat(capability.getUrl()).isNull();
    assertThat(capability.getQuery()).isNull();
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testBuildHttpConnectionExecutionCapability_withHttpCapabilityDetailsLevelQuery() {
    HttpConnectionExecutionCapability capability =
        buildHttpConnectionExecutionCapability("http://domain.com:8080/path/1/2/3?q1=1&q2=2", QUERY, null);
    assertThat(capability.getHost()).isEqualTo("domain.com");
    assertThat(capability.getScheme()).isEqualTo("http");
    assertThat(capability.getPort()).isEqualTo(8080);
    assertThat(capability.getPath()).isEqualTo("path/1/2/3");
    assertThat(capability.getUrl()).isNull();
    assertThat(capability.getQuery()).isEqualTo("q1=1&q2=2");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testBuildHttpConnectionExecutionCapabilityWithHeaders_WithoutHeaders() {
    HttpConnectionExecutionCapability capability =
        buildHttpConnectionExecutionCapability("http://domain.com:8080/path/1/2/3?q1=1&q2=2", null, QUERY, null);
    assertThat(capability.getHost()).isEqualTo("domain.com");
    assertThat(capability.getScheme()).isEqualTo("http");
    assertThat(capability.getPort()).isEqualTo(8080);
    assertThat(capability.getPath()).isEqualTo("path/1/2/3");
    assertThat(capability.getUrl()).isNull();
    assertThat(capability.getQuery()).isEqualTo("q1=1&q2=2");
    assertThat(capability.getHeaders()).isNull();
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testBuildHttpConnectionExecutionCapabilityWithHeaders_WithHeaders() {
    HttpConnectionExecutionCapability capability =
        buildHttpConnectionExecutionCapability("http://domain.com:8080/path/1/2/3?q1=1&q2=2",
            Collections.singletonList(KeyValuePair.builder().key("x-api-key").value("1234").build()), QUERY, null);
    assertThat(capability.getHost()).isEqualTo("domain.com");
    assertThat(capability.getScheme()).isEqualTo("http");
    assertThat(capability.getPort()).isEqualTo(8080);
    assertThat(capability.getPath()).isEqualTo("path/1/2/3");
    assertThat(capability.getUrl()).isNull();
    assertThat(capability.getQuery()).isEqualTo("q1=1&q2=2");
    assertThat(capability.getHeaders().get(0).getKey()).isEqualTo("x-api-key");
    assertThat(capability.getHeaders().get(0).getValue()).isEqualTo("1234");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testBuildHttpConnectionExecutionCapability_withMaskingEvaluatorNGSecret() {
    HttpConnectionExecutionCapability capability = buildHttpConnectionExecutionCapability(
        "http://google.com/${ngSecretManager.obtain(\"secretRef\",-12345)}", PATH, new MaskingExpressionEvaluator());
    assertThat(capability.getHost()).isNull();
    assertThat(capability.getQuery()).isNull();
    assertThat(capability.getUrl()).isEqualTo("http://google.com/<<<secretRef>>>");
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testBuildHttpConnectionExecutionCapability_withMaskingEvaluatorSweepingOPSecret() {
    HttpConnectionExecutionCapability capability = buildHttpConnectionExecutionCapability(
        "https://google.com/${sweepingOutputSecrets.obtain(\"output1\",\"sa32zupgqijF2be+H2lEAw7yfMwGDFtC5zciKbzQGEtm5Vq+cjo7RclAhPVLTig7\")}",
        PATH, new MaskingExpressionEvaluator());
    assertThat(capability.getHost()).isNull();
    assertThat(capability.getQuery()).isNull();
    assertThat(capability.getUrl()).isEqualTo("https://google.com/<<<output1>>>");
  }
}
