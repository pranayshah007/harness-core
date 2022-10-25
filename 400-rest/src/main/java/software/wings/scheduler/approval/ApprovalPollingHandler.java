/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler.approval;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.iterator.MongoPersistenceIterator.SchedulingType.REGULAR;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.iterator.PersistenceIteratorFactory;
import io.harness.iterator.PersistenceIteratorFactory.PumpExecutorOptions;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.mongo.iterator.MongoPersistenceIterator;
import io.harness.mongo.iterator.MongoPersistenceIterator.Handler;
import io.harness.mongo.iterator.filter.MorphiaFilterExpander;
import io.harness.mongo.iterator.provider.MorphiaPersistenceProvider;
import io.harness.workers.background.AccountStatusBasedEntityProcessController;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.approval.ApprovalPollingJobEntity;
import software.wings.beans.approval.ApprovalPollingJobEntity.ApprovalPollingJobEntityKeys;
import software.wings.scheduler.ShellScriptApprovalService;
import software.wings.service.impl.JiraHelperService;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.ApprovalPolingService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.servicenow.ServiceNowService;

import com.google.inject.Inject;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class ApprovalPollingHandler implements Handler<ApprovalPollingJobEntity> {
  public static final Duration TARGET_INTERVAL = ofMinutes(1);
  public static final Duration PUMP_INTERVAL = ofSeconds(10);
  @Inject private AccountService accountService;
  @Inject private PersistenceIteratorFactory persistenceIteratorFactory;
  @Inject private JiraHelperService jiraHelperService;
  @Inject private ServiceNowService serviceNowService;
  @Inject private ShellScriptApprovalService shellScriptApprovalService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private ApprovalPolingService approvalPolingService;
  @Inject private MorphiaPersistenceProvider<ApprovalPollingJobEntity> persistenceProvider;

  public void registerIterators() {
    persistenceIteratorFactory.createPumpIteratorWithDedicatedThreadPool(
        PumpExecutorOptions.builder().name("ApprovalPolling").poolSize(5).interval(PUMP_INTERVAL).build(),
        ApprovalPollingHandler.class,
        MongoPersistenceIterator.<ApprovalPollingJobEntity, MorphiaFilterExpander<ApprovalPollingJobEntity>>builder()
            .clazz(ApprovalPollingJobEntity.class)
            .fieldName(ApprovalPollingJobEntityKeys.nextIteration)
            .targetInterval(TARGET_INTERVAL)
            .acceptableNoAlertDelay(ofMinutes(1))
            .handler(this)
            .entityProcessController(new AccountStatusBasedEntityProcessController<>(accountService))
            .schedulingType(REGULAR)
            .persistenceProvider(persistenceProvider)
            .redistribute(true));
  }

  @Override
  public void handle(ApprovalPollingJobEntity entity) {
    try (AutoLogContext ignore1 = new AccountLogContext(entity.getAccountId(), OVERRIDE_ERROR)) {
      log.info(
          "Polling Approval status for approval polling job {} approval type {}", entity, entity.getApprovalType());
    }

    String workflowExecutionId = entity.getWorkflowExecutionId();
    String appId = entity.getAppId();
    WorkflowExecution workflowExecution = workflowExecutionService.getWorkflowExecution(appId, workflowExecutionId);
    if (workflowExecution == null || ExecutionStatus.isFinalStatus(workflowExecution.getStatus())) {
      approvalPolingService.delete(entity.getApprovalId());
      return;
    }

    switch (entity.getApprovalType()) {
      case JIRA:
        jiraHelperService.handleJiraPolling(entity);
        return;
      case SERVICENOW:
        serviceNowService.handleServiceNowPolling(entity);
        return;
      case SHELL_SCRIPT:
        shellScriptApprovalService.handleShellScriptPolling(entity);
        return;
      default:
        throw new InvalidRequestException(
            "No Polling should be required for approval type: " + entity.getApprovalType());
    }
  }
}
