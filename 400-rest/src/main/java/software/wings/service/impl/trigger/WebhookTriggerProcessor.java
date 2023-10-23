/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.validation.Validator.notNullCheck;
import static io.harness.waiter.OrchestrationNotifyEventListener.ORCHESTRATION;

import static software.wings.beans.trigger.TriggerExecution.WEBHOOK_EVENT_DETAILS_BRANCH_NAMES_KEY;
import static software.wings.beans.trigger.TriggerExecution.WEBHOOK_EVENT_DETAILS_BRANCH_NAME_KEY;
import static software.wings.beans.trigger.TriggerExecution.WEBHOOK_EVENT_DETAILS_GIT_CONNECTOR_ID_KEY;
import static software.wings.beans.trigger.TriggerExecution.WEBHOOK_EVENT_DETAILS_WEBHOOK_SOURCE_KEY;
import static software.wings.utils.GitUtilsManager.fetchCompleteGitRepoUrl;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.waiter.WaitNotifyEngine;

import software.wings.beans.GitConfig;
import software.wings.beans.TaskType;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.TriggerCondition;
import software.wings.beans.trigger.TriggerExecution;
import software.wings.beans.trigger.TriggerExecution.Status;
import software.wings.beans.trigger.TriggerExecution.WebhookEventDetails;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.trigger.request.TriggerDeploymentNeededRequest;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.trigger.TriggerExecutionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Criteria;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(CDC)
@Singleton
@ValidateOnExecution
@Slf4j
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class WebhookTriggerProcessor {
  public static final int TRIGGER_TASK_TIMEOUT = 30;

  @Inject private TriggerExecutionService triggerExecutionService;
  @Inject private SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject private DelegateService delegateService;
  @Inject private WaitNotifyEngine waitNotifyEngine;
  @Inject private AppService appService;
  @Inject private WingsPersistence wingsPersistence;

  public TriggerExecution fetchLastExecutionForContentChanged(Trigger trigger) {
    WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) trigger.getCondition();
    Query<TriggerExecution> query = wingsPersistence.createQuery(TriggerExecution.class)
                                        .disableValidation()
                                        .filter(TriggerExecution.APP_ID_KEY2, trigger.getAppId())
                                        .filter(TriggerExecution.TRIGGER_ID_KEY, trigger.getUuid())
                                        .filter(TriggerExecution.WEBHOOK_TOKEN_KEY, trigger.getWebHookToken())
                                        .filter(TriggerExecution.WORKFLOW_ID_KEY, trigger.getWorkflowId());
    List<Criteria> criterias = new ArrayList<>();
    criterias.add(query.criteria(WEBHOOK_EVENT_DETAILS_BRANCH_NAME_KEY).equal(webHookTriggerCondition.getBranchName()));
    criterias.add(
        query.criteria(WEBHOOK_EVENT_DETAILS_BRANCH_NAMES_KEY).equal(webHookTriggerCondition.getBranchName()));
    query.or(criterias.toArray(new Criteria[0]));
    return query.filter(WEBHOOK_EVENT_DETAILS_GIT_CONNECTOR_ID_KEY, webHookTriggerCondition.getGitConnectorId())
        .filter(WEBHOOK_EVENT_DETAILS_WEBHOOK_SOURCE_KEY, webHookTriggerCondition.getWebhookSource().name())
        .field(WorkflowExecutionKeys.status)
        .in(EnumSet.of(Status.RUNNING, Status.SUCCESS))
        .order("-createdAt")
        .get();
  }

  public boolean validateBranchName(Trigger trigger, TriggerExecution triggerExecution) {
    log.info("Validating branch name for the trigger {}", trigger.getUuid());
    WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) trigger.getCondition();
    WebhookEventDetails webhookEventDetails = triggerExecution.getWebhookEventDetails();
    if (webHookTriggerCondition.getBranchName() == null
        || webhookEventDetails.getBranchNames().contains(webHookTriggerCondition.getBranchName())) {
      log.info("Validating branch name completed for the trigger {}", trigger.getUuid());
      return true;
    }
    String msg =
        String.format("WebHook event branch name [%s] does not match with the trigger condition branch name [%s]",
            webhookEventDetails.getBranchNames(), webHookTriggerCondition.getBranchName());
    log.info(msg);
    throw new InvalidRequestException(msg, WingsException.USER);
  }

  public boolean checkFileContentOptionSelected(Trigger trigger) {
    TriggerCondition condition = trigger.getCondition();
    if (condition instanceof WebHookTriggerCondition) {
      WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) condition;
      return webHookTriggerCondition.isCheckFileContentChanged();
    }
    return false;
  }

  public void initiateTriggerContentChangeDelegateTask(
      Trigger trigger, TriggerExecution prevTriggerExecution, TriggerExecution triggerExecution, String appId) {
    triggerExecution.setStatus(Status.RUNNING);

    WebhookEventDetails webhookEventDetails = triggerExecution.getWebhookEventDetails();
    WebhookEventDetails prevWebhookEventDetails = prevTriggerExecution.getWebhookEventDetails();
    // Set Previous Commit Id
    if (webhookEventDetails.getCommitId().equals(prevWebhookEventDetails.getCommitId())) {
      return;
    }
    webhookEventDetails.setPrevCommitId(prevWebhookEventDetails.getCommitId());
    TriggerExecution savedTriggerExecution = triggerExecutionService.save(triggerExecution);

    log.info("Initiating file content change delegate task request");
    String accountId = appService.getAccountIdByAppId(appId);

    TriggerDeploymentNeededRequest triggerDeploymentNeededRequest =
        createTriggerDeploymentNeededRequest(accountId, appId, webhookEventDetails);

    String waitId = generateUuid();
    DelegateTask delegateTask = DelegateTask.builder()
                                    .data(TaskData.builder()
                                              .async(true)
                                              .taskType(TaskType.TRIGGER_TASK.name())
                                              .parameters(new Object[] {triggerDeploymentNeededRequest})
                                              .timeout(TimeUnit.MINUTES.toMillis(TRIGGER_TASK_TIMEOUT))
                                              .build())
                                    .accountId(accountId)
                                    .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, appId)
                                    .waitId(waitId)
                                    .build();

    waitNotifyEngine.waitForAllOn(
        ORCHESTRATION, new TriggerCallback(accountId, appId, savedTriggerExecution.getUuid()), waitId);
    delegateService.queueTaskV2(delegateTask);
    log.info("Issued file content change delegate task request for trigger execution id {}",
        savedTriggerExecution.getUuid());
  }

  private TriggerDeploymentNeededRequest createTriggerDeploymentNeededRequest(
      @NotEmpty String accountId, @NotEmpty String appId, WebhookEventDetails webhookEventDetails) {
    GitConfig gitConfig = settingsService.fetchGitConfigFromConnectorId(webhookEventDetails.getGitConnectorId());
    notNullCheck("Git ConnectorDisconnectHandler was deleted", gitConfig);
    setGitConfigRepoNameAndUrl(gitConfig, webhookEventDetails);

    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(gitConfig, null, null);

    return TriggerDeploymentNeededRequest.builder()
        .accountId(accountId)
        .appId(appId)
        .gitConnectorId(webhookEventDetails.getGitConnectorId())
        .currentCommitId(webhookEventDetails.getCommitId())
        .oldCommitId(webhookEventDetails.getPrevCommitId())
        .branch(webhookEventDetails.getBranchNames().toString())
        .repoName(gitConfig.getRepoName())
        .filePaths(webhookEventDetails.getFilePaths())
        .gitConfig(gitConfig)
        .encryptionDetails(encryptionDetails)
        .build();
  }

  private void setGitConfigRepoNameAndUrl(GitConfig gitConfig, WebhookEventDetails webhookEventDetails) {
    if (GitConfig.UrlType.ACCOUNT == gitConfig.getUrlType()) {
      String repoName = webhookEventDetails.getRepoName();
      gitConfig.setRepoName(repoName);
      gitConfig.setRepoUrl(fetchCompleteGitRepoUrl(gitConfig, repoName));
    }
  }
}
