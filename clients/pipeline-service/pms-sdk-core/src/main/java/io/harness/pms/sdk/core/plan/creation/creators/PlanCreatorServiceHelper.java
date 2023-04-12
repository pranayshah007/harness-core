/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.creators;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.RollbackModeBehaviour;
import io.harness.pms.plan.creation.PlanCreationBlobResponseUtils;
import io.harness.pms.plan.creation.PlanCreatorUtils;
import io.harness.pms.sdk.core.pipeline.creators.CreatorResponse;
import io.harness.pms.sdk.core.plan.creation.beans.MergePlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PlanCreatorServiceHelper {
  public Optional<PartialPlanCreator<?>> findPlanCreator(
      List<PartialPlanCreator<?>> planCreators, YamlField field, String yamlVersion) {
    return planCreators.stream()
        .filter(planCreator -> {
          Map<String, Set<String>> supportedTypes = planCreator.getSupportedTypes();
          Set<String> supportedVersions = planCreator.getSupportedYamlVersions();
          return supportedVersions.contains(yamlVersion)
              && PlanCreatorUtils.supportsField(supportedTypes, field, yamlVersion);
        })
        .findFirst();
  }

  public Dependencies handlePlanCreationResponses(List<PlanCreationResponse> planCreationResponses,
      MergePlanCreationResponse finalResponse, String currentYaml, Dependencies dependencies,
      List<Map.Entry<String, String>> dependenciesList) {
    String updatedYaml = currentYaml;
    List<String> errorMessages = planCreationResponses.stream()
                                     .filter(resp -> resp != null && EmptyPredicate.isNotEmpty(resp.getErrorMessages()))
                                     .flatMap(resp -> resp.getErrorMessages().stream())
                                     .collect(Collectors.toList());
    if (EmptyPredicate.isNotEmpty(errorMessages)) {
      finalResponse.setErrorMessages(errorMessages);
      return dependencies.toBuilder().clearDependencies().build();
    }

    Map<String, String> newDependencies = new HashMap<>();
    Map<String, Dependency> newMetadataDependency = new HashMap<>();
    for (int i = 0; i < dependenciesList.size(); i++) {
      Map.Entry<String, String> entry = dependenciesList.get(i);
      String fieldYamlPath = entry.getValue();
      PlanCreationResponse response = planCreationResponses.get(i);
      if (response == null) {
        finalResponse.addDependency(currentYaml, entry.getKey(), fieldYamlPath);
        finalResponse.addDependencyMetadata(
            currentYaml, entry.getKey(), dependencies.getDependencyMetadataMap().get(entry.getKey()));
        continue;
      }

      finalResponse.mergeWithoutDependencies(response);

      if (response.getDependencies() != null
          && EmptyPredicate.isNotEmpty(response.getDependencies().getDependenciesMap())) {
        newDependencies.putAll(response.getDependencies().getDependenciesMap());

        if (EmptyPredicate.isNotEmpty(response.getDependencies().getDependencyMetadataMap())) {
          newMetadataDependency.putAll(response.getDependencies().getDependencyMetadataMap());
        }
      }
      if (response.getYamlUpdates() != null && EmptyPredicate.isNotEmpty(response.getYamlUpdates().getFqnToYamlMap())) {
        updatedYaml = PlanCreationBlobResponseUtils.mergeYamlUpdates(
            currentYaml, finalResponse.getYamlUpdates().getFqnToYamlMap());
        finalResponse.updateYamlInDependencies(updatedYaml);
      }
    }
    return dependencies.toBuilder()
        .setYaml(updatedYaml)
        .clearDependencies()
        .putAllDependencies(newDependencies)
        .putAllDependencyMetadata(newMetadataDependency)
        .build();
  }

  public void decorateNodesWithStageFqn(YamlField field, PlanCreationResponse planForField, String yamlVersion) {
    String stageFqn = YamlUtils.getStageFqnPath(field.getNode(), yamlVersion);
    if (!EmptyPredicate.isEmpty(stageFqn)) {
      planForField.getNodes().forEach((k, v) -> v.setStageFqn(stageFqn));

      if (planForField.getPlanNode() != null) {
        planForField.getPlanNode().setStageFqn(stageFqn);
      }
    }
  }

  /**
   * This method copies the ServiceAffinity from parent to children
   * If currentDependency is having a serviceAffinity then its passed to its children else sdk serviceName is added to
   * children dependency.
   *
   * @param creatorResponse
   * @param sdkServiceName
   * @param currentField
   * @param currentNodeServiceAffinity
   */
  public void decorateCreationResponseWithServiceAffinity(CreatorResponse creatorResponse, String sdkServiceName,
      YamlField currentField, String currentNodeServiceAffinity) {
    String serviceAffinity = "";
    if (EmptyPredicate.isNotEmpty(currentNodeServiceAffinity)) {
      serviceAffinity = currentNodeServiceAffinity;
    }
    // Attach ServiceAffinity of current service only if current node is stage
    if (EmptyPredicate.isEmpty(serviceAffinity) && YamlUtils.isStageNode(currentField.getNode())) {
      serviceAffinity = sdkServiceName;
    }

    Dependencies childDependencies = creatorResponse.getDependencies();
    if (childDependencies != null) {
      for (String dependencyKey : childDependencies.getDependenciesMap().keySet()) {
        creatorResponse.addServiceAffinityToResponse(dependencyKey, serviceAffinity);
      }
    }
  }

  public Dependencies removeInitialDependencies(Dependencies dependencies, Dependencies initialDependencies) {
    if (isEmptyDependencies(initialDependencies) || isEmptyDependencies(dependencies)) {
      return dependencies;
    }

    Dependencies.Builder builder = dependencies.toBuilder();
    initialDependencies.getDependenciesMap().keySet().forEach(builder::removeDependencies);
    return builder.build();
  }

  protected boolean isEmptyDependencies(Dependencies dependencies) {
    return dependencies == null || EmptyPredicate.isEmpty(dependencies.getDependenciesMap());
  }

  /**
   * If initialDependencyDetails has rollbackModeBehaviour that is to be propagated, then all the dependencies present
   * in planForField will also be decorated with this rollbackModeBehaviour
   * @param initialDependencyDetails details of the dependency for which the created plan is
   * @param planForField the created plan
   */
  public void decorateResponseWithRollbackModeBehaviour(
      Dependency initialDependencyDetails, PlanCreationResponse planForField) {
    if (initialDependencyDetails == null) {
      return;
    }
    if (!PlanCreatorServiceHelper.isBehaviourToPropagate(initialDependencyDetails.getRollbackModeBehaviour())) {
      return;
    }

    // if rollbackModeBehaviour in initialDependencyDetails is Preserve, add all nodes in planForField to its
    // preservedNodesInRollbackMode list
    checkAndAddNodesToBePreservedInRollbackMode(planForField, initialDependencyDetails.getRollbackModeBehaviour());

    // newDependenciesUuids contains all the uuids which will be registered as dependencies
    Set<String> newDependenciesUuids = planForField.getDependencies().getDependenciesMap().keySet();

    // this map's keys will be a subset of newDependenciesUuids, because not all dependency uuids will have a Dependency
    // instance attached to them
    Map<String, Dependency> decoratedDependencyMetadataMap =
        new HashMap<>(planForField.getDependencies().getDependencyMetadataMap());

    // for every dependency uuid, we will add a Dependency instance which has the rollback mode behaviour from
    // initialDependencyDetails
    for (String newDependencyUuid : newDependenciesUuids) {
      if (decoratedDependencyMetadataMap.containsKey(newDependencyUuid)) {
        decoratedDependencyMetadataMap.put(newDependencyUuid,
            decoratedDependencyMetadataMap.get(newDependencyUuid)
                .toBuilder()
                .setRollbackModeBehaviour(initialDependencyDetails.getRollbackModeBehaviour())
                .build());
      } else {
        decoratedDependencyMetadataMap.put(newDependencyUuid,
            Dependency.newBuilder()
                .setRollbackModeBehaviour(initialDependencyDetails.getRollbackModeBehaviour())
                .build());
      }
    }

    planForField.setDependencies(
        planForField.getDependencies().toBuilder().putAllDependencyMetadata(decoratedDependencyMetadataMap).build());
  }

  void checkAndAddNodesToBePreservedInRollbackMode(
      PlanCreationResponse planForField, RollbackModeBehaviour rollbackModeBehaviour) {
    if (rollbackModeBehaviour != RollbackModeBehaviour.PRESERVE) {
      return;
    }
    List<String> newNodes = new ArrayList<>(planForField.getNodes().keySet());
    newNodes.add(planForField.getPlanNode().getUuid());
    planForField.mergePreservedNodesInRollbackMode(newNodes);
  }

  boolean isBehaviourToPropagate(RollbackModeBehaviour behaviour) {
    return behaviour == RollbackModeBehaviour.PRESERVE;
  }
}
