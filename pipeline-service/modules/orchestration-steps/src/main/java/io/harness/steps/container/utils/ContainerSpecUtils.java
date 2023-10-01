/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.plugin.ContainerStepSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class ContainerSpecUtils {
  public static final String CONNECTOR_ORIGIN = "connector";

  @NotNull
  public List<TaskSelector> getStepDelegateSelectors(ContainerStepSpec containerStepSpec) {
    if (containerStepSpec != null) {
      ParameterField<List<TaskSelectorYaml>> selectors = containerStepSpec.fetchDelegateSelectors();
      if (ParameterField.isNotNull(selectors)) {
        if (!selectors.isExpression()) {
          return TaskSelectorYaml.toTaskSelector(selectors.getValue());
        }
        log.error("Delegate selector expression {} could not be resolved", selectors.getExpressionValue());
      }
    }
    return List.of();
  }

  @NotNull
  private List<TaskSelector> getConnectorDelegateSelectors(ConnectorDetails k8sConnector) {
    if (k8sConnector != null && k8sConnector.getConnectorConfig() instanceof KubernetesClusterConfigDTO) {
      Set<String> delegateSelectorSet =
          ((KubernetesClusterConfigDTO) k8sConnector.getConnectorConfig()).getDelegateSelectors();
      if (delegateSelectorSet != null) {
        return delegateSelectorSet.stream()
            .map(selector -> TaskSelector.newBuilder().setSelector(selector).setOrigin(CONNECTOR_ORIGIN).build())
            .collect(Collectors.toList());
      }
    }
    return List.of();
  }

  @NotNull
  public List<TaskSelector> mergeStepAndConnectorOriginDelegateSelectors(
      ContainerStepSpec containerStepInfo, ConnectorDetails k8sConnector) {
    List<TaskSelector> stepSelector = getStepDelegateSelectors(containerStepInfo);
    List<TaskSelector> connectorSelector = getConnectorDelegateSelectors(k8sConnector);
    List<TaskSelector> finalSelectors = new ArrayList<>();
    finalSelectors.addAll(connectorSelector);
    finalSelectors.addAll(stepSelector);
    return finalSelectors;
  }
}
