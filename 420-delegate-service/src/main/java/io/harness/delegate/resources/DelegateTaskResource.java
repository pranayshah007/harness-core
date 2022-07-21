/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.DelegateTaskResponseV2;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.delegate.task.TaskLogContext;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.security.annotations.DelegateAuth;
import io.harness.service.intfc.DelegateTaskService;

import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Api("agent/tasks")
@Path("agent/tasks")
@Produces("application/json")
@Scope(DELEGATE)
@Slf4j
public class DelegateTaskResource {
  private DelegateTaskService delegateTaskService;

  @Inject
  public DelegateTaskResource(DelegateTaskService delegateTaskService) {
    this.delegateTaskService = delegateTaskService;
  }

  @DelegateAuth
  @POST
  @Path("{taskId}/delegates/{delegateId}")
  @Consumes("application/x-kryo")
  @Timed
  @ExceptionMetered
  public void updateTaskResponse(@PathParam("delegateId") String delegateId, @PathParam("taskId") String taskId,
      @QueryParam("accountId") @NotEmpty String accountId, DelegateTaskResponse delegateTaskResponse) {
    try (AutoLogContext ignore1 = new TaskLogContext(taskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore3 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      try {
        delegateTaskService.processDelegateResponse(accountId, delegateId, taskId, delegateTaskResponse);
      } catch (Exception exception) {
        log.error("Error during update task response. delegateId: {}, taskId: {}, delegateTaskResponse: {}.",
            delegateId, taskId, delegateTaskResponse, exception);
      }
    }
  }

  @DelegateAuth
  @POST
  @Path("{taskId}/delegates/{delegateId}/v2")
  @Timed
  @ExceptionMetered
  public void updateTaskResponseV2(@PathParam("delegateId") String delegateId, @PathParam("taskId") String taskId,
      @QueryParam("accountId") @NotEmpty String accountId, DelegateTaskResponseV2 delegateTaskResponseV2) {
    // Convert DelegateTaskResponseV2 to DelegateTaskResponse
    DelegateResponseData responseDataV2 = delegateTaskResponseV2.getDelegateResponseData();
    Class<? extends DelegateResponseData> responseClass = delegateTaskResponseV2.getTaskType().getResponseClass();
    DelegateResponseData responseData = responseClass.cast(responseDataV2);

    DelegateTaskResponse delegateTaskResponse = DelegateTaskResponse.builder()
                                                    .responseCode(delegateTaskResponseV2.getResponseCode())
                                                    .response(responseData)
                                                    .accountId(accountId)
                                                    .build();

    try (AutoLogContext ignore1 = new TaskLogContext(taskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore3 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      try {
        delegateTaskService.processDelegateResponse(accountId, delegateId, taskId, delegateTaskResponse);
      } catch (Exception exception) {
        log.error("Error during update task response. delegateId: {}, taskId: {}, delegateTaskResponse: {}.",
            delegateId, taskId, delegateTaskResponse, exception);
      }
    }
  }
}
