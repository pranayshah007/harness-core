/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.EcsServiceSpec;
import io.harness.cdng.service.beans.GoogleCloudFunctionsServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.service.steps.sweepingoutput.EcsServiceCustomSweepingOutput;
import io.harness.cdng.service.steps.sweepingoutput.GoogleFunctionsServiceCustomSweepingOutput;
import io.harness.ng.core.service.yaml.NGServiceConfig;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class ServiceCustomSweepingOutputHelper {
  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  public void saveAdditionalServiceFieldsToSweepingOutput(NGServiceConfig ngServiceConfig, Ambiance ambiance) {
    if (ngServiceConfig == null || ngServiceConfig.getNgServiceV2InfoConfig() == null) {
      log.warn("No service configuration found");
      return;
    }
    ServiceDefinition serviceDefinition = ngServiceConfig.getNgServiceV2InfoConfig().getServiceDefinition();
    if (serviceDefinition == null || serviceDefinition.getServiceSpec() == null) {
      log.warn("No service definition found");
      return;
    }
    if (serviceDefinition.getServiceSpec() instanceof EcsServiceSpec) {
      EcsServiceSpec ecsServiceSpec = (EcsServiceSpec) serviceDefinition.getServiceSpec();
      saveAdditionalEcsServiceFieldsToSweepingOutput(ecsServiceSpec, ambiance);
    } else if (serviceDefinition.getServiceSpec() instanceof GoogleCloudFunctionsServiceSpec) {
      GoogleCloudFunctionsServiceSpec googleCloudFunctionsServiceSpec =
          (GoogleCloudFunctionsServiceSpec) serviceDefinition.getServiceSpec();
      saveAdditionalGoogleFunctionServiceFieldsToSweepingOutput(googleCloudFunctionsServiceSpec, ambiance);
    }
  }

  private void saveAdditionalEcsServiceFieldsToSweepingOutput(EcsServiceSpec ecsServiceSpec, Ambiance ambiance) {
    if (ecsServiceSpec.getEcsTaskDefinitionArn() == null
        || ecsServiceSpec.getEcsTaskDefinitionArn().fetchFinalValue() == null) {
      log.info("No task arn found in ecs service");
      return;
    }
    EcsServiceCustomSweepingOutput ecsServiceCustomSweepingOutput =
        EcsServiceCustomSweepingOutput.builder()
            .ecsTaskDefinitionArn(ecsServiceSpec.getEcsTaskDefinitionArn().fetchFinalValue().toString())
            .build();
    sweepingOutputService.consume(ambiance, ServiceStepV3Constants.ECS_SERVICE_SWEEPING_OUTPUT,
        ecsServiceCustomSweepingOutput, StepCategory.STAGE.name());
  }

  private void saveAdditionalGoogleFunctionServiceFieldsToSweepingOutput(
      GoogleCloudFunctionsServiceSpec googleCloudFunctionsServiceSpec, Ambiance ambiance) {
    if (googleCloudFunctionsServiceSpec.getEnvironmentType() == null
        || googleCloudFunctionsServiceSpec.getEnvironmentType().fetchFinalValue() == null) {
      log.info("No env type found in google function service");
      return;
    }
    GoogleFunctionsServiceCustomSweepingOutput googleFunctionsServiceCustomSweepingOutput =
        GoogleFunctionsServiceCustomSweepingOutput.builder()
            .environmentType(googleCloudFunctionsServiceSpec.getEnvironmentType().fetchFinalValue().toString())
            .build();
    sweepingOutputService.consume(ambiance, ServiceStepV3Constants.GOOGLE_FUNCTION_SERVICE_SWEEPING_OUTPUT,
        googleFunctionsServiceCustomSweepingOutput, StepCategory.STAGE.name());
  }
}
