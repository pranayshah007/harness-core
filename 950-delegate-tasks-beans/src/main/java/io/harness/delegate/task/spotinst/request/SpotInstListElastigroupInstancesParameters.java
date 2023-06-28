/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.spotinst.request;

import static io.harness.delegate.task.spotinst.request.SpotInstTaskParameters.SpotInstTaskType.SPOT_INST_LIST_ELASTI_GROUP_INSTANCES;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SpotInstListElastigroupInstancesParameters extends SpotInstTaskParameters {
  private String elastigroupId;

  @Builder
  public SpotInstListElastigroupInstancesParameters(String appId, String accountId, String activityId,
      String commandName, String workflowExecutionId, Integer timeoutIntervalInMin, String awsRegion,
      String elastigroupId, boolean timeoutSupported) {
    super(appId, accountId, activityId, commandName, workflowExecutionId, timeoutIntervalInMin,
        SPOT_INST_LIST_ELASTI_GROUP_INSTANCES, awsRegion, timeoutSupported);
    this.elastigroupId = elastigroupId;
  }
}
