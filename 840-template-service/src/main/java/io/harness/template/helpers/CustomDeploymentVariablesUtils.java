/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.helpers;

import io.harness.data.structure.EmptyPredicate;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableResponseDTO;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.pms.variables.VariableMergeServiceResponse.VariableResponseMapValue;

import java.util.Map;
import java.util.stream.Collectors;

public class CustomDeploymentVariablesUtils {
  public static VariableMergeServiceResponse getVariablesFromResponse(
      CustomDeploymentVariableResponseDTO customDeploymentVariableResponseDTO) {
    Map<String, VariableResponseMapValue> metadataMap =
        customDeploymentVariableResponseDTO.getMetadataMap().entrySet().stream().collect(
            Collectors.toMap(Map.Entry::getKey,
                entry
                -> VariableMergeServiceResponse.VariableResponseMapValue.builder()
                       .yamlProperties(YamlProperties.newBuilder()
                                           .setFqn(entry.getValue().getFqn())
                                           .setVariableName(entry.getValue().getVariableName())
                                           .setLocalName(EmptyPredicate.isNotEmpty(entry.getValue().getLocalName())
                                                   ? entry.getValue().getLocalName()
                                                   : "")
                                           .setAliasFQN(EmptyPredicate.isNotEmpty(entry.getValue().getAliasFqn())
                                                   ? entry.getValue().getAliasFqn()
                                                   : "")
                                           .setVisible(entry.getValue().getVisible())
                                           .build())
                       .build()));
    return VariableMergeServiceResponse.builder()
        .yaml(customDeploymentVariableResponseDTO.getYaml())
        .metadataMap(metadataMap)
        .build();
  }
}
