/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup.beans;

import com.fasterxml.jackson.annotation.JsonTypeName;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.spotinst.model.ElastiGroup;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

import java.util.List;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@TypeAlias("elastigroupSetupDataOutcome")
@JsonTypeName("elastigroupSetupDataOutcome")
@RecasterAlias("io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome")
public class ElastigroupSwapRouteDataOutcome implements Outcome, ExecutionSweepingOutput {
  private String downsizeOldElastiGroup;
  private List<LoadBalancerDetailsForBGDeployment> lbDetails;
  private String newElastiGroupId;
  private String newElastiGroupName;
  private String oldElastiGroupId;
  private String oldElastiGroupName;
}
