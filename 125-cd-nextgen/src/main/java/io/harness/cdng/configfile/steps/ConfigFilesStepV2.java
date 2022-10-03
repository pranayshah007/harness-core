/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.cdng.configfile.mapper.ConfigFileOutcomeMapper;
import io.harness.cdng.configfile.validator.IndividualConfigFileStepValidator;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.service.steps.ServiceStepV3;
import io.harness.cdng.steps.EmptyStepParameters;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;

import com.google.inject.Inject;
import java.util.List;
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
    if (EmptyPredicate.isEmpty(configFiles)) {
      log.info("no config files found for service " + configFilesSweepingOutput.getServiceIdentifier()
          + ". skipping the config files step");
      return StepResponse.builder().status(Status.SKIPPED).build();
    }
    cdExpressionResolver.updateExpressions(ambiance, configFiles);

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
        ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3.SERVICE_CONFIG_FILES_SWEEPING_OUTPUT));
    if (!resolveOptional.isFound()) {
      log.info("Could not find configFileSweepingOutput for the stage.");
    }
    return resolveOptional.isFound() ? (NgConfigFilesMetadataSweepingOutput) resolveOptional.getOutput()
                                     : NgConfigFilesMetadataSweepingOutput.builder().build();
  }
}
