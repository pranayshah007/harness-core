/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig.GitStoreDelegateConfigBuilder;

import static java.util.Collections.emptyList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.beans.DecryptableEntity;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.provision.azure.beans.AzureCreateARMResourcePassThroughData;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.logging.UnitProgress;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import software.wings.beans.TaskType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Slf4j
@OwnedBy(CDP)
public class AzureCommonHelper {
  @Inject private CDStepHelper cdStepHelper;

  @Inject private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;

  public static final String DEFAULT_TIMEOUT = "10m";
  public static final String TEMPLATE_FILE_IDENTIFIER = "templateFile";
  public static final String BLUEPRINT_IDENTIFIER = "bluePrint";
  public static final String PARAMETERS_FILE_IDENTIFIER = "parameterFile";
  public static final String AZURE_TEMPLATE_TYPE = "Azure Template";
  public static final String AZURE_PARAMETER_TYPE = "Azure Parameter";
  public static final String BP_TEMPLATE_TYPE = "Azure BluePrint Folder";

  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;

  public GitStoreDelegateConfig getGitStoreDelegateConfig(StoreConfig store, Ambiance ambiance, List<String> paths) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) store;
    cdStepHelper.validateGitStoreConfig(gitStoreConfig);
    String connectorId = getParameterFieldValue(gitStoreConfig.getConnectorRef());
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);

    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO((ScmConnector) connectorDTO.getConnectorConfig());
    NGAccess basicNGAccessObject = AmbianceUtils.getNgAccess(ambiance);
    SSHKeySpecDTO sshKeySpecDTO =
        gitConfigAuthenticationInfoHelper.getSSHKey(gitConfigDTO, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
    String repoName = gitStoreConfig.getRepoName() != null ? gitStoreConfig.getRepoName().getValue() : null;
    if (gitConfigDTO.getGitConnectionType() == GitConnectionType.ACCOUNT) {
      String repoUrl = cdStepHelper.getGitRepoUrl(gitConfigDTO, repoName);
      gitConfigDTO.setUrl(repoUrl);
      gitConfigDTO.setGitConnectionType(GitConnectionType.REPO);
    }
    ScmConnector scmConnector = cdStepHelper.getScmConnector(
        (ScmConnector) connectorDTO.getConnectorConfig(), basicNGAccessObject.getAccountIdentifier(), gitConfigDTO);
    List<EncryptedDataDetail> encryptedDataDetails =
        gitConfigAuthenticationInfoHelper.getEncryptedDataDetails(scmConnector, sshKeySpecDTO, basicNGAccessObject);
    GitStoreDelegateConfigBuilder builder = GitStoreDelegateConfig.builder();
    builder.gitConfigDTO(scmConnector)
        .sshKeySpecDTO(sshKeySpecDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .fetchType(gitStoreConfig.getGitFetchType())
        .branch(getParameterFieldValue(gitStoreConfig.getBranch()))
        .commitId(getParameterFieldValue(gitStoreConfig.getCommitId()))
        .connectorName(connectorDTO.getName())
        .build();
    builder.paths(paths);

    return builder.build();
  }

  public boolean hasGitStoredParameters(AzureCreateARMResourceStepConfigurationParameters stepConfigurationParameters) {
    return stepConfigurationParameters.getParameters() != null
        && ManifestStoreType.isInGitSubset(stepConfigurationParameters.getParameters().getStore().getSpec().getKind());
  }
  public AzureConnectorDTO getAzureConnectorConfig(Ambiance ambiance, ParameterField<String> connectorRef) {
    return (AzureConnectorDTO) cdStepHelper.getConnector(getParameterFieldValue(connectorRef), ambiance)
        .getConnectorConfig();
  }

  public List<EncryptedDataDetail> getAzureEncryptionDetails(Ambiance ambiance, AzureConnectorDTO azureConnectorDTO) {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    if (isNotEmpty(azureConnectorDTO.getDecryptableEntities())) {
      for (DecryptableEntity decryptableEntity : azureConnectorDTO.getDecryptableEntities()) {
        encryptedDataDetails.addAll(
            secretManagerClientService.getEncryptionDetails(AmbianceUtils.getNgAccess(ambiance), decryptableEntity));
      }
    } else {
      return emptyList();
    }
    return encryptedDataDetails;
  }

  public TaskChainResponse getGitFetchFileTaskChainResponse(Ambiance ambiance,
      List<GitFetchFilesConfig> gitFetchFilesConfigs, StepElementParameters stepElementParameters,
      PassThroughData passThroughData, List<String> commandUnits, CommandUnitsProgress commandUnitsProgress) {
    ParameterField<List<TaskSelectorYaml>> delegateSelector;
    if (stepElementParameters.getSpec() instanceof AzureCreateBPStepParameters) {
      delegateSelector = ((AzureCreateBPStepParameters) stepElementParameters.getSpec()).getDelegateSelectors();
    } else {
      delegateSelector =
          ((AzureCreateARMResourceStepParameters) stepElementParameters.getSpec()).getDelegateSelectors();
    }
    GitFetchRequest gitFetchRequest = GitFetchRequest.builder()
                                          .gitFetchFilesConfigs(gitFetchFilesConfigs)
                                          .accountId(AmbianceUtils.getAccountId(ambiance))
                                          .closeLogStream(true)
                                          .commandUnitsProgress(commandUnitsProgress)
                                          .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.GIT_FETCH_NEXT_GEN_TASK.name())
                                  .parameters(new Object[] {gitFetchRequest})
                                  .build();

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, commandUnits, TaskType.GIT_FETCH_NEXT_GEN_TASK.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(delegateSelector), stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(passThroughData)
        .build();
  }

  boolean isTemplateStoredOnGit(AzureTemplateFile azureCreateTemplateFileSpec) {
    return ManifestStoreType.isInGitSubset(azureCreateTemplateFileSpec.getStore().getSpec().getKind());
  }

  AzureCreateARMResourcePassThroughData getAzureCreatePassThroughData(
      AzureCreateARMResourceStepConfigurationParameters stepConfiguration) {
    boolean hasGitFiles =
        hasGitStoredParameters(stepConfiguration) || isTemplateStoredOnGit(stepConfiguration.getTemplateFile());

    return AzureCreateARMResourcePassThroughData.builder().hasGitFiles(hasGitFiles).build();
  }

  AzureDeploymentMode retrieveDeploymentMode(ARMScopeType scopeType, String mode) {
    if (ARMScopeType.RESOURCE_GROUP == scopeType) {
      return mode != null ? AzureDeploymentMode.valueOf(mode.toUpperCase()) : AzureDeploymentMode.INCREMENTAL;
    }
    return AzureDeploymentMode.INCREMENTAL;
  }

  List<GitFetchFilesConfig> getParametersGitFetchFileConfigs(
      Ambiance ambiance, AzureCreateARMResourceStepConfigurationParameters stepConfiguration) {
    if (stepConfiguration.getParameters() != null
        && ManifestStoreType.isInGitSubset(stepConfiguration.getParameters().getStore().getSpec().getKind())) {
      GitStoreConfig gitStoreConfig = (GitStoreConfig) stepConfiguration.getParameters().getStore().getSpec();
      List<String> paths = new ArrayList<>(ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getPaths()));
      return new ArrayList<>(
          Collections.singletonList(GitFetchFilesConfig.builder()
                                        .manifestType(AZURE_PARAMETER_TYPE)
                                        .identifier(PARAMETERS_FILE_IDENTIFIER)
                                        .gitStoreDelegateConfig(getGitStoreDelegateConfig(
                                            stepConfiguration.getParameters().getStore().getSpec(), ambiance, paths))
                                        .build()));
    }

    return new ArrayList<>();
  }

  public StepResponse getFailureResponse(List<UnitProgress> unitProgresses, String errorMessage) {
    return StepResponse.builder()
        .unitProgressList(unitProgresses)
        .status(Status.FAILED)
        .failureInfo(FailureInfo.newBuilder().setErrorMessage(errorMessage).build())
        .build();
  }

  protected Map<String, Object> getARMOutputs(String outputs) {
    Map<String, Object> outputMap = new LinkedHashMap<>();
    if (isEmpty(outputs)) {
      return outputMap;
    }
    try {
      TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
      Map<String, Object> json = new ObjectMapper().readValue(IOUtils.toInputStream(outputs), typeRef);

      json.forEach((key, object) -> outputMap.put(key, ((Map<String, Object>) object).get("value")));
    } catch (IOException exception) {
      log.warn("Exception while parsing ARM outputs", exception);
      return new LinkedHashMap<>();
    }
    return outputMap;
  }
}
