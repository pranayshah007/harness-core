/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.execution.events.queue.plan;

import io.harness.hsqs.client.model.DequeueResponse;
import io.harness.pms.plan.execution.PipelineExecutor;
import io.harness.pms.plan.execution.entity.PipelineExecutionCreationProperties;

import com.google.inject.Inject;
import org.apache.commons.lang3.SerializationUtils;

public class PlanExecutionQueueMessageListener extends PlanExecutionAbstractQueueMessageListener {
  @Inject private PipelineExecutor pipelineExecutor;

  @Override
  public boolean handleMessage(DequeueResponse message) {
    message.getPayload();

    try {
      PipelineExecutionCreationProperties pipelineExecutionCreationProperties =
          SerializationUtils.deserialize(message.getPayload());

      pipelineExecutor.getPlanExecutionResponseDto(pipelineExecutionCreationProperties.getAccountId(),
          pipelineExecutionCreationProperties.getOrgIdentifier(),
          pipelineExecutionCreationProperties.getProjectIdentifier(), pipelineExecutionCreationProperties.isUseV2(),
          pipelineExecutionCreationProperties.getPipelineEntity(), pipelineExecutionCreationProperties.getExecArgs());
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
