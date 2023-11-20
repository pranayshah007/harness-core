/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.internal;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.perpetualtask.PerpetualTaskType.CONTAINER_INSTANCE_SYNC;

import static java.lang.String.format;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.PerpetualTaskUnassignedReason;
import io.harness.persistence.HPersistence;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import software.wings.service.impl.PerpetualTaskCapabilityCheckResponse;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PerpetualTaskValidationCallback implements OldNotifyCallback {
  private String accountId;
  private String perpetualTaskId;
  private String delegateTaskId;
  private static final String CONTAINER_TYPE_KEY = "containerType";

  public PerpetualTaskValidationCallback(String accountId, String perpetualTaskId, String delegateTaskId) {
    this.accountId = accountId;
    this.perpetualTaskId = perpetualTaskId;
    this.delegateTaskId = delegateTaskId;
  }

  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private HPersistence hPersistence;
  @Inject private PerpetualTaskRecordDao perpetualTaskRecordDao;

  @Override
  public void notify(Map<String, ResponseData> response) {
    log.debug("PT validation task for {} response {} for account {}", perpetualTaskId, response, accountId);
    DelegateResponseData notifyResponseData = (DelegateResponseData) response.values().iterator().next();
    handleResponse(notifyResponseData);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    log.debug("PT validation task failed for {} with response {} for account {}", perpetualTaskId, response, accountId);
    DelegateResponseData notifyResponseData = (DelegateResponseData) response.values().iterator().next();
    handleResponse(notifyResponseData);
  }

  private boolean excludeFromErrorSetting(PerpetualTaskRecord taskRecord) {
    // @TODO to Remove once all delegates upgraded (after June 2024)
    // avoid PT marking as invalid of type container instance sync which collects active service counts.
    // clientContext.clientParams.containerType"  = ""
    if (!CONTAINER_INSTANCE_SYNC.equals(taskRecord.getPerpetualTaskType())) {
      return false;
    }
    return taskRecord.getClientContext() != null && isNotEmpty(taskRecord.getClientContext().getClientParams())
        && taskRecord.getClientContext().getClientParams().get(CONTAINER_TYPE_KEY) != null
        && isEmpty(taskRecord.getClientContext().getClientParams().get(CONTAINER_TYPE_KEY));
  }

  private void handleResponse(DelegateResponseData response) {
    PerpetualTaskRecord taskRecord = hPersistence.get(PerpetualTaskRecord.class, perpetualTaskId);
    Preconditions.checkNotNull(taskRecord, "no perpeetual task found with id " + perpetualTaskId);

    if (response instanceof ErrorNotifyResponseData && !excludeFromErrorSetting(taskRecord)) {
      log.info("Perpetual validation task {} failed, unable to assign delegate.", taskRecord.getUuid());
      if (CONTAINER_INSTANCE_SYNC.equals(taskRecord.getPerpetualTaskType())) {
        perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
            PerpetualTaskUnassignedReason.PT_TASK_FAILED, PerpetualTaskState.TASK_INVALID,
            ((ErrorNotifyResponseData) response).getErrorMessage());
      } else {
        perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
            PerpetualTaskUnassignedReason.PT_TASK_FAILED, PerpetualTaskState.TASK_NON_ASSIGNABLE,
            ((ErrorNotifyResponseData) response).getErrorMessage());
      }
      return;
    }

    if (response instanceof DelegateTaskNotifyResponseData) {
      if (response instanceof PerpetualTaskCapabilityCheckResponse) {
        boolean isAbleToExecutePerpetualTask =
            ((PerpetualTaskCapabilityCheckResponse) response).isAbleToExecutePerpetualTask();
        if (!isAbleToExecutePerpetualTask) {
          perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
              PerpetualTaskUnassignedReason.PT_TASK_FAILED, PerpetualTaskState.TASK_NON_ASSIGNABLE,
              "Unable to execute task");
          return;
        }
      }
      if (((DelegateTaskNotifyResponseData) response).getDelegateMetaInfo() != null) {
        String delegateId = ((DelegateTaskNotifyResponseData) response).getDelegateMetaInfo().getId();
        log.info("Delegate {} is assigned to the inactive {} perpetual task with id={}.", delegateId,
            taskRecord.getPerpetualTaskType(), perpetualTaskId);
        perpetualTaskService.appointDelegate(
            taskRecord.getAccountId(), perpetualTaskId, delegateId, System.currentTimeMillis());
      } else {
        log.info(
            "Perpetual validation task {} unable to assign delegate due to missing DelegateMetaInfo.", delegateTaskId);
        perpetualTaskService.markStateAndNonAssignedReason_OnAssignTryCount(taskRecord,
            PerpetualTaskUnassignedReason.NO_DELEGATE_AVAILABLE, PerpetualTaskState.TASK_NON_ASSIGNABLE,
            "Unable to assign to any delegates");
      }
    } else if (response instanceof RemoteMethodReturnValueData) {
      perpetualTaskRecordDao.updateTaskStateNonAssignableReason(perpetualTaskId,
          PerpetualTaskUnassignedReason.PT_TASK_FAILED, taskRecord.getAssignTryCount(),
          PerpetualTaskState.TASK_NON_ASSIGNABLE);
      log.error("Invalid request exception: ", ((RemoteMethodReturnValueData) response).getException());
    } else {
      log.error(format("Assignment for perpetual task id=%s got unexpected delegate response %s", perpetualTaskId,
          response.toString()));
    }
  }
}
