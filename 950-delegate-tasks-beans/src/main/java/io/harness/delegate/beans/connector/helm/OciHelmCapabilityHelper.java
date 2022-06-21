/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.helm;

import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.HelmInstallationCapability;
import io.harness.expression.ExpressionEvaluator;
import io.harness.k8s.model.HelmVersion;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OciHelmCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ConnectorConfigDTO connectorConfigDTO, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    OciHelmConnectorDTO helmConnector = (OciHelmConnectorDTO) connectorConfigDTO;
    final String helmRepoUrl = helmConnector.getHelmRepoUrl();
    capabilityList.add(HelmInstallationCapability.builder()
                           .version(HelmVersion.V380)
                           .criteria("OCI_HELM_REPO: " + helmRepoUrl)
                           .build());
    populateDelegateSelectorCapability(capabilityList, helmConnector.getDelegateSelectors());
    return capabilityList;
  }
}
