/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.utils;

import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureCapabilityHelper;
import io.harness.delegate.beans.connector.gcp.GcpCapabilityHelper;
import io.harness.delegate.beans.connector.k8Connector.K8sTaskCapabilityHelper;
import io.harness.delegate.beans.connector.rancher.RancherTaskCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.k8s.AzureK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.EksK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.GcpK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.RancherK8sInfraDelegateConfig;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class K8sCapabilityGenerator {
  public static List<ExecutionCapability> generateExecutionCapabilities(
      ExpressionEvaluator maskingEvaluator, @NonNull K8sInfraDelegateConfig k8sInfraDelegateConfig) {
    if (k8sInfraDelegateConfig instanceof DirectK8sInfraDelegateConfig) {
      return K8sTaskCapabilityHelper.fetchRequiredExecutionCapabilities(
          ((DirectK8sInfraDelegateConfig) k8sInfraDelegateConfig).getKubernetesClusterConfigDTO(), maskingEvaluator,
          k8sInfraDelegateConfig.useSocketCapability());
    }

    if (k8sInfraDelegateConfig instanceof GcpK8sInfraDelegateConfig) {
      return GcpCapabilityHelper.fetchRequiredExecutionCapabilities(
          ((GcpK8sInfraDelegateConfig) k8sInfraDelegateConfig).getGcpConnectorDTO(), maskingEvaluator);
    }

    if (k8sInfraDelegateConfig instanceof AzureK8sInfraDelegateConfig) {
      return AzureCapabilityHelper.fetchRequiredExecutionCapabilities(
          ((AzureK8sInfraDelegateConfig) k8sInfraDelegateConfig).getAzureConnectorDTO(), maskingEvaluator);
    }

    if (k8sInfraDelegateConfig instanceof EksK8sInfraDelegateConfig) {
      return AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
          ((EksK8sInfraDelegateConfig) k8sInfraDelegateConfig).getAwsConnectorDTO(), maskingEvaluator);
    }

    if (k8sInfraDelegateConfig instanceof RancherK8sInfraDelegateConfig) {
      return RancherTaskCapabilityHelper.fetchRequiredExecutionCapabilities(
          ((RancherK8sInfraDelegateConfig) k8sInfraDelegateConfig).getRancherConnectorDTO(), maskingEvaluator);
    }
    return new ArrayList<>();
  }
}
