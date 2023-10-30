/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.elastigroup.request.ConnectedCloudProvider;
import io.harness.delegate.task.elastigroup.request.LoadBalancerConfig;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.spotinst.model.ElastiGroup;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@OwnedBy(CDP)
@TypeAlias("elastigroupExecutionPassThroughData")
@RecasterAlias("io.harness.cdng.elastigroup.beans.ElastigroupExecutionPassThroughData")
public class ElastigroupExecutionPassThroughData implements PassThroughData {
  private boolean blueGreen;

  // Available in Startup Script
  @Deprecated private String base64EncodedStartupScript;
  private String base64DecodedStartupScript;
  private InfrastructureOutcome infrastructure;

  // Available in Elastigroup configuration
  private String elastigroupConfiguration;

  // Available in Artifact Task
  private String image;
  private UnitProgressData lastActiveUnitProgressData;

  // Available in Pre Fetch Task
  private String elastigroupNamePrefix;
  private SpotInstConfig spotInstConfig;

  // Available before executing Setup Task
  private ResizeStrategy resizeStrategy;

  //  //Available in Setup Task
  private ElastiGroup generatedElastigroupConfig;
  //
  //  //Available only in BG Step
  private ConnectedCloudProvider connectedCloudProvider;
  private LoadBalancerConfig loadBalancerConfig;
}
