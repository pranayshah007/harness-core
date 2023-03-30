/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer.vm;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.PluginSettingUtils;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.utils.CIStepInfoUtils;
import io.harness.ci.utils.HarnessImageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep.VmPluginStepBuilder;
import io.harness.delegate.beans.ci.vm.steps.VmRunStep;
import io.harness.delegate.beans.ci.vm.steps.VmStepInfo;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Arrays;
import java.util.Map;

@Singleton
public class VmPluginCompatibleStepSerializer {
  @Inject private CIExecutionConfigService ciExecutionConfigService;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private HarnessImageUtils harnessImageUtils;
  @Inject private PluginSettingUtils pluginSettingUtils;
  @Inject private CIFeatureFlagService featureFlagService;

  public VmStepInfo serialize(Ambiance ambiance, PluginCompatibleStep pluginCompatibleStep,
      StageInfraDetails stageInfraDetails, String identifier, ParameterField<Timeout> parameterFieldTimeout,
      String stepName) {
    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, pluginCompatibleStep.getDefaultTimeout());
    Map<String, String> envVars = pluginSettingUtils.getPluginCompatibleEnvVariables(
        pluginCompatibleStep, identifier, timeout, ambiance, Type.VM, true);
    if (CIStepInfoUtils.canRunVmStepOnHost(pluginCompatibleStep.getNonYamlInfo().getStepInfoType(), stageInfraDetails,
            AmbianceUtils.getAccountId(ambiance), ciExecutionConfigService, featureFlagService)) {
      return getHostedStep(ambiance, pluginCompatibleStep, envVars, timeout);
    }
    return getContainerizedStep(ambiance, pluginCompatibleStep, stageInfraDetails, envVars, timeout);
  }

  private VmRunStep getHostedStep(
      Ambiance ambiance, PluginCompatibleStep pluginCompatibleStep, Map<String, String> envVars, long timeout) {
    String name = ciExecutionConfigService.getContainerlessPluginNameForVM(
        pluginCompatibleStep.getNonYamlInfo().getStepInfoType());
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return VmRunStep.builder()
        .entrypoint(Arrays.asList("plugin", "-kind", "harness", "-name", name))
        .envVariables(envVars)
        .timeoutSecs(timeout)
        .connector(getStepConnectorDetails(ngAccess, pluginCompatibleStep))
        .build();
  }

  private VmPluginStep getContainerizedStep(Ambiance ambiance, PluginCompatibleStep pluginCompatibleStep,
      StageInfraDetails stageInfraDetails, Map<String, String> envVars, long timeout) {
    String image = CIStepInfoUtils.getPluginCustomStepImage(
        pluginCompatibleStep, ciExecutionConfigService, Type.VM, AmbianceUtils.getAccountId(ambiance));
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    ConnectorDetails harnessInternalImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForVM(ngAccess, stageInfraDetails);
    VmPluginStepBuilder vmPluginStepBuilder =
        VmPluginStep.builder()
            .image(IntegrationStageUtils.getFullyQualifiedImageName(image, harnessInternalImageConnector))
            .envVariables(envVars)
            .timeoutSecs(timeout)
            .imageConnector(harnessInternalImageConnector)
            .connector(getStepConnectorDetails(ngAccess, pluginCompatibleStep));
    return vmPluginStepBuilder.build();
  }

  private ConnectorDetails getStepConnectorDetails(NGAccess ngAccess, PluginCompatibleStep pluginCompatibleStep) {
    String connectorRef = PluginSettingUtils.getConnectorRef(pluginCompatibleStep);
    if (connectorRef == null) {
      return null;
    }

    Map<EnvVariableEnum, String> connectorSecretEnvMap =
        PluginSettingUtils.getConnectorSecretEnvMap(pluginCompatibleStep.getNonYamlInfo().getStepInfoType());
    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorRef);
    connectorDetails.setEnvToSecretsMap(connectorSecretEnvMap);
    return connectorDetails;
  }
}
