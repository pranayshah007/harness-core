/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import static io.harness.cdng.service.steps.constants.ServiceStepConstants.SERVICE_STEP_COMMAND_UNIT;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.USER_DATA;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import io.harness.cdng.CDStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.beans.AsgServiceSpec;
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

@Slf4j
public class AsgServiceSettingsStep implements SyncExecutable<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.ASG_SERVICE_SETTINGS_STEP.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Inject private ServiceStepsHelper serviceStepsHelper;
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
    if (!ServiceDefinitionType.ASG.getYamlName().equals(service.getServiceDefinition().getServiceSpec().getType())) {
      log.info("skipping AsgServiceSettingsStep because service is not of type Asg");
      return StepResponse.builder().status(Status.SKIPPED).build();
    }

    final AsgServiceSpec serviceSpec = (AsgServiceSpec) service.getServiceDefinition().getServiceSpec();

    expressionResolver.updateExpressions(ambiance, serviceSpec);

    final NGLogCallback logCallback =
        serviceStepsHelper.getServiceLogCallback(ambiance, false, SERVICE_STEP_COMMAND_UNIT);

    final List<StepResponse.StepOutcome> outcomes = new ArrayList<>();

    // Process Asg settings
    if (serviceSpec.getUserData() != null) {
      checkForAccessOrThrow(ambiance, serviceSpec.getUserData());
      outcomes.add(processUserData(ambiance, serviceSpec, logCallback));
    }

    return StepResponse.builder().status(Status.SUCCEEDED).stepOutcomes(outcomes).build();
  }

  private StepResponse.StepOutcome processUserData(
      Ambiance ambiance, AsgServiceSpec serviceSpec, NGLogCallback logCallback) {
    saveExecutionLog(logCallback, "Processing userData script...");
    StoreConfigWrapper storeConfig = serviceSpec.getUserData().getStore();
    saveExecutionLog(logCallback, "Processed userData script");
    return StepResponse.StepOutcome.builder()
        .name(USER_DATA)
        .outcome(UserDataOutcome.builder().store(storeConfig.getSpec()).build())
        .group(StepCategory.STAGE.name())
        .build();
  }
  private void checkForAccessOrThrow(Ambiance ambiance, UserDataConfiguration userDataConfiguration) {
    Set<EntityDetailProtoDTO> entityDetailsProto = userDataConfiguration == null
        ? Set.of()
        : entityReferenceExtractorUtils.extractReferredEntities(ambiance, userDataConfiguration);

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
