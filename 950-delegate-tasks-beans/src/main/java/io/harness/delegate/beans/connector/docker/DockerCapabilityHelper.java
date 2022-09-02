/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.docker;

import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DockerCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ConnectorConfigDTO connectorConfigDTO, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    DockerConnectorDTO dockerConnectorDTO = (DockerConnectorDTO) connectorConfigDTO;
    String dockerRegistryUrl = dockerConnectorDTO.getDockerRegistryUrl();
    if (!(dockerRegistryUrl.endsWith("/v2") || dockerRegistryUrl.endsWith("/v2/"))) {
      dockerRegistryUrl = dockerRegistryUrl.concat("/v2");
    }
    capabilityList.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
        dockerRegistryUrl.endsWith("/") ? dockerRegistryUrl : dockerRegistryUrl.concat("/"), maskingEvaluator));
    populateDelegateSelectorCapability(capabilityList, dockerConnectorDTO.getDelegateSelectors());
    return capabilityList;
  }
}
