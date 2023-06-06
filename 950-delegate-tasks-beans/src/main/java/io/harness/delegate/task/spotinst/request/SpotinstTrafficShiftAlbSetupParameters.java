/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.spotinst.request;

import static io.harness.delegate.task.spotinst.request.SpotInstTaskParameters.SpotInstTaskType.SPOT_INST_ALB_SHIFT_SETUP;

import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SpotinstTrafficShiftAlbSetupParameters extends SpotInstTaskParameters {
  private String elastigroupJson;
  private String elastigroupNamePrefix;
  private String image;
  private List<LbDetailsForAlbTrafficShift> lbDetails;
  private String userData;

  @Builder
  public SpotinstTrafficShiftAlbSetupParameters(String appId, String accountId, String activityId, String commandName,
      String workflowExecutionId, Integer timeoutIntervalInMin, String awsRegion, String elastigroupJson,
      String elastigroupNamePrefix, String image, List<LbDetailsForAlbTrafficShift> lbDetails, String userData,
      boolean timeoutSupported) {
    super(appId, accountId, activityId, commandName, workflowExecutionId, timeoutIntervalInMin,
        SPOT_INST_ALB_SHIFT_SETUP, awsRegion, timeoutSupported);
    this.elastigroupJson = elastigroupJson;
    this.elastigroupNamePrefix = elastigroupNamePrefix;
    this.image = image;
    this.lbDetails = lbDetails;
    this.userData = userData;
  }
}
