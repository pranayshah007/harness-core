/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.plancreator.steps;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.execution.StepsExecutionConfig;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.creators.ChildrenPlanCreator;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(PIPELINE)
public abstract class GenericStepsNodePlanCreator extends ChildrenPlanCreator<StepsExecutionConfig> {
  @Override
  public LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, StepsExecutionConfig config) {
    LinkedHashMap<String, PlanCreationResponse> responseMap = new LinkedHashMap<>();
    List<YamlField> stepYamlFields = ctx.getStepYamlFieldsFromStepsAsCurrentYamlField();
    for (YamlField stepYamlField : stepYamlFields) {
      Map<String, YamlField> stepYamlFieldMap = new HashMap<>();
      stepYamlFieldMap.put(stepYamlField.getNode().getUuid(), stepYamlField);
      responseMap.put(stepYamlField.getNode().getUuid(),
          PlanCreationResponse.builder().dependencies(DependenciesUtils.toDependenciesProto(stepYamlFieldMap)).build());
    }
    return responseMap;
  }

  @Override
  public abstract PlanNode createPlanForParentNode(
      PlanCreationContext ctx, StepsExecutionConfig config, List<String> childrenNodeIds);

  @Override
  public Class<StepsExecutionConfig> getFieldClass() {
    return StepsExecutionConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap(YAMLFieldNameConstants.STEPS, Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }

  @Override
  public Set<String> getSupportedYamlVersions() {
    return Set.of(HarnessYamlVersion.V0);
  }
}