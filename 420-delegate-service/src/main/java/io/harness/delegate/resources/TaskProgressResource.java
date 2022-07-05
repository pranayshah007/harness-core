package io.harness.delegate.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.SendTaskProgressRequest;
import io.harness.delegate.SendTaskStatusRequest;
import io.harness.delegate.TaskProgressRequest;
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
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Api("/agent/delegates/task-progress")
@Path("/agent/delegates/task-progress")
@Produces({ProtocolBufferMediaType.APPLICATION_PROTOBUF, ProtocolBufferMediaType.APPLICATION_PROTOBUF_JSON})
@Slf4j
@OwnedBy(DEL)
public class TaskProgressResource {
  @Inject private TaskProgressService taskProgressService;

  @PUT
  @Path("/progress-update")
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "", nickname = "")
  public Response sendTaskProgressUpdate(SendTaskProgressRequest sendTaskProgressRequest) {
    taskProgressService.sendTaskProgress(sendTaskProgressRequest);
    return Response.ok().build();
  }

  @PUT
  @Path("/progress")
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "", nickname = "")
  public Response taskProgress(TaskProgressRequest taskProgressRequest) {
    taskProgressService.taskProgress(taskProgressRequest);
    return Response.ok().build();
  }

  @PUT
  @Path("/status")
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "", nickname = "")
  public Response sendTaskStatus(SendTaskStatusRequest sendTaskStatusRequest) {
    taskProgressService.sendTaskStatus(sendTaskStatusRequest);
    return Response.ok().build();
  }
}
