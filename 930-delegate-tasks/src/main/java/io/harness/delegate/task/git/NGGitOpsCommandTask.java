/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.git.model.ChangeType.MODIFY;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogHelper.color;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.connector.helper.GitApiAccessDecryptionHelper;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.git.model.CommitAndPushRequest;
import io.harness.git.model.CommitAndPushResult;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.git.model.GitFileChange;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.product.ci.scm.proto.CreateBranchResponse;
import io.harness.product.ci.scm.proto.CreatePRResponse;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.shell.SshSessionConfig;

import software.wings.beans.LogColor;
import software.wings.beans.LogWeight;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.jooq.tools.json.JSONObject;
import org.jooq.tools.json.JSONParser;
import org.jooq.tools.json.ParseException;
import org.jose4j.lang.JoseException;

@Slf4j
@OwnedBy(GITOPS)
public class NGGitOpsCommandTask extends AbstractDelegateRunnableTask {
  private static final String PR_TITLE = "Harness: Updating config overrides";
  private static final String COMMIT_MSG = "Updating Config files";
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private ScmFetchFilesHelperNG scmFetchFilesHelper;
  @Inject private GitFetchFilesTaskHelper gitFetchFilesTaskHelper;
  @Inject private GitDecryptionHelper gitDecryptionHelper;
  @Inject private NGGitService ngGitService;

  public static final String FetchFiles = "Fetch Files";
  public static final String UpdateFiles = "Update GitOps Configuration files";
  public static final String CommitAndPush = "Commit and Push";
  public static final String CreatePR = "Create PR";

  private LogCallback logCallback;

  public NGGitOpsCommandTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    return null;
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) throws IOException, JoseException {
    NGGitOpsTaskParams gitOpsTaskParams = (NGGitOpsTaskParams) parameters;

    switch (gitOpsTaskParams.getGitOpsTaskType()) {
      case MERGE_PR: // TODO
      case CREATE_PR:
        return handleCreatePR(gitOpsTaskParams);
      default:
        return NGGitOpsResponse.builder()
            .taskStatus(TaskStatus.FAILURE)
            .errorMessage("Failed GitOps task: " + gitOpsTaskParams.getGitOpsTaskType())
            .build();
    }
  }

  public DelegateResponseData handleCreatePR(NGGitOpsTaskParams gitOpsTaskParams) {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    try {
      log.info("Running Create PR Task for activityId {}", gitOpsTaskParams.getActivityId());

      logCallback = new NGDelegateLogCallback(getLogStreamingTaskClient(), FetchFiles, true, commandUnitsProgress);

      FetchFilesResult fetchFilesResult = getFetchFilesResult(gitOpsTaskParams);

      logCallback = markDoneAndStartNew(logCallback, UpdateFiles, commandUnitsProgress);

      String baseBranch = gitOpsTaskParams.getGitFetchFilesConfig().getGitStoreDelegateConfig().getBranch();
      String newBranch = baseBranch + "_" + RandomStringUtils.randomAlphabetic(12);

      ScmConnector scmConnector =
          gitOpsTaskParams.getGitFetchFilesConfig().getGitStoreDelegateConfig().getGitConfigDTO();

      createNewBranch(scmConnector, newBranch, baseBranch);
      updateFiles(gitOpsTaskParams, fetchFilesResult);

      logCallback = markDoneAndStartNew(logCallback, CommitAndPush, commandUnitsProgress);

      CommitAndPushResult gitCommitAndPushResult = commit(gitOpsTaskParams, fetchFilesResult, COMMIT_MSG, newBranch);

      List<GitFileChange> files = gitCommitAndPushResult.getFilesCommittedToGit();
      StringBuilder sb = new StringBuilder(1024);
      files.forEach(f -> sb.append("\n- ").append(f.getFilePath()));

      logCallback.saveExecutionLog(format("Following files have been committed to branch %s", newBranch), INFO);
      logCallback.saveExecutionLog(sb.toString(), INFO);
      logCallback = markDoneAndStartNew(logCallback, CreatePR, commandUnitsProgress);

      CreatePRResponse createPRResponse =
          createPullRequest(scmConnector, newBranch, baseBranch, PR_TITLE, gitOpsTaskParams.getAccountId());
      String prLink = getPRLink(createPRResponse.getNumber(), scmConnector.getConnectorType(), scmConnector.getUrl());

      logCallback.saveExecutionLog("Created PR " + prLink, INFO);
      logCallback.saveExecutionLog("Done.", INFO, CommandExecutionStatus.SUCCESS);

      return NGGitOpsResponse.builder()
          .commitId(gitCommitAndPushResult.getGitCommitResult().getCommitId())
          .prNumber(createPRResponse.getNumber())
          .prLink(prLink)
          .taskStatus(TaskStatus.SUCCESS)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception e) {
      return NGGitOpsResponse.builder()
          .taskStatus(TaskStatus.FAILURE)
          .errorMessage(e.getMessage())
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    }
  }

  private FetchFilesResult getFetchFilesResult(NGGitOpsTaskParams gitOpsTaskParams) throws IOException {
    logCallback.saveExecutionLog(color(format("%nStarting Git Fetch Files"), LogColor.White, LogWeight.Bold));

    FetchFilesResult fetchFilesResult =
        fetchFilesFromRepo(gitOpsTaskParams.getGitFetchFilesConfig(), logCallback, gitOpsTaskParams.getAccountId());

    logCallback.saveExecutionLog(
        color(format("%nGit Fetch Files completed successfully."), LogColor.White, LogWeight.Bold), INFO);
    return fetchFilesResult;
  }

  public String getPRLink(int prNumber, ConnectorType connectorType, String url) {
    switch (connectorType) {
      // TODO: GITLAB, BITBUCKET
      case GITHUB:
        return url + "/"
            + "pull/" + prNumber + "/";
      default:
        return "";
    }
  }

  public CommitAndPushResult commit(
      NGGitOpsTaskParams gitOpsTaskParams, FetchFilesResult fetchFilesResult, String commitMessage, String newBranch) {
    GitStoreDelegateConfig gitStoreDelegateConfig =
        gitOpsTaskParams.getGitFetchFilesConfig().getGitStoreDelegateConfig();
    ScmConnector scmConnector = gitStoreDelegateConfig.getGitConfigDTO();
    SSHKeySpecDTO sshKeySpecDTO = gitStoreDelegateConfig.getSshKeySpecDTO();
    GitConfigDTO gitConfig = ScmConnectorMapper.toGitConfigDTO(scmConnector);

    gitConfig.setBranchName(newBranch);
    List<EncryptedDataDetail> encryptionDetails = gitStoreDelegateConfig.getEncryptedDataDetails();
    String commitId = gitStoreDelegateConfig.getCommitId();

    gitDecryptionHelper.decryptGitConfig(gitConfig, encryptionDetails);
    SshSessionConfig sshSessionConfig = gitDecryptionHelper.getSSHSessionConfig(sshKeySpecDTO, encryptionDetails);

    List<GitFileChange> gitFileChanges = new ArrayList<>();
    for (int i = 0; i < fetchFilesResult.getFiles().size(); i++) {
      gitFileChanges.add(GitFileChange.builder()
                             .changeType(MODIFY)
                             .filePath(fetchFilesResult.getFiles().get(i).getFilePath())
                             .fileContent(fetchFilesResult.getFiles().get(i).getFileContent())
                             .build());
    }

    CommitAndPushRequest gitCommitRequest = CommitAndPushRequest.builder()
                                                .gitFileChanges(gitFileChanges)
                                                .branch(newBranch)
                                                .commitId(commitId)
                                                .repoUrl(gitConfig.getUrl())
                                                .accountId(gitOpsTaskParams.getAccountId())
                                                .connectorId(gitOpsTaskParams.getConnectorInfoDTO().getName())
                                                .pushOnlyIfHeadSeen(false)
                                                .forcePush(true)
                                                .commitMessage(commitMessage)
                                                .build();

    return ngGitService.commitAndPush(gitConfig, gitCommitRequest, getAccountId(), sshSessionConfig);
  }

  private LogCallback markDoneAndStartNew(
      LogCallback logCallback, String newName, CommandUnitsProgress commandUnitsProgress) {
    logCallback.saveExecutionLog("\nDone", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    logCallback = new NGDelegateLogCallback(getLogStreamingTaskClient(), newName, true, commandUnitsProgress);
    return logCallback;
  }

  public CreateBranchResponse createNewBranch(ScmConnector scmConnector, String branch, String baseBranch) {
    return scmFetchFilesHelper.createNewBranch(scmConnector, branch, baseBranch);
  }

  public CreatePRResponse createPullRequest(
      ScmConnector scmConnector, String sourceBranch, String targetBranch, String title, String accountId) {
    return scmFetchFilesHelper.createPR(scmConnector,
        GitPRCreateRequest.builder()
            .title(title)
            .sourceBranch(sourceBranch)
            .targetBranch(targetBranch)
            .accountIdentifier(accountId)
            .build());
  }

  public JSONObject mapToJson(Map<String, Object> stringMap) {
    JSONObject fieldsToUpdate = new JSONObject(stringMap);
    JSONObject nestedFields = new JSONObject();

    Set<String> complexFields = new HashSet<>();

    for (Object key : fieldsToUpdate.keySet()) {
      String str = (String) key;
      if (str.contains(".")) {
        /*
          we need to make a conversion as shown below:

          "place.details.city": "blr"

          TO

          "place":
           {
              "details":
              {
                 "city": "blr",
              }
           }

         */
        complexFields.add(str);

        String[] keys = str.split("\\.");

        int len = keys.length - 1;

        Map<Object, Object> map1 = new HashMap<>();
        Map<Object, Object> map2 = new HashMap<>();

        map1.put(keys[len], fieldsToUpdate.get(key));

        while (--len >= 0) {
          map2.put(keys[len], map1);
          map1 = map2;
          map2 = new HashMap<>();
        }

        nestedFields.putAll(map1);
      }
    }

    for (String str : complexFields) {
      fieldsToUpdate.remove(str);
    }

    fieldsToUpdate.putAll(nestedFields);

    return fieldsToUpdate;
  }

  public void updateFiles(NGGitOpsTaskParams gitOpsTaskParams, FetchFilesResult fetchFilesResult)
      throws ParseException, IOException {
    Map<String, Object> stringMap = gitOpsTaskParams.getVariables();
    List<String> fetchedFilesContents = new ArrayList<>();

    for (GitFile gitFile : fetchFilesResult.getFiles()) {
      if (gitFile.getFilePath().contains(".yaml") || gitFile.getFilePath().contains(".yml")) {
        fetchedFilesContents.add(convertYamlToJson(gitFile.getFileContent()));
      } else {
        fetchedFilesContents.add(gitFile.getFileContent());
      }
    }

    List<String> updatedFiles = replaceFieldsNew(fetchedFilesContents, stringMap);
    List<GitFile> updatedGitFiles = new ArrayList<>();

    for (int i = 0; i < updatedFiles.size(); i++) {
      GitFile gitFile = fetchFilesResult.getFiles().get(i);
      if (gitFile.getFilePath().contains(".yaml") || gitFile.getFilePath().contains(".yml")) {
        gitFile.setFileContent(convertJsonToYaml(updatedFiles.get(i)));
      } else {
        gitFile.setFileContent(updatedFiles.get(i));
      }
      updatedGitFiles.add(gitFile);
    }

    fetchFilesResult.setFiles(updatedGitFiles);
  }

  public List<String> replaceFieldsNew(List<String> fileList, Map<String, Object> fieldsToModify)
      throws ParseException, JsonProcessingException {
    JSONObject fieldsToUpdate = mapToJson(fieldsToModify); // get the list of fields to be updated
    List<String> result = new ArrayList<>();
    for (String str : fileList) {
      JSONParser parser = new JSONParser();
      JSONObject json = (JSONObject) parser.parse(str);

      // change the required fields by merging
      json.putAll(fieldsToUpdate);

      result.add(convertToPrettyJson(json.toString()));
    }
    return result;
  }

  public String convertToPrettyJson(String uglyJson) throws JsonProcessingException {
    ObjectMapper jsonReader = new ObjectMapper(new JsonFactory());
    Object obj = jsonReader.readValue(uglyJson, Object.class);

    ObjectMapper jsonWriter = new ObjectMapper();
    return jsonWriter.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
  }

  public String convertYamlToJson(String yaml) throws IOException {
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object obj = yamlReader.readValue(yaml, Object.class);

    ObjectMapper jsonWriter = new ObjectMapper();
    return jsonWriter.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
  }

  public String convertJsonToYaml(String jsonString) throws IOException {
    JsonNode jsonNodeTree = new ObjectMapper().readTree(jsonString);
    return new YAMLMapper().writeValueAsString(jsonNodeTree);
  }

  public FetchFilesResult fetchFilesFromRepo(
      GitFetchFilesConfig gitFetchFilesConfig, LogCallback executionLogCallback, String accountId) throws IOException {
    GitStoreDelegateConfig gitStoreDelegateConfig = gitFetchFilesConfig.getGitStoreDelegateConfig();
    executionLogCallback.saveExecutionLog("Git connector Url: " + gitStoreDelegateConfig.getGitConfigDTO().getUrl());
    String fetchTypeInfo = gitStoreDelegateConfig.getFetchType() == FetchType.BRANCH
        ? "Branch: " + gitStoreDelegateConfig.getBranch()
        : "CommitId: " + gitStoreDelegateConfig.getCommitId();

    executionLogCallback.saveExecutionLog(fetchTypeInfo);

    List<String> filePathsToFetch = null;
    if (EmptyPredicate.isNotEmpty(gitFetchFilesConfig.getGitStoreDelegateConfig().getPaths())) {
      filePathsToFetch = gitFetchFilesConfig.getGitStoreDelegateConfig().getPaths();
      executionLogCallback.saveExecutionLog("\nFetching following Files :");
      gitFetchFilesTaskHelper.printFileNamesInExecutionLogs(filePathsToFetch, executionLogCallback);
    }

    FetchFilesResult gitFetchFilesResult;
    if (gitStoreDelegateConfig.isOptimizedFilesFetch()) {
      executionLogCallback.saveExecutionLog("Using optimized file fetch");
      secretDecryptionService.decrypt(
          GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(gitStoreDelegateConfig.getGitConfigDTO()),
          gitStoreDelegateConfig.getApiAuthEncryptedDataDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          GitApiAccessDecryptionHelper.getAPIAccessDecryptableEntity(gitStoreDelegateConfig.getGitConfigDTO()),
          gitStoreDelegateConfig.getApiAuthEncryptedDataDetails());
      gitFetchFilesResult = scmFetchFilesHelper.fetchFilesFromRepoWithScm(gitStoreDelegateConfig, filePathsToFetch);
    } else {
      GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
      gitDecryptionHelper.decryptGitConfig(gitConfigDTO, gitStoreDelegateConfig.getEncryptedDataDetails());
      SshSessionConfig sshSessionConfig = gitDecryptionHelper.getSSHSessionConfig(
          gitStoreDelegateConfig.getSshKeySpecDTO(), gitStoreDelegateConfig.getEncryptedDataDetails());
      gitFetchFilesResult =
          ngGitService.fetchFilesByPath(gitStoreDelegateConfig, accountId, sshSessionConfig, gitConfigDTO);
    }

    gitFetchFilesTaskHelper.printFileNamesInExecutionLogs(executionLogCallback, gitFetchFilesResult.getFiles());

    return gitFetchFilesResult;
  }
}
