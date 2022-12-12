/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.asg;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AsgTaskHelperBase {
  @Inject private AsgInfraConfigHelper asgInfraConfigHelper;
  @Inject private AsgCommandTaskNGHelper asgCommandTaskNGHelper;

  public LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      boolean shouldOpenStream, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream, commandUnitsProgress);
  }
  /*
    public List<ServerInstanceInfo> getAsgServerInstanceInfos(AsgDeploymentReleaseData deploymentReleaseData) {
      AsgInfraConfig asgInfraConfig = deploymentReleaseData.getAsgInfraConfig();
      asgInfraConfigHelper.decryptAsgInfraConfig(asgInfraConfig);
      List<AsgTask> ecsTasks = asgCommandTaskNGHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
          ecsInfraConfig.getCluster(), deploymentReleaseData.getServiceName(), ecsInfraConfig.getRegion());
      if (ecsTasks != null && ecsTasks.size() > 0) {
        return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
            ecsTasks, ecsInfraConfig.getInfraStructureKey(), ecsInfraConfig.getRegion());
      }
      return new ArrayList<>();
    }
   */
}
