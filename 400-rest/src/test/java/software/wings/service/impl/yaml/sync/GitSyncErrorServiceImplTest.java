/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml.sync;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.git.model.ChangeType.ADD;
import static io.harness.git.model.ChangeType.MODIFY;
import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.VARDAN_BANSAL;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.SettingAttribute.SettingCategory.CONNECTOR;
import static software.wings.beans.yaml.GitFileChange.Builder.aGitFileChange;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.REPOSITORY_NAME;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.yaml.errorhandling.GitSyncError.GitSyncDirection.GIT_TO_HARNESS;
import static software.wings.yaml.errorhandling.GitSyncError.GitSyncDirection.HARNESS_TO_GIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.beans.PageRequest;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.alerts.AlertStatus;
import software.wings.beans.CGConstants;
import software.wings.beans.EntityType;
import software.wings.beans.GitConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.alert.Alert;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.GitConnectionErrorAlert;
import software.wings.beans.yaml.GitFileChange;
import software.wings.service.impl.GitConfigHelperService;
import software.wings.service.impl.yaml.GitSyncErrorStatus;
import software.wings.service.impl.yaml.GitToHarnessErrorCommitStats;
import software.wings.yaml.errorhandling.GitProcessingError;
import software.wings.yaml.errorhandling.GitSyncError;
import software.wings.yaml.errorhandling.GitSyncError.GitSyncErrorKeys;
import software.wings.yaml.errorhandling.GitToHarnessErrorDetails;
import software.wings.yaml.errorhandling.HarnessToGitErrorDetails;
import software.wings.yaml.gitSync.GitFileActivity;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.inject.Inject;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class GitSyncErrorServiceImplTest extends WingsBaseTest {
  @Mock private GitConfigHelperService gitConfigHelperService;
  @InjectMocks @Inject private GitSyncErrorServiceImpl gitSyncErrorService;
  @Inject private HPersistence persistence;

  private final String accountId = generateUuid();
  private static final String WEBHOOK_TOKEN = "Webhook_Token";
  String yamlFilePath = "filePath";
  String errorMessage = "errorMessage";
  String branchName = "branchName";
  String repositoryName = "repositoryName";
  String gitConnectorId = "gitConnectorId";
  String newBranchName = "newBranchName";
  String newRepositoryName = "newRepositoryName";
  String newGitConnectorId = "newGitConnectorId";
  String previousCommitId = "previousCommitId";
  String newCommitId = "newCommitId";
  String yamlContent = "yamlContent";
  String newYamlContent = "newYamlContent";
  String appId = "appId";

  YamlGitConfig yamlGitConfig = YamlGitConfig.builder().branchName(branchName).gitConnectorId(gitConnectorId).build();
  YamlGitConfig newYamlGitConfig =
      YamlGitConfig.builder().branchName(newBranchName).gitConnectorId(newGitConnectorId).build();

  YamlGitConfig yamlGitConfigAccount = YamlGitConfig.builder()
                                           .branchName(branchName)
                                           .repositoryName(repositoryName)
                                           .gitConnectorId(gitConnectorId)
                                           .build();
  YamlGitConfig newYamlGitConfigAccount = YamlGitConfig.builder()
                                              .branchName(newBranchName)
                                              .repositoryName(newRepositoryName)
                                              .gitConnectorId(newGitConnectorId)
                                              .build();

  @Before
  public void setUp() throws Exception {
    yamlGitConfig.setUuid("uuid");
    newYamlGitConfig.setUuid("newUuid");
    yamlGitConfigAccount.setUuid("uuid1");
    newYamlGitConfigAccount.setUuid("newUuid1");
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_fetchGitToHarnessErrors() {
    final String commitId = "gitCommitId";
    // Saving GitSyncError
    SettingAttribute gitConnector = aSettingAttribute()
                                        .withAccountId(accountId)
                                        .withName("settingName")
                                        .withCategory(CONNECTOR)
                                        .withValue(GitConfig.builder().build())
                                        .build();
    String gitConnectorId = persistence.save(gitConnector);
    final GitToHarnessErrorDetails gitSyncErrorDetails = GitToHarnessErrorDetails.builder()
                                                             .gitCommitId(commitId)
                                                             .commitTime(Long.valueOf(123))
                                                             .commitMessage("commitMessage")
                                                             .build();
    final GitSyncError gitSyncError = GitSyncError.builder()
                                          .accountId(accountId)
                                          .yamlFilePath(yamlFilePath)
                                          .gitSyncDirection(GIT_TO_HARNESS.toString())
                                          .gitConnectorId(gitConnectorId)
                                          .branchName(branchName)
                                          .failureReason("failureReason")
                                          .additionalErrorDetails(gitSyncErrorDetails)
                                          .build();
    gitSyncError.setAppId(appId);
    persistence.save(gitSyncError);

    PageRequest<GitToHarnessErrorCommitStats> req = aPageRequest().withLimit("2").withOffset("0").build();
    List<GitToHarnessErrorCommitStats> errorsList =
        gitSyncErrorService.listGitToHarnessErrorsCommits(req, accountId, appId, 2).getResponse();
    assertThat(errorsList.size()).isEqualTo(1);
    GitToHarnessErrorCommitStats error = errorsList.get(0);
    assertThat(error.getFailedCount()).isEqualTo(1);

    final GitSyncError gitSyncError2 = GitSyncError.builder()
                                           .accountId(accountId)
                                           .yamlFilePath(yamlFilePath)
                                           .gitSyncDirection(GIT_TO_HARNESS.toString())
                                           .gitConnectorId(gitConnectorId)
                                           .branchName(branchName)
                                           .repositoryName(repositoryName)
                                           .failureReason("failureReason")
                                           .additionalErrorDetails(gitSyncErrorDetails)
                                           .build();
    gitSyncError2.setAppId(appId);
    persistence.save(gitSyncError2);

    errorsList = gitSyncErrorService.listGitToHarnessErrorsCommits(req, accountId, appId, 2).getResponse();
    assertThat(errorsList.size()).isEqualTo(1);
    error = errorsList.get(0);
    assertThat(error.getFailedCount()).isEqualTo(2);

    verify(gitConfigHelperService, times(2)).createRepositoryInfo(any(), any());
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void test_fetchGitToHarnessErrorsAccount() {
    final String commitId = "gitCommitId";
    // Saving GitSyncError
    SettingAttribute gitConnector = aSettingAttribute()
                                        .withAccountId(accountId)
                                        .withName("settingName")
                                        .withCategory(CONNECTOR)
                                        .withValue(GitConfig.builder().build())
                                        .build();
    String gitConnectorId = persistence.save(gitConnector);
    final GitToHarnessErrorDetails gitSyncErrorDetails = GitToHarnessErrorDetails.builder()
                                                             .gitCommitId(commitId)
                                                             .commitTime(Long.valueOf(123))
                                                             .commitMessage("commitMessage")
                                                             .build();
    final GitSyncError gitSyncError = GitSyncError.builder()
                                          .accountId(accountId)
                                          .yamlFilePath(yamlFilePath)
                                          .gitSyncDirection(GIT_TO_HARNESS.toString())
                                          .gitConnectorId(gitConnectorId)
                                          .branchName(branchName)
                                          .repositoryName(repositoryName)
                                          .failureReason("failureReason")
                                          .additionalErrorDetails(gitSyncErrorDetails)
                                          .build();
    gitSyncError.setAppId(appId);
    persistence.save(gitSyncError);

    PageRequest<GitToHarnessErrorCommitStats> req = aPageRequest().withLimit("2").withOffset("0").build();
    List<GitToHarnessErrorCommitStats> errorsList =
        gitSyncErrorService.listGitToHarnessErrorsCommits(req, accountId, appId, 2).getResponse();
    assertThat(errorsList.size()).isEqualTo(1);
    GitToHarnessErrorCommitStats error = errorsList.get(0);
    assertThat(error.getFailedCount()).isEqualTo(1);
    assertThat(error.getRepositoryName()).isEqualTo(repositoryName);
    assertThat(error.getBranchName()).isEqualTo(branchName);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_fetchGitToHarnessErrorsCommitWise() {
    final String commitId = "gitCommitId";
    // Saving GitSyncError
    final GitToHarnessErrorDetails gitSyncErrorDetails =
        GitToHarnessErrorDetails.builder().gitCommitId(commitId).previousErrors(Collections.emptyList()).build();
    final GitSyncError gitSyncError = GitSyncError.builder()
                                          .accountId(accountId)
                                          .gitSyncDirection(GIT_TO_HARNESS.name())
                                          .yamlFilePath("Setup/Applications/settingAttribute2.yaml")
                                          .additionalErrorDetails(gitSyncErrorDetails)
                                          .build();
    gitSyncError.setCreatedAt(System.currentTimeMillis());
    String id = persistence.save(gitSyncError);

    PageRequest<GitSyncError> req = aPageRequest().withLimit("2").withOffset("0").build();
    List<GitSyncError> errorsList =
        gitSyncErrorService.fetchErrorsInEachCommits(req, commitId, accountId, null, null, null).getResponse();
    assertThat(errorsList.size()).isEqualTo(1);
    GitSyncError error = errorsList.get(0);
    assertThat(error.equals(gitSyncError)).isTrue();
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_getActiveGitToHarnessSyncErrors() {
    // required changes
    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withAccountId(ACCOUNT_ID)
            .withUuid(SETTING_ID)
            .withName("gitconnectorid")
            .withValue(GitConfig.builder().accountId(ACCOUNT_ID).webhookToken(WEBHOOK_TOKEN).build())
            .build();
    persistence.save(settingAttribute);

    YamlGitConfig yamlGitConfig1 = YamlGitConfig.builder()
                                       .entityType(EntityType.APPLICATION)
                                       .entityId("appid")
                                       .accountId(ACCOUNT_ID)
                                       .gitConnectorId(SETTING_ID)
                                       .branchName("branchName")
                                       .repositoryName(REPOSITORY_NAME)
                                       .enabled(true)
                                       .build();

    YamlGitConfig yamlGitConfig2 = YamlGitConfig.builder()
                                       .entityType(EntityType.ACCOUNT)
                                       .entityId(ACCOUNT_ID)
                                       .accountId(ACCOUNT_ID)
                                       .gitConnectorId(SETTING_ID)
                                       .branchName("branchName")
                                       .repositoryName(REPOSITORY_NAME)
                                       .enabled(true)
                                       .build();

    YamlGitConfig yamlGitConfig3 = YamlGitConfig.builder()
                                       .entityType(EntityType.APPLICATION)
                                       .entityId("appid1")
                                       .accountId(ACCOUNT_ID)
                                       .gitConnectorId(SETTING_ID)
                                       .branchName("branchName123")
                                       .repositoryName(REPOSITORY_NAME)
                                       .enabled(true)
                                       .build();
    yamlGitConfig1.setAppId("appid");
    persistence.save(yamlGitConfig1);
    persistence.save(yamlGitConfig2);
    persistence.save(yamlGitConfig3);
    GitToHarnessErrorDetails gitToHarnessErrorDetails =
        GitToHarnessErrorDetails.builder().gitCommitId("commitid").build();
    final GitSyncError gitSyncError1 = GitSyncError.builder()
                                           .yamlFilePath("Setup/index123.yaml")
                                           .accountId(ACCOUNT_ID)
                                           .gitSyncDirection(GIT_TO_HARNESS.toString())
                                           .changeType("MODIFY")
                                           .additionalErrorDetails(gitToHarnessErrorDetails)
                                           .branchName("branchName")
                                           .repositoryName(REPOSITORY_NAME)
                                           .gitConnectorId(SETTING_ID)
                                           .build();
    gitSyncError1.setAppId("appid");
    gitSyncError1.setStatus(GitSyncErrorStatus.ACTIVE);
    final String savedGitSyncError1 = persistence.save(gitSyncError1);

    final GitSyncError gitSyncError2 = GitSyncError.builder()
                                           .yamlFilePath("Setup/index456.yaml")
                                           .accountId(ACCOUNT_ID)
                                           .changeType("MODIFY")
                                           .gitSyncDirection(GIT_TO_HARNESS.toString())
                                           .additionalErrorDetails(gitToHarnessErrorDetails)
                                           .branchName("branchName")
                                           .repositoryName(REPOSITORY_NAME)
                                           .gitConnectorId(SETTING_ID)
                                           .build();
    gitSyncError2.setAppId(CGConstants.GLOBAL_APP_ID);
    gitSyncError2.setStatus(null);
    final String savedGitSyncError2 = persistence.save(gitSyncError2);

    final GitSyncError gitSyncError3 = GitSyncError.builder()
                                           .yamlFilePath("Setup/index789.yaml")
                                           .accountId(ACCOUNT_ID)
                                           .changeType("MODIFY")
                                           .gitSyncDirection(GIT_TO_HARNESS.toString())
                                           .additionalErrorDetails(gitToHarnessErrorDetails)
                                           .branchName("branchName")
                                           .repositoryName(REPOSITORY_NAME)
                                           .gitConnectorId(SETTING_ID)
                                           .build();
    gitSyncError3.setAppId(CGConstants.GLOBAL_APP_ID);
    final String savedGitSyncError3 = persistence.save(gitSyncError3);

    final GitSyncError gitSyncError4 = GitSyncError.builder()
                                           .yamlFilePath("Setup/index101112.yaml")
                                           .accountId(ACCOUNT_ID)
                                           .gitSyncDirection(GIT_TO_HARNESS.toString())
                                           .changeType("MODIFY")
                                           .additionalErrorDetails(gitToHarnessErrorDetails)
                                           .branchName("branchName")
                                           .repositoryName(REPOSITORY_NAME)
                                           .gitConnectorId(SETTING_ID)
                                           .build();
    gitSyncError4.setAppId("appid1");

    persistence.save(gitSyncError4);

    final GitSyncError gitSyncError5 = GitSyncError.builder()
                                           .yamlFilePath("Setup/index131415.yaml")
                                           .accountId(ACCOUNT_ID)
                                           .gitSyncDirection(GIT_TO_HARNESS.toString())
                                           .changeType("MODIFY")
                                           .additionalErrorDetails(gitToHarnessErrorDetails)
                                           .branchName("branchName")
                                           .repositoryName(REPOSITORY_NAME)
                                           .gitConnectorId(SETTING_ID)
                                           .build();
    gitSyncError5.setStatus(GitSyncErrorStatus.EXPIRED);
    persistence.save(gitSyncError5);

    final GitSyncError gitSyncError6 =
        GitSyncError.builder()
            .yamlFilePath("Setup/index789456.yaml")
            .accountId(ACCOUNT_ID)
            .gitSyncDirection(HARNESS_TO_GIT.toString())
            .changeType("MODIFY")
            .additionalErrorDetails(HarnessToGitErrorDetails.builder().fullSyncPath(true).build())
            .branchName("branchName")
            .repositoryName(REPOSITORY_NAME)
            .gitConnectorId(SETTING_ID)
            .build();
    gitSyncError6.setAppId(CGConstants.GLOBAL_APP_ID);
    persistence.save(gitSyncError6);

    final GitSyncError gitSyncError7 =
        GitSyncError.builder()
            .yamlFilePath("Setup/index789456565.yaml")
            .accountId(ACCOUNT_ID)
            .gitSyncDirection(HARNESS_TO_GIT.toString())
            .changeType("MODIFY")
            .additionalErrorDetails(HarnessToGitErrorDetails.builder().fullSyncPath(false).build())
            .branchName("branchName")
            .repositoryName(REPOSITORY_NAME)
            .gitConnectorId(SETTING_ID)
            .build();
    gitSyncError7.setAppId(CGConstants.GLOBAL_APP_ID);
    persistence.save(gitSyncError7);

    final long _30_days_millis = System.currentTimeMillis() - Duration.ofDays(30).toMillis();

    final List<GitSyncError> activeGitToHarnessSyncErrors = gitSyncErrorService.getActiveGitToHarnessSyncErrors(
        ACCOUNT_ID, SETTING_ID, "branchName", REPOSITORY_NAME, _30_days_millis);

    assertThat(activeGitToHarnessSyncErrors.stream().map(GitSyncError::getUuid))
        .contains(savedGitSyncError1, savedGitSyncError2, savedGitSyncError3);
    assertThat(activeGitToHarnessSyncErrors).hasSize(3);
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void test_discardGitSyncErrorsForGivenIds() {
    GitToHarnessErrorDetails gitToHarnessErrorDetails =
        GitToHarnessErrorDetails.builder().gitCommitId("gitCommitId").yamlContent("yamlContent").build();
    final GitSyncError gitSyncError = GitSyncError.builder()
                                          .yamlFilePath("yamlFilePath")
                                          .additionalErrorDetails(gitToHarnessErrorDetails)
                                          .accountId(accountId)
                                          .build();

    persistence.save(gitSyncError);
    gitSyncErrorService.deleteGitSyncErrorAndLogFileActivity(
        Arrays.asList(gitSyncError.getUuid()), GitFileActivity.Status.DISCARDED, accountId);
    assertThat(persistence.get(GitSyncError.class, gitSyncError.getUuid())).isEqualTo(null);
  }

  private void verifyGitErrorDetails(GitSyncError gitSyncError, String errorMessage) {
    assertThat(gitSyncError).isNotNull();
    assertThat(gitSyncError.getAccountId()).isEqualTo(accountId);
    assertThat(gitSyncError.getYamlFilePath()).isEqualTo(yamlFilePath);
    assertThat(gitSyncError.getFailureReason()).isEqualTo(errorMessage);
  }

  private void verifyGitConnectorDetails(GitSyncError gitSyncError, String gitConnectorId, String branchName) {
    assertThat(gitSyncError.getGitConnectorId()).isEqualTo(gitConnectorId);
    assertThat(gitSyncError.getBranchName()).isEqualTo(branchName);
  }

  private void verifyGitConnectorDetails(
      GitSyncError gitSyncError, String gitConnectorId, String branchName, String repositoryName) {
    verifyGitConnectorDetails(gitSyncError, gitConnectorId, branchName);
    assertThat(gitSyncError.getRepositoryName()).isEqualTo(repositoryName);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_AddingNewErrorUsingUpsertHarnessToGitError() {
    // Inserting a new error
    GitFileChange gitFileChange = aGitFileChange()
                                      .withFilePath(yamlFilePath)
                                      .withAccountId(accountId)
                                      .withChangeType(ADD)
                                      .withYamlGitConfig(yamlGitConfig.toDTO())
                                      .build();
    gitSyncErrorService.upsertGitSyncErrors(gitFileChange, errorMessage, false, false);
    GitSyncError gitSyncError =
        persistence.createQuery(GitSyncError.class).filter(GitSyncErrorKeys.yamlFilePath, yamlFilePath).get();

    verifyGitErrorDetails(gitSyncError, errorMessage);
    assertThat(gitSyncError.getGitSyncDirection()).isEqualTo(HARNESS_TO_GIT.toString());
    assertThat(gitSyncError.getChangeType()).isEqualTo("ADD");
    HarnessToGitErrorDetails harnessToGitErrorDetails =
        (HarnessToGitErrorDetails) gitSyncError.getAdditionalErrorDetails();
    assertThat(harnessToGitErrorDetails.isFullSyncPath()).isFalse();
    verifyGitConnectorDetails(gitSyncError, gitConnectorId, branchName);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void test_AddingNewErrorUsingUpsertHarnessToGitErrorAccount() {
    // Inserting a new error
    GitFileChange gitFileChange = aGitFileChange()
                                      .withFilePath(yamlFilePath)
                                      .withAccountId(accountId)
                                      .withChangeType(ADD)
                                      .withYamlGitConfig(yamlGitConfigAccount.toDTO())
                                      .build();
    gitSyncErrorService.upsertGitSyncErrors(gitFileChange, errorMessage, false, false);
    GitSyncError gitSyncError =
        persistence.createQuery(GitSyncError.class).filter(GitSyncErrorKeys.yamlFilePath, yamlFilePath).get();

    verifyGitErrorDetails(gitSyncError, errorMessage);
    assertThat(gitSyncError.getGitSyncDirection()).isEqualTo(HARNESS_TO_GIT.toString());
    assertThat(gitSyncError.getChangeType()).isEqualTo("ADD");
    HarnessToGitErrorDetails harnessToGitErrorDetails =
        (HarnessToGitErrorDetails) gitSyncError.getAdditionalErrorDetails();
    assertThat(harnessToGitErrorDetails.isFullSyncPath()).isFalse();
    verifyGitConnectorDetails(gitSyncError, gitConnectorId, branchName, repositoryName);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_UpdatingErrorUsingUpsertHarnessToGitError() {
    // Inserting a new error
    GitSyncError gitSyncError = GitSyncError.builder()
                                    .gitSyncDirection(HARNESS_TO_GIT.toString())
                                    .failureReason(errorMessage)
                                    .yamlFilePath(yamlFilePath)
                                    .changeType("ADD")
                                    .accountId(accountId)
                                    .branchName(branchName)
                                    .gitConnectorId(gitConnectorId)
                                    .build();
    yamlGitConfig.setUuid("uuid");
    persistence.save(gitSyncError);
    GitFileChange gitFileChange = aGitFileChange()
                                      .withFilePath(yamlFilePath)
                                      .withAccountId(accountId)
                                      .withChangeType(MODIFY)
                                      .withYamlGitConfig(newYamlGitConfig.toDTO())
                                      .build();
    gitSyncErrorService.upsertGitSyncErrors(gitFileChange, "NewErrorMessage", true, false);
    GitSyncError updatedGitSyncError =
        persistence.createQuery(GitSyncError.class).filter(GitSyncErrorKeys.yamlFilePath, yamlFilePath).get();
    assertThat(updatedGitSyncError).isNotNull();
    verifyGitErrorDetails(updatedGitSyncError, "NewErrorMessage");
    assertThat(updatedGitSyncError.getGitSyncDirection()).isEqualTo(HARNESS_TO_GIT.toString());
    assertThat(updatedGitSyncError.getChangeType()).isEqualTo("MODIFY");
    verifyGitConnectorDetails(updatedGitSyncError, newGitConnectorId, newBranchName);
    assertThat(updatedGitSyncError.getYamlContent()).isEqualTo(null);
    HarnessToGitErrorDetails harnessToGitErrorDetails =
        (HarnessToGitErrorDetails) updatedGitSyncError.getAdditionalErrorDetails();
    assertThat(harnessToGitErrorDetails.isFullSyncPath()).isTrue();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void test_UpdatingErrorUsingUpsertHarnessToGitErrorAccount() {
    // Inserting a new error
    GitSyncError gitSyncError = GitSyncError.builder()
                                    .gitSyncDirection(HARNESS_TO_GIT.toString())
                                    .failureReason(errorMessage)
                                    .yamlFilePath(yamlFilePath)
                                    .changeType("ADD")
                                    .accountId(accountId)
                                    .branchName(branchName)
                                    .gitConnectorId(gitConnectorId)
                                    .build();
    yamlGitConfig.setUuid("uuid");
    persistence.save(gitSyncError);
    GitFileChange gitFileChange = aGitFileChange()
                                      .withFilePath(yamlFilePath)
                                      .withAccountId(accountId)
                                      .withChangeType(MODIFY)
                                      .withYamlGitConfig(newYamlGitConfig.toDTO())
                                      .build();
    gitSyncErrorService.upsertGitSyncErrors(gitFileChange, "NewErrorMessage", true, false);
    GitSyncError updatedGitSyncError =
        persistence.createQuery(GitSyncError.class).filter(GitSyncErrorKeys.yamlFilePath, yamlFilePath).get();
    assertThat(updatedGitSyncError).isNotNull();
    verifyGitErrorDetails(updatedGitSyncError, "NewErrorMessage");
    assertThat(updatedGitSyncError.getGitSyncDirection()).isEqualTo(HARNESS_TO_GIT.toString());
    assertThat(updatedGitSyncError.getChangeType()).isEqualTo("MODIFY");
    verifyGitConnectorDetails(updatedGitSyncError, newGitConnectorId, newBranchName);
    assertThat(updatedGitSyncError.getYamlContent()).isEqualTo(null);
    HarnessToGitErrorDetails harnessToGitErrorDetails =
        (HarnessToGitErrorDetails) updatedGitSyncError.getAdditionalErrorDetails();
    assertThat(harnessToGitErrorDetails.isFullSyncPath()).isTrue();
  }

  private void verifyGitToHarnessDetails(
      GitToHarnessErrorDetails gitToHarnessErrorDetails, String gitCommitId, String yamlContent) {
    assertThat(gitToHarnessErrorDetails.getGitCommitId()).isEqualTo(gitCommitId);
    assertThat(gitToHarnessErrorDetails.getYamlContent()).isEqualTo(yamlContent);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_AddingNewErrorUsingUpsertGitToHarnessError() {
    // Inserting a new error
    GitFileChange gitFileChange = aGitFileChange()
                                      .withFilePath(yamlFilePath)
                                      .withAccountId(accountId)
                                      .withChangeType(ADD)
                                      .withYamlGitConfig(yamlGitConfig.toDTO())
                                      .withFileContent(yamlContent)
                                      .withCommitId(previousCommitId)
                                      .build();
    gitSyncErrorService.upsertGitSyncErrors(gitFileChange, errorMessage, false, true);
    GitSyncError gitSyncError =
        persistence.createQuery(GitSyncError.class).filter(GitSyncErrorKeys.yamlFilePath, yamlFilePath).get();

    verifyGitErrorDetails(gitSyncError, errorMessage);
    assertThat(gitSyncError.getGitSyncDirection()).isEqualTo(GIT_TO_HARNESS.toString());
    assertThat(gitSyncError.getChangeType()).isEqualTo("ADD");
    verifyGitConnectorDetails(gitSyncError, gitConnectorId, branchName);
    GitToHarnessErrorDetails gitToHarnessErrorDetails =
        (GitToHarnessErrorDetails) gitSyncError.getAdditionalErrorDetails();
    verifyGitToHarnessDetails(gitToHarnessErrorDetails, previousCommitId, yamlContent);
    assertThat(gitToHarnessErrorDetails.getPreviousErrors()).isNull();
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_UpdatingErrorUsingUpsertGitToHarnessError() {
    // Inserting a new error
    GitSyncError gitSyncError =
        GitSyncError.builder()
            .gitSyncDirection(GIT_TO_HARNESS.toString())
            .failureReason(errorMessage)
            .yamlFilePath(yamlFilePath)
            .changeType("ADD")
            .additionalErrorDetails(GitToHarnessErrorDetails.builder().gitCommitId(previousCommitId).build())
            .accountId(accountId)
            .branchName(branchName)
            .gitConnectorId(gitConnectorId)
            .build();
    yamlGitConfig.setUuid("uuid");
    persistence.save(gitSyncError);
    GitFileChange gitFileChange = aGitFileChange()
                                      .withFilePath(yamlFilePath)
                                      .withAccountId(accountId)
                                      .withChangeType(MODIFY)
                                      .withChangeFromAnotherCommit(false)
                                      .withYamlGitConfig(newYamlGitConfig.toDTO())
                                      .build();
    gitSyncErrorService.upsertGitSyncErrors(gitFileChange, "NewErrorMessage", false, true);
    GitSyncError updatedGitSyncError =
        persistence.createQuery(GitSyncError.class).filter(GitSyncErrorKeys.yamlFilePath, yamlFilePath).get();
    assertThat(updatedGitSyncError).isNotNull();
    verifyGitErrorDetails(updatedGitSyncError, "NewErrorMessage");
    assertThat(updatedGitSyncError.getGitSyncDirection()).isEqualTo(GIT_TO_HARNESS.toString());
    assertThat(updatedGitSyncError.getChangeType()).isEqualTo("MODIFY");
    verifyGitConnectorDetails(updatedGitSyncError, newGitConnectorId, newBranchName);
    assertThat(updatedGitSyncError.getYamlContent()).isEqualTo(null);
    GitToHarnessErrorDetails gitToHarnessErrorDetails =
        (GitToHarnessErrorDetails) updatedGitSyncError.getAdditionalErrorDetails();
    assertThat(gitToHarnessErrorDetails.getPreviousErrors().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void test_UpdatingErrorUsingUpsertGitToHarnessErrorAccount() {
    // Inserting a new error
    GitSyncError gitSyncError =
        GitSyncError.builder()
            .gitSyncDirection(GIT_TO_HARNESS.toString())
            .failureReason(errorMessage)
            .yamlFilePath(yamlFilePath)
            .changeType("ADD")
            .additionalErrorDetails(GitToHarnessErrorDetails.builder().gitCommitId(previousCommitId).build())
            .accountId(accountId)
            .branchName(branchName)
            .repositoryName(repositoryName)
            .gitConnectorId(gitConnectorId)
            .build();
    persistence.save(gitSyncError);
    GitFileChange gitFileChange = aGitFileChange()
                                      .withFilePath(yamlFilePath)
                                      .withAccountId(accountId)
                                      .withChangeType(MODIFY)
                                      .withChangeFromAnotherCommit(false)
                                      .withYamlGitConfig(newYamlGitConfigAccount.toDTO())
                                      .build();
    gitSyncErrorService.upsertGitSyncErrors(gitFileChange, "NewErrorMessage", false, true);
    GitSyncError updatedGitSyncError =
        persistence.createQuery(GitSyncError.class).filter(GitSyncErrorKeys.yamlFilePath, yamlFilePath).get();
    assertThat(updatedGitSyncError).isNotNull();
    verifyGitErrorDetails(updatedGitSyncError, "NewErrorMessage");
    assertThat(updatedGitSyncError.getGitSyncDirection()).isEqualTo(GIT_TO_HARNESS.toString());
    assertThat(updatedGitSyncError.getChangeType()).isEqualTo("MODIFY");
    verifyGitConnectorDetails(updatedGitSyncError, newGitConnectorId, newBranchName, newRepositoryName);
    assertThat(updatedGitSyncError.getYamlContent()).isEqualTo(null);
    GitToHarnessErrorDetails gitToHarnessErrorDetails =
        (GitToHarnessErrorDetails) updatedGitSyncError.getAdditionalErrorDetails();
    assertThat(gitToHarnessErrorDetails.getPreviousErrors().size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_fetchGitConnectivityIssues() {
    String branchName = "branchName";
    String errorMessage = "errorMessage";
    String settingName = "Setting Attribute";
    SettingAttribute settingAttribute = aSettingAttribute()
                                            .withAccountId(accountId)
                                            .withCategory(CONNECTOR)
                                            .withName(settingName)
                                            .withValue(GitConfig.builder().branch(branchName).build())
                                            .build();
    String connectorId = persistence.save(settingAttribute);

    Alert gitConnectionAlert = Alert.builder()
                                   .accountId(accountId)
                                   .status(AlertStatus.Open)
                                   .type(AlertType.GitConnectionError)
                                   .title(errorMessage)
                                   .alertData(GitConnectionErrorAlert.builder()
                                                  .message(errorMessage)
                                                  .branchName(branchName)
                                                  .accountId(accountId)
                                                  .gitConnectorId(connectorId)
                                                  .repositoryName(REPOSITORY_NAME)
                                                  .build())
                                   .build();
    persistence.save(gitConnectionAlert);
    persistence.save(Alert.builder()
                         .accountId(accountId)
                         .status(AlertStatus.Open)
                         .type(AlertType.GitConnectionError)
                         .title(errorMessage)
                         .alertData(GitConnectionErrorAlert.builder()
                                        .message(errorMessage)
                                        .branchName(branchName)
                                        .accountId(accountId)
                                        .gitConnectorId("random_connector_id")
                                        .repositoryName(REPOSITORY_NAME)
                                        .build())
                         .build());

    PageRequest<GitProcessingError> req = aPageRequest().build();
    List<GitProcessingError> gitErrors = gitSyncErrorService.fetchGitConnectivityIssues(req, accountId);
    assertThat(gitErrors).isNotEmpty();
    assertThat(gitErrors.size()).isEqualTo(2);
    GitProcessingError error1 = gitErrors.get(0);
    GitProcessingError error2 = gitErrors.get(1);
    if (null == error1.getRepositoryName()) {
      error1 = gitErrors.get(1);
      error2 = gitErrors.get(0);
    }
    assertThat(error1.getGitConnectorId()).isEqualTo(connectorId);
    assertThat(error1.getBranchName()).isEqualTo(branchName);
    assertThat(error1.getRepositoryName()).isEqualTo(REPOSITORY_NAME);
    assertThat(error1.getAccountId()).isEqualTo(accountId);
    assertThat(error1.getConnectorName()).isEqualTo(settingName);
    assertThat(error1.getMessage()).isEqualTo(errorMessage);

    assertThat(error2.getConnectorName()).isNull();
    assertThat(error2.getBranchName()).isNull();
    assertThat(error2.getRepositoryName()).isNull();

    verify(gitConfigHelperService).createRepositoryInfo(any(GitConfig.class), anyString());
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_getTotalGitCommitsWithErrors() {
    final GitToHarnessErrorDetails gitSyncErrorDetails =
        GitToHarnessErrorDetails.builder().gitCommitId("commitId").previousErrors(Collections.emptyList()).build();
    final GitSyncError gitSyncError = GitSyncError.builder()
                                          .accountId(accountId)
                                          .gitSyncDirection(GIT_TO_HARNESS.name())
                                          .yamlFilePath("filePath")
                                          .additionalErrorDetails(gitSyncErrorDetails)
                                          .build();
    gitSyncError.setAppId(appId);
    persistence.save(gitSyncError);
    int count = gitSyncErrorService.getTotalGitCommitsWithErrors(accountId, appId);
    assertThat(count).isEqualTo(1);
  }
}
