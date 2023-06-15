/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.awssam;

import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AwsSamServerInstanceInfo;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.serverless.model.AwsLambdaFunctionDetails;

import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegateNG;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;

public class AwsSamTaskHelperBase {
  @Inject private AwsNgConfigMapper awsNgConfigMapper;
  @Inject private AwsLambdaHelperServiceDelegateNG awsLambdaHelperServiceDelegateNG;
  @Inject private AwsSamInfraConfigHelper awsSamInfraConfigHelper;

  public List<ServerInstanceInfo> getAwsSamServerInstanceInfos(AwsSamDeploymentReleaseData deploymentReleaseData) {
    List<String> functions = deploymentReleaseData.getFunctions();
    AwsSamInfraConfig awsSamInfraConfig = deploymentReleaseData.getAwsSamInfraConfig();
    awsSamInfraConfigHelper.decryptAwsSamInfraConfig(awsSamInfraConfig);
    AwsInternalConfig awsInternalConfig =
        awsNgConfigMapper.createAwsInternalConfig(awsSamInfraConfig.getAwsConnectorDTO());
    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();
    if (!CollectionUtils.isEmpty(functions)) {
      for (String function : functions) {
        AwsLambdaFunctionDetails awsLambdaFunctionDetails =
            awsLambdaHelperServiceDelegateNG.getAwsLambdaFunctionDetails(
                awsInternalConfig, function, deploymentReleaseData.getRegion());
        if (awsLambdaFunctionDetails != null) {
          AwsSamServerInstanceInfo awsSamServerInstanceInfo = AwsSamServerInstanceInfo.getAwsSamServerInstanceInfo(
              awsLambdaFunctionDetails, deploymentReleaseData.getRegion(), awsSamInfraConfig.getInfraStructureKey());
          serverInstanceInfoList.add(awsSamServerInstanceInfo);
        }
      }
    }
    return serverInstanceInfoList;
  }
}
