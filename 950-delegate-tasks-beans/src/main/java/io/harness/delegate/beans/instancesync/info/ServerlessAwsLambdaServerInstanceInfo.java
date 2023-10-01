/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.info;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.serverless.model.AwsLambdaFunctionDetails;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@JsonTypeName("ServerlessAwsLambdaServerInstanceInfo")
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@RecasterAlias("io.harness.delegate.beans.instancesync.info.ServerlessAwsLambdaServerInstanceInfo")
public class ServerlessAwsLambdaServerInstanceInfo extends ServerInstanceInfo {
  // serverless details
  private String serverlessServiceName;
  private String serverlessStage;

  private String region;

  // lambda function details
  private String functionName;
  private String handler;
  private String memorySize;
  private String runTime;
  private Integer timeout;

  // todo
  private int invocationsCount;
  private int errorsCount;

  // harness
  private String infraStructureKey;

  public static ServerlessAwsLambdaServerInstanceInfo getServerlessAwsLambdaServerInstanceInfo(
      String serverlessServiceName, String serverlessStage, String region,
      AwsLambdaFunctionDetails awsLambdaFunctionDetails, String infraStructureKey) {
    return ServerlessAwsLambdaServerInstanceInfo.builder()
        .serverlessServiceName(serverlessServiceName)
        .serverlessStage(serverlessStage)
        .region(region)
        .functionName(awsLambdaFunctionDetails.getFunctionName())
        .handler(awsLambdaFunctionDetails.getHandler())
        .memorySize(awsLambdaFunctionDetails.getMemorySize())
        .runTime(awsLambdaFunctionDetails.getRunTime())
        .timeout(awsLambdaFunctionDetails.getTimeout())
        .infraStructureKey(infraStructureKey)
        .build();
  }
}
