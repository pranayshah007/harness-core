/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.resources;

import io.harness.assessment.settings.beans.dto.UserResponsesRequest;
import io.harness.assessment.settings.services.AssessmentEvaluationService;
import io.harness.eraro.ResponseMessage;

import com.google.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Path("/v1")
public class AssessmentEvaluationResource {
  private AssessmentEvaluationService assessmentEvaluationService;
  @GET
  @Path(("attempt/{assessmentInviteId}"))
  @Produces({"application/json"})
  public Response getAssessmentForUser(@PathParam("assessmentInviteId") String assessmentInviteId) {
    // Get token info and validate user.
    return Response.status(Response.Status.OK)
        .entity(assessmentEvaluationService.getAssessmentForUser(assessmentInviteId))
        .build();
  }

  @POST
  @Path(("attempt/{assessmentId}"))
  @Consumes({"application/json"})
  @Produces({"application/json"})
  public Response submitAssessmentResponse(@PathParam("assessmentId") String assessmentId,
      @Valid UserResponsesRequest body, @HeaderParam("Auth") String auth) {
    try {
      return Response.status(Response.Status.OK)
          .entity(assessmentEvaluationService.submitAssessmentResponse(body, auth))
          .build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @POST
  @Path(("attempt/save"))
  @Consumes({"application/json"})
  @Produces({"application/json"})
  public Response saveAssessmentResponse(@Valid UserResponsesRequest body, @HeaderParam("Auth") String auth) {
    try {
      return Response.status(Response.Status.OK)
          .entity(assessmentEvaluationService.submitAssessmentResponse(body, auth))
          .build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
