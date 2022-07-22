package io.harness.delegate.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.SendTaskProgressRequest;
import io.harness.delegate.SendTaskProgressResponse;
import io.harness.delegate.SendTaskStatusRequest;
import io.harness.delegate.SendTaskStatusResponse;
import io.harness.delegate.TaskProgressRequest;
import io.harness.delegate.TaskProgressResponse;
import io.harness.security.annotations.DelegateAuth;
import io.harness.service.intfc.TaskProgressService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.dropwizard.jersey.protobuf.ProtocolBufferMediaType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Api("/agent/delegates/task-progress")
@Path("/agent/delegates/task-progress")
@Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
@Slf4j
@OwnedBy(DEL)
public class TaskProgressResource {
  @Inject TaskProgressService taskProgressService;

  @PUT
  @Path("/progress-update")
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "Send task progress", nickname = "sendTaskProgressUpdate")
  public Response sendTaskProgressUpdate(
      SendTaskProgressRequest sendTaskProgressRequest, @QueryParam("accountId") String accountId) {
    SendTaskProgressResponse sendTaskProgressResponse = taskProgressService.sendTaskProgress(sendTaskProgressRequest);
    return Response.ok(sendTaskProgressResponse).build();
  }

  @PUT
  @Path("/progress")
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "update task progress", nickname = "taskProgress")
  public Response taskProgress(TaskProgressRequest taskProgressRequest, @QueryParam("accountId") String accountId) {
    TaskProgressResponse taskProgressResponse = taskProgressService.taskProgress(taskProgressRequest);
    return Response.ok(taskProgressResponse).build();
  }

  @PUT
  @Path("/status")
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "Send task status", nickname = "sendTaskStatus")
  public Response sendTaskStatus(
      SendTaskStatusRequest sendTaskStatusRequest, @QueryParam("accountId") String accountId) {
    SendTaskStatusResponse sendTaskStatusResponse = taskProgressService.sendTaskStatus(sendTaskStatusRequest);
    return Response.ok(sendTaskStatusResponse).build();
  }
}
