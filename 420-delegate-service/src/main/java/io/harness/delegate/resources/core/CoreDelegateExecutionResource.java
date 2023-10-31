/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources.core;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.ResourceType.DELEGATE;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.scheduler.InitializeExecutionInfraResponse;
import io.harness.delegate.core.beans.ResponseCode;
import io.harness.delegate.core.beans.SetupInfraResponse;
import io.harness.delegate.task.tasklogging.ExecutionLogContext;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.executionInfra.ExecutionInfrastructureService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.persistence.HPersistence;
import io.harness.security.annotations.DelegateAuth;
import io.harness.service.intfc.DelegateTaskService;

import software.wings.security.annotations.Scope;
import software.wings.service.intfc.DelegateTaskServiceClassic;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import io.dropwizard.jersey.protobuf.ProtocolBufferMediaType;
import io.swagger.annotations.Api;
import java.util.Objects;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("/executions")
@Path("/executions")
@Consumes(MediaType.APPLICATION_JSON)
@Scope(DELEGATE)
@Slf4j
@OwnedBy(DEL)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CoreDelegateExecutionResource {
  private final DelegateTaskServiceClassic delegateTaskServiceClassic;
  private final ExecutionInfrastructureService infraService;
  private final HPersistence persistence;
  private final DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  private final DelegateTaskService delegateTaskService;

  @DelegateAuth
  @GET
  @Path("payload/{executionId}")
  @Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Timed
  @ExceptionMetered
  public Response acquireRequestPayload(@PathParam("executionId") final String executionId,
      @QueryParam("accountId") @NotEmpty final String accountId,
      @QueryParam("delegateInstanceId") final String delegateInstanceId,
      @QueryParam("delegateId") final String delegateId) {
    try (AutoLogContext ignore1 = new TaskLogContext(executionId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      final var optionalDelegateTask =
          delegateTaskServiceClassic.acquireTask(accountId, delegateId, executionId, delegateInstanceId);
      if (optionalDelegateTask.isEmpty()) {
        return Response.status(Response.Status.NOT_FOUND).build();
      }
      return Response.ok(optionalDelegateTask.get()).build();
    } catch (final Exception e) {
      log.error("Exception serializing task {} data ", executionId, e);
      return Response.serverError().build();
    }
  }

  @DelegateAuth
  @POST
  @Path("response/{executionId}/execution-infra")
  @Consumes(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Timed
  @ExceptionMetered
  public Response handleSetupExecutionInfraResponse(@QueryParam("delegateId") final String delegateId,
      @PathParam("executionId") final String executionId, @QueryParam("accountId") @NotEmpty final String accountId,
      final SetupInfraResponse response) {
    try (AutoLogContext ignore1 = new ExecutionLogContext(executionId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      if (response == null) {
        log.warn("Null response from delegate {} for execution {}", delegateId, executionId);
        return Response.status(BAD_REQUEST).build();
      }

      if (response.getResponseCode() != ResponseCode.RESPONSE_OK) {
        log.error("Error response from delegate {} for execution {}. {}", delegateId, executionId,
            response.getErrorMessage());
        return Response.ok().build();
      }

      final Query<DelegateTask> taskQuery =
          persistence
              .createQuery(DelegateTask.class, delegateTaskMigrationHelper.isMigrationEnabledForTask(executionId))
              .filter(DelegateTaskKeys.accountId, accountId)
              .filter(DelegateTaskKeys.uuid, executionId);
      final DelegateTask task = taskQuery.first();
      if (Objects.isNull(task)) {
        log.error("Task not found when processing infra setup response from delegate {}", delegateId);
        return Response.serverError().build();
      }

      final var updated =
          infraService.updateDelegateInfo(task.getUuid(), delegateId, response.getLocation().getDelegateName());
      if (!updated) {
        log.error("Error updating delegate info for account {} and execution {}", accountId, executionId);
        return Response.serverError().build();
      }

      delegateTaskService.handleResponseV2(task, taskQuery,
          DelegateTaskResponse.builder()
              .response(InitializeExecutionInfraResponse.builder().executionInfraReferenceId(task.getUuid()).build())
              .accountId(task.getAccountId())
              .build());
      return Response.ok().build();
    } catch (final Exception e) {
      log.error("Exception updating execution infra for account {}, with delegate details {}, for execution {}",
          accountId, delegateId, executionId, e);
      return Response.serverError().build();
    }
  }
}
