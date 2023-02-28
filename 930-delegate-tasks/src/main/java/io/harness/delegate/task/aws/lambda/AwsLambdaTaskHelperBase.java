/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.AwsLambdaToServerInstanceInfoMapper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AwsLambdaTaskHelperBase {
  @Inject private AwsLambdaInfraConfigHelper awsLambdaInfraConfigHelper;
  @Inject private AwsLambdaTaskHelper awsLambdaCommandTaskHelper;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  private String latestVersionForAwsLambda = "$LATEST";
  public List<ServerInstanceInfo> getAwsLambdaServerInstanceInfo(AwsLambdaDeploymentReleaseData deploymentReleaseData) {
    AwsLambdaFunctionsInfraConfig awsLambdaFunctionsInfraConfig =
        (AwsLambdaFunctionsInfraConfig) deploymentReleaseData.getAwsLambdaInfraConfig();
    awsLambdaInfraConfigHelper.decryptInfraConfig(awsLambdaFunctionsInfraConfig);
    AwsLambdaFunctionWithActiveVersions awsLambdaFunctionWithActiveVersions =
        awsLambdaCommandTaskHelper.getAwsLambdaFunctionWithActiveVersions(
            awsLambdaFunctionsInfraConfig, deploymentReleaseData.getFunction());
    if (awsLambdaFunctionWithActiveVersions != null && awsLambdaFunctionWithActiveVersions.getVersions() != null) {
      awsLambdaFunctionWithActiveVersions.getVersions().remove(latestVersionForAwsLambda);
    }
    return AwsLambdaToServerInstanceInfoMapper.toServerInstanceInfoList(awsLambdaFunctionWithActiveVersions,
        awsLambdaFunctionsInfraConfig.getRegion(), awsLambdaFunctionsInfraConfig.getInfraStructureKey());
  }
}
