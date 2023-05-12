/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.pipeline.creators;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.YamlFieldBlob;
import io.harness.pms.sdk.PmsSdkModuleUtils;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public abstract class BaseCreatorService<R extends CreatorResponse, M, N> {
  @Inject @Named(PmsSdkModuleUtils.SDK_SERVICE_NAME) String currentServiceName;

  public Map<String, YamlField> getInitialDependencies(Map<String, YamlFieldBlob> dependencyBlobs) {
    Map<String, YamlField> initialDependencies = new HashMap<>();

    if (isNotEmpty(dependencyBlobs)) {
      try {
        for (Map.Entry<String, YamlFieldBlob> entry : dependencyBlobs.entrySet()) {
          initialDependencies.put(entry.getKey(), YamlField.fromFieldBlob(entry.getValue()));
        }
      } catch (Exception e) {
        throw new InvalidRequestException("Invalid YAML found in dependency blobs");
      }
    }

    return initialDependencies;
  }

  public R processNodesRecursively(Dependencies initialDependencies, M metadata, R finalResponse, N request) {
    if (isEmpty(initialDependencies.getDependenciesMap())) {
      return finalResponse;
    }

    Dependencies.Builder dependencies = Dependencies.newBuilder()
                                            .setYaml(initialDependencies.getYaml())
                                            .putAllDependencies(initialDependencies.getDependenciesMap());
    while (!dependencies.getDependenciesMap().isEmpty()) {
      processNodes(dependencies, finalResponse, metadata, request);
      for (Map.Entry<String, String> entry : initialDependencies.getDependenciesMap().entrySet()) {
        dependencies.removeDependencies(entry.getKey());
      }
    }

    if (EmptyPredicate.isNotEmpty(finalResponse.getDependencies().getDependenciesMap())) {
      for (Map.Entry<String, String> entry : initialDependencies.getDependenciesMap().entrySet()) {
        finalResponse.getDependencies().toBuilder().removeDependencies(entry.getKey()).build();
      }
    }

    return finalResponse;
  }

  private void processNodes(Dependencies.Builder dependencies, R finalResponse, M metadata, N request) {
    Map<String, String> currentDependenciesMap = new HashMap<>(dependencies.getDependenciesMap());
    String currentYaml = dependencies.getYaml();
    dependencies.clearDependencies();
    dependencies.clearDependencyMetadata();

    YamlField fullYamlField;
    try {
      fullYamlField = YamlUtils.readTree(currentYaml);
    } catch (IOException ex) {
      String message = "Invalid yaml during plan creation";
      log.error(message, ex);
      throw new InvalidRequestException(message);
    }

    for (Map.Entry<String, String> dependencyEntry : currentDependenciesMap.entrySet()) {
      String yamlPath = dependencyEntry.getValue();
      YamlField yamlField = null;
      try {
        yamlField = fullYamlField.fromYamlPath(yamlPath);
      } catch (IOException e) {
        log.error("Invalid yaml field", e);
      }
      R response = processNodeInternal(metadata, yamlField, request);

      if (response == null) {
        // do not add template yaml fields as dependency.
        if (yamlField.getNode().getTemplate() == null) {
          finalResponse.addDependency(currentYaml, yamlField.getNode().getUuid(), yamlPath);
        }
        continue;
      }
      mergeResponses(finalResponse, response, dependencies);
      finalResponse.addResolvedDependency(currentYaml, yamlField.getNode().getUuid(), yamlPath);
      // (TODO: archit) onboard service affinity to filter/variable creators
      // PlanCreatorServiceHelper.decorateCreationResponseWithServiceAffinity(response, currentServiceName, yamlField,
      // "");
      if (isNotEmpty(response.getDependencies().getDependenciesMap())) {
        dependencies.putAllDependencies(response.getDependencies().getDependenciesMap());
        if (EmptyPredicate.isNotEmpty(response.getDependencies().getDependencyMetadataMap())) {
          dependencies.putAllDependencyMetadata(response.getDependencies().getDependencyMetadataMap());
        }
      }
    }
  }

  public abstract R processNodeInternal(M metadata, YamlField yamlField, N request);

  public abstract void mergeResponses(R finalResponse, R response, Dependencies.Builder dependencies);
}
