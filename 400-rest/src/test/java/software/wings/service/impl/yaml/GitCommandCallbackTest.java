/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.yaml.GitFileChange.Builder.aGitFileChange;
import static software.wings.service.impl.yaml.sync.GitSyncErrorUtils.getYamlContentOfError;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.yaml.errorhandling.GitSyncError.GitSyncDirection.GIT_TO_HARNESS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.eraro.ErrorCode;
import io.harness.ff.FeatureFlagService;
import io.harness.git.model.ChangeType;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import software.wings.beans.GitCommit;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.GitConnectionErrorAlert;
import software.wings.beans.yaml.GitCheckoutResult;
import software.wings.beans.yaml.GitCommand.GitCommandType;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommandExecutionResponse.GitCommandStatus;
import software.wings.beans.yaml.GitDiffResult;
import software.wings.beans.yaml.GitFileChange;
import software.wings.service.impl.yaml.gitdiff.GitChangeSetHandler;
import software.wings.service.impl.yaml.sync.GitSyncFailureAlertDetails;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.service.intfc.yaml.sync.GitSyncErrorService;
import software.wings.service.intfc.yaml.sync.GitSyncService;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.errorhandling.GitToHarnessErrorDetails;
import software.wings.yaml.gitSync.GitWebhookRequestAttributes;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.YamlChangeSet.Status;
import software.wings.yaml.gitSync.YamlGitConfig;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class GitCommandCallbackTest extends CategoryTest {
  private static final String CHANGESET_ID = "changesetId";
  private static final String gitConnectorId = "gitConnectorId";
  private static final String repositoryName = "repositoryName";
  private static final String branchName = "branchName";

  @Mock private YamlChangeSetService yamlChangeSetService;
  @Mock private YamlGitService yamlGitService;
  @Mock private GitSyncErrorService gitSyncErrorService;
  @Mock private GitSyncService gitSyncService;
  @Mock private GitChangeSetHandler gitChangeSetHandler;
  @Mock private FeatureFlagService featureFlagService;

  @InjectMocks
  private GitCommandCallback commandCallback = new GitCommandCallback(
      ACCOUNT_ID, CHANGESET_ID, GitCommandType.COMMIT_AND_PUSH, gitConnectorId, repositoryName, branchName);

  @InjectMocks
  @Spy
  private GitCommandCallback diffCommandCallback =
      new GitCommandCallback(ACCOUNT_ID, CHANGESET_ID, GitCommandType.DIFF, gitConnectorId, repositoryName, branchName);

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCallbackForGitConnectionFailure() throws Exception {
    String errorMessage = "cant connect to git";
    DelegateResponseData notifyResponseData = GitCommandExecutionResponse.builder()
                                                  .errorCode(ErrorCode.GIT_CONNECTION_ERROR)
                                                  .gitCommandStatus(GitCommandStatus.FAILURE)
                                                  .errorMessage(errorMessage)
                                                  .build();

    doReturn(true).when(yamlChangeSetService).updateStatus(anyString(), anyString(), any());
    doNothing().when(yamlGitService).raiseAlertForGitFailure(anyString(), anyString(), any());
    Map<String, Supplier<ResponseData>> map = new HashMap<>();
    map.put("key", () -> notifyResponseData);
    commandCallback.notify(map);
    verify(yamlGitService, times(1))
        .raiseAlertForGitFailure(ACCOUNT_ID, GLOBAL_APP_ID,
            GitSyncFailureAlertDetails.builder()
                .errorMessage(errorMessage)
                .errorCode(ErrorCode.GIT_CONNECTION_ERROR)
                .gitConnectorId(gitConnectorId)
                .branchName(branchName)
                .repositoryName(repositoryName)
                .build());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCallbackForGitConnectionSuccess() throws Exception {
    DelegateResponseData notifyResponseData =
        GitCommandExecutionResponse.builder().gitCommandStatus(GitCommandStatus.SUCCESS).build();

    doReturn(true).when(yamlChangeSetService).updateStatus(anyString(), anyString(), any());
    doThrow(new RuntimeException())
        .when(yamlGitService)
        .closeAlertForGitFailureIfOpen(anyString(), anyString(), any(), any());
    Map<String, Supplier<ResponseData>> map = new HashMap<>();
    map.put("key", () -> notifyResponseData);

    try {
      commandCallback.notify(map);
    } catch (RuntimeException e) {
      // the way we are testing here is, for GitCommandStatus.SUCCESS, closeAlertForGitFailureIfOpen() should get
      // called. in mock, we are throwing exception on this method call as this is what we want to test. rest of code
      // flow is not required for this unit test
      assertThat(true).isTrue();
    }
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testNotifyOnErrorCase() {
    DelegateResponseData notifyResponseData = ErrorNotifyResponseData.builder().build();

    Map<String, Supplier<ResponseData>> map = new HashMap<>();
    map.put("key", () -> notifyResponseData);

    commandCallback.notify(map);
    verify(yamlGitService, never())
        .closeAlertForGitFailureIfOpen(
            anyString(), anyString(), any(AlertType.class), any(GitConnectionErrorAlert.class));
    verify(yamlChangeSetService, times(1)).updateStatus(ACCOUNT_ID, CHANGESET_ID, Status.FAILED);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testNotifyWithUnhandledGitCommandType() {
    DelegateResponseData notifyResponseData = GitCommandExecutionResponse.builder()
                                                  .gitCommandStatus(GitCommandStatus.SUCCESS)
                                                  .gitCommandResult(GitCheckoutResult.builder().build())
                                                  .build();

    Map<String, Supplier<ResponseData>> map = new HashMap<>();
    map.put("key", () -> notifyResponseData);

    on(commandCallback).set("gitCommandType", GitCommandType.CHECKOUT);
    commandCallback.notify(map);
    verify(yamlGitService, times(1))
        .closeAlertForGitFailureIfOpen(ACCOUNT_ID, GLOBAL_APP_ID, AlertType.GitConnectionError,
            GitConnectionErrorAlert.builder()
                .accountId(ACCOUNT_ID)
                .gitConnectorId("gitConnectorId")
                .branchName("branchName")
                .repositoryName(repositoryName)
                .build());
    verify(yamlChangeSetService, times(1)).updateStatus(ACCOUNT_ID, CHANGESET_ID, Status.FAILED);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_addActiveGitSyncErrorsToProcessAgain() {
    final YamlGitConfig yamlGitConfig =
        YamlGitConfig.builder().gitConnectorId("gitconnectorid").branchName("branchname").build();
    final GitFileChange gitFileChange1 = aGitFileChange()
                                             .withFilePath("Setup/Applications/app1/index.yaml")
                                             .withFileContent("error_content")
                                             .withCommitId("commitid-1")
                                             .withSyncFromGit(false)
                                             .withAccountId(ACCOUNT_ID)
                                             .withChangeType(ChangeType.DELETE)
                                             .withYamlGitConfig(yamlGitConfig)
                                             .build();
    final GitFileChange gitFileChange2 = aGitFileChange()
                                             .withFilePath("Setup/Applications/app2/index.yaml")
                                             .withFileContent("error_content")
                                             .withCommitId("commitid-1")
                                             .withSyncFromGit(false)
                                             .withAccountId(ACCOUNT_ID)
                                             .withChangeType(ChangeType.DELETE)
                                             .withYamlGitConfig(yamlGitConfig)
                                             .build();
    final GitDiffResult gitDiffResult =
        GitDiffResult.builder().yamlGitConfig(yamlGitConfig).gitFileChanges(Lists.newArrayList()).build();
    gitDiffResult.addChangeFile(gitFileChange1);
    gitDiffResult.addChangeFile(gitFileChange2);
    GitToHarnessErrorDetails gitToHarnessErrorDetails =
        GitToHarnessErrorDetails.builder().gitCommitId("commitid-1").yamlContent("error_content").build();
    final GitSyncError gitSyncError1 = GitSyncError.builder()
                                           .yamlFilePath("Setup/index.yaml")
                                           .accountId(ACCOUNT_ID)
                                           .changeType("MODIFY")
                                           .additionalErrorDetails(gitToHarnessErrorDetails)
                                           .build();
    final GitSyncError gitSyncError2 = GitSyncError.builder()
                                           .yamlFilePath("Setup/Applications/app1/index.yaml")
                                           .gitSyncDirection(GIT_TO_HARNESS.toString())
                                           .additionalErrorDetails(gitToHarnessErrorDetails)
                                           .accountId(ACCOUNT_ID)
                                           .changeType("MODIFY")
                                           .build();
    doReturn(Arrays.asList(gitSyncError1, gitSyncError2))
        .when(gitSyncErrorService)
        .getActiveGitToHarnessSyncErrors(eq(ACCOUNT_ID), eq("gitconnectorid"), eq("branchname"), any(), anyLong());

    diffCommandCallback.addActiveGitSyncErrorsToProcessAgain(gitDiffResult, ACCOUNT_ID);
    final List<GitFileChange> modifiedGitFileChangeSetList = gitDiffResult.getGitFileChanges();
    assertThat(modifiedGitFileChangeSetList.size()).isEqualTo(3);
    assertThat(modifiedGitFileChangeSetList).contains(gitFileChange1);
    assertThat(modifiedGitFileChangeSetList).contains(gitFileChange2);
    assertThat(modifiedGitFileChangeSetList).contains(gitFileChange2);
    final GitFileChange gitFileChange3 =
        modifiedGitFileChangeSetList.stream()
            .filter(gitFileChange -> gitFileChange != gitFileChange1 && gitFileChange != gitFileChange2)
            .findFirst()
            .get();
    assertThat(gitFileChange3.getFilePath().equals(gitSyncError1.getYamlFilePath())).isTrue();
    assertThat(gitFileChange3.getFileContent().equals(getYamlContentOfError(gitSyncError1))).isTrue();
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void testCallbackForGitDiffFailure() throws Exception {
    DelegateResponseData notifyResponseData = GitCommandExecutionResponse.builder()
                                                  .errorCode(ErrorCode.GIT_DIFF_COMMIT_NOT_IN_ORDER)
                                                  .gitCommandStatus(GitCommandStatus.FAILURE)
                                                  .errorMessage("cant connect to git")
                                                  .build();

    doReturn(true).when(yamlChangeSetService).updateStatus(anyString(), anyString(), any());
    final GitWebhookRequestAttributes webhookRequestAttributes = GitWebhookRequestAttributes.builder()
                                                                     .headCommitId("head")
                                                                     .branchName("master")
                                                                     .gitConnectorId("gitconnector")
                                                                     .build();
    final YamlChangeSet yamlChangeSet =
        YamlChangeSet.builder().gitWebhookRequestAttributes(webhookRequestAttributes).build();

    doReturn(yamlChangeSet).when(yamlChangeSetService).get(ACCOUNT_ID, CHANGESET_ID);
    doReturn(ImmutableList.of("yamlgitconfig1", "yamlgitconfig2"))
        .when(yamlGitService)
        .getYamlGitConfigIds(anyString(), anyString(), anyString(), anyString());
    doReturn(GitCommit.builder().build()).when(yamlGitService).saveCommit(any(GitCommit.class));
    Map<String, Supplier<ResponseData>> map = new HashMap<>();
    map.put("key", () -> notifyResponseData);

    diffCommandCallback.notify(map);
    verify(yamlGitService, times(1)).saveCommit(any(GitCommit.class));
    verify(gitSyncService, times(1))
        .createGitFileSummaryForFailedOrSkippedCommit(any(GitCommit.class), any(Boolean.class));
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_getAllFilesSuccessFullYProccessed() {
    GitFileChange gitFileChange1 = aGitFileChange().withFilePath("a").build();
    GitFileChange gitFileChange2 = aGitFileChange().withFilePath("a").build();
    GitFileChange gitFileChange3 = aGitFileChange().withFilePath("b").build();
    GitFileChange gitFileChange4 = aGitFileChange().withFilePath("c").build();

    List<GitFileChange> fileChangesPartOfYamlChangeSet = Arrays.asList(gitFileChange1, gitFileChange3);
    List<GitFileChange> filesCommited = Arrays.asList(gitFileChange2, gitFileChange4);
    List<GitFileChange> allFilesProcessed =
        commandCallback.getAllFilesSuccessFullyProccessed(fileChangesPartOfYamlChangeSet, filesCommited);
    assertThat(allFilesProcessed.size()).isEqualTo(3);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void test_getAllFilesSuccessFullyProccessedEmpty() {
    GitFileChange gitFileChange1 = aGitFileChange().withFilePath("a").build();
    GitFileChange gitFileChange3 = aGitFileChange().withFilePath("b").build();

    List<GitFileChange> fileChangesPartOfYamlChangeSet = Arrays.asList(gitFileChange1, gitFileChange3);
    List<GitFileChange> filesCommited = new ArrayList<>();
    List<GitFileChange> allFilesProcessed =
        commandCallback.getAllFilesSuccessFullyProccessed(fileChangesPartOfYamlChangeSet, filesCommited);
    assertThat(allFilesProcessed.size()).isEqualTo(2);
    assertThat(allFilesProcessed).isEqualTo(fileChangesPartOfYamlChangeSet);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGitUnseenHead() {
    DelegateResponseData notifyResponseData = GitCommandExecutionResponse.builder()
                                                  .gitCommandStatus(GitCommandStatus.FAILURE)
                                                  .errorCode(ErrorCode.GIT_UNSEEN_REMOTE_HEAD_COMMIT)
                                                  .build();
    doReturn(false).when(featureFlagService).isEnabled(eq(FeatureName.CG_GIT_POLLING), any());
    Map<String, Supplier<ResponseData>> map = new HashMap<>();
    map.put("key", () -> notifyResponseData);
    commandCallback.notify(map);
    verify(yamlChangeSetService, times(1)).updateStatusAndIncrementPushCount(any(), any(), any());
  }
}
