/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.creator.plan.environment.steps;

import static io.harness.eraro.ErrorCode.FREEZE_EXCEPTION;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.creator.plan.environment.EnvironmentMapper;
import io.harness.cdng.creator.plan.environment.EnvironmentStepsUtils;
import io.harness.cdng.environment.steps.EnvironmentStepParameters;
import io.harness.cdng.freeze.FreezeOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.eraro.Level;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.helpers.FreezeRBACHelper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.ng.core.envGroup.EnvironmentGroupOutcome;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.OutputExpressionConstants;
import io.harness.steps.environment.EnvironmentOutcome;
import io.harness.steps.executable.SyncExecutableWithRbac;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
public class EnvironmentStepV2 implements SyncExecutableWithRbac<EnvironmentStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ENVIRONMENT_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject private FreezeEvaluateService freezeEvaluateService;
  public static final String FREEZE_SWEEPING_OUTPUT = "freezeSweepingOutput";

  @Override
  public void validateResources(Ambiance ambiance, EnvironmentStepParameters stepParameters) {
    EnvironmentStepsUtils.validateResources(accessControlClient, ambiance, stepParameters);
  }

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, EnvironmentStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    log.info("Starting execution for Environment Step [{}]", stepParameters);
    if (stepParameters.getEnvGroupRef() != null && stepParameters.getEnvGroupRef().fetchFinalValue() != null) {
      EnvironmentGroupOutcome environmentGroupOutcome = EnvironmentMapper.toEnvironmentGroupOutcome(stepParameters);
      executionSweepingOutputResolver.consume(
          ambiance, OutputExpressionConstants.ENVIRONMENT_GROUP, environmentGroupOutcome, StepCategory.STAGE.name());
    } else if (stepParameters.getEnvironmentRef() != null
        && stepParameters.getEnvironmentRef().fetchFinalValue() != null) {
      EnvironmentOutcome environmentOutcome = EnvironmentMapper.toEnvironmentOutcome(stepParameters);
      executionSweepingOutputResolver.consume(
          ambiance, OutputExpressionConstants.ENVIRONMENT, environmentOutcome, StepCategory.STAGE.name());
    } else {
      return StepResponse.builder()
          .status(Status.FAILED)
          .failureInfo(FailureInfo.newBuilder()
                           .addFailureData(FailureData.newBuilder().setMessage("env or env group must be set").build())
                           .build())
          .build();
    }
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ORG, Lists.newArrayList(AmbianceUtils.getOrgIdentifier(ambiance)));
    entityMap.put(FreezeEntityType.PROJECT, Lists.newArrayList(AmbianceUtils.getProjectIdentifier(ambiance)));
    entityMap.put(FreezeEntityType.ENVIRONMENT, Lists.newArrayList(stepParameters.getEnvironmentRef().getValue()));
    entityMap.put(FreezeEntityType.ENV_TYPE, Lists.newArrayList(stepParameters.getType().name()));
    StepResponse stepResponse = executeFreezePart(ambiance, entityMap);
    if (stepResponse != null) {
      return stepResponse;
    }
    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  @Override
  public Class<EnvironmentStepParameters> getStepParametersClass() {
    return EnvironmentStepParameters.class;
  }

  protected StepResponse executeFreezePart(Ambiance ambiance, Map<FreezeEntityType, List<String>> entityMap) {
    if (ngFeatureFlagHelperService.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.NG_DEPLOYMENT_FREEZE)) {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
      if (FreezeRBACHelper.checkIfUserHasFreezeOverrideAccess(accountId, orgId, projectId, accessControlClient)) {
        return null;
      }
      List<FreezeSummaryResponseDTO> globalFreezeConfigs;
      List<FreezeSummaryResponseDTO> manualFreezeConfigs;
      globalFreezeConfigs = freezeEvaluateService.anyGlobalFreezeActive(accountId, orgId, projectId);
      manualFreezeConfigs = freezeEvaluateService.getActiveFreezeEntities(accountId, orgId, projectId, entityMap);
      if (globalFreezeConfigs.size() + manualFreezeConfigs.size() > 0) {
        final List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
        FreezeOutcome freezeOutcome = FreezeOutcome.builder()
                                          .frozen(true)
                                          .manualFreezeConfigs(manualFreezeConfigs)
                                          .globalFreezeConfigs(globalFreezeConfigs)
                                          .build();
        executionSweepingOutputResolver.consume(ambiance, FREEZE_SWEEPING_OUTPUT, freezeOutcome, "");
        stepOutcomes.add(StepResponse.StepOutcome.builder()
                             .name(OutcomeExpressionConstants.FREEZE_OUTCOME)
                             .outcome(freezeOutcome)
                             .group(StepCategory.STAGE.name())
                             .build());
        return StepResponse.builder()
            .stepOutcomes(stepOutcomes)
            .failureInfo(FailureInfo.newBuilder()
                             .addFailureData(FailureData.newBuilder()
                                                 .addFailureTypes(FailureType.FREEZE_ACTIVE_FAILURE)
                                                 .setLevel(Level.ERROR.name())
                                                 .setCode(FREEZE_EXCEPTION.name())
                                                 .setMessage("Pipeline Aborted due to freeze")
                                                 .build())
                             .build())
            .status(Status.FREEZE_FAILED)
            .build();
      }
    }
    return null;
  }
}
