/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class AzureCapabilityHelper extends ConnectorCapabilityBaseHelper {
  private static final String AZURE_URL = "https://login.microsoftonline.com/";
  private static final String AZURE_US_GOV_URL = "https://login.microsoftonline.us/";

  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ConnectorConfigDTO connectorConfigDTO, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    AzureConnectorDTO azureConnectorDTO = (AzureConnectorDTO) connectorConfigDTO;
    if (azureConnectorDTO != null) {
      String encryptionServiceUrl;
      if (azureConnectorDTO.getAzureEnvironmentType() == null) {
        encryptionServiceUrl = AZURE_URL;
      } else {
        switch (azureConnectorDTO.getAzureEnvironmentType()) {
          case AZURE_US_GOVERNMENT:
            encryptionServiceUrl = AZURE_US_GOV_URL;
            break;
          case AZURE:
          default:
            encryptionServiceUrl = AZURE_URL;
        }
      }
      executionCapabilities.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
          encryptionServiceUrl, maskingEvaluator));
      populateDelegateSelectorCapability(executionCapabilities, azureConnectorDTO.getDelegateSelectors());
    }
    return executionCapabilities;
  }
}
