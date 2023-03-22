/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.GraphLayoutInfo;
import io.harness.pms.contracts.plan.GraphLayoutNode;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.merger.helpers.MergeHelper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

// Todo: Refactor this class. Readability is very bad for this class.
@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class PlanCreationBlobResponseUtils {
  public void merge(PlanCreationBlobResponse.Builder builder, PlanCreationBlobResponse other) {
    if (other == null) {
      return;
    }
    addNodes(builder, other.getNodesMap());
    addDependenciesV2(builder, other);
    mergeStartingNodeId(builder, other.getStartingNodeId());
    mergeContext(builder, other.getContextMap());
    mergeLayoutNodeInfo(builder, other);
    addYamlUpdates(builder, other);
    mergePreservedNodesInRollbackMode(builder, other);
  }

  /**
   * builder: the object into which the new nodes to be preserved in rollback mode will be added
   * other: the object from which the new nodes to be preserved in rollback mode will be added
   */
  public void mergePreservedNodesInRollbackMode(
      PlanCreationBlobResponse.Builder builder, PlanCreationBlobResponse other) {
    if (EmptyPredicate.isEmpty(other.getPreservedNodesInRollbackModeList())) {
      return;
    }
    builder.addAllPreservedNodesInRollbackMode(other.getPreservedNodesInRollbackModeList());
  }

  public PlanCreationBlobResponse addYamlUpdates(
      PlanCreationBlobResponse.Builder builder, PlanCreationBlobResponse currResponse) {
    if (EmptyPredicate.isEmpty(currResponse.getYamlUpdates().getFqnToYamlMap())) {
      return builder.build();
    }
    Map<String, String> yamlUpdateFqnMap = new LinkedHashMap<>(builder.getYamlUpdates().getFqnToYamlMap());
    yamlUpdateFqnMap.putAll(currResponse.getYamlUpdates().getFqnToYamlMap());
    builder.setYamlUpdates(YamlUpdates.newBuilder().putAllFqnToYaml(yamlUpdateFqnMap).build());
    return builder.build();
  }

  public PlanCreationBlobResponse mergeContext(
      PlanCreationBlobResponse.Builder builder, Map<String, PlanCreationContextValue> contextValueMap) {
    if (EmptyPredicate.isEmpty(contextValueMap)) {
      return builder.build();
    }
    builder.putAllContext(contextValueMap);
    return builder.build();
  }

  public PlanCreationBlobResponse addNodes(
      PlanCreationBlobResponse.Builder builder, Map<String, PlanNodeProto> newNodes) {
    if (EmptyPredicate.isEmpty(newNodes)) {
      return builder.build();
    }
    newNodes.values().forEach(newNode -> addNode(builder, newNode));
    return builder.build();
  }

  public PlanCreationBlobResponse addNode(PlanCreationBlobResponse.Builder builder, PlanNodeProto newNode) {
    // TODO: Add logic to update only if newNode has a more recent version.
    builder.putNodes(newNode.getUuid(), newNode);
    removeDependency(builder, newNode.getUuid());
    return builder.build();
  }

  /**
   * Performs the following operations:
   * - Merges the dependencies we get from the currentResponse to the finalResponse
   * - Updates the yaml for the finalResponse based on the yamlUpdates we get from currResponse so from next iteration,
   * updated yaml will be used.
   */
  public PlanCreationBlobResponse addDependenciesV2(
      PlanCreationBlobResponse.Builder builder, PlanCreationBlobResponse currResponse) {
    Dependencies dependencies = currResponse.getDeps();
    if (EmptyPredicate.isEmpty(dependencies.getDependenciesMap())) {
      return builder.build();
    }
    dependencies.getDependenciesMap().forEach((key, value) -> addDependency(builder, key, value));
    if (dependencies.getDependencyMetadataMap() != null) {
      dependencies.getDependencyMetadataMap().forEach((key, value) -> addDependencyMetadata(builder, key, value));
    }

    if (builder.getDeps() == null || EmptyPredicate.isEmpty(builder.getDeps().getYaml())) {
      builder.setDeps(builder.getDeps().toBuilder().setYaml(dependencies.getYaml()).build());
    } else {
      String updatedPipelineJson =
          mergeYamlUpdates(builder.getDeps().getYaml(), currResponse.getYamlUpdates().getFqnToYamlMap());
      builder.setDeps(builder.getDeps().toBuilder().setYaml(updatedPipelineJson).build());
    }
    return builder.build();
  }

  private static PlanCreationBlobResponse addDependencyMetadata(
      PlanCreationBlobResponse.Builder builder, String nodeId, Dependency dependency) {
    if (builder.containsNodes(nodeId)) {
      return builder.build();
    }

    builder.setDeps(builder.getDeps().toBuilder().putDependencyMetadata(nodeId, dependency).build());
    return builder.build();
  }

  public PlanCreationBlobResponse addDependency(PlanCreationBlobResponse.Builder builder, String nodeId, String path) {
    if (builder.containsNodes(nodeId)) {
      return builder.build();
    }

    builder.setDeps(builder.getDeps().toBuilder().putDependencies(nodeId, path).build());
    return builder.build();
  }

  public PlanCreationBlobResponse removeDependency(PlanCreationBlobResponse.Builder builder, String nodeId) {
    builder.setDeps(builder.getDeps().toBuilder().removeDependencies(nodeId).removeDependencyMetadata(nodeId).build());
    return builder.build();
  }

  public void mergeStartingNodeId(PlanCreationBlobResponse.Builder builder, String otherStartingNodeId) {
    if (EmptyPredicate.isEmpty(otherStartingNodeId)) {
      return;
    }
    if (EmptyPredicate.isEmpty(builder.getStartingNodeId())) {
      builder.setStartingNodeId(otherStartingNodeId);
      return;
    }
    if (!builder.getStartingNodeId().equals(otherStartingNodeId)) {
      throw new InvalidRequestException(String.format(
          "Received different set of starting nodes: %s and %s", builder.getStartingNodeId(), otherStartingNodeId));
    }
  }

  public void mergeLayoutNodeInfo(PlanCreationBlobResponse.Builder builder, PlanCreationBlobResponse response) {
    if (response.getGraphLayoutInfo() != null) {
      String otherStartingNodeId = response.getGraphLayoutInfo().getStartingNodeId();
      GraphLayoutInfo.Builder layoutNodeInfo = GraphLayoutInfo.newBuilder();
      if (EmptyPredicate.isEmpty(builder.getGraphLayoutInfo().getStartingNodeId())) {
        layoutNodeInfo.setStartingNodeId(otherStartingNodeId);
      } else {
        layoutNodeInfo.setStartingNodeId(builder.getGraphLayoutInfo().getStartingNodeId());
      }
      Map<String, GraphLayoutNode> layoutMap = new HashMap<>();
      if (builder.getGraphLayoutInfo() != null) {
        layoutMap = builder.getGraphLayoutInfo().getLayoutNodesMap();
      }
      if (!(layoutMap instanceof HashMap)) {
        layoutMap = new HashMap<>(layoutMap);
      }
      if (response.getGraphLayoutInfo().getLayoutNodesMap() != null) {
        layoutMap.putAll(response.getGraphLayoutInfo().getLayoutNodesMap());
      }

      layoutNodeInfo.putAllLayoutNodes(layoutMap);
      builder.setGraphLayoutInfo(layoutNodeInfo);
    }
  }

  /**
   * Takes the current pipeline yaml and updates the yaml based on the fqn to yaml snippet passed in as param
   */
  public String mergeYamlUpdates(String pipelineJson, Map<String, String> fqnToJsonMap) {
    return MergeHelper.mergeUpdatesIntoJson(pipelineJson, fqnToJsonMap);
  }
}
