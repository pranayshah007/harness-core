/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.creators;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.sdk.core.plan.creation.beans.GraphLayoutResponse;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@OwnedBy(PIPELINE)
public abstract class ChildrenPlanCreator<T> implements PartialPlanCreator<T> {
  public String getStartingNodeId(T field) {
    return null;
  }

  public abstract LinkedHashMap<String, PlanCreationResponse> createPlanForChildrenNodes(
      PlanCreationContext ctx, T config);

  public abstract PlanNode createPlanForParentNode(PlanCreationContext ctx, T config, List<String> childrenNodeIds);

  public GraphLayoutResponse getLayoutNodeInfo(PlanCreationContext ctx, T config) {
    return GraphLayoutResponse.builder().build();
  }

  @Override
  public PlanCreationResponse createPlanForField(PlanCreationContext ctx, T config) {
    PlanCreationResponse finalResponse = PlanCreationResponse.builder().build();
    String startingNodeId = getStartingNodeId(config);
    if (EmptyPredicate.isNotEmpty(startingNodeId)) {
      finalResponse.setStartingNodeId(startingNodeId);
    }

    LinkedHashMap<String, PlanCreationResponse> childrenResponses = createPlanForChildrenNodes(ctx, config);
    List<String> childrenNodeIds = new LinkedList<>();
    for (Map.Entry<String, PlanCreationResponse> entry : childrenResponses.entrySet()) {
      finalResponse.merge(entry.getValue());
      childrenNodeIds.add(entry.getKey());
    }

    finalResponse.addNode(createPlanForParentNode(ctx, config, childrenNodeIds));
    finalResponse.setGraphLayoutResponse(getLayoutNodeInfo(ctx, config));
    return finalResponse;
  }
}
