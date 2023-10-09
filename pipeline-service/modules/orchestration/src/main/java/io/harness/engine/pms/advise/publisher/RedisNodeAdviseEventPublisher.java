/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.publisher;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.advise.NodeAdviserUtils;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.execution.NodeExecution;
import io.harness.plan.Node;
import io.harness.pms.contracts.advisers.AdviseEvent;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.events.base.PmsEventCategory;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.PIPELINE)
public class RedisNodeAdviseEventPublisher implements NodeAdviseEventPublisher {
  @Inject private PmsEventSender eventSender;

  @Override
  public String publishEvent(NodeExecution nodeExecution, Node planNode, Status fromStatus) {
    return publishEvent(nodeExecution, nodeExecution.getFailureInfo(), planNode, fromStatus);
  }

  @Override
  public String publishEvent(NodeExecution nodeExecution, FailureInfo failureInfo, Node planNode, Status fromStatus) {
    AdviseEvent adviseEvent = NodeAdviserUtils.createAdviseEvent(nodeExecution, failureInfo, planNode, fromStatus);
    return eventSender.sendEvent(nodeExecution.getAmbiance(), adviseEvent.toByteString(), PmsEventCategory.NODE_ADVISE,
        nodeExecution.getModule(), true);
  }
}
