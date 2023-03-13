/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.k8Connector;

import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
public class KubernetesConnectionTaskParams
    extends ConnectorTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  KubernetesClusterConfigDTO kubernetesClusterConfig;
  private List<EncryptedDataDetail> encryptionDetails;
  private boolean useSocketCapability;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return K8sTaskCapabilityHelper.fetchRequiredExecutionCapabilities(
        kubernetesClusterConfig, maskingEvaluator, useSocketCapability);
  }
}
