package io.harness.pms.sdk.core.variables.beans;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.plan.*;
import io.harness.pms.sdk.core.pipeline.creators.CreatorResponse;
import io.harness.pms.yaml.YamlField;

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
  @Singular Map<String, YamlOutputProperties> yamlOutputProperties;
  @Singular Map<String, YamlField> resolvedDependencies;
  Dependencies dependencies;
  YamlUpdates yamlUpdates;

  public void addDependencies(Dependencies dependencies) {
    if (dependencies == null && EmptyPredicate.isEmpty(dependencies.getDependenciesMap())) {
      return;
    }
    dependencies.getDependenciesMap().forEach((key, value) -> addDependency(dependencies.getYaml(), key, value));
  }

  public void addDependency(String yaml, String nodeId, String yamlPath) {
    if ((dependencies != null && dependencies.getDependenciesMap().containsKey(nodeId))) {
      return;
    }

    if (dependencies == null) {
      dependencies = Dependencies.newBuilder().setYaml(yaml).putDependencies(nodeId, yamlPath).build();
      return;
    }

    dependencies = dependencies.toBuilder().putDependencies(nodeId, yamlPath).build();
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

  public VariablesCreationBlobResponse toBlobResponse() {
    VariablesCreationBlobResponse.Builder finalBuilder = VariablesCreationBlobResponse.newBuilder();

    if (isNotEmpty(dependencies.getDependenciesMap())) {
      for (Map.Entry<String, String> dependency : dependencies.getDependenciesMap().entrySet()) {
        finalBuilder.getDeps().toBuilder().putDependencies(dependency.getKey(), dependency.getValue());
      }
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
    return finalBuilder.build();
  }
}
