/*
 * Copyright 2022 Harness Inc. All rights reserved.
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
import io.harness.delegate.beans.ecs.EcsCanaryDeleteResult;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.ecs.EcsCommandTaskNGHelper;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsInfraConfigHelper;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsCanaryDeleteRequest;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.response.EcsCanaryDeleteResponse;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.Service;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsCanaryDeleteCommandTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private EcsInfraConfigHelper ecsInfraConfigHelper;
  @Inject private EcsCommandTaskNGHelper ecsCommandTaskHelper;

  private EcsInfraConfig ecsInfraConfig;
  private long timeoutInMillis;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsCanaryDeleteRequest)) {
      throw new InvalidArgumentsException(Pair.of("ecsCommandRequest", "Must be instance of EcsCanaryDeleteRequest"));
    }

    EcsCanaryDeleteRequest ecsCanaryDeleteRequest = (EcsCanaryDeleteRequest) ecsCommandRequest;
    timeoutInMillis = ecsCanaryDeleteRequest.getTimeoutIntervalInMin() * 60000;
    ecsInfraConfig = ecsCanaryDeleteRequest.getEcsInfraConfig();

    LogCallback canaryDeleteLogCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.deleteService.toString(), true, commandUnitsProgress);

    try {
      canaryDeleteLogCallback.saveExecutionLog(format("Canary Delete..%n%n"), LogLevel.INFO);
      String ecsServiceDefinitionManifestContent = ecsCanaryDeleteRequest.getEcsServiceDefinitionManifestContent();

      CreateServiceRequest.Builder createServiceRequestBuilder = ecsCommandTaskHelper.parseYamlAsObject(
          ecsServiceDefinitionManifestContent, CreateServiceRequest.serializableBuilderClass());

      CreateServiceRequest createServiceRequest = createServiceRequestBuilder.build();

      String canaryServiceName = createServiceRequest.serviceName() + ecsCanaryDeleteRequest.getEcsServiceNameSuffix();

      Optional<Service> optionalService = ecsCommandTaskHelper.describeService(ecsInfraConfig.getCluster(),
          canaryServiceName, ecsInfraConfig.getRegion(), ecsInfraConfig.getAwsConnectorDTO());

      EcsCanaryDeleteResult ecsCanaryDeleteResult = null;

      if (optionalService.isPresent() && ecsCommandTaskHelper.isServiceActive(optionalService.get())) {
        canaryDeleteLogCallback.saveExecutionLog(format("Deleting service %s..", canaryServiceName), LogLevel.INFO);

        ecsCommandTaskHelper.deleteService(canaryServiceName, ecsInfraConfig.getCluster(), ecsInfraConfig.getRegion(),
            ecsInfraConfig.getAwsConnectorDTO());

        ecsCommandTaskHelper.ecsServiceInactiveStateCheck(canaryDeleteLogCallback, ecsInfraConfig.getAwsConnectorDTO(),
            ecsInfraConfig.getCluster(), canaryServiceName, ecsInfraConfig.getRegion(),
            (int) TimeUnit.MILLISECONDS.toMinutes(timeoutInMillis));

        ecsCanaryDeleteResult =
            EcsCanaryDeleteResult.builder().canaryDeleted(true).canaryServiceName(canaryServiceName).build();

        canaryDeleteLogCallback.saveExecutionLog(
            format("Canary service %s deleted", canaryServiceName), LogLevel.INFO, CommandExecutionStatus.SUCCESS);
      } else {
        canaryDeleteLogCallback.saveExecutionLog(format("Canary service %s doesn't exist", canaryServiceName),
            LogLevel.INFO, CommandExecutionStatus.SUCCESS);
        ecsCanaryDeleteResult =
            EcsCanaryDeleteResult.builder().canaryDeleted(false).canaryServiceName(canaryServiceName).build();
      }
      return EcsCanaryDeleteResponse.builder()
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .ecsCanaryDeleteResult(ecsCanaryDeleteResult)
          .build();
    } catch (Exception ex) {
      canaryDeleteLogCallback.saveExecutionLog(color(format("%n Canary Delete Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new EcsNGException(ex);
    }
  }
}
