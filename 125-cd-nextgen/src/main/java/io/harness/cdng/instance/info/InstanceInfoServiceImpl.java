/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.instance.info;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.instance.outcome.DeploymentInfoOutcome;
import io.harness.cdng.instance.util.InstanceSyncStepResolver;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
@Singleton
public class InstanceInfoServiceImpl implements InstanceInfoService {
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @NotNull
  @Override
  public List<ServerInstanceInfo> listServerInstances(Ambiance ambiance, StepType stepType) {
    if (!InstanceSyncStepResolver.shouldRunInstanceSync(stepType)) {
      return Collections.emptyList();
    }
    log.info("Start listing service instances for step type: {}", stepType.getType());

    RefObject sweepingOutputRefObject =
        RefObjectUtils.getSweepingOutputRefObject(AmbianceUtils.getFQNUsingLevels(ambiance.getLevelsList()) + "."
            + OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME);
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(ambiance, sweepingOutputRefObject);
    if (!optionalSweepingOutput.isFound()) {
      throw new InvalidRequestException(format("Not found sweeping output for step type: %s", stepType.getType()));
    }
    DeploymentInfoOutcome output = (DeploymentInfoOutcome) optionalSweepingOutput.getOutput();

    return output.getServerInstanceInfoList();
  }

  @Override
  public StepOutcome saveServerInstancesIntoSweepingOutput(
      Ambiance ambiance, @NotNull List<ServerInstanceInfo> instanceInfoList) {
    log.info("Start saving service instances into sweeping output, instanceInfoListSize: {}, instanceInfoListClass: {}",
        instanceInfoList.size(), instanceInfoList.getClass());

    DeploymentInfoOutcome deploymentInfoOutcome = buildDeploymentInfoOutcome(instanceInfoList);
    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME,
        deploymentInfoOutcome, StepOutcomeGroup.STEP.name());

    return StepOutcome.builder()
        .name(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME)
        .outcome(deploymentInfoOutcome)
        .build();
  }

  @Override
  public StepOutcome saveDeploymentInfoOutcomeIntoSweepingOutput(
      Ambiance ambiance, DeploymentInfoOutcome deploymentInfoOutcome) {
    final List<ServerInstanceInfo> serverInstanceInfoList = deploymentInfoOutcome.getServerInstanceInfoList();
    if (isNull(serverInstanceInfoList)) {
      throw new InvalidArgumentsException("Parameter serverInstanceInfoList cannot null");
    }
    log.info("Start saving deployment into sweeping output, instanceInfoListSize: {}, instanceInfoListClass: {}",
        serverInstanceInfoList.size(), serverInstanceInfoList.getClass());

    executionSweepingOutputService.consume(ambiance, OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME,
        deploymentInfoOutcome, StepOutcomeGroup.STEP.name());

    return StepOutcome.builder()
        .name(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME)
        .outcome(deploymentInfoOutcome)
        .build();
  }

  private DeploymentInfoOutcome buildDeploymentInfoOutcome(List<ServerInstanceInfo> instanceInfoList) {
    return DeploymentInfoOutcome.builder().serverInstanceInfoList(instanceInfoList).build();
  }
}
