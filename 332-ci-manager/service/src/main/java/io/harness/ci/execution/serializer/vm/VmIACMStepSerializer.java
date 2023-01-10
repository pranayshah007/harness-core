/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer.vm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.IACMTerraformPlanInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.PluginSettingUtils;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.utils.HarnessImageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep.VmPluginStepBuilder;
import io.harness.exception.ngexception.IACMStageExecutionException;
import io.harness.iacmserviceclient.IACMServiceUtils;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class VmIACMStepSerializer {
  @Inject private CIExecutionConfigService ciExecutionConfigService;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private HarnessImageUtils harnessImageUtils;
  @Inject IACMServiceUtils iacmServiceUtils;

  public VmPluginStep serialize(Ambiance ambiance, IACMTerraformPlanInfo stepInfo, StageInfraDetails stageInfraDetails,
      String identifier, ParameterField<Timeout> parameterFieldTimeout) {
    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, stepInfo.getDefaultTimeout());
    Map<String, String> envVars = getTerraformPlanInfoEnvVariables(stepInfo, ambiance, identifier);
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String image = ciExecutionConfigService.getPluginVersionForVM(
        stepInfo.getNonYamlInfo().getStepInfoType(), ngAccess.getAccountIdentifier());

    ConnectorDetails harnessInternalImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForVM(ngAccess, stageInfraDetails);

    VmPluginStepBuilder vmPluginStepBuilder =
        VmPluginStep.builder()
            .image(IntegrationStageUtils.getFullyQualifiedImageName(image, harnessInternalImageConnector))
            .envVariables(envVars)
            .timeoutSecs(timeout)
            .imageConnector(harnessInternalImageConnector);

    String connectorRef = getIACMConnectorRef(stepInfo.getStackID());
    if (connectorRef != null) {
      Map<EnvVariableEnum, String> connectorSecretEnvMap =
          PluginSettingUtils.getConnectorSecretEnvMap(stepInfo.getNonYamlInfo().getStepInfoType());
      ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorRef);
      connectorDetails.setEnvToSecretsMap(connectorSecretEnvMap);
      vmPluginStepBuilder.connector(connectorDetails);
    }

    return vmPluginStepBuilder.build();
  }

  private Map<String, String> getTerraformPlanInfoEnvVariables(
      IACMTerraformPlanInfo stepInfo, Ambiance ambiance, String identifier) {
    String pluginEnvPrefix = "PLUGIN_";
    String tfEnvPrefix = "TF_";

    Map<String, String> envs =
        RunTimeInputHandler.resolveMapParameter("env", "IACMTerraformPlan", identifier, stepInfo.getEnv(), false);
    Map<String, String> envVars = prepareEnvsMaps(envs, pluginEnvPrefix);

    Map<String, String> tfVars =
        RunTimeInputHandler.resolveMapParameter("tfVars", "IACMTerraformPlan", identifier, stepInfo.getTfVars(), false);
    envVars.putAll(prepareEnvsMaps(tfVars, tfEnvPrefix));
    return envVars;
  }

  /**
   * getIACMEnvVariables will extract the StackID from the IACMConnectorStep if the step is available.
   * Once that is done, it will try to query the IACM Server and will try to get the connectorRef that belongs to
   * the stackID. Once the connectorRef is retrieved from the IACM Server, it will then try to retrieve the credentials
   * and inject those as env variables in the container so they are available to the plugins steps.
   */
  public String getIACMConnectorRef(String stackID) {
    String connectorRef = iacmServiceUtils.getIACMConnector(stackID);
    if (connectorRef.isEmpty()) {
      throw new IACMStageExecutionException("Unable to retrieve the stack information for the stack " + stackID);
    }
    return connectorRef;
  }

  Map<String, String> prepareEnvsMaps(Map<String, String> envs, String prefix) {
    Map<String, String> envVars = new HashMap<>();
    if (!isEmpty(envs)) {
      for (Map.Entry<String, String> entry : envs.entrySet()) {
        String key = prefix + entry.getKey().toUpperCase();
        envVars.put(key, entry.getValue());
      }
    }
    return envVars;
  }
}
