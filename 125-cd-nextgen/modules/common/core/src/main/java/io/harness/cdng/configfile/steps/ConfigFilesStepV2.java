/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.steps;

import static io.harness.cdng.service.steps.constants.ServiceStepV3Constants.SERVICE_CONFIG_FILES_SWEEPING_OUTPUT;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.configfile.mapper.ConfigFileOutcomeMapper;
import io.harness.cdng.configfile.validator.IndividualConfigFileStepValidator;
import io.harness.cdng.expressions.CDExpressionResolver;
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
import io.harness.validation.JavaxValidator;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Slf4j
public class ConfigFilesStepV2 extends AbstractConfigFileStep implements SyncExecutable<EmptyStepParameters> {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.CONFIG_FILES_V2.getName())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  @Inject private ExecutionSweepingOutputService sweepingOutputService;
  @Inject private CDExpressionResolver cdExpressionResolver;
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
    final NgConfigFilesMetadataSweepingOutput configFilesSweepingOutput =
        fetchConfigFilesMetadataFromSweepingOutput(ambiance);

    final List<ConfigFileWrapper> configFiles = configFilesSweepingOutput.getFinalSvcConfigFiles();
    final NGLogCallback logCallback = serviceStepsHelper.getServiceLogCallback(ambiance);
    if (EmptyPredicate.isEmpty(configFiles)) {
      logCallback.saveExecutionLog(
          String.format("No config files configured in the service. <+%s> expressions will not work",
              OutcomeExpressionConstants.CONFIG_FILES),
          LogLevel.WARN);
      return StepResponse.builder().status(Status.SKIPPED).build();
    }
    cdExpressionResolver.updateExpressions(ambiance, configFiles);

    JavaxValidator.validateOrThrow(new ConfigFileValidatorDTO(configFiles));
    checkForAccessOrThrow(ambiance, configFiles);

    final ConfigFilesOutcome configFilesOutcome = new ConfigFilesOutcome();
    for (int i = 0; i < configFiles.size(); i++) {
      ConfigFileWrapper file = configFiles.get(i);
      ConfigFileAttributes spec = file.getConfigFile().getSpec();
      String identifier = file.getConfigFile().getIdentifier();
      IndividualConfigFileStepValidator.validateConfigFileAttributes(identifier, spec, true);
      verifyConfigFileReference(identifier, spec, ambiance);
      configFilesOutcome.put(identifier, ConfigFileOutcomeMapper.toConfigFileOutcome(identifier, i + 1, spec));
    }

    sweepingOutputService.consume(
        ambiance, OutcomeExpressionConstants.CONFIG_FILES, configFilesOutcome, StepCategory.STAGE.name());

    return StepResponse.builder().status(Status.SUCCEEDED).build();
  }

  private NgConfigFilesMetadataSweepingOutput fetchConfigFilesMetadataFromSweepingOutput(Ambiance ambiance) {
    final OptionalSweepingOutput resolveOptional = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(SERVICE_CONFIG_FILES_SWEEPING_OUTPUT));
    if (!resolveOptional.isFound()) {
      log.info("Could not find configFileSweepingOutput for the stage.");
    }
    return resolveOptional.isFound() ? (NgConfigFilesMetadataSweepingOutput) resolveOptional.getOutput()
                                     : NgConfigFilesMetadataSweepingOutput.builder().build();
  }
  void checkForAccessOrThrow(Ambiance ambiance, List<ConfigFileWrapper> configFiles) {
    if (EmptyPredicate.isEmpty(configFiles)) {
      return;
    }
    List<EntityDetail> entityDetails = new ArrayList<>();

    for (ConfigFileWrapper configFile : configFiles) {
      Set<EntityDetailProtoDTO> entityDetailsProto =
          configFile == null ? Set.of() : entityReferenceExtractorUtils.extractReferredEntities(ambiance, configFile);
      List<EntityDetail> entityDetail =
          entityDetailProtoToRestMapper.createEntityDetailsDTO(new ArrayList<>(emptyIfNull(entityDetailsProto)));
      if (EmptyPredicate.isNotEmpty(entityDetail)) {
        entityDetails.addAll(entityDetail);
      }
    }
    pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, true);
  }

  @Data
  @Builder
  private static class ConfigFileValidatorDTO {
    @Valid List<ConfigFileWrapper> configFiles;
  }
}
