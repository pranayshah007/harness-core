/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.DelegateFile.Builder.aDelegateFile;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.provision.TerraformConstants.RESOURCE_READY_WAIT_TIME_SECONDS;
import static io.harness.provision.TerraformConstants.TERRAFORM_BACKEND_CONFIGS_FILE_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_PLAN_FILE_OUTPUT_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_STATE_FILE_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_VARIABLES_FILE_NAME;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_DIR;
import static io.harness.provision.TerraformConstants.USER_DIR_KEY;
import static io.harness.provision.TerragruntConstants.DESTROY_PLAN;
import static io.harness.provision.TerragruntConstants.FETCH_CONFIG_FILES;
import static io.harness.provision.TerragruntConstants.FORCE_FLAG;
import static io.harness.provision.TerragruntConstants.INIT;
import static io.harness.provision.TerragruntConstants.PLAN;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_INTERNAL_CACHE_FOLDER;
import static io.harness.provision.TerragruntConstants.TERRAGRUNT_LOCK_FILE_NAME;
import static io.harness.provision.TerragruntConstants.TG_BASE_DIR;
import static io.harness.provision.TerragruntConstants.TG_SCRIPT_DIR;
import static io.harness.provision.TerragruntConstants.WRAP_UP;
import static io.harness.provision.TfVarSource.TfVarSourceType;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogHelper.color;
import static software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommandUnit.Destroy;
import static software.wings.delegatetasks.TerragruntProvisionTaskHelper.copyFilesToWorkingDirectory;
import static software.wings.delegatetasks.TerragruntProvisionTaskHelper.getExecutionLogCallback;
import static software.wings.delegatetasks.TerragruntProvisionTaskHelper.getTfBinaryPath;
import static software.wings.delegatetasks.validation.terraform.TerraformTaskUtils.fetchAllTfVarFilesArgument;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultString;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SecretManagerConfig;
import io.harness.cli.CliResponse;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.terraform.handlers.HarnessSMEncryptionDecryptionHandler;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.terragrunt.ActivityLogOutputStream;
import io.harness.terragrunt.ErrorLogOutputStream;
import io.harness.terragrunt.PlanJsonLogOutputStream;
import io.harness.terragrunt.PlanLogOutputStream;
import io.harness.terragrunt.TerragruntCliCommandRequestParams;
import io.harness.terragrunt.TerragruntCliCommandRequestParams.TerragruntCliCommandRequestParamsBuilder;
import io.harness.terragrunt.TerragruntClient;
import io.harness.terragrunt.TerragruntDelegateTaskOutput;
import io.harness.terragrunt.WorkspaceCommand;

import software.wings.api.terragrunt.TerragruntExecutionData;
import software.wings.api.terragrunt.TerragruntExecutionData.TerragruntExecutionDataBuilder;
import software.wings.beans.GitConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;
import software.wings.beans.NameValuePair;
import software.wings.beans.delegation.TerragruntProvisionParameters;
import software.wings.beans.delegation.TerragruntProvisionParameters.TerragruntCommand;
import software.wings.delegatetasks.validation.terraform.TerraformTaskUtils;
import software.wings.service.impl.yaml.GitClientHelper;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.yaml.GitClient;

import com.google.common.base.Charsets;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class TerragruntProvisionTask extends AbstractDelegateRunnableTask {
  @Inject private GitClient gitClient;
  @Inject private GitClientHelper gitClientHelper;
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateFileManager delegateFileManager;
  @Inject private EncryptDecryptHelper encryptDecryptHelper;
  @Inject private TerragruntProvisionTaskHelper terragruntProvisionTaskHelper;
  @Inject private TerragruntClient terragruntClient;
  @Inject private DelegateLogService delegateLogService;
  @Inject private TerragruntRunAllTaskHandler terragruntRunAllTaskHandler;
  @Inject private TerragruntApplyDestroyTaskHandler terragruntApplyDestroyTaskHandler;
  @Inject private HarnessSMEncryptionDecryptionHandler harnessSMEncryptionDecryptionHandler;

  public TerragruntProvisionTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public TerragruntExecutionData run(TaskParameters parameters) {
    return run((TerragruntProvisionParameters) parameters);
  }

  @Override
  public TerragruntExecutionData run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  private TerragruntExecutionData run(TerragruntProvisionParameters parameters) {
    String scriptDirectory = EMPTY;
    String workingDir = EMPTY;
    String tfVarDirectory = EMPTY;
    String baseDir = EMPTY;
    GitOperationContext gitOperationContext = null;
    EncryptedRecordData encryptedTfPlan = parameters.getEncryptedTfPlan();
    GitConfig gitConfig = parameters.getSourceRepo();
    String sourceRepoSettingId = parameters.getSourceRepoSettingId();

    Optional<LogSanitizer> logSanitizer =
        getLogSannitizerForEncryptedVars(parameters.getActivityId(), parameters.getEncryptedVariables(),
            parameters.getEncryptedBackendConfigs(), parameters.getEncryptedEnvironmentVariables());
    logSanitizer.ifPresent(delegateLogService::registerLogSanitizer);

    LogCallback fetchConfigFilesLogCallback = getExecutionLogCallback(delegateLogService, parameters.getAccountId(),
        parameters.getAppId(), parameters.getActivityId(), FETCH_CONFIG_FILES);
    try {
      gitOperationContext =
          GitOperationContext.builder().gitConfig(gitConfig).gitConnectorId(sourceRepoSettingId).build();

      terragruntProvisionTaskHelper.setGitRepoTypeAndSaveExecutionLog(
          parameters, gitConfig, fetchConfigFilesLogCallback);

      try {
        encryptionService.decrypt(gitConfig, parameters.getSourceRepoEncryptionDetails(), false);
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(gitConfig, parameters.getSourceRepoEncryptionDetails());
        gitClient.ensureRepoLocallyClonedAndUpdated(gitOperationContext);
      } catch (RuntimeException ex) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
        log.error("Exception in processing git operation", sanitizedException);
        return TerragruntExecutionData.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage(TerraformTaskUtils.getGitExceptionMessageIfExists(sanitizedException))
            .build();
      }

      baseDir = resolveBaseDir(parameters.getAccountId(), parameters.getEntityId());
      tfVarDirectory = Paths.get(baseDir, TF_VAR_FILES_DIR).toString();
      workingDir = Paths.get(baseDir, TG_SCRIPT_DIR).toString();

      if (null != parameters.getTfVarSource()
          && parameters.getTfVarSource().getTfVarSourceType() == TfVarSourceType.GIT) {
        terragruntProvisionTaskHelper.fetchTfVarGitSource(parameters, tfVarDirectory, fetchConfigFilesLogCallback);
      }

      try {
        copyFilesToWorkingDirectory(gitClientHelper.getRepoDirectory(gitOperationContext), workingDir);
      } catch (Exception ex) {
        Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
        log.error("Exception in copying files to provisioner specific directory", sanitizedException);
        FileUtils.deleteQuietly(new File(baseDir));
        return TerragruntExecutionData.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage(ExceptionUtils.getMessage(sanitizedException))
            .build();
      }

      scriptDirectory = resolveScriptDirectory(workingDir, parameters.getScriptPath(), parameters.getPathToModule());
      log.info("Script Directory: " + scriptDirectory);
      fetchConfigFilesLogCallback.saveExecutionLog(
          format("Script Directory: [%s]", scriptDirectory), INFO, CommandExecutionStatus.RUNNING);
      fetchConfigFilesLogCallback.saveExecutionLog("Config files have been fetched successfully", INFO, SUCCESS);

    } catch (Exception ex) {
      String message = format("Failed to fetch config files successfully - [%s]",
          ExceptionMessageSanitizer.sanitizeException(ex).getMessage());
      fetchConfigFilesLogCallback.saveExecutionLog(message, ERROR, FAILURE);
      throw ex;
    }

    File tfVariablesFile = null, tfBackendConfigsFile = null;

    LogCallback wrapUpLogCallBack = getExecutionLogCallback(
        delegateLogService, parameters.getAccountId(), parameters.getAppId(), parameters.getActivityId(), WRAP_UP);
    LogCallback initLogCallback = getExecutionLogCallback(
        delegateLogService, parameters.getAccountId(), parameters.getAppId(), parameters.getActivityId(), INIT);

    try (ActivityLogOutputStream activityLogOutputStream = new ActivityLogOutputStream(initLogCallback);
         PlanLogOutputStream planLogOutputStream = new PlanLogOutputStream(
             getExecutionLogCallback(delegateLogService, parameters.getAccountId(), parameters.getAppId(),
                 parameters.getActivityId(), parameters.getCommandUnit() == Destroy ? DESTROY_PLAN : PLAN),
             new ArrayList<>());
         PlanJsonLogOutputStream planJsonLogOutputStream = new PlanJsonLogOutputStream();
         ErrorLogOutputStream errorLogOutputStream = new ErrorLogOutputStream(initLogCallback)) {
      ensureLocalCleanup(scriptDirectory);
      String planName = getPlanName(parameters.getCommand());
      String sourceRepoReference = parameters.getCommitId() != null
          ? parameters.getCommitId()
          : terragruntProvisionTaskHelper.getLatestCommitSHAFromLocalRepo(gitOperationContext);
      final Map<String, String> envVars = terragruntProvisionTaskHelper.getEnvironmentVariables(parameters);

      initLogCallback.saveExecutionLog(
          format("Environment variables: [%s]", terragruntProvisionTaskHelper.collectEnvVarKeys(envVars)), INFO,
          CommandExecutionStatus.RUNNING);

      tfVariablesFile =
          Paths.get(scriptDirectory, format(TERRAFORM_VARIABLES_FILE_NAME, parameters.getEntityId())).toFile();
      tfBackendConfigsFile =
          Paths.get(scriptDirectory, format(TERRAFORM_BACKEND_CONFIGS_FILE_NAME, parameters.getEntityId())).toFile();

      StringBuilder inlineCommandBuffer = new StringBuilder();
      StringBuilder inlineUILogBuffer = new StringBuilder();
      terragruntProvisionTaskHelper.getCommandLineVariableParams(
          parameters, tfVariablesFile, inlineCommandBuffer, inlineUILogBuffer);
      String varParams = inlineCommandBuffer.toString();
      String uiLogs = inlineUILogBuffer.toString();

      terragruntProvisionTaskHelper.saveBacknedConfigToFile(
          tfBackendConfigsFile, parameters.getBackendConfigs(), parameters.getEncryptedBackendConfigs());

      File tfOutputsFile =
          Paths.get(scriptDirectory, format(TERRAFORM_VARIABLES_FILE_NAME, parameters.getEntityId())).toFile();
      String targetArgs = terragruntProvisionTaskHelper.getTargetArgs(parameters.getTargets());

      String tfVarFiles = null == parameters.getTfVarSource()
          ? StringUtils.EMPTY
          : fetchAllTfVarFilesArgument(
              System.getProperty(USER_DIR_KEY), parameters.getTfVarSource(), workingDir, tfVarDirectory);
      varParams = format("%s %s", tfVarFiles, varParams);
      uiLogs = format("%s %s", tfVarFiles, uiLogs);

      TerragruntCliCommandRequestParamsBuilder cliCommandRequestBuilder =
          TerragruntCliCommandRequestParams.builder()
              .commandUnitName(parameters.getCommandUnit().name())
              .backendConfigFilePath(tfBackendConfigsFile.getAbsolutePath())
              .directory(scriptDirectory)
              .envVars(envVars)
              .timeoutInMillis(parameters.getTimeoutInMillis())
              .targetArgs(targetArgs)
              .uiLogs(uiLogs)
              .varParams(varParams)
              .activityLogOutputStream(activityLogOutputStream)
              .planLogOutputStream(planLogOutputStream)
              .planJsonLogOutputStream(planJsonLogOutputStream)
              .tfOutputsFile(tfOutputsFile)
              .errorLogOutputStream(errorLogOutputStream)
              .useAutoApproveFlag(parameters.isUseAutoApproveFlag());

      CliResponse terragruntCliResponse;
      String terraformConfigFileDirectoryPath = EMPTY;
      TerragruntDelegateTaskOutput terragruntDelegateTaskOutput = null;
      CliResponse tgInfoResponse;

      if (parameters.isRunAll()) {
        terragruntDelegateTaskOutput = terragruntRunAllTaskHandler.executeRunAllTask(
            parameters, cliCommandRequestBuilder.build(), delegateLogService, parameters.getCommand());
        terragruntCliResponse = terragruntDelegateTaskOutput.getCliResponse();
      } else {
        try {
          terragruntCliResponse = terragruntClient.version(cliCommandRequestBuilder.build(), initLogCallback);
          if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS) {
            terragruntCliResponse = terragruntClient.init(cliCommandRequestBuilder.build(), initLogCallback);
          }

          tgInfoResponse = terragruntClient.terragruntInfo(cliCommandRequestBuilder.build(), initLogCallback);
          terraformConfigFileDirectoryPath =
              TerragruntProvisionTaskHelper.getTerraformConfigFileDirectoryPath(tgInfoResponse);

          terragruntProvisionTaskHelper.downloadTfStateFile(parameters, terraformConfigFileDirectoryPath);

          if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS && isNotEmpty(parameters.getWorkspace())) {
            WorkspaceCommand workspaceCommand =
                getWorkspaceCommand(scriptDirectory, parameters.getWorkspace(), parameters.getTimeoutInMillis());
            terragruntCliResponse = terragruntClient.workspace(
                cliCommandRequestBuilder.build(), workspaceCommand.command, parameters.getWorkspace(), initLogCallback);
          }
          if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS
              && !terragruntProvisionTaskHelper.shouldSkipRefresh(parameters)) {
            terragruntCliResponse = terragruntClient.refresh(
                cliCommandRequestBuilder.build(), targetArgs, varParams, uiLogs, initLogCallback);
          }
          initLogCallback.saveExecutionLog(
              "Finished terragrunt init task", INFO, terragruntCliResponse.getCommandExecutionStatus());
        } catch (Exception ex) {
          String message = format("Failed to perform terragrunt init tasks - [%s]",
              ExceptionMessageSanitizer.sanitizeException(ex).getMessage());
          initLogCallback.saveExecutionLog(message, ERROR, FAILURE);
          throw ex;
        }

        if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS) {
          switch (parameters.getCommand()) {
            case APPLY: {
              terragruntDelegateTaskOutput = terragruntApplyDestroyTaskHandler.executeApplyTask(parameters,
                  cliCommandRequestBuilder.build(), delegateLogService, planName, terraformConfigFileDirectoryPath);
              terragruntCliResponse = terragruntDelegateTaskOutput.getCliResponse();
              break;
            }
            case DESTROY: {
              String tfAutoApproveArgument = FORCE_FLAG;
              if (parameters.isUseAutoApproveFlag()) {
                String tfBinaryPath = getTfBinaryPath(tgInfoResponse);
                tfAutoApproveArgument = terragruntProvisionTaskHelper.getTfAutoApproveArgument(
                    cliCommandRequestBuilder.build(), tfBinaryPath);
              }
              TerragruntCliCommandRequestParams cliCommandRequestParams =
                  cliCommandRequestBuilder.autoApproveArgument(tfAutoApproveArgument).build();

              terragruntDelegateTaskOutput = terragruntApplyDestroyTaskHandler.executeDestroyTask(
                  parameters, cliCommandRequestParams, delegateLogService, planName, terraformConfigFileDirectoryPath);
              terragruntCliResponse = terragruntDelegateTaskOutput.getCliResponse();
              break;
            }
            default: {
              throw new IllegalArgumentException("Invalid Terragrunt Command : " + parameters.getCommand().name());
            }
          }
        }
      }

      if (terragruntCliResponse.getCommandExecutionStatus() == SUCCESS && !parameters.isRunPlanOnly()) {
        wrapUpLogCallBack.saveExecutionLog(
            format("Waiting: [%s] seconds for resources to be ready", String.valueOf(RESOURCE_READY_WAIT_TIME_SECONDS)),
            INFO, CommandExecutionStatus.RUNNING);
        sleep(ofSeconds(RESOURCE_READY_WAIT_TIME_SECONDS));
      }

      CommandExecutionStatus commandExecutionStatus = terragruntCliResponse.getCommandExecutionStatus() == SUCCESS
          ? CommandExecutionStatus.SUCCESS
          : CommandExecutionStatus.FAILURE;

      final DelegateFile delegateFile = aDelegateFile()
                                            .withAccountId(parameters.getAccountId())
                                            .withDelegateId(getDelegateId())
                                            .withTaskId(getTaskId())
                                            .withEntityId(parameters.getEntityId())
                                            .withBucket(FileBucket.TERRAFORM_STATE)
                                            .withFileName(TERRAFORM_STATE_FILE_NAME)
                                            .build();
      if (!parameters.isRunAll()) {
        File tfStateFile = terragruntProvisionTaskHelper.getTerraformStateFile(
            terraformConfigFileDirectoryPath, parameters.getWorkspace());
        if (tfStateFile != null) {
          try (InputStream initialStream = new FileInputStream(tfStateFile)) {
            delegateFileManager.upload(delegateFile, initialStream);
          }
        } else {
          try (InputStream nullInputStream = new NullInputStream(0)) {
            delegateFileManager.upload(delegateFile, nullInputStream);
          }
        }
      }

      List<NameValuePair> backendConfigs = terragruntProvisionTaskHelper.getAllVariables(
          parameters.getBackendConfigs(), parameters.getEncryptedBackendConfigs());
      List<NameValuePair> environmentVars = terragruntProvisionTaskHelper.getAllVariables(
          parameters.getEnvironmentVariables(), parameters.getEncryptedEnvironmentVariables());

      if (parameters.isExportPlanToApplyStep() && !parameters.isRunAll()) {
        if (Files.exists(Paths.get(terraformConfigFileDirectoryPath, planName))) {
          byte[] terraformPlanFile =
              terragruntProvisionTaskHelper.getTerraformPlanFile(terraformConfigFileDirectoryPath, planName);
          wrapUpLogCallBack.saveExecutionLog(color("\nEncrypting terraform plan \n", LogColor.Yellow, LogWeight.Bold),
              INFO, CommandExecutionStatus.RUNNING);
          SecretManagerConfig secretManagerConfig = parameters.getSecretManagerConfig();

          if (parameters.isEncryptDecryptPlanForHarnessSMOnManager()) {
            encryptedTfPlan = (EncryptedRecordData) harnessSMEncryptionDecryptionHandler.encryptContent(
                terraformPlanFile, parameters.getSecretManagerConfig());

          } else {
            encryptedTfPlan = (EncryptedRecordData) encryptDecryptHelper.encryptContent(
                terraformPlanFile, parameters.getPlanName(), parameters.getSecretManagerConfig());
          }

        } else {
          wrapUpLogCallBack.saveExecutionLog(color("\nTerraform plan not found\n", LogColor.Yellow, LogWeight.Bold),
              WARN, CommandExecutionStatus.RUNNING);
        }
      }

      final TerragruntExecutionDataBuilder terragruntExecutionDataBuilder =
          TerragruntExecutionData.builder()
              .entityId(delegateFile.getEntityId())
              .stateFileId(delegateFile.getFileId())
              .tfPlanJson(terragruntDelegateTaskOutput != null
                      ? terragruntDelegateTaskOutput.getPlanJsonLogOutputStream().getPlanJson()
                      : planJsonLogOutputStream.getPlanJson())
              .commandExecuted(parameters.getCommand())
              .sourceRepoReference(sourceRepoReference)
              .variables(parameters.getRawVariables())
              .backendConfigs(backendConfigs)
              .environmentVariables(environmentVars)
              .targets(parameters.getTargets())
              .tfVarFiles(parameters.getTfVarFiles())
              .tfVarSource(parameters.getTfVarSource())
              .delegateTag(parameters.getDelegateTag())
              .executionStatus(terragruntCliResponse.getCommandExecutionStatus() == SUCCESS ? ExecutionStatus.SUCCESS
                                                                                            : ExecutionStatus.FAILED)
              .errorMessage(terragruntCliResponse.getCommandExecutionStatus() == SUCCESS
                      ? null
                      : "The terragrunt command exited with status "
                          + terragruntCliResponse.getCommandExecutionStatus())
              .workspace(parameters.getWorkspace())
              .encryptedTfPlan(encryptedTfPlan)
              .branch(gitConfig.getBranch())
              .pathToModule(parameters.getPathToModule())
              .runAll(parameters.isRunAll());
      if (parameters.getCommandUnit() != Destroy && commandExecutionStatus == CommandExecutionStatus.SUCCESS
          && !parameters.isRunPlanOnly()) {
        terragruntExecutionDataBuilder.outputs(new String(Files.readAllBytes(tfOutputsFile.toPath()), Charsets.UTF_8));
      }

      wrapUpLogCallBack.saveExecutionLog(
          "Terragrunt execution finished with status: " + commandExecutionStatus, INFO, commandExecutionStatus);

      return terragruntExecutionDataBuilder.build();

    } catch (WingsException ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      return logErrorAndGetFailureResponse(
          wrapUpLogCallBack, sanitizedException, ExceptionUtils.getMessage(sanitizedException));
    } catch (IOException ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      return logErrorAndGetFailureResponse(wrapUpLogCallBack, sanitizedException,
          format("IO Failure occurred while performing Terragrunt Task: %s", sanitizedException.getMessage()));
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return logErrorAndGetFailureResponse(wrapUpLogCallBack, ExceptionMessageSanitizer.sanitizeException(ex),
          "Interrupted while performing Terragrunt Task");
    } catch (TimeoutException | UncheckedTimeoutException ex) {
      return logErrorAndGetFailureResponse(wrapUpLogCallBack, ExceptionMessageSanitizer.sanitizeException(ex),
          "Timed out while performing Terragrunt Task");
    } catch (Exception ex) {
      return logErrorAndGetFailureResponse(
          wrapUpLogCallBack, ExceptionMessageSanitizer.sanitizeException(ex), "Failed to complete Terragrunt Task");
    } finally {
      FileUtils.deleteQuietly(new File(workingDir));
      FileUtils.deleteQuietly(new File(baseDir));
      logSanitizer.ifPresent(delegateLogService::unregisterLogSanitizer);
      if (parameters.getEncryptedTfPlan() != null) {
        try {
          boolean isSafelyDeleted = encryptDecryptHelper.deleteEncryptedRecord(
              parameters.getSecretManagerConfig(), parameters.getEncryptedTfPlan());
          if (isSafelyDeleted) {
            log.info("Terraform Plan has been safely deleted from vault");
          }
        } catch (Exception ex) {
          Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
          wrapUpLogCallBack.saveExecutionLog(
              color(format("Failed to delete secret: [%s] from vault: [%s], please clean it up",
                        parameters.getEncryptedTfPlan().getEncryptionKey(),
                        parameters.getSecretManagerConfig().getName()),
                  LogColor.Yellow, LogWeight.Bold),
              WARN, CommandExecutionStatus.RUNNING);
          wrapUpLogCallBack.saveExecutionLog(sanitizedException.getMessage(), WARN, CommandExecutionStatus.RUNNING);
          log.error("Exception occurred while deleting Terraform Plan from vault", sanitizedException);
        }
      }
    }
  }

  @NonNull
  private String resolveBaseDir(String accountId, String entityId) {
    return TG_BASE_DIR.replace("${ACCOUNT_ID}", accountId).replace("${ENTITY_ID}", entityId);
  }

  private String resolveScriptDirectory(String workingDir, String scriptPath, String pathToModule) {
    return Paths
        .get(Paths.get(System.getProperty(USER_DIR_KEY)).toString(), workingDir, defaultString(scriptPath),
            defaultString(pathToModule))
        .toString();
  }

  private void ensureLocalCleanup(String scriptDirectory) throws IOException {
    FileUtils.deleteQuietly(Paths.get(scriptDirectory, TERRAGRUNT_LOCK_FILE_NAME).toFile());
    try {
      deleteDirectoryAndItsContentIfExists(Paths.get(scriptDirectory, TERRAGRUNT_INTERNAL_CACHE_FOLDER).toString());
    } catch (IOException e) {
      log.warn("Failed to delete .terragrunt-cache folder");
    }
  }

  private WorkspaceCommand getWorkspaceCommand(String scriptDir, String workspace, long timeoutInMillis)
      throws InterruptedException, IOException, TimeoutException {
    List<String> workspaces = getWorkspacesList(scriptDir, timeoutInMillis);
    return workspaces.contains(workspace) ? WorkspaceCommand.SELECT : WorkspaceCommand.NEW;
  }

  public List<String> getWorkspacesList(String scriptDir, long timeout)
      throws InterruptedException, TimeoutException, IOException {
    CliResponse terragruntCliResponse = terragruntClient.workspaceList(scriptDir, timeout);

    if (terragruntCliResponse.getCommandExecutionStatus() != SUCCESS) {
      throw new InvalidRequestException("Failed to list workspaces. " + terragruntCliResponse.getOutput());
    }
    return parseOutput(terragruntCliResponse.getOutput());
  }

  private List<String> parseOutput(String workspaceOutput) {
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

  @NotNull
  private String getPlanName(TerragruntCommand command) {
    switch (command) {
      case APPLY:
        return TERRAFORM_PLAN_FILE_OUTPUT_NAME;
      case DESTROY:
        return TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
      default:
        throw new IllegalArgumentException("Invalid Terraform Command : " + command.name());
    }
  }

  private TerragruntExecutionData logErrorAndGetFailureResponse(LogCallback logCallback, Exception ex, String message) {
    logCallback.saveExecutionLog(message, ERROR, CommandExecutionStatus.FAILURE);
    log.error("Exception in processing terragrunt operation", ExceptionMessageSanitizer.sanitizeException(ex));
    return TerragruntExecutionData.builder().executionStatus(ExecutionStatus.FAILED).errorMessage(message).build();
  }

  private Optional<LogSanitizer> getLogSanitizer(String activityId, List<EncryptedDataDetail> encryptedDataDetails) {
    Set<String> secrets = new HashSet<>();
    if (isNotEmpty(encryptedDataDetails)) {
      for (EncryptedDataDetail encryptedDataDetail : encryptedDataDetails) {
        secrets.add(String.valueOf(encryptionService.getDecryptedValue(encryptedDataDetail, false)));
      }
    }
    return isNotEmpty(secrets) ? Optional.of(new ActivityBasedLogSanitizer(activityId, secrets)) : Optional.empty();
  }

  public Optional<LogSanitizer> getLogSannitizerForEncryptedVars(String activityId,
      Map<String, EncryptedDataDetail> encryptedVariables, Map<String, EncryptedDataDetail> encryptedBackendConfigs,
      Map<String, EncryptedDataDetail> encryptedEnvironmentVariables) {
    List<EncryptedDataDetail> variblesToBeSanitized = new ArrayList<>();
    if (isNotEmpty(encryptedVariables)) {
      variblesToBeSanitized.addAll(encryptedVariables.values());
    }
    if (isNotEmpty(encryptedBackendConfigs)) {
      variblesToBeSanitized.addAll(encryptedBackendConfigs.values());
    }
    if (isNotEmpty(encryptedEnvironmentVariables)) {
      variblesToBeSanitized.addAll(encryptedEnvironmentVariables.values());
    }
    return getLogSanitizer(activityId, variblesToBeSanitized);
  }
}
