/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.pipelinestage;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.sdk.core.variables.AbstractStageVariableCreator;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public class PipelineStageVariableCreator extends AbstractStageVariableCreator<PipelineStageNode> {
  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config) {
    LinkedHashMap<String, VariableCreationResponse> variableResponseMap = new LinkedHashMap<>();
    YamlField specField = config.getNode().getField(YAMLFieldNameConstants.SPEC);
    if (specField != null && specField.getNode().getField(YAMLFieldNameConstants.OUTPUTS) != null) {
      YamlField outputsField = specField.getNode().getField(YAMLFieldNameConstants.OUTPUTS);

      if (outputsField != null && EmptyPredicate.isNotEmpty(outputsField.getNode().asArray())) {
        HashMap<String, YamlField> outputs = new HashMap<>();
        outputs.put(outputsField.getUuid(), outputsField);
        variableResponseMap.put(config.getUuid(),
            VariableCreationResponse.builder().dependencies(DependenciesUtils.toDependenciesProto(outputs)).build());
      }
    }
    return variableResponseMap;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(
        YAMLFieldNameConstants.STAGE, Collections.singleton(StepSpecTypeConstants.PIPELINE_STAGE));
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, PipelineStageNode config) {
    return createVariablesForChildrenNodes(ctx, ctx.getCurrentField());
  }

  @Override
  public Class<PipelineStageNode> getFieldClass() {
    return PipelineStageNode.class;
  }
}
