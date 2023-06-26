/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.customhealthconnector;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomHealthCapabilityHelperTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.ANSUMAN)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilities() {
    String baseURL = "http://0.0.0.0:8080";
    ConnectorConfigDTO connectorConfigDTO =
        CustomHealthConnectorDTO.builder()
            .baseURL(baseURL)
            .headers(List.of(CustomHealthKeyAndValue.builder().key("header-key").value("header-value").build()))
            .method(CustomHealthMethod.POST)
            .validationPath("metrics?from=1527102292")
            .params(List.of(CustomHealthKeyAndValue.builder().key("key").value("value").build()))
            .build();
    List<ExecutionCapability> executionCapabilities =
        CustomHealthCapabilityHelper.fetchRequiredExecutionCapabilities(null, connectorConfigDTO);
    assertThat(executionCapabilities).hasSize(1);
    assertThat(executionCapabilities.get(0)).isInstanceOf(HttpConnectionExecutionCapability.class);
    assertThat(executionCapabilities.get(0).fetchCapabilityBasis()).isEqualTo(baseURL);
    HttpConnectionExecutionCapability httpCapability = (HttpConnectionExecutionCapability) executionCapabilities.get(0);
    assertThat(httpCapability.getHeaders()).isNull();
    assertThat(httpCapability.isIgnoreResponseCode()).isTrue();
  }
}
