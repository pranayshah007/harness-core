/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cf.pipeline;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.PipelineServiceFilter;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.sdk.core.pipeline.filters.FilterJsonCreator;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.steps.StepSpecTypeConstants;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@OwnedBy(HarnessTeam.CF)
public class FeatureFlagStageFilterJsonCreator implements FilterJsonCreator<StageElementConfig> {
  public static final String FEATURE_FLAG_SUPPORTED_TYPE = "FeatureFlag";

  @Override
  public Class<StageElementConfig> getFieldClass() {
    return StageElementConfig.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap("stage", Collections.singleton(FEATURE_FLAG_SUPPORTED_TYPE));
  }

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, StageElementConfig yamlField) {
    YamlField stageField = filterCreationContext.getCurrentField();
    int count = 0;
    YamlField stepsField = stageField.getNode()
                               .getField(YAMLFieldNameConstants.SPEC)
                               .getNode()
                               .getField(YAMLFieldNameConstants.EXECUTION)
                               .getNode()
                               .getField(YAMLFieldNameConstants.STEPS);
    for (YamlNode node : stepsField.getNode().asArray()) {
      YamlField stepField = node.getField("step");
      if (stepField != null) {
        if (Objects.equals(stepField.getNode().getType(), StepSpecTypeConstants.FLAG_CONFIGURATION)) {
          count++;
        }
      }
    }
    return FilterCreationResponse.builder()
        .pipelineFilter(PipelineServiceFilter.builder().featureFlagStepCount(count).build())
        .build();
  }
}
