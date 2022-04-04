/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureblobconnector;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConstants.AZURE_DEFAULT_ENCRYPTION_URL;
import static io.harness.delegate.beans.connector.azurekeyvaultconnector.AzureKeyVaultConstants.AZURE_US_GOVERNMENT_ENCRYPTION_URL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class AzureBlobCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      AzureBlobConnectorDTO azureBlobConnectorDTO, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    if (azureBlobConnectorDTO != null) {
      String encryptionServiceUrl;
      if (azureBlobConnectorDTO.getAzureEnvironmentType() == null) {
        encryptionServiceUrl = String.format(AZURE_DEFAULT_ENCRYPTION_URL, azureBlobConnectorDTO.getVaultName());
      } else {
        switch (azureBlobConnectorDTO.getAzureEnvironmentType()) {
          case AZURE_US_GOVERNMENT:
            encryptionServiceUrl =
                String.format(AZURE_US_GOVERNMENT_ENCRYPTION_URL, azureBlobConnectorDTO.getVaultName());
            break;
          case AZURE:
          default:
            encryptionServiceUrl = String.format(AZURE_DEFAULT_ENCRYPTION_URL, azureBlobConnectorDTO.getVaultName());
        }
      }

      executionCapabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
          encryptionServiceUrl, maskingEvaluator));
      populateDelegateSelectorCapability(executionCapabilities, azureBlobConnectorDTO.getDelegateSelectors());
    }
    return executionCapabilities;
  }
}
