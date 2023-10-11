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

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.delegate.InitializeExecutionInfraResponse;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.core.beans.SetupInfraResponse;
import io.harness.delegate.task.tasklogging.ExecutionLogContext;
import io.harness.delegate.task.tasklogging.TaskLogContext;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.executionInfra.ExecutionInfrastructureService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.Subject;
import io.harness.persistence.HPersistence;
import io.harness.security.annotations.DelegateAuth;
import io.harness.service.intfc.DelegateTaskRetryObserver;
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
import org.apache.commons.lang3.tuple.Pair;

@Api("/executions")
@Path("/executions")
@Consumes(MediaType.APPLICATION_JSON)
@Scope(DELEGATE)
@Slf4j
@OwnedBy(DEL)
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CoreDelegateExecutionResource {
  private final DelegateTaskServiceClassic delegateTaskServiceClassic;
  private final ExecutionInfrastructureService executionInfrastructureService;
  private final HPersistence persistence;
  private final DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  private Subject<DelegateTaskRetryObserver> retryObserverSubject = new Subject<>();
  private final RemoteObserverInformer remoteObserverInformer;
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
      if (!optionalDelegateTask.isPresent()) {
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
  @Path("response/{executionId}/executionInfra")
  @Consumes(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
  @Timed
  @ExceptionMetered
  public Response handleSetupExecutionInfraResponse(@QueryParam("delegateId") final String delegateId,
      @PathParam("executionId") final String executionId, @QueryParam("accountId") @NotEmpty final String accountId,
      SetupInfraResponse response) {
    try (AutoLogContext ignore1 = new ExecutionLogContext(executionId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      if (response == null) {
        throw new InvalidArgumentsException(Pair.of("args", "response cannot be null"));
      }
      Query<DelegateTask> taskQuery =
          persistence
              .createQuery(DelegateTask.class, delegateTaskMigrationHelper.isMigrationEnabledForTask(executionId))
              .filter(DelegateTaskKeys.accountId, accountId)
              .filter(DelegateTaskKeys.uuid, executionId);
      DelegateTask task = taskQuery.get();
      if (Objects.isNull(task)) {
        log.error("When processing response from delegate {}, task not found.", delegateId);
        return Response.serverError().build();
      }
      final String uuid =
          executionInfrastructureService.addExecutionInfrastructure(task, delegateId, response.getLocation());

      delegateTaskService.handleResponse(task, taskQuery,
          DelegateTaskResponse.builder()
              .response(InitializeExecutionInfraResponse.builder().executionInfraReferenceId(uuid).build())
              .accountId(task.getAccountId())
              .build());
      return Response.ok().build();
    } catch (final Exception e) {
      log.error(
          "Exception adding new execution infra entry in manager account_id: %s, delegate_id: %s, execution_id: %s.",
          accountId, delegateId, executionId);
      return Response.serverError().build();
    }
  }
}
