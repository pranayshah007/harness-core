/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.variables.beans;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.VariablesCreationBlobResponse;
import io.harness.pms.contracts.plan.YamlExtraProperties;
import io.harness.pms.contracts.plan.YamlOutputProperties;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.sdk.core.pipeline.creators.CreatorResponse;

import java.util.HashMap;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
@OwnedBy(HarnessTeam.PIPELINE)
public class VariableCreationResponse implements CreatorResponse {
  @Singular Map<String, YamlProperties> yamlProperties;
  // deprecated
  @Singular Map<String, YamlOutputProperties> yamlOutputProperties;
  @Singular Map<String, YamlExtraProperties> yamlExtraProperties;
  @Builder.Default Dependencies dependencies = Dependencies.newBuilder().build();
  @Builder.Default Dependencies resolvedDependencies = Dependencies.newBuilder().build();
  YamlUpdates yamlUpdates;

  public Dependencies getDependencies() {
    return dependencies;
  }

  public void mergeResponses(VariableCreationResponse response) {
    addYamlProperties(response.getYamlProperties());
    addYamlOutputProperties(response.getYamlOutputProperties());
    addYamlExtraProperties(response.getYamlExtraProperties());
    addYamlUpdates(response.getYamlUpdates());
  }

  public void updateYamlInDependencies(String updatedYaml) {
    if (dependencies == null) {
      dependencies = Dependencies.newBuilder().setYaml(updatedYaml).build();
      return;
    }
    dependencies = dependencies.toBuilder().setYaml(updatedYaml).build();
  }

  public void addResolvedDependencies(Dependencies resolvedDependencies) {
    if (resolvedDependencies == null || EmptyPredicate.isEmpty(resolvedDependencies.getDependenciesMap())) {
      return;
    }
    resolvedDependencies.getDependenciesMap().forEach(
        (key, value) -> { addDependency(dependencies.getYaml(), key, value); });
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

  @Override
  public void addAffinityToDependencyMetadata(String dependencyKey, String serviceAffinity) {
    if (!dependencies.getDependencyMetadataMap().containsKey(dependencyKey)) {
      Dependency dependency = Dependency.newBuilder().setServiceAffinity(serviceAffinity).build();
      dependencies = dependencies.toBuilder().putDependencyMetadata(dependencyKey, dependency).build();
    } else {
      Dependency dependency = dependencies.getDependencyMetadataMap().get(dependencyKey);
      Dependency dependencyWithAffinity = dependency.toBuilder().setServiceAffinity(serviceAffinity).build();
      dependencies = dependencies.toBuilder().putDependencyMetadata(dependencyKey, dependencyWithAffinity).build();
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

  public void addYamlProperties(Map<String, YamlProperties> yamlProperties) {
    if (EmptyPredicate.isEmpty(yamlProperties)) {
      return;
    }
    yamlProperties.forEach(this::addYamlProperty);
  }

  private void addYamlProperty(String uuid, YamlProperties yamlProperty) {
    if (yamlProperties != null && yamlProperties.containsKey(uuid)) {
      return;
    }
    if (yamlProperties == null) {
      yamlProperties = new HashMap<>();
    } else if (!(yamlProperties instanceof HashMap)) {
      yamlProperties = new HashMap<>(yamlProperties);
    }
    yamlProperties.put(uuid, yamlProperty);
  }

  public void addYamlOutputProperties(Map<String, YamlOutputProperties> yamlOutputPropertiesMap) {
    if (EmptyPredicate.isEmpty(yamlOutputPropertiesMap)) {
      return;
    }
    yamlOutputPropertiesMap.forEach(this::addYamlOutputProperty);
  }

  private void addYamlOutputProperty(String uuid, YamlOutputProperties yamlOutputPropertyEntry) {
    if (yamlOutputProperties != null && yamlOutputProperties.containsKey(uuid)) {
      return;
    }
    if (this.yamlOutputProperties == null) {
      this.yamlOutputProperties = new HashMap<>();
    } else if (!(this.yamlOutputProperties instanceof HashMap)) {
      this.yamlOutputProperties = new HashMap<>(this.yamlOutputProperties);
    }
    this.yamlOutputProperties.put(uuid, yamlOutputPropertyEntry);
  }

  public void addYamlExtraProperties(Map<String, YamlExtraProperties> yamlExtraPropertiesMap) {
    if (EmptyPredicate.isEmpty(yamlExtraPropertiesMap)) {
      return;
    }
    yamlExtraPropertiesMap.forEach(this::addYamlExtraProperty);
  }

  private void addYamlExtraProperty(String uuid, YamlExtraProperties yamlExtraPropertyEntry) {
    if (this.yamlExtraProperties == null) {
      this.yamlExtraProperties = new HashMap<>();
    } else if (!(this.yamlExtraProperties instanceof HashMap)) {
      this.yamlExtraProperties = new HashMap<>(this.yamlExtraProperties);
    }
    if (yamlExtraProperties.containsKey(uuid)) {
      YamlExtraProperties yamlExtraPropertiesValue = this.yamlExtraProperties.get(uuid);
      YamlExtraProperties.Builder yamlExtraPropertiesBuilder = yamlExtraPropertiesValue.toBuilder();
      yamlExtraPropertiesBuilder.addAllOutputProperties(yamlExtraPropertyEntry.getOutputPropertiesList());
      yamlExtraPropertiesBuilder.addAllProperties(yamlExtraPropertyEntry.getPropertiesList());
      yamlExtraProperties.put(uuid, yamlExtraPropertiesBuilder.build());
    }
    this.yamlExtraProperties.put(uuid, yamlExtraPropertyEntry);
  }

  public void addYamlUpdates(YamlUpdates otherYamlUpdates) {
    if (otherYamlUpdates == null) {
      return;
    }
    if (yamlUpdates == null) {
      yamlUpdates = otherYamlUpdates;
      return;
    }
    yamlUpdates = yamlUpdates.toBuilder().putAllFqnToYaml(otherYamlUpdates.getFqnToYamlMap()).build();
  }

  public VariablesCreationBlobResponse toBlobResponse() {
    VariablesCreationBlobResponse.Builder finalBuilder = VariablesCreationBlobResponse.newBuilder();

    if (dependencies != null && isNotEmpty(dependencies.getDependenciesMap())) {
      finalBuilder.setDeps(dependencies);
    }

    if (resolvedDependencies != null && isNotEmpty(resolvedDependencies.getDependenciesMap())) {
      finalBuilder.setResolvedDeps(resolvedDependencies);
    }

    if (isNotEmpty(yamlProperties)) {
      for (Map.Entry<String, YamlProperties> yamlPropertiesEntry : yamlProperties.entrySet()) {
        finalBuilder.putYamlProperties(yamlPropertiesEntry.getKey(), yamlPropertiesEntry.getValue());
      }
    }
    if (isNotEmpty(yamlOutputProperties)) {
      for (Map.Entry<String, YamlOutputProperties> outputPropertiesEntry : yamlOutputProperties.entrySet()) {
        finalBuilder.putYamlOutputProperties(outputPropertiesEntry.getKey(), outputPropertiesEntry.getValue());
      }
    }
    if (isNotEmpty(yamlExtraProperties)) {
      finalBuilder.putAllYamlExtraProperties(yamlExtraProperties);
    }
    if (yamlUpdates != null) {
      finalBuilder.setYamlUpdates(yamlUpdates);
    }

    return finalBuilder.build();
  }
}
