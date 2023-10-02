/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.ecs.EcsTask;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.EcsTaskToServerInstanceInfoMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@Singleton
@Slf4j
@OwnedBy(CDP)
public class EcsTaskHelperBase {
  @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskNGHelper;

  public LogCallback getLogCallback(ILogStreamingTaskClient logStreamingTaskClient, String commandUnitName,
      boolean shouldOpenStream, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(logStreamingTaskClient, commandUnitName, shouldOpenStream, commandUnitsProgress);
  }

  public List<ServerInstanceInfo> getEcsServerInstanceInfos(EcsDeploymentReleaseData deploymentReleaseData) {
    EcsInfraConfig ecsInfraConfig = deploymentReleaseData.getEcsInfraConfig();
    ecsInfraConfigHelper.decryptEcsInfraConfig(ecsInfraConfig);
    List<EcsTask> ecsTasks = ecsCommandTaskNGHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
        ecsInfraConfig.getCluster(), deploymentReleaseData.getServiceName(), ecsInfraConfig.getRegion());
    if (ecsTasks != null && ecsTasks.size() > 0) {
      return EcsTaskToServerInstanceInfoMapper.toServerInstanceInfoList(
          ecsTasks, ecsInfraConfig.getInfraStructureKey(), ecsInfraConfig.getRegion());
    }
    return new ArrayList<>();
  }
}
