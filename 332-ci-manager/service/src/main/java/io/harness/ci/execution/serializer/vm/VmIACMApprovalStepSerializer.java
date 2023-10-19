/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.serializer.vm;

import io.harness.beans.steps.stepinfo.IACMApprovalInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.ci.execution.execution.CIExecutionConfigService;
import io.harness.ci.execution.integrationstage.IntegrationStageUtils;
import io.harness.ci.execution.serializer.SerializerUtils;
import io.harness.ci.execution.utils.HarnessImageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep.VmPluginStepBuilder;
import io.harness.exception.ngexception.IACMStageExecutionException;
import io.harness.iacm.execution.IACMStepsUtils;
import io.harness.iacmserviceclient.IACMServiceUtils;
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
public class VmIACMApprovalStepSerializer {
  @Inject private CIExecutionConfigService ciExecutionConfigService;
  @Inject private HarnessImageUtils harnessImageUtils;
  @Inject IACMServiceUtils iacmServiceUtils;
  @Inject IACMStepsUtils iacmStepsUtils;
  @Inject private SerializerUtils serializerUtils;

  public VmPluginStep serialize(Ambiance ambiance, IACMApprovalInfo stepInfo, StageInfraDetails stageInfraDetails,
      ParameterField<Timeout> parameterFieldTimeout) {
    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, stepInfo.getDefaultTimeout());
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    Map<String, String> envVars = stepInfo.getEnvVariables().getValue();
    envVars.put("PLUGIN_ENDPOINT_VARIABLES",
        iacmStepsUtils.populatePipelineIds(ambiance, envVars.get("PLUGIN_ENDPOINT_VARIABLES")));
    envVars.put("PLUGIN_AUTO_APPROVE", stepInfo.getAutoApprove().getValue().toString());

    Map<String, String> statusEnvVars = serializerUtils.getStepStatusEnvVars(ambiance);
    envVars.putAll(statusEnvVars);
    String image;
    if (stepInfo.getImage().getValue() != null) {
      image = stepInfo.getImage().getValue();
    } else {
      image = ciExecutionConfigService.getPluginVersionForVM(
          stepInfo.getNonYamlInfo().getStepInfoType(), ngAccess.getAccountIdentifier());
    }

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
