/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import static io.harness.beans.FeatureName.CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_NG;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.cdng.provision.terraform.TerraformPlanCommand.APPLY;
import static io.harness.common.ParameterFieldHelper.getBooleanParameterFieldValue;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.pms.listener.NgOrchestrationNotifyEventListener.NG_ORCHESTRATION;
import static io.harness.validation.Validator.notEmptyCheck;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FileReference;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.beans.SecretManagerConfig;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.utils.ArtifactUtils;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.fileservice.FileServiceClientFactory;
import io.harness.cdng.k8s.K8sStepHelper;
import io.harness.cdng.k8s.beans.StepExceptionPassThroughData;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.ArtifactoryStorageConfigDTO;
import io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.FileStorageConfigDTO;
import io.harness.cdng.manifest.yaml.FileStorageStoreConfig;
import io.harness.cdng.manifest.yaml.GitLabStore;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.GitStoreConfigDTO;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.S3StorageConfigDTO;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.pipeline.executions.TerraformSecretCleanupTaskNotifyCallback;
import io.harness.cdng.provision.terraform.TerraformConfig.TerraformConfigBuilder;
import io.harness.cdng.provision.terraform.TerraformConfig.TerraformConfigKeys;
import io.harness.cdng.provision.terraform.TerraformInheritOutput.TerraformInheritOutputBuilder;
import io.harness.cdng.provision.terraform.executions.TFApplyExecutionDetailsKey;
import io.harness.cdng.provision.terraform.executions.TFPlanExecutionDetailsKey;
import io.harness.cdng.provision.terraform.executions.TerraformApplyExecutionDetails;
import io.harness.cdng.provision.terraform.executions.TerraformApplyExecutionDetailsService;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExectionDetailsService;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails;
import io.harness.cdng.provision.terraform.outcome.TerraformGitRevisionOutcome;
import io.harness.cdng.provision.terraform.output.TerraformHumanReadablePlanOutput;
import io.harness.cdng.provision.terraform.output.TerraformPlanJsonOutput;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFileDelegateConfig;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFilesResponse;
import io.harness.delegate.beans.aws.s3.AwsS3FetchFilesTaskParams;
import io.harness.delegate.beans.aws.s3.S3FileDetailRequest;
import io.harness.delegate.beans.aws.s3.S3FileDetailResponse;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreTFDelegateConfig;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.filestore.FileStoreFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig.GitFetchFilesConfigBuilder;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.delegate.task.terraform.InlineTerraformBackendConfigFileInfo;
import io.harness.delegate.task.terraform.InlineTerraformVarFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformBackendConfigFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformBackendConfigFileInfo.RemoteTerraformBackendConfigFileInfoBuilder;
import io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo;
import io.harness.delegate.task.terraform.RemoteTerraformVarFileInfo.RemoteTerraformVarFileInfoBuilder;
import io.harness.delegate.task.terraform.TerraformBackendConfigFileInfo;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters.TerraformTaskNGParametersBuilder;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.delegate.task.terraform.TerraformVarFileInfo;
import io.harness.delegate.task.terraform.cleanup.TerraformSecretCleanupTaskParameters;
import io.harness.delegate.task.terraform.provider.TerraformAwsProviderCredentialDelegateInfo;
import io.harness.delegate.task.terraform.provider.TerraformProviderCredentialDelegateInfo;
import io.harness.delegate.task.terraform.provider.TerraformProviderType;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.persistence.HPersistence;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepResponseBuilder;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.provision.TerraformConstants;
import io.harness.remote.client.CGRestUtils;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.serializer.KryoSerializer;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;
import io.harness.validation.Validator;
import io.harness.validator.NGRegexValidatorConstants;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.TaskType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_INFRA_PROVISIONERS})
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
  public static final String TF_JSON_OUTPUT_SECRET_FORMAT = "terraform_output_%s_%s";
  public static final String TF_ENCRYPTED_JSON_OUTPUT_NAME = "TF_JSON_OUTPUT_ENCRYPTED";
  public static final String TF_JSON_OUTPUT_SECRET_IDENTIFIER_FORMAT = "<+secrets.getValue(\"%s\")>";

  @Inject private HPersistence persistence;
  @Inject private K8sStepHelper k8sStepHelper;
  @Inject TerraformPlanExectionDetailsService terraformPlanExectionDetailsService;
  @Inject TerraformApplyExecutionDetailsService terraformApplyExecutionDetailsService;
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
  @Inject private NGEncryptedDataService ngEncryptedDataService;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private StepHelper stepHelper;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private SecretCrudService ngSecretService;

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
    return getGitFetchFilesConfig(store, ambiance, identifier, null, false);
  }

  public GitFetchFilesConfig getGitFetchFilesConfig(
      StoreConfig store, Ambiance ambiance, String identifier, String manifestType, boolean isVarFileOptional) {
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
    String repoName = gitStoreConfig.getRepoName() != null ? gitStoreConfig.getRepoName().getValue() : null;
    if (gitConfigDTO.getGitConnectionType() == GitConnectionType.ACCOUNT) {
      String repoUrl = getGitRepoUrl(gitConfigDTO, repoName);
      gitConfigDTO.setUrl(repoUrl);
      gitConfigDTO.setGitConnectionType(GitConnectionType.REPO);
    }
    List<String> paths = new ArrayList<>();
    if (TF_CONFIG_FILES.equals(identifier) || TF_BACKEND_CONFIG_FILE.equals(identifier)) {
      paths.add(getParameterFieldValue(gitStoreConfig.getFolderPath()));
    } else {
      paths.addAll(getParameterFieldValue(gitStoreConfig.getPaths()));
    }
    ScmConnector scmConnector = cdStepHelper.getScmConnector((ScmConnector) connectorDTO.getConnectorConfig(),
        basicNGAccessObject.getAccountIdentifier(), gitConfigDTO, repoName);
    List<EncryptedDataDetail> encryptedDataDetails =
        gitConfigAuthenticationInfoHelper.getEncryptedDataDetails(scmConnector, sshKeySpecDTO, basicNGAccessObject);
    GitStoreDelegateConfig gitStoreDelegateConfig = GitStoreDelegateConfig.builder()
                                                        .gitConfigDTO(scmConnector)
                                                        .sshKeySpecDTO(sshKeySpecDTO)
                                                        .encryptedDataDetails(encryptedDataDetails)
                                                        .fetchType(gitStoreConfig.getGitFetchType())
                                                        .branch(getParameterFieldValue(gitStoreConfig.getBranch()))
                                                        .commitId(getParameterFieldValue(gitStoreConfig.getCommitId()))
                                                        .paths(paths)
                                                        .connectorName(connectorDTO.getName())
                                                        .optional(isVarFileOptional)
                                                        .build();

    GitFetchFilesConfigBuilder builder = GitFetchFilesConfig.builder();

    if (manifestType != null) {
      builder.manifestType(manifestType);
    }
    builder.identifier(identifier).succeedIfFileNotFound(false).gitStoreDelegateConfig(gitStoreDelegateConfig);
    return builder.build();
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

  public TerraformProviderCredential toTerraformProviderCredential(
      @NonNull TerraformProviderCredentialConfig credentialConfig) {
    if (!TerraformProviderType.AWS.equals(credentialConfig.getType())) {
      throw new InvalidRequestException(
          String.format("Provider Type [%s] is not supported", credentialConfig.getType()));
    }

    TerraformAwsProviderCredentialConfig awsCredentialConfig = (TerraformAwsProviderCredentialConfig) credentialConfig;

    return TerraformProviderCredential.builder()
        .uuid(generateUuid())
        .type(awsCredentialConfig.getType())
        .spec(AWSIAMRoleCredentialSpec.builder()
                  .connectorRef(ParameterField.createValueField(awsCredentialConfig.getConnectorRef()))
                  .region(ParameterField.createValueField(awsCredentialConfig.getRegion()))
                  .roleArn(ParameterField.createValueField(awsCredentialConfig.getRoleArn()))
                  .build())
        .build();
  }

  @Nullable
  public TerraformProviderCredentialDelegateInfo getProviderCredentialDelegateInfo(
      TerraformProviderCredential providerCredential, Ambiance ambiance) {
    if (providerCredential == null || providerCredential.getSpec() == null) {
      return null;
    }
    List<EncryptedDataDetail> encryptedDataDetails;

    if (TerraformProviderType.AWS.equals(providerCredential.getType())) {
      AWSIAMRoleCredentialSpec spec = (AWSIAMRoleCredentialSpec) providerCredential.getSpec();
      ConnectorInfoDTO connectorDTO =
          cdStepHelper.getConnector(getParameterFieldValue(spec.getConnectorRef()), ambiance);
      if (!(connectorDTO.getConnectorConfig() instanceof AwsConnectorDTO)) {
        throw new InvalidRequestException("Connector provided for terraform Aws provider must be of type AWS");
      }

      NGAccess basicNGAccessObject = AmbianceUtils.getNgAccess(ambiance);
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(
          basicNGAccessObject, ((AwsConnectorDTO) connectorDTO.getConnectorConfig()).getCredential().getConfig());

      return TerraformAwsProviderCredentialDelegateInfo.builder()
          .encryptedDataDetails(encryptedDataDetails)
          .connectorDTO(connectorDTO)
          .roleArn(getParameterFieldValue(spec.getRoleArn()))
          .region(getParameterFieldValue(spec.getRegion()))
          .build();
    } else {
      throw new InvalidRequestException(
          "Explicit provider credentials are not supported for provider type" + providerCredential.getType());
    }
  }

  public FileStoreFetchFilesConfig getFileStoreFetchFilesConfig(
      StoreConfig store, Ambiance ambiance, String identifier) {
    if (store == null
        || !(ManifestStoreType.ARTIFACTORY.equals(store.getKind()) || ManifestStoreType.S3.equals(store.getKind()))) {
      return null;
    }
    String storeKind = store.getKind();
    ConnectorInfoDTO connectorDTO =
        cdStepHelper.getConnector(getParameterFieldValue(store.getConnectorReference()), ambiance);
    validateStoreConfig(storeKind, connectorDTO, identifier);
    NGAccess basicNGAccessObject = AmbianceUtils.getNgAccess(ambiance);
    FileStoreFetchFilesConfig fileStoreFetchFilesConfig;
    List<EncryptedDataDetail> encryptedDataDetails;
    switch (storeKind) {
      case ManifestStoreType.ARTIFACTORY:
        fileStoreFetchFilesConfig = getArtifactoryStoreDelegateConfig((ArtifactoryStoreConfig) store, identifier);
        encryptedDataDetails = secretManagerClientService.getEncryptionDetails(basicNGAccessObject,
            ((ArtifactoryConnectorDTO) connectorDTO.getConnectorConfig()).getAuth().getCredentials());
        break;
      case ManifestStoreType.S3:
        fileStoreFetchFilesConfig = getS3StoreDelegateConfig((S3StoreConfig) store, identifier);
        encryptedDataDetails = secretManagerClientService.getEncryptionDetails(
            basicNGAccessObject, ((AwsConnectorDTO) connectorDTO.getConnectorConfig()).getCredential().getConfig());
        break;
      default:
        throw new InvalidRequestException(format("Unsupported config type %s", storeKind));
    }
    fileStoreFetchFilesConfig.setConnectorDTO(connectorDTO);
    fileStoreFetchFilesConfig.setIdentifier(identifier);
    fileStoreFetchFilesConfig.setManifestStoreType(storeKind);
    fileStoreFetchFilesConfig.setEncryptedDataDetails(encryptedDataDetails);
    fileStoreFetchFilesConfig.setSucceedIfFileNotFound(false);
    return fileStoreFetchFilesConfig;
  }

  private S3StoreTFDelegateConfig getS3StoreDelegateConfig(S3StoreConfig s3StoreConfig, String identifier) {
    String region = getParameterFieldValue(s3StoreConfig.getRegion());
    notEmptyCheck("Region is empty", region);
    String bucket = getParameterFieldValue(s3StoreConfig.getBucketName());
    notEmptyCheck("Bucket name is empty", bucket);

    List<String> paths = new ArrayList<>();
    if (TF_CONFIG_FILES.equals(identifier)) {
      paths.add(getParameterFieldValue(s3StoreConfig.getFolderPath()));
    } else {
      paths.addAll(getParameterFieldValue(s3StoreConfig.getPaths()));
    }

    return S3StoreTFDelegateConfig.builder().region(region).bucketName(bucket).paths(paths).build();
  }

  private ArtifactoryStoreDelegateConfig getArtifactoryStoreDelegateConfig(
      ArtifactoryStoreConfig store, String identifier) {
    if (TerraformStepHelper.TF_CONFIG_FILES.equals(identifier)
        && getParameterFieldValue(store.getArtifactPaths()).size() > 1) {
      throw new InvalidRequestException("Config file should not contain more than one file path");
    }
    return ArtifactoryStoreDelegateConfig.builder()
        .repositoryName(getParameterFieldValue(store.getRepositoryName()))
        .artifacts(getParameterFieldValue(store.getArtifactPaths()))
        .build();
  }

  private void validateStoreConfig(StoreConfig storeConfig) {
    Validator.notNullCheck("StoreConfig is null", storeConfig);
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
      TerraformTaskNGResponse terraformTaskNGResponse, Ambiance ambiance,
      TerraformPassThroughData terraformPassThroughData) {
    validatePlanStepConfigFiles(planStepParameters);
    TerraformPlanExecutionDataParameters configuration = planStepParameters.getConfiguration();
    TerraformInheritOutputBuilder builder =
        TerraformInheritOutput.builder().workspace(getParameterFieldValue(configuration.getWorkspace()));
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
        builder.fileStorageConfigDTO(
            ((ArtifactoryStoreConfig) configuration.getConfigFiles().getStore().getSpec()).toFileStorageConfigDTO());
        break;
      case S3:
        S3StorageConfigDTO s3ConfigDTO =
            (S3StorageConfigDTO) ((S3StoreConfig) configuration.getConfigFiles().getStore().getSpec())
                .toFileStorageConfigDTO();
        Map<String, Map<String, String>> keyVersionMap = terraformTaskNGResponse.getKeyVersionMap();
        s3ConfigDTO.setVersions(isNotEmpty(keyVersionMap) ? keyVersionMap.get(TF_CONFIG_FILES) : null);
        builder.fileStorageConfigDTO(s3ConfigDTO);
        break;
      default:
        throw new InvalidRequestException(format("Unsupported store type: [%s]", storeConfigType));
    }

    if (terraformPassThroughData != null) {
      builder.varFileConfigs(toTerraformVarFileConfigWithPTD(configuration.getVarFiles(), terraformPassThroughData));
    } else {
      builder.varFileConfigs(toTerraformVarFileConfig(configuration.getVarFiles(), terraformTaskNGResponse));
    }

    if (configuration.getProviderCredential() != null) {
      builder.providerCredentialConfig(toTerraformProviderCredentialConfig(configuration.getProviderCredential()));
    }

    builder.backendConfig(getBackendConfig(configuration.getBackendConfig()))
        .backendConfigurationFileConfig(
            toTerraformBackendConfigFileConfig(configuration.getBackendConfig(), terraformTaskNGResponse))
        .environmentVariables(getEnvironmentVariablesMap(configuration.getEnvironmentVariables()))
        .targets(getParameterFieldValue(configuration.getTargets()))
        .encryptedTfPlan(terraformTaskNGResponse.getEncryptedTfPlan())
        .encryptionConfig(getEncryptionConfig(ambiance, planStepParameters))
        .planName(getTerraformPlanName(planStepParameters.getConfiguration().getCommand(), ambiance,
            planStepParameters.getProvisionerIdentifier().getValue()))
        .skipStateStorage(ParameterFieldHelper.getBooleanParameterFieldValue(
            planStepParameters.getConfiguration().getSkipStateStorage()));
    String fullEntityId =
        generateFullIdentifier(getParameterFieldValue(planStepParameters.getProvisionerIdentifier()), ambiance);
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

  @NotNull
  public Map<String, String> getRevisionsMap(
      LinkedHashMap<String, TerraformVarFile> varFiles, Map<String, String> commitIdForConfigFilesMap) {
    Map<String, String> outputKeys = new HashMap();
    if (isNotEmpty(commitIdForConfigFilesMap)) {
      outputKeys.put(TF_CONFIG_FILES, commitIdForConfigFilesMap.get(TF_CONFIG_FILES));
      outputKeys.put(TF_BACKEND_CONFIG_FILE, commitIdForConfigFilesMap.get(TF_BACKEND_CONFIG_FILE));
      int i = 0;
      for (Entry<String, TerraformVarFile> file : varFiles.entrySet()) {
        if (file.getValue().getSpec().getType().equals(TerraformVarFileTypes.Remote)) {
          i++;
          if (ManifestStoreType.isInGitSubset(
                  ((RemoteTerraformVarFileSpec) file.getValue().getSpec()).getStore().getSpec().getKind())) {
            outputKeys.put(file.getKey(), commitIdForConfigFilesMap.get(format(TF_VAR_FILES, i)));
          }
        }
      }
    }
    return outputKeys;
  }

  public void addTerraformRevisionOutcomeIfRequired(
      StepResponseBuilder stepResponseBuilder, Map<String, String> outputKeys) {
    if (isNotEmpty(outputKeys)) {
      stepResponseBuilder.stepOutcome(StepResponse.StepOutcome.builder()
                                          .name(TerraformGitRevisionOutcome.OUTCOME_NAME)
                                          .outcome(TerraformGitRevisionOutcome.builder().revisions(outputKeys).build())
                                          .build());
    }
  }

  @NotNull
  public Map<String, String> getRevisionsMap(
      List<TerraformVarFileConfig> varFileConfigs, Map<String, String> commitIdForConfigFilesMap) {
    Map<String, String> outputKeys = new HashMap();
    if (isNotEmpty(commitIdForConfigFilesMap)) {
      outputKeys.put(TF_CONFIG_FILES, commitIdForConfigFilesMap.get(TF_CONFIG_FILES));
      outputKeys.put(TF_BACKEND_CONFIG_FILE, commitIdForConfigFilesMap.get(TF_BACKEND_CONFIG_FILE));
      int i = 0;
      if (isNotEmpty(varFileConfigs)) {
        for (TerraformVarFileConfig file : varFileConfigs) {
          if (file instanceof TerraformRemoteVarFileConfig && isNotEmpty(file.getIdentifier())) {
            i++;
            if (((TerraformRemoteVarFileConfig) file).getGitStoreConfigDTO() != null) {
              outputKeys.put(file.getIdentifier(), commitIdForConfigFilesMap.get(format(TF_VAR_FILES, i)));
            }
          }
        }
      }
    }
    return outputKeys;
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

  public void saveTerraformApplyExecutionDetails(
      Ambiance ambiance, String provisionerIdentifier, String tfEncryptedJsonOutputSecretId) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();
    TerraformApplyExecutionDetails terraformApplyExecutionDetails =
        TerraformApplyExecutionDetails.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pipelineExecutionId(planExecutionId)
            .stageExecutionId(stageExecutionId)
            .provisionerId(provisionerIdentifier)
            .tfEncryptedJsonOutputSecretId(tfEncryptedJsonOutputSecretId)
            .build();

    terraformApplyExecutionDetailsService.save(terraformApplyExecutionDetails);
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

  public TFApplyExecutionDetailsKey createTFApplyExecutionDetailsKey(Ambiance ambiance) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    return TFApplyExecutionDetailsKey.builder()
        .scope(Scope.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build())
        .pipelineExecutionId(planExecutionId)
        .build();
  }

  public List<TerraformPlanExecutionDetails> getAllPipelineTFPlanExecutionDetails(
      TFPlanExecutionDetailsKey tfPlanExecutionDetailsKey) {
    return terraformPlanExectionDetailsService.listAllPipelineTFPlanExecutionDetails(tfPlanExecutionDetailsKey);
  }

  public List<TerraformApplyExecutionDetails> getAllPipelineTFApplyExecutionDetails(
      TFApplyExecutionDetailsKey tfApplyExecutionDetailsKey) {
    return terraformApplyExecutionDetailsService.listAllPipelineTFApplyExecutionDetails(tfApplyExecutionDetailsKey);
  }

  public Map<EncryptionConfig, List<EncryptedRecordData>> getEncryptedTfPlanWithConfig(
      List<TerraformPlanExecutionDetails> terraformPlanExecutionDetailsList) {
    Map<EncryptionConfig, List<EncryptedRecordData>> map = new HashMap<>();

    for (TerraformPlanExecutionDetails executionDetails : terraformPlanExecutionDetailsList) {
      if (executionDetails.getEncryptionConfig() != null) {
        if (!map.isEmpty()) {
          boolean isGrouped = false;
          for (Entry<EncryptionConfig, List<EncryptedRecordData>> entry : map.entrySet()) {
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
        getParameterFieldValue(planStepParameters.getConfiguration().getSecretManagerRef()),
        AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance));

    SecretManagerConfig secretManagerConfig =
        SecretManagerConfigMapper.fromDTO(secretManagerClientService.getSecretManager(
            identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
            identifierRef.getProjectIdentifier(), identifierRef.getIdentifier(), false));
    if (cdFeatureFlagHelper.isEnabled(
            AmbianceUtils.getAccountId(ambiance), CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_NG)
        && isHarnessSecretManager(secretManagerConfig)) {
      secretManagerConfig.maskSecrets();
    }
    return secretManagerConfig;
  }

  public boolean isHarnessSecretManager(SecretManagerConfig secretManagerConfig) {
    return secretManagerConfig != null && secretManagerConfig.isGlobalKms();
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
        getParameterFieldValue(stepParameters.getProvisionerIdentifier()), APPLY.name(), ambiance);

    FileStorageConfigDTO fileStorageConfigDTO = inheritOutput.getFileStorageConfigDTO();

    // this should be removed after some time
    FileStorageConfigDTO fileStoreConfigDTO =
        inheritOutput.getFileStoreConfig() != null ? inheritOutput.getFileStoreConfig().toFileStorageConfigDTO() : null;

    TerraformConfig terraformConfig =
        TerraformConfig.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
            .entityId(
                generateFullIdentifier(getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance))
            .pipelineExecutionId(AmbianceUtils.getPlanExecutionIdForExecutionMode(ambiance))
            .configFiles(
                inheritOutput.getConfigFiles() != null ? inheritOutput.getConfigFiles().toGitStoreConfigDTO() : null)
            .useConnectorCredentials(inheritOutput.isUseConnectorCredentials())
            .fileStoreConfig(fileStorageConfigDTO != null ? fileStorageConfigDTO : fileStoreConfigDTO)
            .varFileConfigs(inheritOutput.getVarFileConfigs())
            .backendConfig(inheritOutput.getBackendConfig())
            .backendConfigFileConfig(inheritOutput.getBackendConfigurationFileConfig())
            .environmentVariables(inheritOutput.getEnvironmentVariables())
            .workspace(inheritOutput.getWorkspace())
            .targets(inheritOutput.getTargets())
            .providerCredentialConfig(inheritOutput.getProviderCredentialConfig())
            .skipStateStorage(inheritOutput.isSkipStateStorage())
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

  public void saveRollbackDestroyConfigInline(TerraformApplyStepParameters stepParameters,
      TerraformTaskNGResponse response, Ambiance ambiance, TerraformPassThroughData terraformPassThroughData) {
    validateApplyStepConfigFilesInline(stepParameters);
    TerraformStepConfigurationInterface configuration = stepParameters.getConfiguration();
    TerraformExecutionDataParameters spec = configuration.getSpec();
    TerraformConfigBuilder builder =
        TerraformConfig.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
            .entityId(
                generateFullIdentifier(getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance))
            .pipelineExecutionId(AmbianceUtils.getPlanExecutionIdForExecutionMode(ambiance));

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
      case S3:
        S3StorageConfigDTO fileStorageConfigDTO =
            (S3StorageConfigDTO) ((S3StoreConfig) store.getSpec()).toFileStorageConfigDTO();
        Map<String, Map<String, String>> keyVersionMap = response.getKeyVersionMap();
        fileStorageConfigDTO.setVersions(isNotEmpty(keyVersionMap) ? keyVersionMap.get(TF_CONFIG_FILES) : null);
        builder.fileStoreConfig(fileStorageConfigDTO);
        break;
      default:
        throw new InvalidRequestException(format("Unsupported store type: [%s]", storeConfigType));
    }

    if (terraformPassThroughData != null) {
      builder.varFileConfigs(toTerraformVarFileConfigWithPTD(spec.getVarFiles(), terraformPassThroughData));
    } else {
      builder.varFileConfigs(toTerraformVarFileConfig(spec.getVarFiles(), response));
    }
    builder.backendConfig(getBackendConfig(spec.getBackendConfig()))
        .backendConfigFileConfig(toTerraformBackendConfigFileConfig(spec.getBackendConfig(), response))
        .environmentVariables(getEnvironmentVariablesMap(spec.getEnvironmentVariables()))
        .workspace(getParameterFieldValue(spec.getWorkspace()))
        .targets(getParameterFieldValue(spec.getTargets()))
        .isTerraformCloudCli(getParameterFieldValue(spec.getIsTerraformCloudCli()))
        .skipStateStorage(getBooleanParameterFieldValue(stepParameters.getConfiguration().getSkipStateStorage()));
    if (spec.getProviderCredential() != null) {
      builder.providerCredentialConfig(toTerraformProviderCredentialConfig(spec.getProviderCredential()));
    }

    terraformConfigDAL.saveTerraformConfig(builder.build());
  }

  public String getBackendConfig(TerraformBackendConfig backendConfig) {
    if (backendConfig != null) {
      TerraformBackendConfigSpec terraformBackendConfigSpec = backendConfig.getTerraformBackendConfigSpec();
      if (terraformBackendConfigSpec instanceof InlineTerraformBackendConfigSpec) {
        return getParameterFieldValue(((InlineTerraformBackendConfigSpec) terraformBackendConfigSpec).getContent());
      }
    }
    return null;
  }

  public TerraformConfig getLastSuccessfulApplyConfig(TerraformDestroyStepParameters parameters, Ambiance ambiance) {
    String entityId = generateFullIdentifier(getParameterFieldValue(parameters.getProvisionerIdentifier()), ambiance);
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

  public Map<String, Object> encryptTerraformJsonOutput(
      String terraformOutputString, Ambiance ambiance, String secretManagerRef, String provisionerId) {
    Map<String, Object> outputs = new LinkedHashMap<>();
    if (isEmpty(terraformOutputString)) {
      return outputs;
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    String projectIdentifier = AmbianceUtils.getProjectIdentifier(ambiance);

    IdentifierRef secretManagerIdentifierRef =
        IdentifierRefHelper.getIdentifierRef(secretManagerRef, accountId, orgIdentifier, projectIdentifier);

    String secretIdentifier =
        format(TF_JSON_OUTPUT_SECRET_FORMAT, provisionerId, RandomStringUtils.randomAlphanumeric(6));

    SecretDTOV2 tfJsonOutputSecretDTO =
        SecretDTOV2.builder()
            .name(secretIdentifier)
            .identifier(secretIdentifier)
            .orgIdentifier(orgIdentifier)
            .projectIdentifier(projectIdentifier)
            .type(SecretType.SecretText)
            .spec(SecretTextSpecDTO.builder()
                      .secretManagerIdentifier(secretManagerIdentifierRef.getIdentifier())
                      .valueType(ValueType.Inline)
                      .value(terraformOutputString)
                      .build())
            .build();

    try {
      ngSecretService.create(accountId, tfJsonOutputSecretDTO);
      outputs.put(TF_ENCRYPTED_JSON_OUTPUT_NAME, format(TF_JSON_OUTPUT_SECRET_IDENTIFIER_FORMAT, secretIdentifier));
      saveTerraformApplyExecutionDetails(ambiance, provisionerId, secretIdentifier);
    } catch (Exception exception) {
      log.error("Encryption of terraform json output failed with error: ", exception);
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
    TerraformConfig terraformConfig =
        TerraformConfig.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
            .entityId(rollbackConfig.getEntityId())
            .pipelineExecutionId(AmbianceUtils.getPlanExecutionIdForExecutionMode(ambiance))
            .configFiles(rollbackConfig.getConfigFiles())
            .fileStoreConfig(rollbackConfig.getFileStoreConfig())
            .varFileConfigs(rollbackConfig.getVarFileConfigs())
            .backendConfig(rollbackConfig.getBackendConfig())
            .backendConfigFileConfig(rollbackConfig.getBackendConfigFileConfig())
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
    if (backendConfig != null) {
      TerraformBackendConfigSpec spec = backendConfig.getTerraformBackendConfigSpec();
      if (spec instanceof InlineTerraformBackendConfigSpec) {
        String content = getParameterFieldValue(((InlineTerraformBackendConfigSpec) spec).getContent());
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
            String content = getParameterFieldValue(((InlineTerraformVarFileSpec) spec).getContent());
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

  public TerraformProviderCredentialConfig toTerraformProviderCredentialConfig(
      @NonNull TerraformProviderCredential providerCredential) {
    if (TerraformProviderType.AWS.equals(providerCredential.getType())) {
      AWSIAMRoleCredentialSpec awsIamRoleCredentialSpec = (AWSIAMRoleCredentialSpec) providerCredential.getSpec();
      return TerraformAwsProviderCredentialConfig.builder()
          .connectorRef(getParameterFieldValue(awsIamRoleCredentialSpec.getConnectorRef()))
          .region(getParameterFieldValue(awsIamRoleCredentialSpec.getRegion()))
          .roleArn(getParameterFieldValue(awsIamRoleCredentialSpec.getRoleArn()))
          .type(providerCredential.getType())
          .build();
    }
    return null;
  }

  public TerraformBackendConfigFileConfig toTerraformBackendConfigFileConfig(
      TerraformBackendConfig backendConfig, TerraformTaskNGResponse response) {
    TerraformBackendConfigFileConfig fileConfig = null;
    if (backendConfig != null) {
      TerraformBackendConfigSpec spec = backendConfig.getTerraformBackendConfigSpec();
      if (spec instanceof InlineTerraformBackendConfigSpec) {
        String content = getParameterFieldValue(((InlineTerraformBackendConfigSpec) spec).getContent());
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
          } else if (storeConfig.getKind().equals(ManifestStoreType.S3)) {
            S3StoreConfig s3StoreConfig = (S3StoreConfig) storeConfig;
            S3StorageConfigDTO fileStorageConfigDTO = (S3StorageConfigDTO) s3StoreConfig.toFileStorageConfigDTO();
            Map<String, Map<String, String>> keyVersionMap = response.getKeyVersionMap();
            fileStorageConfigDTO.setVersions(
                isNotEmpty(keyVersionMap) ? keyVersionMap.get(TF_BACKEND_CONFIG_FILE) : null);
            fileConfig =
                TerraformRemoteBackendConfigFileConfig.builder().fileStoreConfigDTO(fileStorageConfigDTO).build();
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
      Map<String, TerraformVarFile> varFilesMap, TerraformTaskNGResponse response) {
    if (EmptyPredicate.isNotEmpty(varFilesMap)) {
      List<TerraformVarFileConfig> varFileConfigs = new ArrayList<>();
      int i = 0;
      for (Entry<String, TerraformVarFile> file : varFilesMap.entrySet()) {
        if (file != null) {
          TerraformVarFileSpec spec = file.getValue().getSpec();
          if (spec instanceof InlineTerraformVarFileSpec) {
            String content = getParameterFieldValue(((InlineTerraformVarFileSpec) spec).getContent());
            if (EmptyPredicate.isNotEmpty(content)) {
              varFileConfigs.add(
                  TerraformInlineVarFileConfig.builder().varFileContent(content).identifier(file.getKey()).build());
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
                        .identifier(file.getKey())
                        .build());
              } else if (storeConfig.getKind().equals(ManifestStoreType.S3)) {
                S3StoreConfig s3StoreConfig = (S3StoreConfig) storeConfig;
                S3StorageConfigDTO fileStorageConfigDTO = (S3StorageConfigDTO) s3StoreConfig.toFileStorageConfigDTO();
                Map<String, Map<String, String>> keyVersionMap = response.getKeyVersionMap();
                fileStorageConfigDTO.setVersions(
                    isNotEmpty(keyVersionMap) ? keyVersionMap.get(format(TF_VAR_FILES, i)) : null);
                varFileConfigs.add(TerraformRemoteVarFileConfig.builder()
                                       .fileStoreConfigDTO(fileStorageConfigDTO)
                                       .identifier(file.getKey())
                                       .build());
              } else {
                GitStoreConfigDTO gitStoreConfigDTO = getStoreConfigAtCommitId(
                    storeConfig, response.getCommitIdForConfigFilesMap().get(format(TF_VAR_FILES, i)))
                                                          .toGitStoreConfigDTO();

                varFileConfigs.add(TerraformRemoteVarFileConfig.builder()
                                       .gitStoreConfigDTO(gitStoreConfigDTO)
                                       .identifier(file.getKey())
                                       .build());
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
      List<TerraformVarFileConfig> varFileConfigs, Ambiance ambiance, boolean useOriginalIdentifier) {
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
            String identifier;
            if (useOriginalIdentifier && isNotEmpty(fileConfig.getIdentifier())) {
              identifier = fileConfig.getIdentifier();
            } else {
              identifier = format(TerraformStepHelper.TF_VAR_FILES, i);
            }
            GitStoreConfig gitStoreConfig =
                ((TerraformRemoteVarFileConfig) fileConfig).getGitStoreConfigDTO().toGitStoreConfig();
            remoteTerraformVarFileInfoBuilder.gitFetchFilesConfig(getGitFetchFilesConfig(
                gitStoreConfig, ambiance, identifier, "GIT VAR_FILES", terraformRemoteVarFileConfig.isOptional()));
          }
          if (terraformRemoteVarFileConfig.getFileStoreConfigDTO() != null) {
            FileStoreFetchFilesConfig fileStoreFetchFilesConfig =
                prepareTerraformConfigFileInfo(terraformRemoteVarFileConfig.getFileStoreConfigDTO(), ambiance);
            if (useOriginalIdentifier) {
              fileStoreFetchFilesConfig.setIdentifier(terraformRemoteVarFileConfig.getIdentifier());
            }
            remoteTerraformVarFileInfoBuilder.filestoreFetchFilesConfig(fileStoreFetchFilesConfig);
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
          remoteTerraformBCFileInfoBuilder.filestoreFetchFilesConfig(
              prepareTerraformConfigFileInfo(terraformRemoteBCFileConfig.getFileStoreConfigDTO(), ambiance));
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

  public void cleanupAllTerraformApplyExecutionDetails(TFApplyExecutionDetailsKey tfApplyExecutionDetailsKey) {
    boolean deleteSuccess =
        terraformApplyExecutionDetailsService.deleteAllTerraformApplyExecutionDetails(tfApplyExecutionDetailsKey);
    if (!deleteSuccess) {
      log.warn("Unable to delete the TerraformApplyExecutionDetails");
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

  public void cleanupTerraformJsonOutputSecret(
      List<TerraformApplyExecutionDetails> terraformApplyExecutionDetailsList) {
    terraformApplyExecutionDetailsList.forEach(eD -> {
      if (isNotEmpty(eD.getTfEncryptedJsonOutputSecretId())) {
        ngSecretService.delete(eD.getAccountIdentifier(), eD.getOrgIdentifier(), eD.getProjectIdentifier(),
            eD.getTfEncryptedJsonOutputSecretId(), false);
      }
    });
  }

  private void runCleanupTerraformSecretTask(Ambiance ambiance, EncryptionConfig encryptionConfig,
      List<EncryptedRecordData> encryptedRecordDataList, String cleanupSecretUuid) {
    BaseNGAccess ngAccess = BaseNGAccess.builder()
                                .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
                                .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
                                .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
                                .build();
    Map<String, String> abstractions = ArtifactUtils.getTaskSetupAbstractions(ngAccess);

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
            .taskSetupAbstractions(abstractions)
            .logStreamingAbstractions(new LinkedHashMap<>() {
              { put(SetupAbstractionKeys.accountId, AmbianceUtils.getAccountId(ambiance)); }
            })
            .build();

    String taskId = delegateGrpcClientWrapper.submitAsyncTaskV2(delegateTaskRequest, Duration.ZERO);
    log.info("Task Successfully queued with taskId: {}", taskId);
    waitNotifyEngine.waitForAllOn(NG_ORCHESTRATION, new TerraformSecretCleanupTaskNotifyCallback(), taskId);
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

  public FileStoreFetchFilesConfig prepareTerraformConfigFileInfo(
      FileStorageConfigDTO fileStorageConfigDTO, Ambiance ambiance) {
    String kind = fileStorageConfigDTO.getKind();
    NGAccess basicNGAccessObject = AmbianceUtils.getNgAccess(ambiance);
    ConnectorInfoDTO connectorDTO;
    List<EncryptedDataDetail> encryptedDataDetails;
    FileStoreFetchFilesConfig fileStoreFetchFilesConfig;
    if (ManifestStoreType.ARTIFACTORY.equals(kind)) {
      ArtifactoryStorageConfigDTO artifactoryStorageConfigDTO = (ArtifactoryStorageConfigDTO) fileStorageConfigDTO;
      connectorDTO = cdStepHelper.getConnector(artifactoryStorageConfigDTO.getConnectorRef(), ambiance);
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(basicNGAccessObject,
          ((ArtifactoryConnectorDTO) connectorDTO.getConnectorConfig()).getAuth().getCredentials());
      fileStoreFetchFilesConfig = ArtifactoryStoreDelegateConfig.builder()
                                      .repositoryName(artifactoryStorageConfigDTO.getRepositoryName())
                                      .artifacts(artifactoryStorageConfigDTO.getArtifactPaths())
                                      .build();
    } else if (ManifestStoreType.S3.equals(kind)) {
      S3StorageConfigDTO s3StorageConfigDTO = (S3StorageConfigDTO) fileStorageConfigDTO;
      connectorDTO = cdStepHelper.getConnector(s3StorageConfigDTO.getConnectorRef(), ambiance);
      encryptedDataDetails = secretManagerClientService.getEncryptionDetails(
          basicNGAccessObject, ((AwsConnectorDTO) connectorDTO.getConnectorConfig()).getCredential().getConfig());
      fileStoreFetchFilesConfig = S3StoreTFDelegateConfig.builder()
                                      .region(s3StorageConfigDTO.getRegion())
                                      .bucketName(s3StorageConfigDTO.getBucket())
                                      .paths(isNotEmpty(s3StorageConfigDTO.getFolderPath())
                                              ? Collections.singletonList(s3StorageConfigDTO.getFolderPath())
                                              : s3StorageConfigDTO.getPaths())
                                      .versions(s3StorageConfigDTO.getVersions())
                                      .build();
    } else {
      return null;
    }
    fileStoreFetchFilesConfig.setIdentifier(TF_CONFIG_FILES);
    fileStoreFetchFilesConfig.setManifestStoreType(kind);
    fileStoreFetchFilesConfig.setConnectorDTO(connectorDTO);
    fileStoreFetchFilesConfig.setSucceedIfFileNotFound(false);
    fileStoreFetchFilesConfig.setEncryptedDataDetails(encryptedDataDetails);
    return fileStoreFetchFilesConfig;
  }

  private void validateStoreConfig(String storeKind, ConnectorInfoDTO connectorDTO, String identifier) {
    String validationMessage;
    switch (identifier) {
      case TerraformStepHelper.TF_CONFIG_FILES:
        validationMessage = "Config Files";
        break;
      case TerraformStepHelper.TF_BACKEND_CONFIG_FILE:
        validationMessage = "Backend Configuration File";
        break;
      default:
        validationMessage = format("Var Files with identifier: %s", identifier);
    }
    cdStepHelper.validateManifest(storeKind, connectorDTO, validationMessage);
  }

  public void validateSecretManager(Ambiance ambiance, IdentifierRef identifierRef) {
    boolean isSecretManagerReadOnly =
        ngEncryptedDataService.isSecretManagerReadOnly(identifierRef.getAccountIdentifier(),
            identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
    if (isSecretManagerReadOnly) {
      throw new InvalidRequestException(
          "Please configure a secret manager which allows to store terraform plan as a secret. Read-only secret manager is not allowed.");
    }
  }

  public boolean tfPlanEncryptionOnManager(String accountId, EncryptionConfig encryptionConfig) {
    return cdFeatureFlagHelper.isEnabled(accountId, CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_NG)
        && isHarnessSecretManager((SecretManagerConfig) encryptionConfig);
  }

  private List<AwsS3FetchFileDelegateConfig> getS3FetchFileDelegateConfigs(
      List<S3StoreTFDelegateConfig> s3FileConfigs) {
    return s3FileConfigs.stream()
        .map(s3StoreTFDelegateConfig
            -> AwsS3FetchFileDelegateConfig.builder()
                   .identifier(s3StoreTFDelegateConfig.getIdentifier())
                   .region(s3StoreTFDelegateConfig.getRegion())
                   .awsConnector((AwsConnectorDTO) s3StoreTFDelegateConfig.getConnectorDTO().getConnectorConfig())
                   .encryptionDetails(s3StoreTFDelegateConfig.getEncryptedDataDetails())
                   .fileDetails(s3StoreTFDelegateConfig.getPaths()
                                    .stream()
                                    .map(path
                                        -> S3FileDetailRequest.builder()
                                               .bucketName(s3StoreTFDelegateConfig.getBucketName())
                                               .fileKey(path)
                                               .build())
                                    .collect(Collectors.toList()))
                   .versions(s3StoreTFDelegateConfig.getVersions())
                   .build())
        .collect(Collectors.toList());
  }

  private List<S3StoreTFDelegateConfig> getS3FilesConfigs(List<TerraformVarFileInfo> varFilesInfo) {
    List<S3StoreTFDelegateConfig> S3FetchFilesConfigs = new ArrayList<>();

    varFilesInfo.forEach(terraformVarFileInfo -> {
      if (terraformVarFileInfo instanceof RemoteTerraformVarFileInfo) {
        RemoteTerraformVarFileInfo remoteTerraformVarFileInfo = (RemoteTerraformVarFileInfo) terraformVarFileInfo;
        if (remoteTerraformVarFileInfo.getFilestoreFetchFilesConfig() != null) {
          FileStoreFetchFilesConfig fileStoreFetchFilesConfig =
              remoteTerraformVarFileInfo.getFilestoreFetchFilesConfig();

          if (fileStoreFetchFilesConfig instanceof S3StoreTFDelegateConfig) {
            S3FetchFilesConfigs.add((S3StoreTFDelegateConfig) fileStoreFetchFilesConfig);
          }
        }
      }
    });
    return S3FetchFilesConfigs;
  }

  private List<GitFetchFilesConfig> getGitFilesConfigs(List<TerraformVarFileInfo> varFilesInfo) {
    List<GitFetchFilesConfig> gitFetchFilesConfigs = new ArrayList<>();

    varFilesInfo.forEach(terraformVarFileInfo -> {
      if (terraformVarFileInfo instanceof RemoteTerraformVarFileInfo) {
        RemoteTerraformVarFileInfo remoteTerraformVarFileInfo = (RemoteTerraformVarFileInfo) terraformVarFileInfo;
        if (remoteTerraformVarFileInfo.getGitFetchFilesConfig() != null) {
          gitFetchFilesConfigs.add(remoteTerraformVarFileInfo.getGitFetchFilesConfig());
        }
      }
    });
    return gitFetchFilesConfigs;
  }

  public boolean hasGitVarFiles(List<TerraformVarFileInfo> varFilesInfo) {
    return varFilesInfo.stream().anyMatch(terraformVarFileInfo -> {
      if (terraformVarFileInfo instanceof RemoteTerraformVarFileInfo) {
        RemoteTerraformVarFileInfo remoteTerraformVarFileInfo = (RemoteTerraformVarFileInfo) terraformVarFileInfo;
        return remoteTerraformVarFileInfo.getGitFetchFilesConfig() != null;
      }
      return false;
    });
  }

  public boolean hasS3VarFiles(List<TerraformVarFileInfo> varFilesInfo) {
    return varFilesInfo.stream().anyMatch(terraformVarFileInfo -> {
      if (terraformVarFileInfo instanceof RemoteTerraformVarFileInfo) {
        RemoteTerraformVarFileInfo remoteTerraformVarFileInfo = (RemoteTerraformVarFileInfo) terraformVarFileInfo;
        FileStoreFetchFilesConfig fileStoreFetchFilesConfig = remoteTerraformVarFileInfo.getFilestoreFetchFilesConfig();
        return fileStoreFetchFilesConfig instanceof S3StoreTFDelegateConfig;
      }
      return false;
    });
  }

  public TaskChainResponse getExceptionTaskChainResponse(
      Ambiance ambiance, UnitProgressData unitProgressData, Exception e) {
    return TaskChainResponse.builder()
        .chainEnd(true)
        .passThroughData(
            StepExceptionPassThroughData.builder()
                .unitProgressData(
                    cdStepHelper.completeUnitProgressData(unitProgressData, ambiance, ExceptionUtils.getMessage(e)))
                .errorMessage(e.getCause() != null ? String.format("%s: %s", e.getMessage(), e.getCause().getMessage())
                        : (e.getMessage() != null && !e.getMessage().isEmpty()) ? e.getMessage()
                                                                                : e.toString())
                .build())
        .build();
  }

  public List<TerraformVarFileConfig> toTerraformVarFileConfigWithPTD(
      Map<String, TerraformVarFile> varFilesMap, TerraformPassThroughData terraformPassThroughData) {
    if (EmptyPredicate.isNotEmpty(varFilesMap)) {
      List<TerraformVarFileConfig> varFileConfigs = new ArrayList<>();
      for (TerraformVarFile file : varFilesMap.values()) {
        if (file != null) {
          TerraformVarFileSpec spec = file.getSpec();
          if (spec instanceof InlineTerraformVarFileSpec) {
            String content = getParameterFieldValue(((InlineTerraformVarFileSpec) spec).getContent());
            if (EmptyPredicate.isNotEmpty(content)) {
              varFileConfigs.add(TerraformInlineVarFileConfig.builder()
                                     .identifier(file.getIdentifier())
                                     .varFileContent(content)
                                     .build());
            }
          } else if (spec instanceof RemoteTerraformVarFileSpec) {
            StoreConfigWrapper storeConfigWrapper = ((RemoteTerraformVarFileSpec) spec).getStore();
            if (storeConfigWrapper != null) {
              StoreConfig storeConfig = storeConfigWrapper.getSpec();
              if (storeConfig.getKind().equals(ManifestStoreType.ARTIFACTORY)) {
                varFileConfigs.add(
                    TerraformRemoteVarFileConfig.builder()
                        .identifier(file.getIdentifier())
                        .fileStoreConfigDTO(((FileStorageStoreConfig) storeConfig).toFileStorageConfigDTO())
                        .build());
              } else if (storeConfig.getKind().equals(ManifestStoreType.S3)) {
                S3StoreConfig s3StoreConfig = (S3StoreConfig) storeConfig;
                S3StorageConfigDTO fileStorageConfigDTO = (S3StorageConfigDTO) s3StoreConfig.toFileStorageConfigDTO();
                Map<String, Map<String, String>> keyVersionMap = terraformPassThroughData.getKeyVersionMap();
                fileStorageConfigDTO.setVersions(
                    isNotEmpty(keyVersionMap) ? keyVersionMap.get(file.getIdentifier()) : null);
                varFileConfigs.add(TerraformRemoteVarFileConfig.builder()
                                       .identifier(file.getIdentifier())
                                       .fileStoreConfigDTO(fileStorageConfigDTO)
                                       .build());
              } else {
                GitStoreConfigDTO gitStoreConfigDTO;
                String fetchedCommitId = getFetchedCommitId(terraformPassThroughData, file.getIdentifier());

                if (isNotBlank(fetchedCommitId)) {
                  gitStoreConfigDTO = getStoreConfigAtCommitId(storeConfig, fetchedCommitId).toGitStoreConfigDTO();
                } else {
                  GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig.cloneInternal();
                  gitStoreConfigDTO = gitStoreConfig.toGitStoreConfigDTO();
                }

                boolean isVarFileOptional = false;
                if (((RemoteTerraformVarFileSpec) spec).getOptional() != null) {
                  isVarFileOptional = ParameterFieldHelper.getBooleanParameterFieldValue(
                      ((RemoteTerraformVarFileSpec) spec).getOptional());
                }

                varFileConfigs.add(TerraformRemoteVarFileConfig.builder()
                                       .identifier(file.getIdentifier())
                                       .isOptional(isVarFileOptional)
                                       .gitStoreConfigDTO(gitStoreConfigDTO)
                                       .build());
              }
            }
          }
        }
      }
      return varFileConfigs;
    }
    return Collections.emptyList();
  }

  @VisibleForTesting
  protected String getFetchedCommitId(TerraformPassThroughData terraformPassThroughData, String fileIdentifier) {
    String fetchedCommitId = null;
    if (terraformPassThroughData.getFetchedCommitIdsMap() != null
        && isNotEmpty(terraformPassThroughData.getFetchedCommitIdsMap()) && isNotBlank(fileIdentifier)) {
      fetchedCommitId = terraformPassThroughData.getFetchedCommitIdsMap().get(fileIdentifier);
    }

    if (isNotBlank(fetchedCommitId)) {
      return fetchedCommitId;
    }

    if (terraformPassThroughData.getGitVarFilesFromMultipleRepo() != null && isNotBlank(fileIdentifier)
        && terraformPassThroughData.getGitVarFilesFromMultipleRepo().get(fileIdentifier) != null) {
      FetchFilesResult gitFetchFilesResult =
          terraformPassThroughData.getGitVarFilesFromMultipleRepo().get(fileIdentifier);
      if (gitFetchFilesResult != null && gitFetchFilesResult.getCommitResult() != null) {
        fetchedCommitId = gitFetchFilesResult.getCommitResult().getCommitId();
      }
    }
    return fetchedCommitId;
  }

  public StepResponse handleStepExceptionFailure(StepExceptionPassThroughData stepExceptionPassThroughData) {
    return cdStepHelper.handleStepExceptionFailure(stepExceptionPassThroughData);
  }

  public TaskChainResponse executeTerraformTask(TerraformTaskNGParameters terraformTaskNGParameters,
      StepBaseParameters stepElementParameters, Ambiance ambiance, TerraformPassThroughData terraformPassThroughData,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, String commandUnitName) {
    TaskData taskData =
        TaskData.builder()
            .async(true)
            .taskType(terraformTaskNGParameters.getDelegateTaskType().name())
            .timeout(StepUtils.getTimeoutMillis(stepElementParameters.getTimeout(), TerraformConstants.DEFAULT_TIMEOUT))
            .parameters(new Object[] {terraformTaskNGParameters})
            .build();

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, Collections.singletonList(commandUnitName),
        terraformTaskNGParameters.getDelegateTaskType().getDisplayName(),
        TaskSelectorYaml.toTaskSelector(delegateSelectors), stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .taskRequest(taskRequest)
        .passThroughData(terraformPassThroughData)
        .chainEnd(true)
        .build();
  }

  private TaskChainResponse getGitFetchFileTaskChainResponse(Ambiance ambiance,
      List<GitFetchFilesConfig> gitFetchFilesConfigs, StepBaseParameters stepElementParameters,
      TerraformPassThroughData passThroughData, String commandUnitName,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    GitFetchRequest gitFetchRequest = GitFetchRequest.builder()
                                          .gitFetchFilesConfigs(gitFetchFilesConfigs)
                                          .closeLogStream(!passThroughData.hasS3Files())
                                          .accountId(AmbianceUtils.getAccountId(ambiance))
                                          .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.GIT_FETCH_NEXT_GEN_TASK.name())
                                  .parameters(new Object[] {gitFetchRequest})
                                  .build();

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, Arrays.asList(K8sCommandUnitConstants.FetchFiles, commandUnitName),
        TaskType.GIT_FETCH_NEXT_GEN_TASK.getDisplayName(), TaskSelectorYaml.toTaskSelector(delegateSelectors),
        stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(passThroughData)
        .build();
  }

  private TaskChainResponse getS3FetchFileTaskChainResponse(Ambiance ambiance,
      List<AwsS3FetchFileDelegateConfig> awsS3FetchFileDelegateConfigs, StepBaseParameters stepElementParameters,
      TerraformPassThroughData passThroughData, CommandUnitsProgress commandUnitsProgress, String commandUnitName,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    AwsS3FetchFilesTaskParams awsS3FetchFilesTaskParams = AwsS3FetchFilesTaskParams.builder()
                                                              .fetchFileDelegateConfigs(awsS3FetchFileDelegateConfigs)
                                                              .shouldOpenLogStream(!passThroughData.hasGitFiles())
                                                              .closeLogStream(true)
                                                              .commandUnitsProgress(commandUnitsProgress)
                                                              .build();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(TaskType.FETCH_S3_FILE_TASK_NG.name())
                                  .parameters(new Object[] {awsS3FetchFilesTaskParams})
                                  .build();

    final TaskRequest taskRequest = TaskRequestsUtils.prepareCDTaskRequest(ambiance, taskData,
        referenceFalseKryoSerializer, Arrays.asList(K8sCommandUnitConstants.FetchFiles, commandUnitName),
        TaskType.FETCH_S3_FILE_TASK_NG.getDisplayName(), TaskSelectorYaml.toTaskSelector(delegateSelectors),
        stepHelper.getEnvironmentType(ambiance));

    return TaskChainResponse.builder()
        .chainEnd(false)
        .taskRequest(taskRequest)
        .passThroughData(passThroughData)
        .build();
  }

  public TaskChainResponse fetchRemoteVarFiles(TerraformPassThroughData terraformPassThroughData,
      List<TerraformVarFileInfo> varFilesInfo, Ambiance ambiance, StepBaseParameters stepElementParameters,
      String commandUnitName, ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    TaskChainResponse response = null;
    List<GitFetchFilesConfig> gitFetchFilesConfigs;
    List<S3StoreTFDelegateConfig> s3FileConfigs;

    if (terraformPassThroughData.hasGitFiles()) {
      gitFetchFilesConfigs = getGitFilesConfigs(varFilesInfo);
      if (isNotEmpty(gitFetchFilesConfigs)) {
        response = getGitFetchFileTaskChainResponse(ambiance, gitFetchFilesConfigs, stepElementParameters,
            terraformPassThroughData, commandUnitName, delegateSelectors);
      }
    } else if (terraformPassThroughData.hasS3Files()) {
      List<AwsS3FetchFileDelegateConfig> awsS3FetchFileDelegateConfigs;
      s3FileConfigs = getS3FilesConfigs(varFilesInfo);
      awsS3FetchFileDelegateConfigs = getS3FetchFileDelegateConfigs(s3FileConfigs);
      if (isNotEmpty(awsS3FetchFileDelegateConfigs)) {
        response = getS3FetchFileTaskChainResponse(ambiance, awsS3FetchFileDelegateConfigs, stepElementParameters,
            terraformPassThroughData, null, commandUnitName, delegateSelectors);
      }
    }

    return response;
  }

  private TaskChainResponse handleGitFetchResponse(Ambiance ambiance, StepBaseParameters stepElementParameters,
      TerraformPassThroughData terraformPassThroughData, GitFetchResponse responseData, String commandUnitName,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    terraformPassThroughData.setFetchedCommitIdsMap(responseData.getFetchedCommitIdsMap());
    terraformPassThroughData.setGitVarFilesFromMultipleRepo(responseData.getFilesFromMultipleRepo());
    UnitProgressData unitProgressData;
    unitProgressData = responseData.getUnitProgressData();

    TerraformTaskNGParametersBuilder builder = terraformPassThroughData.getTerraformTaskNGParametersBuilder();

    if (terraformPassThroughData.hasS3Files()) {
      List<S3StoreTFDelegateConfig> s3FileConfigs;
      List<AwsS3FetchFileDelegateConfig> awsS3FetchFileDelegateConfigs;
      s3FileConfigs = getS3FilesConfigs(builder.build().getVarFileInfos());
      awsS3FetchFileDelegateConfigs = getS3FetchFileDelegateConfigs(s3FileConfigs);

      return getS3FetchFileTaskChainResponse(ambiance, awsS3FetchFileDelegateConfigs, stepElementParameters,
          terraformPassThroughData, UnitProgressDataMapper.toCommandUnitsProgress(responseData.getUnitProgressData()),
          commandUnitName, delegateSelectors);
    } else {
      terraformPassThroughData.getUnitProgresses().addAll(unitProgressData.getUnitProgresses());
    }

    List<TerraformVarFileInfo> varFiles = getVarFilesInCorrectOrder(ambiance, terraformPassThroughData, builder);

    if (!varFiles.isEmpty()) {
      builder.varFileInfos(varFiles);
    }
    builder.commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(responseData.getUnitProgressData()));

    return executeTerraformTask(
        builder.build(), stepElementParameters, ambiance, terraformPassThroughData, delegateSelectors, commandUnitName);
  }

  private TaskChainResponse handleAwsS3FetchFileResponse(Ambiance ambiance, StepBaseParameters stepElementParameters,
      TerraformPassThroughData terraformPassThroughData, AwsS3FetchFilesResponse responseData, String commandUnitName,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    UnitProgressData unitProgressData;
    unitProgressData = responseData.getUnitProgressData();
    terraformPassThroughData.getUnitProgresses().addAll(unitProgressData.getUnitProgresses());
    terraformPassThroughData.setS3VarFilesDetails(responseData.getS3filesDetails());
    if (responseData.getKeyVersionMap() != null && isNotEmpty(responseData.getKeyVersionMap())) {
      terraformPassThroughData.getKeyVersionMap().putAll(responseData.getKeyVersionMap());
    }

    TerraformTaskNGParametersBuilder builder = terraformPassThroughData.getTerraformTaskNGParametersBuilder();

    List<TerraformVarFileInfo> varFiles = getVarFilesInCorrectOrder(ambiance, terraformPassThroughData, builder);

    if (!varFiles.isEmpty()) {
      builder.varFileInfos(varFiles);
    }
    builder.varFileInfos(varFiles);
    builder.commandUnitsProgress(UnitProgressDataMapper.toCommandUnitsProgress(responseData.getUnitProgressData()));

    return executeTerraformTask(
        builder.build(), stepElementParameters, ambiance, terraformPassThroughData, delegateSelectors, commandUnitName);
  }

  public List<TerraformVarFileInfo> toTerraformVarFileInfoWithIdentifierAndManifest(
      Map<String, TerraformVarFile> varFilesMap, Ambiance ambiance) {
    if (EmptyPredicate.isNotEmpty(varFilesMap)) {
      List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
      int i = 0;
      for (TerraformVarFile file : varFilesMap.values()) {
        if (file != null) {
          TerraformVarFileSpec spec = file.getSpec();
          if (spec instanceof InlineTerraformVarFileSpec) {
            String content = getParameterFieldValue(((InlineTerraformVarFileSpec) spec).getContent());
            if (EmptyPredicate.isNotEmpty(content)) {
              varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent(content).build());
            }
          } else if (spec instanceof RemoteTerraformVarFileSpec) {
            StoreConfigWrapper storeConfigWrapper = ((RemoteTerraformVarFileSpec) spec).getStore();
            if (storeConfigWrapper != null) {
              i++;
              StoreConfig storeConfig = storeConfigWrapper.getSpec();
              // Retrieve the files from the GIT stores

              boolean isVarFileOptional = false;
              if (((RemoteTerraformVarFileSpec) spec).getOptional() != null) {
                isVarFileOptional = ParameterFieldHelper.getBooleanParameterFieldValue(
                    ((RemoteTerraformVarFileSpec) spec).getOptional());
              }

              GitFetchFilesConfig gitFetchFilesConfig = getGitFetchFilesConfig(
                  storeConfig, ambiance, file.getIdentifier(), "GIT VAR_FILES", isVarFileOptional);
              // And retrieve the files from the Files stores
              FileStoreFetchFilesConfig fileFetchFilesConfig =
                  getFileStoreFetchFilesConfig(storeConfig, ambiance, file.getIdentifier());
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

  public List<TerraformVarFileInfo> getRemoteVarFilesInfo(
      Map<String, TerraformVarFile> varFilesMap, Ambiance ambiance) {
    if (EmptyPredicate.isNotEmpty(varFilesMap)) {
      List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
      int i = 0;
      for (TerraformVarFile file : varFilesMap.values()) {
        if (file != null) {
          TerraformVarFileSpec spec = file.getSpec();
          if (spec instanceof RemoteTerraformVarFileSpec) {
            StoreConfigWrapper storeConfigWrapper = ((RemoteTerraformVarFileSpec) spec).getStore();
            if (storeConfigWrapper != null) {
              i++;
              StoreConfig storeConfig = storeConfigWrapper.getSpec();
              // Retrieve the files from the GIT stores

              boolean isVarFileOptional = false;
              if (((RemoteTerraformVarFileSpec) spec).getOptional() != null) {
                isVarFileOptional = ParameterFieldHelper.getBooleanParameterFieldValue(
                    ((RemoteTerraformVarFileSpec) spec).getOptional());
              }

              GitFetchFilesConfig gitFetchFilesConfig = getGitFetchFilesConfig(
                  storeConfig, ambiance, file.getIdentifier(), "GIT VAR_FILES", isVarFileOptional);

              //  And retrieve the files from the Files stores
              FileStoreFetchFilesConfig fileFetchFilesConfig =
                  getFileStoreFetchFilesConfig(storeConfig, ambiance, file.getIdentifier());
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

  public TaskChainResponse executeNextLink(Ambiance ambiance, ThrowingSupplier<ResponseData> responseSupplier,
      PassThroughData passThroughData, ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      StepBaseParameters stepElementParameters, String commandUnitName) {
    TerraformPassThroughData terraformPassThroughData = (TerraformPassThroughData) passThroughData;

    UnitProgressData unitProgressData = null;

    try {
      ResponseData responseData = responseSupplier.get();

      if (responseData instanceof GitFetchResponse) {
        unitProgressData = ((GitFetchResponse) responseData).getUnitProgressData();
        return handleGitFetchResponse(ambiance, stepElementParameters, terraformPassThroughData,
            (GitFetchResponse) responseData, commandUnitName, delegateSelectors);
      } else if (responseData instanceof AwsS3FetchFilesResponse) {
        unitProgressData = ((AwsS3FetchFilesResponse) responseData).getUnitProgressData();
        return handleAwsS3FetchFileResponse(ambiance, stepElementParameters, terraformPassThroughData,
            (AwsS3FetchFilesResponse) responseData, commandUnitName, delegateSelectors);
      } else {
        String errorMessage = "Unknown Error";
        return TaskChainResponse.builder()
            .chainEnd(true)
            .passThroughData(StepExceptionPassThroughData.builder()
                                 .unitProgressData(UnitProgressData.builder().unitProgresses(new ArrayList<>()).build())
                                 .errorMessage(errorMessage)
                                 .build())
            .build();
      }
    } catch (TaskNGDataException e) {
      log.error(format("Exception in terraform %s step: %s", commandUnitName, e.getMessage()));
      return getExceptionTaskChainResponse(ambiance, e.getCommandUnitsProgress(), e);
    } catch (Exception e) {
      log.error(format("Exception in terraform %s step: %s", commandUnitName, e.getMessage()));
      return getExceptionTaskChainResponse(ambiance, unitProgressData, e);
    }
  }

  @NotNull
  public Map<String, String> getRevisionsMap(
      TerraformPassThroughData terraformPassThroughData, TerraformTaskNGResponse terraformTaskNGResponse) {
    Map<String, String> outputKeys = new HashMap<>();
    if (isNotEmpty(terraformTaskNGResponse.getCommitIdForConfigFilesMap())) {
      outputKeys.put(TF_CONFIG_FILES, terraformTaskNGResponse.getCommitIdForConfigFilesMap().get(TF_CONFIG_FILES));
      outputKeys.put(
          TF_BACKEND_CONFIG_FILE, terraformTaskNGResponse.getCommitIdForConfigFilesMap().get(TF_BACKEND_CONFIG_FILE));
    }
    if (isNotEmpty(terraformPassThroughData.getFetchedCommitIdsMap())) {
      outputKeys.putAll(terraformPassThroughData.getFetchedCommitIdsMap());
    }
    return outputKeys;
  }

  private List<GitFile> getGitFetchFilesFromMultipleRepos(
      Map<String, FetchFilesResult> gitVarFilesFromMultipleRepo, String gitVarFileIdentifier) {
    Map<String, FetchFilesResult> filteredMapByIdentifier =
        gitVarFilesFromMultipleRepo.entrySet()
            .stream()
            .filter(x -> x.getKey().equals(gitVarFileIdentifier))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    return filteredMapByIdentifier.values()
        .stream()
        .map(FetchFilesResult::getFiles)
        .flatMap(Collection::stream)
        .collect(Collectors.toList());
  }

  private List<S3FileDetailResponse> getS3RemoteFilesContentFiltered(
      Map<String, List<S3FileDetailResponse>> s3filesDetailsMap, String s3VarFileIdentifier) {
    Map<String, List<S3FileDetailResponse>> s3filesDetails =
        s3filesDetailsMap.entrySet()
            .stream()
            .filter(x -> x.getKey().equals(s3VarFileIdentifier))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    return s3filesDetails.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
  }

  @VisibleForTesting
  protected List<TerraformVarFileInfo> getVarFilesWithStepIdentifiersOrder(Ambiance ambiance,
      TerraformPassThroughData terraformPassThroughData, TerraformTaskNGParametersBuilder tfTaskParametersBuilder) {
    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();
    if (EmptyPredicate.isNotEmpty(terraformPassThroughData.getOriginalStepVarFiles())) {
      for (TerraformVarFile file : terraformPassThroughData.getOriginalStepVarFiles().values()) {
        if (file != null) {
          String varFileIdentifier = file.getIdentifier();
          TerraformVarFileSpec spec = file.getSpec();
          if (spec instanceof InlineTerraformVarFileSpec) {
            String content = getParameterFieldValue(((InlineTerraformVarFileSpec) spec).getContent());
            varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent(content).build());
          } else if (spec instanceof RemoteTerraformVarFileSpec) {
            StoreConfigWrapper storeConfigWrapper = ((RemoteTerraformVarFileSpec) spec).getStore();
            if (storeConfigWrapper != null) {
              StoreConfig storeConfig = storeConfigWrapper.getSpec();
              if (storeConfig != null && ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
                List<GitFile> gitVarFiles = getGitFetchFilesFromMultipleRepos(
                    terraformPassThroughData.getGitVarFilesFromMultipleRepo(), varFileIdentifier);
                gitVarFiles.forEach(gitFile -> varFileInfo.add(createInlineAndUpdateExpressionGit(ambiance, gitFile)));
              } else if (storeConfig != null && ManifestStoreType.S3.equals(storeConfig.getKind())) {
                List<S3FileDetailResponse> s3VarFilesResponse =
                    getS3RemoteFilesContentFiltered(terraformPassThroughData.getS3VarFilesDetails(), varFileIdentifier);
                s3VarFilesResponse.forEach(
                    s3FileResponse -> varFileInfo.add(createInlineAndUpdateExpressionS3(ambiance, s3FileResponse)));
              } else if (storeConfig != null && ManifestStoreType.ARTIFACTORY.equals(storeConfig.getKind())) {
                TerraformVarFileInfo artifactoryVarFileInfo =
                    getArtifactoryVarFile(tfTaskParametersBuilder.build().getVarFileInfos(), varFileIdentifier);
                if (artifactoryVarFileInfo != null) {
                  varFileInfo.add(artifactoryVarFileInfo);
                }
              }
            }
          }
        }
      }
    }
    return varFileInfo;
  }

  @VisibleForTesting
  protected List<TerraformVarFileInfo> getVarFilesWithInheritedConfigsOrder(Ambiance ambiance,
      TerraformPassThroughData terraformPassThroughData, TerraformTaskNGParametersBuilder tfTaskParametersBuilder) {
    List<TerraformVarFileInfo> varFileInfo = new ArrayList<>();

    List<TerraformVarFileConfig> terraformVarFileConfigs = terraformPassThroughData.getOriginalVarFileConfigs();

    terraformVarFileConfigs.forEach(terraformVarFileConfig -> {
      String varFileIdentifier = terraformVarFileConfig.getIdentifier();
      if (terraformVarFileConfig instanceof TerraformInlineVarFileConfig) {
        TerraformInlineVarFileConfig inlineVarFileConfig = (TerraformInlineVarFileConfig) terraformVarFileConfig;
        String content = inlineVarFileConfig.getVarFileContent();
        varFileInfo.add(InlineTerraformVarFileInfo.builder().varFileContent(content).build());
      } else if (terraformVarFileConfig instanceof TerraformRemoteVarFileConfig) {
        TerraformRemoteVarFileConfig terraformRemoteVarFileConfig =
            (TerraformRemoteVarFileConfig) terraformVarFileConfig;

        if (terraformRemoteVarFileConfig.getGitStoreConfigDTO() != null) {
          List<GitFile> gitVarFiles = getGitFetchFilesFromMultipleRepos(
              terraformPassThroughData.getGitVarFilesFromMultipleRepo(), varFileIdentifier);
          gitVarFiles.forEach(gitFile -> varFileInfo.add(createInlineAndUpdateExpressionGit(ambiance, gitFile)));
        } else if (terraformRemoteVarFileConfig.getFileStoreConfigDTO() != null) {
          FileStorageConfigDTO fileStorageConfigDTO = terraformRemoteVarFileConfig.getFileStoreConfigDTO();

          if (fileStorageConfigDTO.getKind().equals(ManifestStoreType.S3)) {
            List<S3FileDetailResponse> s3VarFilesResponse =
                getS3RemoteFilesContentFiltered(terraformPassThroughData.getS3VarFilesDetails(), varFileIdentifier);
            s3VarFilesResponse.forEach(
                s3FileResponse -> varFileInfo.add(createInlineAndUpdateExpressionS3(ambiance, s3FileResponse)));
          } else if (fileStorageConfigDTO.getKind().equals(ManifestStoreType.ARTIFACTORY)) {
            TerraformVarFileInfo artifactoryVarFileInfo =
                getArtifactoryVarFile(tfTaskParametersBuilder.build().getVarFileInfos(), varFileIdentifier);
            if (artifactoryVarFileInfo != null) {
              varFileInfo.add(artifactoryVarFileInfo);
            }
          }
        }
      }
    });
    return varFileInfo;
  }

  private TerraformVarFileInfo createInlineAndUpdateExpressionGit(Ambiance ambiance, GitFile gitFile) {
    return InlineTerraformVarFileInfo.builder()
        .varFileContent(cdExpressionResolver.updateExpressions(ambiance, gitFile.getFileContent()).toString())
        .filePath(gitFile.getFilePath())
        .build();
  }

  private TerraformVarFileInfo createInlineAndUpdateExpressionS3(
      Ambiance ambiance, S3FileDetailResponse varFileContent) {
    return InlineTerraformVarFileInfo.builder()
        .varFileContent(cdExpressionResolver.updateExpressions(ambiance, varFileContent.getFileContent()).toString())
        .filePath(varFileContent.getFileKey())
        .build();
  }

  private TerraformVarFileInfo getArtifactoryVarFile(
      List<TerraformVarFileInfo> terraformVarFileInfos, String varFileIdentifier) {
    return terraformVarFileInfos.stream()
        .filter(terraformVarFileInfo -> {
          if (terraformVarFileInfo instanceof RemoteTerraformVarFileInfo) {
            RemoteTerraformVarFileInfo remoteTerraformVarFileInfo = (RemoteTerraformVarFileInfo) terraformVarFileInfo;
            if (remoteTerraformVarFileInfo.getFilestoreFetchFilesConfig() instanceof ArtifactoryStoreDelegateConfig) {
              FileStoreFetchFilesConfig fileStoreFetchFilesConfig =
                  remoteTerraformVarFileInfo.getFilestoreFetchFilesConfig();
              return fileStoreFetchFilesConfig.getIdentifier().equals(varFileIdentifier);
            }
          }
          return false;
        })
        .findFirst()
        .orElse(null);
  }

  private List<TerraformVarFileInfo> getVarFilesInCorrectOrder(
      Ambiance ambiance, TerraformPassThroughData terraformPassThroughData, TerraformTaskNGParametersBuilder builder) {
    List<TerraformVarFileInfo> varFiles;
    if (!terraformPassThroughData.getOriginalStepVarFiles().isEmpty()) {
      varFiles = getVarFilesWithStepIdentifiersOrder(ambiance, terraformPassThroughData, builder);
    } else {
      varFiles = getVarFilesWithInheritedConfigsOrder(ambiance, terraformPassThroughData, builder);
    }
    return varFiles;
  }
}