/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.spotinst.request;

import static io.harness.delegate.task.spotinst.request.SpotInstTaskParameters.SpotInstTaskType.SPOT_INST_ALB_SHIFT_SWAP_ROUTES;

import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.spotinst.model.ElastiGroup;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SpotinstTrafficShiftAlbSwapRoutesParameters extends SpotInstTaskParameters {
  private ElastiGroup newElastigroup;
  private ElastiGroup oldElastigroup;
  private String elastigroupNamePrefix;
  private boolean downsizeOldElastigroup;
  private boolean rollback;
  private int newElastigroupWeight;
  private List<LbDetailsForAlbTrafficShift> details;

  @Builder
  public SpotinstTrafficShiftAlbSwapRoutesParameters(String appId, String accountId, String activityId,
      String commandName, String workflowExecutionId, Integer timeoutIntervalInMin, String awsRegion,
      ElastiGroup newElastigroup, ElastiGroup oldElastigroup, String elastigroupNamePrefix,
      boolean downsizeOldElastigroup, boolean rollback, List<LbDetailsForAlbTrafficShift> details,
      int newElastigroupWeight, boolean timeoutSupported) {
    super(appId, accountId, activityId, commandName, workflowExecutionId, timeoutIntervalInMin,
        SPOT_INST_ALB_SHIFT_SWAP_ROUTES, awsRegion, timeoutSupported);
    this.newElastigroup = newElastigroup;
    this.oldElastigroup = oldElastigroup;
    this.elastigroupNamePrefix = elastigroupNamePrefix;
    this.downsizeOldElastigroup = downsizeOldElastigroup;
    this.rollback = rollback;
    this.details = details;
    this.newElastigroupWeight = newElastigroupWeight;
  }
}
