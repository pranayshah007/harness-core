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
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.EcsNGException;
import io.harness.delegate.task.ecs.EcsTaskHelperBase;
import io.harness.delegate.task.ecs.request.EcsCommandRequest;
import io.harness.delegate.task.ecs.request.EcsTaskArnBlueGreenCreateServiceRequest;
import io.harness.delegate.task.ecs.response.EcsCommandResponse;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.google.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@NoArgsConstructor
@Slf4j
public class EcsTaskArnBlueGreenCreateServiceCommandTaskHandler extends EcsCommandTaskNGHandler {
  @Inject private EcsTaskHelperBase ecsTaskHelperBase;
  @Inject private EcsDeploymentHelper ecsDeploymentHelper;

  @Override
  protected EcsCommandResponse executeTaskInternal(EcsCommandRequest ecsCommandRequest,
      ILogStreamingTaskClient iLogStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) throws Exception {
    if (!(ecsCommandRequest instanceof EcsTaskArnBlueGreenCreateServiceRequest)) {
      throw new InvalidArgumentsException(
          Pair.of("ecsCommandRequest", "Must be instance of EcsTaskArnBlueGreenCreateServiceRequest"));
    }
    EcsTaskArnBlueGreenCreateServiceRequest ecsTaskArnBlueGreenCreateServiceRequest =
        (EcsTaskArnBlueGreenCreateServiceRequest) ecsCommandRequest;

    LogCallback deployLogCallback = ecsTaskHelperBase.getLogCallback(
        iLogStreamingTaskClient, EcsCommandUnitConstants.deploy.toString(), true, commandUnitsProgress);
    try {
      return ecsDeploymentHelper.deployStageService(deployLogCallback, ecsCommandRequest,
          ecsTaskArnBlueGreenCreateServiceRequest.getEcsScalableTargetManifestContentList(),
          ecsTaskArnBlueGreenCreateServiceRequest.getEcsScalingPolicyManifestContentList(), null,
          ecsTaskArnBlueGreenCreateServiceRequest.getEcsServiceDefinitionManifestContent(),
          ecsTaskArnBlueGreenCreateServiceRequest.getEcsTaskDefinitionArn(),
          ecsTaskArnBlueGreenCreateServiceRequest.getEcsLoadBalancerConfig(),
          ecsTaskArnBlueGreenCreateServiceRequest.getTargetGroupArnKey(),
          ecsTaskArnBlueGreenCreateServiceRequest.isSameAsAlreadyRunningInstances(),
          ecsTaskArnBlueGreenCreateServiceRequest.isRemoveAutoScalingFromBlueService());
    } catch (Exception e) {
      deployLogCallback.saveExecutionLog(color(format("%n Deployment Failed."), LogColor.Red, LogWeight.Bold),
          LogLevel.ERROR, CommandExecutionStatus.FAILURE);
      throw new EcsNGException(e);
    }
  }
}
