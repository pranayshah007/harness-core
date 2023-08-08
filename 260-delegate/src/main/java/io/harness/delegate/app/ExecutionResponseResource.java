/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.delegate.service.common.ManagerCallHelper;
import io.harness.delegate.service.handlermapping.context.Context;
import io.harness.delegate.task.tasklogging.ExecutionLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.security.annotations.PublicApi;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.io.IOException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("execution")
@Path("/execution")
@OwnedBy(DEL)
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Inject)
@PublicApi
public class ExecutionResponseResource {
  private final DelegateAgentManagerClient managerClient;
  private final Context context;
  private final DelegateConfiguration configuration;

  @POST
  @Consumes("application/x-kryo-v2")
  @Path("/{executionId}/execution-response")
  public boolean executionResponse(
      @PathParam("executionId") final String taskId, final DelegateTaskResponse executionResponse) {
    try (AutoLogContext ignore = new ExecutionLogContext(taskId, OVERRIDE_ERROR)) {
      var call = managerClient.sendTaskStatus(
          context.get(Context.DELEGATE_ID), taskId, configuration.getAccountId(), executionResponse);
      try {
        ManagerCallHelper.executeCallWithRetryableException(call, "sending execution request failed");
      } catch (IOException e) {
        log.error("Fail to send response.");
        return false;
      }
    }
    return true;
  }
}
