/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.bamboo;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.resources.bamboo.BambooResourceService;
import io.harness.cdng.artifact.resources.bamboo.dtos.BambooPlanKeysDTO;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import software.wings.helpers.ext.jenkins.BuildDetails;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.Collections;
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
@Path("/artifacts/bamboo")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class BambooArtifactResource {
  private final BambooResourceService bambooResourceService;
  private final ArtifactResourceUtils artifactResourceUtils;
  @GET
  @Path("plans")
  @ApiOperation(value = "Gets Job details for Jenkins", nickname = "getJobDetailsForJenkins")
  public ResponseDTO<BambooPlanKeysDTO> getPlansKey(@QueryParam("connectorRef") String bambooConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(bambooConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    BambooPlanKeysDTO buildDetails = bambooResourceService.getPlanName(connectorRef, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(buildDetails);
  }

  @GET
  @Path("plan/{planName}/paths")
  @ApiOperation(value = "Gets Job details for Jenkins", nickname = "getJobDetailsForJenkins")
  public ResponseDTO<List<String>> getPlansKey(@QueryParam("connectorRef") String bambooConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @PathParam("planName") String planName,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(bambooConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    List<String> artifactPaths =
        bambooResourceService.getArtifactPath(connectorRef, orgIdentifier, projectIdentifier, planName);
    return ResponseDTO.newResponse(artifactPaths);
  }

  @GET
  @Path("plan/{planName}/builds")
  @ApiOperation(value = "Gets Job details for Jenkins", nickname = "getJobDetailsForJenkins")
  public ResponseDTO<List<BuildDetails>> getBuilds(@QueryParam("connectorRef") String bambooConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @PathParam("planName") String planName,
      @QueryParam(NGCommonEntityConstants.ARTIFACT_PATH) List<String> artifactPath,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(bambooConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    List<BuildDetails> artifactPaths =
        bambooResourceService.getBuilds(connectorRef, orgIdentifier, projectIdentifier, planName, artifactPath);
    return ResponseDTO.newResponse(artifactPaths);
  }
}
