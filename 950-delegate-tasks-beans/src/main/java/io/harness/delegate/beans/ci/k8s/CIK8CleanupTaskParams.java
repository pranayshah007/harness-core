/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.k8s;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.k8Connector.K8sTaskCapabilityHelper;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.LiteEngineConnectionCapability;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.BooleanUtils;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CIK8CleanupTaskParams implements CICleanupTaskParams, ExecutionCapabilityDemander {
  @NotNull private ConnectorDetails k8sConnector;
  @NotNull private List<String> podNameList; // Currently only single pod deletion is supported for each stage
  @NotNull private List<String> serviceNameList;
  @NotNull private List<String> cleanupContainerNames;
  @NotNull private String LiteEngineIP;
  @NotNull private int LiteEnginePort;
  private Boolean isLocal;
  @Expression(ALLOW_SECRETS) @NotNull private String namespace;
  @Builder.Default private static final CICleanupTaskParams.Type type = Type.GCP_K8;

  private Boolean useSocketCapability; // using a boxed boolean for forward compatibility and null value handling

  @Override
  public Type getType() {
    return type;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    if (isNotEmpty(LiteEngineIP) && podNameList.size() == 1) {
      return Collections.singletonList(LiteEngineConnectionCapability.builder()
                                           .k8sConnectorDetails(k8sConnector)
                                           .namespace(namespace)
                                           .podName(podNameList.get(0))
                                           .ip(LiteEngineIP)
                                           .port(LiteEnginePort)
                                           .isLocal(isLocal)
                                           .build());
    }
    KubernetesClusterConfigDTO kubernetesClusterConfigDTO =
        (KubernetesClusterConfigDTO) k8sConnector.getConnectorConfig();
    return K8sTaskCapabilityHelper.fetchRequiredExecutionCapabilities(
        kubernetesClusterConfigDTO, maskingEvaluator, BooleanUtils.toBoolean(useSocketCapability));
  }
}
