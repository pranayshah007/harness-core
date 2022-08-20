/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.steps;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.configfile.validator.IndividualConfigFileStepValidator.validateConfigFileAttributes;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.configfile.mapper.ConfigFileOutcomeMapper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.connector.services.ConnectorService;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.executables.SyncExecutable;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@Slf4j
public class IndividualConfigFileStep implements SyncExecutable<ConfigFileStepParameters> {
  private static final String OUTPUT = "output";
  public static final StepType STEP_TYPE =
      StepType.newBuilder().setType(ExecutionNodeType.CONFIG_FILE.getName()).setStepCategory(StepCategory.STEP).build();

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private ConfigFileStepUtils configFileStepUtils;

  @Override
  public Class<ConfigFileStepParameters> getStepParametersClass() {
    return ConfigFileStepParameters.class;
  }

  @Override
  public StepResponse executeSync(Ambiance ambiance, ConfigFileStepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ConfigFileAttributes finalConfigFile = configFileStepUtils.applyConfigFileOverrides(stepParameters);
    cdExpressionResolver.updateStoreConfigExpressions(ambiance, finalConfigFile.getStore().getValue());
    validateConfigFileAttributes(stepParameters.getIdentifier(), finalConfigFile, true);
    configFileStepUtils.verifyConfigFileReference(stepParameters.getIdentifier(), finalConfigFile, ambiance);
    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(OUTPUT)
                         .outcome(ConfigFileOutcomeMapper.toConfigFileOutcome(
                             stepParameters.getIdentifier(), stepParameters.getOrder(), finalConfigFile))
                         .build())
        .build();
  }
}
