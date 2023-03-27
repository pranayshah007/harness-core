/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.beans.FeatureName.CDS_TERRAFORM_REMOTE_BACKEND_CONFIG_NG;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.cdng.provision.terraform.TerraformPlanCommand.APPLY;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;
import static io.harness.validation.Validator.notEmptyCheck;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.FileReference;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.beans.SecretManagerConfig;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.fileservice.FileServiceClientFactory;
import io.harness.cdng.k8s.K8sStepHelper;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.FileStorageStoreConfig;
import io.harness.cdng.manifest.yaml.GitLabStore;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.GitStoreConfigDTO;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.pipeline.executions.TerraformSecretCleanupTaskNotifyCallback;
import io.harness.cdng.provision.terraform.TerraformConfig.TerraformConfigBuilder;
import io.harness.cdng.provision.terraform.TerraformConfig.TerraformConfigKeys;
import io.harness.cdng.provision.terraform.TerraformInheritOutput.TerraformInheritOutputBuilder;
import io.harness.cdng.provision.terraform.executions.TFPlanExecutionDetailsKey;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExectionDetailsService;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails;
import io.harness.cdng.provision.terraform.output.TerraformHumanReadablePlanOutput;
import io.harness.cdng.provision.terraform.output.TerraformPlanJsonOutput;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.AccountId;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.filestore.FileStoreFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.terraform.InlineTerraformBackendConfigFileInfo;
import io.harness.delegate.task.terraform.InlineTerraformVarFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformBackendConfigFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformBackendConfigFileInfo.RemoteTerraformBackendConfigFileInfoBuilder;
import io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo.RemoteTerraformVarFileInfoBuilder;
import io.harness.delegate.task.terraform.TerraformBackendConfigFileInfo;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.delegate.task.terraform.TerraformVarFileInfo;
import io.harness.delegate.task.terraform.cleanup.TerraformSecretCleanupTaskParameters;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.CGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.utils.IdentifierRefHelper;
import io.harness.validation.Validator;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.TaskType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.io.IOException;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class TerraformStepHelper {
  private static final String INHERIT_OUTPUT_FORMAT = "tfInheritOutput_%s";
  public static final String TF_NAME_PREFIX_NG = "tfPlan_%s_%s";
  public static final String TF_DESTROY_NAME_PREFIX_NG = "tfDestroyPlan_%s_%s";
  private static final String TF_INHERIT_OUTPUT_FORMAT = "tfInheritOutput_%s_%s";
  public static final String TF_CONFIG_FILES = "TF_CONFIG_FILES";
  public static final String TF_VAR_FILES = "TF_VAR_FILES_%d";
  public static final String TF_BACKEND_CONFIG_FILE = "TF_BACKEND_CONFIG_FILE";
  public static final String USE_CONNECTOR_CREDENTIALS = "useConnectorCredentials";
  public static final String TERRAFORM_CLOUD_CLI = "Terraform cloud CLI";
  public static final String SKIP_REFRESH_COMMAND = "Skip Refresh Command";
  public static final String CLI_OPTIONS = "CLI Options";

  @Inject private HPersistence persistence;
  @Inject private K8sStepHelper k8sStepHelper;
  @Inject TerraformPlanExectionDetailsService terraformPlanExectionDetailsService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Inject private FileServiceClientFactory fileService;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject public TerraformConfigDAL terraformConfigDAL;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private WaitNotifyEngine waitNotifyEngine;

  @Inject private FileStoreService fileStoreService;
  @Inject DelegateServiceGrpcClient delegateServiceGrpcClient;

  public static Optional<EntityDetail> prepareEntityDetailForBackendConfigFiles(
      String accountId, String orgIdentifier, String projectIdentifier, TerraformBackendConfig config) {
    if (config == null || config.getType().equals(TerraformVarFileTypes.Inline)) {
      return Optional.empty();
    }
    ParameterField<String> connectorReference =
        ((RemoteTerraformBackendConfigSpec) config.getTerraformBackendConfigSpec())
            .getStore()
            .getSpec()
            .getConnectorReference();
    if (connectorReference == null) {
      return Optional.empty();
    }
    String connectorReferenceValue = connectorReference.getValue();
    IdentifierRef identifierRef =
        IdentifierRefHelper.getIdentifierRef(connectorReferenceValue, accountId, orgIdentifier, projectIdentifier);
    EntityDetail entityDetail = EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
    return Optional.of(entityDetail);
  }

  public static List<EntityDetail> prepareEntityDetailsForVarFiles(
      String accountId, String orgIdentifier, String projectIdentifier, Map<String, TerraformVarFile> varFiles) {
    List<EntityDetail> entityDetailList = new ArrayList<>();

    if (EmptyPredicate.isNotEmpty(varFiles)) {
      for (Map.Entry<String, TerraformVarFile> varFileEntry : varFiles.entrySet()) {
        if (varFileEntry.getValue().getType().equals(TerraformVarFileTypes.Remote)) {
          String connectorRef = ((RemoteTerraformVarFileSpec) varFileEntry.getValue().getSpec())
                                    .getStore()
                                    .getSpec()
                                    .getConnectorReference()
                                    .getValue();
          IdentifierRef identifierRef =
              IdentifierRefHelper.getIdentifierRef(connectorRef, accountId, orgIdentifier, projectIdentifier);
          EntityDetail entityDetail =
              EntityDetail.builder().type(EntityType.CONNECTORS).entityRef(identifierRef).build();
          entityDetailList.add(entityDetail);
        }
      }
    }

    return entityDetailList;
  }

  public String generateFullIdentifier(String provisionerIdentifier, Ambiance ambiance) {
    if (Pattern.matches(NGRegexValidatorConstants.IDENTIFIER_PATTERN, provisionerIdentifier)) {
      return format("%s/%s/%s/%s", AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance), provisionerIdentifier);
    } else {
      throw new InvalidRequestException(
          format("Provisioner Identifier cannot contain special characters or spaces: [%s]", provisionerIdentifier));
    }
  }

  public GitFetchFilesConfig getGitFetchFilesConfig(StoreConfig store, Ambiance ambiance, String identifier) {
    if (store == null || !ManifestStoreType.isInGitSubset(store.getKind())) {
      return null;
    }
    GitStoreConfig gitStoreConfig = (GitStoreConfig) store;
    cdStepHelper.validateGitStoreConfig(gitStoreConfig);
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);
    String validationMessage = "";
    switch (identifier) {
      case TerraformStepHelper.TF_CONFIG_FILES:
        validationMessage = "Config Files";
        break;
      case TerraformStepHelper.TF_VAR_FILES:
        validationMessage = "Backend Configuration Files";
        break;
      default:
        validationMessage = format("Var Files with identifier: %s", identifier);
    }
    // TODO: fix manifest part, remove k8s dependency
    cdStepHelper.validateManifest(store.getKind(), connectorDTO, validationMessage);
    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO((ScmConnector) connectorDTO.getConnectorConfig());
    NGAccess basicNGAccessObject = AmbianceUtils.getNgAccess(ambiance);
    SSHKeySpecDTO sshKeySpecDTO =
        gitConfigAuthenticationInfoHelper.getSSHKey(gitConfigDTO, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));
    List<EncryptedDataDetail> encryptedDataDetails =
        gitConfigAuthenticationInfoHelper.getEncryptedDataDetails(gitConfigDTO, sshKeySpecDTO, basicNGAccessObject);
    String repoName = gitStoreConfig.getRepoName() != null ? gitStoreConfig.getRepoName().getValue() : null;
    if (gitConfigDTO.getGitConnectionType() == GitConnectionType.ACCOUNT) {
      String repoUrl = getGitRepoUrl(gitConfigDTO, repoName);
      gitConfigDTO.setUrl(repoUrl);
      gitConfigDTO.setGitConnectionType(GitConnectionType.REPO);
    }
    List<String> paths = new ArrayList<>();
    if (TF_CONFIG_FILES.equals(identifier) || TF_BACKEND_CONFIG_FILE.equals(identifier)) {
      paths.add(ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getFolderPath()));
    } else {
      paths.addAll(ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getPaths()));
    }
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .gitConfigDTO(gitConfigDTO)
                                                        .sshKeySpecDTO(sshKeySpecDTO)
                                                        .encryptedDataDetails(encryptedDataDetails)
                                                        .fetchType(gitStoreConfig.getGitFetchType())
                                                        .branch(getParameterFieldValue(gitStoreConfig.getBranch()))
                                                        .commitId(getParameterFieldValue(gitStoreConfig.getCommitId()))
                                                        .paths(paths)
                                                        .connectorName(connectorDTO.getName())
                                                        .build();

    return GitFetchFilesConfig.builder()
        .identifier(identifier)
        .succeedIfFileNotFound(false)
        .gitStoreDelegateConfig(gitStoreDelegateConfig)
        .build();
  }

  String getLocalFileStoreDelegateConfigPath(StoreConfig store, Ambiance ambiance) {
    HarnessStore harnessStore = (HarnessStore) store;
    String scopedFilePath = harnessStore.getFiles().getValue().get(0);
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    FileReference fileReference = FileReference.of(
        scopedFilePath, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Optional<FileStoreNodeDTO> optionalFileStoreNodeDTO =
        fileStoreService.getWithChildrenByPath(fileReference.getAccountIdentifier(), fileReference.getOrgIdentifier(),
            fileReference.getProjectIdentifier(), fileReference.getPath(), false);
    if (optionalFileStoreNodeDTO.isPresent()) {
      FileStoreNodeDTO manifestFileDirectory = optionalFileStoreNodeDTO.get();
      return manifestFileDirectory.getPath();
    }
    return null;
  }

  public FileStoreFetchFilesConfig getFileStoreFetchFilesConfig(
      StoreConfig store, Ambiance ambiance, String identifier) {
    if (store == null || !ManifestStoreType.ARTIFACTORY.equals(store.getKind())) {
      return null;
    }
    ArtifactoryStoreConfig artifactoryStoreConfig = (ArtifactoryStoreConfig) store;
    validateArtifactoryStoreConfig(artifactoryStoreConfig);
    String connectorId = ParameterFieldHelper.getParameterFieldValue(artifactoryStoreConfig.getConnectorRef());
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);
    String validationMessage = "";
    switch (identifier) {
      case TerraformStepHelper.TF_CONFIG_FILES:
        if (ParameterFieldHelper.getParameterFieldValue(artifactoryStoreConfig.getArtifactPaths()).size() > 1) {
          throw new InvalidRequestException("Config file should not contain more than one file path");
        }
        validationMessage = "Config Files";
        break;
      case TerraformStepHelper.TF_BACKEND_CONFIG_FILE:
        validationMessage = "Backend Configuration File";
        break;
      default:
        validationMessage = format("Var Files with identifier: %s", identifier);
    }
    cdStepHelper.validateManifest(store.getKind(), connectorDTO, validationMessage);
    NGAccess basicNGAccessObject = AmbianceUtils.getNgAccess(ambiance);
    List<EncryptedDataDetail> encryptedDataDetails = secretManagerClientService.getEncryptionDetails(
        basicNGAccessObject, ((ArtifactoryConnectorDTO) connectorDTO.getConnectorConfig()).getAuth().getCredentials());
    return ArtifactoryStoreDelegateConfig.builder()
        .repositoryName(ParameterFieldHelper.getParameterFieldValue(artifactoryStoreConfig.getRepositoryName()))
        .identifier(identifier)
        .manifestStoreType(store.getKind())
        .connectorDTO(connectorDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .succeedIfFileNotFound(false)
        .artifacts(ParameterFieldHelper.getParameterFieldValue(artifactoryStoreConfig.getArtifactPaths()))
        .build();
  }

  private void validateArtifactoryStoreConfig(ArtifactoryStoreConfig artifactoryStoreConfig) {
    Validator.notNullCheck("artifactoryStoreConfig is null", artifactoryStoreConfig);
  }

  private String getGitRepoUrl(GitConfigDTO gitConfigDTO, String repoName) {
    repoName = trimToEmpty(repoName);
    notEmptyCheck("Repo name cannot be empty for Account level git connector", repoName);
    String purgedRepoUrl = gitConfigDTO.getUrl().replaceAll("/*$", "");
    String purgedRepoName = repoName.replaceAll("^/*", "");
    return purgedRepoUrl + "/" + purgedRepoName;
  }

  public TerraformInheritOutput getSavedInheritOutput(String provisionerIdentifier, String command, Ambiance ambiance) {
    String fullEntityId = generateFullIdentifier(provisionerIdentifier, ambiance);
    OptionalSweepingOutput output = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(format(TF_INHERIT_OUTPUT_FORMAT, command, fullEntityId)));
    if (!output.isFound()) {
      // This is for backward compatibility as after we release this, there may be IN PROGRESS workflows which saved
      // inherit output  with old name. To be removed after some time.
      output = executionSweepingOutputService.resolveOptional(
          ambiance, RefObjectUtils.getSweepingOutputRefObject(format(INHERIT_OUTPUT_FORMAT, fullEntityId)));
    }
    if (!output.isFound()) {
      throw new InvalidRequestException(
          format("Did not find any Plan step for provisioner identifier: [%s]", provisionerIdentifier));
    }

    return (TerraformInheritOutput) output.getOutput();
  }

  public void saveTerraformInheritOutput(TerraformPlanStepParameters planStepParameters,
      TerraformTaskNGResponse terraformTaskNGResponse, Ambiance ambiance) {
    validatePlanStepConfigFiles(planStepParameters);
    TerraformPlanExecutionDataParameters configuration = planStepParameters.getConfiguration();
    TerraformInheritOutputBuilder builder = TerraformInheritOutput.builder().workspace(
        ParameterFieldHelper.getParameterFieldValue(configuration.getWorkspace()));
    StoreConfigWrapper store = configuration.getConfigFiles().getStore();
    StoreConfigType storeConfigType = store.getType();
    switch (storeConfigType) {
      case GIT:
      case GITHUB:
      case GITLAB:
      case BITBUCKET:
        Map<String, String> commitIdMap = terraformTaskNGResponse.getCommitIdForConfigFilesMap();
        builder.configFiles(getStoreConfigAtCommitId(
            configuration.getConfigFiles().getStore().getSpec(), commitIdMap.get(TF_CONFIG_FILES)));
        builder.useConnectorCredentials(isExportCredentialForSourceModule(
            configuration.getConfigFiles(), ExecutionNodeType.TERRAFORM_PLAN.getYamlType()));

        break;
      case ARTIFACTORY:
        builder.fileStoreConfig((FileStorageStoreConfig) configuration.getConfigFiles().getStore().getSpec());
        break;
      default:
        throw new InvalidRequestException(format("Unsupported store type: [%s]", storeConfigType));
    }

    builder.varFileConfigs(toTerraformVarFileConfig(configuration.getVarFiles(), terraformTaskNGResponse, ambiance))
        .backendConfig(getBackendConfig(configuration.getBackendConfig()))
        .backendConfigurationFileConfig(
            toTerraformBackendConfigFileConfig(configuration.getBackendConfig(), terraformTaskNGResponse))
        .environmentVariables(getEnvironmentVariablesMap(configuration.getEnvironmentVariables()))
        .targets(ParameterFieldHelper.getParameterFieldValue(configuration.getTargets()))
        .encryptedTfPlan(terraformTaskNGResponse.getEncryptedTfPlan())
        .encryptionConfig(getEncryptionConfig(ambiance, planStepParameters))
        .planName(getTerraformPlanName(planStepParameters.getConfiguration().getCommand(), ambiance,
            planStepParameters.getProvisionerIdentifier().getValue()));
    String fullEntityId = generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(planStepParameters.getProvisionerIdentifier()), ambiance);
    String inheritOutputName =
        format(TF_INHERIT_OUTPUT_FORMAT, planStepParameters.getConfiguration().command.name(), fullEntityId);
    executionSweepingOutputService.consume(ambiance, inheritOutputName, builder.build(), StepOutcomeGroup.STAGE.name());
  }

  @Nullable
  public String saveTerraformPlanJsonOutput(
      Ambiance ambiance, TerraformTaskNGResponse response, String provisionIdentifier) {
    if (isEmpty(response.getTfPlanJsonFileId())) {
      return null;
    }

    TerraformPlanJsonOutput planJsonOutput = TerraformPlanJsonOutput.builder()
                                                 .provisionerIdentifier(provisionIdentifier)
                                                 .tfPlanFileId(response.getTfPlanJsonFileId())
                                                 .tfPlanFileBucket(FileBucket.TERRAFORM_PLAN_JSON.name())
                                                 .build();

    String outputName = TerraformPlanJsonOutput.getOutputName(provisionIdentifier);
    executionSweepingOutputService.consume(ambiance, outputName, planJsonOutput, StepCategory.STEP.name());

    return outputName;
  }

  @Nullable
  public String saveTerraformPlanHumanReadableOutput(
      Ambiance ambiance, TerraformTaskNGResponse response, String provisionIdentifier) {
    if (isEmpty(response.getTfHumanReadablePlanFileId())) {
      return null;
    }

    TerraformHumanReadablePlanOutput terraformHumanReadablePlanOutput =
        TerraformHumanReadablePlanOutput.builder()
            .provisionerIdentifier(provisionIdentifier)
            .tfPlanFileId(response.getTfHumanReadablePlanFileId())
            .tfPlanFileBucket(FileBucket.TERRAFORM_HUMAN_READABLE_PLAN.name())
            .build();

    String outputName = TerraformHumanReadablePlanOutput.getOutputName(provisionIdentifier);
    executionSweepingOutputService.consume(
        ambiance, outputName, terraformHumanReadablePlanOutput, StepCategory.STEP.name());

    return outputName;
  }

  public void saveTerraformPlanExecutionDetails(Ambiance ambiance, TerraformTaskNGResponse response,
      String provisionerIdentifier, TerraformPlanStepParameters planStepParameters) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();
    TerraformPlanExecutionDetails terraformPlanExecutionDetails =
        TerraformPlanExecutionDetails.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pipelineExecutionId(planExecutionId)
            .stageExecutionId(stageExecutionId)
            .provisionerId(provisionerIdentifier)
            .encryptedTfPlan(List.of(response.getEncryptedTfPlan()))
            .encryptionConfig(getEncryptionConfig(ambiance, planStepParameters))
            .tfPlanJsonFieldId(response.getTfPlanJsonFileId())
            .tfPlanFileBucket(FileBucket.TERRAFORM_PLAN_JSON.name())
            .tfHumanReadablePlanId(response.getTfHumanReadablePlanFileId())
            .tfHumanReadablePlanFileBucket(FileBucket.TERRAFORM_HUMAN_READABLE_PLAN.name())
            .build();

    terraformPlanExectionDetailsService.save(terraformPlanExecutionDetails);
  }

  public void cleanupTfPlanJson(List<TerraformPlanExecutionDetails> terraformPlanExecutionDetailsList) {
    for (TerraformPlanExecutionDetails terraformPlanExecutionDetails : terraformPlanExecutionDetailsList) {
      if (isNotEmpty(terraformPlanExecutionDetails.getTfPlanJsonFieldId())
          && isNotEmpty(terraformPlanExecutionDetails.getTfPlanFileBucket())) {
        FileBucket fileBucket = FileBucket.valueOf(terraformPlanExecutionDetails.getTfPlanFileBucket());
        try {
          log.info("Remove terraform plan json file [{}] from bucket [{}] for provisioner [{}]",
              terraformPlanExecutionDetails.getTfPlanJsonFieldId(), fileBucket,
              terraformPlanExecutionDetails.getProvisionerId());
          CGRestUtils.getResponse(
              fileService.get().deleteFile(terraformPlanExecutionDetails.getTfPlanJsonFieldId(), fileBucket));
        } catch (Exception e) {
          log.warn("Failed to remove terraform plan json file [{}] for provisioner [{}]",
              terraformPlanExecutionDetails.getTfPlanJsonFieldId(), terraformPlanExecutionDetails.getProvisionerId(),
              e);
        }
      }
    }
  }

  public TFPlanExecutionDetailsKey createTFPlanExecutionDetailsKey(Ambiance ambiance) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    return TFPlanExecutionDetailsKey.builder()
        .scope(Scope.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build())
        .pipelineExecutionId(planExecutionId)
        .build();
  }

  public List<TerraformPlanExecutionDetails> getAllPipelineTFPlanExecutionDetails(
      TFPlanExecutionDetailsKey tfPlanExecutionDetailsKey) {
    return terraformPlanExectionDetailsService.listAllPipelineTFPlanExecutionDetails(tfPlanExecutionDetailsKey);
  }

  public Map<EncryptionConfig, List<EncryptedRecordData>> getEncryptedTfPlanWithConfig(
      List<TerraformPlanExecutionDetails> terraformPlanExecutionDetailsList) {
    Map<EncryptionConfig, List<EncryptedRecordData>> map = new HashMap<>();

    for (TerraformPlanExecutionDetails executionDetails : terraformPlanExecutionDetailsList) {
      if (executionDetails.getEncryptionConfig() != null) {
        if (!map.isEmpty()) {
          boolean isGrouped = false;
          for (Map.Entry<EncryptionConfig, List<EncryptedRecordData>> entry : map.entrySet()) {
            if (executionDetails.getEncryptionConfig() instanceof SecretManagerConfig) {
              SecretManagerConfig secretManagerConfig = (SecretManagerConfig) executionDetails.getEncryptionConfig();
              String identifier = secretManagerConfig.getIdentifier();
              String projIdentifier = secretManagerConfig.getProjectIdentifier();
              String accIdentifier = secretManagerConfig.getAccountIdentifier();
              String orgIdentifier = secretManagerConfig.getOrgIdentifier();

              if (!isBlank(identifier) && !isBlank(projIdentifier) && !isBlank(accIdentifier)
                  && !isBlank(orgIdentifier)) {
                if (identifier.equals(((SecretManagerConfig) entry.getKey()).getNgMetadata().getIdentifier())
                    && projIdentifier.equals(
                        ((SecretManagerConfig) entry.getKey()).getNgMetadata().getProjectIdentifier())
                    && accIdentifier.equals(
                        ((SecretManagerConfig) entry.getKey()).getNgMetadata().getAccountIdentifier())
                    && orgIdentifier.equals(
                        ((SecretManagerConfig) entry.getKey()).getNgMetadata().getOrgIdentifier())) {
                  entry.getValue().add(executionDetails.getEncryptedTfPlan().get(0));
                  isGrouped = true;
                }
              }
            }
          }
          if (!isGrouped) {
            map.put(executionDetails.getEncryptionConfig(),
                new ArrayList<>(Arrays.asList(executionDetails.getEncryptedTfPlan().get(0))));
          }
        } else {
          map.put(executionDetails.getEncryptionConfig(),
              new ArrayList<>(Arrays.asList(executionDetails.getEncryptedTfPlan().get(0))));
        }
      }
    }
    return map;
  }

  public void cleanupTfPlanHumanReadable(List<TerraformPlanExecutionDetails> terraformPlanExecutionDetailsList) {
    for (TerraformPlanExecutionDetails terraformPlanExecutionDetails : terraformPlanExecutionDetailsList) {
      if (isNotEmpty(terraformPlanExecutionDetails.getTfHumanReadablePlanId())
          && isNotEmpty(terraformPlanExecutionDetails.getTfHumanReadablePlanFileBucket())) {
        FileBucket fileBucket = FileBucket.valueOf(terraformPlanExecutionDetails.getTfHumanReadablePlanFileBucket());
        try {
          log.info("Remove terraform plan human readable file [{}] from bucket [{}] for provisioner [{}]",
              terraformPlanExecutionDetails.getTfHumanReadablePlanId(), fileBucket,
              terraformPlanExecutionDetails.getProvisionerId());
          CGRestUtils.getResponse(
              fileService.get().deleteFile(terraformPlanExecutionDetails.getTfHumanReadablePlanId(), fileBucket));
        } catch (Exception e) {
          log.warn("Failed to remove terraform plan human readable file [{}] for provisioner [{}]",
              terraformPlanExecutionDetails.getTfHumanReadablePlanId(),
              terraformPlanExecutionDetails.getProvisionerId(), e);
        }
      }
    }
  }

  public String getTerraformPlanName(TerraformPlanCommand terraformPlanCommand, Ambiance ambiance, String provisionId) {
    String prefix =
        TerraformPlanCommand.DESTROY == terraformPlanCommand ? TF_DESTROY_NAME_PREFIX_NG : TF_NAME_PREFIX_NG;
    return format(prefix, ambiance.getPlanExecutionId(), provisionId).replaceAll("_", "-");
  }

  public EncryptionConfig getEncryptionConfig(Ambiance ambiance, TerraformPlanStepParameters planStepParameters) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        ParameterFieldHelper.getParameterFieldValue(planStepParameters.getConfiguration().getSecretManagerRef()),
        AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance));

    return SecretManagerConfigMapper.fromDTO(secretManagerClientService.getSecretManager(
        identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(),
        identifierRef.getIdentifier(), false));
  }

  public Map<String, String> getEnvironmentVariablesMap(Map<String, Object> inputVariables) {
    if (isEmpty(inputVariables)) {
      return new HashMap<>();
    }
    Map<String, String> res = new LinkedHashMap<>();
    inputVariables.keySet().forEach(
        key -> res.put(key, ((ParameterField<?>) inputVariables.get(key)).getValue().toString()));
    return res;
  }

  private GitStoreConfig getStoreConfigAtCommitId(StoreConfig storeConfig, String commitId) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig.cloneInternal();
    if (isEmpty(commitId) || FetchType.COMMIT == gitStoreConfig.getGitFetchType()) {
      return gitStoreConfig;
    }
    ParameterField<String> commitIdField = ParameterField.createValueField(commitId);
    switch (storeConfig.getKind()) {
      case ManifestStoreType.BITBUCKET: {
        BitbucketStore bitbucketStore = (BitbucketStore) gitStoreConfig;
        bitbucketStore.setBranch(ParameterField.ofNull());
        bitbucketStore.setGitFetchType(FetchType.COMMIT);
        bitbucketStore.setCommitId(commitIdField);
        break;
      }
      case ManifestStoreType.GITLAB: {
        GitLabStore gitLabStore = (GitLabStore) gitStoreConfig;
        gitLabStore.setBranch(ParameterField.ofNull());
        gitLabStore.setGitFetchType(FetchType.COMMIT);
        gitLabStore.setCommitId(commitIdField);
        break;
      }
      case ManifestStoreType.GIT: {
        GitStore gitStore = (GitStore) gitStoreConfig;
        gitStore.setBranch(ParameterField.ofNull());
        gitStore.setGitFetchType(FetchType.COMMIT);
        gitStore.setCommitId(commitIdField);
        break;
      }
      case ManifestStoreType.GITHUB: {
        GithubStore githubStore = (GithubStore) gitStoreConfig;
        githubStore.setBranch(ParameterField.ofNull());
        githubStore.setGitFetchType(FetchType.COMMIT);
        githubStore.setCommitId(commitIdField);
        break;
      }
      default: {
        log.warn(format("Unknown store kind: [%s]", storeConfig.getKind()));
        break;
      }
    }
    return gitStoreConfig;
  }

  public void saveRollbackDestroyConfigInherited(TerraformApplyStepParameters stepParameters, Ambiance ambiance) {
    TerraformInheritOutput inheritOutput = getSavedInheritOutput(
        ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), APPLY.name(), ambiance);

    TerraformConfig terraformConfig =
        TerraformConfig.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
            .entityId(
                generateFullIdentifier(getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance))
            .pipelineExecutionId(ambiance.getPlanExecutionId())
            .configFiles(
                inheritOutput.getConfigFiles() != null ? inheritOutput.getConfigFiles().toGitStoreConfigDTO() : null)
            .useConnectorCredentials(inheritOutput.isUseConnectorCredentials())
            .fileStoreConfig(inheritOutput.getFileStoreConfig() != null
                    ? inheritOutput.getFileStoreConfig().toFileStorageConfigDTO()
                    : null)
            .varFileConfigs(inheritOutput.getVarFileConfigs())
            .backendConfig(inheritOutput.getBackendConfig())
            .backendConfigFileConfig(inheritOutput.getBackendConfigurationFileConfig())
            .environmentVariables(inheritOutput.getEnvironmentVariables())
            .workspace(inheritOutput.getWorkspace())
            .targets(inheritOutput.getTargets())
            .build();

    terraformConfigDAL.saveTerraformConfig(terraformConfig);
  }

  public void validateApplyStepParamsInline(TerraformApplyStepParameters stepParameters) {
    Validator.notNullCheck("Apply Step Parameters are null", stepParameters);
    Validator.notNullCheck("Apply Step configuration is NULL", stepParameters.getConfiguration());
  }

  public void validateApplyStepConfigFilesInline(TerraformApplyStepParameters stepParameters) {
    Validator.notNullCheck("Apply Step Parameters are null", stepParameters);
    Validator.notNullCheck("Apply Step configuration is NULL", stepParameters.getConfiguration());
    TerraformExecutionDataParameters spec = stepParameters.getConfiguration().getSpec();
    Validator.notNullCheck("Apply Step Spec is NULL", spec);
    Validator.notNullCheck("Apply Step Spec does not have Config files", spec.getConfigFiles());
    Validator.notNullCheck("Apply Step Spec does not have Config files store", spec.getConfigFiles().getStore());
  }

  public void validateDestroyStepParamsInline(TerraformDestroyStepParameters stepParameters) {
    Validator.notNullCheck("Destroy Step Parameters are null", stepParameters);
    Validator.notNullCheck("Destroy Step configuration is NULL", stepParameters.getConfiguration());
  }

  public void validateDestroyStepConfigFilesInline(TerraformDestroyStepParameters stepParameters) {
    Validator.notNullCheck("Destroy Step Parameters are null", stepParameters);
    Validator.notNullCheck("Destroy Step configuration is NULL", stepParameters.getConfiguration());
    TerraformExecutionDataParameters spec = stepParameters.getConfiguration().getSpec();
    Validator.notNullCheck("Destroy Step Spec is NULL", spec);
    Validator.notNullCheck("Destroy Step Spec does not have Config files", spec.getConfigFiles());
    Validator.notNullCheck("Destroy Step Spec does not have Config files store", spec.getConfigFiles().getStore());
  }

  public void validatePlanStepConfigFiles(TerraformPlanStepParameters stepParameters) {
    Validator.notNullCheck("Plan Step Parameters are null", stepParameters);
    Validator.notNullCheck("Plan Step configuration is NULL", stepParameters.getConfiguration());
    Validator.notNullCheck("Plan Step does not have Config files", stepParameters.getConfiguration().getConfigFiles());
    Validator.notNullCheck(
        "Plan Step does not have Config files store", stepParameters.getConfiguration().getConfigFiles().getStore());
    Validator.notNullCheck("Plan Step does not have Plan Command", stepParameters.getConfiguration().getCommand());
    Validator.notNullCheck(
        "Plan Step does not have Secret Manager Ref", stepParameters.getConfiguration().getSecretManagerRef());
  }

  public void saveRollbackDestroyConfigInline(
      TerraformApplyStepParameters stepParameters, TerraformTaskNGResponse response, Ambiance ambiance) {
    validateApplyStepConfigFilesInline(stepParameters);
    TerraformStepConfigurationInterface configuration = stepParameters.getConfiguration();
    TerraformExecutionDataParameters spec = configuration.getSpec();
    TerraformConfigBuilder builder =
        TerraformConfig.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
            .entityId(generateFullIdentifier(
                ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance))
            .pipelineExecutionId(ambiance.getPlanExecutionId());

    StoreConfigWrapper store = spec.getConfigFiles().getStore();
    StoreConfigType storeConfigType = store.getType();
    switch (storeConfigType) {
      case GIT:
      case GITHUB:
      case GITLAB:
      case BITBUCKET:
        Map<String, String> commitIdMap = response.getCommitIdForConfigFilesMap();
        builder.configFiles(
            getStoreConfigAtCommitId(spec.getConfigFiles().getStore().getSpec(), commitIdMap.get(TF_CONFIG_FILES))
                .toGitStoreConfigDTO());

        builder.useConnectorCredentials(isExportCredentialForSourceModule(
            configuration.getSpec().getConfigFiles(), ExecutionNodeType.TERRAFORM_APPLY.getYamlType()));

        break;
      case ARTIFACTORY:
        builder.fileStoreConfig(((FileStorageStoreConfig) store.getSpec()).toFileStorageConfigDTO());
        break;
      default:
        throw new InvalidRequestException(format("Unsupported store type: [%s]", storeConfigType));
    }

    builder.varFileConfigs(toTerraformVarFileConfig(spec.getVarFiles(), response, ambiance))
        .backendConfig(getBackendConfig(spec.getBackendConfig()))
        .backendConfigFileConfig(toTerraformBackendConfigFileConfig(spec.getBackendConfig(), response))
        .environmentVariables(getEnvironmentVariablesMap(spec.getEnvironmentVariables()))
        .workspace(ParameterFieldHelper.getParameterFieldValue(spec.getWorkspace()))
        .targets(ParameterFieldHelper.getParameterFieldValue(spec.getTargets()))
        .isTerraformCloudCli(ParameterFieldHelper.getParameterFieldValue(spec.getIsTerraformCloudCli()));

    terraformConfigDAL.saveTerraformConfig(builder.build());
  }

  public String getBackendConfig(TerraformBackendConfig backendConfig) {
    if (backendConfig != null) {
      TerraformBackendConfigSpec terraformBackendConfigSpec = backendConfig.getTerraformBackendConfigSpec();
      if (terraformBackendConfigSpec instanceof InlineTerraformBackendConfigSpec) {
        return ParameterFieldHelper.getParameterFieldValue(
            ((InlineTerraformBackendConfigSpec) terraformBackendConfigSpec).getContent());
      }
    }
    return null;
  }

  public TerraformConfig getLastSuccessfulApplyConfig(TerraformDestroyStepParameters parameters, Ambiance ambiance) {
    String entityId = generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(parameters.getProvisionerIdentifier()), ambiance);
    Query<TerraformConfig> query =
        persistence.createQuery(TerraformConfig.class)
            .filter(TerraformConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
            .filter(TerraformConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
            .filter(TerraformConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
            .filter(TerraformConfigKeys.entityId, entityId)
            .order(Sort.descending(TerraformConfigKeys.createdAt));
    TerraformConfig terraformConfig = terraformConfigDAL.getTerraformConfig(query, ambiance);
    if (terraformConfig == null) {
      throw new InvalidRequestException(format("Terraform config for Last Apply not found: [%s]", entityId));
    }
    return terraformConfig;
  }

  public Map<String, Object> parseTerraformOutputs(String terraformOutputString) {
    Map<String, Object> outputs = new LinkedHashMap<>();
    if (isEmpty(terraformOutputString)) {
      return outputs;
    }
    try {
      TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
      Map<String, Object> json = new ObjectMapper().readValue(IOUtils.toInputStream(terraformOutputString), typeRef);

      json.forEach((key, object) -> outputs.put(key, ((Map<String, Object>) object).get("value")));

    } catch (IOException exception) {
      log.error("", exception);
    }
    return outputs;
  }

  public String getLatestFileId(String entityId) {
    try {
      return CGRestUtils.getResponse(fileService.get().getLatestFileId(entityId, FileBucket.TERRAFORM_STATE));
    } catch (Exception exception) {
      String message = format("Unable to call fileservice to fetch latest file id for entityId: [%s]", entityId);
      throw new InvalidRequestException(message, exception);
    }
  }

  public void saveTerraformConfig(TerraformConfig rollbackConfig, Ambiance ambiance) {
    TerraformConfig terraformConfig = TerraformConfig.builder()
                                          .accountId(AmbianceUtils.getAccountId(ambiance))
                                          .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
                                          .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
                                          .entityId(rollbackConfig.getEntityId())
                                          .pipelineExecutionId(ambiance.getPlanExecutionId())
                                          .configFiles(rollbackConfig.getConfigFiles())
                                          .fileStoreConfig(rollbackConfig.getFileStoreConfig())
                                          .varFileConfigs(rollbackConfig.getVarFileConfigs())
                                          .backendConfig(rollbackConfig.getBackendConfig())
                                          .environmentVariables(rollbackConfig.getEnvironmentVariables())
                                          .workspace(rollbackConfig.getWorkspace())
                                          .targets(rollbackConfig.getTargets())
                                          .isTerraformCloudCli(rollbackConfig.isTerraformCloudCli)
                                          .build();

    terraformConfigDAL.saveTerraformConfig(terraformConfig);
  }

  public void updateParentEntityIdAndVersion(String entityId, String stateFileId) {
    try {
      CGRestUtils.getResponse(fileService.get().updateParentEntityIdAndVersion(
          URLEncoder.encode(entityId, "UTF-8"), stateFileId, FileBucket.TERRAFORM_STATE));
    } catch (Exception ex) {
      log.error(format("EntityId and Version update failed for entityId: [%s], with error %s: ", entityId,
          ExceptionUtils.getMessage(ex)));
      throw new InvalidRequestException(
          format("Unable to update StateFile version for entityId: [%s], Please try re-running pipeline", entityId));
    }
  }

  public boolean isExportCredentialForSourceModule(TerraformConfigFilesWrapper configFiles, String type) {
    String description = String.format("%s step", type);
    return configFiles.getModuleSource() != null
        && !ParameterField.isNull(configFiles.getModuleSource().getUseConnectorCredentials())
        && CDStepHelper.getParameterFieldBooleanValue(
            configFiles.getModuleSource().getUseConnectorCredentials(), USE_CONNECTOR_CREDENTIALS, description);
  }

  // Conversion Methods

  public TerraformBackendConfigFileInfo toTerraformBackendFileInfo(
      TerraformBackendConfig backendConfig, Ambiance ambiance) {
    TerraformBackendConfigFileInfo fileInfo = null;
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), CDS_TERRAFORM_REMOTE_BACKEND_CONFIG_NG)) {
      return null;
    }
    if (backendConfig != null) {
      TerraformBackendConfigSpec spec = backendConfig.getTerraformBackendConfigSpec();
      if (spec instanceof InlineTerraformBackendConfigSpec) {
        String content =
            ParameterFieldHelper.getParameterFieldValue(((InlineTerraformBackendConfigSpec) spec).getContent());
        if (EmptyPredicate.isNotEmpty(content)) {
          fileInfo = InlineTerraformBackendConfigFileInfo.builder().backendConfigFileContent(content).build();
        }
      } else if (spec instanceof RemoteTerraformBackendConfigSpec) {
        StoreConfigWrapper storeConfigWrapper = ((RemoteTerraformBackendConfigSpec) spec).getStore();
        if (storeConfigWrapper != null) {
          StoreConfig storeConfig = storeConfigWrapper.getSpec();
          // Retrieve the files from the GIT stores
          GitFetchFilesConfig gitFetchFilesConfig =
              getGitFetchFilesConfig(storeConfig, ambiance, TF_BACKEND_CONFIG_FILE);
          // And retrive the files from the Files stores
          FileStoreFetchFilesConfig fileFetchFilesConfig =
              getFileStoreFetchFilesConfig(storeConfig, ambiance, TF_BACKEND_CONFIG_FILE);

          if (ManifestStoreType.HARNESS.equals(storeConfig.getKind())) {
            fileInfo = harnessFileStoreToInlineBackendConfig(storeConfig, ambiance);
          } else {
            fileInfo = RemoteTerraformBackendConfigFileInfo.builder()
                           .gitFetchFilesConfig(gitFetchFilesConfig)
                           .filestoreFetchFilesConfig(fileFetchFilesConfig)
                           .build();
          }
        }
      }
    }
    return fileInfo;
  }

  private InlineTerraformBackendConfigFileInfo harnessFileStoreToInlineBackendConfig(
      StoreConfig storeConfig, Ambiance ambiance) {
    String filePath = getLocalFileStoreDelegateConfigPath(storeConfig, ambiance);
    Optional<FileStoreNodeDTO> file = fileStoreService.getWithChildrenByPath(AmbianceUtils.getAccountId(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance), filePath, true);

    if (!file.isPresent()) {
      throw new InvalidRequestException(format("File not found in local file store, path [%s]", filePath));
    }

    FileStoreNodeDTO fileStoreNodeDTO = file.get();
    if (!(fileStoreNodeDTO instanceof FileNodeDTO)) {
      throw new InvalidRequestException(format("Requested file is a folder, path [%s]", filePath));
    }

    return InlineTerraformBackendConfigFileInfo.builder()
        .backendConfigFileContent(((FileNodeDTO) fileStoreNodeDTO).getContent())
        .build();
  }

  public List<TerraformVarFileInfo> toTerraformVarFileInfo(
      Map<String, TerraformVarFile> varFilesMap, Ambiance ambiance) {
    if (EmptyPredicate.isNotEmpty(varFilesMap)) {
      List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
      int i = 0;
      for (TerraformVarFile file : varFilesMap.values()) {
        if (file != null) {
          TerraformVarFileSpec spec = file.getSpec();
          if (spec instanceof InlineTerraformVarFileSpec) {
            String content =
                ParameterFieldHelper.getParameterFieldValue(((InlineTerraformVarFileSpec) spec).getContent());
            if (EmptyPredicate.isNotEmpty(content)) {
              varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent(content).build());
            }
          } else if (spec instanceof RemoteTerraformVarFileSpec) {
            StoreConfigWrapper storeConfigWrapper = ((RemoteTerraformVarFileSpec) spec).getStore();
            if (storeConfigWrapper != null) {
              i++;
              StoreConfig storeConfig = storeConfigWrapper.getSpec();
              // Retrieve the files from the GIT stores
              GitFetchFilesConfig gitFetchFilesConfig =
                  getGitFetchFilesConfig(storeConfig, ambiance, format(TerraformStepHelper.TF_VAR_FILES, i));
              // And retrive the files from the Files stores
              FileStoreFetchFilesConfig fileFetchFilesConfig =
                  getFileStoreFetchFilesConfig(storeConfig, ambiance, format(TerraformStepHelper.TF_VAR_FILES, i));
              varFileInfo.add(RemoteTerraformVarFileInfo.builder()
                                  .gitFetchFilesConfig(gitFetchFilesConfig)
                                  .filestoreFetchFilesConfig(fileFetchFilesConfig)
                                  .build());
            }
          }
        }
      }
      return varFileInfo;
    }
    return Collections.emptyList();
  }

  public TerraformBackendConfigFileConfig toTerraformBackendConfigFileConfig(
      TerraformBackendConfig backendConfig, TerraformTaskNGResponse response) {
    TerraformBackendConfigFileConfig fileConfig = null;
    if (backendConfig != null) {
      TerraformBackendConfigSpec spec = backendConfig.getTerraformBackendConfigSpec();
      if (spec instanceof InlineTerraformBackendConfigSpec) {
        String content =
            ParameterFieldHelper.getParameterFieldValue(((InlineTerraformBackendConfigSpec) spec).getContent());
        if (EmptyPredicate.isNotEmpty(content)) {
          fileConfig = TerraformInlineBackendConfigFileConfig.builder().backendConfigFileContent(content).build();
        }
      } else if (spec instanceof RemoteTerraformBackendConfigSpec) {
        StoreConfigWrapper storeConfigWrapper = ((RemoteTerraformBackendConfigSpec) spec).getStore();
        if (storeConfigWrapper != null) {
          StoreConfig storeConfig = storeConfigWrapper.getSpec();
          if (storeConfig.getKind().equals(ManifestStoreType.ARTIFACTORY)
              || storeConfig.getKind().equals(ManifestStoreType.HARNESS)) {
            fileConfig = TerraformRemoteBackendConfigFileConfig.builder()
                             .fileStoreConfigDTO(((FileStorageStoreConfig) storeConfig).toFileStorageConfigDTO())
                             .build();
          } else {
            GitStoreConfigDTO gitStoreConfigDTO = getStoreConfigAtCommitId(
                storeConfig, response.getCommitIdForConfigFilesMap().get(TF_BACKEND_CONFIG_FILE))
                                                      .toGitStoreConfigDTO();

            fileConfig = TerraformRemoteBackendConfigFileConfig.builder().gitStoreConfigDTO(gitStoreConfigDTO).build();
          }
        }
      }
    }
    return fileConfig;
  }

  public List<TerraformVarFileConfig> toTerraformVarFileConfig(
      Map<String, TerraformVarFile> varFilesMap, TerraformTaskNGResponse response, Ambiance ambiance) {
    if (EmptyPredicate.isNotEmpty(varFilesMap)) {
      List<TerraformVarFileConfig> varFileConfigs = new ArrayList<>();
      int i = 0;
      for (TerraformVarFile file : varFilesMap.values()) {
        if (file != null) {
          TerraformVarFileSpec spec = file.getSpec();
          if (spec instanceof InlineTerraformVarFileSpec) {
            String content =
                ParameterFieldHelper.getParameterFieldValue(((InlineTerraformVarFileSpec) spec).getContent());
            if (EmptyPredicate.isNotEmpty(content)) {
              varFileConfigs.add(TerraformInlineVarFileConfig.builder().varFileContent(content).build());
            }
          } else if (spec instanceof RemoteTerraformVarFileSpec) {
            StoreConfigWrapper storeConfigWrapper = ((RemoteTerraformVarFileSpec) spec).getStore();
            if (storeConfigWrapper != null) {
              i++;
              StoreConfig storeConfig = storeConfigWrapper.getSpec();
              if (storeConfig.getKind().equals(ManifestStoreType.ARTIFACTORY)) {
                varFileConfigs.add(
                    TerraformRemoteVarFileConfig.builder()
                        .fileStoreConfigDTO(((FileStorageStoreConfig) storeConfig).toFileStorageConfigDTO())
                        .build());
              } else {
                GitStoreConfigDTO gitStoreConfigDTO = getStoreConfigAtCommitId(
                    storeConfig, response.getCommitIdForConfigFilesMap().get(format(TF_VAR_FILES, i)))
                                                          .toGitStoreConfigDTO();

                varFileConfigs.add(TerraformRemoteVarFileConfig.builder().gitStoreConfigDTO(gitStoreConfigDTO).build());
              }
            }
          }
        }
      }
      return varFileConfigs;
    }
    return Collections.emptyList();
  }

  public List<TerraformVarFileInfo> prepareTerraformVarFileInfo(
      List<TerraformVarFileConfig> varFileConfigs, Ambiance ambiance) {
    if (EmptyPredicate.isNotEmpty(varFileConfigs)) {
      int i = 0;
      List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
      for (TerraformVarFileConfig fileConfig : varFileConfigs) {
        if (fileConfig instanceof TerraformInlineVarFileConfig) {
          varFileInfo.add(InlineTerraformVarFileInfo.builder()
                              .varFileContent(((TerraformInlineVarFileConfig) fileConfig).getVarFileContent())
                              .build());
        } else if (fileConfig instanceof TerraformRemoteVarFileConfig) {
          i++;
          RemoteTerraformVarFileInfoBuilder remoteTerraformVarFileInfoBuilder = RemoteTerraformVarFileInfo.builder();
          TerraformRemoteVarFileConfig terraformRemoteVarFileConfig = (TerraformRemoteVarFileConfig) fileConfig;
          if (terraformRemoteVarFileConfig.getGitStoreConfigDTO() != null) {
            GitStoreConfig gitStoreConfig =
                ((TerraformRemoteVarFileConfig) fileConfig).getGitStoreConfigDTO().toGitStoreConfig();
            remoteTerraformVarFileInfoBuilder.gitFetchFilesConfig(
                getGitFetchFilesConfig(gitStoreConfig, ambiance, format(TerraformStepHelper.TF_VAR_FILES, i)));
          }
          if (terraformRemoteVarFileConfig.getFileStoreConfigDTO() != null) {
            remoteTerraformVarFileInfoBuilder.filestoreFetchFilesConfig(getFileStoreFetchFilesConfig(
                terraformRemoteVarFileConfig.getFileStoreConfigDTO().toFileStorageStoreConfig(), ambiance,
                format(TerraformStepHelper.TF_VAR_FILES, i)));
          }
          varFileInfo.add(remoteTerraformVarFileInfoBuilder.build());
        }
      }
      return varFileInfo;
    }
    return Collections.emptyList();
  }

  public TerraformBackendConfigFileInfo prepareTerraformBackendConfigFileInfo(
      TerraformBackendConfigFileConfig bcFileConfig, Ambiance ambiance) {
    TerraformBackendConfigFileInfo fileInfo = null;
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), CDS_TERRAFORM_REMOTE_BACKEND_CONFIG_NG)) {
      return null;
    }
    if (bcFileConfig != null) {
      if (bcFileConfig instanceof TerraformInlineBackendConfigFileConfig) {
        fileInfo = InlineTerraformBackendConfigFileInfo.builder()
                       .backendConfigFileContent(
                           ((TerraformInlineBackendConfigFileConfig) bcFileConfig).getBackendConfigFileContent())
                       .build();
      } else if (bcFileConfig instanceof TerraformRemoteBackendConfigFileConfig) {
        RemoteTerraformBackendConfigFileInfoBuilder remoteTerraformBCFileInfoBuilder =
            RemoteTerraformBackendConfigFileInfo.builder();
        TerraformRemoteBackendConfigFileConfig terraformRemoteBCFileConfig =
            (TerraformRemoteBackendConfigFileConfig) bcFileConfig;
        if (terraformRemoteBCFileConfig.getGitStoreConfigDTO() != null) {
          GitStoreConfig gitStoreConfig =
              ((TerraformRemoteBackendConfigFileConfig) bcFileConfig).getGitStoreConfigDTO().toGitStoreConfig();
          remoteTerraformBCFileInfoBuilder.gitFetchFilesConfig(
              getGitFetchFilesConfig(gitStoreConfig, ambiance, format(TerraformStepHelper.TF_BACKEND_CONFIG_FILE)));
          fileInfo = remoteTerraformBCFileInfoBuilder.build();
        }
        if (terraformRemoteBCFileConfig.getFileStoreConfigDTO() != null) {
          remoteTerraformBCFileInfoBuilder.filestoreFetchFilesConfig(getFileStoreFetchFilesConfig(
              terraformRemoteBCFileConfig.getFileStoreConfigDTO().toFileStorageStoreConfig(), ambiance,
              format(TerraformStepHelper.TF_BACKEND_CONFIG_FILE)));
          if (HARNESS_STORE_TYPE.equals(terraformRemoteBCFileConfig.getFileStoreConfigDTO().getKind())) {
            fileInfo = harnessFileStoreToInlineBackendConfig(
                terraformRemoteBCFileConfig.getFileStoreConfigDTO().toFileStorageStoreConfig(), ambiance);
          } else {
            fileInfo = remoteTerraformBCFileInfoBuilder.build();
          }
        }
      }
    }
    return fileInfo;
  }

  public void cleanupAllTerraformPlanExecutionDetails(TFPlanExecutionDetailsKey tfPlanExecutionDetailsKey) {
    boolean deleteSuccess =
        terraformPlanExectionDetailsService.deleteAllTerraformPlanExecutionDetails(tfPlanExecutionDetailsKey);
    if (!deleteSuccess) {
      log.warn("Unable to delete the TerraformPlanExecutionDetails");
    }
  }

  public void cleanupTerraformVaultSecret(
      Ambiance ambiance, List<TerraformPlanExecutionDetails> terraformPlanExecutionDetailsList, String pipelineId) {
    Map<EncryptionConfig, List<EncryptedRecordData>> encryptedTfPlanWithConfig =
        getEncryptedTfPlanWithConfig(terraformPlanExecutionDetailsList);
    encryptedTfPlanWithConfig.forEach((encryptionConfig, listEncryptedPlan) -> {
      runCleanupTerraformSecretTask(ambiance, encryptionConfig, listEncryptedPlan, pipelineId);
    });
  }

  private void runCleanupTerraformSecretTask(Ambiance ambiance, EncryptionConfig encryptionConfig,
      List<EncryptedRecordData> encryptedRecordDataList, String cleanupSecretUuid) {
    DelegateTaskRequest delegateTaskRequest =
        DelegateTaskRequest.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .taskParameters(TerraformSecretCleanupTaskParameters.builder()
                                .encryptedRecordDataList(encryptedRecordDataList)
                                .encryptionConfig(encryptionConfig)
                                .cleanupUuid(cleanupSecretUuid)
                                .build())
            .taskType(TaskType.TERRAFORM_SECRET_CLEANUP_TASK_NG.name())
            .executionTimeout(Duration.ofMinutes(10))
            .taskSetupAbstraction(SetupAbstractionKeys.ng, "true")
            .logStreamingAbstractions(new LinkedHashMap<>() {
              { put(SetupAbstractionKeys.accountId, AmbianceUtils.getAccountId(ambiance)); }
            })
            .build();

    String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
    log.info("Task Successfully queued with taskId: {}", taskId);
    waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION, new TerraformSecretCleanupTaskNotifyCallback(), taskId);
  }

  public void checkIfTaskIsSupportedByDelegate(
      Ambiance ambiance, io.harness.delegate.TaskType taskType, String taskLogicName) {
    AccountId accountIdentifier = AccountId.newBuilder().setId(AmbianceUtils.getAccountId(ambiance)).build();

    boolean taskTypeSupported = delegateServiceGrpcClient.isTaskTypeSupported(accountIdentifier, taskType);
    if (!taskTypeSupported) {
      throw new InvalidRequestException(format("None of available delegates supports %s integration", taskLogicName));
    }
  }

  public void checkIfTerraformCloudCliIsEnabled(
      FeatureName featurename, boolean isTerraformCloudCli, Ambiance ambiance) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), featurename) && isTerraformCloudCli) {
      throw new AccessDeniedException(
          format("'%s' is not enabled for account '%s'. Please contact harness customer care to enable FF.",
              FeatureName.CD_TERRAFORM_CLOUD_CLI_NG.name(), AmbianceUtils.getAccountId(ambiance)),
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
  }

  public Map<String, String> getTerraformCliFlags(List<TerraformCliOptionFlag> commandFlags) {
    if (commandFlags == null) {
      return new HashMap<>();
    }

    Map<String, String> commandsValueMap = new HashMap<>();
    for (TerraformCliOptionFlag commandFlag : commandFlags) {
      commandsValueMap.put(commandFlag.getCommandType().name(), commandFlag.getFlag().getValue());
    }

    return commandsValueMap;
  }
}