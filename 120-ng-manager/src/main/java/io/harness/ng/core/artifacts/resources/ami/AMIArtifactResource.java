/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.ami;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.ami.AMITagObject;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AMIArtifactConfig;
import io.harness.cdng.artifact.resources.ami.AMIResourceService;
import io.harness.common.NGExpressionUtils;
import io.harness.delegate.task.artifacts.ami.AMIFilter;
import io.harness.delegate.task.artifacts.ami.AMITag;
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
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Api("artifacts")
@Path("/artifacts/ami")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class AMIArtifactResource {
  private final AMIResourceService amiResourceService;

  private final ArtifactResourceUtils artifactResourceUtils;

  @POST
  @Path("tags")
  @ApiOperation(value = "List Tags for AMI Artifacts", nickname = "listTagsForAMIArtifact")
  public ResponseDTO<List<AMITagObject>> listTags(@QueryParam("connectorRef") String awsConnectorRef,
      @QueryParam("region") String region, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      AMIArtifactConfig amiArtifactConfig = (AMIArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(awsConnectorRef)) {
        awsConnectorRef = (String) amiArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (StringUtils.isBlank(region)) {
        region = (String) amiArtifactConfig.getRegion().fetchFinalValue();
      }
    }

    // Getting the resolved connectorRef  in case of expressions
    String resolvedAwsConnectorRef =
        artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            runtimeInputYaml, awsConnectorRef, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved project  in case of expressions
    String resolvedRegion = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(resolvedAwsConnectorRef, accountId, orgIdentifier, projectIdentifier);

    List<String> response =
        amiResourceService.listTags(connectorRef, accountId, orgIdentifier, projectIdentifier, resolvedRegion);

    List<AMITagObject> amiTags = new ArrayList<>();

    for (String s : response) {
      AMITagObject amiTagObject = AMITagObject.builder().tagName(s).build();

      amiTags.add(amiTagObject);
    }

    return ResponseDTO.newResponse(amiTags);
  }

  @POST
  @Path("versions")
  @ApiOperation(value = "List Versions for AMI Artifacts", nickname = "listVersionsForAMIArtifact")
  public ResponseDTO<List<BuildDetails>> listVersions(@QueryParam("connectorRef") String awsConnectorRef,
      @QueryParam("region") String region, @QueryParam("versionRegex") String versionRegex,
      AMIRequestBody amiRequestBody, @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    List<AMITag> amiTags = new ArrayList<>();

    List<AMIFilter> amiFilters = new ArrayList<>();

    if (amiRequestBody != null) {
      amiTags = amiRequestBody.getTags();

      amiFilters = amiRequestBody.getFilters();
    }
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      AMIArtifactConfig amiArtifactConfig = (AMIArtifactConfig) artifactSpecFromService;

      if (amiArtifactConfig != null) {
        if (StringUtils.isBlank(awsConnectorRef)) {
          awsConnectorRef = (String) amiArtifactConfig.getConnectorRef().fetchFinalValue();
        }

        if (StringUtils.isBlank(region)) {
          region = (String) amiArtifactConfig.getRegion().fetchFinalValue();
        }

        if (amiArtifactConfig.getTags() != null) {
          if (NGExpressionUtils.isRuntimeField(amiArtifactConfig.getTags().getExpressionValue())) {
            amiTags = amiRequestBody.getTags();
          } else {
            amiTags = amiArtifactConfig.getTags().getValue();
          }
        }

        if (amiArtifactConfig.getFilters() != null) {
          if (NGExpressionUtils.isRuntimeField(amiArtifactConfig.getFilters().getExpressionValue())) {
            amiFilters = amiRequestBody.getFilters();
          } else {
            amiFilters = amiArtifactConfig.getFilters().getValue();
          }
        }
      }

      // Getting the resolved connectorRef  in case of expressions
      awsConnectorRef = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, amiRequestBody.getRuntimeInputYaml(), awsConnectorRef, fqnPath, null, serviceRef);

      // Getting the resolved project  in case of expressions
      region = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
          pipelineIdentifier, amiRequestBody.getRuntimeInputYaml(), region, fqnPath, null, serviceRef);
    }

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorRef, accountId, orgIdentifier, projectIdentifier);

    List<BuildDetails> builds = amiResourceService.listVersions(
        connectorRef, accountId, orgIdentifier, projectIdentifier, region, amiTags, amiFilters, versionRegex);

    return ResponseDTO.newResponse(builds);
  }
}
