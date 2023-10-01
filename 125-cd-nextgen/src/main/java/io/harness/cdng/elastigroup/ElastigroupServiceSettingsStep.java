/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE_STEP_COMMAND_UNIT;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.STARTUP_SCRIPT;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.elastigroup.config.StartupScriptOutcome;
import io.harness.cdng.elastigroup.config.yaml.StartupScriptConfiguration;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.beans.ElastigroupServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.steps.helpers.ServiceStepsHelper;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper;
import io.harness.ng.core.service.yaml.NGServiceV2InfoConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.EntityReferenceExtractorUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/*
Single step for Startup Script
 */
@Slf4j
public class ElastigroupServiceSettingsStep implements SyncExecutable<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ELASTIGROUP_SERVICE_SETTINGS_STEP.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private ServiceStepsHelper serviceStepsHelper;
  @Inject private ElastigroupHelperService elastigroupHelperService;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private CDExpressionResolver expressionResolver;
  @Inject EntityDetailProtoToRestMapper entityDetailProtoToRestMapper;
  @Inject private EntityReferenceExtractorUtils entityReferenceExtractorUtils;
  @Inject private PipelineRbacHelper pipelineRbacHelper;

  @Override
  public Class<EmptyStepParameters> getStepParametersClass() {
    return EmptyStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, EmptyStepParameters stepParameters, StepInputPackage inputPackage,
      PassThroughData passThroughData) {
    final Optional<NGServiceV2InfoConfig> serviceOptional = cdStepHelper.fetchServiceConfigFromSweepingOutput(ambiance);
    if (serviceOptional.isEmpty()) {
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    NGServiceV2InfoConfig service = serviceOptional.get();
    if (!ServiceDefinitionType.ELASTIGROUP.getYamlName().equals(
            service.getServiceDefinition().getServiceSpec().getType())) {
      log.info("skipping ElastigroupServiceSettingsStep because service is not of type elastigroup");
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    final ElastigroupServiceSpec serviceSpec = (ElastigroupServiceSpec) service.getServiceDefinition().getServiceSpec();

    expressionResolver.updateExpressions(ambiance, serviceSpec);

    final NGLogCallback logCallback =
        serviceStepsHelper.getServiceLogCallback(ambiance, false, SERVICE_STEP_COMMAND_UNIT);

    final List<StepResponse.StepOutcome> outcomes = new ArrayList<>();

    // Process elastigroup settings
    if (serviceSpec.getStartupScript() != null) {
      checkForAccessOrThrow(ambiance, serviceSpec.getStartupScript());
      outcomes.add(processStartupScript(ambiance, serviceSpec, logCallback));
    }

    return StepResponse.builder().status(Status.SUCCEEDED).stepOutcomes(outcomes).build();
  }

  private StepResponse.StepOutcome processStartupScript(
      Ambiance ambiance, ElastigroupServiceSpec serviceSpec, NGLogCallback logCallback) {
    saveExecutionLog(logCallback, "Processing startup script...");
    StoreConfigWrapper storeConfig = serviceSpec.getStartupScript().getStore();
    elastigroupHelperService.validateSettingsStoreReferences(storeConfig, ambiance, "Startup script");
    saveExecutionLog(logCallback, "Processed startup script");
    return StepResponse.StepOutcome.builder()
        .name(STARTUP_SCRIPT)
        .outcome(StartupScriptOutcome.builder().store(storeConfig.getSpec()).build())
        .group(StepCategory.STAGE.name())
        .build();
  }
  private void checkForAccessOrThrow(Ambiance ambiance, StartupScriptConfiguration startupScriptConfiguration) {
    Set<EntityDetailProtoDTO> entityDetailsProto = startupScriptConfiguration == null
        ? Set.of()
        : entityReferenceExtractorUtils.extractReferredEntities(ambiance, startupScriptConfiguration);

    List<EntityDetail> entityDetails =
        entityDetailProtoToRestMapper.createEntityDetailsDTO(new ArrayList<>(emptyIfNull(entityDetailsProto)));

    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, true);
  }
  private void saveExecutionLog(NGLogCallback logCallback, String line) {
    if (logCallback != null) {
      logCallback.saveExecutionLog(line);
    }
  }
}
