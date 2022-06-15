/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.DelegateFile.Builder.aDelegateFile;
import static io.harness.delegate.task.terraform.TerraformCommand.APPLY;
import static io.harness.delegate.task.terraform.TerraformCommand.DESTROY;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.logging.LogLevel.WARN;
import static io.harness.provision.TerraformConstants.RESOURCE_READY_WAIT_TIME_SECONDS;
import static io.harness.provision.TerraformConstants.TERRAFORM_APPLY_PLAN_FILE_VAR_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_BACKEND_CONFIGS_FILE_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_DESTROY_PLAN_FILE_VAR_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_INTERNAL_FOLDER;
import static io.harness.provision.TerraformConstants.TERRAFORM_PLAN_FILE_OUTPUT_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_STATE_FILE_NAME;
import static io.harness.provision.TerraformConstants.TERRAFORM_VARIABLES_FILE_NAME;
import static io.harness.provision.TerraformConstants.TF_SCRIPT_DIR;
import static io.harness.provision.TerraformConstants.TF_VAR_FILES_DIR;
import static io.harness.provision.TerraformConstants.USER_DIR_KEY;
import static io.harness.provision.TerraformConstants.WORKSPACE_DIR_BASE;
import static io.harness.provision.TfVarSource.TfVarSourceType;
import static io.harness.threading.Morpheus.sleep;

import static software.wings.beans.LogColor.Yellow;
import static software.wings.beans.LogWeight.Bold;
import static software.wings.delegatetasks.validation.terraform.TerraformTaskUtils.fetchAllTfVarFilesArgument;
import static software.wings.service.impl.aws.model.AwsConstants.AWS_DEFAULT_REGION;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.cli.CliResponse;
import io.harness.cli.LogCallbackOutputStream;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.terraform.TerraformBaseHelper;
import io.harness.delegate.task.terraform.TerraformCommand;
import io.harness.delegate.task.terraform.TerraformCommandUnit;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.TerraformCommandExecutionException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.git.model.GitRepositoryType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.PlanJsonLogOutputStream;
import io.harness.secretmanagerclient.EncryptDecryptHelper;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.terraform.TerraformClient;
import io.harness.terraform.TerraformHelperUtils;
import io.harness.terraform.beans.TerraformVersion;
import io.harness.terraform.expression.TerraformPlanExpressionInterface;
import io.harness.terraform.request.TerraformExecuteStepRequest;

import software.wings.api.TerraformExecutionData;
import software.wings.api.TerraformExecutionData.TerraformExecutionDataBuilder;
import software.wings.api.terraform.TfVarGitSource;
import software.wings.beans.GitConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;
import software.wings.beans.LogWeight;
import software.wings.beans.NameValuePair;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.delegation.TerraformProvisionParameters;
import software.wings.beans.yaml.GitFetchFilesRequest;
import software.wings.delegatetasks.validation.terraform.TerraformTaskUtils;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.yaml.GitClientHelper;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.yaml.GitClient;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class TerraformProvisionTask extends AbstractDelegateRunnableTask {
  @Inject private GitClient gitClient;
  @Inject private GitClientHelper gitClientHelper;
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateLogService logService;
  @Inject private DelegateFileManager delegateFileManager;
  @Inject private EncryptDecryptHelper planEncryptDecryptHelper;
  @Inject private TerraformBaseHelper terraformBaseHelper;
  @Inject private AwsHelperService awsHelperService;
  @Inject private TerraformClient terraformClient;

  private static final String AWS_ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
  private static final String AWS_SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
  private static final String AWS_SESSION_TOKEN = "AWS_SESSION_TOKEN";

  public TerraformProvisionTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public TerraformExecutionData run(TaskParameters parameters) {
    return run((TerraformProvisionParameters) parameters);
  }

  @Override
  public TerraformExecutionData run(Object[] parameters) {
    return run((TerraformProvisionParameters) parameters[0]);
  }

  private enum WorkspaceCommand {
    SELECT("select"),
    NEW("new");
    private String command;

    WorkspaceCommand(String command) {
      this.command = command;
    }
  }

  private Pattern varList = Pattern.compile("^\\s*\\[.*?]\\s*$");

  private void saveVariable(BufferedWriter writer, String key, String value) throws IOException {
    // If the variable is wrapped with [] square brackets, we assume it is a list and we keep it as is.
    if (varList.matcher(value).matches()) {
      writer.write(format("%s = %s%n", key, value));
      return;
    }

    writer.write(format("%s = \"%s\" %n", key, value.replaceAll("\"", "\\\"")));
  }

  private TerraformExecutionData run(TerraformProvisionParameters parameters) {
    GitConfig gitConfig = parameters.getSourceRepo();
    String sourceRepoSettingId = parameters.getSourceRepoSettingId();
    LogCallback logCallback = getLogCallback(parameters);

    GitOperationContext gitOperationContext =
        GitOperationContext.builder().gitConfig(gitConfig).gitConnectorId(sourceRepoSettingId).build();

    if (isNotEmpty(gitConfig.getBranch())) {
      saveExecutionLog("Branch: " + gitConfig.getBranch(), CommandExecutionStatus.RUNNING, INFO, logCallback);
    }
    saveExecutionLog(
        "\nNormalized Path: " + parameters.getScriptPath(), CommandExecutionStatus.RUNNING, INFO, logCallback);
    gitConfig.setGitRepoType(GitRepositoryType.TERRAFORM);

    if (isNotEmpty(gitConfig.getReference())) {
      saveExecutionLog(format("%nInheriting git state at commit id: [%s]", gitConfig.getReference()),
          CommandExecutionStatus.RUNNING, INFO, logCallback);
    }
    EncryptedRecordData encryptedTfPlan = parameters.getEncryptedTfPlan();
    try {
      encryptionService.decrypt(gitConfig, parameters.getSourceRepoEncryptionDetails(), false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(gitConfig, parameters.getSourceRepoEncryptionDetails());
      gitClient.ensureRepoLocallyClonedAndUpdated(gitOperationContext);
    } catch (RuntimeException ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      log.error("Exception in processing git operation", sanitizedException);
      return TerraformExecutionData.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(TerraformTaskUtils.getGitExceptionMessageIfExists(sanitizedException))
          .build();
    }

    String baseDir = parameters.isUseActivityIdBasedTfBaseDir()
        ? terraformBaseHelper.activityIdBasedBaseDir(
            parameters.getAccountId(), String.valueOf(parameters.getEntityId().hashCode()), parameters.getActivityId())
        : terraformBaseHelper.resolveBaseDir(
            parameters.getAccountId(), String.valueOf(parameters.getEntityId().hashCode()));
    String tfVarDirectory = Paths.get(baseDir, TF_VAR_FILES_DIR).toString();
    String workingDir = Paths.get(baseDir, TF_SCRIPT_DIR).toString();

    if (null != parameters.getTfVarSource()
        && parameters.getTfVarSource().getTfVarSourceType() == TfVarSourceType.GIT) {
      fetchTfVarGitSource(parameters, tfVarDirectory, logCallback);
    }

    try {
      copyFilesToWorkingDirectory(gitClientHelper.getRepoDirectory(gitOperationContext), workingDir);
    } catch (Exception ex) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
      log.error("Exception in copying files to provisioner specific directory", sanitizedException);
      FileUtils.deleteQuietly(new File(baseDir));
      return TerraformExecutionData.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(ExceptionUtils.getMessage(sanitizedException))
          .build();
    }
    String scriptDirectory = terraformBaseHelper.resolveScriptDirectory(workingDir, parameters.getScriptPath());
    log.info("Script Directory: " + scriptDirectory);
    saveExecutionLog(
        format("Script Directory: [%s]", scriptDirectory), CommandExecutionStatus.RUNNING, INFO, logCallback);

    File tfVariablesFile = null, tfBackendConfigsFile = null;
    String tfPlanJsonFilePath = null;

    try (ActivityLogOutputStream activityLogOutputStream = new ActivityLogOutputStream(parameters, logCallback);
         LogCallbackOutputStream logCallbackOutputStream = new LogCallbackOutputStream(logCallback);
         PlanJsonLogOutputStream planJsonLogOutputStream =
             new PlanJsonLogOutputStream(parameters.isUseOptimizedTfPlanJson())) {
      ensureLocalCleanup(scriptDirectory);
      String sourceRepoReference = parameters.getCommitId() != null
          ? parameters.getCommitId()
          : getLatestCommitSHAFromLocalRepo(gitOperationContext);

      Map<String, String> awsAuthEnvVariables = null;
      if (parameters.getAwsConfig() != null && parameters.getAwsConfigEncryptionDetails() != null) {
        try {
          awsAuthEnvVariables = getAwsAuthVariables(parameters);
        } catch (Exception e) {
          throw new InvalidRequestException(ExceptionMessageSanitizer.sanitizeException(e).getMessage());
        }
      }

      final Map<String, String> envVars = getEnvironmentVariables(parameters, awsAuthEnvVariables);
      saveExecutionLog(format("Environment variables: [%s]", collectEnvVarKeys(envVars)),
          CommandExecutionStatus.RUNNING, INFO, logCallback);

      tfVariablesFile =
          Paths.get(scriptDirectory, format(TERRAFORM_VARIABLES_FILE_NAME, parameters.getEntityId().hashCode()))
              .toFile();
      tfBackendConfigsFile =
          Paths.get(scriptDirectory, format(TERRAFORM_BACKEND_CONFIGS_FILE_NAME, parameters.getEntityId().hashCode()))
              .toFile();

      downloadTfStateFile(parameters, scriptDirectory);

      StringBuilder inlineCommandBuffer = new StringBuilder();
      StringBuilder inlineUILogBuffer = new StringBuilder();
      getCommandLineVariableParams(parameters, tfVariablesFile, inlineCommandBuffer, inlineUILogBuffer);
      String varParams = inlineCommandBuffer.toString();
      String inlineVarParams = varParams;
      String uiLogs = inlineUILogBuffer.toString();

      if (isNotEmpty(parameters.getBackendConfigs()) || isNotEmpty(parameters.getEncryptedBackendConfigs())) {
        try (BufferedWriter writer =
                 new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tfBackendConfigsFile), "UTF-8"))) {
          if (isNotEmpty(parameters.getBackendConfigs())) {
            for (Entry<String, String> entry : parameters.getBackendConfigs().entrySet()) {
              saveVariable(writer, entry.getKey(), entry.getValue());
            }
          }
          if (isNotEmpty(parameters.getEncryptedBackendConfigs())) {
            for (Entry<String, EncryptedDataDetail> entry : parameters.getEncryptedBackendConfigs().entrySet()) {
              String value = String.valueOf(encryptionService.getDecryptedValue(entry.getValue(), false));
              saveVariable(writer, entry.getKey(), value);
            }
          }
        }
      }

      File tfOutputsFile =
          Paths.get(scriptDirectory, format(TERRAFORM_VARIABLES_FILE_NAME, parameters.getEntityId().hashCode()))
              .toFile();
      String targetArgs = getTargetArgs(parameters.getTargets());

      String tfVarFiles = null == parameters.getTfVarSource()
          ? StringUtils.EMPTY
          : fetchAllTfVarFilesArgument(
              System.getProperty(USER_DIR_KEY), parameters.getTfVarSource(), workingDir, tfVarDirectory);
      varParams = format("%s %s", tfVarFiles, varParams);
      uiLogs = format("%s %s", tfVarFiles, uiLogs);

      int code;
      TerraformVersion version = terraformClient.version(parameters.getTimeoutInMillis(), scriptDirectory);
      if (parameters.isUseTfClient()) {
        try {
          log.info(format("Using TFClient for Running Terraform Commands for account %s", parameters.getAccountId()));
          code = executeWithTerraformClient(parameters, tfBackendConfigsFile, tfOutputsFile, scriptDirectory,
              workingDir, tfVarDirectory, inlineVarParams, uiLogs, envVars, logCallback, planJsonLogOutputStream);
        } catch (TerraformCommandExecutionException exception) {
          log.warn(ExceptionMessageSanitizer.sanitizeException(exception).getMessage());
          code = 0;
        }
      } else {
        switch (parameters.getCommand()) {
          case APPLY: {
            String command = format("terraform init %s",
                tfBackendConfigsFile.exists() ? format("-backend-config=%s", tfBackendConfigsFile.getAbsolutePath())
                                              : "");
            String commandToLog = command;
            /**
             * echo "no" is to prevent copying of state from local to remote by suppressing the
             * copy prompt. As of tf version 0.12.3
             * there is no way to provide this as a command line argument
             */
            saveExecutionLog(commandToLog, CommandExecutionStatus.RUNNING, INFO, logCallback);
            code = executeShellCommand(
                format("echo \"no\" | %s", command), scriptDirectory, parameters, envVars, activityLogOutputStream);

            if (isNotEmpty(parameters.getWorkspace())) {
              WorkspaceCommand workspaceCommand = getWorkspaceCommand(scriptDirectory, parameters.getWorkspace(),
                  envVars, parameters.getTimeoutInMillis(), activityLogOutputStream, logCallback);
              command = format("terraform workspace %s %s", workspaceCommand.command, parameters.getWorkspace());
              commandToLog = command;
              saveExecutionLog(commandToLog, CommandExecutionStatus.RUNNING, INFO, logCallback);
              code = executeShellCommand(command, scriptDirectory, parameters, envVars, activityLogOutputStream);
            }
            if (code == 0 && !shouldSkipRefresh(parameters)) {
              command = format("terraform refresh -input=false %s %s ", targetArgs, varParams);
              commandToLog = format("terraform refresh -input=false %s %s ", targetArgs, uiLogs);
              saveExecutionLog(commandToLog, CommandExecutionStatus.RUNNING, INFO, logCallback);
              code = executeShellCommand(command, scriptDirectory, parameters, envVars, activityLogOutputStream);
            }
            // if the plan exists we should use the approved plan, instead create a plan
            if (code == 0 && parameters.getEncryptedTfPlan() == null) {
              saveExecutionLog(color("\nGenerating terraform plan \n", Yellow, Bold), CommandExecutionStatus.RUNNING,
                  INFO, logCallback);
              command = format("terraform plan -out=tfplan -input=false %s %s ", targetArgs, varParams);
              commandToLog = format("terraform plan -out=tfplan -input=false %s %s ", targetArgs, uiLogs);
              saveExecutionLog(commandToLog, CommandExecutionStatus.RUNNING, INFO, logCallback);
              code = executeShellCommand(command, scriptDirectory, parameters, envVars, logCallbackOutputStream);

              if (code == 0 && parameters.isSaveTerraformJson()) {
                code = executeTerraformShowCommand(
                    parameters, scriptDirectory, APPLY, envVars, planJsonLogOutputStream, logCallback);
              }
            } else if (code == 0 && parameters.getEncryptedTfPlan() != null) {
              // case when we are inheriting the approved  plan
              saveExecutionLog(color("\nDecrypting terraform plan before applying\n", Yellow, Bold),
                  CommandExecutionStatus.RUNNING, INFO, logCallback);
              saveTerraformPlanContentToFile(parameters, scriptDirectory);
              saveExecutionLog(color("\nUsing approved terraform plan \n", Yellow, Bold),
                  CommandExecutionStatus.RUNNING, INFO, logCallback);
            }
            if (code == 0 && !parameters.isRunPlanOnly()) {
              command = "terraform apply -input=false tfplan";
              commandToLog = command;
              saveExecutionLog(commandToLog, CommandExecutionStatus.RUNNING, INFO, logCallback);
              code = executeShellCommand(command, scriptDirectory, parameters, envVars, activityLogOutputStream);
            }
            if (code == 0 && !parameters.isRunPlanOnly()) {
              command = format("terraform output --json > %s", tfOutputsFile.toString());
              commandToLog = command;
              saveExecutionLog(commandToLog, CommandExecutionStatus.RUNNING, INFO, logCallback);
              code = executeShellCommand(command, scriptDirectory, parameters, envVars, activityLogOutputStream);
            }

            break;
          }
          case DESTROY: {
            String command = format("terraform init -input=false %s",
                tfBackendConfigsFile.exists() ? format("-backend-config=%s", tfBackendConfigsFile.getAbsolutePath())
                                              : "");
            String commandToLog = command;
            saveExecutionLog(commandToLog, CommandExecutionStatus.RUNNING, INFO, logCallback);
            code = executeShellCommand(command, scriptDirectory, parameters, envVars, activityLogOutputStream);

            if (isNotEmpty(parameters.getWorkspace())) {
              WorkspaceCommand workspaceCommand = getWorkspaceCommand(scriptDirectory, parameters.getWorkspace(),
                  envVars, parameters.getTimeoutInMillis(), activityLogOutputStream, logCallback);
              command = format("terraform workspace %s %s", workspaceCommand.command, parameters.getWorkspace());
              commandToLog = command;
              saveExecutionLog(commandToLog, CommandExecutionStatus.RUNNING, INFO, logCallback);
              code = executeShellCommand(command, scriptDirectory, parameters, envVars, activityLogOutputStream);
            }

            if (code == 0 && !shouldSkipRefresh(parameters)) {
              command = format("terraform refresh -input=false %s %s", targetArgs, varParams);
              commandToLog = format("terraform refresh -input=false %s %s", targetArgs, uiLogs);
              saveExecutionLog(commandToLog, CommandExecutionStatus.RUNNING, INFO, logCallback);
              code = executeShellCommand(command, scriptDirectory, parameters, envVars, activityLogOutputStream);
            }
            if (code == 0) {
              if (parameters.isRunPlanOnly()) {
                command =
                    format("terraform plan -destroy -out=tfdestroyplan -input=false %s %s ", targetArgs, varParams);
                commandToLog =
                    format("terraform plan -destroy -out=tfdestroyplan -input=false %s %s ", targetArgs, uiLogs);
                saveExecutionLog(commandToLog, CommandExecutionStatus.RUNNING, INFO, logCallback);
                code = executeShellCommand(command, scriptDirectory, parameters, envVars, logCallbackOutputStream);

                if (code == 0 && parameters.isSaveTerraformJson()) {
                  code = executeTerraformShowCommand(
                      parameters, scriptDirectory, DESTROY, envVars, planJsonLogOutputStream, logCallback);
                }
              } else {
                if (parameters.getEncryptedTfPlan() == null) {
                  String autoApproveArg = TerraformHelperUtils.getAutoApproveArgument(version);
                  command = format("terraform destroy %s %s %s", autoApproveArg, targetArgs, varParams);
                  commandToLog = format("terraform destroy %s %s %s", autoApproveArg, targetArgs, uiLogs);
                  saveExecutionLog(commandToLog, CommandExecutionStatus.RUNNING, INFO, logCallback);
                  code = executeShellCommand(command, scriptDirectory, parameters, envVars, activityLogOutputStream);
                } else {
                  // case when we are inheriting the approved destroy plan
                  saveTerraformPlanContentToFile(parameters, scriptDirectory);
                  saveExecutionLog(
                      "Using approved terraform destroy plan", CommandExecutionStatus.RUNNING, INFO, logCallback);

                  command = "terraform apply -input=false tfdestroyplan";
                  commandToLog = command;
                  saveExecutionLog(commandToLog, CommandExecutionStatus.RUNNING, INFO, logCallback);
                  code = executeShellCommand(command, scriptDirectory, parameters, envVars, activityLogOutputStream);
                }
              }
            }
            break;
          }
          default: {
            throw new IllegalArgumentException("Invalid Terraform Command : " + parameters.getCommand().name());
          }
        }
      }

      String tfPlanJsonFileId = null;
      if (code == 0 && parameters.isSaveTerraformJson() && parameters.isUseOptimizedTfPlanJson()
          && version.minVersion(0, 12)) {
        String planName = getPlanName(parameters);
        saveExecutionLog(format("Uploading terraform %s json representation", planName), CommandExecutionStatus.RUNNING,
            INFO, logCallback);
        // We're going to read content from json plan file and ideally no one should write anything into output
        // stream at this stage. Just in case let's flush everything from buffer and close output stream
        // We have enough guards at different layers to prevent repeat close as result of autocloseable
        planJsonLogOutputStream.flush();
        planJsonLogOutputStream.close();
        tfPlanJsonFilePath = planJsonLogOutputStream.getTfPlanJsonLocalPath();
        tfPlanJsonFileId = terraformBaseHelper.uploadTfPlanJson(parameters.getAccountId(), getDelegateId(), getTaskId(),
            parameters.getEntityId(), planName, tfPlanJsonFilePath);
        saveExecutionLog(format("Path to '%s' json representation is available via expression %s %n", planName,
                             parameters.getCommand() == APPLY ? TerraformPlanExpressionInterface.EXAMPLE_USAGE
                                                              : TerraformPlanExpressionInterface.DESTROY_EXAMPLE_USAGE),
            CommandExecutionStatus.RUNNING, INFO, logCallback);
      }

      if (code == 0 && !parameters.isRunPlanOnly()) {
        saveExecutionLog(
            format("Waiting: [%s] seconds for resources to be ready", String.valueOf(RESOURCE_READY_WAIT_TIME_SECONDS)),
            CommandExecutionStatus.RUNNING, INFO, logCallback);
        sleep(ofSeconds(RESOURCE_READY_WAIT_TIME_SECONDS));
      }

      CommandExecutionStatus commandExecutionStatus =
          code == 0 ? CommandExecutionStatus.SUCCESS : CommandExecutionStatus.FAILURE;

      saveExecutionLog("Script execution finished with status: " + commandExecutionStatus, commandExecutionStatus, INFO,
          logCallback);

      final DelegateFile delegateFile = aDelegateFile()
                                            .withAccountId(parameters.getAccountId())
                                            .withDelegateId(getDelegateId())
                                            .withTaskId(getTaskId())
                                            .withEntityId(parameters.getEntityId())
                                            .withBucket(FileBucket.TERRAFORM_STATE)
                                            .withFileName(TERRAFORM_STATE_FILE_NAME)
                                            .build();

      File tfStateFile = TerraformHelperUtils.getTerraformStateFile(scriptDirectory, parameters.getWorkspace());
      if (tfStateFile != null) {
        try (InputStream initialStream = new FileInputStream(tfStateFile)) {
          delegateFileManager.upload(delegateFile, initialStream);
        }
      } else {
        try (InputStream nullInputStream = new NullInputStream(0)) {
          delegateFileManager.upload(delegateFile, nullInputStream);
        }
      }

      List<NameValuePair> backendConfigs =
          getAllVariables(parameters.getBackendConfigs(), parameters.getEncryptedBackendConfigs());
      List<NameValuePair> environmentVars =
          getAllVariables(parameters.getEnvironmentVariables(), parameters.getEncryptedEnvironmentVariables());

      if (parameters.isExportPlanToApplyStep()) {
        byte[] terraformPlanFile = getTerraformPlanFile(scriptDirectory, parameters);
        saveExecutionLog(
            color("\nEncrypting terraform plan \n", Yellow, Bold), CommandExecutionStatus.RUNNING, INFO, logCallback);

        if (parameters.isUseOptimizedTfPlanJson()) {
          DelegateFile planDelegateFile =
              aDelegateFile()
                  .withAccountId(parameters.getAccountId())
                  .withDelegateId(getDelegateId())
                  .withTaskId(getTaskId())
                  .withEntityId(parameters.getEntityId())
                  .withBucket(FileBucket.TERRAFORM_PLAN)
                  .withFileName(format(TERRAFORM_PLAN_FILE_OUTPUT_NAME, getPlanName(parameters)))
                  .build();
          encryptedTfPlan = (EncryptedRecordData) planEncryptDecryptHelper.encryptFile(
              terraformPlanFile, parameters.getPlanName(), parameters.getSecretManagerConfig(), planDelegateFile);

        } else {
          encryptedTfPlan = (EncryptedRecordData) planEncryptDecryptHelper.encryptContent(
              terraformPlanFile, parameters.getPlanName(), parameters.getSecretManagerConfig());
        }
      }

      final TerraformExecutionDataBuilder terraformExecutionDataBuilder =
          TerraformExecutionData.builder()
              .entityId(delegateFile.getEntityId())
              .stateFileId(delegateFile.getFileId())
              .tfPlanJson(planJsonLogOutputStream.getPlanJson())
              .tfPlanJsonFiledId(tfPlanJsonFileId)
              .commandExecuted(parameters.getCommand())
              .sourceRepoReference(sourceRepoReference)
              .variables(parameters.getRawVariables())
              .backendConfigs(backendConfigs)
              .environmentVariables(environmentVars)
              .targets(parameters.getTargets())
              .tfVarFiles(parameters.getTfVarFiles())
              .tfVarSource(parameters.getTfVarSource())
              .delegateTag(parameters.getDelegateTag())
              .executionStatus(code == 0 ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED)
              .errorMessage(code == 0 ? null : "The terraform command exited with code " + code)
              .workspace(parameters.getWorkspace())
              .encryptedTfPlan(encryptedTfPlan)
              .awsConfigId(parameters.getAwsConfigId())
              .awsRoleArn(parameters.getAwsRoleArn())
              .awsRegion(parameters.getAwsRegion());

      if (parameters.getCommandUnit() != TerraformCommandUnit.Destroy
          && commandExecutionStatus == CommandExecutionStatus.SUCCESS && !parameters.isRunPlanOnly()) {
        terraformExecutionDataBuilder.outputs(new String(Files.readAllBytes(tfOutputsFile.toPath()), Charsets.UTF_8));
      }

      return terraformExecutionDataBuilder.build();

    } catch (WingsException ex) {
      return logErrorAndGetFailureResponse(
          ex, ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)), logCallback);
    } catch (IOException ex) {
      return logErrorAndGetFailureResponse(ex, "IO Failure occurred while performing Terraform Task", logCallback);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return logErrorAndGetFailureResponse(ex, "Interrupted while performing Terraform Task", logCallback);
    } catch (TimeoutException | UncheckedTimeoutException ex) {
      return logErrorAndGetFailureResponse(ex, "Timed out while performing Terraform Task", logCallback);
    } catch (Exception ex) {
      return logErrorAndGetFailureResponse(ex, "Failed to complete Terraform Task", logCallback);
    } finally {
      FileUtils.deleteQuietly(new File(workingDir));
      FileUtils.deleteQuietly(new File(baseDir));
      if (tfPlanJsonFilePath != null) {
        FileUtils.deleteQuietly(new File(tfPlanJsonFilePath));
      }

      if (parameters.getEncryptedTfPlan() != null) {
        try {
          boolean isSafelyDeleted = planEncryptDecryptHelper.deleteEncryptedRecord(
              parameters.getSecretManagerConfig(), parameters.getEncryptedTfPlan());
          if (isSafelyDeleted) {
            log.info("Terraform Plan has been safely deleted from vault");
          }
        } catch (Exception ex) {
          Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
          saveExecutionLog(color(format("Failed to delete secret: [%s] from vault: [%s], please clean it up",
                                     parameters.getEncryptedTfPlan().getEncryptionKey(),
                                     parameters.getSecretManagerConfig().getName()),
                               Yellow, Bold),
              CommandExecutionStatus.RUNNING, WARN, logCallback);
          saveExecutionLog(sanitizedException.getMessage(), CommandExecutionStatus.RUNNING, WARN, logCallback);
          log.error("Exception occurred while deleting Terraform Plan from vault", sanitizedException);
        }
      }
    }
  }

  private Map<String, String> getAwsAuthVariables(TerraformProvisionParameters parameters) {
    encryptionService.decrypt(parameters.getAwsConfig(), parameters.getAwsConfigEncryptionDetails(), false);
    ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
        parameters.getAwsConfig(), parameters.getAwsConfigEncryptionDetails());
    Map<String, String> awsAuthEnvVariables = new HashMap<>();
    if (isNotEmpty(parameters.getAwsRoleArn())) {
      String region = isNotEmpty(parameters.getAwsRegion()) ? parameters.getAwsRegion() : AWS_DEFAULT_REGION;
      AWSSecurityTokenServiceClient awsSecurityTokenServiceClient =
          awsHelperService.getAmazonAWSSecurityTokenServiceClient(parameters.getAwsConfig(), region);

      AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest();
      assumeRoleRequest.setRoleArn(parameters.getAwsRoleArn());
      assumeRoleRequest.setRoleSessionName(UUIDGenerator.generateUuid());
      AssumeRoleResult assumeRoleResult = awsSecurityTokenServiceClient.assumeRole(assumeRoleRequest);
      awsAuthEnvVariables.put(AWS_SECRET_ACCESS_KEY, assumeRoleResult.getCredentials().getSecretAccessKey());
      awsAuthEnvVariables.put(AWS_ACCESS_KEY_ID, assumeRoleResult.getCredentials().getAccessKeyId());
      awsAuthEnvVariables.put(AWS_SESSION_TOKEN, assumeRoleResult.getCredentials().getSessionToken());
    } else {
      AWSCredentialsProvider awsCredentialsProvider =
          awsHelperService.getAWSCredentialsProvider(parameters.getAwsConfig());
      AWSCredentials awsCredentials = awsCredentialsProvider.getCredentials();
      awsAuthEnvVariables.put(AWS_SECRET_ACCESS_KEY, awsCredentials.getAWSSecretKey());
      awsAuthEnvVariables.put(AWS_ACCESS_KEY_ID, awsCredentials.getAWSAccessKeyId());
      if (awsCredentials instanceof AWSSessionCredentials) {
        awsAuthEnvVariables.put(AWS_SESSION_TOKEN, ((AWSSessionCredentials) awsCredentials).getSessionToken());
      }
    }
    return awsAuthEnvVariables;
  }

  private int executeWithTerraformClient(TerraformProvisionParameters parameters, File tfBackendConfigsFile,
      File tfOutputsFile, String scriptDirectory, String workingDir, String tfVarDirectory, String varParams,
      String uiLogs, Map<String, String> envVars, LogCallback logCallback,
      PlanJsonLogOutputStream planJsonLogOutputStream)
      throws InterruptedException, IOException, TimeoutException, TerraformCommandExecutionException {
    CliResponse response;

    TerraformExecuteStepRequest terraformExecuteStepRequest =
        TerraformExecuteStepRequest.builder()
            .tfBackendConfigsFile(tfBackendConfigsFile.getAbsolutePath())
            .tfOutputsFile(tfOutputsFile.getAbsolutePath())
            .tfVarFilePaths(TerraformTaskUtils.fetchAndBuildAllTfVarFilesPaths(
                System.getProperty(USER_DIR_KEY), parameters.getTfVarSource(), workingDir, tfVarDirectory))
            .varParams(varParams)
            .uiLogs(uiLogs)
            .scriptDirectory(scriptDirectory)
            .envVars(envVars)
            .targets(parameters.getTargets())
            .workspace(parameters.getWorkspace())
            .isRunPlanOnly(parameters.isRunPlanOnly())
            .encryptedTfPlan(parameters.getEncryptedTfPlan())
            .encryptionConfig(parameters.getSecretManagerConfig())
            .isSkipRefreshBeforeApplyingPlan(parameters.isSkipRefreshBeforeApplyingPlan())
            .isSaveTerraformJson(parameters.isSaveTerraformJson())
            .useOptimizedTfPlan(parameters.isUseOptimizedTfPlanJson())
            .logCallback(logCallback)
            .planJsonLogOutputStream(planJsonLogOutputStream)
            .timeoutInMillis(parameters.getTimeoutInMillis())
            .accountId(parameters.getAccountId())
            .build();
    switch (parameters.getCommand()) {
      case APPLY: {
        if (terraformExecuteStepRequest.isRunPlanOnly()) {
          response = terraformBaseHelper.executeTerraformPlanStep(terraformExecuteStepRequest);
        } else {
          response = terraformBaseHelper.executeTerraformApplyStep(terraformExecuteStepRequest);
        }
        break;
      }
      case DESTROY: {
        response = terraformBaseHelper.executeTerraformDestroyStep(terraformExecuteStepRequest);
        break;
      }
      default: {
        throw new IllegalArgumentException(
            "Invalid Terraform Command for TF client: " + parameters.getCommand().name());
      }
    }
    return response.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS ? 0 : 1;
  }

  private void fetchTfVarGitSource(
      TerraformProvisionParameters parameters, String tfVarDirectory, LogCallback logCallback) {
    if (parameters.getTfVarSource().getTfVarSourceType() == TfVarSourceType.GIT) {
      TfVarGitSource tfVarGitSource = (TfVarGitSource) parameters.getTfVarSource();
      saveExecutionLog(
          format("Fetching TfVar files from Git repository: [%s]", tfVarGitSource.getGitConfig().getRepoUrl()),
          CommandExecutionStatus.RUNNING, INFO, logCallback);

      encryptionService.decrypt(tfVarGitSource.getGitConfig(), tfVarGitSource.getEncryptedDataDetails(), false);
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          tfVarGitSource.getGitConfig(), tfVarGitSource.getEncryptedDataDetails());
      gitClient.downloadFiles(tfVarGitSource.getGitConfig(),
          GitFetchFilesRequest.builder()
              .branch(tfVarGitSource.getGitFileConfig().getBranch())
              .commitId(tfVarGitSource.getGitFileConfig().getCommitId())
              .filePaths(tfVarGitSource.getGitFileConfig().getFilePathList())
              .useBranch(tfVarGitSource.getGitFileConfig().isUseBranch())
              .gitConnectorId(tfVarGitSource.getGitFileConfig().getConnectorId())
              .recursive(true)
              .build(),
          tfVarDirectory, false);

      saveExecutionLog(
          format("TfVar Git directory: [%s]", tfVarDirectory), CommandExecutionStatus.RUNNING, INFO, logCallback);
    }
  }

  private boolean shouldSkipRefresh(TerraformProvisionParameters parameters) {
    return parameters.getEncryptedTfPlan() != null && parameters.isSkipRefreshBeforeApplyingPlan();
  }

  private String collectEnvVarKeys(Map<String, String> envVars) {
    if (isNotEmpty(envVars)) {
      return envVars.keySet().stream().collect(Collectors.joining(", "));
    }
    return "";
  }

  private List<NameValuePair> getAllVariables(
      Map<String, String> variables, Map<String, EncryptedDataDetail> encryptedVariables) {
    List<NameValuePair> allVars = new ArrayList<>();
    if (isNotEmpty(variables)) {
      for (Entry<String, String> entry : variables.entrySet()) {
        allVars.add(new NameValuePair(entry.getKey(), entry.getValue(), ServiceVariableType.TEXT.name()));
      }
    }

    if (isNotEmpty(encryptedVariables)) {
      for (Entry<String, EncryptedDataDetail> entry : encryptedVariables.entrySet()) {
        allVars.add(new NameValuePair(
            entry.getKey(), entry.getValue().getEncryptedData().getUuid(), ServiceVariableType.ENCRYPTED_TEXT.name()));
      }
    }
    return allVars;
  }

  private int executeTerraformShowCommand(TerraformProvisionParameters parameters, String scriptDirectory,
      TerraformCommand terraformCommand, Map<String, String> envVars, PlanJsonLogOutputStream planJsonLogOutputStream,
      LogCallback logCallback) throws IOException, InterruptedException, TimeoutException {
    TerraformVersion version = terraformClient.version(parameters.getTimeoutInMillis(), scriptDirectory);
    if (!version.minVersion(0, 12)) {
      String messageFormat = "Terraform plan json export not supported in v%d.%d.%d. Minimum version is v0.12.x. "
          + "Skipping command.";
      String message = format(messageFormat, version.getMajor(), version.getMinor(), version.getPatch());
      logCallback.saveExecutionLog(color("\n" + message + "\n", Yellow, Bold), WARN, CommandExecutionStatus.SKIPPED);

      return 0;
    }

    String planName =
        terraformCommand == APPLY ? TERRAFORM_PLAN_FILE_OUTPUT_NAME : TERRAFORM_DESTROY_PLAN_FILE_OUTPUT_NAME;
    saveExecutionLog(format("%nGenerating json representation of %s %n", planName), CommandExecutionStatus.RUNNING,
        INFO, logCallback);
    String command = format("terraform show -json %s", planName);
    saveExecutionLog(command, CommandExecutionStatus.RUNNING, INFO, logCallback);
    int code = executeShellCommand(command, scriptDirectory, parameters, envVars, planJsonLogOutputStream);
    if (code == 0) {
      if (!parameters.isUseOptimizedTfPlanJson()) {
        saveExecutionLog(
            format("%nJson representation of %s is exported as a variable %s %n", planName,
                terraformCommand == APPLY ? TERRAFORM_APPLY_PLAN_FILE_VAR_NAME : TERRAFORM_DESTROY_PLAN_FILE_VAR_NAME),
            CommandExecutionStatus.RUNNING, INFO, logCallback);
      }
    }
    return code;
  }

  private ImmutableMap<String, String> getEnvironmentVariables(
      TerraformProvisionParameters parameters, Map<String, String> awsAuthEnvVariables) throws IOException {
    ImmutableMap.Builder<String, String> envVars = ImmutableMap.builder();
    if (isNotEmpty(parameters.getEnvironmentVariables())) {
      envVars.putAll(parameters.getEnvironmentVariables());
    }
    if (isNotEmpty(parameters.getEncryptedEnvironmentVariables())) {
      for (Entry<String, EncryptedDataDetail> entry : parameters.getEncryptedEnvironmentVariables().entrySet()) {
        String value = String.valueOf(encryptionService.getDecryptedValue(entry.getValue(), false));
        envVars.put(entry.getKey(), value);
      }
    }
    if (isNotEmpty(awsAuthEnvVariables)) {
      envVars.putAll(awsAuthEnvVariables);
    }
    return envVars.build();
  }

  private TerraformExecutionData logErrorAndGetFailureResponse(Exception ex, String message, LogCallback logCallback) {
    Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(ex);
    saveExecutionLog(message, CommandExecutionStatus.FAILURE, ERROR, logCallback);
    log.error("Exception in processing terraform operation", sanitizedException);
    return TerraformExecutionData.builder().executionStatus(ExecutionStatus.FAILED).errorMessage(message).build();
  }

  /*
  Copies Files from the directory common to the git connector to a directory specific to the app
  and provisioner
   */
  private void copyFilesToWorkingDirectory(String sourceDir, String destinationDir) throws IOException {
    File dest = new File(destinationDir);
    File src = new File(sourceDir);
    deleteDirectoryAndItsContentIfExists(dest.getAbsolutePath());
    FileUtils.copyDirectory(src, dest);
    FileIo.waitForDirectoryToBeAccessibleOutOfProcess(dest.getPath(), 10);
  }

  @VisibleForTesting
  public void getCommandLineVariableParams(TerraformProvisionParameters parameters, File tfVariablesFile,
      StringBuilder executeParams, StringBuilder uiLogParams) throws IOException {
    if (isEmpty(parameters.getVariables()) && isEmpty(parameters.getEncryptedVariables())) {
      FileUtils.deleteQuietly(tfVariablesFile);
      return;
    }
    String variableFormatString = " -var='%s=%s' ";
    if (isNotEmpty(parameters.getVariables())) {
      for (Entry<String, String> entry : parameters.getVariables().entrySet()) {
        executeParams.append(format(variableFormatString, entry.getKey(), entry.getValue()));
        uiLogParams.append(format(variableFormatString, entry.getKey(), entry.getValue()));
      }
    }

    if (isNotEmpty(parameters.getEncryptedVariables())) {
      for (Entry<String, EncryptedDataDetail> entry : parameters.getEncryptedVariables().entrySet()) {
        executeParams.append(format(variableFormatString, entry.getKey(),
            String.valueOf(encryptionService.getDecryptedValue(entry.getValue(), false))));
        uiLogParams.append(format(variableFormatString, entry.getKey(), format("HarnessSecret:[%s]", entry.getKey())));
      }
    }
  }

  private void downloadTfStateFile(TerraformProvisionParameters parameters, String scriptDirectory) throws IOException {
    terraformBaseHelper.downloadTfStateFile(
        parameters.getWorkspace(), parameters.getAccountId(), parameters.getCurrentStateFileId(), scriptDirectory);
  }

  private WorkspaceCommand getWorkspaceCommand(String scriptDir, String workspace, Map<String, String> envVars,
      long timeoutInMillis, LogOutputStream logOutputStream, LogCallback logCallback)
      throws InterruptedException, IOException, TimeoutException {
    List<String> workspaces = getWorkspacesList(scriptDir, envVars, timeoutInMillis, logOutputStream, logCallback);
    return workspaces.contains(workspace) ? WorkspaceCommand.SELECT : WorkspaceCommand.NEW;
  }

  public List<String> getWorkspacesList(String scriptDir, Map<String, String> envVars, long timeout,
      LogOutputStream logOutputStream, LogCallback logCallback)
      throws InterruptedException, TimeoutException, IOException {
    String command = "terraform workspace list";
    saveExecutionLog(command, CommandExecutionStatus.RUNNING, INFO, logCallback);
    ProcessExecutor processExecutor = new ProcessExecutor()
                                          .command("/bin/sh", "-c", command)
                                          .readOutput(true)
                                          .environment(envVars)
                                          .timeout(timeout, TimeUnit.MILLISECONDS)
                                          .directory(Paths.get(scriptDir).toFile())
                                          .redirectOutput(logOutputStream);

    ProcessResult processResult = processExecutor.execute();
    String output = processResult.outputUTF8();
    if (processResult.getExitValue() != 0) {
      throw new InvalidRequestException("Failed to list workspaces. " + output);
    }
    return terraformBaseHelper.parseOutput(output);
  }

  public int executeShellCommand(String command, String directory, TerraformProvisionParameters parameters,
      Map<String, String> envVars, LogOutputStream logOutputStream)
      throws RuntimeException, IOException, InterruptedException, TimeoutException {
    String joinedCommands = format("cd \"%s\" && %s", directory, command);
    ProcessExecutor processExecutor = new ProcessExecutor()
                                          .timeout(parameters.getTimeoutInMillis(), TimeUnit.MILLISECONDS)
                                          .command("/bin/sh", "-c", joinedCommands)
                                          .readOutput(true)
                                          .environment(envVars)
                                          .redirectOutput(logOutputStream);

    ProcessResult processResult = processExecutor.execute();
    return processResult.getExitValue();
  }

  private void ensureLocalCleanup(String scriptDirectory) throws IOException {
    FileUtils.deleteQuietly(Paths.get(scriptDirectory, TERRAFORM_STATE_FILE_NAME).toFile());
    try {
      deleteDirectoryAndItsContentIfExists(Paths.get(scriptDirectory, TERRAFORM_INTERNAL_FOLDER).toString());
    } catch (IOException e) {
      log.warn("Failed to delete .terraform folder");
    }
    deleteDirectoryAndItsContentIfExists(Paths.get(scriptDirectory, WORKSPACE_DIR_BASE).toString());
  }

  @VisibleForTesting
  public String getTargetArgs(List<String> targets) {
    StringBuilder targetArgs = new StringBuilder();
    if (isNotEmpty(targets)) {
      for (String target : targets) {
        targetArgs.append("-target=" + target + " ");
      }
    }
    return targetArgs.toString();
  }

  @VisibleForTesting
  public byte[] getTerraformPlanFile(String scriptDirectory, TerraformProvisionParameters parameters)
      throws IOException {
    return Files.readAllBytes(Paths.get(scriptDirectory, getPlanName(parameters)));
  }

  @VisibleForTesting
  public void saveTerraformPlanContentToFile(TerraformProvisionParameters parameters, String scriptDirectory)
      throws IOException {
    File tfPlanFile = Paths.get(scriptDirectory, getPlanName(parameters)).toFile();
    EncryptedRecordData encryptedRecordData = parameters.getEncryptedTfPlan();
    byte[] decryptedTerraformPlan = planEncryptDecryptHelper.getDecryptedContent(
        parameters.getSecretManagerConfig(), encryptedRecordData, parameters.getAccountId());
    FileUtils.copyInputStreamToFile(new ByteArrayInputStream(decryptedTerraformPlan), tfPlanFile);
  }

  @NotNull
  private String getPlanName(TerraformProvisionParameters parameters) {
    return terraformBaseHelper.getPlanName(parameters.getCommand());
  }

  public String getLatestCommitSHAFromLocalRepo(GitOperationContext gitOperationContext) {
    return terraformBaseHelper.getLatestCommitSHA(new File(gitClientHelper.getRepoDirectory(gitOperationContext)));
  }

  private void saveExecutionLog(
      String line, CommandExecutionStatus commandExecutionStatus, LogLevel logLevel, LogCallback logCallback) {
    logCallback.saveExecutionLog(line, logLevel, commandExecutionStatus);
  }

  private LogCallback getLogCallback(TerraformProvisionParameters parameters) {
    return new ExecutionLogCallback(logService, parameters.getAccountId(), parameters.getAppId(),
        parameters.getActivityId(), parameters.getCommandUnit().name());
  }

  private String color(String line, LogColor color, LogWeight logWeight) {
    return LogHelper.doneColoring(LogHelper.color(line, color, logWeight));
  }

  @AllArgsConstructor
  @EqualsAndHashCode(callSuper = false)
  private class ActivityLogOutputStream extends LogOutputStream {
    private TerraformProvisionParameters parameters;
    private LogCallback logCallback;

    @Override
    protected void processLine(String line) {
      saveExecutionLog(line, CommandExecutionStatus.RUNNING, INFO, logCallback);
    }
  }
}
