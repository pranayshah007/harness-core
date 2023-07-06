/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.GAURAV_NANDA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class NGAzureKeyVaultFetchEngineTaskParametersTest {
  @Test
  @Owner(developers = GAURAV_NANDA)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilities_containsSubscriptionReachableUrl() {
    // Arrange
    AzureKeyVaultConnectorDTO defaultAzureConnectorDTO = AzureKeyVaultConnectorDTO.builder().build();
    NGAzureKeyVaultFetchEngineTaskParameters parameters =
        NGAzureKeyVaultFetchEngineTaskParameters.builder().azureKeyVaultConnectorDTO(defaultAzureConnectorDTO).build();

    // Act
    List<ExecutionCapability> executionCapabilities = parameters.fetchRequiredExecutionCapabilities(null);

    // Assert
    assertThat(executionCapabilities).hasSize(1);
    assertThat(executionCapabilities.get(0))
        .isEqualTo(HttpConnectionExecutionCapability.builder()
                       .scheme("https")
                       .host("management.azure.com")
                       .port(-1)
                       .path("subscriptions")
                       .query("api-version=2019-06-01")
                       .build());
  }

  @Test
  @Owner(developers = GAURAV_NANDA)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilities_dtoWithDelegateSelector_returnsSelectorCapability() {
    // Arrange
    AzureKeyVaultConnectorDTO defaultAzureConnectorDTO =
        AzureKeyVaultConnectorDTO.builder().delegateSelectors(Set.of("selector1", "selector2")).build();
    NGAzureKeyVaultFetchEngineTaskParameters parameters =
        NGAzureKeyVaultFetchEngineTaskParameters.builder().azureKeyVaultConnectorDTO(defaultAzureConnectorDTO).build();

    // Act
    List<ExecutionCapability> executionCapabilities = parameters.fetchRequiredExecutionCapabilities(null);

    // Assert
    assertThat(executionCapabilities).hasSize(2);
    assertThat(executionCapabilities.get(1))
        .isEqualTo(SelectorCapability.builder()
                       .selectors(Set.of("selector1", "selector2"))
                       .selectorOrigin("connector")
                       .build());
  }
}
