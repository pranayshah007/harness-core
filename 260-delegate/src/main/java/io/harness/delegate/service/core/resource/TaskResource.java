/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.resource;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.service.DelegateAgentService;
import io.harness.security.annotations.PublicApi;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("task")
@Path("/task/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@OwnedBy(DEL)
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@PublicApi
public class TaskResource {
  private final DelegateAgentService delegateAgentService;

  @POST
  @Path("/{taskId}/execution-response")
  @ApiOperation(value = "send task execution result")
  public boolean executionResponse(
      @PathParam("taskId") final String taskId, final DelegateTaskResponse executionResponse) {
    delegateAgentService.sendTaskResponse(taskId, executionResponse);
    return true;
  }
}
