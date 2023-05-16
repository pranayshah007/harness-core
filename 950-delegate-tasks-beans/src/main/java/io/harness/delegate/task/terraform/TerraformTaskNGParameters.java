/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;
import static io.harness.expression.Expression.DISALLOW_SECRETS;

import static software.wings.beans.TaskType.TERRAFORM_TASK_NG;
import static software.wings.beans.TaskType.TERRAFORM_TASK_NG_V5;
import static software.wings.beans.TaskType.TERRAFORM_TASK_NG_V6;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryCapabilityHelper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.GitConnectionNGCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.beans.storeconfig.S3StoreTFDelegateConfig;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.capability.ProcessExecutionCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.filestore.FileStoreFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.TaskType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@Slf4j
@OwnedBy(CDP)
public class TerraformTaskNGParameters
    implements TaskParameters, ExecutionCapabilityDemander, NestedAnnotationResolver {
  @NonNull String accountId;
  String currentStateFileId;

  @NonNull TFTaskType taskType;
  @NonNull String entityId;
  String workspace;
  GitFetchFilesConfig configFile;
  FileStoreFetchFilesConfig fileStoreConfigFiles;
  @Expression(ALLOW_SECRETS) List<TerraformVarFileInfo> varFileInfos;
  @Expression(ALLOW_SECRETS) TerraformBackendConfigFileInfo backendConfigFileInfo;
  // TODO once FF TERRAFORM_REMOTE_BACKEND_CONFIG is remoted, this field can be removed as well
  @Expression(ALLOW_SECRETS) String backendConfig;
  @Expression(DISALLOW_SECRETS) List<String> targets;
  @Expression(ALLOW_SECRETS) Map<String, String> environmentVariables;
  boolean saveTerraformStateJson;
  boolean saveTerraformHumanReadablePlan;
  boolean tfModuleSourceInheritSSH;
  long timeoutInMillis;
  boolean useOptimizedTfPlan;
  boolean isTerraformCloudCli;
  boolean skipTerraformRefresh;
  @Expression(ALLOW_SECRETS) Map<String, String> terraformCommandFlags;
  // For plan
  TerraformCommand terraformCommand;

  // To aid in logging
  TerraformCommandUnit terraformCommandUnit;

  // For Apply when inheriting from plan
  EncryptionConfig encryptionConfig;
  EncryptedRecordData encryptedTfPlan;
  String planName;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();
    if (configFile != null) {
      capabilities = ProcessExecutionCapabilityHelper.generateExecutionCapabilitiesForTerraform(
          configFile.getGitStoreDelegateConfig().getEncryptedDataDetails(), maskingEvaluator);
      log.info("Adding Required Execution Capabilities for GitStores");
      capabilities.add(GitConnectionNGCapability.builder()
                           .gitConfig((GitConfigDTO) configFile.getGitStoreDelegateConfig().getGitConfigDTO())
                           .encryptedDataDetails(configFile.getGitStoreDelegateConfig().getEncryptedDataDetails())
                           .sshKeySpecDTO(configFile.getGitStoreDelegateConfig().getSshKeySpecDTO())
                           .build());

      GitConfigDTO gitConfigDTO = (GitConfigDTO) configFile.getGitStoreDelegateConfig().getGitConfigDTO();
      if (isNotEmpty(gitConfigDTO.getDelegateSelectors())) {
        capabilities.add(SelectorCapability.builder().selectors(gitConfigDTO.getDelegateSelectors()).build());
      }
    }
    if (fileStoreConfigFiles != null) {
      capabilities.addAll(ProcessExecutionCapabilityHelper.generateExecutionCapabilitiesForTerraform(
          fileStoreConfigFiles.getEncryptedDataDetails(), maskingEvaluator));
      switch (fileStoreConfigFiles.getManifestStoreType()) {
        case "Artifactory":
          capabilities.addAll(ArtifactoryCapabilityHelper.fetchRequiredExecutionCapabilities(
              fileStoreConfigFiles.getConnectorDTO().getConnectorConfig(), maskingEvaluator));
          log.info("Adding Required Execution Capabilities for ArtifactoryStores");
          break;
        default:
          break;
      }
    }
    if (isNotEmpty(varFileInfos)) {
      for (TerraformVarFileInfo varFileInfo : varFileInfos) {
        if (varFileInfo instanceof RemoteTerraformVarFileInfo) {
          GitFetchFilesConfig gitFetchFilesConfig = ((RemoteTerraformVarFileInfo) varFileInfo).getGitFetchFilesConfig();
          if (gitFetchFilesConfig != null) {
            FileStoreFetchFilesConfig fileStoreFetchConfig =
                ((RemoteTerraformVarFileInfo) varFileInfo).getFilestoreFetchFilesConfig();
            capabilities.addAll(getCapabilities(maskingEvaluator, gitFetchFilesConfig, fileStoreFetchConfig));
          }
        }
      }
    }
    if (backendConfigFileInfo instanceof RemoteTerraformBackendConfigFileInfo) {
      GitFetchFilesConfig gitFetchFilesConfig =
          ((RemoteTerraformBackendConfigFileInfo) backendConfigFileInfo).getGitFetchFilesConfig();
      FileStoreFetchFilesConfig fileStoreFetchConfig =
          ((RemoteTerraformBackendConfigFileInfo) backendConfigFileInfo).getFilestoreFetchFilesConfig();
      capabilities.addAll(getCapabilities(maskingEvaluator, gitFetchFilesConfig, fileStoreFetchConfig));
    }
    if (encryptionConfig != null) {
      capabilities.addAll(
          EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilityForSecretManager(encryptionConfig, null));
    }
    return capabilities;
  }

  private List<ExecutionCapability> getCapabilities(ExpressionEvaluator maskingEvaluator,
      GitFetchFilesConfig gitFetchFilesConfig, FileStoreFetchFilesConfig fileStoreFetchConfig) {
    List<ExecutionCapability> capabilities = new ArrayList<>();
    if (gitFetchFilesConfig != null) {
      capabilities.add(
          GitConnectionNGCapability.builder()
              .gitConfig((GitConfigDTO) gitFetchFilesConfig.getGitStoreDelegateConfig().getGitConfigDTO())
              .encryptedDataDetails(gitFetchFilesConfig.getGitStoreDelegateConfig().getEncryptedDataDetails())
              .sshKeySpecDTO(gitFetchFilesConfig.getGitStoreDelegateConfig().getSshKeySpecDTO())
              .build());

      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitFetchFilesConfig.getGitStoreDelegateConfig().getGitConfigDTO();
      if (isNotEmpty(gitConfigDTO.getDelegateSelectors())) {
        capabilities.add(SelectorCapability.builder().selectors(gitConfigDTO.getDelegateSelectors()).build());
      }
    }

    if (fileStoreFetchConfig != null) {
      switch (fileStoreFetchConfig.getManifestStoreType()) {
        case "Artifactory":
          capabilities.addAll(ArtifactoryCapabilityHelper.fetchRequiredExecutionCapabilities(
              fileStoreFetchConfig.getConnectorDTO().getConnectorConfig(), maskingEvaluator));
          log.info("Adding Required Execution Capabilities for ArtifactoryStores");
          break;
        default:
      }
    }
    return capabilities;
  }

  public TaskType getDelegateTaskType() {
    if (hasS3Store()) {
      return TERRAFORM_TASK_NG_V6;
    }
    if (isNotEmpty(this.terraformCommandFlags)) {
      return TERRAFORM_TASK_NG_V5;
    }
    if (this.skipTerraformRefresh) {
      return TaskType.TERRAFORM_TASK_NG_V4;
    }
    if (this.isTerraformCloudCli) {
      return TaskType.TERRAFORM_TASK_NG_V3;
    } else {
      return this.backendConfigFileInfo == null ? TERRAFORM_TASK_NG : TaskType.TERRAFORM_TASK_NG_V2;
    }
  }

  private boolean hasS3Store() {
    // check config files
    if (this.fileStoreConfigFiles instanceof S3StoreTFDelegateConfig) {
      return true;
    }
    // check for backend configuration
    if (this.backendConfigFileInfo instanceof RemoteTerraformBackendConfigFileInfo
        && ((RemoteTerraformBackendConfigFileInfo) this.backendConfigFileInfo).getFilestoreFetchFilesConfig()
                instanceof S3StoreTFDelegateConfig) {
      return true;
    }
    // check for var files
    if (isNotEmpty(varFileInfos)) {
      for (TerraformVarFileInfo terraformVarFileInfo : varFileInfos) {
        if (terraformVarFileInfo instanceof RemoteTerraformVarFileInfo
            && ((RemoteTerraformVarFileInfo) terraformVarFileInfo).filestoreFetchFilesConfig
                instanceof S3StoreTFDelegateConfig) {
          return true;
        }
      }
    }
    return false;
  }
}
