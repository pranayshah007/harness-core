/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureblobconnector;

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
public class AzureBlobCapabilityHelper extends AzureCapabilityBaseHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      AzureBlobConnectorDTO azureBlobConnectorDTO, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    if (azureBlobConnectorDTO != null) {
      return getExecutionCapabilities(azureBlobConnectorDTO.getVaultName(), azureBlobConnectorDTO.getAzureEnvironmentType(),
              azureBlobConnectorDTO.getDelegateSelectors(), maskingEvaluator);
    }
    return executionCapabilities;
  }
}
