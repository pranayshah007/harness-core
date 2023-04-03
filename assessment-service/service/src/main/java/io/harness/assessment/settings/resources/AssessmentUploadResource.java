/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.resources;

import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadRequest;
import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadResponse;
import io.harness.assessment.settings.services.AssessmentUploadService;
import io.harness.eraro.ResponseMessage;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Path("/v1/assessment")
@Api("assessment-upload")
public class AssessmentUploadResource {
  private AssessmentUploadService assessmentUploadService;
  @POST
  @Consumes({"application/json"})
  @Produces({"application/json"})
  @ApiOperation(value = "Upload an assessment to the system.", nickname = "uploadAssessment",
      response = AssessmentUploadResponse.class)
  public Response
  uploadAssessment(@Valid AssessmentUploadRequest body) {
    try {
      return Response.status(Response.Status.OK).entity(assessmentUploadService.uploadNewAssessment(body)).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @GET
  @Path("{assessmentId}/template")
  @Produces({"application/json"})
  @ApiOperation(
      value = "Get an assessment in the system.", nickname = "getAssessment", response = AssessmentUploadResponse.class)
  public Response
  getAssessment(@PathParam("assessmentId") String assessmentId) {
    try {
      AssessmentUploadResponse assessmentUploadResponse = assessmentUploadService.getAssessment(assessmentId);
      return Response.status(Response.Status.OK).entity(assessmentUploadResponse).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @PATCH
  @Path("{assessmentId}/publish")
  @Produces({"application/json"})
  @ApiOperation(
      value = "Publish an assessment.", nickname = "publishAssessment", response = AssessmentUploadResponse.class)
  public Response
  publishAssessment(@PathParam("assessmentId") String assessmentId) {
    try {
      AssessmentUploadResponse assessmentUploadResponse = assessmentUploadService.publishAssessment(assessmentId);
      return Response.status(Response.Status.OK).entity(assessmentUploadResponse).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @PUT
  @Consumes({"application/json"})
  @Produces({"application/json"})
  @ApiOperation(value = "Update an assessment to the system.", nickname = "updateAssessment",
      response = AssessmentUploadResponse.class)
  public Response
  updateAssessment(@Valid AssessmentUploadRequest body) {
    try {
      return Response.status(Response.Status.OK).entity(assessmentUploadService.updateAssessment(body)).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }
}
