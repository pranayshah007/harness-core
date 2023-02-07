/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import com.google.cloud.functions.v2.Function;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.InvalidProtocolBufferException;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.GoogleFunctionToServerInstanceInfoMapper;
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
  @Inject private GoogleFunctionInfraConfigHelper googleFunctionInfraConfigHelper;
  @Inject private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;
  public List<ServerInstanceInfo> getGoogleFunctionServerInstanceInfo(
      GoogleFunctionDeploymentReleaseData deploymentReleaseData) throws InvalidProtocolBufferException {
    GcpGoogleFunctionInfraConfig gcpGoogleFunctionInfraConfig =
        (GcpGoogleFunctionInfraConfig) deploymentReleaseData.getGoogleFunctionInfraConfig();
    googleFunctionInfraConfigHelper.decryptInfraConfig(gcpGoogleFunctionInfraConfig);
    Optional<Function> optionalFunction = googleFunctionCommandTaskHelper.getFunction(
        deploymentReleaseData.getFunction(), gcpGoogleFunctionInfraConfig.getGcpConnectorDTO(),
        gcpGoogleFunctionInfraConfig.getProject(), gcpGoogleFunctionInfraConfig.getRegion());
    if (optionalFunction.isPresent()) {
      Function function = optionalFunction.get();
      GoogleFunction googleFunction =
          googleFunctionCommandTaskHelper.getGoogleFunction(function, gcpGoogleFunctionInfraConfig, null);
      return GoogleFunctionToServerInstanceInfoMapper.toServerInstanceInfoList(googleFunction,
          gcpGoogleFunctionInfraConfig.getProject(), gcpGoogleFunctionInfraConfig.getRegion(),
          gcpGoogleFunctionInfraConfig.getInfraStructureKey());
    }
    return new ArrayList<>();
  }
}
