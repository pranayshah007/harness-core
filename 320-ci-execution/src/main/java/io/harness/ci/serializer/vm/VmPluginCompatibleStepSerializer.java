/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer.vm;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.execution.CIExecutionConfigService;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.stateutils.buildstate.ConnectorUtils;
import io.harness.stateutils.buildstate.PluginSettingUtils;
import io.harness.steps.CIStepInfoUtils;
import io.harness.util.HarnessImageUtils;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;

@Singleton
public class VmPluginCompatibleStepSerializer {
  @Inject private CIExecutionConfigService ciExecutionConfigService;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private HarnessImageUtils harnessImageUtils;

  public VmPluginStep serialize(Ambiance ambiance, PluginCompatibleStep pluginCompatibleStep,
      VmStageInfraDetails vmStageInfraDetails, String identifier, ParameterField<Timeout> parameterFieldTimeout,
      String stepName) {
    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, pluginCompatibleStep.getDefaultTimeout());
    Map<String, String> envVars =
        PluginSettingUtils.getPluginCompatibleEnvVariables(pluginCompatibleStep, identifier, timeout, Type.VM);
    String image = CIStepInfoUtils.getPluginCustomStepImage(
        pluginCompatibleStep, ciExecutionConfigService, Type.VM, AmbianceUtils.getAccountId(ambiance));

    String connectorRef = PluginSettingUtils.getConnectorRef(pluginCompatibleStep);
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    Map<EnvVariableEnum, String> connectorSecretEnvMap =
        PluginSettingUtils.getConnectorSecretEnvMap(pluginCompatibleStep.getNonYamlInfo().getStepInfoType());
    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorRef);
    connectorDetails.setEnvToSecretsMap(connectorSecretEnvMap);

    ConnectorDetails harnessInternalImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForVM(ngAccess, vmStageInfraDetails);
    return VmPluginStep.builder()
        .image(IntegrationStageUtils.getFullyQualifiedImageName(image, harnessInternalImageConnector))
        .connector(connectorDetails)
        .envVariables(envVars)
        .timeoutSecs(timeout)
        .imageConnector(harnessInternalImageConnector)
        .build();
  }
}
