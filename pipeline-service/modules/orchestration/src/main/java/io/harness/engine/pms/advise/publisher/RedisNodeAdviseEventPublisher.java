/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.publisher;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.InterruptEffect;
import io.harness.plan.Node;
import io.harness.pms.contracts.advisers.AdviseEvent;
import io.harness.pms.contracts.advisers.AdviseEvent.Builder;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.events.base.PmsEventCategory;

import com.google.inject.Inject;
import java.util.List;

@OwnedBy(HarnessTeam.PIPELINE)
public class RedisNodeAdviseEventPublisher implements NodeAdviseEventPublisher {
  @Inject private PmsEventSender eventSender;

  @Override
  public String publishEvent(NodeExecution nodeExecution, Node planNode, Status fromStatus) {
    return publishEvent(nodeExecution, nodeExecution.getFailureInfo(), planNode, fromStatus);
  }

  @Override
  public String publishEvent(NodeExecution nodeExecution, FailureInfo failureInfo, Node planNode, Status fromStatus) {
    Builder builder = AdviseEvent.newBuilder()
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

    return eventSender.sendEvent(nodeExecution.getAmbiance(), builder.build().toByteString(),
        PmsEventCategory.NODE_ADVISE, nodeExecution.getModule(), true);
  }

  private boolean isPreviousAdviserExpired(List<InterruptEffect> interruptHistories) {
    if (interruptHistories.size() == 0) {
      return false;
    }
    return interruptHistories.get(interruptHistories.size() - 1).getInterruptConfig().getIssuedBy().hasTimeoutIssuer();
  }
}
