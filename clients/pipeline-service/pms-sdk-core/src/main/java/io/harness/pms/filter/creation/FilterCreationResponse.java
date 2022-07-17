/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.filter.creation;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.FilterCreationBlobResponse;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.pipeline.filter.PipelineFilter;
import io.harness.pms.sdk.core.pipeline.creators.CreatorResponse;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class FilterCreationResponse implements CreatorResponse {
  PipelineFilter pipelineFilter;
  int stageCount;
  @Default List<EntityDetailProtoDTO> referredEntities = new ArrayList<>();
  @Default List<String> stageNames = new ArrayList<>();
  @Default Dependencies dependencies = Dependencies.newBuilder().build();
  @Default Dependencies resolvedDependencies = Dependencies.newBuilder().build();
  YamlUpdates yamlUpdates;

  public Dependencies getDependencies() {
    return dependencies;
  }

  public void addReferredEntities(List<EntityDetailProtoDTO> refferedEntities) {
    if (EmptyPredicate.isEmpty(refferedEntities)) {
      return;
    }
    if (EmptyPredicate.isEmpty(this.referredEntities)) {
      this.referredEntities = new ArrayList<>();
    }
    this.referredEntities.addAll(refferedEntities);
  }

  public void addStageNames(List<String> stageNames) {
    if (EmptyPredicate.isEmpty(stageNames)) {
      return;
    }
    if (EmptyPredicate.isEmpty(this.stageNames)) {
      this.stageNames = new ArrayList<>();
    }
    this.stageNames.addAll(stageNames);
  }

  public void addResolvedDependency(String yaml, String nodeId, String yamlPath) {
    if (resolvedDependencies == null) {
      resolvedDependencies = Dependencies.newBuilder().setYaml(yaml).build();
    }

    resolvedDependencies = resolvedDependencies.toBuilder().putDependencies(nodeId, yamlPath).build();
    if (dependencies != null) {
      dependencies = dependencies.toBuilder().removeDependencies(nodeId).build();
    }
  }

  public void addDependencies(Dependencies dependencies) {
    if (dependencies == null || EmptyPredicate.isEmpty(dependencies.getDependenciesMap())) {
      return;
    }
    dependencies.getDependenciesMap().forEach((key, value) -> { addDependency(dependencies.getYaml(), key, value); });
  }

  public void addDependency(String yaml, String nodeId, String yamlPath) {
    if (dependencies != null && dependencies.getDependenciesMap().containsKey(nodeId)) {
      return;
    }

    if (dependencies == null) {
      dependencies = Dependencies.newBuilder().setYaml(yaml).putDependencies(nodeId, yamlPath).build();
      return;
    }
    dependencies = dependencies.toBuilder().putDependencies(nodeId, yamlPath).build();
  }

  public FilterCreationBlobResponse toBlobResponse() {
    FilterCreationBlobResponse.Builder finalBlobResponseBuilder = FilterCreationBlobResponse.newBuilder();
    if (pipelineFilter != null) {
      finalBlobResponseBuilder.setFilter(pipelineFilter.toJson());
    }

    if (dependencies != null && isNotEmpty(dependencies.getDependenciesMap())) {
      finalBlobResponseBuilder.setDeps(dependencies);
    }

    if (resolvedDependencies != null && isNotEmpty(resolvedDependencies.getDependenciesMap())) {
      finalBlobResponseBuilder.setResolvedDeps(resolvedDependencies);
    }

    if (isNotEmpty(referredEntities)) {
      finalBlobResponseBuilder.addAllReferredEntities(referredEntities);
    }

    if (isNotEmpty(stageNames)) {
      finalBlobResponseBuilder.addAllStageNames(stageNames);
    }

    finalBlobResponseBuilder.setStageCount(stageCount);
    return finalBlobResponseBuilder.build();
  }
}
