/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.core.resource;

import com.google.inject.Inject;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.service.DelegateAgentService;
import io.harness.security.annotations.PublicApi;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import static io.harness.annotations.dev.HarnessTeam.DEL;

@Api("task")
@Path("/task")
@Produces(MediaType.APPLICATION_JSON)
@OwnedBy(DEL)
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@PublicApi
public class TaskResource {
  private final DelegateAgentService delegateAgentService;

  @POST
  @Consumes("application/x-kryo-v2")
  @Path("/{taskId}/execution-response")
  public boolean executionResponse(@PathParam("taskId") final String taskId, final DelegateTaskResponse executionResponse) {
    delegateAgentService.sendTaskResponse(taskId, executionResponse);
    return true;
  }
}
