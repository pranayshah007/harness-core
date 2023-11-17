/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.execution;

import static io.harness.ci.commonconstants.CIExecutionConstants.CLIENT_CERTIFICATE;
import static io.harness.ci.commonconstants.CIExecutionConstants.CLIENT_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.CLIENT_SECRET;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ACCESS_KEY;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_ASSUME_ROLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_EXTERNAL_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_JSON_KEY;
import static io.harness.ci.commonconstants.CIExecutionConstants.PLUGIN_SECRET_KEY;
import static io.harness.ci.commonconstants.CIExecutionConstants.TENANT_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.WORKSPACE_ID;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.entities.VariablesRepo;
import io.harness.beans.entities.Workspace;
import io.harness.beans.entities.WorkspaceVariables;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.stepinfo.IACMApprovalInfo;
import io.harness.beans.steps.stepinfo.IACMTerraformPluginInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.buildstate.PluginSettingUtils;
import io.harness.connector.ConnectorEnvVariablesHelper;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureAuthDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientKeyCertDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureClientSecretKeyDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureCredentialDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureManualDetailsDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpManualDetailsDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.ngexception.IACMStageExecutionException;
import io.harness.iacmserviceclient.IACMServiceUtils;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.codec.digest.DigestUtils;

public class IACMStepsUtils {
  private static final String ENV_VARIABLE = "env";
  private static final String TF_VARIABLE = "tf";

  @Inject ConnectorUtils connectorUtils;
  @Inject IACMServiceUtils iacmServiceUtils;
  @Inject ConnectorEnvVariablesHelper connectorEnvVariablesHelper;

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

  private String getHarnessInfracostKey() {
    return iacmServiceUtils.getCostEstimationToken();
  }

  private String getInfracostAPIEndpoint() {
    return iacmServiceUtils.getCostEstimationAPIEndpoint();
  }

  public void createExecution(Ambiance ambiance, String workspaceId) {
    iacmServiceUtils.createIACMExecution(ambiance, workspaceId);
  }

  public String generateHashedGitRepoInfo(String repo, String connector, String branch, String commit, String path) {
    return DigestUtils.md5Hex(String.format("%s_%s_%s_%s_%s", repo, connector, branch, commit, path));
  }

  public String generateVariableFileBasePath(String hashedGitRepoInfo) {
    return String.format("/harness/.iacm/%s/", hashedGitRepoInfo);
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
    pluginEnvs.put("PLUGIN_HARNESS_INFRACOST_KEY", getHarnessInfracostKey());
    pluginEnvs.put("PLUGIN_PRICING_API_ENDPOINT", getInfracostAPIEndpoint());

    if (workspaceInfo.getTerraform_variable_files() != null) {
      for (VariablesRepo variablesRepo : workspaceInfo.getTerraform_variable_files()) {
        String hashedGitInfo = this.generateHashedGitRepoInfo(variablesRepo.getRepository(),
            variablesRepo.getRepository_connector(), variablesRepo.getRepository_branch(),
            variablesRepo.getRepository_commit(), variablesRepo.getRepository_path());

        if (Objects.equals(variablesRepo.getRepository_connector(), workspaceInfo.getRepository_connector())
            && Objects.equals(variablesRepo.getRepository(), workspaceInfo.getRepository())
            && Objects.equals(
                variablesRepo.getRepository_branch(), Objects.toString(workspaceInfo.getRepository_branch(), ""))
            && Objects.equals(
                variablesRepo.getRepository_commit(), Objects.toString(workspaceInfo.getRepository_commit(), ""))) {
          pluginEnvs.put(String.format("PLUGIN_VARIABLE_CONNECTOR_%s", hashedGitInfo),
              "/harness/" + variablesRepo.getRepository_path());
          continue;
        }
        pluginEnvs.put(String.format("PLUGIN_VARIABLE_CONNECTOR_%s", hashedGitInfo),
            this.generateVariableFileBasePath(hashedGitInfo) + variablesRepo.getRepository_path());
      }
    }
    for (WorkspaceVariables variable : variables) {
      switch (variable.getKind()) {
        case ENV_VARIABLE:
          if (Objects.equals(variable.getValue_type(), "secret")) {
            pluginEnvs.put(
                variable.getKey(), "${ngSecretManager.obtain(\"" + variable.getValue() + "\", functorToken)}");
          } else {
            pluginEnvs.put(variable.getKey(), variable.getValue());
          }
          break;
        case TF_VARIABLE:
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

  public Map<String, String> getIACMEnvVariables(String org, String project, String account, String workspaceId) {
    Workspace workspaceInfo = getIACMWorkspaceInfo(org, project, account, workspaceId);
    return getWorkspaceVariables(org, project, account, workspaceId, workspaceInfo);
  }

  public Map<String, String> getIACMEnvVariablesFromConnector(
      String org, String project, String account, String workspaceId) {
    Map<String, String> envVars = new HashMap<>();
    Workspace workspaceInfo = getIACMWorkspaceInfo(org, project, account, workspaceId);
    String connectorRef = workspaceInfo.getProvider_connector();
    if (connectorRef == null) {
      return envVars;
    }
    NGAccess ngAccess =
        BaseNGAccess.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build();
    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorRef);
    if (connectorDetails == null) {
      return envVars;
    }
    switch (connectorDetails.getConnectorType()) {
      case AWS: {
        AwsConnectorDTO config = (AwsConnectorDTO) connectorDetails.getConnectorConfig();
        AwsCredentialDTO credential = config.getCredential();
        if (credential.getConfig() instanceof AwsManualConfigSpecDTO) {
          AwsManualConfigSpecDTO awsManualConfigSpecDTO = (AwsManualConfigSpecDTO) credential.getConfig();
          if (awsManualConfigSpecDTO.getAccessKey() != null) {
            envVars.put(PLUGIN_ACCESS_KEY, awsManualConfigSpecDTO.getAccessKey());
          }
        }
        if (credential.getCrossAccountAccess() != null) {
          String crossAccountRoleArn = credential.getCrossAccountAccess().getCrossAccountRoleArn();
          if (isNotEmpty(crossAccountRoleArn)) {
            envVars.put(PLUGIN_ASSUME_ROLE, crossAccountRoleArn);
          }
          String externalId = credential.getCrossAccountAccess().getExternalId();
          if (isNotEmpty(externalId)) {
            envVars.put(PLUGIN_EXTERNAL_ID, externalId);
          }
        }
        break;
      }
      case GCP:
        // None
        break;
      case AZURE: {
        AzureConnectorDTO config = (AzureConnectorDTO) connectorDetails.getConnectorConfig();
        AzureCredentialDTO credential = config.getCredential();
        if (credential.getConfig() instanceof GcpManualDetailsDTO) {
          AzureManualDetailsDTO dto = (AzureManualDetailsDTO) credential.getConfig();
          if (dto.getClientId() != null) {
            envVars.put(CLIENT_ID, dto.getClientId());
          }
          if (dto.getTenantId() != null) {
            envVars.put(TENANT_ID, dto.getTenantId());
          }
        }
        break;
      }
      default:
        break;
    }
    return envVars;
  }

  public Map<String, String> getIACMSecretVariables(String org, String project, String account, String workspaceId) {
    WorkspaceVariables[] variables = getIACMWorkspaceVariables(org, project, account, workspaceId);
    Map<String, String> secrets = new HashMap<>();
    for (WorkspaceVariables variable : variables) {
      switch (variable.getKind()) {
        case ENV_VARIABLE:
          if (Objects.equals(variable.getValue_type(), "secret")) {
            secrets.put(variable.getKey(), variable.getValue());
          }
          break;
        case TF_VARIABLE:
          if (Objects.equals(variable.getValue_type(), "secret")) {
            secrets.put("PLUGIN_WS_TF_VAR_" + variable.getKey(), variable.getValue());
          }
          break;
        default:
          break;
      }
    }
    return secrets;
  }

  public Map<String, String> getIACMSecretVariablesFromConnector(
      String org, String project, String account, String workspaceId) {
    Map<String, String> secrets = new HashMap<>();
    Workspace workspaceInfo = getIACMWorkspaceInfo(org, project, account, workspaceId);
    String connectorRef = workspaceInfo.getProvider_connector();
    if (connectorRef == null) {
      return secrets;
    }
    NGAccess ngAccess =
        BaseNGAccess.builder().accountIdentifier(account).orgIdentifier(org).projectIdentifier(project).build();
    ConnectorDetails connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorRef);
    if (connectorDetails == null) {
      return secrets;
    }
    switch (connectorDetails.getConnectorType()) {
      case AWS: {
        AwsConnectorDTO config = (AwsConnectorDTO) connectorDetails.getConnectorConfig();
        AwsCredentialDTO credential = config.getCredential();
        if (credential.getConfig() instanceof AwsManualConfigSpecDTO) {
          AwsManualConfigSpecDTO dto = (AwsManualConfigSpecDTO) credential.getConfig();
          if (dto.getAccessKeyRef() != null) {
            secrets.put(PLUGIN_ACCESS_KEY, dto.getAccessKeyRef().toSecretRefStringValue());
          }
          if (dto.getSecretKeyRef() != null) {
            secrets.put(PLUGIN_SECRET_KEY, dto.getSecretKeyRef().toSecretRefStringValue());
          }
        }
        break;
      }
      case GCP: {
        GcpConnectorDTO config = (GcpConnectorDTO) connectorDetails.getConnectorConfig();
        GcpConnectorCredentialDTO credential = config.getCredential();
        if (credential.getConfig() instanceof GcpManualDetailsDTO) {
          GcpManualDetailsDTO dto = (GcpManualDetailsDTO) credential.getConfig();
          if (dto.getSecretKeyRef() != null) {
            secrets.put(PLUGIN_JSON_KEY, dto.getSecretKeyRef().toSecretRefStringValue());
          }
        }
        break;
      }
      case AZURE: {
        AzureConnectorDTO config = (AzureConnectorDTO) connectorDetails.getConnectorConfig();
        AzureCredentialDTO credential = config.getCredential();
        if (credential.getConfig() instanceof GcpManualDetailsDTO) {
          AzureManualDetailsDTO dto = (AzureManualDetailsDTO) credential.getConfig();
          AzureAuthDTO authDTO = dto.getAuthDTO();
          if (authDTO.getCredentials() instanceof AzureClientSecretKeyDTO) {
            AzureClientSecretKeyDTO azureClientSecretKeyDTO = (AzureClientSecretKeyDTO) authDTO.getCredentials();
            if (azureClientSecretKeyDTO.getSecretKey() != null) {
              secrets.put(CLIENT_SECRET, azureClientSecretKeyDTO.getSecretKey().toSecretRefStringValue());
            }
          } else if (authDTO.getCredentials() instanceof AzureClientKeyCertDTO) {
            AzureClientKeyCertDTO azureClientKeyCertDTO = (AzureClientKeyCertDTO) authDTO.getCredentials();
            if (azureClientKeyCertDTO.getClientCertRef() != null) {
              secrets.put(CLIENT_CERTIFICATE, azureClientKeyCertDTO.getClientCertRef().toSecretRefStringValue());
            }
          }
        }
        break;
      }
      default:
        break;
    }
    return secrets;
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

  public Map<String, String> getVariablesForKubernetes(
      Ambiance ambiance, IACMTerraformPluginInfo iacmTerraformPluginInfo) {
    if (iacmTerraformPluginInfo.getWorkspace() != null) {
      createExecution(ambiance, iacmTerraformPluginInfo.getWorkspace());
    }
    Map<String, String> envVars = iacmTerraformPluginInfo.getEnvVariables().getValue();
    if (envVars == null) {
      return new HashMap<>();
    }
    Map<String, String> envVariablesFromConnector = iacmTerraformPluginInfo.getEnvVariablesFromConnector().getValue();
    if (envVariablesFromConnector != null) {
      envVars.putAll(envVariablesFromConnector);
    }
    Map<String, String> secretVars = iacmTerraformPluginInfo.getSecretVariables().getValue();
    if (secretVars != null) {
      // Remove every secret from the env vars - we handle them below in getPluginCompatibleSecretVars()
      for (String key : secretVars.keySet()) {
        envVars.remove(key);
      }
    }
    Map<String, String> secretVarsFromConnector = iacmTerraformPluginInfo.getSecretVariablesFromConnector().getValue();
    if (secretVarsFromConnector != null) {
      // Remove every secret from the env vars - we handle them below in getPluginCompatibleSecretVars()
      for (String key : secretVarsFromConnector.keySet()) {
        envVars.remove(key);
      }
    }
    envVars.put("PLUGIN_ENDPOINT_VARIABLES", populatePipelineIds(ambiance, envVars.get("PLUGIN_ENDPOINT_VARIABLES")));
    return envVars;
  }

  public Map<String, String> getVariablesForKubernetes(Ambiance ambiance, IACMApprovalInfo iacmApprovalInfo) {
    if (iacmApprovalInfo.getWorkspace() != null) {
      createExecution(ambiance, iacmApprovalInfo.getWorkspace());
    }
    Map<String, String> envVars = iacmApprovalInfo.getEnvVariables().getValue();
    if (envVars == null) {
      return new HashMap<>();
    }
    envVars.put("PLUGIN_ENDPOINT_VARIABLES", populatePipelineIds(ambiance, envVars.get("PLUGIN_ENDPOINT_VARIABLES")));
    envVars.put("PLUGIN_AUTO_APPROVE", iacmApprovalInfo.getAutoApprove().getValue().toString());

    return envVars;
  }

  public Map<String, SecretNGVariable> getSecretVariablesForKubernetes(
      IACMTerraformPluginInfo iacmTerraformPluginInfo) {
    Map<String, SecretNGVariable> secrets = new HashMap<>();

    // Check for workspace variables that contain secrets
    Map<String, String> secretVars = iacmTerraformPluginInfo.getSecretVariables().getValue();
    if (secretVars != null) {
      for (Map.Entry<String, String> entry : secretVars.entrySet()) {
        secrets.put(entry.getKey(),
            SecretNGVariable.builder()
                .type(NGVariableType.SECRET)
                .value(ParameterField.createValueField(SecretRefHelper.createSecretRef(entry.getValue())))
                .name(entry.getKey())
                .build());
      }
    }

    // Check for secrets in the connector
    Map<String, String> secretVarsFromConnector = iacmTerraformPluginInfo.getSecretVariablesFromConnector().getValue();
    if (secretVarsFromConnector != null) {
      for (Map.Entry<String, String> entry : secretVarsFromConnector.entrySet()) {
        secrets.put(entry.getKey(),
            SecretNGVariable.builder()
                .type(NGVariableType.SECRET)
                .value(ParameterField.createValueField(SecretRefHelper.createSecretRef(entry.getValue())))
                .name(entry.getKey())
                .build());
      }
    }

    return secrets;
  }
}
