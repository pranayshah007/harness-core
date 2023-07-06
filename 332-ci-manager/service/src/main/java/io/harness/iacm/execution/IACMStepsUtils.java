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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

  private String getTerraformEndpointsInfo(String account, String org, String project, String workspaceId) {
    return iacmServiceUtils.GetTerraformEndpointsData(account, org, project, workspaceId);
  }

  public void createExecution(Ambiance ambiance, String workspaceId) {
    iacmServiceUtils.createIACMExecution(ambiance, workspaceId);
  }

  public String populatePipelineIds(Ambiance ambiance, String json) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode rootNode = mapper.readTree(json);

      if (rootNode.isObject()) {
        ObjectNode objectNode = (ObjectNode) rootNode;
        objectNode.put("pipeline_execution_id", ambiance.getPlanExecutionId());
        objectNode.put("pipeline_stage_execution_id", ambiance.getStageExecutionId());
      }

      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
    } catch (Exception e) {
      e.printStackTrace();
      return json;
    }
  }

  public Map<String, String> replaceExpressionFunctorToken(Ambiance ambiance, Map<String, String> envVar) {
    for (Map.Entry<String, String> entry : envVar.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();

      if (value != null && value.contains("functorToken")) {
        value = value.replace("functorToken", String.valueOf(ambiance.getExpressionFunctorToken()));
      }

      envVar.put(key, value);
    }
    return envVar;
  }

  private Map<String, String> getWorkspaceVariables(
      String org, String projectId, String accountId, String workspaceID, Workspace workspaceInfo) {
    WorkspaceVariables[] variables = getIACMWorkspaceVariables(org, projectId, accountId, workspaceID);
    HashMap<String, String> pluginEnvs = new HashMap<>();

    // Plugin system env variables
    pluginEnvs.put("PLUGIN_ROOT_DIR", workspaceInfo.getRepository_path());
    pluginEnvs.put("PLUGIN_TF_VERSION", workspaceInfo.getProvisioner_version());
    pluginEnvs.put("PLUGIN_CONNECTOR_REF", workspaceInfo.getProvider_connector());
    pluginEnvs.put("PLUGIN_PROVISIONER", workspaceInfo.getProvisioner());
    pluginEnvs.put("PLUGIN_ENDPOINT_VARIABLES", getTerraformEndpointsInfo(accountId, org, projectId, workspaceID));

    for (WorkspaceVariables variable : variables) {
      switch (variable.getKind()) {
        case "env":
          if (Objects.equals(variable.getValue_type(), "secret")) {
            pluginEnvs.put(
                variable.getKey(), "${ngSecretManager.obtain(\"" + variable.getValue() + "\", functorToken)}");
          } else {
            pluginEnvs.put(variable.getKey(), variable.getValue());
          }
          break;
        case "tf":
          if (Objects.equals(variable.getValue_type(), "secret")) {
            pluginEnvs.put("PLUGIN_WS_TF_VAR_" + variable.getKey(),
                "${ngSecretManager.obtain(\"" + variable.getValue() + "\", functorToken)}");
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

  private Map<String, String> buildIACMEnvVariables(String workspaceId, String org, String project, String account) {
    Workspace workspaceInfo = getIACMWorkspaceInfo(org, project, account, workspaceId);

    return getWorkspaceVariables(org, project, account, workspaceId, workspaceInfo);
  }

  public Map<String, String> getIACMEnvVariables(String org, String project, String account, String workspaceId) {
    return buildIACMEnvVariables(workspaceId, org, project, account);
  }

  public boolean isIACMStep(PluginStepInfo pluginStepInfo) {
    return pluginStepInfo.getEnvVariables() != null && pluginStepInfo.getEnvVariables().getValue() != null
        && pluginStepInfo.getEnvVariables().getValue().get(WORKSPACE_ID) != null
        && !Objects.equals(pluginStepInfo.getEnvVariables().getValue().get(WORKSPACE_ID).getValue(), "");
  }

  public ConnectorDetails retrieveIACMConnectorDetails(Ambiance ambiance, String connectorRef, String provisioner) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    if (connectorRef != null) {
      Map<EnvVariableEnum, String> connectorSecretEnvMap = null;
      if (provisioner.equals("terraform")) {
        connectorSecretEnvMap = PluginSettingUtils.getConnectorSecretEnvMap(CIStepInfoType.IACM_TERRAFORM_PLUGIN);
      }
      ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorRef);
      connectorDetails.setEnvToSecretsMap(connectorSecretEnvMap);
      return connectorDetails;
    }
    return null;
  }
}
