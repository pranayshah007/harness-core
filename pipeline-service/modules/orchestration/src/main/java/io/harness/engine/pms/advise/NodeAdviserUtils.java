/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.engine.pms.advise;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.advisers.CommonAdviserTypes;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.InterruptEffect;
import io.harness.plan.Node;
import io.harness.pms.contracts.advisers.AdviseEvent;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.sdk.core.adviser.OrchestrationAdviserTypes;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.EnumUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class NodeAdviserUtils {
  public AdviseEvent createAdviseEvent(
      NodeExecution nodeExecution, FailureInfo failureInfo, Node planNode, Status fromStatus) {
    AdviseEvent.Builder builder =
        AdviseEvent.newBuilder()
            .setAmbiance(nodeExecution.getAmbiance())
            .setFailureInfo(failureInfo)
            .addAllAdviserObtainments(planNode.getAdviserObtainments())
            .setIsPreviousAdviserExpired(isPreviousAdviserExpired(nodeExecution.getInterruptHistories()))
            .addAllRetryIds(nodeExecution.getRetryIds())
            .setToStatus(nodeExecution.getStatus())
            .setFromStatus(fromStatus);

    if (!EmptyPredicate.isEmpty(nodeExecution.getNotifyId())) {
      builder.setNotifyId(nodeExecution.getNotifyId());
    }
    return builder.build();
  }

  public boolean hasCustomAdviser(Node planNode) {
    boolean result = false;
    List<AdviserObtainment> adviserObtainmentList = planNode.getAdviserObtainments();
    if (isEmpty(adviserObtainmentList)) {
      return true;
    }
    for (AdviserObtainment adviserObtainment : adviserObtainmentList) {
      String type = adviserObtainment.getType().getType();
      if (!EnumUtils.isValidEnum(OrchestrationAdviserTypes.class, type)
          && !EnumUtils.isValidEnum(CommonAdviserTypes.class, type)) {
        result = true;
      }
    }
    return result;
  }

  private boolean isPreviousAdviserExpired(List<InterruptEffect> interruptHistories) {
    if (interruptHistories.size() == 0) {
      return false;
    }
    return interruptHistories.get(interruptHistories.size() - 1).getInterruptConfig().getIssuedBy().hasTimeoutIssuer();
  }
}
