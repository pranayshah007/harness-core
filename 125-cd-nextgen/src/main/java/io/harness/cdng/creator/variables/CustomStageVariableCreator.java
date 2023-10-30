/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.variables;

import static io.harness.pms.yaml.YAMLFieldNameConstants.CUSTOM;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STRATEGY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.creator.plan.stage.CustomStageNode;
import io.harness.cdng.environment.yaml.EnvironmentYamlV2;
import io.harness.pms.sdk.core.variables.AbstractStageVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT, HarnessModuleComponent.CDS_PIPELINE})
@Slf4j

public class CustomStageVariableCreator extends AbstractStageVariableCreator<CustomStageNode> {
  @Inject private StageVariableCreatorHelper stageVariableCreatorHelper;

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config) {
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();

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

    YamlField strategyField = config.getNode().getField(STRATEGY);

    if (strategyField != null) {
      Map<String, YamlField> strategyDependencyMap = new HashMap<>();
      strategyDependencyMap.put(strategyField.getNode().getUuid(), strategyField);
      responseMap.put(strategyField.getNode().getUuid(),
          VariableCreationResponse.builder()
              .dependencies(DependenciesUtils.toDependenciesProto(strategyDependencyMap))
              .build());
    }
    return responseMap;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.STAGE, Collections.singleton(CUSTOM));
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, CustomStageNode customStageNode) {
    LinkedHashMap<String, VariableCreationResponse> responseMap =
        createVariablesForChildrenNodesPipelineV2Yaml(ctx, customStageNode);

    responseMap.putAll(createVariablesForChildrenNodes(ctx, ctx.getCurrentField()));
    return responseMap;
  }

  private LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesPipelineV2Yaml(
      VariableCreationContext ctx, CustomStageNode config) {
    LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();
    try {
      final EnvironmentYamlV2 environment = config.getCustomStageConfig().getEnvironment();

      if (environment != null) {
        stageVariableCreatorHelper.createVariablesForEnvironment(ctx, responseMap, null, environment);
      }
    } catch (Exception ex) {
      log.error("Exception during Custom Stage Node variable creation", ex);
    }
    return responseMap;
  }

  @Override
  public Class<CustomStageNode> getFieldClass() {
    return CustomStageNode.class;
  }
}
