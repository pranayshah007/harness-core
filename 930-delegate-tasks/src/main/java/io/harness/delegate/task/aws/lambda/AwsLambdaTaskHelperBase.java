/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import com.amazonaws.services.lambda.model.GetFunctionResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.AwsLambdaToServerInstanceInfoMapper;
import io.harness.delegate.beans.instancesync.mapper.GoogleFunctionToServerInstanceInfoMapper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.delegate.task.googlefunction.GoogleFunctionCommandTaskHelper;
import io.harness.delegate.task.googlefunction.GoogleFunctionDeploymentReleaseData;
import io.harness.delegate.task.googlefunction.GoogleFunctionInfraConfigHelper;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.harness.annotations.dev.HarnessTeam.CDP;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AwsLambdaTaskHelperBase {
  @Inject private AwsLambdaInfraConfigHelper awsLambdaInfraConfigHelper;
  @Inject private AwsLambdaTaskHelper awsLambdaCommandTaskHelper;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  public List<ServerInstanceInfo> getAwsLambdaServerInstanceInfo(
      AwsLambdaDeploymentReleaseData deploymentReleaseData) {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig =
        (AwsLambdaFunctionsInfraConfig) deploymentReleaseData.getAwsLambdaInfraConfig();
    awsLambdaInfraConfigHelper.decryptInfraConfig(awsLambdaFunctionsInfraConfig);
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsLambdaFunctionsInfraConfig.getAwsConnectorDTO());
    AwsLambdaFunctionWithActiveVersions awsLambdaFunctionWithActiveVersions =
              awsLambdaCommandTaskHelper.getAwsLambdaFunctionWithActiveVersions(awsLambdaFunctionsInfraConfig, deploymentReleaseData.getFunction());
      return AwsLambdaToServerInstanceInfoMapper.toServerInstanceInfoList(awsLambdaFunctionWithActiveVersions,
              awsLambdaFunctionsInfraConfig.getRegion(),
              awsLambdaFunctionsInfraConfig.getInfraStructureKey());
  }
}
