/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.k8s;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.CIK8ServicePodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.connector.k8Connector.K8sTaskCapabilityHelper;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.BooleanUtils;

@Data
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CIK8InitializeTaskParams
    extends ConnectorTaskParams implements CIInitializeTaskParams, ExecutionCapabilityDemander {
  @NotNull private ConnectorDetails k8sConnector;
  @Expression(ALLOW_SECRETS) @NotNull private CIK8PodParams<CIK8ContainerParams> cik8PodParams;
  @Expression(ALLOW_SECRETS) @NotNull private List<CIK8ServicePodParams> servicePodParams;
  @NotNull
  private int podMaxWaitUntilReadySecs; // Max time for pod to reach running state after its creation in seconds
  @Builder.Default private static final Type type = Type.GCP_K8;
  private Boolean useSocketCapability; // using a boxed boolean for forward compatibility and null value handling

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    KubernetesClusterConfigDTO kubernetesClusterConfigDTO =
        (KubernetesClusterConfigDTO) k8sConnector.getConnectorConfig();
    return K8sTaskCapabilityHelper.fetchRequiredExecutionCapabilities(
        kubernetesClusterConfigDTO, maskingEvaluator, BooleanUtils.toBoolean(useSocketCapability));
  }
}
