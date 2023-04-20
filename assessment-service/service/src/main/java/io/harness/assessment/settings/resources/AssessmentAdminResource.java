/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.resources;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;

import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadRequest;
import io.harness.assessment.settings.beans.dto.upload.AssessmentUploadResponse;
import io.harness.assessment.settings.services.AssessmentUploadService;
import io.harness.eraro.ResponseMessage;
import io.harness.utils.YamlPipelineUtils;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.time.ZonedDateTime;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Path("/v1/assessment")
@Api("assessment-admin")
@Slf4j
public class AssessmentAdminResource {
  private AssessmentUploadService assessmentUploadService;

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

  @GET
  @Path("{assessmentId}/template/yaml")
  @Produces(APPLICATION_OCTET_STREAM)
  @ApiOperation(value = "Get an assessment in the system.", nickname = "getAssessmentYAML",
      response = AssessmentUploadRequest.class)
  public Response
  getAssessmentYAML(@PathParam("assessmentId") String assessmentId) {
    AssessmentUploadRequest assessmentUploadResponse = assessmentUploadService.getAssessment(assessmentId).getRequest();
    return Response.ok(YamlPipelineUtils.writeYamlString(assessmentUploadResponse), APPLICATION_OCTET_STREAM)
        .header("Content-Disposition",
            "attachment; filename=" + prepareAssessmentFileName(assessmentId, ZonedDateTime.now().toEpochSecond()))
        .build();
  }
  private static String prepareAssessmentFileName(String assessmentId, long currentTs) {
    return format("%s-%s.yaml", assessmentId, currentTs);
  }
}
