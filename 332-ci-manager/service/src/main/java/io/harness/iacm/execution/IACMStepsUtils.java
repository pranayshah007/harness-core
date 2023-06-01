/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.execution;

import static io.harness.ci.commonconstants.CIExecutionConstants.WORKSPACE_ID;

import io.harness.beans.entities.Workspace;
import io.harness.beans.entities.WorkspaceVariables;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.IACMTerraformPluginInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.PluginSettingUtils;
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
  @Inject ConnectorUtils connectorUtils;
  @Inject IACMServiceUtils iacmServiceUtils;

  private Workspace getIACMWorkspaceInfo(String org, String projectId, String accountId, String workspaceID) {
    Workspace workspaceInfo = iacmServiceUtils.getIACMWorkspaceInfo(org, projectId, accountId, workspaceID);
    if (workspaceInfo == null) {
      throw new IACMStageExecutionException(
          "Unable to retrieve the workspaceInfo information for the workspace " + workspaceID);
    }
    return workspaceInfo;
  }

  private String getTerraformEndpointsInfo(Ambiance ambiance, String workspaceId) {
    return iacmServiceUtils.GetTerraformEndpointsData(ambiance, workspaceId);
  }

  public void createExecution(Ambiance ambiance, String workspaceId) {
    iacmServiceUtils.createIACMExecution(ambiance, workspaceId);
  }

  private Map<String, String> getWorkspaceVariables(Ambiance ambiance, String org, String projectId, String accountId,
      String workspaceID, String command, Workspace workspaceInfo) {
    WorkspaceVariables[] variables = getIACMWorkspaceVariables(org, projectId, accountId, workspaceID);
    HashMap<String, String> pluginEnvs = new HashMap<>();

    // Plugin system env variables
    pluginEnvs.put("PLUGIN_ROOT_DIR", workspaceInfo.getRepository_path());
    pluginEnvs.put("PLUGIN_TF_VERSION", workspaceInfo.getProvisioner_version());
    pluginEnvs.put("PLUGIN_ENDPOINT_VARIABLES", getTerraformEndpointsInfo(ambiance, workspaceID));

    if (!Objects.equals(command, "")) {
      pluginEnvs.put("PLUGIN_COMMAND", command);
    }

    for (WorkspaceVariables variable : variables) {
      switch (variable.getKind()) {
        case "env":
          if (Objects.equals(variable.getValue_type(), "secret")) {
            pluginEnvs.put("PLUGIN_WS_ENV_VAR_" + variable.getKey(),
                "${ngSecretManager.obtain(\"" + variable.getValue() + "\", " + ambiance.getExpressionFunctorToken()
                    + ")}");
          } else {
            pluginEnvs.put("PLUGIN_WS_ENV_VAR_" + variable.getKey(), variable.getValue());
          }
          break;
        case "tf":
          if (Objects.equals(variable.getValue_type(), "secret")) {
            pluginEnvs.put("PLUGIN_WS_TF_VAR_" + variable.getKey(),
                "${ngSecretManager.obtain(\"" + variable.getValue() + "\", " + ambiance.getExpressionFunctorToken()
                    + ")}");
          } else {
            pluginEnvs.put("PLUGIN_WS_TF_VAR_" + variable.getKey(), variable.getValue());
          }
          break;
        default:
          break;
      }
    }

    return pluginEnvs;
  }

  private WorkspaceVariables[] getIACMWorkspaceVariables(
      String org, String projectId, String accountId, String workspaceID) {
    return iacmServiceUtils.getIacmWorkspaceEnvs(org, projectId, accountId, workspaceID);
  }

  // Extract the keyword operation
  private String extractOperation(PluginStepInfo stepInfo) {
    TextNode operationTextNode = (TextNode) stepInfo.getSettings().get("command");

    if (operationTextNode == null) {
      return "";
    }

    return operationTextNode.asText();
  }

  public Map<String, String> getIACMEnvVariables(Ambiance ambiance, PluginStepInfo stepInfo) {
    String workspaceId = stepInfo.getEnvVariables().getValue().get(WORKSPACE_ID).getValue();
    String command = extractOperation(stepInfo);
    return buildIACMEnvVariables(ambiance, workspaceId, command);
  }

  private Map<String, String> buildIACMEnvVariables(Ambiance ambiance, String workspaceId, String command) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    Workspace workspaceInfo = getIACMWorkspaceInfo(
        ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), ngAccess.getAccountIdentifier(), workspaceId);

    createExecution(ambiance, workspaceId);

    return getWorkspaceVariables(ambiance, ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(),
        ngAccess.getAccountIdentifier(), workspaceId, command, workspaceInfo);
  }

  public Map<String, String> getIACMEnvVariables(Ambiance ambiance, IACMTerraformPluginInfo stepInfo) {
    String workspaceId = stepInfo.getWorkspace();
    String command = stepInfo.getCommand().getValue();
    return buildIACMEnvVariables(ambiance, workspaceId, command);
  }

  public boolean isIACMStep(PluginStepInfo pluginStepInfo) {
    return pluginStepInfo.getEnvVariables() != null && pluginStepInfo.getEnvVariables().getValue() != null
        && pluginStepInfo.getEnvVariables().getValue().get(WORKSPACE_ID) != null
        && !Objects.equals(pluginStepInfo.getEnvVariables().getValue().get(WORKSPACE_ID).getValue(), "");
  }

  public ConnectorDetails retrieveIACMConnectorDetails(Ambiance ambiance, PluginStepInfo stepInfo) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String workspaceId = stepInfo.getEnvVariables().getValue().get(WORKSPACE_ID).getValue();
    Workspace workspaceInfo = getIACMWorkspaceInfo(
        ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), ngAccess.getAccountIdentifier(), workspaceId);
    if (workspaceInfo.getProvider_connector() != null) {
      Map<EnvVariableEnum, String> connectorSecretEnvMap = null;
      if (workspaceInfo.getProvisioner().equals("terraform")) {
        connectorSecretEnvMap = PluginSettingUtils.getConnectorSecretEnvMap(CIStepInfoType.IACM_TERRAFORM_PLUGIN);
      }
      ConnectorDetails connectorDetails =
          connectorUtils.getConnectorDetails(ngAccess, workspaceInfo.getProvider_connector());
      connectorDetails.setEnvToSecretsMap(connectorSecretEnvMap);
      return connectorDetails;
    }
    return null;
  }

  public ConnectorDetails retrieveIACMConnectorDetails(Ambiance ambiance, IACMTerraformPluginInfo stepInfo) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String workspaceId = stepInfo.getWorkspace();
    Workspace workspaceInfo = getIACMWorkspaceInfo(
        ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), ngAccess.getAccountIdentifier(), workspaceId);
    if (workspaceInfo.getProvider_connector() != null) {
      Map<EnvVariableEnum, String> connectorSecretEnvMap = null;
      if (workspaceInfo.getProvisioner().equals("terraform")) {
        connectorSecretEnvMap = PluginSettingUtils.getConnectorSecretEnvMap(CIStepInfoType.IACM_TERRAFORM_PLUGIN);
      }
      ConnectorDetails connectorDetails =
          connectorUtils.getConnectorDetails(ngAccess, workspaceInfo.getProvider_connector());
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
}
