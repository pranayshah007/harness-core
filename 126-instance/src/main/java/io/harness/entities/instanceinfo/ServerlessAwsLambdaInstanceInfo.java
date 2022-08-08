/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.DX)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class ServerlessAwsLambdaInstanceInfo extends InstanceInfo {
  // serverless details
  @NotNull private String serviceName;
  @NotNull private String stage;

  // lambda function details
  @NotNull private String functionName;
  @NotNull private String region;
  private String handler;
  private String memorySize;
  private String runTime;
  private Integer timeout;

  // harness
  private String infraStructureKey;
}
