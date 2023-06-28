/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.spotinst.request;

import static io.harness.delegate.task.spotinst.request.SpotInstTaskParameters.SpotInstTaskType.SPOT_INST_DEPLOY;

import io.harness.spotinst.model.ElastiGroup;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SpotInstDeployTaskParameters extends SpotInstTaskParameters {
  private ElastiGroup newElastiGroupWithUpdatedCapacity;
  private ElastiGroup oldElastiGroupWithUpdatedCapacity;
  private boolean resizeNewFirst;
  private boolean blueGreen;
  private boolean rollback;

  @Builder
  public SpotInstDeployTaskParameters(String accountId, String appId, String commandName, String activityId,
      Integer timeoutIntervalInMin, ElastiGroup newElastiGroupWithUpdatedCapacity,
      ElastiGroup oldElastiGroupWithUpdatedCapacity, String workflowExecutionId, String awsRegion,
      boolean resizeNewFirst, boolean blueGreen, boolean rollback, boolean timeoutSupported) {
    super(appId, accountId, activityId, commandName, workflowExecutionId, timeoutIntervalInMin, SPOT_INST_DEPLOY,
        awsRegion, timeoutSupported);
    this.newElastiGroupWithUpdatedCapacity = newElastiGroupWithUpdatedCapacity;
    this.oldElastiGroupWithUpdatedCapacity = oldElastiGroupWithUpdatedCapacity;
    this.resizeNewFirst = resizeNewFirst;
    this.blueGreen = blueGreen;
    this.rollback = rollback;
  }
}
