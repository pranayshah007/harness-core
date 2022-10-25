/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.exception.utils.ExceptionUtils;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import software.wings.helpers.ext.trigger.response.TriggerDeploymentNeededResponse;
import software.wings.helpers.ext.trigger.response.TriggerResponse;
import software.wings.service.intfc.TriggerService;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
public class TriggerCallback implements OldNotifyCallback {
  private String accountId;
  private String appId;
  private String triggerExecutionId;

  @Inject private TriggerService triggerService;

  public TriggerCallback(String accountId, String appId, String triggerExecutionId) {
    this.accountId = accountId;
    this.appId = appId;
    this.triggerExecutionId = triggerExecutionId;
  }

  @Override
  public void notify(Map<String, ResponseData> response) {
    log.info("Trigger command response {} for account {}", response, accountId);

    DelegateResponseData notifyResponseData = (DelegateResponseData) response.values().iterator().next();
    TriggerResponse triggerResponse = new TriggerResponse();
    triggerResponse.setExecutionStatus(ExecutionStatus.FAILED);
    if (notifyResponseData instanceof ErrorNotifyResponseData) {
      triggerResponse.setErrorMsg(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage());
    } else if (notifyResponseData instanceof RemoteMethodReturnValueData) {
      triggerResponse.setErrorMsg(
          ExceptionUtils.getMessage(((RemoteMethodReturnValueData) notifyResponseData).getException()));
    } else if (!(notifyResponseData instanceof TriggerDeploymentNeededResponse)) {
      triggerResponse.setErrorMsg("Unknown Response from delegate");
    } else {
      triggerResponse = (TriggerDeploymentNeededResponse) notifyResponseData;
    }
    triggerService.handleTriggerTaskResponse(appId, triggerExecutionId, triggerResponse);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    log.info("Trigger command request failed for account {} and for trigger executionId {} with response {}", accountId,
        triggerExecutionId, response);
    DelegateResponseData notifyResponseData = (DelegateResponseData) response.values().iterator().next();
    TriggerResponse triggerResponse = new TriggerResponse();
    triggerResponse.setExecutionStatus(ExecutionStatus.FAILED);
    if (notifyResponseData instanceof ErrorNotifyResponseData) {
      triggerResponse.setErrorMsg(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage());
    } else {
      triggerResponse.setErrorMsg("Unknown error occurred while verifying file content changed");
    }

    triggerService.handleTriggerTaskResponse(appId, triggerExecutionId, triggerResponse);
  }
}
