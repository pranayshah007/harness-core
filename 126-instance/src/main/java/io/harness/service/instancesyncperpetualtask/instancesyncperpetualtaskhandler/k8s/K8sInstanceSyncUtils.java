/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.instancesyncperpetualtask.instancesyncperpetualtaskhandler.k8s;

import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_AWS;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_AZURE;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_GCP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureCapabilityHelper;
import io.harness.delegate.beans.connector.gcp.GcpCapabilityHelper;
import io.harness.delegate.beans.connector.k8Connector.K8sTaskCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public class K8sInstanceSyncUtils {
  public static List<ExecutionCapability> fetchRequiredK8sExecutionCapabilities(
      InfrastructureMappingDTO infrastructureMappingDTO, ConnectorConfigDTO connectorConfigDTO,
      ExpressionEvaluator maskingEvaluator, boolean useSocketCapability) {
    List<ExecutionCapability> capabilities = new ArrayList<>(Collections.emptyList());

    if (infrastructureMappingDTO.getInfrastructureKind().equals(KUBERNETES_DIRECT)) {
      capabilities.addAll(K8sTaskCapabilityHelper.fetchRequiredExecutionCapabilities(
          connectorConfigDTO, maskingEvaluator, useSocketCapability));
    }

    if (infrastructureMappingDTO.getInfrastructureKind().equals(KUBERNETES_GCP)) {
      capabilities.addAll(GcpCapabilityHelper.fetchRequiredExecutionCapabilities(connectorConfigDTO, maskingEvaluator));
    }

    if (infrastructureMappingDTO.getInfrastructureKind().equals(KUBERNETES_AZURE)) {
      capabilities.addAll(
          AzureCapabilityHelper.fetchRequiredExecutionCapabilities(connectorConfigDTO, maskingEvaluator));
    }

    if (infrastructureMappingDTO.getInfrastructureKind().equals(KUBERNETES_AWS)) {
      capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(connectorConfigDTO, maskingEvaluator));
    }
    return capabilities;
  }
}
