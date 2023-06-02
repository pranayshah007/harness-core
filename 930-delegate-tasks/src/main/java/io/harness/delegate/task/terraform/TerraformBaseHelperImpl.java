/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terraform;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.DelegateFile.Builder.aDelegateFile;
import static io.harness.delegate.beans.connector.scm.GitAuthType.SSH;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.AMAZON_S3;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.ARTIFACTORY;
import static io.harness.delegate.task.terraform.TerraformCommand.APPLY;
import static io.harness.delegate.task.terraform.TerraformCommand.DESTROY;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Explanation.EXPLANATION_FAILED_TO_DOWNLOAD_FROM_ARTIFACTORY;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Explanation.EXPLANATION_FILES_NOT_FOUND_IN_S3_CONFIG;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Explanation.EXPLANATION_NO_ARTIFACT_DETAILS_FOR_ARTIFACTORY_CONFIG;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_FAILED_TO_DOWNLOAD_FROM_ARTIFACTORY;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_FILES_NOT_FOUND_IN_S3_CONFIG;
import static io.harness.delegate.task.terraform.TerraformExceptionConstants.Hints.HINT_NO_ARTIFACT_DETAILS_FOR_ARTIFACTORY_CONFIG;
import static io.harness.eraro.ErrorCode.DEFAULT_ERROR_CODE;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.provision.TerraformConstants.ACTIVITY_ID_BASED_TF_BASE_DIR;
import static io.harness.provision.TerraformConstants.PLAN_HUMAN_READABLE_TXT_FILE_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_APPLY_PLAN_FILE_VAR_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_BACKEND_CONFIGS_FILE_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_CLOUD_VARIABLES_FILE_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_DESTROY_HUMAN_READABLE_PLAN_FILE_VAR_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_DESTROY_PLAN_FILE_VAR_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_HUMAN_READABLE_PLAN_FILE_VAR_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_PLAN_FILE_OUTPUT_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_PLAN_JSON_FILE_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_STATE_FILE_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_VARIABLES_FILE_NAME;
import static io.harness.provision.TerraformConstants.TF_BASE_DIR;
import static io.harness.provision.TerraformConstants.TF_SCRIPT_DIR;
import static io.harness.provision.TerraformConstants.TF_WORKING_DIR;
import static io.harness.provision.TerraformConstants.USER_DIR_KEY;
import static io.harness.provision.TerraformConstants.WORKSPACE_STATE_FILE_PATH_FORMAT;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryNgService;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.cli.CliResponse;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.shell.SshSessionConfigMapper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3StoreTFDelegateConfig;
import io.harness.delegate.clienttools.TerraformConfigInspectVersion;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.delegate.task.aws.AwsNgConfigMapper;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.TerraformCommandExecutionException;
import io.harness.exception.WingsException;
import io.harness.exception.runtime.JGitRuntimeException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.git.GitClientHelper;
import io.harness.git.GitClientV2;
import io.harness.git.model.DownloadFilesRequest;
import io.harness.git.model.GitBaseRequest;
import io.harness.git.model.GitRepositoryType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.PlanHumanReadableOutputStream;
import io.harness.logging.PlanJsonLogOutputStream;
import io.harness.logging.PlanLogOutputStream;
import io.harness.provision.TerraformPlanSummary;
import io.harness.provision.model.TfConfigInspectVersion;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;
import io.harness.terraform.TerraformClient;
import io.harness.terraform.TerraformHelperUtils;
import io.harness.terraform.TerraformStepResponse;
import io.harness.terraform.TerraformStepResponse.TerraformStepResponseBuilder;
import io.harness.terraform.request.TerraformApplyCommandRequest;
import io.harness.terraform.request.TerraformDestroyCommandRequest;
import io.harness.terraform.request.TerraformExecuteStepRequest;
import io.harness.terraform.request.TerraformInitCommandRequest;
import io.harness.terraform.request.TerraformPlanCommandRequest;
import io.harness.terraform.request.TerraformRefreshCommandRequest;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.delegation.TerraformProvisionParameters;
import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;

@Slf4j
@OwnedBy(CDP)
public class TerraformBaseHelperImpl implements TerraformBaseHelper {
  private static final String ARTIFACT_PATH_METADATA_KEY = "artifactPath";
  private static final String ARTIFACT_NAME_METADATA_KEY = "artifactName";
  private static final String PRINT_BACKEND_CONFIG = "Initialize terraform with backend configuration: %n%s%n";
  public static final String SSH_KEY_DIR = ".ssh";
  public static final String SSH_KEY_FILENAME = "ssh.key";
  public static final String SSH_COMMAND_PREFIX = "ssh";
  public static final String GIT_SSH_COMMAND = "GIT_SSH_COMMAND";
  public static final String TF_SSH_COMMAND_ARG =
      " -o StrictHostKeyChecking=no -o BatchMode=yes -o PasswordAuthentication=no -i ";

  @Inject DelegateFileManagerBase delegateFileManagerBase;
  @Inject TerraformClient terraformClient;
  @Inject EncryptDecryptHelper encryptDecryptHelper;
  @Inject GitClientV2 gitClient;
  @Inject GitClientHelper gitClientHelper;
  @Inject SecretDecryptionService secretDecryptionService;
  @Inject SshSessionConfigMapper sshSessionConfigMapper;
  @Inject NGGitService ngGitService;
  @Inject DelegateFileManagerBase delegateFileManager;
  @Inject ArtifactoryNgService artifactoryNgService;
  @Inject ArtifactoryRequestMapper artifactoryRequestMapper;
  @Inject AwsApiHelperService awsApiHelperService;
  @Inject AwsNgConfigMapper awsNgConfigMapper;

  @Override
  public void downloadTfStateFile(String workspace, String accountId, String currentStateFileId, String scriptDirectory)
      throws IOException {
    File tfStateFile = (isEmpty(workspace))
        ? Paths.get(scriptDirectory, TERRAFORM_STATE_FILE_NAME).toFile()
        : Paths.get(scriptDirectory, format(WORKSPACE_STATE_FILE_PATH_FORMAT, workspace)).toFile();

    if (currentStateFileId != null) {
      try (InputStream stateRemoteInputStream =
               delegateFileManagerBase.downloadByFileId(FileBucket.TERRAFORM_STATE, currentStateFileId, accountId)) {
        FileUtils.copyInputStreamToFile(stateRemoteInputStream, tfStateFile);
      }
    } else {
      FileUtils.deleteQuietly(tfStateFile);
    }
  }

  @Override
  public List<String> parseOutput(String workspaceOutput) {
    List<String> outputs = Arrays.asList(StringUtils.split(workspaceOutput, "\n"));
    List<String> workspaces = new ArrayList<>();
    for (String output : outputs) {
      if (output.charAt(0) == '*') {
        output = output.substring(1);
      }
      output = output.trim();
      workspaces.add(output);
    }
    return workspaces;
  }

  @Override
  public TerraformStepResponse executeTerraformApplyStep(TerraformExecuteStepRequest terraformExecuteStepRequest)
      throws InterruptedException, IOException, TimeoutException, TerraformCommandExecutionException {
    CliResponse response;
    LogCallback logCallback = terraformExecuteStepRequest.getLogCallback();
    String tfBackendConfigsFile = terraformExecuteStepRequest.getTfBackendConfigsFile();
    TerraformInitCommandRequest terraformInitCommandRequest =
        TerraformInitCommandRequest.builder()
            .tfBackendConfigsFilePath(tfBackendConfigsFile)
            .additionalCliFlags(terraformExecuteStepRequest.getAdditionalCliFlags())
            .build();

    if (!isEmpty(tfBackendConfigsFile)) {
      logCallback.saveExecutionLog(
          format(PRINT_BACKEND_CONFIG, FileUtils.readFileToString(FileUtils.getFile(tfBackendConfigsFile), UTF_8)),
          INFO, CommandExecutionStatus.RUNNING);
    }
    terraformClient.init(terraformInitCommandRequest, terraformExecuteStepRequest.getTimeoutInMillis(),
        terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(), logCallback);

    TerraformStepResponse terraformPlanStepResponse = null;

    if (!terraformExecuteStepRequest.isTerraformCloudCli()) {
      String workspace = terraformExecuteStepRequest.getWorkspace();
      if (isNotEmpty(workspace)) {
        selectWorkspaceIfExist(terraformExecuteStepRequest, workspace);
      }

      if (!terraformExecuteStepRequest.isSkipTerraformRefresh()) {
        if (!(terraformExecuteStepRequest.getEncryptedTfPlan() != null
                && terraformExecuteStepRequest.isSkipRefreshBeforeApplyingPlan())) {
          TerraformRefreshCommandRequest terraformRefreshCommandRequest =
              TerraformRefreshCommandRequest.builder()
                  .varFilePaths(terraformExecuteStepRequest.getTfVarFilePaths())
                  .varParams(terraformExecuteStepRequest.getVarParams())
                  .targets(terraformExecuteStepRequest.getTargets())
                  .uiLogs(terraformExecuteStepRequest.getUiLogs())
                  .additionalCliFlags(terraformExecuteStepRequest.getAdditionalCliFlags())
                  .build();
          terraformClient.refresh(terraformRefreshCommandRequest, terraformExecuteStepRequest.getTimeoutInMillis(),
              terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(), logCallback);
        }
      }
      // Execute TF plan
      terraformPlanStepResponse = executeTerraformPlanCommand(terraformExecuteStepRequest);
    }

    TerraformApplyCommandRequest terraformApplyCommandRequest =
        TerraformApplyCommandRequest.builder()
            .planName(TERRAFORM_PLAN_FILE_OUTPUT_NAME)
            .isTerraformCloudCli(terraformExecuteStepRequest.isTerraformCloudCli())
            .targets(terraformExecuteStepRequest.getTargets())
            .additionalCliFlags(terraformExecuteStepRequest.getAdditionalCliFlags())
            .build();

    terraformClient.apply(terraformApplyCommandRequest, terraformExecuteStepRequest.getTimeoutInMillis(),
        terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(), logCallback);

    response = terraformClient.output(terraformExecuteStepRequest.getTfOutputsFile(),
        terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
        terraformExecuteStepRequest.getScriptDirectory(), logCallback);

    TerraformStepResponseBuilder applyResponse = TerraformStepResponse.builder();
    if (terraformPlanStepResponse != null) {
      applyResponse.terraformPlanSummary(terraformPlanStepResponse.getTerraformPlanSummary());
    }
    applyResponse.cliResponse(response);
    return applyResponse.build();
  }

  private void selectWorkspaceIfExist(TerraformExecuteStepRequest terraformExecuteStepRequest, String workspace)
      throws InterruptedException, TimeoutException, IOException {
    CliResponse response;
    response = terraformClient.getWorkspaceList(terraformExecuteStepRequest.getTimeoutInMillis(),
        terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
        terraformExecuteStepRequest.getLogCallback());
    if (response != null && response.getOutput() != null) {
      List<String> workspacelist = parseOutput(response.getOutput());
      // if workspace is specified in Harness but none exists in the environment, then select a new workspace
      terraformClient.workspace(workspace, workspacelist.contains(workspace),
          terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
          terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback(),
          terraformExecuteStepRequest.getAdditionalCliFlags());
    }
  }

  @NotNull
  @VisibleForTesting
  public TerraformStepResponse executeTerraformPlanCommand(TerraformExecuteStepRequest terraformExecuteStepRequest)
      throws InterruptedException, TimeoutException, IOException {
    CliResponse response = null;
    TerraformPlanSummary terraformPlanSummary = null;
    if (terraformExecuteStepRequest.getEncryptedTfPlan() != null) {
      terraformExecuteStepRequest.getLogCallback().saveExecutionLog(
          color("\nDecrypting terraform plan before applying\n", LogColor.Yellow, LogWeight.Bold), INFO,
          CommandExecutionStatus.RUNNING);
      saveTerraformPlanContentToFile(terraformExecuteStepRequest.getEncryptionConfig(),
          terraformExecuteStepRequest.getEncryptedTfPlan(), terraformExecuteStepRequest.getScriptDirectory(),
          terraformExecuteStepRequest.getAccountId(), TERRAFORM_PLAN_FILE_OUTPUT_NAME);
      terraformPlanSummary =
          analyseTerraformPlanSummaryWithTfClient(terraformExecuteStepRequest.isAnalyseTfPlanSummary(),
              terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
              terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback(),
              terraformExecuteStepRequest.getPlanLogOutputStream(), TERRAFORM_PLAN_FILE_OUTPUT_NAME);
      terraformExecuteStepRequest.getLogCallback().saveExecutionLog(
          color("\nUsing approved terraform plan \n", LogColor.Yellow, LogWeight.Bold), INFO,
          CommandExecutionStatus.RUNNING);
    } else {
      terraformExecuteStepRequest.getLogCallback().saveExecutionLog(
          color("\nGenerating terraform plan\n", LogColor.Yellow, LogWeight.Bold), INFO,
          CommandExecutionStatus.RUNNING);

      TerraformPlanCommandRequest terraformPlanCommandRequest =
          TerraformPlanCommandRequest.builder()
              .varFilePaths(terraformExecuteStepRequest.getTfVarFilePaths())
              .varParams(terraformExecuteStepRequest.getVarParams())
              .targets(terraformExecuteStepRequest.getTargets())
              .destroySet(terraformExecuteStepRequest.isTfPlanDestroy())
              .uiLogs(terraformExecuteStepRequest.getUiLogs())
              .isTerraformCloudCli(terraformExecuteStepRequest.isTerraformCloudCli())
              .additionalCliFlags(terraformExecuteStepRequest.getAdditionalCliFlags())
              .build();
      response = terraformClient.plan(terraformPlanCommandRequest, terraformExecuteStepRequest.getTimeoutInMillis(),
          terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
          terraformExecuteStepRequest.getLogCallback());

      if (terraformExecuteStepRequest.isSaveTerraformJson() && !terraformExecuteStepRequest.isTerraformCloudCli()) {
        response =
            executeTerraformShowCommandWithTfClient(terraformExecuteStepRequest.isTfPlanDestroy() ? DESTROY : APPLY,
                terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
                terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback(),
                terraformExecuteStepRequest.getPlanJsonLogOutputStream(),
                terraformExecuteStepRequest.isUseOptimizedTfPlan());
      }

      // Generating Human Readable Representation of the tfplan
      if (terraformExecuteStepRequest.isSaveTerraformHumanReadablePlan()
          && !terraformExecuteStepRequest.isTerraformCloudCli()) {
        executeTerraformHumanReadableShowCommandWithTfClient(
            terraformExecuteStepRequest.isTfPlanDestroy() ? DESTROY : APPLY,
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback(),
            terraformExecuteStepRequest.getPlanHumanReadableOutputStream(),
            terraformExecuteStepRequest.isUseOptimizedTfPlan());
      }

      terraformPlanSummary = analyseTerraformPlanSummaryWithTfClient(
          terraformExecuteStepRequest.isAnalyseTfPlanSummary(), terraformExecuteStepRequest.getTimeoutInMillis(),
          terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(),
          terraformExecuteStepRequest.getLogCallback(), terraformExecuteStepRequest.getPlanLogOutputStream(),
          terraformExecuteStepRequest.isTfPlanDestroy() ? TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME
                                                        : TERRAFORM_PLAN_FILE_OUTPUT_NAME);
    }

    return TerraformStepResponse.builder().cliResponse(response).terraformPlanSummary(terraformPlanSummary).build();
  }

  @Override
  public TerraformStepResponse executeTerraformPlanStep(TerraformExecuteStepRequest terraformExecuteStepRequest)
      throws InterruptedException, IOException, TimeoutException {
    String tfBackendConfigsFile = terraformExecuteStepRequest.getTfBackendConfigsFile();
    LogCallback logCallback = terraformExecuteStepRequest.getLogCallback();

    TerraformInitCommandRequest terraformInitCommandRequest =
        TerraformInitCommandRequest.builder()
            .tfBackendConfigsFilePath(tfBackendConfigsFile)
            .additionalCliFlags(terraformExecuteStepRequest.getAdditionalCliFlags())
            .build();
    if (!isEmpty(tfBackendConfigsFile)) {
      logCallback.saveExecutionLog(
          format(PRINT_BACKEND_CONFIG, FileUtils.readFileToString(FileUtils.getFile(tfBackendConfigsFile), UTF_8)),
          INFO, CommandExecutionStatus.RUNNING);
    }
    terraformClient.init(terraformInitCommandRequest, terraformExecuteStepRequest.getTimeoutInMillis(),
        terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(), logCallback);

    if (!terraformExecuteStepRequest.isTerraformCloudCli()) {
      String workspace = terraformExecuteStepRequest.getWorkspace();
      if (isNotEmpty(workspace)) {
        selectWorkspaceIfExist(terraformExecuteStepRequest, workspace);
      }

      if (!terraformExecuteStepRequest.isSkipTerraformRefresh()) {
        // Plan step always performs a refresh
        TerraformRefreshCommandRequest terraformRefreshCommandRequest =
            TerraformRefreshCommandRequest.builder()
                .varFilePaths(terraformExecuteStepRequest.getTfVarFilePaths())
                .varParams(terraformExecuteStepRequest.getVarParams())
                .targets(terraformExecuteStepRequest.getTargets())
                .uiLogs(terraformExecuteStepRequest.getUiLogs())
                .additionalCliFlags(terraformExecuteStepRequest.getAdditionalCliFlags())
                .build();
        terraformClient.refresh(terraformRefreshCommandRequest, terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(), logCallback);
      }
    }

    return executeTerraformPlanCommand(terraformExecuteStepRequest);
  }

  @Override
  public TerraformStepResponse executeTerraformDestroyStep(TerraformExecuteStepRequest terraformExecuteStepRequest)
      throws InterruptedException, IOException, TimeoutException {
    CliResponse response;
    TerraformPlanSummary terraformPlanSummary = null;
    LogCallback logCallback = terraformExecuteStepRequest.getLogCallback();
    String tfBackendConfigsFile = terraformExecuteStepRequest.getTfBackendConfigsFile();
    TerraformInitCommandRequest terraformInitCommandRequest =
        TerraformInitCommandRequest.builder()
            .tfBackendConfigsFilePath(tfBackendConfigsFile)
            .additionalCliFlags(terraformExecuteStepRequest.getAdditionalCliFlags())
            .build();

    if (!isEmpty(tfBackendConfigsFile)) {
      logCallback.saveExecutionLog(
          format(PRINT_BACKEND_CONFIG, FileUtils.readFileToString(FileUtils.getFile(tfBackendConfigsFile), UTF_8)),
          INFO, CommandExecutionStatus.RUNNING);
    }
    terraformClient.init(terraformInitCommandRequest, terraformExecuteStepRequest.getTimeoutInMillis(),
        terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(), logCallback);

    if (!terraformExecuteStepRequest.isTerraformCloudCli()) {
      String workspace = terraformExecuteStepRequest.getWorkspace();
      if (isNotEmpty(workspace)) {
        selectWorkspaceIfExist(terraformExecuteStepRequest, workspace);
      }

      if (!terraformExecuteStepRequest.isSkipTerraformRefresh()) {
        if (!(terraformExecuteStepRequest.getEncryptedTfPlan() != null
                && terraformExecuteStepRequest.isSkipRefreshBeforeApplyingPlan())) {
          TerraformRefreshCommandRequest terraformRefreshCommandRequest =
              TerraformRefreshCommandRequest.builder()
                  .varFilePaths(terraformExecuteStepRequest.getTfVarFilePaths())
                  .varParams(terraformExecuteStepRequest.getVarParams())
                  .targets(terraformExecuteStepRequest.getTargets())
                  .uiLogs(terraformExecuteStepRequest.getUiLogs())
                  .additionalCliFlags(terraformExecuteStepRequest.getAdditionalCliFlags())
                  .build();
          terraformClient.refresh(terraformRefreshCommandRequest, terraformExecuteStepRequest.getTimeoutInMillis(),
              terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(), logCallback);
        }
      }
    }

    if (terraformExecuteStepRequest.isRunPlanOnly()) {
      TerraformPlanCommandRequest terraformPlanCommandRequest =
          TerraformPlanCommandRequest.builder()
              .varFilePaths(terraformExecuteStepRequest.getTfVarFilePaths())
              .varParams(terraformExecuteStepRequest.getVarParams())
              .targets(terraformExecuteStepRequest.getTargets())
              .destroySet(true)
              .uiLogs(terraformExecuteStepRequest.getUiLogs())
              .build();
      response = terraformClient.plan(terraformPlanCommandRequest, terraformExecuteStepRequest.getTimeoutInMillis(),
          terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(), logCallback);

      if (response.getExitCode() == 0) {
        terraformPlanSummary = analyseTerraformPlanSummaryWithTfClient(
            terraformExecuteStepRequest.isAnalyseTfPlanSummary(), terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(), logCallback,
            terraformExecuteStepRequest.getPlanLogOutputStream(), TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME);
      }

      if (terraformExecuteStepRequest.isSaveTerraformJson()) {
        response = executeTerraformShowCommandWithTfClient(DESTROY, terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(), logCallback,
            terraformExecuteStepRequest.getPlanJsonLogOutputStream(),
            terraformExecuteStepRequest.isUseOptimizedTfPlan());
      }
      if (terraformExecuteStepRequest.isSaveTerraformHumanReadablePlan()) {
        response = executeTerraformHumanReadableShowCommandWithTfClient(DESTROY,
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), terraformExecuteStepRequest.getLogCallback(),
            terraformExecuteStepRequest.getPlanHumanReadableOutputStream(),
            terraformExecuteStepRequest.isUseOptimizedTfPlan());
      }
    } else {
      if (terraformExecuteStepRequest.getEncryptedTfPlan() == null) {
        TerraformDestroyCommandRequest terraformDestroyCommandRequest =
            TerraformDestroyCommandRequest.builder()
                .varFilePaths(terraformExecuteStepRequest.getTfVarFilePaths())
                .varParams(terraformExecuteStepRequest.getVarParams())
                .uiLogs(terraformExecuteStepRequest.getUiLogs())
                .targets(terraformExecuteStepRequest.getTargets())
                .isTerraformCloudCli(terraformExecuteStepRequest.isTerraformCloudCli())
                .additionalCliFlags(terraformExecuteStepRequest.getAdditionalCliFlags())
                .build();
        response = terraformClient.destroy(terraformDestroyCommandRequest,
            terraformExecuteStepRequest.getTimeoutInMillis(), terraformExecuteStepRequest.getEnvVars(),
            terraformExecuteStepRequest.getScriptDirectory(), logCallback);
      } else {
        saveTerraformPlanContentToFile(terraformExecuteStepRequest.getEncryptionConfig(),
            terraformExecuteStepRequest.getEncryptedTfPlan(), terraformExecuteStepRequest.getScriptDirectory(),
            terraformExecuteStepRequest.getAccountId(), TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME);

        terraformPlanSummary = analyseTerraformPlanSummaryWithTfClient(
            terraformExecuteStepRequest.isAnalyseTfPlanSummary(), terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(), logCallback,
            terraformExecuteStepRequest.getPlanLogOutputStream(), TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME);

        TerraformApplyCommandRequest terraformApplyCommandRequest =
            TerraformApplyCommandRequest.builder()
                .planName(TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME)
                .additionalCliFlags(terraformExecuteStepRequest.getAdditionalCliFlags())
                .build();
        response = terraformClient.apply(terraformApplyCommandRequest, terraformExecuteStepRequest.getTimeoutInMillis(),
            terraformExecuteStepRequest.getEnvVars(), terraformExecuteStepRequest.getScriptDirectory(), logCallback);
      }
    }

    return TerraformStepResponse.builder().cliResponse(response).terraformPlanSummary(terraformPlanSummary).build();
  }

  private CliResponse executeTerraformShowCommandWithTfClient(TerraformCommand terraformCommand, long timeoutInMillis,
      Map<String, String> envVars, String scriptDirectory, LogCallback logCallback,
      PlanJsonLogOutputStream planJsonLogOutputStream, boolean useOptimizedTfPlan)
      throws IOException, InterruptedException, TimeoutException {
    String planName =
        terraformCommand == APPLY ? TERRAFORM_PLAN_FILE_OUTPUT_NAME : TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
    logCallback.saveExecutionLog(
        format("%nGenerating JSON representation of %s %n", planName), INFO, CommandExecutionStatus.RUNNING);
    CliResponse response =
        terraformClient.show(planName, timeoutInMillis, envVars, scriptDirectory, logCallback, planJsonLogOutputStream);
    if (!useOptimizedTfPlan && response.getCommandExecutionStatus().equals(CommandExecutionStatus.SUCCESS)) {
      logCallback.saveExecutionLog(
          format("%nJSON representation of %s is exported as a variable %s %n", planName,
              terraformCommand == APPLY ? TERRAFORM_APPLY_PLAN_FILE_VAR_NAME : TERRAFORM_DESTROY_PLAN_FILE_VAR_NAME),
          INFO, CommandExecutionStatus.RUNNING);
    }

    return response;
  }

  private CliResponse executeTerraformHumanReadableShowCommandWithTfClient(TerraformCommand terraformCommand,
      long timeoutInMillis, Map<String, String> envVars, String scriptDirectory, LogCallback logCallback,
      PlanHumanReadableOutputStream planHumanReadableOutputStream, boolean useOptimizedTfPlan)
      throws IOException, InterruptedException, TimeoutException {
    String planName =
        terraformCommand == APPLY ? TERRAFORM_PLAN_FILE_OUTPUT_NAME : TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
    CliResponse response = terraformClient.prepareHumanReadablePlan(
        planName, timeoutInMillis, envVars, scriptDirectory, logCallback, planHumanReadableOutputStream);
    if (!useOptimizedTfPlan && response.getCommandExecutionStatus().equals(CommandExecutionStatus.SUCCESS)) {
      logCallback.saveExecutionLog(
          format("%nHuman Readable representation of %s is exported as a variable %s %n", planName,
              terraformCommand == APPLY ? TERRAFORM_HUMAN_READABLE_PLAN_FILE_VAR_NAME
                                        : TERRAFORM_DESTROY_HUMAN_READABLE_PLAN_FILE_VAR_NAME),
          INFO, CommandExecutionStatus.RUNNING);
    }

    return response;
  }

  private TerraformPlanSummary analyseTerraformPlanSummaryWithTfClient(boolean shouldAnalyseTfPlanSymmary,
      long timeoutInMillis, Map<String, String> envVars, String scriptDirectory, LogCallback logCallback,
      PlanLogOutputStream planLogOutputStream, String planName)
      throws IOException, InterruptedException, TimeoutException {
    if (shouldAnalyseTfPlanSymmary) {
      logCallback.saveExecutionLog(
          format("%nAnalysing Terraform plan %s %n", planName), INFO, CommandExecutionStatus.RUNNING);
      CliResponse response =
          terraformClient.show(planName, timeoutInMillis, envVars, scriptDirectory, logCallback, planLogOutputStream);

      return processTerraformPlanSummary(response.getExitCode(), logCallback, planLogOutputStream);
    }

    return null;
  }

  public TerraformPlanSummary processTerraformPlanSummary(
      int exitCode, LogCallback logCallback, PlanLogOutputStream planLogOutputStream) {
    if (exitCode == 0 && planLogOutputStream != null && planLogOutputStream.processPlan()) {
      return generateTerraformPlanSummary(exitCode, logCallback, planLogOutputStream);
    }

    log.warn("Parsing Terraform Plan summary has failed!");
    logCallback.saveExecutionLog("Parsing Terraform Plan summary has failed!", WARN);

    return null;
  }

  public TerraformPlanSummary generateTerraformPlanSummary(
      int exitCode, LogCallback logCallback, PlanLogOutputStream planLogOutputStream) {
    logCallback.saveExecutionLog("\nNumber of resources to add: " + planLogOutputStream.getNumOfResourcesToAdd(), INFO,
        CommandExecutionStatus.RUNNING);
    logCallback.saveExecutionLog("Number of resources to change: " + planLogOutputStream.getNumOfResourcesToChange(),
        INFO, CommandExecutionStatus.RUNNING);
    logCallback.saveExecutionLog("Number of resources to destroy: " + planLogOutputStream.getNumOfResourcesToDestroy(),
        INFO, CommandExecutionStatus.RUNNING);

    if (planLogOutputStream.planChangesExist()) {
      logCallback.saveExecutionLog(
          color("\nThere are changes to be made\n", Yellow, Bold), INFO, CommandExecutionStatus.RUNNING);
    } else {
      logCallback.saveExecutionLog(
          color("\nThere are NOT changes to be made\n", Yellow, Bold), INFO, CommandExecutionStatus.RUNNING);
    }

    return TerraformPlanSummary.builder()
        .add(planLogOutputStream.getNumOfResourcesToAdd())
        .commandExitCode(exitCode)
        .change(planLogOutputStream.getNumOfResourcesToChange())
        .destroy(planLogOutputStream.getNumOfResourcesToDestroy())
        .changesExist(planLogOutputStream.planChangesExist())
        .build();
  }

  @VisibleForTesting
  public void saveTerraformPlanContentToFile(EncryptionConfig encryptionConfig, EncryptedRecordData encryptedTfPlan,
      String scriptDirectory, String accountId, String terraformOutputFileName) throws IOException {
    File tfPlanFile = Paths.get(scriptDirectory, terraformOutputFileName).toFile();
    byte[] decryptedTerraformPlan =
        encryptDecryptHelper.getDecryptedContent(encryptionConfig, encryptedTfPlan, accountId);
    FileUtils.copyInputStreamToFile(new ByteArrayInputStream(decryptedTerraformPlan), tfPlanFile);
  }

  public EncryptedRecordData encryptPlan(byte[] content, String planName, EncryptionConfig encryptionConfig) {
    return (EncryptedRecordData) encryptDecryptHelper.encryptContent(content, planName, encryptionConfig);
  }

  public EncryptedRecordData encryptPlan(
      byte[] content, TerraformTaskNGParameters taskNGParameters, String delegateId, String taskId) throws IOException {
    if (taskNGParameters.isUseOptimizedTfPlan()) {
      DelegateFile planDelegateFile =
          aDelegateFile()
              .withAccountId(taskNGParameters.getAccountId())
              .withDelegateId(delegateId)
              .withTaskId(taskId)
              .withEntityId(taskNGParameters.getEntityId())
              .withBucket(FileBucket.TERRAFORM_PLAN)
              .withFileName(format(TERRAFORM_PLAN_FILE_OUTPUT_NAME, taskNGParameters.getPlanName()))
              .build();
      return (EncryptedRecordData) encryptDecryptHelper.encryptFile(
          content, taskNGParameters.getPlanName(), taskNGParameters.getEncryptionConfig(), planDelegateFile);
    }

    return encryptPlan(content, taskNGParameters.getPlanName(), taskNGParameters.getEncryptionConfig());
  }

  @NotNull
  public String getPlanName(TerraformCommand command) {
    switch (command) {
      case APPLY:
        return TERRAFORM_PLAN_FILE_OUTPUT_NAME;
      case DESTROY:
        return TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
      default:
        throw new IllegalArgumentException("Invalid Terraform Command : " + command.toString());
    }
  }

  @NonNull
  public String resolveBaseDir(String accountId, String entityId) {
    return TF_BASE_DIR.replace("${ACCOUNT_ID}", accountId).replace("${ENTITY_ID}", entityId);
  }

  @Override
  public String activityIdBasedBaseDir(String accountId, String entityId, String activityId) {
    return ACTIVITY_ID_BASED_TF_BASE_DIR.replace("${ACCOUNT_ID}", accountId)
        .replace("${ENTITY_ID}", entityId)
        .replace("${ACTIVITY_ID}", activityId);
  }

  public String resolveScriptDirectory(String workingDir, String scriptPath) {
    return Paths
        .get(Paths.get(System.getProperty(USER_DIR_KEY)).toString(), workingDir, scriptPath == null ? "" : scriptPath)
        .toString();
  }

  public String getLatestCommitSHA(File repoDir) {
    if (repoDir.exists()) {
      try (Git git = Git.open(repoDir)) {
        Iterator<RevCommit> commits = git.log().call().iterator();
        if (commits.hasNext()) {
          RevCommit firstCommit = commits.next();

          return firstCommit.toString().split(" ")[1];
        }
      } catch (IOException | GitAPIException e) {
        log.error(
            "Failed to extract the commit id from the cloned repo.", ExceptionMessageSanitizer.sanitizeException(e));
      }
    }
    return null;
  }

  public GitBaseRequest getGitBaseRequestForConfigFile(
      String accountId, GitStoreDelegateConfig confileFileGitStore, GitConfigDTO configFileGitConfigDTO) {
    secretDecryptionService.decrypt(configFileGitConfigDTO.getGitAuth(), confileFileGitStore.getEncryptedDataDetails());
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        configFileGitConfigDTO.getGitAuth(), confileFileGitStore.getEncryptedDataDetails());

    SshSessionConfig sshSessionConfig = null;
    if (configFileGitConfigDTO.getGitAuthType() == SSH) {
      sshSessionConfig = getSshSessionConfig(confileFileGitStore);
    }

    return GitBaseRequest.builder()
        .branch(confileFileGitStore.getBranch())
        .commitId(confileFileGitStore.getCommitId())
        .repoUrl(configFileGitConfigDTO.getUrl())
        .repoType(GitRepositoryType.TERRAFORM)
        .authRequest(
            ngGitService.getAuthRequest((GitConfigDTO) confileFileGitStore.getGitConfigDTO(), sshSessionConfig))
        .accountId(accountId)
        .connectorId(confileFileGitStore.getConnectorName())
        .build();
  }

  public Map<String, String> buildCommitIdToFetchedFilesMap(String configFileIdentifier,
      GitBaseRequest gitBaseRequestForConfigFile, Map<String, String> commitIdForConfigFilesMap) {
    // Config File
    commitIdForConfigFilesMap.put(configFileIdentifier, getLatestCommitSHAFromLocalRepo(gitBaseRequestForConfigFile));
    return commitIdForConfigFilesMap;
  }

  public String getLatestCommitSHAFromLocalRepo(GitBaseRequest gitBaseRequest) {
    return getLatestCommitSHA(new File(gitClientHelper.getRepoDirectory(gitBaseRequest)));
  }

  public String fetchConfigFileAndPrepareScriptDir(GitBaseRequest gitBaseRequestForConfigFile, String accountId,
      String workspace, String currentStateFileId, GitStoreDelegateConfig confileFileGitStore, LogCallback logCallback,
      String scriptPath, String baseDir) {
    fetchConfigFileAndCloneLocally(gitBaseRequestForConfigFile, logCallback);

    String workingDir = getWorkingDir(baseDir);

    copyConfigFilestoWorkingDirectory(logCallback, gitBaseRequestForConfigFile, baseDir, workingDir);

    String scriptDirectory = resolveScriptDirectory(workingDir, scriptPath);
    log.info("Script Directory: " + scriptDirectory);
    logCallback.saveExecutionLog(
        format("Script Directory: [%s]", scriptDirectory), INFO, CommandExecutionStatus.RUNNING);

    try {
      TerraformHelperUtils.ensureLocalCleanup(scriptDirectory);
      downloadTfStateFile(workspace, accountId, currentStateFileId, scriptDirectory);
    } catch (IOException ioException) {
      log.warn("Exception Occurred when cleaning Terraform local directory",
          ExceptionMessageSanitizer.sanitizeException(ioException));
    }
    return scriptDirectory;
  }

  public String fetchConfigFileAndPrepareScriptDir(ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig,
      String accountId, String workspace, String currentStateFileId, LogCallback logCallback, String baseDir) {
    if (artifactoryStoreDelegateConfig.getArtifacts().size() < 1) {
      throw NestedExceptionUtils.hintWithExplanationException(HINT_NO_ARTIFACT_DETAILS_FOR_ARTIFACTORY_CONFIG,
          EXPLANATION_NO_ARTIFACT_DETAILS_FOR_ARTIFACTORY_CONFIG,
          new TerraformCommandExecutionException("No Artifactory config files details set", WingsException.USER));
    }
    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        (ArtifactoryConnectorDTO) artifactoryStoreDelegateConfig.getConnectorDTO().getConnectorConfig();
    secretDecryptionService.decrypt(
        artifactoryConnectorDTO.getAuth().getCredentials(), artifactoryStoreDelegateConfig.getEncryptedDataDetails());
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        artifactoryConnectorDTO.getAuth().getCredentials(), artifactoryStoreDelegateConfig.getEncryptedDataDetails());
    ArtifactoryConfigRequest artifactoryConfigRequest =
        artifactoryRequestMapper.toArtifactoryRequest(artifactoryConnectorDTO);
    Map<String, String> artifactMetadata = new HashMap<>();
    String artifactPath = artifactoryStoreDelegateConfig.getArtifacts().get(0);
    artifactMetadata.put(ARTIFACT_PATH_METADATA_KEY, artifactPath);
    artifactMetadata.put(ARTIFACT_NAME_METADATA_KEY, artifactPath);
    InputStream artifactInputStream = artifactoryNgService.downloadArtifacts(artifactoryConfigRequest,
        artifactoryStoreDelegateConfig.getRepositoryName(), artifactMetadata, ARTIFACT_PATH_METADATA_KEY,
        ARTIFACT_NAME_METADATA_KEY);
    if (artifactInputStream == null) {
      throw NestedExceptionUtils.hintWithExplanationException(HINT_FAILED_TO_DOWNLOAD_FROM_ARTIFACTORY,
          String.format(EXPLANATION_FAILED_TO_DOWNLOAD_FROM_ARTIFACTORY, artifactPath,
              artifactoryConfigRequest.getArtifactoryUrl()),
          new TerraformCommandExecutionException("Failed to download config file", WingsException.USER));
    }
    String workingDir = getWorkingDir(baseDir);

    copyConfigFilesToWorkingDirectory(
        logCallback, artifactInputStream, baseDir, workingDir, artifactoryStoreDelegateConfig.getRepositoryName());

    String scriptDirectory = resolveScriptDirectory(workingDir, artifactoryStoreDelegateConfig.getRepositoryName());

    log.info("Script Directory: " + scriptDirectory);
    logCallback.saveExecutionLog(
        format("Script Directory: [%s]", scriptDirectory), INFO, CommandExecutionStatus.RUNNING);

    try {
      TerraformHelperUtils.ensureLocalCleanup(scriptDirectory);
      downloadTfStateFile(workspace, accountId, currentStateFileId, scriptDirectory);
    } catch (IOException ioException) {
      log.warn("Exception Occurred when cleaning Terraform local directory",
          ExceptionMessageSanitizer.sanitizeException(ioException));
    }
    return scriptDirectory;
  }

  @Override
  public String fetchS3ConfigFilesAndPrepareScriptDir(S3StoreTFDelegateConfig s3StoreTFDelegateConfig,
      TerraformTaskNGParameters terraformTaskNGParameters, String baseDir,
      Map<String, Map<String, String>> keyVersionMap, LogCallback logCallback) {
    String scriptPath = FilenameUtils.normalize(s3StoreTFDelegateConfig.getPaths().get(0));
    logCallback.saveExecutionLog("Normalized Path: " + scriptPath, INFO, CommandExecutionStatus.RUNNING);
    String workingDir = getWorkingDir(baseDir);
    String folderPath = s3StoreTFDelegateConfig.getPaths().get(0);
    String prefix;
    if ("/".equals(folderPath)) {
      prefix = "";
    } else if (!folderPath.endsWith("/")) {
      prefix = folderPath + "/";
    } else {
      prefix = folderPath;
    }
    logCallback.saveExecutionLog("Downloading S3 objects...", INFO, CommandExecutionStatus.RUNNING);
    downloadS3Objects(s3StoreTFDelegateConfig, prefix, keyVersionMap, workingDir);
    logCallback.saveExecutionLog("Downloading completed", INFO, CommandExecutionStatus.RUNNING);
    String scriptDirectory = resolveScriptDirectory(workingDir, scriptPath);
    try {
      TerraformHelperUtils.ensureLocalCleanup(scriptDirectory);
      downloadTfStateFile(terraformTaskNGParameters.getWorkspace(), terraformTaskNGParameters.getAccountId(),
          terraformTaskNGParameters.getCurrentStateFileId(), scriptDirectory);
    } catch (IOException ioException) {
      log.warn("Exception Occurred when cleaning Terraform local directory",
          ExceptionMessageSanitizer.sanitizeException(ioException));
    }
    return scriptDirectory;
  }

  private void downloadS3Objects(S3StoreTFDelegateConfig s3StoreTFDelegateConfig, String prefix,
      Map<String, Map<String, String>> keyVersionMap, String destDir) {
    AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) s3StoreTFDelegateConfig.getConnectorDTO().getConnectorConfig();
    secretDecryptionService.decrypt(
        awsConnectorDTO.getCredential().getConfig(), s3StoreTFDelegateConfig.getEncryptedDataDetails());
    AwsInternalConfig awsConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    String identifier = s3StoreTFDelegateConfig.getIdentifier();
    String region = s3StoreTFDelegateConfig.getRegion();
    String bucketName = s3StoreTFDelegateConfig.getBucketName();

    ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request();
    listObjectsV2Request.withPrefix(prefix).withBucketName(bucketName).withMaxKeys(500);

    List<String> objectKeyList = Lists.newArrayList();
    ListObjectsV2Result result;
    do {
      result = awsApiHelperService.listObjectsInS3(awsConfig, region, listObjectsV2Request);
      List<String> objectKeyListForCurrentBatch = result.getObjectSummaries()
                                                      .stream()
                                                      .map(S3ObjectSummary::getKey)
                                                      .filter(key -> !key.endsWith("/"))
                                                      .collect(toList());
      objectKeyList.addAll(objectKeyListForCurrentBatch);
      listObjectsV2Request.setContinuationToken(result.getNextContinuationToken());
    } while (result.isTruncated());

    if (objectKeyList.isEmpty()) {
      throw NestedExceptionUtils.hintWithExplanationException(HINT_FILES_NOT_FOUND_IN_S3_CONFIG,
          EXPLANATION_FILES_NOT_FOUND_IN_S3_CONFIG,
          new TerraformCommandExecutionException(
              format("Couldn't found any object in S3 bucket: [%s] with a prefix: [%s] ", bucketName, prefix),
              WingsException.USER));
    }
    boolean versioningEnabled = awsApiHelperService.isVersioningEnabledForBucket(awsConfig, bucketName, region);
    Map<String, String> versionMap =
        keyVersionMap.get(identifier) == null ? new HashMap<>() : keyVersionMap.get(identifier);
    for (String objectKey : objectKeyList) {
      String version =
          isEmpty(s3StoreTFDelegateConfig.getVersions()) ? null : s3StoreTFDelegateConfig.getVersions().get(objectKey);

      S3Object object = version != null && versioningEnabled
          ? awsApiHelperService.getVersionedObjectFromS3(
              awsConfig, region, bucketName, objectKey, s3StoreTFDelegateConfig.getVersions().get(objectKey))
          : awsApiHelperService.getObjectFromS3(awsConfig, region, bucketName, objectKey);

      if (versioningEnabled) {
        versionMap.put(objectKey, object.getObjectMetadata().getVersionId());
      }
      if (object != null) {
        try {
          FileUtils.copyInputStreamToFile(object.getObjectContent(), Paths.get(destDir, objectKey).toFile());
        } catch (IOException e) {
          log.error("Failed to download file from S3 bucket.", ExceptionMessageSanitizer.sanitizeException(e));
          throw new TerraformCommandExecutionException(
              "Error encountered when copying files to provisioner specific directory", WingsException.USER);
        }
      }
    }
    keyVersionMap.put(identifier, versionMap);
  }

  public String getWorkingDir(String baseDir) {
    return Paths.get(baseDir, TF_SCRIPT_DIR).toString();
  }

  public String getBaseDir(String entityId) {
    return TF_WORKING_DIR + entityId;
  }

  public void fetchConfigFileAndCloneLocally(GitBaseRequest gitBaseRequestForConfigFile, LogCallback logCallback) {
    try {
      gitClient.ensureRepoLocallyClonedAndUpdated(gitBaseRequestForConfigFile);
    } catch (RuntimeException ex) {
      RuntimeException sanitizedException = (RuntimeException) ExceptionMessageSanitizer.sanitizeException(ex);
      String msg = isNotEmpty(sanitizedException.getMessage())
          ? format("Failed performing git operation. Reason: %s", sanitizedException.getMessage())
          : "Failed performing git operation.";
      logCallback.saveExecutionLog(msg, ERROR, CommandExecutionStatus.RUNNING);
      throw new JGitRuntimeException(msg, sanitizedException.getCause(), DEFAULT_ERROR_CODE,
          gitBaseRequestForConfigFile.getCommitId(), gitBaseRequestForConfigFile.getBranch());
    }
  }

  public String uploadTfStateFile(String accountId, String delegateId, String taskId, String entityId, File tfStateFile)
      throws IOException {
    final DelegateFile delegateFile = aDelegateFile()
                                          .withAccountId(accountId)
                                          .withDelegateId(delegateId)
                                          .withTaskId(taskId)
                                          .withEntityId(entityId)
                                          .withBucket(FileBucket.TERRAFORM_STATE)
                                          .withFileName(TERRAFORM_STATE_FILE_NAME)
                                          .build();

    if (tfStateFile != null) {
      try (InputStream initialStream = new FileInputStream(tfStateFile)) {
        delegateFileManager.upload(delegateFile, initialStream);
      }
    } else {
      try (InputStream nullInputStream = new NullInputStream(0)) {
        delegateFileManager.upload(delegateFile, nullInputStream);
      }
    }
    return delegateFile.getFileId();
  }

  public void copyConfigFilestoWorkingDirectory(
      LogCallback logCallback, GitBaseRequest gitBaseRequestForConfigFile, String baseDir, String workingDir) {
    try {
      TerraformHelperUtils.copyFilesToWorkingDirectory(
          gitClientHelper.getRepoDirectory(gitBaseRequestForConfigFile), workingDir);
    } catch (Exception ex) {
      handleExceptionWhileCopyingConfigFiles(logCallback, baseDir, ExceptionMessageSanitizer.sanitizeException(ex));
    }
  }

  private void copyConfigFilesToWorkingDirectory(
      LogCallback logCallback, InputStream inputStream, String baseDir, String workingDir, String scriptDirectory) {
    try {
      File dest = new File(workingDir);
      deleteDirectoryAndItsContentIfExists(dest.getAbsolutePath());
      File scriptDir = new File(dest, scriptDirectory);
      scriptDir.mkdirs();
      unzip(scriptDir, new ZipInputStream(inputStream));
      FileIo.waitForDirectoryToBeAccessibleOutOfProcess(scriptDir.getPath(), 10);
    } catch (Exception ex) {
      handleExceptionWhileCopyingConfigFiles(logCallback, baseDir, ExceptionMessageSanitizer.sanitizeException(ex));
    }
  }

  public TerraformConfigInspectVersion getTerraformConfigInspectVersion(TerraformProvisionParameters parameters) {
    if (parameters.getTerraformConfigInspectVersion() != null
        && TfConfigInspectVersion.V1_2.equals(parameters.getTerraformConfigInspectVersion())) {
      return TerraformConfigInspectVersion.V1_2;
    } else if (parameters.isUseTfConfigInspectLatestVersion()) {
      return TerraformConfigInspectVersion.V1_1;
    } else {
      return TerraformConfigInspectVersion.V1_0;
    }
  }

  private void handleExceptionWhileCopyingConfigFiles(LogCallback logCallback, String baseDir, Exception ex) {
    log.error(String.format("Exception in copying files to provisioner specific directory", ex.getMessage()), ex);
    FileUtils.deleteQuietly(new File(baseDir));
    logCallback.saveExecutionLog(
        "Failed copying files to provisioner specific directory", ERROR, CommandExecutionStatus.RUNNING);
    throw new TerraformCommandExecutionException(
        "Error encountered when copying files to provisioner specific directory", WingsException.USER);
  }

  private static void unzip(File destDir, ZipInputStream zipInputStream) throws IOException {
    byte[] buffer = new byte[1024];
    ZipEntry zipEntry = zipInputStream.getNextEntry();
    while (zipEntry != null) {
      File newFile = getNewFileForZipEntry(destDir, zipEntry);
      if (zipEntry.isDirectory()) {
        if (!newFile.isDirectory() && !newFile.mkdirs()) {
          throw new IOException("Failed to create directory " + newFile);
        }
      } else {
        // fix for Windows-created archives
        File parent = newFile.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
          throw new IOException("Failed to create directory " + parent);
        }

        FileOutputStream fileOutputStream = new FileOutputStream(newFile);
        int len;
        while ((len = zipInputStream.read(buffer)) > 0) {
          fileOutputStream.write(buffer, 0, len);
        }
        fileOutputStream.close();
      }
      zipEntry = zipInputStream.getNextEntry();
    }
    zipInputStream.closeEntry();
    zipInputStream.close();
  }

  private static File getNewFileForZipEntry(File destinationDir, ZipEntry zipEntry) throws IOException {
    File destFile = new File(destinationDir, zipEntry.getName());

    String destDirPath = destinationDir.getCanonicalPath();
    String destFilePath = destFile.getCanonicalPath();

    if (!destFilePath.startsWith(destDirPath + File.separator)) {
      throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
    }

    return destFile;
  }

  public String checkoutRemoteBackendConfigFileAndConvertToFilePath(TerraformBackendConfigFileInfo configFileInfo,
      String scriptDir, LogCallback logCallback, String accountId, String tfConfigDirectory,
      Map<String, String> commitIdToFetchedFilesMap, Map<String, Map<String, String>> keyVersionMap)
      throws IOException {
    if (configFileInfo instanceof InlineTerraformBackendConfigFileInfo
        && ((InlineTerraformBackendConfigFileInfo) configFileInfo).getBackendConfigFileContent() != null) {
      return TerraformHelperUtils.createFileFromStringContent(
          ((InlineTerraformBackendConfigFileInfo) configFileInfo).getBackendConfigFileContent(), scriptDir,
          TERRAFORM_BACKEND_CONFIGS_FILE_NAME);
    } else if (configFileInfo instanceof RemoteTerraformFileInfo) {
      List<String> tfBackendConfigFilePath = new ArrayList<>();
      Path tfConfigDirAbsPath = Paths.get(tfConfigDirectory).toAbsolutePath();
      checkoutRemoteTerraformFileAndConvertToFilePath((RemoteTerraformFileInfo) configFileInfo, logCallback, accountId,
          tfConfigDirectory, tfBackendConfigFilePath, tfConfigDirAbsPath, commitIdToFetchedFilesMap, keyVersionMap);
      return tfBackendConfigFilePath.get(0);
    }
    return null;
  }

  private void checkoutRemoteTerraformFileAndConvertToFilePath(RemoteTerraformFileInfo remoteFileInfo,
      LogCallback logCallback, String accountId, String tfVarDirectory, List<String> filePaths, Path filesDirAbsPath,
      Map<String, String> commitIdToFetchedFilesMap, Map<String, Map<String, String>> keyVersionMap)
      throws IOException {
    if (remoteFileInfo.getGitFetchFilesConfig() != null) {
      GitStoreDelegateConfig gitStoreDelegateConfig =
          remoteFileInfo.getGitFetchFilesConfig().getGitStoreDelegateConfig();
      GitConfigDTO gitConfigDTO = (GitConfigDTO) gitStoreDelegateConfig.getGitConfigDTO();
      if (EmptyPredicate.isNotEmpty(gitStoreDelegateConfig.getPaths())) {
        String commitId = handleGitVarFiles(
            logCallback, accountId, tfVarDirectory, filesDirAbsPath, filePaths, gitStoreDelegateConfig, gitConfigDTO);
        commitIdToFetchedFilesMap.putIfAbsent(remoteFileInfo.getGitFetchFilesConfig().getIdentifier(), commitId);
      }
    } else if (remoteFileInfo.getFilestoreFetchFilesConfig() != null) {
      if (remoteFileInfo.getFilestoreFetchFilesConfig().getType() == ARTIFACTORY) {
        ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig =
            (ArtifactoryStoreDelegateConfig) remoteFileInfo.getFilestoreFetchFilesConfig();
        handleFileStorageFiles(logCallback, filesDirAbsPath, filePaths, artifactoryStoreDelegateConfig);
      } else if (remoteFileInfo.getFilestoreFetchFilesConfig().getType() == AMAZON_S3) {
        S3StoreTFDelegateConfig s3StoreTFDelegateConfig =
            (S3StoreTFDelegateConfig) remoteFileInfo.getFilestoreFetchFilesConfig();
        handleS3VarFiles(
            s3StoreTFDelegateConfig, tfVarDirectory, filesDirAbsPath, filePaths, keyVersionMap, logCallback);
      }
    }
  }

  private void handleS3VarFiles(S3StoreTFDelegateConfig s3StoreTFDelegateConfig, String tfVarDirectory,
      Path filesDirAbsPath, List<String> filePaths, Map<String, Map<String, String>> keyVersionMap,
      LogCallback logCallback) {
    logCallback.saveExecutionLog(
        format("Fetching files for %s from S3... Region: [%s], Bucket: [%s]", s3StoreTFDelegateConfig.getIdentifier(),
            s3StoreTFDelegateConfig.getRegion(), s3StoreTFDelegateConfig.getBucketName()),
        INFO, CommandExecutionStatus.RUNNING);
    for (String path : s3StoreTFDelegateConfig.getPaths()) {
      downloadS3Objects(s3StoreTFDelegateConfig, path, keyVersionMap, tfVarDirectory);
      filePaths.add(filesDirAbsPath + "/" + path);
    }
    logCallback.saveExecutionLog(
        format("Files are saved in directory: [%s]", filesDirAbsPath), INFO, CommandExecutionStatus.RUNNING);
  }

  public List<String> checkoutRemoteVarFileAndConvertToVarFilePaths(List<TerraformVarFileInfo> varFileInfo,
      String scriptDir, LogCallback logCallback, String accountId, String tfVarDirectory,
      Map<String, String> commitIdToFetchedFilesMap, boolean isTerraformCloudCli,
      Map<String, Map<String, String>> keyVersionMap) throws IOException {
    Path tfVarDirAbsPath = Paths.get(tfVarDirectory).toAbsolutePath();
    if (EmptyPredicate.isNotEmpty(varFileInfo)) {
      List<String> varFilePaths = new ArrayList<>();
      for (TerraformVarFileInfo varFile : varFileInfo) {
        if (varFile instanceof InlineTerraformVarFileInfo) {
          if (isTerraformCloudCli) {
            varFilePaths.add(TerraformHelperUtils.createFileFromStringContent(
                ((InlineTerraformVarFileInfo) varFile).getVarFileContent(), scriptDir,
                TERRAFORM_CLOUD_VARIABLES_FILE_NAME));
          } else {
            varFilePaths.add(TerraformHelperUtils.createFileFromStringContent(
                ((InlineTerraformVarFileInfo) varFile).getVarFileContent(), scriptDir, TERRAFORM_VARIABLES_FILE_NAME));
          }
        } else if (varFile instanceof RemoteTerraformVarFileInfo) {
          checkoutRemoteTerraformFileAndConvertToFilePath((RemoteTerraformFileInfo) varFile, logCallback, accountId,
              tfVarDirectory, varFilePaths, tfVarDirAbsPath, commitIdToFetchedFilesMap, keyVersionMap);
        }
      }
      logCallback.saveExecutionLog(
          format("Var File directory: [%s]", tfVarDirAbsPath), INFO, CommandExecutionStatus.RUNNING);
      return varFilePaths;
    }
    return Collections.emptyList();
  }

  private void handleFileStorageFiles(LogCallback logCallback, Path dirAbsPath, List<String> filePaths,
      ArtifactoryStoreDelegateConfig artifactoryStoreDelegateConfig) throws IOException {
    logCallback.saveExecutionLog(format("Fetching Var files from Artifactory repository: [%s]",
                                     artifactoryStoreDelegateConfig.getRepositoryName()),
        INFO, CommandExecutionStatus.RUNNING);
    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        (ArtifactoryConnectorDTO) artifactoryStoreDelegateConfig.getConnectorDTO().getConnectorConfig();
    secretDecryptionService.decrypt(
        artifactoryConnectorDTO.getAuth().getCredentials(), artifactoryStoreDelegateConfig.getEncryptedDataDetails());
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        artifactoryConnectorDTO.getAuth().getCredentials(), artifactoryStoreDelegateConfig.getEncryptedDataDetails());
    ArtifactoryConfigRequest artifactoryConfigRequest =
        artifactoryRequestMapper.toArtifactoryRequest(artifactoryConnectorDTO);

    for (String artifactPath : artifactoryStoreDelegateConfig.getArtifacts()) {
      Map<String, String> artifactMetadata = new HashMap<>();
      artifactMetadata.put(ARTIFACT_PATH_METADATA_KEY, artifactPath);
      artifactMetadata.put(ARTIFACT_NAME_METADATA_KEY, artifactPath);
      InputStream artifactInputStream = artifactoryNgService.downloadArtifacts(artifactoryConfigRequest,
          artifactoryStoreDelegateConfig.getRepositoryName(), artifactMetadata, ARTIFACT_PATH_METADATA_KEY,
          ARTIFACT_NAME_METADATA_KEY);
      if (artifactInputStream == null) {
        throw NestedExceptionUtils.hintWithExplanationException(HINT_FAILED_TO_DOWNLOAD_FROM_ARTIFACTORY,
            String.format(EXPLANATION_FAILED_TO_DOWNLOAD_FROM_ARTIFACTORY, artifactPath,
                artifactoryConfigRequest.getArtifactoryUrl()),
            new TerraformCommandExecutionException("Failed to download config file", WingsException.USER));
      }
      File tfVarDir = new File(dirAbsPath.toString(), artifactPath);

      if (!tfVarDir.exists()) {
        tfVarDir.mkdirs();
      }
      unzip(tfVarDir, new ZipInputStream(artifactInputStream));
      for (File file : tfVarDir.listFiles()) {
        if (file.isFile()) {
          filePaths.add(file.getAbsolutePath());
        }
      }
    }
  }

  private String handleGitVarFiles(LogCallback logCallback, String accountId, String tfVarDirectory,
      Path tfVarDirAbsPath, List<String> varFilePaths, GitStoreDelegateConfig gitStoreDelegateConfig,
      GitConfigDTO gitConfigDTO) throws IOException {
    logCallback.saveExecutionLog(format("Fetching Var files from Git repository: [%s]", gitConfigDTO.getUrl()), INFO,
        CommandExecutionStatus.RUNNING);

    secretDecryptionService.decrypt(gitConfigDTO.getGitAuth(), gitStoreDelegateConfig.getEncryptedDataDetails());
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        gitConfigDTO.getGitAuth(), gitStoreDelegateConfig.getEncryptedDataDetails());

    SshSessionConfig sshSessionConfig = null;
    if (gitConfigDTO.getGitAuthType() == SSH) {
      sshSessionConfig = getSshSessionConfig(gitStoreDelegateConfig);
    }

    String commitId =
        gitClient.downloadFiles(DownloadFilesRequest.builder()
                                    .branch(gitStoreDelegateConfig.getBranch())
                                    .commitId(gitStoreDelegateConfig.getCommitId())
                                    .filePaths(gitStoreDelegateConfig.getPaths())
                                    .connectorId(gitStoreDelegateConfig.getConnectorName())
                                    .repoUrl(gitConfigDTO.getUrl())
                                    .accountId(accountId)
                                    .recursive(true)
                                    .authRequest(ngGitService.getAuthRequest(gitConfigDTO, sshSessionConfig))
                                    .repoType(GitRepositoryType.TERRAFORM)
                                    .destinationDirectory(tfVarDirectory)
                                    .build());

    // One remote file can have multiple different var file paths provided
    // Combine them here and add to list.
    for (String paths : gitStoreDelegateConfig.getPaths()) {
      varFilePaths.add(tfVarDirAbsPath + "/" + paths);
    }
    return commitId;
  }

  private SshSessionConfig getSshSessionConfig(GitStoreDelegateConfig gitStoreDelegateConfig) {
    if (gitStoreDelegateConfig.getSshKeySpecDTO() == null) {
      throw new InvalidRequestException(
          format("SSHKeySpecDTO is null for connector %s", gitStoreDelegateConfig.getConnectorName()));
    }
    return sshSessionConfigMapper.getSSHSessionConfig(
        gitStoreDelegateConfig.getSshKeySpecDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());
  }

  public void configureCredentialsForModuleSource(String baseDir, Map<String, String> envVars,
      GitStoreDelegateConfig conFileFileGitStore, LogCallback logCallback) throws IOException {
    GitConfigDTO gitConfigDTO = (GitConfigDTO) conFileFileGitStore.getGitConfigDTO();
    if (gitConfigDTO.getGitAuthType() == SSH) {
      String sshKeyPath;
      SshSessionConfig sshSessionConfig = getSshSessionConfig(conFileFileGitStore);
      if (isNotEmpty(sshSessionConfig.getKeyPassphrase())) {
        logCallback.saveExecutionLog(
            color("\nExporting SSH Key with Passphrase for Module Source is not Supported", Yellow), WARN);
        return;
      }
      if (!sshSessionConfig.isKeyLess() && isNotEmpty(sshSessionConfig.getKey())) {
        String sshKey = String.valueOf(sshSessionConfig.getKey());
        Files.createDirectories(Paths.get(baseDir, SSH_KEY_DIR));
        sshKeyPath = Paths.get(baseDir, SSH_KEY_DIR, SSH_KEY_FILENAME).toAbsolutePath().toString();
        FileIo.writeUtf8StringToFile(sshKeyPath, sshKey);

      } else if (sshSessionConfig.isKeyLess() && isNotEmpty(sshSessionConfig.getKeyPath())) {
        sshKeyPath = sshSessionConfig.getKeyPath();

      } else {
        logCallback.saveExecutionLog(
            color("\nExporting Username and Password with SSH for Module Source is not Supported", Yellow), WARN);
        return;
      }
      exportSSHKey(envVars, sshKeyPath, logCallback);
    } else {
      logCallback.saveExecutionLog(
          color("\nExporting Username and Password for Module Source is not Supported", Yellow), WARN);
    }
  }

  public void exportSSHKey(Map<String, String> envVars, String sshKeyPath, LogCallback logCallback) throws IOException {
    logCallback.saveExecutionLog(color("\nExporting SSH Key:", White), INFO);

    File file = new File(Paths.get(sshKeyPath).toString());

    // Giving Read Only Permission to ownerOnly for the SSH File, This is needed to avoided security attack
    file.setWritable(false);
    file.setReadable(false, false);
    file.setExecutable(false);
    file.setReadable(true);

    String newSSHArg = TF_SSH_COMMAND_ARG + sshKeyPath;

    String sshCommand = System.getenv(GIT_SSH_COMMAND) == null ? SSH_COMMAND_PREFIX + newSSHArg
                                                               : System.getenv(GIT_SSH_COMMAND) + newSSHArg;

    envVars.put(GIT_SSH_COMMAND, sshCommand);

    logCallback.saveExecutionLog(color("\n   Successfully Exported SSH Key:", White), INFO);
  }

  public void performCleanupOfTfDirs(TerraformTaskNGParameters parameters, LogCallback logCallback) {
    {
      FileUtils.deleteQuietly(new File(getBaseDir(parameters.getEntityId())));
      if (parameters.getEncryptedTfPlan() != null) {
        try {
          boolean isSafelyDeleted = encryptDecryptHelper.deleteEncryptedRecord(
              parameters.getEncryptionConfig(), parameters.getEncryptedTfPlan());
          if (isSafelyDeleted) {
            log.info("Terraform Plan has been safely deleted from vault");
          }
        } catch (Exception ex) {
          Exception sanitizeException = ExceptionMessageSanitizer.sanitizeException(ex);
          logCallback.saveExecutionLog(
              color(format("Failed to delete secret: [%s] from vault: [%s], please clean it up",
                        parameters.getEncryptedTfPlan().getEncryptionKey(), parameters.getEncryptionConfig().getName()),
                  LogColor.Yellow, LogWeight.Bold),
              WARN, CommandExecutionStatus.RUNNING);
          logCallback.saveExecutionLog(sanitizeException.getMessage(), WARN);
          log.error("Exception occurred while deleting Terraform Plan from vault", sanitizeException);
        }
      }
      logCallback.saveExecutionLog("Done cleaning up directories.", INFO, CommandExecutionStatus.SUCCESS);
    }
  }

  @Override
  public String uploadTfPlanJson(String accountId, String delegateId, String taskId, String entityId, String planName,
      String localFilePath) throws IOException {
    final DelegateFile delegateFile = aDelegateFile()
                                          .withAccountId(accountId)
                                          .withDelegateId(delegateId)
                                          .withTaskId(taskId)
                                          .withEntityId(entityId)
                                          .withBucket(FileBucket.TERRAFORM_PLAN_JSON)
                                          .withFileName(format(TERRAFORM_PLAN_JSON_FILE_NAME, planName))
                                          .build();

    try (InputStream fileStream = new FileInputStream(localFilePath)) {
      delegateFileManagerBase.upload(delegateFile, fileStream);
    }

    return delegateFile.getFileId();
  }

  @Override
  public String uploadTfPlanHumanReadable(String accountId, String delegateId, String taskId, String entityId,
      String planName, String localFilePath) throws IOException {
    final DelegateFile delegateFile = aDelegateFile()
                                          .withAccountId(accountId)
                                          .withDelegateId(delegateId)
                                          .withTaskId(taskId)
                                          .withEntityId(entityId)
                                          .withBucket(FileBucket.TERRAFORM_HUMAN_READABLE_PLAN)
                                          .withFileName(format(PLAN_HUMAN_READABLE_TXT_FILE_NAME, planName))
                                          .build();

    try (InputStream fileStream = new FileInputStream(localFilePath)) {
      delegateFileManagerBase.upload(delegateFile, fileStream);
    }
    return delegateFile.getFileId();
  }
}
