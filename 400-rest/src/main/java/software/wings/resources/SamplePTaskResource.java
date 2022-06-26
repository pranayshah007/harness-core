/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import io.harness.grpc.utils.HTimestamps;
import io.harness.perpetualtask.example.SamplePTaskService;
import io.harness.pingpong.PingDelegateService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.harness.rest.RestResponse;
import io.swagger.annotations.Api;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import org.glassfish.jersey.message.internal.MediaTypeProvider;

import java.time.Instant;

@Slf4j
@Api("perpetual-task")
@Path("/perpetual-task/v2")
@Produces("application/x-protobuf")
public class SamplePTaskResource {
  @Inject private SamplePTaskService samplePTaskService;

  @Inject
  public SamplePTaskResource(SamplePTaskService samplePTaskService) {
    this.samplePTaskService = samplePTaskService;
  }

  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<PingDelegateService> create(@QueryParam("accountId") String accountId,
                             @QueryParam("country") String countryName, @QueryParam("population") int population) {
    Instant timestamp = Instant.now();

    PingDelegateService ping = PingDelegateService.newBuilder()
            .setDelegateId("UNREGISTERED")
            .setPingTimestamp(HTimestamps.fromInstant(timestamp))
            .setProcessId("PROCESS_ID")
            .setVersion("1.000.")
            .build();

    return new RestResponse <>(ping);

  }

  /*@PUT
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> update(@QueryParam("accountId") String accountId, @QueryParam("taskId") String taskId,
      @QueryParam("country") String countryName, @QueryParam("population") int population) {
    samplePTaskService.update(accountId, taskId, countryName, population);
    return new RestResponse<>();
  }*/
}
