/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.elastigroup;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class ElastigroupSwapRouteResult {
  private String downsizeOldElastiGroup;
  private List<LoadBalancerDetailsForBGDeployment> lbDetails;
  private String newElastiGroupId;
  private String newElastiGroupName;
  private String oldElastiGroupId;
  private String oldElastiGroupName;
}
