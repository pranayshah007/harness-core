/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.filters;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.PipelineServiceFilter;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.sdk.core.pipeline.filters.ChildrenFilterJsonCreator;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.PIPELINE)
public class ParallelFilterJsonCreator extends ChildrenFilterJsonCreator<YamlField> {
  @Override
  public Map<String, YamlField> getDependencies(FilterCreationContext filterCreationContext) {
    List<YamlField> dependencyNodeIdsList =
        PlanCreatorUtils.getDependencyNodeIdsForParallelNode(filterCreationContext.getCurrentField());
    return dependencyNodeIdsList.stream().collect(
        Collectors.toMap(yamlField -> yamlField.getNode().getUuid(), yamlField -> yamlField));
  }

  @Override
  public PipelineFilter getFilterForGivenField(FilterCreationContext filterCreationContext) {
    return PipelineServiceFilter.builder()
        .stageTypes(StagesFilterJsonCreator.getStageYamlFields(filterCreationContext.getCurrentField())
                        .stream()
                        .map(YamlField::getNode)
                        .map(YamlNode::getType)
                        .collect(Collectors.toSet()))
        .build();
  }

  @Override
  public int getStageCount(FilterCreationContext filterCreationContext, Collection<YamlField> children) {
    return children.size();
  }

  @Override
  public Class<YamlField> getFieldClass() {
    return YamlField.class;
  }

  @Override
  public Map<String, Set<String>> getSupportedTypes() {
    return Collections.singletonMap("parallel", Collections.singleton(PlanCreatorUtils.ANY_TYPE));
  }
}
