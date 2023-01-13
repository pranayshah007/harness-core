/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.resources;

import io.harness.cvng.core.jobs.FakeFeatureFlagSRMProducer;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import lombok.Builder;
import lombok.Data;
import retrofit2.http.Body;

@Api("fakeff/")
@Path("fakeff")
@Produces("application/json")
@NextGenManagerAuth
public class FakeFeatureFlagSRMEventResource {
  @Inject private FakeFeatureFlagSRMProducer fakeFeatureFlagSRMProducer;

  @POST
  @Path("register")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "register fake ff event in srm queue", nickname = "register")
  public void register(@Body FFEventBody ffEventBody) {
    fakeFeatureFlagSRMProducer.publishEvent(ffEventBody);
  }

  @Data
  public static class FFEventBody {
    String accId;
    String orgId;
    String projectId;
    List<String> services;
    List<String> envs;
    long startTime;
    long endTime;
    String name;
    String user;
    List<String> descriptions;
    String changeEventLink;
    String internalChangeLink;
  }
}
