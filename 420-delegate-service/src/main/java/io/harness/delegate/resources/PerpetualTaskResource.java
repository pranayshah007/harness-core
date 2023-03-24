/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.HeartbeatRequest;
import io.harness.perpetualtask.HeartbeatResponse;
import io.harness.perpetualtask.PerpetualTaskAssignDetails;
import io.harness.perpetualtask.PerpetualTaskContextResponse;
import io.harness.perpetualtask.PerpetualTaskFailureRequest;
import io.harness.perpetualtask.PerpetualTaskFailureResponse;
import io.harness.perpetualtask.PerpetualTaskListResponse;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.security.annotations.DelegateAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.dropwizard.jersey.protobuf.ProtocolBufferMediaType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@Api("/agent/delegates/perpetual-task")
@Path("/agent/delegates/perpetual-task")
@Produces(ProtocolBufferMediaType.APPLICATION_PROTOBUF)
@Slf4j
@OwnedBy(DEL)
public class PerpetualTaskResource {
  @Inject private PerpetualTaskService perpetualTaskService;

  @GET
  @Path("/list")
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "Get list of perpetual task assigned to delegate", nickname = "perpetualTaskList")
  public Response perpetualTaskList(
      @QueryParam("delegateId") String delegateId, @QueryParam("accountId") String accountId) {
    List<PerpetualTaskAssignDetails> perpetualTaskAssignDetails =
        perpetualTaskService.listAssignedTasks(delegateId, accountId);
    PerpetualTaskListResponse perpetualTaskListResponse =
        PerpetualTaskListResponse.newBuilder().addAllPerpetualTaskAssignDetails(perpetualTaskAssignDetails).build();
    return Response.ok(perpetualTaskListResponse).build();
  }

  @GET
  @Path("/context")
  @Timed
  @DelegateAuth
  @ExceptionMetered
  @ApiOperation(value = "Get perpetual task context for given perpetual task", nickname = "perpetualTaskContext")
  public Response perpetualTaskContext(@QueryParam("taskId") String taskId, @QueryParam("accountId") String accountId) {
    PerpetualTaskContextResponse response =
        PerpetualTaskContextResponse.newBuilder()
            .setPerpetualTaskContext(perpetualTaskService.perpetualTaskContext(taskId, false))
            .build();
    return Response.ok(response).build();
  }

  @GET
  @Path("/context/v2")
  @Timed
  @DelegateAuth
  @ExceptionMetered
  @ApiOperation(value = "Get perpetual task context for given perpetual task", nickname = "perpetualTaskContext")
  public Response perpetualTaskContextV2(
      @QueryParam("taskId") String taskId, @QueryParam("accountId") String accountId) {
    PerpetualTaskContextResponse response =
        PerpetualTaskContextResponse.newBuilder()
            .setPerpetualTaskContext(perpetualTaskService.perpetualTaskContext(taskId, true))
            .build();
    return Response.ok(response).build();
  }

  // TODO: ARPIT remove this deprecated call once DEL-5026 changes are in immutable delegate agent.
  @PUT
  @Path("/heartbeat")
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "Heartbeat recording", nickname = "heartbeat")
  @Deprecated
  public Response heartbeat(@QueryParam("accountId") String accountId, HeartbeatRequest heartbeatRequest) {
    PerpetualTaskResponse perpetualTaskResponse = PerpetualTaskResponse.builder()
                                                      .responseMessage(heartbeatRequest.getResponseMessage())
                                                      .responseCode(heartbeatRequest.getResponseCode())
                                                      .build();
    long heartbeatMillis = HTimestamps.toInstant(heartbeatRequest.getHeartbeatTimestamp()).toEpochMilli();
    perpetualTaskService.triggerCallback(heartbeatRequest.getId(), heartbeatMillis, perpetualTaskResponse);
    return Response.ok(HeartbeatResponse.newBuilder().build()).build();
  }

  @PUT
  @Path("/failure")
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "Record perpetual task failure", nickname = "recordTaskFailure")
  public Response recordTaskFailure(
      @QueryParam("accountId") String accountId, PerpetualTaskFailureRequest perpetualTaskFailureRequest) {
    perpetualTaskService.recordTaskFailure(
        perpetualTaskFailureRequest.getId(), perpetualTaskFailureRequest.getExceptionMessage());
    return Response.ok(PerpetualTaskFailureResponse.newBuilder().build()).build();
  }
}
