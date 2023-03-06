/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraform.handlers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.ARTIFACTORY;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Explanation.EXPLANATION_NO_CONFIG_SET;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_NO_CONFIG_SET;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.provision.TerraformConstants.TERRAFORM_BACKEND_CONFIGS_FILE_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_VARIABLES_FILE_NAME;
import static io.harness.provision.TerraformConstants.TF_BACKEND_CONFIG_DIR;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_DIR;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.terraform.TerraformBackendConfigFileInfo;
import io.harness.delegate.task.terraform.TerraformBaseHelper;
import io.harness.delegate.task.terraform.TerraformTaskNGParameters;
import io.harness.delegate.task.terraform.TerraformTaskNGResponse;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.TerraformCommandExecutionException;
import io.harness.exception.WingsException;
import io.harness.git.model.GitBaseRequest;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.PlanJsonLogOutputStream;
import io.harness.logging.PlanLogOutputStream;
import io.harness.terraform.TerraformHelperUtils;
import io.harness.terraform.TerraformStepResponse;
import io.harness.terraform.request.TerraformExecuteStepRequest;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;

@Slf4j
@OwnedBy(CDP)
public class TerraformDestroyTaskHandler extends TerraformAbstractTaskHandler {
  @Inject TerraformBaseHelper terraformBaseHelper;

  @Override
  public TerraformTaskNGResponse executeTaskInternal(
      TerraformTaskNGParameters taskParameters, String delegateId, String taskId, LogCallback logCallback)
      throws TerraformCommandExecutionException, IOException, TimeoutException, InterruptedException {
    String scriptDirectory;
    String baseDir = terraformBaseHelper.getBaseDir(taskParameters.getEntityId());
    Map<String, String> commitIdToFetchedFilesMap = new HashMap<>();

    if (taskParameters.getConfigFile() != null) {
      GitStoreDelegateConfig conFileFileGitStore = taskParameters.getConfigFile().getGitStoreDelegateConfig();
      String scriptPath = FilenameUtils.normalize(conFileFileGitStore.getPaths().get(0));

      if (isNotEmpty(conFileFileGitStore.getBranch())) {
        logCallback.saveExecutionLog(
            "Branch: " + conFileFileGitStore.getBranch(), INFO, CommandExecutionStatus.RUNNING);
      }

      logCallback.saveExecutionLog("Normalized Path: " + scriptPath, INFO, CommandExecutionStatus.RUNNING);

      if (isNotEmpty(conFileFileGitStore.getCommitId())) {
        logCallback.saveExecutionLog(
            format("%nInheriting git state at commit id: [%s]", conFileFileGitStore.getCommitId()), INFO,
            CommandExecutionStatus.RUNNING);
      }

      if (taskParameters.isTfModuleSourceInheritSSH()) {
        terraformBaseHelper.configureCredentialsForModuleSource(
            baseDir, taskParameters.getEnvironmentVariables(), conFileFileGitStore, logCallback);
      }

      GitBaseRequest gitBaseRequestForConfigFile = terraformBaseHelper.getGitBaseRequestForConfigFile(
          taskParameters.getAccountId(), conFileFileGitStore, (GitConfigDTO) conFileFileGitStore.getGitConfigDTO());

      scriptDirectory = terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(gitBaseRequestForConfigFile,
          taskParameters.getAccountId(), taskParameters.getWorkspace(), taskParameters.getCurrentStateFileId(),
          conFileFileGitStore, logCallback, scriptPath, baseDir);

      commitIdToFetchedFilesMap = terraformBaseHelper.buildCommitIdToFetchedFilesMap(
          taskParameters.getConfigFile().getIdentifier(), gitBaseRequestForConfigFile, commitIdToFetchedFilesMap);
    } else if (taskParameters.getFileStoreConfigFiles() != null
        && taskParameters.getFileStoreConfigFiles().getType() == ARTIFACTORY) {
      ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
          (ArtifactoryStoreDelegateConfig) taskParameters.getFileStoreConfigFiles();
      if (isNotEmpty(artifactoryStoreDelegateConfig.getRepositoryName())) {
        logCallback.saveExecutionLog(
            "Repository: " + artifactoryStoreDelegateConfig.getRepositoryName(), INFO, CommandExecutionStatus.RUNNING);
      }

      scriptDirectory = terraformBaseHelper.fetchConfigFileAndPrepareScriptDir(artifactoryStoreDelegateConfig,
          taskParameters.getAccountId(), taskParameters.getWorkspace(), taskParameters.getCurrentStateFileId(),
          logCallback, baseDir);
    } else {
      throw NestedExceptionUtils.hintWithExplanationException(HINT_NO_CONFIG_SET, EXPLANATION_NO_CONFIG_SET,
          new TerraformCommandExecutionException("No Terraform config set", WingsException.USER));
    }

    String tfVarDirectory = Paths.get(baseDir, TF_VAR_FILES_DIR).toString();
    List<String> varFilePaths = terraformBaseHelper.checkoutRemoteVarFileAndConvertToVarFilePaths(
        taskParameters.getVarFileInfos(), scriptDirectory, logCallback, taskParameters.getAccountId(), tfVarDirectory,
        commitIdToFetchedFilesMap, taskParameters.isTerraformCloudCli());

    if (taskParameters.isTerraformCloudCli() && !varFilePaths.isEmpty()) {
      logCallback.saveExecutionLog(format("Var files are moved in %s having a suffix: .auto.tfvars", scriptDirectory),
          INFO, CommandExecutionStatus.RUNNING);
      for (String varFilePath : varFilePaths) {
        TerraformHelperUtils.copytfCloudVarFilesToScriptDirectory(varFilePath, scriptDirectory);
      }
    }

    File tfOutputsFile = Paths.get(scriptDirectory, format(TERRAFORM_VARIABLES_FILE_NAME, "output")).toFile();
    String tfBackendConfigDirectory = Paths.get(baseDir, TF_BACKEND_CONFIG_DIR).toString();
    String backendConfigFile = taskParameters.getBackendConfig() != null
        ? TerraformHelperUtils.createFileFromStringContent(
            taskParameters.getBackendConfig(), scriptDirectory, TERRAFORM_BACKEND_CONFIGS_FILE_NAME)
        : taskParameters.getBackendConfig();
    TerraformBackendConfigFileInfo configFileInfo = null;
    if (taskParameters.getBackendConfigFileInfo() != null) {
      configFileInfo = taskParameters.getBackendConfigFileInfo();
      backendConfigFile =
          terraformBaseHelper.checkoutRemoteBackendConfigFileAndConvertToFilePath(configFileInfo, scriptDirectory,
              logCallback, taskParameters.getAccountId(), tfBackendConfigDirectory, commitIdToFetchedFilesMap);
    }
    try (PlanJsonLogOutputStream planJsonLogOutputStream = new PlanJsonLogOutputStream();
         PlanLogOutputStream planLogOutputStream = new PlanLogOutputStream()) {
      TerraformExecuteStepRequest terraformExecuteStepRequest =
          TerraformExecuteStepRequest.builder()
              .tfBackendConfigsFile(backendConfigFile)
              .tfOutputsFile(tfOutputsFile.getAbsolutePath())
              .tfVarFilePaths(varFilePaths)
              .workspace(taskParameters.getWorkspace())
              .targets(taskParameters.getTargets())
              .scriptDirectory(scriptDirectory)
              .encryptedTfPlan(taskParameters.getEncryptedTfPlan())
              .encryptionConfig(taskParameters.getEncryptionConfig())
              .envVars(taskParameters.getEnvironmentVariables())
              .isSaveTerraformJson(taskParameters.isSaveTerraformStateJson())
              .logCallback(logCallback)
              .isSaveTerraformHumanReadablePlan(taskParameters.isSaveTerraformHumanReadablePlan())
              .planJsonLogOutputStream(planJsonLogOutputStream)
              .planLogOutputStream(planLogOutputStream)
              .analyseTfPlanSummary(false) // this only temporary until the logic for NG is implemented - FF should be
                                           // sent from manager side
              .timeoutInMillis(taskParameters.getTimeoutInMillis())
              .useOptimizedTfPlan(taskParameters.isUseOptimizedTfPlan())
              .accountId(taskParameters.getAccountId())
              .isTerraformCloudCli(taskParameters.isTerraformCloudCli())
              .build();

      TerraformStepResponse terraformStepResponse =
          terraformBaseHelper.executeTerraformDestroyStep(terraformExecuteStepRequest);

      logCallback.saveExecutionLog("Script execution finished with status: "
              + terraformStepResponse.getCliResponse().getCommandExecutionStatus(),
          INFO, CommandExecutionStatus.RUNNING);

      File tfStateFile = TerraformHelperUtils.getTerraformStateFile(scriptDirectory, taskParameters.getWorkspace());

      String stateFileId = terraformBaseHelper.uploadTfStateFile(
          taskParameters.getAccountId(), delegateId, taskId, taskParameters.getEntityId(), tfStateFile);

      logCallback.saveExecutionLog("\nDone executing scripts.\n", INFO, CommandExecutionStatus.RUNNING);

      return TerraformTaskNGResponse.builder()
          .commitIdForConfigFilesMap(commitIdToFetchedFilesMap)
          .encryptedTfPlan(taskParameters.getEncryptedTfPlan())
          .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
          .stateFileId(stateFileId)
          .build();
    }
  }
}
