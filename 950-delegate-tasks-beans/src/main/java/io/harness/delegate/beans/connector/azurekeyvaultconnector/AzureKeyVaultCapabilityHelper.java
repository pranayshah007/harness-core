/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azurekeyvaultconnector;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.helpers.AzureCapabilityBaseHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class AzureKeyVaultCapabilityHelper extends AzureCapabilityBaseHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
          AzureKeyVaultConnectorDTO azureKeyVaultConnectorDTO, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    if (azureKeyVaultConnectorDTO != null) {
      return getExecutionCapabilities(azureKeyVaultConnectorDTO.getVaultName(), azureKeyVaultConnectorDTO.getAzureEnvironmentType(),
              azureKeyVaultConnectorDTO.getDelegateSelectors(), maskingEvaluator);
    }
    return executionCapabilities;
  }
}
