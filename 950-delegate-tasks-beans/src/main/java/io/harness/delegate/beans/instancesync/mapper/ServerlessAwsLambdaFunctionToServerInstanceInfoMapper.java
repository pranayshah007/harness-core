/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.mapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.ServerlessAwsLambdaServerInstanceInfo;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaFunctionToServerInstanceInfoMapper {
  public List<ServerInstanceInfo> toServerInstanceInfoList(
      List<ServerlessAwsLambdaFunction> serverlessAwsLambdaFunctions, String region, String stage, String serviceName,
      String infraStructureKey) {
    return serverlessAwsLambdaFunctions.stream()
        .map(func -> toServerInstanceInfo(func, region, stage, serviceName, infraStructureKey))
        .collect(Collectors.toList());
  }

  public ServerInstanceInfo toServerInstanceInfo(ServerlessAwsLambdaFunction serverlessAwsLambdaFunction, String region,
      String stage, String serviceName, String infraStructureKey) {
    return ServerlessAwsLambdaServerInstanceInfo.builder()
        .serverlessServiceName(serviceName)
        .region(region)
        .serverlessStage(stage)
        .functionName(serverlessAwsLambdaFunction.getFunctionName())
        .handler(serverlessAwsLambdaFunction.getHandler())
        .memorySize(serverlessAwsLambdaFunction.getMemorySize())
        .runTime(serverlessAwsLambdaFunction.getRunTime())
        .timeout(serverlessAwsLambdaFunction.getTimeout())
        .infraStructureKey(infraStructureKey)
        .build();
  }
}
