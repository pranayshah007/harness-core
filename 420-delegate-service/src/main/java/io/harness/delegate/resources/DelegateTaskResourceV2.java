/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.DelegateTaskResponseV2;
import io.harness.delegate.beans.SerializedResponseData;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.delegate.task.TaskLogContext;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.security.annotations.DelegateAuth;
import io.harness.service.intfc.DelegateTaskService;
import io.harness.tasks.ResponseData;

import software.wings.beans.SerializationFormat;
import software.wings.beans.TaskType;
import software.wings.security.annotations.Scope;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Api("agent/v2/tasks")
@Path("agent/v2/tasks")
@Produces("application/json")
@Scope(DELEGATE)
@Slf4j
public class DelegateTaskResourceV2 {
  private DelegateTaskService delegateTaskService;

  @Inject
  public DelegateTaskResourceV2(DelegateTaskService delegateTaskService) {
    this.delegateTaskService = delegateTaskService;
  }

  @DelegateAuth
  @POST
  @Path("{taskId}/delegates/{delegateId}")
  @Timed
  @ExceptionMetered
  public void updateTaskResponseV2(@PathParam("delegateId") String delegateId, @PathParam("taskId") String taskId,
      @QueryParam("accountId") @NotEmpty String accountId, DelegateTaskResponseV2 delegateTaskResponseV2) {
    TaskType taskType = delegateTaskResponseV2.getTaskType();
    ObjectMapper objectMapper = new ObjectMapper();
    // Convert DelegateTaskResponseV2 to DelegateTaskResponse
    ResponseData responseData = delegateTaskResponseV2.getResponseData();
    byte[] serializedData = null;
    try {
      serializedData = objectMapper.writeValueAsBytes(responseData);
    } catch (JsonProcessingException e) {
      log.error("Could not serialize response data to bytes", e);
    }
    SerializedResponseData serializedResponseData = SerializedResponseData.builder()
                                                        .data(serializedData)
                                                        .taskType(taskType)
                                                        .serializationFormat(SerializationFormat.JSON)
                                                        .build();

    DelegateTaskResponse delegateTaskResponse = DelegateTaskResponse.builder()
                                                    .responseCode(delegateTaskResponseV2.getResponseCode())
                                                    .response(serializedResponseData)
                                                    .taskType(taskType)
                                                    .serializationFormat(SerializationFormat.JSON)
                                                    .accountId(accountId)
                                                    .build();

    try (AutoLogContext ignore1 = new TaskLogContext(taskId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore3 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      try {
        log.info("processing delegate response");
        delegateTaskService.processDelegateResponse(accountId, delegateId, taskId, delegateTaskResponse);
        log.info("done processing delegate response");
      } catch (Exception exception) {
        log.error("Error during update task response. delegateId: {}, taskId: {}, delegateTaskResponse: {}.",
            delegateId, taskId, delegateTaskResponse, exception);
      }
    }
  }
}
