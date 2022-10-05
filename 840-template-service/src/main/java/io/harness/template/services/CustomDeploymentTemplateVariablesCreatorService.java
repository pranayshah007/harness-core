/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.template.services;

import io.harness.customDeployment.remote.CustomDeploymentResourceClient;
import io.harness.ng.core.customDeployment.CustomDeploymentVariableResponseDTO;
import io.harness.ng.core.customDeployment.CustomDeploymentYamlRequestDTO;
import io.harness.ng.core.template.TemplateEntityType;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.remote.client.NGRestUtils;
import io.harness.template.helpers.CustomDeploymentVariablesUtils;

import com.google.inject.Inject;

public class CustomDeploymentTemplateVariablesCreatorService implements TemplateVariableCreatorService {
  @Inject CustomDeploymentResourceClient customDeploymentResourceClient;

  @Override
  public boolean supportsVariables() {
    return true;
  }

  @Override
  public VariableMergeServiceResponse getVariables(
      String accountId, String orgId, String projectId, String entityYaml, TemplateEntityType templateEntityType) {
    CustomDeploymentYamlRequestDTO requestDTO = CustomDeploymentYamlRequestDTO.builder().entityYaml(entityYaml).build();
    CustomDeploymentVariableResponseDTO customDeploymentVariableResponseDTO =
        NGRestUtils.getResponse(customDeploymentResourceClient.getExpressionVariables(requestDTO));
    return CustomDeploymentVariablesUtils.getVariablesFromResponse(customDeploymentVariableResponseDTO);
  }
}
