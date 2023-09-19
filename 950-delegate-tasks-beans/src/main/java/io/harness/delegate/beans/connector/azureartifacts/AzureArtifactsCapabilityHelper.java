/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureartifacts;

import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AzureArtifactsCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ConnectorConfigDTO connectorConfigDTO, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();

    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO = (AzureArtifactsConnectorDTO) connectorConfigDTO;

    String registryUrl = azureArtifactsConnectorDTO.getAzureArtifactsUrl();

    capabilityList.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        registryUrl.endsWith("/") ? registryUrl : registryUrl.concat("/"), maskingEvaluator));

    populateDelegateSelectorCapability(capabilityList, azureArtifactsConnectorDTO.getDelegateSelectors(),azureArtifactsConnectorDTO.getConnectorType());

    return capabilityList;
  }
}
