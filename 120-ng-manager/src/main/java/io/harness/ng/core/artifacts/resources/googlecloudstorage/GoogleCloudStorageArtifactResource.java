/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.googlecloudstorage;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GoogleCloudStorageArtifactConfig;
import io.harness.cdng.artifact.resources.googlecloudstorage.dtos.GoogleCloudStorageBucketDetails;
import io.harness.cdng.artifact.resources.googlecloudstorage.dtos.GoogleCloudStorageBucketsResponseDTO;
import io.harness.cdng.artifact.resources.googlecloudstorage.service.GoogleCloudStorageArtifactResourceService;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
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
@Path("/artifacts/google-cloud-storage")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class GoogleCloudStorageArtifactResource {
  private final ArtifactResourceUtils artifactResourceUtils;
  private final GoogleCloudStorageArtifactResourceService googleCloudStorageArtifactResourceService;

  @POST
  @Path("buckets")
  @ApiOperation(value = "Get list of buckets from google cloud storage", nickname = "getGcsBuckets")
  public ResponseDTO<GoogleCloudStorageBucketsResponseDTO> getBuckets(@QueryParam("project") String project,
      @QueryParam("connectorRef") String gcpConnectorRef,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      GoogleCloudStorageArtifactConfig googleCloudStorageArtifactConfig =
          (GoogleCloudStorageArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(gcpConnectorRef)) {
        gcpConnectorRef = (String) googleCloudStorageArtifactConfig.getConnectorRef().fetchFinalValue();
      }
      if (StringUtils.isBlank(project)) {
        project = (String) googleCloudStorageArtifactConfig.getProject().fetchFinalValue();
      }
    }

    // Getting the resolved connectorRef in case of expressions
    String resolvedGcpConnectorRef =
        artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            runtimeInputYaml, gcpConnectorRef, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved project in case of expressions
    String resolvedProject = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, project, fqnPath, gitEntityBasicInfo, serviceRef);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(resolvedGcpConnectorRef, accountId, orgIdentifier, projectIdentifier);

    List<GoogleCloudStorageBucketDetails> buckets = googleCloudStorageArtifactResourceService.listGcsBuckets(
        connectorRef, accountId, orgIdentifier, projectIdentifier, resolvedProject);
    return ResponseDTO.newResponse(GoogleCloudStorageBucketsResponseDTO.builder().buckets(buckets).build());
  }
}
