/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.FileBucket.TERRAFORM_STATE;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.provision.TerraformConstants.TERRAFORM_STATE_FILE_NAME;
import static io.harness.provision.TerraformConstants.WORKSPACE_STATE_FILE_PATH_FORMAT;
import static io.harness.provision.TerragruntConstants.FORCE_FLAG;
import static io.harness.provision.TerragruntConstants.TF_DEFAULT_BINARY_PATH;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.cli.CliResponse;
import io.harness.exception.FileReadException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.filesystem.FileIo;
import io.harness.git.model.GitRepositoryType;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.provision.TfVarSource.TfVarSourceType;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;
import io.harness.terraform.TerraformClient;
import io.harness.terraform.TerraformHelperUtils;
import io.harness.terraform.beans.TerraformVersion;
import io.harness.terragrunt.TerragruntCliCommandRequestParams;

import software.wings.api.terraform.TfVarGitSource;
import software.wings.beans.GitConfig;
import software.wings.beans.GitOperationContext;
import software.wings.beans.NameValuePair;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.beans.delegation.TerragruntProvisionParameters;
import software.wings.beans.yaml.GitFetchFilesRequest;
import software.wings.service.impl.yaml.GitClientHelper;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.yaml.GitClient;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

@OwnedBy(CDP)
@Singleton
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class TerragruntProvisionTaskHelper {
  @Inject private GitClient gitClient;
  @Inject private GitClientHelper gitClientHelper;
  @Inject private EncryptionService encryptionService;
  @Inject private DelegateFileManager delegateFileManager;
  @Inject private TerraformClient terraformClient;

  static String getTerraformConfigFileDirectoryPath(CliResponse tgInfoResponse)
      throws InterruptedException, IOException, TimeoutException {
    String tgInfoOutput = tgInfoResponse.getOutput();
    return (String) JsonUtils.jsonPath(tgInfoOutput, "WorkingDir");
  }

  public static String getTfBinaryPath(CliResponse tgInfoResponse) {
    if (tgInfoResponse != null && tgInfoResponse.getOutput() != null) {
      String tgInfoOutput = tgInfoResponse.getOutput();
      return (String) JsonUtils.jsonPath(tgInfoOutput, "TerraformBinary");
    }
    log.warn("Error in finding terraform binary path, using default path");
    return TF_DEFAULT_BINARY_PATH;
  }

  // Todo: refactor to use same methods in Terraform
  public void setGitRepoTypeAndSaveExecutionLog(
      TerragruntProvisionParameters parameters, GitConfig gitConfig, LogCallback logCallback) {
    if (isNotEmpty(gitConfig.getBranch())) {
      logCallback.saveExecutionLog("Branch: " + gitConfig.getBranch(), INFO, CommandExecutionStatus.RUNNING);
    }
    logCallback.saveExecutionLog(
        "\nNormalized Path: " + parameters.getScriptPath(), INFO, CommandExecutionStatus.RUNNING);
    gitConfig.setGitRepoType(GitRepositoryType.TERRAGRUNT);

    if (isNotEmpty(gitConfig.getReference())) {
      logCallback.saveExecutionLog(format("%nInheriting git state at commit id: [%s]", gitConfig.getReference()), INFO,
          CommandExecutionStatus.RUNNING);
    }
  }

  private Pattern varList = Pattern.compile("^\\s*\\[.*?]\\s*$");

  public void saveVariable(BufferedWriter writer, String key, String value) throws IOException {
    // If the variable is wrapped with [] square brackets, we assume it is a list and we keep it as is.
    if (varList.matcher(value).matches()) {
      writer.write(format("%s = %s%n", key, value));
      return;
    }

    writer.write(format("%s = \"%s\" %n", key, value.replaceAll("\"", "\\\"")));
  }

  public void getCommandLineVariableParams(TerragruntProvisionParameters parameters, File tfVariablesFile,
      StringBuilder executeParams, StringBuilder uiLogParams) throws IOException {
    if (isEmpty(parameters.getVariables()) && isEmpty(parameters.getEncryptedVariables())) {
      FileUtils.deleteQuietly(tfVariablesFile);
      return;
    }
    String variableFormatString = " -var='%s=%s' ";
    if (isNotEmpty(parameters.getVariables())) {
      for (Map.Entry<String, String> entry : parameters.getVariables().entrySet()) {
        executeParams.append(format(variableFormatString, entry.getKey(), entry.getValue()));
        uiLogParams.append(format(variableFormatString, entry.getKey(), entry.getValue()));
      }
    }

    if (isNotEmpty(parameters.getEncryptedVariables())) {
      for (Map.Entry<String, EncryptedDataDetail> entry : parameters.getEncryptedVariables().entrySet()) {
        executeParams.append(format(variableFormatString, entry.getKey(),
            String.valueOf(encryptionService.getDecryptedValue(entry.getValue(), false))));
        uiLogParams.append(format(variableFormatString, entry.getKey(), format("HarnessSecret:[%s]", entry.getKey())));
      }
    }
  }

  public void fetchTfVarGitSource(
      TerragruntProvisionParameters parameters, String tfVarDirectory, LogCallback logCallback) {
    if (parameters.getTfVarSource().getTfVarSourceType() == TfVarSourceType.GIT) {
      TfVarGitSource tfVarGitSource = (TfVarGitSource) parameters.getTfVarSource();
      logCallback.saveExecutionLog(
          format("Fetching TfVar files from Git repository: [%s]", tfVarGitSource.getGitConfig().getRepoUrl()), INFO,
          CommandExecutionStatus.RUNNING);

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

      logCallback.saveExecutionLog(
          format("TfVar Git directory: [%s]", tfVarDirectory), INFO, CommandExecutionStatus.RUNNING);
    }
  }

  public static void copyFilesToWorkingDirectory(String sourceDir, String destinationDir) throws IOException {
    File dest = new File(destinationDir);
    File src = new File(sourceDir);
    deleteDirectoryAndItsContentIfExists(dest.getAbsolutePath());
    FileUtils.copyDirectory(src, dest);
    FileIo.waitForDirectoryToBeAccessibleOutOfProcess(dest.getPath(), 10);
  }

  public String getLatestCommitSHAFromLocalRepo(GitOperationContext gitOperationContext) {
    File repoDir = new File(gitClientHelper.getRepoDirectory(gitOperationContext));
    if (repoDir.exists()) {
      try (Git git = Git.open(repoDir)) {
        Iterator<RevCommit> commits = git.log().call().iterator();
        if (commits.hasNext()) {
          RevCommit firstCommit = commits.next();

          return firstCommit.toString().split(" ")[1];
        }
      } catch (IOException | GitAPIException e) {
        log.error("Failed to extract the commit id from the cloned repo.");
      }
    }

    return null;
  }

  public ImmutableMap<String, String> getEnvironmentVariables(TerragruntProvisionParameters parameters)
      throws IOException {
    ImmutableMap.Builder<String, String> envVars = ImmutableMap.builder();
    if (isNotEmpty(parameters.getEnvironmentVariables())) {
      envVars.putAll(parameters.getEnvironmentVariables());
    }
    if (isNotEmpty(parameters.getEncryptedEnvironmentVariables())) {
      for (Map.Entry<String, EncryptedDataDetail> entry : parameters.getEncryptedEnvironmentVariables().entrySet()) {
        String value = String.valueOf(encryptionService.getDecryptedValue(entry.getValue(), false));
        envVars.put(entry.getKey(), value);
      }
    }
    return envVars.build();
  }

  public String collectEnvVarKeys(Map<String, String> envVars) {
    if (isNotEmpty(envVars)) {
      return envVars.keySet().stream().collect(Collectors.joining(", "));
    }
    return "";
  }

  public void downloadTfStateFile(TerragruntProvisionParameters parameters, String terraformConfigFileDirectory) {
    File tfStateFile = (isEmpty(parameters.getWorkspace()))
        ? Paths.get(terraformConfigFileDirectory, TERRAFORM_STATE_FILE_NAME).toFile()
        : Paths.get(terraformConfigFileDirectory, format(WORKSPACE_STATE_FILE_PATH_FORMAT, parameters.getWorkspace()))
              .toFile();

    if (parameters.getCurrentStateFileId() != null) {
      try (InputStream stateRemoteInputStream = delegateFileManager.downloadByFileId(
               TERRAFORM_STATE, parameters.getCurrentStateFileId(), parameters.getAccountId())) {
        PushbackInputStream pushbackInputStream = new PushbackInputStream(stateRemoteInputStream);
        int firstByte = pushbackInputStream.read();
        if (firstByte == -1) {
          FileUtils.deleteQuietly(tfStateFile);
        } else {
          pushbackInputStream.unread(firstByte);
          FileUtils.copyInputStreamToFile(pushbackInputStream, tfStateFile);
        }
      } catch (IOException ex) {
        throw new FileReadException(
            format("Could not download Terraform state file with id: %s", parameters.getCurrentStateFileId()));
      }
    } else {
      FileUtils.deleteQuietly(tfStateFile);
    }
  }

  public String getTargetArgs(List<String> targets) {
    StringBuilder targetArgs = new StringBuilder();
    if (isNotEmpty(targets)) {
      for (String target : targets) {
        targetArgs.append("-target=" + target + " ");
      }
    }
    return targetArgs.toString();
  }

  public boolean shouldSkipRefresh(TerragruntProvisionParameters parameters) {
    return parameters.getEncryptedTfPlan() != null && parameters.isSkipRefreshBeforeApplyingPlan();
  }

  public List<NameValuePair> getAllVariables(
      Map<String, String> variables, Map<String, EncryptedDataDetail> encryptedVariables) {
    List<NameValuePair> allVars = new ArrayList<>();
    if (isNotEmpty(variables)) {
      for (Map.Entry<String, String> entry : variables.entrySet()) {
        allVars.add(new NameValuePair(entry.getKey(), entry.getValue(), ServiceVariableType.TEXT.name()));
      }
    }

    if (isNotEmpty(encryptedVariables)) {
      for (Map.Entry<String, EncryptedDataDetail> entry : encryptedVariables.entrySet()) {
        allVars.add(new NameValuePair(
            entry.getKey(), entry.getValue().getEncryptedData().getUuid(), ServiceVariableType.ENCRYPTED_TEXT.name()));
      }
    }
    return allVars;
  }

  public File getTerraformStateFile(String terraformScriptDirectory, String workspace) {
    if (!StringUtils.isBlank(terraformScriptDirectory)) {
      File tfStateFile = isEmpty(workspace)
          ? Paths.get(terraformScriptDirectory, TERRAFORM_STATE_FILE_NAME).toFile()
          : Paths.get(terraformScriptDirectory, format(WORKSPACE_STATE_FILE_PATH_FORMAT, workspace)).toFile();

      if (tfStateFile.exists()) {
        return tfStateFile;
      }
    }
    return null;
  }

  public byte[] getTerraformPlanFile(String scriptDirectory, String planname) throws IOException {
    return Files.readAllBytes(Paths.get(scriptDirectory, planname));
  }

  public void saveBacknedConfigToFile(File tfBackendConfigsFile, Map<String, String> backendConfigs,
      Map<String, EncryptedDataDetail> encryptedBackendConfigs) throws IOException {
    if (isNotEmpty(backendConfigs) || isNotEmpty(encryptedBackendConfigs)) {
      try (BufferedWriter writer =
               new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tfBackendConfigsFile), "UTF-8"))) {
        if (isNotEmpty(backendConfigs)) {
          for (Map.Entry<String, String> entry : backendConfigs.entrySet()) {
            saveVariable(writer, entry.getKey(), entry.getValue());
          }
        }
        if (isNotEmpty(encryptedBackendConfigs)) {
          for (Map.Entry<String, EncryptedDataDetail> entry : encryptedBackendConfigs.entrySet()) {
            String value = String.valueOf(encryptionService.getDecryptedValue(entry.getValue(), false));
            saveVariable(writer, entry.getKey(), value);
          }
        }
      }
    }
  }

  public String getTfAutoApproveArgument(TerragruntCliCommandRequestParams cliParams, String tfBinaryPath)
      throws IOException, InterruptedException, TimeoutException {
    if (!cliParams.isUseAutoApproveFlag()) {
      return FORCE_FLAG;
    }
    TerraformVersion version =
        terraformClient.version(tfBinaryPath, cliParams.getTimeoutInMillis(), cliParams.getDirectory());
    return TerraformHelperUtils.getAutoApproveArgument(version);
  }

  public static LogCallback getExecutionLogCallback(
      DelegateLogService delegateLogService, String accountId, String appId, String activityId, String commandUnit) {
    return new ExecutionLogCallback(delegateLogService, accountId, appId, activityId, commandUnit);
  }
}
