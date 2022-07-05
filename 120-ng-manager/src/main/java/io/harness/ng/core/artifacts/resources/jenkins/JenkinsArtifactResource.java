/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.jenkins;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.jenkins.dtos.JenkinsJobDetailsDTO;
import io.harness.cdng.artifact.resources.jenkins.service.JenkinsResourceService;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.helpers.ext.jenkins.JobDetails.JobParameter;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/jenkins")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class JenkinsArtifactResource {
  private final JenkinsResourceService jenkinsResourceService;
  @GET
  @Path("jobs")
  @ApiOperation(value = "Gets Job details for Jenkins", nickname = "getJobDetailsForJenkins")
  public ResponseDTO<JenkinsJobDetailsDTO> getJobDetails(@QueryParam("connectorRef") String jenkinsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("parentJobName") String parentJobName, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(jenkinsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    JenkinsJobDetailsDTO buildDetails =
        jenkinsResourceService.getJobDetails(connectorRef, orgIdentifier, projectIdentifier, parentJobName);
    return ResponseDTO.newResponse(buildDetails);
  }

  @GET
  @Path("job/{jobName}/paths")
  @ApiOperation(value = "Gets jenkins Artifact Paths", nickname = "getArtifactPath For Jenkins")
  public ResponseDTO<List<String>> getArtifactPath(@QueryParam("connectorRef") String jenkinsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @PathParam("jobName") String jobName, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(jenkinsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return ResponseDTO.newResponse(
        jenkinsResourceService.getArtifactPath(connectorRef, orgIdentifier, projectIdentifier, jobName));
  }

  @GET
  @Path("job/{jobName}/builds")
  @ApiOperation(value = "Gets Jenkins builds", nickname = "getBuilds For Jenkins")
  public ResponseDTO<List<BuildDetails>> getBuildsForJob(@QueryParam("connectorRef") String jenkinsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @PathParam("jobName") String jobName, @NotNull @QueryParam("artifactPaths") List<String> artifactPath,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(jenkinsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return ResponseDTO.newResponse(
        jenkinsResourceService.getBuildForJob(connectorRef, orgIdentifier, projectIdentifier, jobName, artifactPath));
  }

  @GET
  @Path("job/{jobName}/details")
  @ApiOperation(value = "Gets Jenkins Job paramter", nickname = "getJobParameters for Jenkins")
  public ResponseDTO<List<JobParameter>> getJobParameters(@QueryParam("connectorRef") String jenkinsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @NotNull @PathParam("jobName") String jobName, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(jenkinsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    List<JobDetails> jobDetails =
        jenkinsResourceService.getJobParameters(connectorRef, orgIdentifier, projectIdentifier, jobName);
    List<JobParameter> jobParameters = jobDetails.get(0).getParameters();

    return ResponseDTO.newResponse(jobParameters);
  }
}
