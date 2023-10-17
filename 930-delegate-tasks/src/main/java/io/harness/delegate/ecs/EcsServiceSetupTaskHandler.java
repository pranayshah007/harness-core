/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.ecs;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.ecs.EcsBasicDeployData;
import io.harness.delegate.beans.ecs.EcsBasicDeployData.EcsBasicDeployDataBuilder;
import io.harness.delegate.beans.ecs.EcsServiceData;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.EcsUtils;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsServiceSetupRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.delegate.task.ecs.response.EcsServiceSetupResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.List;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Service;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsServiceSetupTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private EcsDeploymentHelper ecsDeploymentHelper;
  private EcsInfraConfig ecsInfraConfig;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsServiceSetupRequest)) {
      throw new InvalidArgumentsException(Pair.of("ecsCommandRequest", "Must be instance of EcsServiceSetupRequest"));
    }

    EcsServiceSetupRequest serviceSetupRequest = (EcsServiceSetupRequest) ecsCommandRequest;
    ecsInfraConfig = serviceSetupRequest.getInfraConfig();

    LogCallback logCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.serviceSetup.toString(), true, commandUnitsProgress);

    try {
      long timeoutInMs = serviceSetupRequest.getTimeoutIntervalInMillis();
      CreateServiceRequest createServiceRequest = ecsDeploymentHelper.createServiceDefinitionRequest(logCallback,
          serviceSetupRequest.getEcsInfraConfig(), serviceSetupRequest.getTaskDefinitionManifestContent(),
          serviceSetupRequest.getServiceDefinitionManifestContent(),
          serviceSetupRequest.getScalableTargetManifestContentList(),
          serviceSetupRequest.getScalingPolicyManifestContentList(), serviceSetupRequest.getTaskDefinitionArn());

      String servicePrefix = EcsUtils.getServicePrefixByRemovingNumber(serviceSetupRequest.getNewServiceName());

      // update service name
      createServiceRequest =
          createServiceRequest.toBuilder().serviceName(serviceSetupRequest.getNewServiceName()).desiredCount(0).build();

      // fetch all services in cluster
      List<Service> services = ecsCommandTaskHelper.fetchServicesOfCluster(
          ecsInfraConfig.getAwsConnectorDTO(), ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion());

      // filter services with prefix
      List<Service> eligibleServices = ecsCommandTaskHelper.fetchServicesWithPrefix(services, servicePrefix);

      // get stale services
      List<Service> staleServices = ecsCommandTaskHelper.fetchStaleServices(eligibleServices,
          Lists.newArrayList(serviceSetupRequest.getOldServiceName(), serviceSetupRequest.getNewServiceName()));

      // downsize stale services which are having running instances
      ecsCommandTaskHelper.downsizeStaleServicesHavingRunningInstances(
          staleServices, logCallback, ecsInfraConfig, timeoutInMs);

      // delete stale services which are having zero instances
      ecsCommandTaskHelper.deleteStaleServicesWithZeroInstance(staleServices, logCallback, ecsInfraConfig);

      // create a new service
      ecsCommandTaskHelper.createService(createServiceRequest, ecsInfraConfig, logCallback, timeoutInMs);

      EcsBasicDeployDataBuilder basicDeployDataBuilder =
          EcsBasicDeployData.builder()
              .newServiceData(EcsServiceData.builder()
                                  .region(ecsInfraConfig.getRegion())
                                  .serviceName(serviceSetupRequest.getNewServiceName())
                                  .tasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                                      ecsInfraConfig.getCluster(), serviceSetupRequest.getNewServiceName(),
                                      ecsInfraConfig.getRegion()))
                                  .build())
              .oldServiceData(EcsServiceData.builder().build());
      if (!serviceSetupRequest.isFirstTimeDeployment()) {
        basicDeployDataBuilder.oldServiceData(
            EcsServiceData.builder()
                .region(ecsInfraConfig.getRegion())
                .serviceName(serviceSetupRequest.getOldServiceName())
                .tasks(ecsCommandTaskHelper.getRunningEcsTasks(ecsInfraConfig.getAwsConnectorDTO(),
                    ecsInfraConfig.getCluster(), serviceSetupRequest.getOldServiceName(), ecsInfraConfig.getRegion()))
                .build());
      }

      EcsServiceSetupResponse ecsServiceSetupResponse = EcsServiceSetupResponse.builder()
                                                            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                            .deployData(basicDeployDataBuilder.build())
                                                            .build();
      logCallback.saveExecutionLog("Service setup complete", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      log.info("Completed task execution for command: {}", ecsCommandRequest.getEcsCommandType().name());
      return ecsServiceSetupResponse;
    } catch (Exception ex) {
      logCallback.saveExecutionLog(color(format("%n Deployment Failed."), LogColor.Red, LogWeight.Bold), LogLevel.ERROR,
          CommandExecutionStatus.FAILURE);
      throw new EcsNGException(ex);
    }
  }
}
