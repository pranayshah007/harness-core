/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.filter.creation;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.FilterCreationBlobResponse;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class FilterCreationBlobResponseUtils {
  public void mergeResponses(
      FilterCreationBlobResponse.Builder builder, FilterCreationResponseWrapper response, Map<String, String> filters) {
    if (response == null || response.getResponse() == null) {
      return;
    }
    mergeResolvedDependencies(builder, response.getResponse());
    mergeDependencies(builder, response.getResponse());
    mergeFilters(response, filters);
    updateStageCount(builder, response.getResponse());
    mergeReferredEntities(builder, response.getResponse());
    mergeStageNames(builder, response.getResponse());
    addYamlUpdates(builder, response.getResponse());
  }

  public void updateStageCount(
      FilterCreationBlobResponse.Builder builder, FilterCreationBlobResponse filterCreationBlobResponse) {
    builder.setStageCount(builder.getStageCount() + filterCreationBlobResponse.getStageCount());
  }

  public void mergeFilters(FilterCreationResponseWrapper response, Map<String, String> filters) {
    if (isNotEmpty(response.getResponse().getFilter())) {
      filters.put(response.getServiceName(), response.getResponse().getFilter());
    }
    if (!filters.containsKey(response.getServiceName())) {
      filters.put(response.getServiceName(), response.getResponse().getFilter());
    }
  }

  public void mergeReferredEntities(FilterCreationBlobResponse.Builder builder, FilterCreationBlobResponse response) {
    if (isNotEmpty(response.getReferredEntitiesList())) {
      builder.addAllReferredEntities(response.getReferredEntitiesList());
    }
  }

  public void mergeStageNames(FilterCreationBlobResponse.Builder builder, FilterCreationBlobResponse response) {
    if (response.getStageNamesList() != null) {
      builder.addAllStageNames(new ArrayList<>(response.getStageNamesList()));
    }
  }

  public void mergeResolvedDependencies(
      FilterCreationBlobResponse.Builder builder, FilterCreationBlobResponse response) {
    if (isNotEmpty(response.getResolvedDeps().getDependenciesMap())) {
      response.getResolvedDeps().getDependenciesMap().forEach((key, value) -> {
        builder.setResolvedDeps(builder.getResolvedDeps().toBuilder().putDependencies(key, value));
        builder.setDeps(builder.getDeps().toBuilder().removeDependencies(key));
      });
    }
  }

  public void mergeDependencies(FilterCreationBlobResponse.Builder builder, FilterCreationBlobResponse response) {
    if (isNotEmpty(response.getDeps().getDependenciesMap())) {
      response.getDeps().getDependenciesMap().forEach((key, value) -> {
        if (!builder.getResolvedDeps().containsDependencies(key)) {
          builder.setDeps(builder.getDeps().toBuilder().putDependencies(key, value));
          Dependency dependency = response.getDeps().getDependencyMetadataMap().get(key);
          if (dependency != null) {
            builder.setDeps(builder.getDeps().toBuilder().putDependencyMetadata(key, dependency));
          }
        }
      });
    }
  }

  public FilterCreationBlobResponse addYamlUpdates(
      FilterCreationBlobResponse.Builder builder, FilterCreationBlobResponse currResponse) {
    if (EmptyPredicate.isEmpty(currResponse.getYamlUpdates().getFqnToYamlMap())) {
      return builder.build();
    }
    Map<String, String> yamlUpdateFqnMap = new LinkedHashMap<>(builder.getYamlUpdates().getFqnToYamlMap());
    yamlUpdateFqnMap.putAll(currResponse.getYamlUpdates().getFqnToYamlMap());
    builder.setYamlUpdates(YamlUpdates.newBuilder().putAllFqnToYaml(yamlUpdateFqnMap).build());
    return builder.build();
  }

  public Dependencies removeTemplateDependencies(Dependencies initialDependencies) {
    String yaml = initialDependencies.getYaml();
    Map<String, String> newDependenciesMap = new HashMap<>();

    if (yaml == null) {
      throw new InvalidRequestException("Yaml should not be null in dependencies");
    }

    YamlField yamlField = null;
    try {
      yamlField = YamlUtils.readTree(yaml);
    } catch (IOException e) {
      log.error("Invalid yaml field", e);
    }
    for (Map.Entry<String, String> entry : initialDependencies.getDependenciesMap().entrySet()) {
      String yamlPath = entry.getValue();
      YamlField yamlFieldAtPath = null;
      try {
        yamlFieldAtPath = yamlField.fromYamlPath(yamlPath);
      } catch (IOException e) {
        log.error("Cannot get yaml field on given yaml path " + yamlPath);
      }
      if (yamlFieldAtPath != null && yamlFieldAtPath.getNode().getTemplate() == null) {
        newDependenciesMap.put(entry.getKey(), yamlPath);
      }
    }
    return initialDependencies.toBuilder().clearDependencies().putAllDependencies(newDependenciesMap).build();
  }
}
