/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.spotconnector;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.beans.connector.spotconnector.SpotCredentialType.PERMANENT_TOKEN;
import static io.harness.spotinst.model.SpotInstConstants.spotInstHealthUrl;

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
public class SpotCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ConnectorConfigDTO connectorConfigDTO, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    SpotConnectorDTO spotConnectorDTO = (SpotConnectorDTO) connectorConfigDTO;
    SpotCredentialDTO credential = spotConnectorDTO.getCredential();
    if (credential.getSpotCredentialType() == PERMANENT_TOKEN) {
      capabilityList.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
          spotInstHealthUrl, maskingEvaluator));
    }
    populateDelegateSelectorCapability(capabilityList, spotConnectorDTO.getDelegateSelectors(), spotConnectorDTO.getConnectorType());
    return capabilityList;
  }
}
