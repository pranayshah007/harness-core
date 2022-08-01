/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.filters;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.DependenciesUtils;
import io.harness.pms.yaml.YamlField;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.PIPELINE)
public abstract class ChildrenFilterJsonCreator<T> implements FilterJsonCreator<T> {
  public abstract Map<String, YamlField> getDependencies(FilterCreationContext filterCreationContext);
  public abstract PipelineFilter getFilterForGivenField(FilterCreationContext filterCreationContext);

  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, T field) {
    FilterCreationResponse response = FilterCreationResponse.builder().build();
    Map<String, YamlField> yamlFieldsDependencies = getDependencies(filterCreationContext);
    Dependencies dependencies = DependenciesUtils.toDependenciesProto(yamlFieldsDependencies);
    response.addDependencies(dependencies);

    response.setPipelineFilter(getFilterForGivenField(filterCreationContext));
    response.addStageNames(getStageNames(filterCreationContext, yamlFieldsDependencies.values()));
    response.setReferredEntities(getReferredEntities(filterCreationContext, field));
    // Note: Currently we treat that all the dependency fields are children but that might not be true.
    // Todo: Support for dependency not as direct children
    response.setStageCount(getStageCount(filterCreationContext, yamlFieldsDependencies.values()));
    return response;
  }

  public List<String> getStageNames(FilterCreationContext filterCreationContext, Collection<YamlField> children) {
    return new ArrayList<>();
  }

  public List<EntityDetailProtoDTO> getReferredEntities(FilterCreationContext context, T field) {
    return new ArrayList<>();
  }

  public abstract int getStageCount(FilterCreationContext filterCreationContext, Collection<YamlField> children);
}
