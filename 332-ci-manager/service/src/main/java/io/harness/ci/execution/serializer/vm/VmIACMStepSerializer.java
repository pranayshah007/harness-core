/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer.vm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.entities.Stack;
import io.harness.beans.entities.StackVariables;
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
import java.util.Objects;

@Singleton
public class VmIACMStepSerializer {
  @Inject private CIExecutionConfigService ciExecutionConfigService;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private HarnessImageUtils harnessImageUtils;
  @Inject IACMServiceUtils iacmServiceUtils;

  public VmPluginStep serialize(Ambiance ambiance, IACMTerraformPlanInfo stepInfo, StageInfraDetails stageInfraDetails,
      String identifier, ParameterField<Timeout> parameterFieldTimeout) {
    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, stepInfo.getDefaultTimeout());
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    Stack stackInfo = getIACMStackInfo(ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(),
        ngAccess.getAccountIdentifier(), stepInfo.getStackID());

    Map<String, String> envVars = getStackVariables(ambiance, ngAccess.getOrgIdentifier(),
        ngAccess.getProjectIdentifier(), ngAccess.getAccountIdentifier(), stepInfo.getStackID(), stackInfo);

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

    if (stackInfo.getProvider_connector() != null) {
      Map<EnvVariableEnum, String> connectorSecretEnvMap =
          PluginSettingUtils.getConnectorSecretEnvMap(stepInfo.getNonYamlInfo().getStepInfoType());
      ConnectorDetails connectorDetails =
          connectorUtils.getConnectorDetails(ngAccess, stackInfo.getProvider_connector());
      connectorDetails.setEnvToSecretsMap(connectorSecretEnvMap);
      vmPluginStepBuilder.connector(connectorDetails);
    }

    return vmPluginStepBuilder.build();
  }

  private Map<String, String> getStackVariables(
      Ambiance ambiance, String org, String projectId, String accountId, String stackID, Stack stackInfo) {
    String pluginEnvPrefix = "PLUGIN_";
    String tfEnvPrefix = "TF_";

    StackVariables[] variables = getIACMStackVariables(org, projectId, accountId, stackID);
    HashMap<String, String> env = new HashMap<>();

    HashMap<String, String> envSecrets = new HashMap<>();
    HashMap<String, String> tfVars = new HashMap<>();
    HashMap<String, String> tfVarsSecrets = new HashMap<>();

    for (StackVariables variable : variables) {
      switch (variable.getKind()) {
        case "env":
          if (Objects.equals(variable.getValue_type(), "secret")) {
            envSecrets.put(variable.getKey(), "${ngSecretManager.obtain(\"" + variable.getKey() + "\", -871314908)})}");
          } else {
            env.put(variable.getKey(), variable.getValue());
          }
          break;
        case "tf":
          if (Objects.equals(variable.getValue_type(), "secret")) {
            tfVarsSecrets.put(
                variable.getKey(), "${ngSecretManager.obtain(\"" + variable.getKey() + "\", -871314908)})}");
          } else {
            tfVars.put(variable.getKey(), variable.getValue());
          }
          break;
        default:
          break;
      }
    }

    // Plugin system env variables
    env.put("ROOT_DIR", stackInfo.getRepository_path());
    env.put("TF_VERSION", stackInfo.getProvisioner_version());
    env.put("ENDPOINT_VARIABLES", getTerraformEndpointsInfo(ambiance, stackID));

    Map<String, String> envVars = prepareEnvsMaps(env, pluginEnvPrefix);
    envVars.putAll(prepareEnvsMaps(envSecrets, "ENV_SECRETS_"));
    envVars.putAll(prepareEnvsMaps(tfVarsSecrets, "TFVARS_SECRETS_"));
    envVars.putAll(prepareEnvsMaps(tfVars, tfEnvPrefix));
    return envVars;
  }

  /**
   * getIACMEnvVariables will extract the StackID from the IACMConnectorStep if the step is available.
   * Once that is done, it will try to query the IACM Server and will try to get the connectorRef that belongs to
   * the stackID. Once the connectorRef is retrieved from the IACM Server, it will then try to retrieve the credentials
   * and inject those as env variables in the container so they are available to the plugins steps.
   */
  public Stack getIACMStackInfo(String org, String projectId, String accountId, String stackID) {
    Stack stack = iacmServiceUtils.getIACMStackInfo(org, projectId, accountId, stackID);
    if (stack == null) {
      throw new IACMStageExecutionException("Unable to retrieve the stack information for the stack " + stackID);
    }
    return stack;
  }

  public StackVariables[] getIACMStackVariables(String org, String projectId, String accountId, String stackID) {
    return iacmServiceUtils.getIacmStackEnvs(org, projectId, accountId, stackID);
  }

  Map<String, String> prepareEnvsMaps(Map<String, String> envs, String prefix) {
    Map<String, String> envVars = new HashMap<>();
    if (!isEmpty(envs)) {
      for (Map.Entry<String, String> entry : envs.entrySet()) {
        String key = prefix + entry.getKey();
        envVars.put(key, entry.getValue());
      }
    }
    return envVars;
  }

  private String getTerraformEndpointsInfo(Ambiance ambiance, String stackId) {
    return iacmServiceUtils.GetTerraformEndpointsData(ambiance, stackId);
  }
}
