/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.resources;

import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadRequest;
import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadResponse;
import io.harness.assessment.settings.services.AssessmentUploadService;
import io.harness.eraro.ResponseMessage;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.io.InputStream;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.FormDataParam;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Path("/v1/assessment")
@Api("assessment-upload")
@Slf4j
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
      AssessmentUploadResponse assessmentUploadResponse = assessmentUploadService.uploadNewAssessment(body);
      if (assessmentUploadResponse.getErrors() != null && assessmentUploadResponse.getErrors().size() > 0) {
        return Response.status(Response.Status.BAD_REQUEST).entity(assessmentUploadResponse).build();
      }
      return Response.status(Response.Status.OK).entity(assessmentUploadResponse).build();
    } catch (Exception e) {
      log.error("Error", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @POST
  @Path("/yaml")
  @Consumes(MULTIPART_FORM_DATA)
  @Produces({"application/json"})
  @ApiOperation(value = "Upload an assessment to the system.", nickname = "uploadAssessmentYAML",
      response = AssessmentUploadResponse.class)
  public Response
  uploadAssessmentYAML(@FormDataParam("file") InputStream uploadedInputStream
      //, @FormDataParam("assessmentId") String assessmentId
  ) {
    try {
      AssessmentUploadResponse assessmentUploadResponse =
          assessmentUploadService.uploadNewAssessmentYAML(uploadedInputStream);
      if (assessmentUploadResponse.getErrors() != null && assessmentUploadResponse.getErrors().size() > 0) {
        return Response.status(Response.Status.BAD_REQUEST).entity(assessmentUploadResponse).build();
      }
      return Response.status(Response.Status.OK).entity(assessmentUploadResponse).build();
    } catch (Exception e) {
      log.error("Error", e);
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
}
