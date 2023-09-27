/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.delegate.task.shell.ShellScriptTaskNG.COMMAND_UNIT;
import static io.harness.springdata.PersistenceUtils.DEFAULT_RETRY_POLICY;

import static java.util.Objects.isNull;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EmbeddedUser;
import io.harness.data.structure.EmptyPredicate;
import io.harness.engine.executions.plan.PlanExecutionService;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.jira.JiraIssueNG;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.NGLogCallback;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.repositories.ApprovalInstanceRepository;
import io.harness.servicenow.ServiceNowFieldValueNG;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.servicenow.misc.TicketNG;
import io.harness.steps.StepSpecTypeConstants;
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
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance;
import io.harness.steps.approval.step.jira.entities.JiraApprovalInstance.JiraApprovalInstanceKeys;
import io.harness.steps.approval.step.servicenow.beans.ServiceNowApprovalResponseData;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance;
import io.harness.steps.approval.step.servicenow.entities.ServiceNowApprovalInstance.ServiceNowApprovalInstanceKeys;
import io.harness.tasks.ResponseData;
import io.harness.waiter.WaitNotifyEngine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import dev.morphia.mapping.Mapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
@Slf4j
public class ApprovalInstanceServiceImpl implements ApprovalInstanceService {
  private final ApprovalInstanceRepository approvalInstanceRepository;
  private final TransactionTemplate transactionTemplate;
  private final WaitNotifyEngine waitNotifyEngine;
  private final PlanExecutionService planExecutionService;
  private final LogStreamingStepClientFactory logStreamingStepClientFactory;
  private static final int MAX_BATCH_SIZE = 500;
  private static final String SERVICENOW_STATE_FIELD = "state";
  private static final String JIRA_STATUS_FIELD = "Status";
  private static final Set<String> approvalStepSpecTypeConstants =
      new HashSet<>(Arrays.asList(StepSpecTypeConstants.HARNESS_APPROVAL, StepSpecTypeConstants.SERVICENOW_APPROVAL,
          StepSpecTypeConstants.JIRA_APPROVAL, StepSpecTypeConstants.CUSTOM_APPROVAL));
  private static final ApprovalType[] approvalsWithDelegateTasksInPolling =
      new ApprovalType[] {ApprovalType.SERVICENOW_APPROVAL, ApprovalType.CUSTOM_APPROVAL, ApprovalType.JIRA_APPROVAL};

  @Inject private PmsEngineExpressionService pmsEngineExpressionService;

  @Inject
  public ApprovalInstanceServiceImpl(ApprovalInstanceRepository approvalInstanceRepository,
      TransactionTemplate transactionTemplate, WaitNotifyEngine waitNotifyEngine,
      PlanExecutionService planExecutionService, LogStreamingStepClientFactory logStreamingStepClientFactory) {
    this.approvalInstanceRepository = approvalInstanceRepository;
    this.transactionTemplate = transactionTemplate;
    this.waitNotifyEngine = waitNotifyEngine;
    this.planExecutionService = planExecutionService;
    this.logStreamingStepClientFactory = logStreamingStepClientFactory;
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
  public List<ApprovalInstance> getApprovalInstancesByExecutionId(@NotEmpty String planExecutionId,
      @Valid ApprovalStatus approvalStatus, @Valid ApprovalType approvalType, String nodeExecutionId) {
    if (isEmpty(planExecutionId)) {
      throw new InvalidRequestException("PlanExecutionId cannot be empty");
    }

    Criteria criteria = Criteria.where(ApprovalInstanceKeys.planExecutionId).is(planExecutionId);

    if (!isNull(approvalStatus)) {
      criteria.and(ApprovalInstanceKeys.status).is(approvalStatus);
    } else {
      criteria.and(ApprovalInstanceKeys.status).in(Arrays.asList(ApprovalStatus.values()));
    }

    if (!isNull(approvalType)) {
      criteria.and(ApprovalInstanceKeys.type).is(approvalType);
    } else {
      criteria.and(ApprovalInstanceKeys.type).in(Arrays.asList(ApprovalType.values()));
    }

    if (EmptyPredicate.isNotEmpty(nodeExecutionId)) {
      criteria.and(ApprovalInstanceKeys.nodeExecutionId).is(nodeExecutionId);
    }
    // uses planExecutionId_status_type_nodeExecutionId if nodeExecutionId is absent
    // uses nodeExecutionId if nodeExecutionId is present
    List<ApprovalInstance> approvalInstances = approvalInstanceRepository.findAll(criteria);

    log.info("No. of approval instances fetched in getApprovalInstancesByExecutionId: {}", approvalInstances.size());
    return approvalInstances;
  }

  @Override
  public HarnessApprovalInstance getHarnessApprovalInstance(@NotNull String approvalInstanceId) {
    ApprovalInstance instance = get(approvalInstanceId);
    if (instance == null) {
      throw new InvalidRequestException(String.format("Invalid harness approval instance id: %s", approvalInstanceId));
    } else if (instance.getType() != ApprovalType.HARNESS_APPROVAL) {
      throw new InvalidRequestException(String.format(
          "operation not permitted for %s type of approval step, it is supported only for Harness Approval Step",
          instance.getType()));
    }
    return (HarnessApprovalInstance) instance;
  }

  @Override
  public Optional<ApprovalInstance> findLatestApprovalInstanceByPlanExecutionIdAndType(
      @NotEmpty String planExecutionId, @NotNull ApprovalType approvalType) {
    if (isEmpty(planExecutionId)) {
      throw new InvalidArgumentsException("PlanExecutionId cannot be null or empty");
    }
    if (approvalType == null) {
      throw new InvalidArgumentsException("ApprovalType cannot be null");
    }

    Query approvalInstancesByPlanExecutionIdAndTypeQuery =
        query(where(ApprovalInstanceKeys.planExecutionId).is(planExecutionId))
            .addCriteria(where(ApprovalInstanceKeys.type).in(approvalType))
            .with(Sort.by(Sort.Direction.DESC, ApprovalInstanceKeys.createdAt))
            .limit(1);

    List<ApprovalInstance> approvalInstances =
        approvalInstanceRepository.find(approvalInstancesByPlanExecutionIdAndTypeQuery);
    return isEmpty(approvalInstances) ? Optional.empty() : Optional.ofNullable(approvalInstances.get(0));
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
  public void abortByNodeExecutionId(@NotNull String nodeExecutionId) {
    // Only allow waiting instances to be aborted. This is to prevent race condition between
    // instance expiry/aborted and instance approval/rejection.
    approvalInstanceRepository.updateFirst(
        new Query(Criteria.where(ApprovalInstanceKeys.nodeExecutionId).is(nodeExecutionId))
            .addCriteria(Criteria.where(ApprovalInstanceKeys.status).is(ApprovalStatus.WAITING)),
        new Update().set(ApprovalInstanceKeys.status, ApprovalStatus.ABORTED));
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
  }

  @Override
  public HarnessApprovalInstance addHarnessApprovalActivity(@NotNull String approvalInstanceId,
      @NotNull EmbeddedUser user, @NotNull @Valid HarnessApprovalActivityRequestDTO request) {
    final String instanceId = new String(approvalInstanceId);
    synchronized (instanceId) {
      HarnessApprovalInstance instance =
          doTransaction(status -> addHarnessApprovalActivityInTransaction(approvalInstanceId, user, request));
      return instance;
    }
  }

  @Override
  public HarnessApprovalInstance addHarnessApprovalActivityV2(@NotNull String approvalInstanceId,
      @NotNull EmbeddedUser user, @NotNull @Valid HarnessApprovalActivityRequestDTO request, boolean shouldCloseStep) {
    HarnessApprovalInstance instance = addHarnessApprovalActivity(approvalInstanceId, user, request);

    if (request.getAction() == HarnessApprovalAction.APPROVE) {
      rejectPreviousExecutionsV2(instance, user);
    }

    if (shouldCloseStep) {
      closeHarnessApprovalStep(instance);
    }
    return instance;
  }

  @Override
  public void closeHarnessApprovalStep(HarnessApprovalInstance instance) {
    if (instance.getStatus().isFinalStatus()) {
      waitNotifyEngine.doneWith(
          instance.getId(), HarnessApprovalResponseData.builder().approvalInstanceId(instance.getId()).build());
    }
  }

  @Override
  public void rejectPreviousExecutions(
      @NotNull String approvalInstanceId, @NotNull EmbeddedUser user, boolean unauthorized, Ambiance ambiance) {
    NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
    String pipelineUrl = pmsEngineExpressionService.renderExpression(ambiance, "<+pipeline.executionUrl>", true);
    if (unauthorized) {
      logCallback.saveExecutionLog(String.format(
          "Unable to auto reject previous execution with approval id %s as the user does not have the access to reject this execution",
          approvalInstanceId));
      return;
    }
    String comment = "Rejected due to approval of " + pipelineUrl;
    HarnessApprovalActivityRequestDTO harnessApprovalActivityRequest =
        HarnessApprovalActivityRequestDTO.builder().comments(comment).action(HarnessApprovalAction.REJECT).build();
    HarnessApprovalInstance instance =
        addHarnessApprovalActivity(approvalInstanceId, user, harnessApprovalActivityRequest);
    closeHarnessApprovalStep(instance);
  }

  @Override
  public List<String> findAllPreviousWaitingApprovals(String accountId, String orgId, String projectId,
      @NotEmpty String pipelineId, String approvalKey, Ambiance ambiance, Long createdAt) {
    Criteria criteria = Criteria.where("accountId").is(accountId);
    if (!isNull(orgId)) {
      criteria.and("orgIdentifier").is(orgId);
    }
    if (!isNull(projectId)) {
      criteria.and("projectIdentifier").is(projectId);
    }
    criteria.and("pipelineIdentifier").is(pipelineId);
    if (EmptyPredicate.isNotEmpty(approvalKey)) {
      criteria.and("approvalKey").is(approvalKey);
    } else {
      return new ArrayList<>();
    }
    criteria.and(ApprovalInstanceKeys.status).is(ApprovalStatus.WAITING);
    criteria.and("isAutoRejectEnabled").is(true);
    criteria.and(ApprovalInstanceKeys.createdAt).lt(createdAt);

    List<ApprovalInstance> approvalInstances = approvalInstanceRepository.findAll(criteria);
    approvalInstances = filterOnService(approvalInstances, ambiance);
    log.info("No. of approval instances fetched waiting for approval that will be auto rejected : {}",
        approvalInstances.size());

    return approvalInstances.stream()
        .filter(instance -> !instance.hasExpired())
        .map(ApprovalInstance::getId)
        .collect(Collectors.toList());
  }

  private List<ApprovalInstance> filterOnService(List<ApprovalInstance> approvalInstances, Ambiance currAmbiance) {
    List<ApprovalInstance> filteredApprovalInstances = new ArrayList<>();
    String newServiceIdentifier =
        pmsEngineExpressionService.renderExpression(currAmbiance, "<+service.identifier>", true);
    approvalInstances.forEach(approvalInstance -> {
      String oldServiceIdentifier =
          pmsEngineExpressionService.renderExpression(approvalInstance.getAmbiance(), "<+service.identifier>", true);
      if (oldServiceIdentifier.equals(newServiceIdentifier)) {
        filteredApprovalInstances.add(approvalInstance);
      }
    });
    return filteredApprovalInstances;
  }

  @Override
  public boolean isNodeExecutionOfApprovalStepType(NodeExecution nodeExecution) {
    if (isNull(nodeExecution) || isNull(nodeExecution.getStepType())) {
      return false;
    }
    if (!StepCategory.STEP.equals(nodeExecution.getStepType().getStepCategory())) {
      return false;
    }
    String stepType = nodeExecution.getStepType().getType();
    return approvalStepSpecTypeConstants.contains(stepType);
  }

  /**
   * Deletes all approval Instances for the given nodeExecutionIds
   * in a batch operation
   * @param nodeExecutionIds
   */
  @Override
  public void deleteByNodeExecutionIds(@NotNull Set<String> nodeExecutionIds) {
    if (isEmpty(nodeExecutionIds)) {
      return;
    }
    List<String> nodeExecutionIdsList = new ArrayList<>(nodeExecutionIds);
    List<List<String>> batchNodeExecutionIdsList = Lists.partition(nodeExecutionIdsList, MAX_BATCH_SIZE);
    batchNodeExecutionIdsList.forEach(
        batchNodeExecutionIds -> deleteByNodeExecutionIdsInternal(new HashSet<>(batchNodeExecutionIds)));
  }

  private void deleteByNodeExecutionIdsInternal(Set<String> nodeExecutionIds) {
    Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> {
      // uses nodeExecutionId_1 idx
      long deletedCount = approvalInstanceRepository.deleteAllByNodeExecutionIdIn(nodeExecutionIds);
      log.info("Successfully deleted {} approvalInstances based on nodeExecutionIds", deletedCount);
      return null;
    });
  }

  @Override
  public void updateTicketFieldsInServiceNowApprovalInstance(
      @NotNull ServiceNowApprovalInstance approvalInstance, @NotNull ServiceNowTicketNG ticketNG) {
    if (ticketNG == null || ticketNG.getFields() == null || EmptyPredicate.isEmpty(ticketNG.getFields())) {
      log.warn("Skipping updating ticket fields in ServiceNow approval instance as ticket fields are not present");
      return;
    }
    Map<String, ServiceNowFieldValueNG> serviceNowTicketFields = ticketNG.getFields();
    if (!serviceNowTicketFields.containsKey(SERVICENOW_STATE_FIELD)) {
      log.warn("Skipping updating ticket fields in ServiceNow approval instance as {} field is not present",
          SERVICENOW_STATE_FIELD);
      return;
    }
    ServiceNowFieldValueNG stateFieldValue = serviceNowTicketFields.get(SERVICENOW_STATE_FIELD);

    Map<String, ServiceNowFieldValueNG> updatedApprovalInstanceTicketFields;
    if (isEmpty(approvalInstance.getTicketFields())) {
      updatedApprovalInstanceTicketFields = new HashMap<>();
      updatedApprovalInstanceTicketFields.put(SERVICENOW_STATE_FIELD, stateFieldValue);
    } else if (approvalInstance.getTicketFields().containsKey(SERVICENOW_STATE_FIELD)
        && approvalInstance.getTicketFields().get(SERVICENOW_STATE_FIELD).equals(stateFieldValue)) {
      // updated value present in approval instance
      return;
    } else {
      updatedApprovalInstanceTicketFields = approvalInstance.getTicketFields();
      updatedApprovalInstanceTicketFields.put(SERVICENOW_STATE_FIELD, stateFieldValue);
    }

    Update update = new Update().set(ServiceNowApprovalInstanceKeys.ticketFields, updatedApprovalInstanceTicketFields);
    // it only makes sense to update ticket fields for instances in waiting state
    approvalInstanceRepository.updateFirst(
        new Query(Criteria.where(Mapper.ID_KEY).is(approvalInstance.getId()))
            .addCriteria(Criteria.where(ApprovalInstanceKeys.status).is(ApprovalStatus.WAITING)),
        update);
  }

  @Override
  public void updateTicketFieldsInJiraApprovalInstance(
      @NotNull JiraApprovalInstance approvalInstance, @NotNull JiraIssueNG ticketNG) {
    if (ticketNG == null || ticketNG.getFields() == null || EmptyPredicate.isEmpty(ticketNG.getFields())) {
      log.warn("Skipping updating ticket fields in Jira approval instance as ticket fields are not present");
      return;
    }
    Map<String, Object> jiraTicketFields = ticketNG.getFields();
    if (!jiraTicketFields.containsKey(JIRA_STATUS_FIELD)) {
      log.warn(
          "Skipping updating ticket fields in Jira approval instance as {} field is not present", JIRA_STATUS_FIELD);
      return;
    }
    Object statusFieldValue = jiraTicketFields.get(JIRA_STATUS_FIELD);

    Map<String, Object> updatedApprovalInstanceTicketFields;
    if (isEmpty(approvalInstance.getTicketFields())) {
      updatedApprovalInstanceTicketFields = new HashMap<>();
      updatedApprovalInstanceTicketFields.put(JIRA_STATUS_FIELD, statusFieldValue);
    } else if (approvalInstance.getTicketFields().containsKey(JIRA_STATUS_FIELD)
        && approvalInstance.getTicketFields().get(JIRA_STATUS_FIELD).equals(statusFieldValue)) {
      // updated value present in approval instance
      return;
    } else {
      updatedApprovalInstanceTicketFields = approvalInstance.getTicketFields();
      updatedApprovalInstanceTicketFields.put(JIRA_STATUS_FIELD, statusFieldValue);
    }

    Update update = new Update().set(JiraApprovalInstanceKeys.ticketFields, updatedApprovalInstanceTicketFields);
    // it only makes sense to update ticket fields for instances in waiting state
    approvalInstanceRepository.updateFirst(
        new Query(Criteria.where(Mapper.ID_KEY).is(approvalInstance.getId()))
            .addCriteria(Criteria.where(ApprovalInstanceKeys.status).is(ApprovalStatus.WAITING)),
        update);
  }
  @Override
  public void updateLatestDelegateTaskId(@NotNull String approvalInstanceId, String latestDelegateTaskId) {
    if (StringUtils.isBlank(approvalInstanceId)) {
      log.warn("Skipping updating latest delegate task id as empty approval id received");
      return;
    }

    if (StringUtils.isBlank(latestDelegateTaskId)) {
      log.warn("Skipping updating latest delegate task id in approval instance as empty delegate task id received");
      return;
    }

    // update task id in approval instance to latest delegate task id
    Update update = new Update().set(ServiceNowApprovalInstanceKeys.latestDelegateTaskId, latestDelegateTaskId);
    // it only makes sense to update delegate task id for instances in waiting state
    // if the instance is aborted/expired etc., and a task is queued that task id will not be updated.
    approvalInstanceRepository.updateFirst(
        new Query(Criteria.where(Mapper.ID_KEY).is(approvalInstanceId))
            .addCriteria(Criteria.where(ApprovalInstanceKeys.status).is(ApprovalStatus.WAITING))
            .addCriteria(
                Criteria.where(ApprovalInstanceKeys.type).in(Arrays.asList(approvalsWithDelegateTasksInPolling))),
        update);
  }

  @Override
  public void updateKeyListInKeyValueCriteria(@NotNull String approvalInstanceId, String keyListInKeyValueCriteria) {
    if (StringUtils.isBlank(approvalInstanceId)) {
      log.warn("Skipping updating keyListInKeyValueCriteria as empty approval id received");
      return;
    }

    if (isNull(keyListInKeyValueCriteria)) {
      log.warn(
          "Skipping updating keyListInKeyValueCriteria in approval instance as null keyListInKeyValueCriteria received");
      return;
    }

    // update keyListInKeyValueCriteria in approval instance to filter fields
    Update update = new Update().set(JiraApprovalInstanceKeys.keyListInKeyValueCriteria, keyListInKeyValueCriteria);
    // it only makes sense to update keyListInKeyValueCriteria for Jira instances in waiting state
    // if the instance is aborted/expired etc., and a task is queued then keyListInKeyValueCriteria will not be updated.
    approvalInstanceRepository.updateFirst(
        new Query(Criteria.where(Mapper.ID_KEY).is(approvalInstanceId))
            .addCriteria(Criteria.where(ApprovalInstanceKeys.status).is(ApprovalStatus.WAITING))
            .addCriteria(Criteria.where(ApprovalInstanceKeys.type).is(ApprovalType.JIRA_APPROVAL)),
        update);
  }

  public void rejectPreviousExecutionsV2(HarnessApprovalInstance instance, EmbeddedUser user) {
    if (instance.getIsAutoRejectEnabled() == null || !instance.getIsAutoRejectEnabled()) {
      return;
    }
    List<String> rejectedApprovalIds = findAllPreviousWaitingApprovals(instance.getAccountId(),
        instance.getOrgIdentifier(), instance.getProjectIdentifier(), instance.getPipelineIdentifier(),
        instance.getApprovalKey(), instance.getAmbiance(), instance.getCreatedAt());
    rejectedApprovalIds.forEach(id -> rejectPreviousExecutions(id, user, false, instance.getAmbiance()));
    NGLogCallback logCallback =
        new NGLogCallback(logStreamingStepClientFactory, instance.getAmbiance(), COMMAND_UNIT, false);
    logCallback.saveExecutionLog("Successfully rejected all previous executions waiting for approval on this step");
  }

  @VisibleForTesting
  HarnessApprovalInstance addHarnessApprovalActivityInTransaction(@NotNull String approvalInstanceId,
      @NotNull EmbeddedUser user, @NotNull @Valid HarnessApprovalActivityRequestDTO request) {
    try {
      HarnessApprovalInstance instance = fetchWaitingHarnessApproval(approvalInstanceId);
      Ambiance ambiance = instance.getAmbiance();
      NGLogCallback logCallback = new NGLogCallback(logStreamingStepClientFactory, ambiance, COMMAND_UNIT, false);
      if (instance.hasExpired()) {
        throw new InvalidRequestException("Harness approval instance has already expired");
      }

      if (request.isAutoApprove()) {
        instance.setStatus(ApprovalStatus.APPROVED);
      } else if (request.getAction() == HarnessApprovalAction.REJECT) {
        instance.setStatus(ApprovalStatus.REJECTED);
      } else {
        int newCount = (instance.getApprovalActivities() == null ? 0 : instance.getApprovalActivities().size()) + 1;
        instance.setStatus(
            (newCount >= instance.getApprovers().getMinimumCount()) ? ApprovalStatus.APPROVED : ApprovalStatus.WAITING);
      }
      instance.addApprovalActivity(user, request);
      logCallback.saveExecutionLog(String.format(
          "Request to %s this approval received by %s with comments:{%s} and inputs:%s", request.getAction(),
          StringUtils.isBlank(user.getName()) ? user.getName() : user.getEmail(), request.getComments(),
          isEmpty(request.getApproverInputs())
              ? "[]"
              : request.getApproverInputs()
                    .stream()
                    .map(input -> String.format("( %s : %s)", input.getName(), input.getValue()))
                    .collect(Collectors.toList())));
      return approvalInstanceRepository.save(instance);
    } catch (Exception e) {
      log.error("failed to add approval activity", e);
      throw new InvalidRequestException(String.format("Unable to add Harness Approval Activity : %s", e.getMessage()));
    }
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

  private <T> T doTransaction(TransactionCallback<T> callback) {
    return Failsafe.with(DEFAULT_RETRY_POLICY).get(() -> transactionTemplate.execute(callback));
  }
}
