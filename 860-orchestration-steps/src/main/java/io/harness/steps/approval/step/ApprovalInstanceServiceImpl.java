/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.execution.utils.StatusUtils;
import io.harness.repositories.ApprovalInstanceRepository;
import io.harness.servicenow.TicketNG;
import io.harness.steps.approval.step.beans.ApprovalStatus;
import io.harness.steps.approval.step.beans.ApprovalType;
import io.harness.steps.approval.step.custom.beans.CustomApprovalResponseData;
import io.harness.steps.approval.step.custom.entities.CustomApprovalInstance.CustomApprovalInstanceKeys;
import io.harness.steps.approval.step.entities.ApprovalInstance;
import io.harness.steps.approval.step.entities.ApprovalInstance.ApprovalInstanceKeys;
import io.harness.steps.approval.step.harness.HarnessApprovalResponseData;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalAction;
import io.harness.steps.approval.step.harness.beans.HarnessApprovalActivityRequestDTO;
import io.harness.steps.approval.step.harness.entities.HarnessApprovalInstance;
import io.harness.steps.approval.step.jira.beans.JiraApprovalResponseData;
import io.harness.steps.approval.step.servicenow.beans.ServiceNowApprovalResponseData;
import io.harness.tasks.ResponseData;
import io.harness.utils.RetryUtils;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.mongodb.morphia.mapping.Mapper;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(CDC)
@Slf4j
public class ApprovalInstanceServiceImpl implements ApprovalInstanceService {
  private final ApprovalInstanceRepository approvalInstanceRepository;
  private final TransactionTemplate transactionTemplate;
  private final WaitNotifyEngine waitNotifyEngine;
  private final PlanExecutionService planExecutionService;

  private final RetryPolicy<Object> transactionRetryPolicy = RetryUtils.getRetryPolicy("[Retrying] attempt: {}",
      "[Failed] attempt: {}", ImmutableList.of(TransactionException.class), Duration.ofSeconds(1), 3, log);

  @Inject
  public ApprovalInstanceServiceImpl(ApprovalInstanceRepository approvalInstanceRepository,
      TransactionTemplate transactionTemplate, WaitNotifyEngine waitNotifyEngine,
      PlanExecutionService planExecutionService) {
    this.approvalInstanceRepository = approvalInstanceRepository;
    this.transactionTemplate = transactionTemplate;
    this.waitNotifyEngine = waitNotifyEngine;
    this.planExecutionService = planExecutionService;
  }

  @Override
  public ApprovalInstance save(@NotNull ApprovalInstance instance) {
    return approvalInstanceRepository.save(instance);
  }

  @Override
  public ApprovalInstance get(@NotNull String approvalInstanceId) {
    Optional<ApprovalInstance> optional = approvalInstanceRepository.findById(approvalInstanceId);
    if (!optional.isPresent()) {
      throw new InvalidRequestException(String.format("Invalid approval instance id: %s", approvalInstanceId));
    }
    return optional.get();
  }

  @Override
  public HarnessApprovalInstance getHarnessApprovalInstance(@NotNull String approvalInstanceId) {
    ApprovalInstance instance = get(approvalInstanceId);
    if (instance == null || instance.getType() != ApprovalType.HARNESS_APPROVAL) {
      throw new InvalidRequestException(String.format("Invalid harness approval instance id: %s", approvalInstanceId));
    }
    return (HarnessApprovalInstance) instance;
  }

  @Override
  public void delete(@NotNull String approvalInstanceId) {
    approvalInstanceRepository.deleteById(approvalInstanceId);
  }

  @Override
  public void resetNextIterations(@NotNull String approvalInstanceId, List<Long> nextIterations) {
    approvalInstanceRepository.updateFirst(new Query(Criteria.where(Mapper.ID_KEY).is(approvalInstanceId)),
        new Update().set(CustomApprovalInstanceKeys.nextIterations, nextIterations));
  }

  @Override
  public void expireByNodeExecutionId(@NotNull String nodeExecutionId) {
    // Only allow waiting instances to be expired. This is to prevent race condition between instance expiry and
    // instance approval/rejection.
    approvalInstanceRepository.updateFirst(
        new Query(Criteria.where(ApprovalInstanceKeys.nodeExecutionId).is(nodeExecutionId))
            .addCriteria(Criteria.where(ApprovalInstanceKeys.status).is(ApprovalStatus.WAITING)),
        new Update().set(ApprovalInstanceKeys.status, ApprovalStatus.EXPIRED));
  }

  @Override
  public void markExpiredInstances() {
    UpdateResult result = approvalInstanceRepository.updateMulti(
        new Query(Criteria.where(ApprovalInstanceKeys.status).is(ApprovalStatus.WAITING))
            .addCriteria(Criteria.where(ApprovalInstanceKeys.deadline).lt(System.currentTimeMillis())),
        new Update().set(ApprovalInstanceKeys.status, ApprovalStatus.EXPIRED));
    log.info(String.format("No. of approval instances expired: %d", result.getModifiedCount()));
  }

  @Override
  public void finalizeStatus(@NotNull String approvalInstanceId, ApprovalStatus status, String errorMessage) {
    finalizeStatus(approvalInstanceId, status, errorMessage, null);
  }

  @Override
  public void finalizeStatus(@NotNull String approvalInstanceId, ApprovalStatus status) {
    finalizeStatus(approvalInstanceId, status, null, null);
  }

  @Override
  public void finalizeStatus(@NotNull String approvalInstanceId, ApprovalStatus status, TicketNG ticketNG) {
    finalizeStatus(approvalInstanceId, status, null, ticketNG);
  }

  @Override
  public void finalizeStatus(
      @NotNull String approvalInstanceId, ApprovalStatus status, String errorMessage, TicketNG ticketNG) {
    // Only allow waiting instances to be approved or rejected. This is to prevent race condition between instance
    // expiry and instance approval/rejection.
    Update update = new Update().set(ApprovalInstanceKeys.status, status);
    if (errorMessage != null) {
      update.set(ApprovalInstanceKeys.errorMessage, errorMessage);
    }
    ApprovalInstance instance = approvalInstanceRepository.updateFirst(
        new Query(Criteria.where(Mapper.ID_KEY).is(approvalInstanceId))
            .addCriteria(Criteria.where(ApprovalInstanceKeys.status).is(ApprovalStatus.WAITING)),
        update);

    if (status.isFinalStatus()) {
      ResponseData responseData;
      switch (instance.getType()) {
        case JIRA_APPROVAL:
          responseData = JiraApprovalResponseData.builder().instanceId(approvalInstanceId).build();
          break;
        case SERVICENOW_APPROVAL:
          responseData = ServiceNowApprovalResponseData.builder().instanceId(approvalInstanceId).build();
          break;
        case CUSTOM_APPROVAL:
          responseData = CustomApprovalResponseData.builder().instanceId(approvalInstanceId).ticket(ticketNG).build();
          break;
        default:
          responseData = null;
      }
      waitNotifyEngine.doneWith(approvalInstanceId, responseData);
    }
    updatePlanStatus(instance);
  }

  @Override
  public HarnessApprovalInstance addHarnessApprovalActivity(@NotNull String approvalInstanceId,
      @NotNull EmbeddedUser user, @NotNull @Valid HarnessApprovalActivityRequestDTO request) {
    HarnessApprovalInstance instance =
        doTransaction(status -> addHarnessApprovalActivityInTransaction(approvalInstanceId, user, request));
    if (instance.getStatus().isFinalStatus()) {
      waitNotifyEngine.doneWith(
          instance.getId(), HarnessApprovalResponseData.builder().approvalInstanceId(instance.getId()).build());
    }
    updatePlanStatus(instance);
    return instance;
  }

  private HarnessApprovalInstance addHarnessApprovalActivityInTransaction(@NotNull String approvalInstanceId,
      @NotNull EmbeddedUser user, @NotNull @Valid HarnessApprovalActivityRequestDTO request) {
    HarnessApprovalInstance instance = fetchWaitingHarnessApproval(approvalInstanceId);
    if (instance.hasExpired()) {
      throw new InvalidRequestException("Harness approval instance has already expired");
    }

    if (request.getAction() == HarnessApprovalAction.REJECT) {
      instance.setStatus(ApprovalStatus.REJECTED);
    } else {
      int newCount = (instance.getApprovalActivities() == null ? 0 : instance.getApprovalActivities().size()) + 1;
      instance.setStatus(
          (newCount >= instance.getApprovers().getMinimumCount()) ? ApprovalStatus.APPROVED : ApprovalStatus.WAITING);
    }
    instance.addApprovalActivity(user, request);
    return approvalInstanceRepository.save(instance);
  }

  private HarnessApprovalInstance fetchWaitingHarnessApproval(String approvalInstanceId) {
    HarnessApprovalInstance instance = getHarnessApprovalInstance(approvalInstanceId);
    if (instance.getStatus() == ApprovalStatus.EXPIRED) {
      throw new InvalidRequestException("Harness approval instance has already expired");
    }
    if (instance.getStatus() != ApprovalStatus.WAITING) {
      throw new InvalidRequestException(
          String.format("Harness approval instance has already completed. Status: %s", instance.getStatus()));
    }
    return instance;
  }

  private void updatePlanStatus(ApprovalInstance instance) {
    if (instance == null || instance.getStatus() == ApprovalStatus.WAITING) {
      return;
    }

    // Update plan status after the completion of the approval step.
    Ambiance ambiance = instance.getAmbiance();
    Status planStatus = planExecutionService.calculateStatusExcluding(
        ambiance.getPlanExecutionId(), AmbianceUtils.obtainCurrentRuntimeId(ambiance));
    if (!StatusUtils.isFinalStatus(planStatus)) {
      planExecutionService.updateStatus(ambiance.getPlanExecutionId(), planStatus);
    }
  }

  private <T> T doTransaction(TransactionCallback<T> callback) {
    return Failsafe.with(transactionRetryPolicy).get(() -> transactionTemplate.execute(callback));
  }
}
