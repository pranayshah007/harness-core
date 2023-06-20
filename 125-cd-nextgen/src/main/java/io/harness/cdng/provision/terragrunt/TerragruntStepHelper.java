/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/*

 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terragrunt;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_NG;
import static io.harness.cdng.provision.terraform.TerraformPlanCommand.APPLY;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.validation.Validator.notEmptyCheck;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.SecretManagerConfig;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.fileservice.FileServiceClientFactory;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.GitLabStore;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExectionDetailsService;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails;
import io.harness.cdng.provision.terraform.output.TerraformPlanJsonOutput;
import io.harness.cdng.provision.terragrunt.TerragruntConfig.TerragruntConfigBuilder;
import io.harness.cdng.provision.terragrunt.TerragruntConfig.TerragruntConfigKeys;
import io.harness.cdng.provision.terragrunt.TerragruntInheritOutput.TerragruntInheritOutputBuilder;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.validator.scmValidators.GitConfigAuthenticationInfoHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.InlineFileConfig;
import io.harness.delegate.beans.storeconfig.InlineStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.terragrunt.request.TerragruntRunConfiguration;
import io.harness.delegate.beans.terragrunt.request.TerragruntTaskRunType;
import io.harness.delegate.beans.terragrunt.response.TerragruntApplyTaskResponse;
import io.harness.delegate.beans.terragrunt.response.TerragruntPlanTaskResponse;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.CGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.utils.IdentifierRefHelper;
import io.harness.validation.Validator;
import io.harness.validator.NGRegexValidatorConstants;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
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
@OwnedBy(CDP)
public class TerragruntStepHelper {
  public static final String USE_CONNECTOR_CREDENTIALS = "useConnectorCredentials";
  public static final String TG_CONFIG_FILES = "TG_CONFIG_FILES";
  public static final String TF_BACKEND_CONFIG_FILE = "TF_BACKEND_CONFIG_FILE";
  public static final String TF_VAR_FILES = "TF_VAR_FILES_%s";
  private static final String TG_INHERIT_OUTPUT_FORMAT = "tgInheritOutput_%s_%s";
  public static final String DEFAULT_TIMEOUT = "10m";
  public static final String TERRAGRUNT_FILE_NAME_FORMAT = "terragrunt-${UUID}.tfvars";
  public static final String TG_DESTROY_NAME_PREFIX_NG = "tgDestroyPlan_%s_%s";
  public static final String TG_NAME_PREFIX_NG = "tgPlan_%s_%s";

  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private FileServiceClientFactory fileService;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject TerraformPlanExectionDetailsService terraformPlanExectionDetailsService;
  @Inject public TerragruntConfigDAL terragruntConfigDAL;
  @Inject private HPersistence persistence;

  public static StepType addStepType(String yamlType) {
    return StepType.newBuilder().setType(yamlType).setStepCategory(StepCategory.STEP).build();
  }

  public static void addConnectorRef(
      Map<String, ParameterField<String>> connectorRefMap, TerragruntStepConfiguration terragruntStepConfiguration) {
    if (terragruntStepConfiguration.terragruntStepConfigurationType == TerragruntStepConfigurationType.INLINE) {
      TerragruntExecutionData terragruntExecutionData = terragruntStepConfiguration.terragruntExecutionData;

      connectorRefMap.put("configuration.spec.configFiles.store.spec.connectorRef",
          terragruntExecutionData.getTerragruntConfigFilesWrapper().store.getSpec().getConnectorReference());

      List<TerragruntVarFileWrapper> terragruntVarFiles = terragruntExecutionData.getTerragruntVarFiles();
      addConnectorRefFromVarFiles(terragruntVarFiles, connectorRefMap);

      TerragruntBackendConfig terragruntBackendConfig = terragruntExecutionData.getTerragruntBackendConfig();
      addConnectorRefFromBackendConfig(terragruntBackendConfig, connectorRefMap);
    }
  }

  public static void addConnectorRefFromVarFiles(
      List<TerragruntVarFileWrapper> terragruntVarFiles, Map<String, ParameterField<String>> connectorRefMap) {
    if (EmptyPredicate.isNotEmpty(terragruntVarFiles)) {
      for (TerragruntVarFileWrapper terragruntVarFile : terragruntVarFiles) {
        if (terragruntVarFile.getVarFile().getType().equals(TerragruntVarFileTypes.Remote)) {
          connectorRefMap.put(
              "configuration.varFiles." + terragruntVarFile.getVarFile().identifier + ".spec.store.spec.connectorRef",
              ((RemoteTerragruntVarFileSpec) terragruntVarFile.varFile.spec).store.getSpec().getConnectorReference());
        }
      }
    }
  }

  public static void addConnectorRefFromBackendConfig(
      TerragruntBackendConfig terragruntBackendConfig, Map<String, ParameterField<String>> connectorRefMap) {
    if (terragruntBackendConfig != null
        && terragruntBackendConfig.getType().equals(TerragruntBackendFileTypes.Remote)) {
      connectorRefMap.put("configuration.spec.backendConfig.spec.store.spec.connectorRef",
          ((RemoteTerragruntBackendConfigSpec) terragruntBackendConfig.getTerragruntBackendConfigSpec())
              .store.getSpec()
              .getConnectorReference());
    }
  }

  public static List<EntityDetail> prepareEntityDetailsForVarFiles(
      String accountId, String orgIdentifier, String projectIdentifier, Map<String, TerragruntVarFile> varFiles) {
    List<EntityDetail> entityDetailList = new ArrayList<>();

    if (EmptyPredicate.isNotEmpty(varFiles)) {
      for (Map.Entry<String, TerragruntVarFile> varFileEntry : varFiles.entrySet()) {
        if (varFileEntry.getValue().getType().equals(TerragruntVarFileTypes.Remote)) {
          String connectorRef = ((RemoteTerragruntVarFileSpec) varFileEntry.getValue().getSpec())
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

  public static Optional<EntityDetail> prepareEntityDetailForBackendConfigFiles(
      String accountId, String orgIdentifier, String projectIdentifier, TerragruntBackendConfig config) {
    if (config == null || config.getType().equals(TerragruntBackendFileTypes.Inline)) {
      return Optional.empty();
    }
    ParameterField<String> connectorReference =
        ((RemoteTerragruntBackendConfigSpec) config.getTerragruntBackendConfigSpec())
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

  public void validatePlanStepConfigFiles(TerragruntPlanStepParameters stepParameters) {
    Validator.notNullCheck("Plan Step Parameters are null", stepParameters);
    Validator.notNullCheck("Plan Step configuration is NULL", stepParameters.getConfiguration());
    Validator.notNullCheck("Plan Step does not have Config files", stepParameters.getConfiguration().getConfigFiles());
    Validator.notNullCheck(
        "Plan Step does not have Config files store", stepParameters.getConfiguration().getConfigFiles().getStore());
    Validator.notNullCheck("Plan Step does not have Plan Command", stepParameters.getConfiguration().getCommand());
    Validator.notNullCheck(
        "Plan Step does not have Secret Manager Ref", stepParameters.getConfiguration().getSecretManagerRef());
    Validator.notNullCheck(
        "Plan Step does not have Module Config", stepParameters.getConfiguration().getTerragruntModuleConfig());
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

  public boolean isExportCredentialForSourceModule(TerragruntConfigFilesWrapper configFiles, String type) {
    String description = String.format("%s step", type);
    return configFiles.getModuleSource() != null
        && !ParameterField.isNull(configFiles.getModuleSource().getUseConnectorCredentials())
        && CDStepHelper.getParameterFieldBooleanValue(
            configFiles.getModuleSource().getUseConnectorCredentials(), USE_CONNECTOR_CREDENTIALS, description);
  }

  public String getLatestFileId(String entityId) {
    try {
      return CGRestUtils.getResponse(fileService.get().getLatestFileId(entityId, FileBucket.TERRAFORM_STATE));
    } catch (Exception exception) {
      String message = format("Unable to call fileservice to fetch latest file id for entityId: [%s]", entityId);
      throw new InvalidRequestException(message, exception);
    }
  }

  public GitStoreDelegateConfig getGitFetchFilesConfig(StoreConfig store, Ambiance ambiance, String identifier) {
    if (store == null || !ManifestStoreType.isInGitSubset(store.getKind())) {
      return null;
    }
    GitStoreConfig gitStoreConfig = (GitStoreConfig) store;
    cdStepHelper.validateGitStoreConfig(gitStoreConfig);
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);
    String validationMessage = "";
    switch (identifier) {
      case TerragruntStepHelper.TG_CONFIG_FILES:
        validationMessage = "Config Files";
        break;
      case TerragruntStepHelper.TF_BACKEND_CONFIG_FILE:
        validationMessage = "Backend Configuration Files";
        break;
      default:
        validationMessage = format("Var Files with identifier: %s", identifier);
    }

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
    if (TG_CONFIG_FILES.equals(identifier) || TF_BACKEND_CONFIG_FILE.equals(identifier)) {
      paths.add(ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getFolderPath()));
    } else {
      paths.addAll(ParameterFieldHelper.getParameterFieldValue(gitStoreConfig.getPaths()));
    }
    return GitStoreDelegateConfig.builder()
        .gitConfigDTO(gitConfigDTO)
        .sshKeySpecDTO(sshKeySpecDTO)
        .encryptedDataDetails(encryptedDataDetails)
        .fetchType(gitStoreConfig.getGitFetchType())
        .branch(getParameterFieldValue(gitStoreConfig.getBranch()))
        .commitId(getParameterFieldValue(gitStoreConfig.getCommitId()))
        .paths(paths)
        .connectorName(connectorDTO.getName())
        .build();
  }

  private void addAllEncryptionDataDetail(
      StoreConfig store, Ambiance ambiance, List<EncryptedDataDetail> encryptedDataDetails) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) store;
    cdStepHelper.validateGitStoreConfig(gitStoreConfig);
    String connectorId = gitStoreConfig.getConnectorRef().getValue();
    ConnectorInfoDTO connectorDTO = cdStepHelper.getConnector(connectorId, ambiance);

    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO((ScmConnector) connectorDTO.getConnectorConfig());
    NGAccess basicNGAccessObject = AmbianceUtils.getNgAccess(ambiance);
    SSHKeySpecDTO sshKeySpecDTO =
        gitConfigAuthenticationInfoHelper.getSSHKey(gitConfigDTO, AmbianceUtils.getAccountId(ambiance),
            AmbianceUtils.getOrgIdentifier(ambiance), AmbianceUtils.getProjectIdentifier(ambiance));

    encryptedDataDetails.addAll(
        gitConfigAuthenticationInfoHelper.getEncryptedDataDetails(gitConfigDTO, sshKeySpecDTO, basicNGAccessObject));
  }

  public List<StoreDelegateConfig> toStoreDelegateVarFiles(
      Map<String, TerragruntVarFile> varFilesMap, Ambiance ambiance) {
    if (EmptyPredicate.isNotEmpty(varFilesMap)) {
      List<StoreDelegateConfig> varFileInfo = new ArrayList<>();

      for (TerragruntVarFile file : varFilesMap.values()) {
        if (file != null) {
          TerragruntVarFileSpec spec = file.getSpec();
          if (spec instanceof InlineTerragruntVarFileSpec) {
            String content =
                ParameterFieldHelper.getParameterFieldValue(((InlineTerragruntVarFileSpec) spec).getContent());
            if (EmptyPredicate.isNotEmpty(content)) {
              List<InlineFileConfig> files = new ArrayList<>();
              files.add(InlineFileConfig.builder().content(content).name(TERRAGRUNT_FILE_NAME_FORMAT).build());
              varFileInfo.add(InlineStoreDelegateConfig.builder().files(files).build());
            }
          } else if (spec instanceof RemoteTerragruntVarFileSpec) {
            StoreConfigWrapper storeConfigWrapper = ((RemoteTerragruntVarFileSpec) spec).getStore();
            if (storeConfigWrapper != null) {
              StoreConfig storeConfig = storeConfigWrapper.getSpec();
              String identifier = file.getIdentifier();

              // Retrieve the files from the GIT stores
              GitStoreDelegateConfig gitStoreDelegateConfig =
                  getGitFetchFilesConfig(storeConfig, ambiance, format(TerragruntStepHelper.TF_VAR_FILES, identifier));
              varFileInfo.add(gitStoreDelegateConfig);
            }
          }
        }
      }

      return varFileInfo;
    }
    return Collections.emptyList();
  }

  public List<StoreDelegateConfig> toStoreDelegateVarFilesFromTgConfig(
      List<TerragruntVarFileConfig> varFilesList, Ambiance ambiance) {
    if (EmptyPredicate.isNotEmpty(varFilesList)) {
      List<StoreDelegateConfig> varFileInfo = new ArrayList<>();

      varFilesList.forEach(config -> {
        if (config instanceof TerragruntInlineVarFileConfig) {
          List<InlineFileConfig> files = new ArrayList<>();
          files.add(InlineFileConfig.builder()
                        .content(((TerragruntInlineVarFileConfig) config).getVarFileContent())
                        .name(TERRAGRUNT_FILE_NAME_FORMAT)
                        .build());
          varFileInfo.add(InlineStoreDelegateConfig.builder().files(files).build());
        }

        if (config instanceof TerragruntRemoteVarFileConfig) {
          StoreConfig storeConfig = ((TerragruntRemoteVarFileConfig) config).getGitStoreConfigDTO().toGitStoreConfig();
          String varFileIdentifier = ((TerragruntRemoteVarFileConfig) config).getVarFileIdentifier();
          GitStoreDelegateConfig gitStoreDelegateConfig = getGitFetchFilesConfig(
              storeConfig, ambiance, format(TerragruntStepHelper.TF_VAR_FILES, varFileIdentifier));
          varFileInfo.add(gitStoreDelegateConfig);
        }
      });

      return varFileInfo;
    }
    return Collections.emptyList();
  }

  private List<TerragruntVarFileConfig> toTerragruntVarFilesConfigWithCommitId(
      Map<String, TerragruntVarFile> varFilesMap, Map<String, String> varFilesSourceReference) {
    if (EmptyPredicate.isNotEmpty(varFilesMap)) {
      List<TerragruntVarFileConfig> varFileInfo = new ArrayList<>();

      for (TerragruntVarFile file : varFilesMap.values()) {
        if (file != null) {
          TerragruntVarFileSpec spec = file.getSpec();
          if (spec instanceof InlineTerragruntVarFileSpec) {
            String content =
                ParameterFieldHelper.getParameterFieldValue(((InlineTerragruntVarFileSpec) spec).getContent());
            if (EmptyPredicate.isNotEmpty(content)) {
              TerragruntInlineVarFileConfig inlineVarFileConfig = TerragruntInlineVarFileConfig.builder()
                                                                      .varFileContent(content)
                                                                      .varFileName(TERRAGRUNT_FILE_NAME_FORMAT)
                                                                      .varFileIdentifier(file.identifier)
                                                                      .build();
              varFileInfo.add(inlineVarFileConfig);
            }
          } else if (spec instanceof RemoteTerragruntVarFileSpec) {
            StoreConfigWrapper storeConfigWrapper = ((RemoteTerragruntVarFileSpec) spec).getStore();
            if (storeConfigWrapper != null) {
              StoreConfig storeConfig = storeConfigWrapper.getSpec();

              String identifier = file.getIdentifier();
              GitStoreConfig gitStoreConfig =
                  getStoreConfigAtCommitId(storeConfig, varFilesSourceReference.get(identifier));

              varFileInfo.add(TerragruntRemoteVarFileConfig.builder()
                                  .gitStoreConfigDTO(gitStoreConfig.toGitStoreConfigDTO())
                                  .build());
            }
          }
        }
      }

      return varFileInfo;
    }
    return Collections.emptyList();
  }

  private String getGitRepoUrl(GitConfigDTO gitConfigDTO, String repoName) {
    repoName = trimToEmpty(repoName);
    notEmptyCheck("Repo name cannot be empty for Account level git connector", repoName);
    String purgedRepoUrl = gitConfigDTO.getUrl().replaceAll("/*$", "");
    String purgedRepoName = repoName.replaceAll("^/*", "");
    return purgedRepoUrl + "/" + purgedRepoName;
  }

  public StoreDelegateConfig getBackendConfig(TerragruntBackendConfig backendConfig, Ambiance ambiance) {
    if (backendConfig != null) {
      TerragruntBackendConfigSpec terragruntBackendConfigSpec = backendConfig.getTerragruntBackendConfigSpec();
      if (terragruntBackendConfigSpec instanceof InlineTerragruntBackendConfigSpec) {
        String content = ParameterFieldHelper.getParameterFieldValue(
            ((InlineTerragruntBackendConfigSpec) terragruntBackendConfigSpec).getContent());

        List<InlineFileConfig> inlineFiles = new ArrayList<>();
        inlineFiles.add(InlineFileConfig.builder().content(content).name(TERRAGRUNT_FILE_NAME_FORMAT).build());
        return InlineStoreDelegateConfig.builder().files(inlineFiles).build();
      }
      if (terragruntBackendConfigSpec instanceof RemoteTerragruntBackendConfigSpec) {
        StoreConfigWrapper storeConfigWrapper =
            ((RemoteTerragruntBackendConfigSpec) terragruntBackendConfigSpec).getStore();
        StoreConfig storeConfig = storeConfigWrapper.getSpec();
        return getGitFetchFilesConfig(storeConfig, ambiance, TerragruntStepHelper.TF_BACKEND_CONFIG_FILE);
      }
    }
    return null;
  }

  public StoreDelegateConfig getBackendConfigFromTgConfig(
      TerragruntBackendConfigFileConfig backendConfig, Ambiance ambiance) {
    if (backendConfig != null) {
      if (backendConfig instanceof TerragruntInlineBackendConfigFileConfig) {
        String content = ((TerragruntInlineBackendConfigFileConfig) backendConfig).backendConfigFileContent;

        List<InlineFileConfig> inlineFiles = new ArrayList<>();
        inlineFiles.add(InlineFileConfig.builder().content(content).name(TERRAGRUNT_FILE_NAME_FORMAT).build());
        return InlineStoreDelegateConfig.builder().files(inlineFiles).build();
      }
      if (backendConfig instanceof TerragruntRemoteBackendConfigFileConfig) {
        StoreConfig storeConfig =
            ((TerragruntRemoteBackendConfigFileConfig) backendConfig).gitStoreConfigDTO.toGitStoreConfig();
        return getGitFetchFilesConfig(storeConfig, ambiance, TerragruntStepHelper.TF_BACKEND_CONFIG_FILE);
      }
    }
    return null;
  }

  private TerragruntBackendConfigFileConfig getTerragruntBackendConfigWithCommitIdReference(
      TerragruntBackendConfig backendConfig, String backendConfigCommitItReference) {
    if (backendConfig != null) {
      TerragruntBackendConfigSpec terragruntBackendConfigSpec = backendConfig.getTerragruntBackendConfigSpec();
      if (terragruntBackendConfigSpec instanceof InlineTerragruntBackendConfigSpec) {
        String content = ParameterFieldHelper.getParameterFieldValue(
            ((InlineTerragruntBackendConfigSpec) terragruntBackendConfigSpec).getContent());
        return TerragruntInlineBackendConfigFileConfig.builder().backendConfigFileContent(content).build();
      }
      if (terragruntBackendConfigSpec instanceof RemoteTerragruntBackendConfigSpec) {
        StoreConfigWrapper storeConfigWrapper =
            ((RemoteTerragruntBackendConfigSpec) terragruntBackendConfigSpec).getStore();
        StoreConfig storeConfig = storeConfigWrapper.getSpec();

        GitStoreConfig gitStoreConfig = getStoreConfigAtCommitId(storeConfig, backendConfigCommitItReference);
        return TerragruntRemoteBackendConfigFileConfig.builder()
            .gitStoreConfigDTO(gitStoreConfig.toGitStoreConfigDTO())
            .build();
      }
    }
    return null;
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

  public EncryptionConfig getEncryptionConfig(Ambiance ambiance, TerragruntPlanStepParameters planStepParameters) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(
        ParameterFieldHelper.getParameterFieldValue(planStepParameters.getConfiguration().getSecretManagerRef()),
        AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
        AmbianceUtils.getProjectIdentifier(ambiance));

    SecretManagerConfig secretManagerConfig =
        SecretManagerConfigMapper.fromDTO(secretManagerClientService.getSecretManager(
            identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
            identifierRef.getProjectIdentifier(), identifierRef.getIdentifier(), false));

    if (cdFeatureFlagHelper.isEnabled(
            identifierRef.getAccountIdentifier(), FeatureName.CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_NG)
        && isHarnessSecretManager(secretManagerConfig)) {
      secretManagerConfig.maskSecrets();
    }
    return secretManagerConfig;
  }

  public boolean isHarnessSecretManager(SecretManagerConfig secretManagerConfig) {
    return secretManagerConfig != null && secretManagerConfig.isGlobalKms();
  }

  public void saveTerragruntInheritOutput(TerragruntPlanStepParameters planStepParameters,
      TerragruntPlanTaskResponse terragruntTaskNGResponse, Ambiance ambiance) {
    validatePlanStepConfigFiles(planStepParameters);
    TerragruntPlanExecutionDataParameters configuration = planStepParameters.getConfiguration();
    TerragruntInheritOutputBuilder builder = TerragruntInheritOutput.builder();
    builder.workspace(ParameterFieldHelper.getParameterFieldValue(configuration.getWorkspace()));
    StoreConfigWrapper store = configuration.getConfigFiles().getStore();
    StoreConfigType storeConfigType = store.getType();
    switch (storeConfigType) {
      case GIT:
      case GITHUB:
      case GITLAB:
      case BITBUCKET:
        GitStoreConfig gitStoreConfig = getStoreConfigAtCommitId(configuration.getConfigFiles().getStore().getSpec(),
            terragruntTaskNGResponse.getConfigFilesSourceReference());
        builder.configFiles(gitStoreConfig);
        builder.useConnectorCredentials(isExportCredentialForSourceModule(
            configuration.getConfigFiles(), ExecutionNodeType.TERRAGRUNT_PLAN.getYamlType()));
        break;
      default:
        throw new InvalidRequestException(format("Unsupported store type: [%s]", storeConfigType));
    }

    builder
        .varFileConfigs(toTerragruntVarFilesConfigWithCommitId(
            configuration.getVarFiles(), terragruntTaskNGResponse.getVarFilesSourceReference()))
        .backendConfigFile(getTerragruntBackendConfigWithCommitIdReference(
            configuration.getBackendConfig(), terragruntTaskNGResponse.getBackendFileSourceReference()))
        .encryptedPlan(terragruntTaskNGResponse.getEncryptedPlan())
        .encryptionConfig(getEncryptionConfig(ambiance, planStepParameters))
        .environmentVariables(getEnvironmentVariablesMap(configuration.getEnvironmentVariables()))
        .targets(getParameterFieldValue(configuration.getTargets()))
        .runConfiguration(getTerragruntRunConfiguration(configuration.getTerragruntModuleConfig()))
        .planName(getTerragruntPlanName(planStepParameters.getConfiguration().getCommand(), ambiance,
            planStepParameters.getProvisionerIdentifier().getValue()));
    String fullEntityId = generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(planStepParameters.getProvisionerIdentifier()), ambiance);
    String inheritOutputName =
        format(TG_INHERIT_OUTPUT_FORMAT, planStepParameters.getConfiguration().command.name(), fullEntityId);
    executionSweepingOutputService.consume(ambiance, inheritOutputName, builder.build(), StepOutcomeGroup.STAGE.name());
  }

  private TerragruntRunConfiguration getTerragruntRunConfiguration(TerragruntModuleConfig moduleConfig) {
    if (moduleConfig.terragruntRunType == TerragruntRunType.RUN_ALL) {
      return TerragruntRunConfiguration.builder()
          .runType(TerragruntTaskRunType.RUN_ALL)
          .path(moduleConfig.getPath().getValue())
          .build();
    } else {
      return TerragruntRunConfiguration.builder()
          .runType(TerragruntTaskRunType.RUN_MODULE)
          .path(moduleConfig.getPath().getValue())
          .build();
    }
  }

  public String getTerragruntPlanName(
      TerragruntPlanCommand terragruntPlanCommand, Ambiance ambiance, String provisionId) {
    String prefix =
        TerragruntPlanCommand.DESTROY == terragruntPlanCommand ? TG_DESTROY_NAME_PREFIX_NG : TG_NAME_PREFIX_NG;
    return format(prefix, ambiance.getPlanExecutionId(), provisionId).replaceAll("_", "-");
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

  public void saveTerragruntPlanExecutionDetails(Ambiance ambiance, TerragruntPlanTaskResponse response,
      String provisionerIdentifier, TerragruntPlanStepParameters planStepParameters) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();
    TerraformPlanExecutionDetails terragruntPlanExecutionDetails =
        TerraformPlanExecutionDetails.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pipelineExecutionId(planExecutionId)
            .stageExecutionId(stageExecutionId)
            .provisionerId(provisionerIdentifier)
            .encryptedTfPlan(List.of(response.getEncryptedPlan()))
            .encryptionConfig(getEncryptionConfig(ambiance, planStepParameters))
            .tfPlanJsonFieldId(response.getPlanJsonFileId())
            .tfPlanFileBucket(FileBucket.TERRAFORM_PLAN_JSON.name())
            .build();

    terraformPlanExectionDetailsService.save(terragruntPlanExecutionDetails);
  }

  @Nullable
  public String saveTerraformPlanJsonOutput(
      Ambiance ambiance, TerragruntPlanTaskResponse response, String provisionIdentifier) {
    if (isEmpty(response.getPlanJsonFileId())) {
      return null;
    }

    TerraformPlanJsonOutput planJsonOutput = TerraformPlanJsonOutput.builder()
                                                 .provisionerIdentifier(provisionIdentifier)
                                                 .tfPlanFileId(response.getPlanJsonFileId())
                                                 .tfPlanFileBucket(FileBucket.TERRAFORM_PLAN_JSON.name())
                                                 .build();

    String outputName = TerraformPlanJsonOutput.getOutputName(provisionIdentifier);
    executionSweepingOutputService.consume(ambiance, outputName, planJsonOutput, StepCategory.STEP.name());
    return outputName;
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

  public List<EncryptedDataDetail> getEncryptionDetails(StoreConfig configFiles, TerragruntBackendConfig backendConfig,
      LinkedHashMap<String, TerragruntVarFile> varFile, Ambiance ambiance) {
    List<EncryptedDataDetail> encryptedDataDetailsList = new ArrayList<>();
    addAllEncryptionDataDetail(configFiles, ambiance, encryptedDataDetailsList);

    if (backendConfig != null && backendConfig.getType().equalsIgnoreCase(TerragruntBackendFileTypes.Remote)) {
      StoreConfig backendStoreConfig =
          ((RemoteTerragruntBackendConfigSpec) backendConfig.getTerragruntBackendConfigSpec()).getStore().getSpec();
      addAllEncryptionDataDetail(backendStoreConfig, ambiance, encryptedDataDetailsList);
    }

    varFile.forEach((identifier, terragruntVarFile) -> {
      if (terragruntVarFile.getType().equalsIgnoreCase(TerragruntVarFileTypes.Remote)) {
        StoreConfig varFileStoreConfig =
            ((RemoteTerragruntVarFileSpec) terragruntVarFile.getSpec()).getStore().getSpec();

        addAllEncryptionDataDetail(varFileStoreConfig, ambiance, encryptedDataDetailsList);
      }
    });

    return encryptedDataDetailsList;
  }

  public List<EncryptedDataDetail> getEncryptionDetailsFromTgInheritConfig(StoreConfig configFiles,
      TerragruntBackendConfigFileConfig backendConfigFile, List<TerragruntVarFileConfig> varFileConfigs,
      Ambiance ambiance) {
    List<EncryptedDataDetail> encryptedDataDetailsList = new ArrayList<>();
    addAllEncryptionDataDetail(configFiles, ambiance, encryptedDataDetailsList);

    if (backendConfigFile instanceof TerragruntRemoteBackendConfigFileConfig) {
      StoreConfig backendStoreConfig =
          ((TerragruntRemoteBackendConfigFileConfig) backendConfigFile).getGitStoreConfigDTO().toGitStoreConfig();
      addAllEncryptionDataDetail(backendStoreConfig, ambiance, encryptedDataDetailsList);
    }

    if (varFileConfigs != null) {
      varFileConfigs.forEach(terragruntVarFile -> {
        if (terragruntVarFile instanceof TerragruntRemoteVarFileConfig) {
          StoreConfig varFileStoreConfig =
              ((TerragruntRemoteVarFileConfig) terragruntVarFile).getGitStoreConfigDTO().toGitStoreConfig();

          addAllEncryptionDataDetail(varFileStoreConfig, ambiance, encryptedDataDetailsList);
        }
      });
    }

    return encryptedDataDetailsList;
  }

  public void validateApplyStepParamsInline(TerragruntApplyStepParameters stepParameters) {
    Validator.notNullCheck("Apply Step Parameters are null", stepParameters);
    Validator.notNullCheck("Apply Step configuration is NULL", stepParameters.getConfiguration());
  }

  public void validateApplyStepConfigFilesInline(TerragruntApplyStepParameters stepParameters) {
    Validator.notNullCheck("Apply Step Parameters are null", stepParameters);
    Validator.notNullCheck("Apply Step configuration is NULL", stepParameters.getConfiguration());
    TerragruntExecutionDataParameters spec = stepParameters.getConfiguration().getSpec();
    Validator.notNullCheck("Apply Step Spec is NULL", spec);
    Validator.notNullCheck("Apply Step Spec does not have Config files", spec.getConfigFiles());
    Validator.notNullCheck("Apply Step Spec does not have Config files store", spec.getConfigFiles().getStore());
  }

  public TerragruntInheritOutput getSavedInheritOutput(
      String provisionerIdentifier, String command, Ambiance ambiance) {
    String fullEntityId = generateFullIdentifier(provisionerIdentifier, ambiance);
    OptionalSweepingOutput output = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(format(TG_INHERIT_OUTPUT_FORMAT, command, fullEntityId)));
    if (!output.isFound()) {
      throw new InvalidRequestException(
          format("Did not find any Plan step for provisioner identifier: [%s]", provisionerIdentifier));
    }

    return (TerragruntInheritOutput) output.getOutput();
  }

  public void saveRollbackDestroyConfigInline(
      TerragruntApplyStepParameters stepParameters, TerragruntApplyTaskResponse response, Ambiance ambiance) {
    validateApplyStepConfigFilesInline(stepParameters);
    TerragruntStepConfigurationParameters configuration = stepParameters.getConfiguration();
    TerragruntExecutionDataParameters spec = configuration.getSpec();
    TerragruntConfigBuilder builder =
        TerragruntConfig.builder()
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
        GitStoreConfig gitStoreConfig = getStoreConfigAtCommitId(
            spec.getConfigFiles().getStore().getSpec(), response.getConfigFilesSourceReference());
        builder.configFiles(gitStoreConfig.toGitStoreConfigDTO());
        builder.useConnectorCredentials(isExportCredentialForSourceModule(
            configuration.getSpec().getConfigFiles(), ExecutionNodeType.TERRAGRUNT_APPLY.getYamlType()));
        break;
      default:
        throw new InvalidRequestException(format("Unsupported store type: [%s]", storeConfigType));
    }

    builder
        .varFileConfigs(
            toTerragruntVarFilesConfigWithCommitId(spec.getVarFiles(), response.getVarFilesSourceReference()))
        .backendConfigFile(getTerragruntBackendConfigWithCommitIdReference(
            spec.getBackendConfig(), response.getBackendFileSourceReference()))
        .environmentVariables(getEnvironmentVariablesMap(spec.getEnvironmentVariables()))
        .workspace(ParameterFieldHelper.getParameterFieldValue(spec.getWorkspace()))
        .targets(ParameterFieldHelper.getParameterFieldValue(spec.getTargets()))
        .runConfiguration(getTerragruntRunConfiguration(spec.getTerragruntModuleConfig()));

    terragruntConfigDAL.saveTerragruntConfig(builder.build());
  }

  public Map<String, Object> parseTerragruntOutputs(String terragruntOutputString) {
    Map<String, Object> outputs = new LinkedHashMap<>();
    if (isEmpty(terragruntOutputString)) {
      return outputs;
    }
    try {
      TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
      Map<String, Object> json = new ObjectMapper().readValue(IOUtils.toInputStream(terragruntOutputString), typeRef);

      json.forEach((key, object) -> outputs.put(key, ((Map<String, Object>) object).get("value")));

    } catch (IOException exception) {
      log.error("", exception);
    }
    return outputs;
  }

  public void saveRollbackDestroyConfigInherited(TerragruntApplyStepParameters stepParameters, Ambiance ambiance) {
    TerragruntInheritOutput inheritOutput = getSavedInheritOutput(
        ParameterFieldHelper.getParameterFieldValue(stepParameters.getProvisionerIdentifier()), APPLY.name(), ambiance);

    TerragruntConfig terragruntConfig =
        TerragruntConfig.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
            .entityId(
                generateFullIdentifier(getParameterFieldValue(stepParameters.getProvisionerIdentifier()), ambiance))
            .pipelineExecutionId(AmbianceUtils.getPlanExecutionIdForExecutionMode(ambiance))
            .configFiles(
                inheritOutput.getConfigFiles() != null ? inheritOutput.getConfigFiles().toGitStoreConfigDTO() : null)
            .useConnectorCredentials(inheritOutput.isUseConnectorCredentials())
            .varFileConfigs(inheritOutput.getVarFileConfigs())
            .backendConfigFile(inheritOutput.getBackendConfigFile())
            .environmentVariables(inheritOutput.getEnvironmentVariables())
            .workspace(inheritOutput.getWorkspace())
            .targets(inheritOutput.getTargets())
            .runConfiguration(inheritOutput.getRunConfiguration())
            .build();

    terragruntConfigDAL.saveTerragruntConfig(terragruntConfig);
  }

  public void validateDestroyStepParamsInline(TerragruntDestroyStepParameters stepParameters) {
    Validator.notNullCheck("Destroy Step Parameters are null", stepParameters);
    Validator.notNullCheck("Destroy Step configuration is NULL", stepParameters.getConfiguration());
  }

  public TerragruntConfig getLastSuccessfulApplyConfig(TerragruntDestroyStepParameters parameters, Ambiance ambiance) {
    String entityId = generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(parameters.getProvisionerIdentifier()), ambiance);
    Query<TerragruntConfig> query =
        persistence.createQuery(TerragruntConfig.class)
            .filter(TerragruntConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
            .filter(TerragruntConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
            .filter(TerragruntConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
            .filter(TerragruntConfigKeys.entityId, entityId)
            .order(Sort.descending(TerragruntConfigKeys.createdAt));
    TerragruntConfig terragruntConfig = terragruntConfigDAL.getTerragruntConfig(query, ambiance);
    if (terragruntConfig == null) {
      throw new InvalidRequestException(format("Terragrunt config for Last Apply not found: [%s]", entityId));
    }
    return terragruntConfig;
  }

  public void saveTerragruntConfig(TerragruntConfig rollbackConfig, Ambiance ambiance) {
    TerragruntConfig terragruntConfig =
        TerragruntConfig.builder()
            .accountId(AmbianceUtils.getAccountId(ambiance))
            .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
            .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
            .entityId(rollbackConfig.getEntityId())
            .pipelineExecutionId(AmbianceUtils.getPlanExecutionIdForExecutionMode(ambiance))
            .configFiles(rollbackConfig.getConfigFiles())
            .varFileConfigs(rollbackConfig.getVarFileConfigs())
            .backendConfigFile(rollbackConfig.getBackendConfigFile())
            .environmentVariables(rollbackConfig.getEnvironmentVariables())
            .workspace(rollbackConfig.getWorkspace())
            .targets(rollbackConfig.getTargets())
            .runConfiguration(rollbackConfig.getRunConfiguration())
            .build();

    terragruntConfigDAL.saveTerragruntConfig(terragruntConfig);
  }

  public boolean tfPlanEncryptionOnManager(String accountId, EncryptionConfig encryptionConfig) {
    return cdFeatureFlagHelper.isEnabled(accountId, CDS_TERRAFORM_TERRAGRUNT_PLAN_ENCRYPTION_ON_MANAGER_NG)
        && isHarnessSecretManager((SecretManagerConfig) encryptionConfig);
  }
}
