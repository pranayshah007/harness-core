/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import io.harness.cdng.creator.plan.stage.DeploymentStageNode;
import io.harness.cdng.visitor.YamlTypes;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.pms.sdk.core.variables.AbstractStageVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DeploymentStageVariableCreator extends AbstractStageVariableCreator<DeploymentStageNode> {
  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config) {
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();
    YamlField serviceField =
        config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YamlTypes.SERVICE_CONFIG);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(serviceField)) {
      VariableCreationResponse serviceVariableResponse = ServiceVariableCreator.createVariableResponse(serviceField);
      responseMap.put(serviceField.getNode().getUuid(), serviceVariableResponse);
    }

    YamlField infraField =
        config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YamlTypes.PIPELINE_INFRASTRUCTURE);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(infraField)) {
      VariableCreationResponse infraVariableResponse = InfraVariableCreator.createVariableResponse(infraField);
      responseMap.put(infraField.getNode().getUuid(), infraVariableResponse);
    }

    YamlField executionField =
        config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.EXECUTION);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(executionField)) {
      Map<String, YamlField> executionDependencyMap = new HashMap<>();
      executionDependencyMap.put(executionField.getNode().getUuid(), executionField);
      responseMap.put(executionField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(executionDependencyMap))
              .build());
    }

    return responseMap;
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, DeploymentStageNode config) {
    YamlField currentField = ctx.getCurrentField();
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();

    // add dependencies for provision node
    YamlField infraField = currentField.getNode()
                               .getField(YAMLFieldNameConstants.SPEC)
                               .getNode()
                               .getField(YamlTypes.PIPELINE_INFRASTRUCTURE);

    if (VariableCreatorHelper.isNotYamlFieldEmpty(infraField)) {
      Map<String, YamlField> infraDependencyMap = new LinkedHashMap<>();
      YamlField infraDefNode = infraField.getNode().getField(YamlTypes.INFRASTRUCTURE_DEF);
      if (VariableCreatorHelper.isNotYamlFieldEmpty(infraDefNode)
          && VariableCreatorHelper.isNotYamlFieldEmpty(infraDefNode.getNode().getField(YamlTypes.SPEC))) {
        YamlField provisionerField = infraDefNode.getNode().getField(YAMLFieldNameConstants.PROVISIONER);
        if (provisionerField != null) {
          infraDependencyMap.putAll(InfraVariableCreator.addDependencyForProvisionerSteps(provisionerField));
        }
      }
      responseMap.put(infraField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(infraDependencyMap))
              .build());
    }

    // add dependencies for execution node
    YamlField executionField = currentField.getNode()
                                   .getField(YAMLFieldNameConstants.SPEC)
                                   .getNode()
                                   .getField(YAMLFieldNameConstants.EXECUTION);
    if (VariableCreatorHelper.isNotYamlFieldEmpty(executionField)) {
      Map<String, YamlField> executionDependencyMap = new HashMap<>();
      executionDependencyMap.put(executionField.getNode().getUuid(), executionField);
      responseMap.put(executionField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(executionDependencyMap))
              .build());
    }
    return responseMap;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.DEPLOYMENT_STAGE));
  }

  @Override
  public Class<DeploymentStageNode> getFieldClass() {
    return DeploymentStageNode.class;
  }
}
