/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plancreator;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STAGE;
import static io.harness.pms.yaml.YAMLFieldNameConstants.STEP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.plancreator.steps.v1.DummyNodeV1;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.sdk.core.variables.v1.ChildrenVariableCreatorV1;
import io.harness.pms.yaml.YamlField;
import io.harness.steps.StepSpecTypeConstantsV1;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.PIPELINE)
public class EmptyVariableCreatorV1 extends ChildrenVariableCreatorV1<DummyNodeV1> {
  public Set<String> getSupportedStepTypes() {
    return Set.of(StepSpecTypeConstantsV1.HTTP, StepSpecTypeConstantsV1.SHELL_SCRIPT);
  }

  public Set<String> getSupportedStageTypes() {
    return new HashSet<>();
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    Set<String> stepTypes = getSupportedStepTypes();
    Set<String> stageTypes = getSupportedStageTypes();
    if (EmptyPredicate.isEmpty(stepTypes) && EmptyPredicate.isNotEmpty(stageTypes)) {
      return Collections.emptyMap();
    }
    return Map.of(STEP, stepTypes, STAGE, stageTypes);
  }

  @Override
  public VariableCreationResponse createVariablesForParentNode(VariableCreationContext ctx, YamlField config) {
    return VariableCreationResponse.builder().build();
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
      VariableCreationContext ctx, YamlField config) {
    LinkedHashMap<String, VariableCreationResponse> responseLinkedHashMap = new LinkedHashMap<>();
    return responseLinkedHashMap;
  }

  @Override
  public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
      VariableCreationContext ctx, DummyNodeV1 config) {
    return createVariablesForChildrenNodes(ctx, ctx.getCurrentField());
  }

  @Override
  public VariableCreationResponse createVariablesForParentNodeV2(VariableCreationContext ctx, DummyNodeV1 config) {
    return VariableCreationResponse.builder().build();
  }
}
