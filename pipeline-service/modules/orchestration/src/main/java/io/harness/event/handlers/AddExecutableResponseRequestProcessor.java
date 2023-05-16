/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.AddExecutableResponseRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.execution.utils.SdkResponseEventUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import lombok.extern.slf4j.Slf4j;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
@Slf4j
public class AddExecutableResponseRequestProcessor implements SdkResponseProcessor {
  @Inject private NodeExecutionService nodeExecutionService;

  @Override
  public void handleEvent(SdkResponseEventProto event) {
    AddExecutableResponseRequest request = event.getAddExecutableResponseRequest();
    if (request.getExecutableResponse().hasAsync()
        && request.getExecutableResponse().getAsync().getStatus() != Status.NO_OP) {
      // As override set is empty, this will prevent any race condition as mongo will handle it using
      // StatusUtils.nodeAllowedStartSet()
      NodeExecution nodeExecution = nodeExecutionService.updateStatusWithOps(
          SdkResponseEventUtils.getNodeExecutionId(event), request.getExecutableResponse().getAsync().getStatus(),
          ops
          -> ops.addToSet(NodeExecutionKeys.executableResponses, request.getExecutableResponse()),
          EnumSet.noneOf(Status.class));
      // Update of node execution can also fail due to race condition between the status. This will also fail to update
      // executable response. Hence updating executable response in case failure happended due to race conditon for
      // status
      if (nodeExecution == null) {
        log.info("Update NodeExecution failed for status - {}, updating only executable Response",
            request.getExecutableResponse().getAsync().getStatus());
        nodeExecutionService.update(SdkResponseEventUtils.getNodeExecutionId(event),
            ops -> ops.addToSet(NodeExecutionKeys.executableResponses, request.getExecutableResponse()));
      }
    } else {
      nodeExecutionService.updateV2(SdkResponseEventUtils.getNodeExecutionId(event),
          ops -> ops.addToSet(NodeExecutionKeys.executableResponses, request.getExecutableResponse()));
    }
  }
}
