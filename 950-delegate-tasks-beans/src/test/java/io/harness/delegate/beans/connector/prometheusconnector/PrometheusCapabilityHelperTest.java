/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.prometheusconnector;

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

public class PrometheusCapabilityHelperTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.DHRUVX)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilities() {
    String prometheusBaseUrl = "http://0.0.0.0:8080";
    String prometheusQueryPath = "api/v1/query?query=up";
    ConnectorConfigDTO connectorConfigDTO = PrometheusConnectorDTO.builder().url(prometheusBaseUrl).build();
    List<ExecutionCapability> executionCapabilities =
        PrometheusCapabilityHelper.fetchRequiredExecutionCapabilities(null, connectorConfigDTO);
    assertThat(executionCapabilities).hasSize(1);
    assertThat(executionCapabilities.get(0)).isInstanceOf(HttpConnectionExecutionCapability.class);
    assertThat(executionCapabilities.get(0).fetchCapabilityBasis())
        .isEqualTo(prometheusBaseUrl + "/" + prometheusQueryPath);
  }
}
