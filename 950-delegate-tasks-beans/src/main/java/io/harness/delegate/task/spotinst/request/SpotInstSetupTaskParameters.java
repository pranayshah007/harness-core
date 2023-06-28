/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.spotinst.request;

import static io.harness.delegate.task.spotinst.request.SpotInstTaskParameters.SpotInstTaskType.SPOT_INST_SETUP;

import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SpotInstSetupTaskParameters extends SpotInstTaskParameters {
  private String elastiGroupJson;
  private String elastiGroupNamePrefix;
  private boolean blueGreen;
  private String image;
  private String resizeStrategy;
  private List<LoadBalancerDetailsForBGDeployment> awsLoadBalancerConfigs;
  private String userData;

  @Builder
  public SpotInstSetupTaskParameters(String accountId, String appId, String commandName, String activityId,
      Integer timeoutIntervalInMin, String elastiGroupJson, String workflowExecutionId, String elastiGroupNamePrefix,
      boolean blueGreen, String image, String resizeStrategy, String awsRegion,
      List<LoadBalancerDetailsForBGDeployment> awsLoadBalancerConfigs, String userData, boolean timeouSupported) {
    super(appId, accountId, activityId, commandName, workflowExecutionId, timeoutIntervalInMin, SPOT_INST_SETUP,
        awsRegion, timeouSupported);
    this.blueGreen = blueGreen;
    this.elastiGroupJson = elastiGroupJson;
    this.elastiGroupNamePrefix = elastiGroupNamePrefix;
    this.image = image;
    this.resizeStrategy = resizeStrategy;
    this.awsLoadBalancerConfigs = awsLoadBalancerConfigs;
    this.userData = userData;
  }
}
