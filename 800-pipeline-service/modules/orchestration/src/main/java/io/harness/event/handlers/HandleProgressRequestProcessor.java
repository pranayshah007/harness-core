/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.springdata.SpringDataMongoUtils.setUnset;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.execution.events.HandleProgressRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.execution.utils.SdkResponseEventUtils;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;

@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class HandleProgressRequestProcessor implements SdkResponseProcessor {
  @Inject private NodeExecutionService nodeExecutionService;

  @Override
  public void handleEvent(SdkResponseEventProto event) {
    HandleProgressRequest progressRequest = event.getProgressRequest();
    Map<String, Object> progressDoc = RecastOrchestrationUtils.fromJson(progressRequest.getProgressJson());
    nodeExecutionService.updateV2(SdkResponseEventUtils.getNodeExecutionId(event),
        ops -> setUnset(ops, NodeExecutionKeys.progressData, progressDoc));
  }
}
