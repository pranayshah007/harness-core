/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.resources;

import io.harness.assessment.settings.beans.dto.AssessmentResultsResponse;
import io.harness.assessment.settings.services.AssessmentResultService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Path("/v1")
@Api("assessment-result")
public class AssessmentResultResource {
  private AssessmentResultService assessmentResultService;

  @GET
  @Path(("results/{resultCode}"))
  @Produces({"application/json"})
  @ApiOperation(value = "View results of an assessment previously attempted.", nickname = "getAssessmentResults",
      response = AssessmentResultsResponse.class)
  public Response
  getAssessmentResults(@PathParam("resultCode") String resultCode, @QueryParam("benchmarkId") String benchmarkId) {
    // A uniquely generated non guessable link.
    return Response.status(Response.Status.OK)
        .entity(assessmentResultService.getResults(resultCode, benchmarkId))
        .build();
  }
}
