/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import static io.harness.eraro.ErrorCode.FREEZE_EXCEPTION;

import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.cdng.freeze.FreezeOutcome;
import io.harness.cdng.helpers.NgExpressionHelper;
import io.harness.cdng.service.steps.constants.ServiceStepConstants;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.eraro.Level;
import io.harness.freeze.beans.FreezeEntityType;
import io.harness.freeze.beans.response.FreezeSummaryResponseDTO;
import io.harness.freeze.helpers.FreezeRBACHelper;
import io.harness.freeze.notifications.NotificationHelper;
import io.harness.freeze.service.FreezeEvaluateService;
import io.harness.freeze.service.FrozenExecutionService;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.plan.ExpressionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.rbac.CDNGRbacUtility;
import io.harness.repositories.UpsertOptions;
import io.harness.steps.EntityReferenceExtractorUtils;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ServiceStep implements SyncExecutable<ServiceStepParameters> {
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject @Named("PRIVILEGED") private AccessControlClient accessControlClient;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private NGFeatureFlagHelperService ngFeatureFlagHelperService;
  @Inject private FreezeEvaluateService freezeEvaluateService;
  @Inject private FrozenExecutionService frozenExecutionService;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private NotificationHelper notificationHelper;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject NgExpressionHelper ngExpressionHelper;

  @Override
  public Class<ServiceStepParameters> getStepParametersClass() {
    return ServiceStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, ServiceStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ServiceStepUtils.validateResources(
        entityReferenceExtractorUtils, pipelineRbacHelper, accessControlClient, ambiance, stepParameters);
    ServiceEntity serviceEntity = ServiceStepUtils.getServiceEntity(serviceEntityService, ambiance, stepParameters);
    serviceEntityService.upsert(serviceEntity, UpsertOptions.DEFAULT.withNoOutbox().withNoSetupUsage());
    Map<FreezeEntityType, List<String>> entityMap = new HashMap<>();
    entityMap.put(FreezeEntityType.ORG, Lists.newArrayList(serviceEntity.getOrgIdentifier()));
    entityMap.put(FreezeEntityType.PROJECT, Lists.newArrayList(serviceEntity.getProjectIdentifier()));
    entityMap.put(FreezeEntityType.SERVICE, Lists.newArrayList(serviceEntity.getIdentifier()));
    StepResponse stepResponse = executeFreezePart(ambiance, entityMap, stepParameters, serviceEntity);
    if (stepResponse != null) {
      return stepResponse;
    }
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepOutcome.builder()
                         .name(OutcomeExpressionConstants.SERVICE)
                         .outcome(ServiceStepOutcome.fromServiceEntity(stepParameters.getType(), serviceEntity))
                         .group(StepOutcomeGroup.STAGE.name())
                         .build())
        .build();
  }

  protected StepResponse executeFreezePart(Ambiance ambiance, Map<FreezeEntityType, List<String>> entityMap,
      ServiceStepParameters stepParameters, ServiceEntity serviceEntity) {
    if (ngFeatureFlagHelperService.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.NG_DEPLOYMENT_FREEZE)) {
      String accountId = AmbianceUtils.getAccountId(ambiance);
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
      if (FreezeRBACHelper.checkIfUserHasFreezeOverrideAccess(ngFeatureFlagHelperService, accountId, orgId, projectId,
              accessControlClient, CDNGRbacUtility.constructPrincipalFromAmbiance(ambiance))) {
        return null;
      }
      List<FreezeSummaryResponseDTO> globalFreezeConfigs;
      List<FreezeSummaryResponseDTO> manualFreezeConfigs;
      globalFreezeConfigs = freezeEvaluateService.anyGlobalFreezeActive(accountId, orgId, projectId);
      manualFreezeConfigs = freezeEvaluateService.getActiveManualFreezeEntities(accountId, orgId, projectId, entityMap);
      if (globalFreezeConfigs.size() + manualFreezeConfigs.size() > 0) {
        final List<StepResponse.StepOutcome> stepOutcomes = new ArrayList<>();
        FreezeOutcome freezeOutcome = FreezeOutcome.builder()
                                          .frozen(true)
                                          .manualFreezeConfigs(manualFreezeConfigs)
                                          .globalFreezeConfigs(globalFreezeConfigs)
                                          .build();

        frozenExecutionService.createFrozenExecution(ambiance, manualFreezeConfigs, globalFreezeConfigs);

        sweepingOutputService.consume(ambiance, ServiceStepConstants.FREEZE_SWEEPING_OUTPUT, freezeOutcome, "");
        stepOutcomes.add(StepResponse.StepOutcome.builder()
                             .name(OutcomeExpressionConstants.FREEZE_OUTCOME)
                             .outcome(freezeOutcome)
                             .group(StepCategory.STAGE.name())
                             .build());
        stepOutcomes.add(StepOutcome.builder()
                             .name(OutcomeExpressionConstants.SERVICE)
                             .outcome(ServiceStepOutcome.fromServiceEntity(stepParameters.getType(), serviceEntity))
                             .group(StepOutcomeGroup.STAGE.name())
                             .build());
        String executionUrl =
            engineExpressionService.renderExpression(ambiance, ServiceStepConstants.PIPELINE_EXECUTION_EXPRESSION,
                ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED);
        String baseUrl = ngExpressionHelper.getBaseUrl(AmbianceUtils.getAccountId(ambiance));
        notificationHelper.sendNotificationForFreezeConfigs(freezeOutcome.getManualFreezeConfigs(),
            freezeOutcome.getGlobalFreezeConfigs(), ambiance, executionUrl, baseUrl);
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
