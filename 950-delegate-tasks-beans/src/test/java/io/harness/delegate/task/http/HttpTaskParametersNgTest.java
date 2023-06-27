/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.http.HttpHeaderConfig;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HttpTaskParametersNgTest {
  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testBuildCapabilityWithoutHeaders() {
    HttpTaskParametersNg httpTaskParametersNg =
        HttpTaskParametersNg.builder().method("GET").url("http://www.abc.xyz").build();

    List<ExecutionCapability> executionCapabilities = httpTaskParametersNg.fetchRequiredExecutionCapabilities(null);
    HttpConnectionExecutionCapability httpCapability = (HttpConnectionExecutionCapability) executionCapabilities.get(0);
    assertThat(httpCapability.getHeaders()).hasSize(0);
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testBuildCapabilityWithHeadersWithFFEnabled() {
    HttpTaskParametersNg httpTaskParametersNg =
        HttpTaskParametersNg.builder()
            .method("GET")
            .url("http://www.abc.xyz")
            .requestHeader(Collections.singletonList(HttpHeaderConfig.builder().key("x-api-key").value("test").build()))
            .shouldAvoidHeadersInCapability(true)
            .isIgnoreResponseCode(false)
            .build();

    List<ExecutionCapability> executionCapabilities = httpTaskParametersNg.fetchRequiredExecutionCapabilities(null);
    HttpConnectionExecutionCapability httpCapability = (HttpConnectionExecutionCapability) executionCapabilities.get(0);
    assertThat(httpCapability.getHeaders()).isNull();
    assertThat(httpCapability.isIgnoreResponseCode()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testBuildCapabilityWithHeadersWithFFEnabledWithIgnoreResponseCode() {
    HttpTaskParametersNg httpTaskParametersNg =
        HttpTaskParametersNg.builder()
            .method("GET")
            .url("http://www.abc.xyz")
            .requestHeader(Collections.singletonList(HttpHeaderConfig.builder().key("x-api-key").value("test").build()))
            .shouldAvoidHeadersInCapability(true)
            .isIgnoreResponseCode(true)
            .build();

    List<ExecutionCapability> executionCapabilities = httpTaskParametersNg.fetchRequiredExecutionCapabilities(null);
    HttpConnectionExecutionCapability httpCapability = (HttpConnectionExecutionCapability) executionCapabilities.get(0);
    assertThat(httpCapability.getHeaders()).isNull();
    assertThat(httpCapability.isIgnoreResponseCode()).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.HINGER)
  @Category(UnitTests.class)
  public void testBuildCapabilityWithHeadersWithFFDisabled() {
    HttpTaskParametersNg httpTaskParametersNg =
        HttpTaskParametersNg.builder()
            .method("GET")
            .url("http://www.abc.xyz")
            .requestHeader(Collections.singletonList(HttpHeaderConfig.builder().key("x-api-key").value("test").build()))
            .shouldAvoidHeadersInCapability(false)
            .isIgnoreResponseCode(false)
            .build();

    List<ExecutionCapability> executionCapabilities = httpTaskParametersNg.fetchRequiredExecutionCapabilities(null);
    HttpConnectionExecutionCapability httpCapability = (HttpConnectionExecutionCapability) executionCapabilities.get(0);
    assertThat(httpCapability.getHeaders()).hasSize(1);
    assertThat(httpCapability.isIgnoreResponseCode()).isFalse();
  }

  @Test
  @Owner(developers = OwnerRule.SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testBuildCapabilityWithHeadersWithFFDisabledWithIgnoreResponseCode() {
    HttpTaskParametersNg httpTaskParametersNg =
        HttpTaskParametersNg.builder()
            .method("GET")
            .url("http://www.abc.xyz")
            .requestHeader(Collections.singletonList(HttpHeaderConfig.builder().key("x-api-key").value("test").build()))
            .shouldAvoidHeadersInCapability(false)
            .isIgnoreResponseCode(true)
            .build();

    List<ExecutionCapability> executionCapabilities = httpTaskParametersNg.fetchRequiredExecutionCapabilities(null);
    HttpConnectionExecutionCapability httpCapability = (HttpConnectionExecutionCapability) executionCapabilities.get(0);
    assertThat(httpCapability.getHeaders()).hasSize(1);
    assertThat(httpCapability.isIgnoreResponseCode()).isTrue();
  }
}
