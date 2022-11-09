/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup.beans;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.spotinst.model.ElastiGroup;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@TypeAlias("elastigroupSetupDataOutcome")
@JsonTypeName("elastigroupSetupDataOutcome")
@RecasterAlias("io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome")
public class ElastigroupSetupDataOutcome implements Outcome, ExecutionSweepingOutput {
  private Integer maxInstanceCount;
  private boolean useCurrentRunningInstanceCount;
  private Integer currentRunningInstanceCount;
  private ResizeStrategy resizeStrategy;
  private boolean isBlueGreen;
  private ElastiGroup newElastiGroupOriginalConfig;
  private ElastiGroup oldElastiGroupOriginalConfig;
  private String elastiGroupNamePrefix;
}
