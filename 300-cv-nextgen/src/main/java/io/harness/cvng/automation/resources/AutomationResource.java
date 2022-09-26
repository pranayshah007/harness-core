/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.automation.resources;

import io.harness.annotations.ExposeInternalException;
import io.harness.cvng.cdng.services.api.CVNGStepTaskService;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

@Api("automation")
@Path("/automation")
@Produces("application/json")
@ExposeInternalException
@NextGenManagerAuth
public class AutomationResource {
  @Inject private CVNGStepTaskService stepTaskService;

  @GET
  @Path("/verification-job-instance/{verifyStepExecutionId}")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "get verification job instance", nickname = "getVerificationJobInstance", hidden = true)
  public RestResponse<VerificationJobInstance> getVerificationJobInstance(
      @NotBlank @NotNull @PathParam("verifyStepExecutionId") String verificationJobInstanceId) {
    return new RestResponse(stepTaskService.getByVerificationJobInstanceId(verificationJobInstanceId));
  }
}
