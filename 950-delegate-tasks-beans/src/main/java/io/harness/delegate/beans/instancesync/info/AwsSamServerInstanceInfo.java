/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.info;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.serverless.model.AwsLambdaFunctionDetails;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@JsonTypeName("AwsSamServerInstanceInfo")
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class AwsSamServerInstanceInfo extends ServerInstanceInfo {
  private String functionName;
  private String region;

  private String handler;
  private String memorySize;
  private String runTime;

  private String infraStructureKey;

  public static AwsSamServerInstanceInfo getAwsSamServerInstanceInfo(
      AwsLambdaFunctionDetails awsLambdaFunctionDetails, String region, String infraStructureKey) {
    return AwsSamServerInstanceInfo.builder()
        .functionName(awsLambdaFunctionDetails.getFunctionName())
        .region(region)
        .handler(awsLambdaFunctionDetails.getHandler())
        .memorySize(awsLambdaFunctionDetails.getMemorySize())
        .runTime(awsLambdaFunctionDetails.getRunTime())
        .infraStructureKey(infraStructureKey)
        .build();
  }
}
