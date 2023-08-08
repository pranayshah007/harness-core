/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer.vm;

import io.harness.beans.steps.stepinfo.IACMTerraformPluginInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.ci.utils.HarnessImageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep.VmPluginStepBuilder;
import io.harness.exception.ngexception.IACMStageExecutionException;
import io.harness.iacm.execution.IACMStepsUtils;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;

@Singleton
public class VmIACMStepSerializer {
  @Inject private CIExecutionConfigService ciExecutionConfigService;
  @Inject private HarnessImageUtils harnessImageUtils;
  @Inject private IACMStepsUtils iacmStepsUtils;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private SerializerUtils serializerUtils;

  public VmPluginStep serialize(Ambiance ambiance, IACMTerraformPluginInfo stepInfo,
      StageInfraDetails stageInfraDetails, ParameterField<Timeout> parameterFieldTimeout) {
    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, stepInfo.getDefaultTimeout());

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String workspaceId = stepInfo.getWorkspace();

    Map<String, String> envVars = stepInfo.getEnvVariables().getValue();
    iacmStepsUtils.createExecution(ambiance, workspaceId);
    envVars = iacmStepsUtils.replaceExpressionFunctorToken(ambiance, envVars);
    envVars.put("PLUGIN_ENDPOINT_VARIABLES",
        iacmStepsUtils.populatePipelineIds(ambiance, envVars.get("PLUGIN_ENDPOINT_VARIABLES")));

    String image;
    if (stepInfo.getImage().getValue() != null) {
      image = stepInfo.getImage().getValue();
    } else {
      image = ciExecutionConfigService.getPluginVersionForVM(
          stepInfo.getNonYamlInfo().getStepInfoType(), ngAccess.getAccountIdentifier());
    }
    Map<String, String> statusEnvVars = serializerUtils.getStepStatusEnvVars(ambiance);
    envVars.putAll(statusEnvVars);

    ConnectorDetails harnessInternalImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForVM(ngAccess, stageInfraDetails);

    VmPluginStepBuilder vmPluginStepBuilder =
        VmPluginStep.builder()
            .image(IntegrationStageUtils.getFullyQualifiedImageName(image, harnessInternalImageConnector))
            .envVariables(envVars)
            .timeoutSecs(timeout)
            .imageConnector(harnessInternalImageConnector);

    String connectorRef;
    String provider;
    if (envVars.containsKey("PLUGIN_CONNECTOR_REF")) {
      connectorRef = envVars.get("PLUGIN_CONNECTOR_REF");
      envVars.remove("PLUGIN_CONNECTOR_REF");
    } else {
      throw new IACMStageExecutionException("The connector ref is missing. Check the workspace");
    }
    if (envVars.containsKey("PLUGIN_PROVISIONER")) {
      provider = envVars.get("PLUGIN_PROVISIONER");
      envVars.remove("PLUGIN_PROVISIONER");
    } else {
      throw new IACMStageExecutionException("The provisioner type is missing. Check the workspace");
    }
    vmPluginStepBuilder.connector(iacmStepsUtils.retrieveIACMConnectorDetails(ambiance, connectorRef, provider));

    return vmPluginStepBuilder.build();
  }
}