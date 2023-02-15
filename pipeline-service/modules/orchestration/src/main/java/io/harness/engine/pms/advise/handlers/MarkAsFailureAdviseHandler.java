/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise.handlers;

import io.harness.execution.NodeExecution;
import io.harness.pms.contracts.advisers.AdviserResponse;

/***
 * Upon Failure node already has status as failed, the failure-strategy "MarkAsFailure" doesn't need to do update status
 * hence directly running the next node.
 */
public class MarkAsFailureAdviseHandler extends NextStepHandler {
  @Override
  public void handleAdvise(NodeExecution prevNodeExecution, AdviserResponse adviserResponse) {
    String nextNodeId = adviserResponse.getMarkAsFailureAdvise().getNextNodeId();
    runNextNode(prevNodeExecution, nextNodeId);
  }
}
