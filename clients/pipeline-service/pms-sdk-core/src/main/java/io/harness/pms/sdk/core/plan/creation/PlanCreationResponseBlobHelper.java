/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.sdk.core.plan.creation.beans.MergePlanCreationResponse;
import io.harness.pms.sdk.core.plan.creation.mappers.PlanNodeProtoMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@OwnedBy(PIPELINE)
@Singleton
public class PlanCreationResponseBlobHelper {
  @Inject PlanNodeProtoMapper planNodeProtoMapper;

  public PlanCreationBlobResponse toBlobResponse(MergePlanCreationResponse planCreationResponse) {
    PlanCreationBlobResponse.Builder finalBlobResponseBuilder = PlanCreationBlobResponse.newBuilder();
    if (EmptyPredicate.isNotEmpty(planCreationResponse.getNodes())) {
      Map<String, PlanNodeProto> newNodes = new HashMap<>();
      planCreationResponse.getNodes().forEach(
          (k, v) -> newNodes.put(k, planNodeProtoMapper.toPlanNodeProtoWithDecoratedFields(v)));
      finalBlobResponseBuilder.putAllNodes(newNodes);
    }
    if (planCreationResponse.getDependencies() != null) {
      finalBlobResponseBuilder.setDeps(planCreationResponse.getDependencies());
    }
    if (EmptyPredicate.isNotEmpty(planCreationResponse.getStartingNodeId())) {
      finalBlobResponseBuilder.setStartingNodeId(planCreationResponse.getStartingNodeId());
    }
    if (EmptyPredicate.isNotEmpty(planCreationResponse.getContextMap())) {
      for (Map.Entry<String, PlanCreationContextValue> dependency : planCreationResponse.getContextMap().entrySet()) {
        finalBlobResponseBuilder.putContext(dependency.getKey(), dependency.getValue());
      }
    }
    if (planCreationResponse.getGraphLayoutResponse() != null) {
      finalBlobResponseBuilder.setGraphLayoutInfo(planCreationResponse.getGraphLayoutResponse().getLayoutNodeInfo());
    }
    if (planCreationResponse.getYamlUpdates() != null) {
      finalBlobResponseBuilder.setYamlUpdates(planCreationResponse.getYamlUpdates());
    }
    if (planCreationResponse.getPreservedNodesInRollbackMode() != null) {
      finalBlobResponseBuilder.addAllPreservedNodesInRollbackMode(
          planCreationResponse.getPreservedNodesInRollbackMode());
    }
    if (planCreationResponse.getServiceAffinityMap() != null) {
      finalBlobResponseBuilder.putAllServiceAffinity(planCreationResponse.getServiceAffinityMap());
    }
    return finalBlobResponseBuilder.build();
  }
}
