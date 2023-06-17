/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.microservice.NotifyEngineTarget.GENERAL;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.IGOR;
import static io.harness.rule.OwnerRule.ROHIT_KUMAR;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.VARDAN_BANSAL;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.trigger.WebhookSource.AZURE_DEVOPS;
import static software.wings.beans.trigger.WebhookSource.GITHUB;
import static software.wings.service.impl.yaml.YamlGitServiceImpl.PUSH_IF_NOT_HEAD_MAX_RETRY_COUNT;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.exception.GeneralException;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.GitConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.GitConnectionErrorAlert;
import software.wings.beans.yaml.GitFileChange;
import software.wings.service.impl.trigger.WebhookEventUtils;
import software.wings.service.impl.yaml.sync.GitSyncFailureAlertDetails;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.yaml.YamlChangeSetService;
import software.wings.service.intfc.yaml.YamlDirectoryService;
import software.wings.service.intfc.yaml.sync.GitSyncErrorService;
import software.wings.yaml.gitSync.GitWebhookRequestAttributes;
import software.wings.yaml.gitSync.YamlChangeSet;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.ws.rs.core.HttpHeaders;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class YamlGitServiceImplTest extends WingsBaseTest {
  @Inject private HPersistence persistence;

  @Mock private DelegateService delegateService;
  @Mock private AlertService alertService;
  @Mock private SecretManager secretManager;
  @Mock private AccountService mockAccountService;
  @Mock private YamlDirectoryService mockYamlDirectoryService;
  @Mock private HttpHeaders httpHeaders;
  @Mock private WebhookEventUtils webhookEventUtils;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private YamlChangeSetService yamlChangeSetService;
  @Mock private GitSyncErrorService gitSyncErrorService;
  @InjectMocks @Inject private YamlGitServiceImpl yamlGitService;

  private static final String TEST_GIT_REPO_URL = "https://github.com/rathn/SyncTest";
  private static final String TEST_GIT_REPO_USER = "user";
  private static final String TEST_GIT_REPO_PASSWORD = "password";
  private static final String WEBHOOK_TOKEN = "Webhook_Token";
  private static final String GH_PUSH_REQ_FILE =
      "400-rest/src/test/resources/software/wings/service/impl/webhook/github_push_request.json";
  private static final String AZURE_DEVOPS_CODE_PUSH_WEBHOOK =
      "400-rest/src/test/resources/software/wings/service/impl/webhook/azure_code_push_request.json";
  private static final String AZURE_DEVOPS_MERGE_PULL_REQUEST_COMPLETED_WEBHOOK =
      "400-rest/src/test/resources/software/wings/service/impl/webhook/azure_merge_pull_completed_request.json";
  private static final String AZURE_DEVOPS_MERGE_PULL_REQUEST_ACTIVE_WEBHOOK =
      "400-rest/src/test/resources/software/wings/service/impl/webhook/azure_merge_pull_active_request.json";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testCheckForValidNameSyntax() throws Exception {
    List<GitFileChange> gitFileChanges = new ArrayList<>();
    gitFileChanges.add(GitFileChange.Builder.aGitFileChange().withFilePath("Setup/Defaults.yaml").build());
    gitFileChanges.add(
        GitFileChange.Builder.aGitFileChange().withFilePath("Setup/Applications/App1/Index.yaml").build());
    gitFileChanges.add(
        GitFileChange.Builder.aGitFileChange().withFilePath("Setup/Artifact Servers/jenkins.yaml").build());
    gitFileChanges.add(
        GitFileChange.Builder.aGitFileChange().withFilePath("Setup/Verification Providers/NewRelic.yaml").build());
    gitFileChanges.add(GitFileChange.Builder.aGitFileChange()
                           .withFilePath("Setup/Applications/app1/Services/service1/Index.yaml")
                           .build());
    gitFileChanges.add(GitFileChange.Builder.aGitFileChange().withFilePath("Setup/Applications").build());
    gitFileChanges.add(GitFileChange.Builder.aGitFileChange().withFilePath("Setup/Applications/app1/Services").build());
    gitFileChanges.add(GitFileChange.Builder.aGitFileChange().withFilePath("Setup/Verification Providers").build());
    gitFileChanges.add(GitFileChange.Builder.aGitFileChange().withFilePath("Setup/Verification Providers").build());
    gitFileChanges.add(
        GitFileChange.Builder.aGitFileChange()
            .withFilePath("Setup/Applications/App1/Environments/env1/PCF Overrides/Services/SERVICE_NAME/a.yml")
            .build());
    gitFileChanges.add(
        GitFileChange.Builder.aGitFileChange()
            .withFilePath("Setup/Applications/App1/Environments/env1/PCF Overrides/Services/SERVICE_NAME/a.yml")
            .build());
    gitFileChanges.add(
        GitFileChange.Builder.aGitFileChange()
            .withFilePath("Setup/Applications/App1/Environments/env1/PCF Overrides/Services/SERVICE_NAME/Index.yaml")
            .build());

    yamlGitService.ensureValidNameSyntax(gitFileChanges, null);

    try {
      yamlGitService.ensureValidNameSyntax(
          Collections.singletonList(GitFileChange.Builder.aGitFileChange()
                                        .withFilePath("Setup/Applications/app1/Services/service/1/Index.yaml")
                                        .build()),
          YamlChangeSet.builder().build());
      assertThat(false).isTrue();
    } catch (Exception ex) {
      assertThat(ex instanceof WingsException).isTrue();
      assertThat(ex.getMessage().contains(
                     "Invalid entity name, entity can not contain / in the name. Caused invalid file path:"))
          .isTrue();
    }
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void getAllYamlErrorsForAccount() {
    yamlGitService.getAllYamlErrorsForAccount(ACCOUNT_ID);
    verify(mockYamlDirectoryService)
        .traverseDirectory(anyList(), eq(ACCOUNT_ID), any(), anyString(), eq(false), eq(false), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void getAllYamlErrorsForAllAccounts() {
    List<Account> accounts = Arrays.asList(anAccount().withAccountName("Name1").withUuid("AccId1").build(),
        anAccount().withAccountName("Name2").withUuid("AccId2").build());
    doReturn(accounts).when(mockAccountService).list(any());
    yamlGitService.getAllYamlErrorsForAllAccounts();
    verify(mockYamlDirectoryService, times(2)).getDirectory(anyString(), anyString(), anyBoolean(), any());
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testProcessWebhookPost() throws Exception {
    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withAccountId(ACCOUNT_ID)
            .withUuid(SETTING_ID)
            .withName("GitConnector")
            .withValue(GitConfig.builder().accountId(ACCOUNT_ID).webhookToken(WEBHOOK_TOKEN).build())
            .build();

    YamlGitConfig yamlGitConfig =
        YamlGitConfig.builder().accountId(ACCOUNT_ID).gitConnectorId(SETTING_ID).branchName("master").build();

    when(webhookEventUtils.isGitPingEvent(httpHeaders)).thenReturn(false);
    when(webhookEventUtils.obtainWebhookSource(httpHeaders)).thenReturn(GITHUB);
    doNothing().when(webhookEventUtils).validatePushEvent(GITHUB, httpHeaders);
    when(webhookEventUtils.obtainBranchName(any(), any(), any())).thenReturn("master");
    final YamlChangeSet yamlChangeSet = YamlChangeSet.builder().build();
    yamlChangeSet.setUuid("uuid");
    doReturn(yamlChangeSet).when(yamlChangeSetService).save(any(YamlChangeSet.class));
    persistence.save(settingAttribute);
    persistence.save(yamlGitConfig);

    String response = yamlGitService.validateAndQueueWebhookRequest(
        ACCOUNT_ID, WEBHOOK_TOKEN, obtainPayload(GH_PUSH_REQ_FILE), httpHeaders);
    assertThat(response).isEqualTo("Successfully accepted webhook request for processing");
    verify(yamlChangeSetService, times(1)).save(any(YamlChangeSet.class));
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void testProcessAzureDevopsWebhookForCodePushEvent() throws Exception {
    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withAccountId(ACCOUNT_ID)
            .withUuid(SETTING_ID)
            .withName("GitConnector")
            .withValue(GitConfig.builder().accountId(ACCOUNT_ID).webhookToken(WEBHOOK_TOKEN).build())
            .build();

    YamlGitConfig yamlGitConfig =
        YamlGitConfig.builder().accountId(ACCOUNT_ID).gitConnectorId(SETTING_ID).branchName("main").build();

    when(webhookEventUtils.isGitPingEvent(httpHeaders)).thenReturn(false);
    when(webhookEventUtils.obtainWebhookSource(httpHeaders)).thenReturn(AZURE_DEVOPS);
    doNothing().when(webhookEventUtils).validatePushEvent(AZURE_DEVOPS, httpHeaders);
    when(webhookEventUtils.obtainBranchName(any(), any(), any())).thenReturn("main");
    final YamlChangeSet yamlChangeSet = YamlChangeSet.builder().build();
    yamlChangeSet.setUuid("uuid");
    doReturn(yamlChangeSet).when(yamlChangeSetService).save(any(YamlChangeSet.class));
    persistence.save(settingAttribute);
    persistence.save(yamlGitConfig);

    String response = yamlGitService.validateAndQueueWebhookRequest(
        ACCOUNT_ID, WEBHOOK_TOKEN, obtainPayload(AZURE_DEVOPS_CODE_PUSH_WEBHOOK), httpHeaders);
    assertThat(response).isEqualTo("Successfully accepted webhook request for processing");
    verify(yamlChangeSetService, times(1)).save(any(YamlChangeSet.class));
  }

  @Test
  @Owner(developers = VARDAN_BANSAL)
  @Category(UnitTests.class)
  public void testProcessAzureDevOpsWebhookForCodePullRequestMergeEvent() throws Exception {
    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withAccountId(ACCOUNT_ID)
            .withUuid(SETTING_ID)
            .withName("GitConnector")
            .withValue(GitConfig.builder().accountId(ACCOUNT_ID).webhookToken(WEBHOOK_TOKEN).build())
            .build();

    YamlGitConfig yamlGitConfig =
        YamlGitConfig.builder().accountId(ACCOUNT_ID).gitConnectorId(SETTING_ID).branchName("main").build();

    when(webhookEventUtils.isGitPingEvent(httpHeaders)).thenReturn(false);
    when(webhookEventUtils.obtainWebhookSource(httpHeaders)).thenReturn(AZURE_DEVOPS);
    doNothing().when(webhookEventUtils).validatePushEvent(AZURE_DEVOPS, httpHeaders);
    when(webhookEventUtils.obtainBranchName(any(), any(), any())).thenReturn("main");
    final YamlChangeSet yamlChangeSet = YamlChangeSet.builder().build();
    yamlChangeSet.setUuid("uuid");
    doReturn(yamlChangeSet).when(yamlChangeSetService).save(any(YamlChangeSet.class));
    persistence.save(settingAttribute);
    persistence.save(yamlGitConfig);
    when(webhookEventUtils.shouldIgnorePullRequestMergeEventWithActiveStatusFromAzure(any())).thenReturn(true);
    String response = yamlGitService.validateAndQueueWebhookRequest(
        ACCOUNT_ID, WEBHOOK_TOKEN, obtainPayload(AZURE_DEVOPS_MERGE_PULL_REQUEST_ACTIVE_WEBHOOK), httpHeaders);
    assertThat(response).isEqualTo(
        "Skipped processing for merge pull request event since pull request has active status and is still unmerged.");
    verify(yamlChangeSetService, times(0)).save(any(YamlChangeSet.class));

    when(webhookEventUtils.shouldIgnorePullRequestMergeEventWithActiveStatusFromAzure(any())).thenReturn(false);
    response = yamlGitService.validateAndQueueWebhookRequest(
        ACCOUNT_ID, WEBHOOK_TOKEN, obtainPayload(AZURE_DEVOPS_MERGE_PULL_REQUEST_COMPLETED_WEBHOOK), httpHeaders);
    assertThat(response).isEqualTo("Successfully accepted webhook request for processing");
    verify(yamlChangeSetService, times(1)).save(any(YamlChangeSet.class));
  }

  private String obtainPayload(String filePath) throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();

    File file = new File(filePath);
    return FileUtils.readFileToString(file, Charset.defaultCharset());
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_handleGitChangeSet() {
    final GitWebhookRequestAttributes gitWebhookRequestAttributes = GitWebhookRequestAttributes.builder()
                                                                        .gitConnectorId(SETTING_ID)
                                                                        .branchName("branchName")
                                                                        .headCommitId("commitId")
                                                                        .build();
    final YamlChangeSet yamlChangeSet =
        YamlChangeSet.builder().accountId(ACCOUNT_ID).gitWebhookRequestAttributes(gitWebhookRequestAttributes).build();
    yamlChangeSet.setUuid("changesetId");

    SettingAttribute settingAttribute =
        aSettingAttribute()
            .withAccountId(ACCOUNT_ID)
            .withUuid(SETTING_ID)
            .withName("gitconnectorid")
            .withValue(GitConfig.builder().accountId(ACCOUNT_ID).webhookToken(WEBHOOK_TOKEN).build())
            .build();

    YamlGitConfig yamlGitConfig =
        YamlGitConfig.builder().accountId(ACCOUNT_ID).gitConnectorId(SETTING_ID).branchName("branchName").build();
    persistence.save(settingAttribute);
    persistence.save(yamlGitConfig);
    yamlGitService.handleGitChangeSet(yamlChangeSet, ACCOUNT_ID);
    verify(delegateService, times(1)).queueTaskV2(any(DelegateTask.class));
    verify(waitNotifyEngine, times(1)).waitForAllOn(eq(GENERAL), any(GitCommandCallback.class), anyString());
    verify(yamlChangeSetService, times(0))
        .updateStatus(eq(ACCOUNT_ID), eq("changesetId"), any(YamlChangeSet.Status.class));
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testShouldPushOnlyIfHeadSeen() {
    YamlChangeSet yamlChangeSet =
        YamlChangeSet.builder().pushRetryCount(PUSH_IF_NOT_HEAD_MAX_RETRY_COUNT - 1).fullSync(true).build();
    assertThat(yamlGitService.shouldPushOnlyIfHeadSeen(yamlChangeSet, "random")).isFalse();
    assertThat(yamlGitService.shouldPushOnlyIfHeadSeen(YamlChangeSet.builder().build(), "random")).isTrue();
    assertThat(yamlGitService.shouldPushOnlyIfHeadSeen(YamlChangeSet.builder().build(), "")).isFalse();
  }

  @Test(expected = GeneralException.class)
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void testSaveForRepoNameValidationOnAccountLevelConnector() {
    String gitConfig = persistence.save(aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withUuid(SETTING_ID)
                                            .withName("gitconnectorid")
                                            .withValue(GitConfig.builder()
                                                           .urlType(GitConfig.UrlType.ACCOUNT)
                                                           .accountId(ACCOUNT_ID)
                                                           .webhookToken(WEBHOOK_TOKEN)
                                                           .build())
                                            .build());

    yamlGitService.save(YamlGitConfig.builder().gitConnectorId(gitConfig).build(), false);
  }

  @Test(expected = GeneralException.class)
  @Owner(developers = IGOR)
  @Category(UnitTests.class)
  public void testSaveForRepoNameValidationOnRepoLevelConnector() {
    String gitConfig = persistence.save(aSettingAttribute()
                                            .withAccountId(ACCOUNT_ID)
                                            .withUuid(SETTING_ID)
                                            .withName("gitconnectorid")
                                            .withValue(GitConfig.builder()
                                                           .urlType(GitConfig.UrlType.REPO)
                                                           .accountId(ACCOUNT_ID)
                                                           .webhookToken(WEBHOOK_TOKEN)
                                                           .build())
                                            .build());

    yamlGitService.save(
        YamlGitConfig.builder().gitConnectorId(gitConfig).repositoryName("should-not-have-repo-name").build(), false);
  }

  // In some cases delegate throw an exception that don't are showed in UI.
  // SPG-50/PL-18485
  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldRaiseAlertWhenSyncFailureIsGeneralYamlErrorCode() {
    final GitSyncFailureAlertDetails alertDetails = GitSyncFailureAlertDetails.builder()
                                                        .gitConnectorId("gitConnectorId")
                                                        .branchName("master")
                                                        .errorMessage("a short but beautiful error message")
                                                        .repositoryName("harness-core")
                                                        .errorCode(ErrorCode.GENERAL_YAML_ERROR)
                                                        .build();
    yamlGitService.raiseAlertForGitFailure(ACCOUNT_ID, APP_ID, alertDetails);
    verify(alertService)
        .openAlert(eq(ACCOUNT_ID), eq(APP_ID), eq(AlertType.GitConnectionError),
            ArgumentMatchers.notNull(GitConnectionErrorAlert.class));
  }
}
