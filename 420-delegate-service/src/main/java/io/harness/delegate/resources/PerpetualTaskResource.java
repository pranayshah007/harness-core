package io.harness.delegate.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.dropwizard.jersey.protobuf.ProtocolBufferMediaType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.HeartbeatRequest;
import io.harness.perpetualtask.HeartbeatResponse;
import io.harness.perpetualtask.PerpetualTaskAssignDetails;
import io.harness.perpetualtask.PerpetualTaskContextRequest;
import io.harness.perpetualtask.PerpetualTaskContextResponse;
import io.harness.perpetualtask.PerpetualTaskListRequest;
import io.harness.perpetualtask.PerpetualTaskListResponse;
import io.harness.perpetualtask.PerpetualTaskResponse;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.rest.RestResponse;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.harness.security.annotations.DelegateAuth;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import lombok.extern.slf4j.Slf4j;

@Api("/agent/delegates/perpetual-task")
@Path("/agent/delegates/perpetual-task")
@Produces({"application/x-protobuf", "application/x-protobuf-text-format", "application/x-protobuf-json-format"})
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
  public RestResponse<PerpetualTaskListResponse> perpetualTaskList(PerpetualTaskListRequest perpetualTaskListRequest) {
    List<PerpetualTaskAssignDetails> perpetualTaskAssignDetails =
        perpetualTaskService.listAssignedTasks(perpetualTaskListRequest.getDelegateId().getId());
    PerpetualTaskListResponse response =
        PerpetualTaskListResponse.newBuilder().addAllPerpetualTaskAssignDetails(perpetualTaskAssignDetails).build();
    return new RestResponse<>(response);
  }

  @GET
  @Path("/context")
  @Timed
  @DelegateAuth
  @ExceptionMetered
  @ApiOperation(value = "Get perpetual task context for given perpetual task", nickname = "perpetualTaskContext")
  public RestResponse<PerpetualTaskContextResponse> perpetualTaskContext(
      PerpetualTaskContextRequest perpetualTaskContextRequest) {
    PerpetualTaskContextResponse response = PerpetualTaskContextResponse.newBuilder()
                                                .setPerpetualTaskContext(perpetualTaskService.perpetualTaskContext(
                                                    perpetualTaskContextRequest.getPerpetualTaskId().getId()))
                                                .build();
    return new RestResponse<>(response);
  }

  @GET
  @Path("/heartbeat")
  @Timed
  @ExceptionMetered
  @DelegateAuth
  @ApiOperation(value = "Heartbeat recording", nickname = "heartbeat")
  public RestResponse<HeartbeatResponse> heartbeat(HeartbeatRequest heartbeatRequest) {
    PerpetualTaskResponse perpetualTaskResponse = PerpetualTaskResponse.builder()
                                                      .responseMessage(heartbeatRequest.getResponseMessage())
                                                      .responseCode(heartbeatRequest.getResponseCode())
                                                      .build();
    long heartbeatMillis = HTimestamps.toInstant(heartbeatRequest.getHeartbeatTimestamp()).toEpochMilli();
    perpetualTaskService.triggerCallback(heartbeatRequest.getId(), heartbeatMillis, perpetualTaskResponse);
    return new RestResponse<>(HeartbeatResponse.newBuilder().build());
  }
}
