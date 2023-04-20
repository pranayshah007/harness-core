/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.assessment.settings.resources;

import static java.lang.String.format;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

import io.harness.assessment.settings.beans.dto.BenchmarkDTO;
import io.harness.assessment.settings.beans.dto.upload.BenchmarkUploadResponse;
import io.harness.assessment.settings.beans.dto.upload.BenchmarksUploadRequest;
import io.harness.assessment.settings.services.BenchmarkService;
import io.harness.eraro.ResponseMessage;
import io.harness.utils.YamlPipelineUtils;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.io.InputStream;
import java.time.ZonedDateTime;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
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
@Path("/v1/benchmark")
@Api("benchmark")
@Slf4j
public class BenchmarkResource {
  private BenchmarkService benchmarkService;
  @POST
  @Consumes({"application/json"})
  @Produces({"application/json"})
  @ApiOperation(value = "Upload an benchmark against a assessment to the system.", nickname = "uploadBenchmark",
      response = BenchmarkUploadResponse.class)
  public Response
  uploadBenchmark(@Valid BenchmarksUploadRequest body) {
    try {
      BenchmarkUploadResponse uploadResponse = benchmarkService.uploadBenchmark(body);
      if (uploadResponse.getErrors() != null && uploadResponse.getErrors().size() > 0) {
        return Response.status(Response.Status.BAD_REQUEST).entity(uploadResponse).build();
      }
      return Response.status(Response.Status.OK).entity(uploadResponse).build();
    } catch (Exception e) {
      log.error("error", e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @GET
  @Path("{assessmentId}/{majorVersion}")
  @Produces({"application/json"})
  @ApiOperation(value = "Get list of benchmarks against an assessment in the system.", nickname = "getBenchmarks",
      response = BenchmarkDTO.class, responseContainer = "List")
  public Response
  getBenchmarks(@PathParam("assessmentId") String assessmentId, @PathParam("majorVersion") Long majorVersion) {
    try {
      BenchmarkUploadResponse benchmarks = benchmarkService.getBenchmarks(assessmentId, majorVersion);
      return Response.status(Response.Status.OK).entity(benchmarks.getBenchmarks()).build();
    } catch (Exception e) {
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @GET
  @Path("{assessmentId}/{majorVersion}/yaml")
  @Produces(APPLICATION_OCTET_STREAM)
  @ApiOperation(value = "Get list of benchmarks against an assessment in the system YAML.",
      nickname = "getBenchmarksYAML", response = BenchmarksUploadRequest.class, responseContainer = "List")
  public Response
  getBenchmarksYAML(@PathParam("assessmentId") String assessmentId, @PathParam("majorVersion") Long majorVersion) {
    BenchmarkUploadResponse benchmarkUploadResponse = benchmarkService.getBenchmarks(assessmentId, majorVersion);
    return Response
        .ok(YamlPipelineUtils.writeYamlString(benchmarkUploadResponse.getRequest()), APPLICATION_OCTET_STREAM)
        .header("Content-Disposition",
            "attachment; filename=" + prepareBenchmarkFileName(assessmentId, ZonedDateTime.now().toEpochSecond()))
        .build();
  }
  private static String prepareBenchmarkFileName(String assessmentId, long currentTs) {
    return format("%s-%s.yaml", assessmentId, currentTs);
  }

  @POST
  @Path("/yaml")
  @Consumes(MULTIPART_FORM_DATA)
  @Produces({"application/json"})
  @ApiOperation(value = "Upload an benchmark to the system YAML.", nickname = "uploadBenchmarkYAML",
      response = BenchmarkUploadResponse.class)
  public Response
  uploadAssessmentYAML(@FormDataParam("file") InputStream uploadedInputStream) {
    try {
      BenchmarkUploadResponse assessmentUploadResponse = benchmarkService.uploadNewBenchmarkYAML(uploadedInputStream);
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
}
