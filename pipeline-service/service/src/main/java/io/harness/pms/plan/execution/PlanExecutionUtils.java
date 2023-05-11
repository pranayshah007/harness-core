/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.plan.Plan.PlanBuilder;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dto.LevelDTO;
import io.harness.plan.Plan;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.yaml.YamlUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionUtils {
  public Plan extractPlan(PlanCreationBlobResponse planCreationBlobResponse) {
    PlanBuilder planBuilder = Plan.builder();
    return buildPlan(planCreationBlobResponse, planBuilder);
  }

  public Plan extractPlan(String planNodeUuid, PlanCreationBlobResponse planCreationBlobResponse) {
    PlanBuilder planBuilder = Plan.builder().uuid(planNodeUuid);
    return buildPlan(planCreationBlobResponse, planBuilder);
  }

  private Plan buildPlan(PlanCreationBlobResponse planCreationBlobResponse, PlanBuilder planBuilder) {
    Collection<PlanNodeProto> planNodeProtoList = planCreationBlobResponse.getNodesMap().values();
    for (PlanNodeProto planNodeProto : planNodeProtoList) {
      planBuilder.planNode(PlanNode.fromPlanNodeProto(planNodeProto));
    }
    if (isNotEmpty(planCreationBlobResponse.getStartingNodeId())) {
      planBuilder.startingNodeId(planCreationBlobResponse.getStartingNodeId());
    }
    if (planCreationBlobResponse.hasGraphLayoutInfo()) {
      planBuilder.graphLayoutInfo(planCreationBlobResponse.getGraphLayoutInfo());
    }
    planBuilder.preservedNodesInRollbackMode(planCreationBlobResponse.getPreservedNodesInRollbackModeList());

    return planBuilder.build();
  }

  public String getFQNUsingLevelDTOs(List<LevelDTO> levels) {
    List<String> fqnList = new ArrayList<>();
    for (LevelDTO level : levels) {
      if (YamlUtils.shouldIncludeInQualifiedName(
              level.getIdentifier(), level.getSetupId(), level.isSkipExpressionChain())) {
        fqnList.add(level.getIdentifier());
      }
    }
    return String.join(".", fqnList);
  }
}
