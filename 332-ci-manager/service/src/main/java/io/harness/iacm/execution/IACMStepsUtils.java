/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.execution;

import static io.harness.ci.commonconstants.CIExecutionConstants.EVALUATE;
import static io.harness.ci.commonconstants.CIExecutionConstants.EXECUTE;
import static io.harness.ci.commonconstants.CIExecutionConstants.INITIALISE;
import static io.harness.ci.commonconstants.CIExecutionConstants.STACK_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.TEARDOWN;
import static io.harness.ci.commonconstants.CIExecutionConstants.WORKFLOW;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.beans.entities.Stack;
import io.harness.beans.entities.StackVariables;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.PluginSettingUtils;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.utils.HarnessImageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.exception.ngexception.IACMStageExecutionException;
import io.harness.iacmserviceclient.IACMServiceUtils;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.TimeoutUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class IACMStepsUtils {
  @Inject private CIExecutionConfigService ciExecutionConfigService;
  @Inject ConnectorUtils connectorUtils;
  @Inject HarnessImageUtils harnessImageUtils;
  @Inject IACMServiceUtils iacmServiceUtils;
  private Stack getIACMStackInfo(String org, String projectId, String accountId, String stackID) {
    Stack stack = iacmServiceUtils.getIACMStackInfo(org, projectId, accountId, stackID);
    if (stack == null) {
      throw new IACMStageExecutionException("Unable to retrieve the stack information for the stack " + stackID);
    }
    return stack;
  }

  private String getTerraformEndpointsInfo(Ambiance ambiance, String stackId) {
    return iacmServiceUtils.GetTerraformEndpointsData(ambiance, stackId);
  }

  private void createExecution(Ambiance ambiance, String stackId, String action) {
    iacmServiceUtils.createIACMExecution(ambiance, stackId, action);
  }

  private Map<String, String> getStackVariables(Ambiance ambiance, String org, String projectId, String accountId,
      String stackID, String command, Stack stackInfo) {
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
    env.put("OPERATIONS", command);

    Map<String, String> envVars = prepareEnvsMaps(env, pluginEnvPrefix);
    envVars.putAll(prepareEnvsMaps(envSecrets, "ENV_SECRETS_"));
    envVars.putAll(prepareEnvsMaps(tfVarsSecrets, "TFVARS_SECRETS_"));
    envVars.putAll(prepareEnvsMaps(tfVars, tfEnvPrefix));
    return envVars;
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

  private StackVariables[] getIACMStackVariables(String org, String projectId, String accountId, String stackID) {
    return iacmServiceUtils.getIacmStackEnvs(org, projectId, accountId, stackID);
  }

  private String selectPluginImage(NGAccess ngAccess, String provisioner) {
    switch (provisioner) {
      case "terraform":
        return ciExecutionConfigService.getPluginVersionForVM(
            CIStepInfoType.IACM_TERRAFORM, ngAccess.getAccountIdentifier());
      default:
        return null;
    }
  }
  public VmPluginStep injectIACMInfo(Ambiance ambiance, PluginStepInfo stepInfo, StageInfraDetails stageInfraDetails,
      ParameterField<Timeout> parameterFieldTimeout) {
    long timeout = TimeoutUtils.getTimeoutInSeconds(parameterFieldTimeout, stepInfo.getDefaultTimeout());

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String stackId = stepInfo.getEnvVariables().getValue().get(STACK_ID).getValue();
    String workflow = stepInfo.getEnvVariables().getValue().get(WORKFLOW).getValue();

    Stack stackInfo = getIACMStackInfo(
        ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), ngAccess.getAccountIdentifier(), stackId);

    createExecution(ambiance, stackInfo.getIdentifier(), workflow);

    String command = "";
    switch (stepInfo.getUses().getValue()) {
      case INITIALISE:
        command = INITIALISE;
        break;
      case EVALUATE:
        if (Objects.equals(workflow, TEARDOWN)) {
          command = "plan-destroy";
        } else {
          command = "plan";
        }
        break;
      case EXECUTE:
        if (Objects.equals(workflow, TEARDOWN)) {
          command = "destroy";
        } else {
          command = "apply";
        }
        break;
    }

    Map<String, String> envVars = getStackVariables(ambiance, ngAccess.getOrgIdentifier(),
        ngAccess.getProjectIdentifier(), ngAccess.getAccountIdentifier(), stackId, command, stackInfo);

    String image = selectPluginImage(ngAccess, stackInfo.getProvisioner());

    ConnectorDetails harnessInternalImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForVM(ngAccess, stageInfraDetails);

    VmPluginStep.VmPluginStepBuilder vmPluginStepBuilder =
        VmPluginStep.builder()
            .image(IntegrationStageUtils.getFullyQualifiedImageName(image, harnessInternalImageConnector))
            .envVariables(envVars)
            .timeoutSecs(timeout)
            .imageConnector(harnessInternalImageConnector);

    if (stackInfo.getProvider_connector() != null) {
      Map<EnvVariableEnum, String> connectorSecretEnvMap = null;
      if (stackInfo.getProvisioner().equals("terraform")) {
        connectorSecretEnvMap = PluginSettingUtils.getConnectorSecretEnvMap(CIStepInfoType.IACM_TERRAFORM);
      }
      ConnectorDetails connectorDetails =
          connectorUtils.getConnectorDetails(ngAccess, stackInfo.getProvider_connector());
      connectorDetails.setEnvToSecretsMap(connectorSecretEnvMap);
      vmPluginStepBuilder.connector(connectorDetails);
    }

    return vmPluginStepBuilder.build();
  }
}
