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
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.provision.TerraformConstants.TF_DESTROY_NAME_PREFIX;
import static io.harness.provision.TerraformConstants.TF_NAME_PREFIX;
import static io.harness.validation.Validator.notEmptyCheck;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
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
import io.harness.delegate.beans.terragrunt.response.TerragruntPlanTaskResponse;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.CGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.utils.IdentifierRefHelper;
import io.harness.validation.Validator;
import io.harness.validator.NGRegexValidatorConstants;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
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

  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private FileServiceClientFactory fileService;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private GitConfigAuthenticationInfoHelper gitConfigAuthenticationInfoHelper;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject TerraformPlanExectionDetailsService terraformPlanExectionDetailsService;

  public static StepType addStepType(String yamlType) {
    return StepType.newBuilder().setType(yamlType).setStepCategory(StepCategory.STEP).build();
  }

  public void checkIfTerragruntFeatureIsEnabled(Ambiance ambiance, String step) {
    if (!cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance), FeatureName.TERRAGRUNT_PROVISION_NG)) {
      throw new AccessDeniedException(
          format("'%s' is not enabled for account '%s'. Please contact harness customer care to enable FF '%s'.", step,
              AmbianceUtils.getAccountId(ambiance), FeatureName.TERRAGRUNT_PROVISION_NG.name()),
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
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
        && terragruntBackendConfig.getTerragruntBackendConfigSpec().getType().equals(
            TerragruntBackendFileTypes.Remote)) {
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

  private boolean isExportCredentialForSourceModule(TerragruntConfigFilesWrapper configFiles, String type) {
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
    if (TG_CONFIG_FILES.equals(identifier)) {
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
              files.add(InlineFileConfig.builder().content(content).name("terragrunt-${UUID}.tfvars").build());
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

  private List<StoreDelegateConfig> toStoreDelegateVarFilesWithCommitId(
      Map<String, TerragruntVarFile> varFilesMap, Map<String, String> varFilesSourceReference, Ambiance ambiance) {
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
              files.add(InlineFileConfig.builder().content(content).name("terragrunt-${UUID}.tfvars").build());
              varFileInfo.add(InlineStoreDelegateConfig.builder().files(files).build());
            }
          } else if (spec instanceof RemoteTerragruntVarFileSpec) {
            StoreConfigWrapper storeConfigWrapper = ((RemoteTerragruntVarFileSpec) spec).getStore();
            if (storeConfigWrapper != null) {
              StoreConfig storeConfig = storeConfigWrapper.getSpec();

              String identifier = file.getIdentifier();
              GitStoreConfig gitStoreConfig =
                  getStoreConfigAtCommitId(storeConfig, varFilesSourceReference.get(identifier));

              varFileInfo.add(getGitFetchFilesConfig(gitStoreConfig, ambiance, TerragruntStepHelper.TF_VAR_FILES));
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
        inlineFiles.add(InlineFileConfig.builder().content(content).name("terragrunt-${UUID}.tf").build());
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

  public StoreDelegateConfig getBackendConfigWithCommitIdReference(
      TerragruntBackendConfig backendConfig, Ambiance ambiance, String backendConfigCommitItReference) {
    if (backendConfig != null) {
      TerragruntBackendConfigSpec terragruntBackendConfigSpec = backendConfig.getTerragruntBackendConfigSpec();
      if (terragruntBackendConfigSpec instanceof InlineTerragruntBackendConfigSpec) {
        String content = ParameterFieldHelper.getParameterFieldValue(
            ((InlineTerragruntBackendConfigSpec) terragruntBackendConfigSpec).getContent());
        return InlineStoreDelegateConfig.builder()
            .files(List.of(InlineFileConfig.builder().content(content).build()))
            .build();
      }
      if (terragruntBackendConfigSpec instanceof RemoteTerragruntBackendConfigSpec) {
        StoreConfigWrapper storeConfigWrapper =
            ((RemoteTerragruntBackendConfigSpec) terragruntBackendConfigSpec).getStore();
        StoreConfig storeConfig = storeConfigWrapper.getSpec();

        GitStoreConfig gitStoreConfig = getStoreConfigAtCommitId(storeConfig, backendConfigCommitItReference);
        return getGitFetchFilesConfig(gitStoreConfig, ambiance, TerragruntStepHelper.TF_BACKEND_CONFIG_FILE);
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

    return SecretManagerConfigMapper.fromDTO(secretManagerClientService.getSecretManager(
        identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(),
        identifierRef.getIdentifier(), false));
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
        builder.configFiles(getGitFetchFilesConfig(gitStoreConfig, ambiance, TerragruntStepHelper.TG_CONFIG_FILES));
        builder.useConnectorCredentials(isExportCredentialForSourceModule(
            configuration.getConfigFiles(), ExecutionNodeType.TERRAGRUNT_PLAN.getYamlType()));
        break;
      default:
        throw new InvalidRequestException(format("Unsupported store type: [%s]", storeConfigType));
    }

    builder
        .varFileConfigs(toStoreDelegateVarFilesWithCommitId(
            configuration.getVarFiles(), terragruntTaskNGResponse.getVarFilesSourceReference(), ambiance))
        .backendConfigFile(getBackendConfigWithCommitIdReference(
            configuration.getBackendConfig(), ambiance, terragruntTaskNGResponse.getBackendFileSourceReference()))
        .encryptedPlan(terragruntTaskNGResponse.getEncryptedPlan())
        .encryptionConfig(getEncryptionConfig(ambiance, planStepParameters))
        .environmentVariables(getEnvironmentVariablesMap(configuration.getEnvironmentVariables()))
        .targets(getParameterFieldValue(configuration.getTargets()))
        .runConfiguration(getTerragruntRunConfiguration(configuration))
        .planName(getTerragruntPlanName(planStepParameters.getConfiguration().getCommand(), ambiance,
            planStepParameters.getProvisionerIdentifier().getValue()));
    String fullEntityId = generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(planStepParameters.getProvisionerIdentifier()), ambiance);
    String inheritOutputName =
        format(TG_INHERIT_OUTPUT_FORMAT, planStepParameters.getConfiguration().command.name(), fullEntityId);
    executionSweepingOutputService.consume(ambiance, inheritOutputName, builder.build(), StepOutcomeGroup.STAGE.name());
  }

  private TerragruntRunConfiguration getTerragruntRunConfiguration(
      TerragruntPlanExecutionDataParameters configuration) {
    if (configuration.getTerragruntModuleConfig().terragruntRunType == TerragruntRunType.RUN_ALL) {
      return TerragruntRunConfiguration.builder()
          .runType(TerragruntTaskRunType.RUN_ALL)
          .path(configuration.getTerragruntModuleConfig().getPath().getValue())
          .build();
    } else {
      return TerragruntRunConfiguration.builder()
          .runType(TerragruntTaskRunType.RUN_MODULE)
          .path(configuration.getTerragruntModuleConfig().getPath().getValue())
          .build();
    }
  }

  private String getTerragruntPlanName(
      TerragruntPlanCommand terragruntPlanCommand, Ambiance ambiance, String provisionId) {
    String prefix = TerragruntPlanCommand.DESTROY == terragruntPlanCommand ? TF_DESTROY_NAME_PREFIX : TF_NAME_PREFIX;
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
}
