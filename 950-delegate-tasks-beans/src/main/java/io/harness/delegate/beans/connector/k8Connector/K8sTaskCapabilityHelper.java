/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.k8Connector;

import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.delegate.task.mixin.SocketConnectivityCapabilityGenerator;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class K8sTaskCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public static List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ConnectorConfigDTO connectorConfigDTO, ExpressionEvaluator maskingEvaluator) {
    return fetchRequiredExecutionCapabilities(connectorConfigDTO, maskingEvaluator, false);
  }

  public static List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ConnectorConfigDTO connectorConfigDTO, ExpressionEvaluator maskingEvaluator, boolean useSocketCapability) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    KubernetesClusterConfigDTO kubernetesClusterConfigDTO = (KubernetesClusterConfigDTO) connectorConfigDTO;
    KubernetesCredentialDTO credential = kubernetesClusterConfigDTO.getCredential();
    if (credential.getKubernetesCredentialType() == KubernetesCredentialType.MANUAL_CREDENTIALS) {
      KubernetesClusterDetailsDTO k8sManualCreds = (KubernetesClusterDetailsDTO) credential.getConfig();

      if (useSocketCapability) {
        SocketConnectivityCapabilityGenerator.addSocketConnectivityExecutionCapability(
            k8sManualCreds.getMasterUrl(), capabilityList);
      } else {
        capabilityList.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
            k8sManualCreds.getMasterUrl(), maskingEvaluator));
      }
    } else if (credential.getKubernetesCredentialType() != KubernetesCredentialType.INHERIT_FROM_DELEGATE) {
      throw new UnknownEnumTypeException(
          "Kubernetes Credential Type", String.valueOf(credential.getKubernetesCredentialType()));
    }
    populateDelegateSelectorCapability(capabilityList, kubernetesClusterConfigDTO.getDelegateSelectors());
    return capabilityList;
  }
}
