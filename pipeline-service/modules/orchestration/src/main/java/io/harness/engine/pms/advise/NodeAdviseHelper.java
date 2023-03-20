/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.pms.advise;

import io.harness.engine.pms.advise.publisher.NodeAdviseEventPublisher;
import io.harness.execution.NodeExecution;
import io.harness.plan.Node;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;

import com.google.inject.Inject;

public class NodeAdviseHelper {
  @Inject private NodeAdviseEventPublisher nodeAdviseEventPublisher;

  public void queueAdvisingEvent(NodeExecution nodeExecution, Node planNode, Status fromStatus) {
    nodeAdviseEventPublisher.publishEvent(nodeExecution, planNode, fromStatus);
  }

  public void queueAdvisingEvent(
      NodeExecution nodeExecution, FailureInfo failureInfo, Node planNode, Status fromStatus) {
    nodeAdviseEventPublisher.publishEvent(nodeExecution, failureInfo, planNode, fromStatus);
  }
}
