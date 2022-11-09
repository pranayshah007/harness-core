/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import static io.harness.eraro.ErrorCode.FREEZE_EXCEPTION;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.freeze.FreezeOutcome;
import io.harness.cdng.service.ServiceStepUtils;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.eraro.Level;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.helpers.FreezeRBACHelper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
// This step only produces an Outcome for service expressions to work
public class ServiceStepV2 implements SyncExecutable<ServiceStepParametersV2> {
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(ExecutionNodeType.SERVICE_V2.getName()).setStepCategory(StepCategory.STEP).build();
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject private FreezeEvaluateService freezeEvaluateService;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  public static final String FREEZE_SWEEPING_OUTPUT = "freezeSweepingOutput";

  @Override
  public Class<ServiceStepParametersV2> getStepParametersClass() {
    return ServiceStepParametersV2.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, ServiceStepParametersV2 stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ServiceStepUtils.validateResourcesV2(
        entityReferenceExtractorUtils, pipelineRbacHelper, accessControlClient, ambiance, stepParameters);
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ORG, Lists.newArrayList(AmbianceUtils.getOrgIdentifier(ambiance)));
    entityMap.put(FreezeEntityType.PROJECT, Lists.newArrayList(AmbianceUtils.getOrgIdentifier(ambiance)));
    entityMap.put(FreezeEntityType.SERVICE, Lists.newArrayList(stepParameters.getIdentifier()));
    StepResponse stepResponse = executeFreezePart(ambiance, entityMap, stepParameters);
    if (stepResponse != null) {
      return stepResponse;
    }
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OutcomeExpressionConstants.SERVICE)
                         .outcome(ServiceStepOutcome.fromServiceStepV2(stepParameters.getIdentifier(),
                             stepParameters.getName(), stepParameters.getType(), stepParameters.getDescription(),
                             stepParameters.getTags(), stepParameters.getGitOpsEnabled()))
                         .group(StepCategory.STAGE.name())
                         .build())
        .build();
  }

  protected StepResponse executeFreezePart(
      Ambiance ambiance, Map<FreezeEntityType, List<String>> entityMap, ServiceStepParametersV2 stepParameters) {
    if (ngFeatureFlagHelperService.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.NG_DEPLOYMENT_FREEZE)) {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
      if (FreezeRBACHelper.checkIfUserHasFreezeOverrideAccess(
              ngFeatureFlagHelperService, accountId, orgId, projectId, accessControlClient)) {
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
        sweepingOutputService.consume(ambiance, FREEZE_SWEEPING_OUTPUT, freezeOutcome, "");
        stepOutcomes.add(StepResponse.StepOutcome.builder()
                             .name(OutcomeExpressionConstants.FREEZE_OUTCOME)
                             .outcome(freezeOutcome)
                             .group(StepCategory.STAGE.name())
                             .build());
        stepOutcomes.add(StepResponse.StepOutcome.builder()
                             .name(OutcomeExpressionConstants.SERVICE)
                             .outcome(ServiceStepOutcome.fromServiceStepV2(stepParameters.getIdentifier(),
                                 stepParameters.getName(), stepParameters.getType(), stepParameters.getDescription(),
                                 stepParameters.getTags(), stepParameters.getGitOpsEnabled()))
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
