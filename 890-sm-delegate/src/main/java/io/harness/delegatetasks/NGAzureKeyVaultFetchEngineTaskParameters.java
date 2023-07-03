/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@OwnedBy(PL)
@Getter
@Builder
public class NGAzureKeyVaultFetchEngineTaskParameters implements TaskParameters, ExecutionCapabilityDemander {
  private final AzureKeyVaultConnectorDTO azureKeyVaultConnectorDTO;
  private final String SUBSCRIPTION_REACHABLE_URL = "https://management.azure.com/subscriptions?api-version=2019-06-01";

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    executionCapabilities.add(
        HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(SUBSCRIPTION_REACHABLE_URL,
            HttpConnectionExecutionCapabilityGenerator.HttpCapabilityDetailsLevel.QUERY, maskingEvaluator));
    ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability(
        executionCapabilities, azureKeyVaultConnectorDTO.getDelegateSelectors());
    return executionCapabilities;
  }
}
