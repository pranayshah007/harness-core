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
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.PluginSettingUtils;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.utils.HarnessImageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.exception.ngexception.IACMStageExecutionException;
import io.harness.iacmserviceclient.IACMServiceUtils;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.fasterxml.jackson.databind.node.TextNode;
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

  public void createExecution(Ambiance ambiance, String stackId, String action) {
    iacmServiceUtils.createIACMExecution(ambiance, stackId, action);
  }

  private Map<String, String> getStackVariables(Ambiance ambiance, String org, String projectId, String accountId,
      String stackID, String command, Stack stackInfo) {
    String pluginEnvPrefix = "PLUGIN_";

    StackVariables[] variables = getIACMStackVariables(org, projectId, accountId, stackID);
    HashMap<String, String> pluginEnvs = new HashMap<>();

    HashMap<String, String> env = new HashMap<>();
    HashMap<String, String> tfInputEnvs = new HashMap<>();

    for (StackVariables variable : variables) {
      switch (variable.getKind()) {
        case "env":
          if (Objects.equals(variable.getValue_type(), "secret")) {
            env.put(variable.getKey(), "${ngSecretManager.obtain(\"" + variable.getKey() + "\", -871314908)}");
          } else {
            env.put(variable.getKey(), variable.getValue());
          }
          break;
        case "tf":
          if (Objects.equals(variable.getValue_type(), "secret")) {
            tfInputEnvs.put(variable.getKey(), "${ngSecretManager.obtain(\"" + variable.getKey() + "\", -871314908)}");
          } else {
            tfInputEnvs.put(variable.getKey(), variable.getValue());
          }
          break;
        default:
          break;
      }
    }

    // Plugin system env variables
    pluginEnvs.put("ROOT_DIR", stackInfo.getRepository_path());
    pluginEnvs.put("TF_VERSION", stackInfo.getProvisioner_version());
    pluginEnvs.put("ENDPOINT_VARIABLES", getTerraformEndpointsInfo(ambiance, stackID));
    pluginEnvs.put("VARS", transformMapToString(tfInputEnvs));
    pluginEnvs.put("ENV_VARS", transformMapToString(env));

    if (!Objects.equals(command, "")) {
      pluginEnvs.put("OPERATIONS", command);
    }
    return prepareEnvsMaps(pluginEnvs, pluginEnvPrefix);
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

  // This will be expanded eventually I guess when we start to have more plugins
  private String selectTypeOfPlugin(PluginStepInfo stepInfo) {
    TextNode operationTextNode = (TextNode) stepInfo.getSettings().get("operation");

    if (operationTextNode == null) {
      return "";
    }

    return operationTextNode.asText();
  }

  private String selectPluginImage(NGAccess ngAccess, PluginStepInfo stepInfo) {
    if (stepInfo.getImage() != null && stepInfo.getImage().getValue() != null) {
      return stepInfo.getImage().getValue();
    }
    String type = selectTypeOfPlugin(stepInfo);
    switch (type) {
      case INITIALISE:
      case EVALUATE:
      case EXECUTE:
        return ciExecutionConfigService.getPluginVersionForVM(
            CIStepInfoType.IACM_TERRAFORM, ngAccess.getAccountIdentifier());
      default:
        return null;
    }
  }

  public Map<String, String> getIACMEnvVariables(Ambiance ambiance, PluginStepInfo stepInfo) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String stackId = stepInfo.getEnvVariables().getValue().get(STACK_ID).getValue();
    String workflow = stepInfo.getEnvVariables().getValue().get(WORKFLOW).getValue();

    Stack stackInfo = getIACMStackInfo(
        ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), ngAccess.getAccountIdentifier(), stackId);

    String command = extractCommand(stepInfo, workflow);

    return getStackVariables(ambiance, ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(),
        ngAccess.getAccountIdentifier(), stackId, command, stackInfo);
  }

  public String retrieveIACMPluginImage(Ambiance ambiance, PluginStepInfo stepInfo) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    return selectPluginImage(ngAccess, stepInfo);
  }

  public boolean isIACMStep(PluginStepInfo pluginStepInfo) {
    return pluginStepInfo.getEnvVariables() != null && pluginStepInfo.getEnvVariables().getValue() != null
        && pluginStepInfo.getEnvVariables().getValue().get(STACK_ID) != null
        && !Objects.equals(pluginStepInfo.getEnvVariables().getValue().get(STACK_ID).getValue(), "");
  }

  public ConnectorDetails retrieveIACMConnectorDetails(Ambiance ambiance, PluginStepInfo stepInfo) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String stackId = stepInfo.getEnvVariables().getValue().get(STACK_ID).getValue();
    Stack stackInfo = getIACMStackInfo(
        ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), ngAccess.getAccountIdentifier(), stackId);
    if (stackInfo.getProvider_connector() != null) {
      Map<EnvVariableEnum, String> connectorSecretEnvMap = null;
      if (stackInfo.getProvisioner().equals("terraform")) {
        connectorSecretEnvMap = PluginSettingUtils.getConnectorSecretEnvMap(CIStepInfoType.IACM_TERRAFORM);
      }
      ConnectorDetails connectorDetails =
          connectorUtils.getConnectorDetails(ngAccess, stackInfo.getProvider_connector());
      connectorDetails.setEnvToSecretsMap(connectorSecretEnvMap);
      return connectorDetails;
    }
    return null;
  }

  private String transformMapToString(Map<String, String> originalMap) {
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    for (Map.Entry<String, String> entry : originalMap.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      String transformedValue = String.format("\"%s\"", value);
      sb.append(String.format("\"%s\":%s,", key, transformedValue));
    }
    if (sb.length() > 1) {
      sb.deleteCharAt(sb.length() - 1);
    }
    sb.append('}');
    return sb.toString();
  }

  private String extractCommand(PluginStepInfo stepInfo, String workflow) {
    String command;
    TextNode operationTextNode = (TextNode) stepInfo.getSettings().get("operation");

    if (operationTextNode == null) {
      return "";
    }

    String operation = operationTextNode.asText();
    switch (operation) {
      case INITIALISE:
        command = INITIALISE;
        break;
      case EVALUATE:
        if (Objects.equals(workflow, TEARDOWN)) {
          command = "evaluate-plan-destroy";
        } else {
          command = "evaluate-plan";
        }
        break;
      case EXECUTE:
        if (Objects.equals(workflow, TEARDOWN)) {
          command = "execute-destroy";
        } else {
          command = "execute-apply";
        }
        break;
      default:
        command = "";
    }
    return command;
  }
}
