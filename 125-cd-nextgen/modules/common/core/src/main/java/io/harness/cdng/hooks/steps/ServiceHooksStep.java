/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.hooks.steps;

import static io.harness.cdng.hooks.ServiceHookConstants.POST_HOOK;
import static io.harness.cdng.hooks.ServiceHookConstants.PRE_HOOK;
import static io.harness.cdng.service.steps.constants.ServiceStepV3Constants.SERVICE_HOOKS_SWEEPING_OUTPUT;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.hooks.ServiceHook;
import io.harness.cdng.hooks.ServiceHookType;
import io.harness.cdng.hooks.ServiceHookWrapper;
import io.harness.cdng.hooks.mapper.ServiceHookOutcomeMapper;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logging.LogLevel;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.EntityReferenceExtractorUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class ServiceHooksStep implements SyncExecutable<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.SERVICE_HOOKS.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private ServiceStepsHelper serviceStepsHelper;

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
    final ServiceHooksMetadataSweepingOutput serviceHooksSweepingOutput =
        fetchServiceHooksMetadataFromSweepingOutput(ambiance);

    final List<ServiceHookWrapper> serviceHooks = serviceHooksSweepingOutput.getFinalServiceHooks();
    final NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    if (EmptyPredicate.isEmpty(serviceHooks)) {
      logCallback.saveExecutionLog(
          String.format("No service hooks configured in the service. <+%s> expressions will not work",
              OutcomeExpressionConstants.SERVICE_HOOKS),
          LogLevel.WARN);
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    checkForAccessOrThrow(ambiance, serviceHooks);

    final ServiceHooksOutcome serviceHooksOutcome = new ServiceHooksOutcome();
    for (int i = 0; i < serviceHooks.size(); i++) {
      ServiceHookWrapper hookWrapper = serviceHooks.get(i);
      ServiceHookType hookType;
      ServiceHook hook;
      if (hookWrapper.getPostHook() != null) {
        hookType = ServiceHookType.getHookType(POST_HOOK);
        hook = hookWrapper.getPostHook();
      } else {
        hookType = ServiceHookType.getHookType(PRE_HOOK);
        hook = hookWrapper.getPreHook();
      }
      List<String> actions = new ArrayList<>();
      hook.getActions().forEach(action -> actions.add(action.getDisplayName()));
      String identifier = hook.getIdentifier();
      StoreConfig storeConfig = hook.getStore();

      serviceHooksOutcome.put(
          identifier, ServiceHookOutcomeMapper.toServiceHookOutcome(identifier, hookType, actions, storeConfig, i + 1));
    }

    sweepingOutputService.consume(
        ambiance, OutcomeExpressionConstants.SERVICE_HOOKS, serviceHooksOutcome, StepCategory.STAGE.name());

    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private ServiceHooksMetadataSweepingOutput fetchServiceHooksMetadataFromSweepingOutput(Ambiance ambiance) {
    final OptionalSweepingOutput resolveOptional = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(SERVICE_HOOKS_SWEEPING_OUTPUT));

    return resolveOptional.isFound() ? (ServiceHooksMetadataSweepingOutput) resolveOptional.getOutput()
                                     : ServiceHooksMetadataSweepingOutput.builder().build();
  }

  void checkForAccessOrThrow(Ambiance ambiance, List<ServiceHookWrapper> serviceHooks) {
    if (EmptyPredicate.isEmpty(serviceHooks)) {
      return;
    }
    List<EntityDetail> entityDetails = new ArrayList<>();

    for (ServiceHookWrapper serviceHook : serviceHooks) {
      Set<EntityDetailProtoDTO> entityDetailsProto =
          serviceHook == null ? Set.of() : entityReferenceExtractorUtils.extractReferredEntities(ambiance, serviceHook);
      List<EntityDetail> entityDetail =
          entityDetailProtoToRestMapper.createEntityDetailsDTO(new ArrayList<>(emptyIfNull(entityDetailsProto)));
      if (EmptyPredicate.isNotEmpty(entityDetail)) {
        entityDetails.addAll(entityDetail);
      }
    }
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, true);
  }
}
