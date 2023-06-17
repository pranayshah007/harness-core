/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.spotinst.request;

import static io.harness.delegate.task.spotinst.request.SpotInstTaskParameters.SpotInstTaskType.SPOT_INST_SWAP_ROUTES;

import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.spotinst.model.ElastiGroup;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SpotInstSwapRoutesTaskParameters extends SpotInstTaskParameters {
  private ElastiGroup newElastiGroup;
  private ElastiGroup oldElastiGroup;
  private String elastiGroupNamePrefix;
  private boolean downsizeOldElastiGroup;
  private boolean rollback;
  private int steadyStateTimeOut;
  private List<LoadBalancerDetailsForBGDeployment> lBdetailsForBGDeploymentList;

  @Builder
  public SpotInstSwapRoutesTaskParameters(String appId, String accountId, String activityId, String commandName,
      String workflowExecutionId, Integer timeoutIntervalInMin, String awsRegion, ElastiGroup newElastiGroup,
      ElastiGroup oldElastiGroup, boolean downsizeOldElastiGroup, boolean rollback, String elastiGroupNamePrefix,
      int steadyStateTimeOut, List<LoadBalancerDetailsForBGDeployment> lBdetailsForBGDeploymentList,
      boolean timeoutSupported) {
    super(appId, accountId, activityId, commandName, workflowExecutionId, timeoutIntervalInMin, SPOT_INST_SWAP_ROUTES,
        awsRegion, timeoutSupported);
    this.newElastiGroup = newElastiGroup;
    this.oldElastiGroup = oldElastiGroup;
    this.downsizeOldElastiGroup = downsizeOldElastiGroup;
    this.rollback = rollback;
    this.elastiGroupNamePrefix = elastiGroupNamePrefix;
    this.steadyStateTimeOut = steadyStateTimeOut;
    this.lBdetailsForBGDeploymentList = lBdetailsForBGDeploymentList;
  }
}
