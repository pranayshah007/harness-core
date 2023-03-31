/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.git.model.ChangeType.MODIFY;
import static io.harness.logging.LogLevel.ERROR;
import static io.harness.logging.LogLevel.INFO;

import static software.wings.beans.LogColor.White;
import static software.wings.beans.LogHelper.color;
import static software.wings.beans.LogWeight.Bold;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.gitsync.GitPRCreateRequest;
import io.harness.connector.service.git.NGGitService;
import io.harness.connector.task.git.GitDecryptionHelper;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.gitapi.GitApiMergePRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.gitapi.client.impl.AzureRepoApiClient;
import io.harness.delegate.task.gitapi.client.impl.BitbucketApiClient;
import io.harness.delegate.task.gitapi.client.impl.GithubApiClient;
import io.harness.delegate.task.gitapi.client.impl.GitlabApiClient;
import io.harness.delegate.task.gitops.GitOpsTaskHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.git.helper.BitbucketHelper;
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
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.jooq.tools.StringUtils;
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
  @Inject private GithubApiClient githubApiClient;
  @Inject private GitlabApiClient gitlabApiClient;
  @Inject private AzureRepoApiClient azureRepoApiClient;
  @Inject private BitbucketApiClient bitbucketApiClient;
  @Inject public GitOpsTaskHelper gitOpsTaskHelper;

  private Gson gson = new GsonBuilder().setPrettyPrinting().create();

  public static final String FetchFiles = "Fetch Files";
  public static final String UpdateFiles = "Update GitOps Configuration files";
  public static final String CommitAndPush = "Commit and Push";
  public static final String CreatePR = "Create PR";
  public static final String MergePR = "Merge PR";
  public static final String PullRequestMessage = "Pull Request successfully merged";

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
      case MERGE_PR:
        return handleMergePR(gitOpsTaskParams);
      case CREATE_PR:
      case UPDATE_RELEASE_REPO:
        return handleCreatePR(gitOpsTaskParams);
      default:
        return NGGitOpsResponse.builder()
            .taskStatus(TaskStatus.FAILURE)
            .errorMessage("Failed GitOps task: " + gitOpsTaskParams.getGitOpsTaskType())
            .build();
    }
  }

  public DelegateResponseData handleMergePR(NGGitOpsTaskParams gitOpsTaskParams) {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    try {
      log.info("Running Merge PR Task for activityId {}", gitOpsTaskParams.getActivityId());
      logCallback = new NGDelegateLogCallback(getLogStreamingTaskClient(), MergePR, true, commandUnitsProgress);
      GitApiTaskParams taskParams = gitOpsTaskParams.getGitApiTaskParams();
      ConnectorType connectorType = gitOpsTaskParams.connectorInfoDTO.getConnectorType();
      GitApiTaskResponse responseData;
      switch (connectorType) {
        case GITHUB:
          responseData = (GitApiTaskResponse) githubApiClient.mergePR(taskParams);
          if (responseData.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS
              && taskParams.isDeleteSourceBranch()) {
            GitApiTaskResponse resp = (GitApiTaskResponse) githubApiClient.deleteRef(taskParams);
            if (resp.getCommandExecutionStatus() == CommandExecutionStatus.FAILURE) {
              // Not failing the command unit for failure to delete source branch
              logCallback.saveExecutionLog(
                  format("Error encountered when deleting source branch %s of the pull request",
                      gitOpsTaskParams.getGitApiTaskParams().getRef()),
                  INFO);
            }
          }
          break;
        case GITLAB:
          responseData = (GitApiTaskResponse) gitlabApiClient.mergePR(taskParams);
          break;
        case AZURE_REPO:
          responseData = (GitApiTaskResponse) azureRepoApiClient.mergePR(taskParams);
          break;
        case BITBUCKET:
          responseData = (GitApiTaskResponse) bitbucketApiClient.mergePR(taskParams);
          break;

        default:
          String errorMsg = "Failed to execute MergePR step. Connector not supported";
          logCallback.saveExecutionLog(errorMsg, INFO, CommandExecutionStatus.FAILURE);
          throw new InvalidRequestException(errorMsg);
      }

      if (responseData.getCommandExecutionStatus() != CommandExecutionStatus.SUCCESS) {
        logCallback.saveExecutionLog(
            "Error encountered when merging the pull request", ERROR, CommandExecutionStatus.FAILURE);
        return NGGitOpsResponse.builder()
            .taskStatus(TaskStatus.FAILURE)
            .errorMessage(responseData.getErrorMessage())
            .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
            .build();
      }

      logCallback.saveExecutionLog(format("PR Link: %s", gitOpsTaskParams.getPrLink(), INFO));
      logCallback.saveExecutionLog(format("%s", PullRequestMessage), INFO);
      String sha = ((GitApiMergePRTaskResponse) responseData.getGitApiResult()).getSha();
      logCallback.saveExecutionLog(format("Commit Sha is %s", sha), INFO);
      logCallback.saveExecutionLog("Done.", INFO, CommandExecutionStatus.SUCCESS);

      return NGGitOpsResponse.builder()
          .taskStatus(TaskStatus.SUCCESS)
          .commitId(sha)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception e) {
      logCallback.saveExecutionLog(e.getMessage(), ERROR, CommandExecutionStatus.FAILURE);
      return NGGitOpsResponse.builder()
          .taskStatus(TaskStatus.FAILURE)
          .errorMessage(e.getMessage())
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    }
  }

  public FetchFilesResult getFiles(NGGitOpsTaskParams gitOpsTaskParams, CommandUnitsProgress commandUnitsProgress)
      throws IOException, ParseException {
    try {
      FetchFilesResult fetchFilesResult =
          gitOpsTaskHelper.getFetchFilesResult(gitOpsTaskParams.getGitFetchFilesConfig(),
              gitOpsTaskParams.getAccountId(), logCallback, gitOpsTaskParams.isCloseLogStream());
      updateFilesNotFoundWithEmptyContent(
          fetchFilesResult, gitOpsTaskParams.getGitFetchFilesConfig().getGitStoreDelegateConfig().getPaths());
      this.logCallback = markDoneAndStartNew(logCallback, UpdateFiles, commandUnitsProgress);
      updateFiles(gitOpsTaskParams.getFilesToVariablesMap(), fetchFilesResult);
      return fetchFilesResult;
    } catch (Exception e) {
      if (e.getCause() instanceof NoSuchFileException) {
        this.logCallback.saveExecutionLog(color(format("Files were not found. Creating files at the requested path."),
                                              LogColor.White, LogWeight.Bold),
            ERROR);
        List<GitFile> gitFiles = new ArrayList<>();
        gitOpsTaskParams.getFilesToVariablesMap().forEach((file, values) -> {
          // default is yaml
          if (file.toLowerCase().endsWith(".json")) {
            gitFiles.add(GitFile.builder().filePath(file).fileContent(gson.toJson(values)).build());
          } else {
            try {
              gitFiles.add(
                  GitFile.builder().filePath(file).fileContent(convertJsonToYaml(gson.toJson(values))).build());
            } catch (IOException ex) {
              throw new RuntimeException("Failed to convert json to yaml while fetching files.");
            }
          }
        });
        return FetchFilesResult.builder().files(gitFiles).build();
      }
      throw e;
    }
  }

  @VisibleForTesting
  void updateFilesNotFoundWithEmptyContent(@Nonnull FetchFilesResult fetchFilesResult, @Nonnull List<String> paths) {
    List<GitFile> updatedGitFiles = new ArrayList<>();
    for (String path : paths) {
      Optional<GitFile> gitfile =
          fetchFilesResult.getFiles().stream().filter(gitFile -> gitFile.getFilePath().equals(path)).findFirst();
      if (!gitfile.isPresent()) {
        updatedGitFiles.add(GitFile.builder().fileContent("").filePath(path).build());
      } else {
        updatedGitFiles.add(gitfile.get());
      }
    }
    fetchFilesResult.setFiles(updatedGitFiles);
  }

  public DelegateResponseData handleCreatePR(NGGitOpsTaskParams gitOpsTaskParams) {
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    try {
      log.info("Running Create PR Task for activityId {}", gitOpsTaskParams.getActivityId());

      logCallback = new NGDelegateLogCallback(getLogStreamingTaskClient(), FetchFiles, true, commandUnitsProgress);
      FetchFilesResult fetchFilesResult = getFiles(gitOpsTaskParams, commandUnitsProgress);
      List<GitFile> fetchFilesResultFiles = fetchFilesResult.getFiles();
      StringBuilder sb = new StringBuilder(1024);
      fetchFilesResultFiles.forEach(f -> sb.append("\n- ").append(f.getFilePath()));

      logCallback.saveExecutionLog("Following files will be updated.");
      logCallback.saveExecutionLog(sb.toString(), INFO);
      logCallback = markDoneAndStartNew(logCallback, CommitAndPush, commandUnitsProgress);

      String baseBranch = gitOpsTaskParams.getGitFetchFilesConfig().getGitStoreDelegateConfig().getBranch();
      String newBranch = baseBranch + "_" + RandomStringUtils.randomAlphabetic(12);
      ScmConnector scmConnector =
          gitOpsTaskParams.getGitFetchFilesConfig().getGitStoreDelegateConfig().getGitConfigDTO();

      createNewBranch(scmConnector, newBranch, baseBranch);
      CommitAndPushResult gitCommitAndPushResult = commit(gitOpsTaskParams, fetchFilesResult, COMMIT_MSG, newBranch);

      List<GitFileChange> files = gitCommitAndPushResult.getFilesCommittedToGit();
      if (files == null || isEmpty(files)) {
        logCallback.saveExecutionLog(
            "No files were committed. Hence not creating a pull request.", ERROR, CommandExecutionStatus.FAILURE);
        throw new InvalidRequestException("No files were committed. Hence not creating a pull request.");
      }

      StringBuilder sb2 = new StringBuilder(1024);
      files.forEach(f -> sb2.append("\n- ").append(f.getFilePath()));

      logCallback.saveExecutionLog(format("Following files have been committed to branch %s", newBranch), INFO);
      logCallback.saveExecutionLog(sb.toString(), INFO);
      logCallback = markDoneAndStartNew(logCallback, CreatePR, commandUnitsProgress);

      String prTitle = resolvePRTitle(gitOpsTaskParams);

      CreatePRResponse createPRResponse =
          createPullRequest(scmConnector, newBranch, baseBranch, prTitle, gitOpsTaskParams.getAccountId());

      String prLink = getPRLink(createPRResponse.getNumber(), scmConnector, scmConnector.getConnectorType());

      logCallback.saveExecutionLog("Created PR " + prLink, INFO);
      logCallback.saveExecutionLog("Done.", INFO, CommandExecutionStatus.SUCCESS);

      return NGGitOpsResponse.builder()
          .commitId(gitCommitAndPushResult.getGitCommitResult().getCommitId())
          .prNumber(createPRResponse.getNumber())
          .prLink(prLink)
          .ref(newBranch)
          .taskStatus(TaskStatus.SUCCESS)
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    } catch (Exception e) {
      log.error("Failed to execute NGGitOpsCommandTask", e);
      logCallback.saveExecutionLog(Objects.toString(e.getMessage(), ""), ERROR, CommandExecutionStatus.FAILURE);
      return NGGitOpsResponse.builder()
          .taskStatus(TaskStatus.FAILURE)
          .errorMessage(e.getMessage())
          .unitProgressData(UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress))
          .build();
    }
  }

  public String resolvePRTitle(NGGitOpsTaskParams ngGitOpsTaskParams) {
    return (StringUtils.isEmpty(ngGitOpsTaskParams.getPrTitle())) ? PR_TITLE : ngGitOpsTaskParams.getPrTitle();
  }

  public String getPRLink(int prNumber, ScmConnector scmConnector, ConnectorType connectorType) {
    switch (connectorType) {
      case GITHUB:
        GithubConnectorDTO githubConnectorDTO = (GithubConnectorDTO) scmConnector;
        return "https://github.com"
            + "/" + githubConnectorDTO.getGitRepositoryDetails().getOrg() + "/"
            + githubConnectorDTO.getGitRepositoryDetails().getName() + "/pull"
            + "/" + prNumber;
      case AZURE_REPO:
        AzureRepoConnectorDTO azureRepoConnectorDTO = (AzureRepoConnectorDTO) scmConnector;
        return azureRepoConnectorDTO.getUrl() + "/pullrequest/" + prNumber;
      case GITLAB:
        GitlabConnectorDTO gitlabConnectorDTO = (GitlabConnectorDTO) scmConnector;
        return gitlabConnectorDTO.getUrl() + "/merge_requests/" + prNumber;
      case BITBUCKET:
        return BitbucketHelper.getBitbucketPRLink((BitbucketConnectorDTO) scmConnector, prNumber);
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

  public CreateBranchResponse createNewBranch(ScmConnector scmConnector, String branch, String baseBranch)
      throws Exception {
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

  public JSONObject mapToJson(String key, String value) {
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

    String[] keys = key.split("\\.");

    int len = keys.length - 1;

    Map<Object, Object> map1 = new HashMap<>();
    Map<Object, Object> map2 = new HashMap<>();

    map1.put(keys[len], value);

    while (--len >= 0) {
      map2.put(keys[len], map1);
      map1 = map2;
      map2 = new HashMap<>();
    }
    return new JSONObject(map1);
  }

  /**
   * updateFiles iterates over checkout files converts the yaml to json format. It finds the variables from
   * filesToVariablesMap map and then performs a merge.
   * If the variable key exists in the checkout file then it updates its value(override).
   * If the variable doesn't exist, we append the variable to the file.
   *
   * @param filesToVariablesMap resolve filesPaths from the releaseRepoConfig to Cluster Variables
   * @param fetchFilesResult    Files checkout out from git
   * @throws ParseException
   * @throws IOException
   */
  public void updateFiles(Map<String, Map<String, String>> filesToVariablesMap, FetchFilesResult fetchFilesResult)
      throws ParseException, IOException {
    List<String> updatedFiles = new ArrayList<>();
    logCallback.saveExecutionLog("Updating files with the following variables.");
    for (GitFile gitFile : fetchFilesResult.getFiles()) {
      logCallback.saveExecutionLog(String.format("\n%s:", gitFile.getFilePath()));
      Map<String, String> stringObjectMap = filesToVariablesMap.get(gitFile.getFilePath());
      stringObjectMap.forEach(
          (k, v) -> logCallback.saveExecutionLog(format("%s:%s", color(k, White, Bold), color(v, White, Bold)), INFO));
      String fileContent = gitFile.getFileContent();
      if (!StringUtils.isEmpty(fileContent) && !fileContent.toLowerCase().endsWith(".json")) {
        fileContent = convertYamlToJson(fileContent);
      }
      updatedFiles.add(updateJSONFile(fileContent, stringObjectMap));
    }

    List<GitFile> updatedGitFiles = new ArrayList<>();

    // Update the files and then convert them to yaml format
    for (int i = 0; i < updatedFiles.size(); i++) {
      GitFile gitFile = fetchFilesResult.getFiles().get(i);
      if (gitFile.getFilePath().toLowerCase().endsWith(".json")) {
        gitFile.setFileContent(updatedFiles.get(i));
      } else {
        gitFile.setFileContent(convertJsonToYaml(updatedFiles.get(i)));
      }
      updatedGitFiles.add(gitFile);
    }
    fetchFilesResult.setFiles(updatedGitFiles);
  }

  /**
   * This method replaces values for existing fields from file content and adds new entries for new keys
   * in the stringObjectMap
   *
   * @param fileContent
   * @param stringObjectMap
   * @return Updated file content with new keys
   * @throws ParseException
   * @throws JsonProcessingException
   */
  public String updateJSONFile(String fileContent, Map<String, String> stringObjectMap)
      throws ParseException, JsonProcessingException {
    JSONObject existingFile = new JSONObject();
    if (!StringUtils.isEmpty(fileContent)) {
      JSONParser parser = new JSONParser();
      existingFile = (JSONObject) parser.parse(fileContent);
    }

    for (String key : stringObjectMap.keySet()) {
      existingFile = mergeJSON(existingFile, mapToJson(key, stringObjectMap.get(key)));
    }

    return convertToPrettyJson(existingFile.toString());
  }

  public JSONObject mergeJSON(JSONObject existing, JSONObject updated) {
    Set<String> existingKeys = existing.keySet();
    for (Object updatedKey : updated.keySet()) {
      if (!existingKeys.contains(updatedKey)) {
        existing.put(updatedKey, updated.get(updatedKey));
      } else {
        Object existingObject = existing.get(updatedKey);
        Object updatingObject = updated.get(updatedKey);
        if (existingObject instanceof String && updatingObject instanceof String) {
          existing.put(updatedKey, updatingObject);
        } else if (existingObject instanceof String) {
          throw new InvalidRequestException("Data Type Mismatch: expected string, received nested map.");
        } else if (updatingObject instanceof String) {
          throw new InvalidRequestException("Data Type Mismatch: expected nested map, received string.");
        } else {
          if (existingObject instanceof Map && updatingObject instanceof Map) {
            existing.put(
                updatedKey, mergeJSON(new JSONObject((Map) existingObject), new JSONObject((Map) updatingObject)));
          } else {
            throw new InvalidRequestException(
                format("Received unsupported data type for this operation: %s", updatingObject.getClass()));
          }
        }
      }
    }
    return existing;
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
    return new YAMLMapper().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).writeValueAsString(jsonNodeTree);
  }
}
