/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.buckets.resources.s3;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.buckets.resources.s3.S3ResourceService;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.buckets.resources.BucketsResourceUtils;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ARTIFACTS, HarnessModuleComponent.CDS_COMMON_STEPS})
@Api("buckets")
@Path("/buckets/s3")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class S3BucketResource {
  private final S3ResourceService s3ResourceService;
  private final BucketsResourceUtils bucketsResourceUtils;
  private final ArtifactResourceUtils artifactResourceUtils;

  @GET
  @Path("getBuckets")
  @ApiOperation(value = "Gets s3 buckets", nickname = "getBucketListForS3")
  public ResponseDTO<Map<String, String>> getBuckets(@QueryParam("region") String region,
      @QueryParam("connectorRef") String awsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier, @QueryParam("fqnPath") String fqnPath,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    Map<String, String> s3Buckets = artifactResourceUtils.getBucketsS3(
        region, awsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier, fqnPath, serviceRef);
    return ResponseDTO.newResponse(s3Buckets);
  }

  @POST
  @Path("v2/getBucketsInManifests")
  @ApiOperation(value = "Gets s3 buckets", nickname = "getBucketsInManifests")
  public ResponseDTO<Map<String, String>> getBucketsInManifests(@QueryParam("region") String region,
      @QueryParam("connectorRef") String awsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
    Map<String, String> s3Buckets = artifactResourceUtils.getBucketsInManifestsS3(region, awsConnectorIdentifier,
        accountId, orgIdentifier, projectIdentifier, pipelineIdentifier, fqnPath, runtimeInputYaml, serviceRef);

    return ResponseDTO.newResponse(s3Buckets);
  }

  @GET
  @Path("getBucketsV2")
  @ApiOperation(value = "Gets s3 buckets", nickname = "getV2BucketListForS3")
  public ResponseDTO<List<BucketResponseDTO>> getBucketsV2(@QueryParam("region") String region,
      @NotNull @QueryParam("connectorRef") String awsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    List<BucketResponseDTO> bucketResponse = artifactResourceUtils.getBucketsV2S3(
        region, awsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    return ResponseDTO.newResponse(bucketResponse);
  }

  @POST
  @Path("v2/getBuckets")
  @ApiOperation(value = "Gets s3 buckets", nickname = "listBucketsWithServiceV2")
  public ResponseDTO<List<BucketResponseDTO>> getBucketsV2WithServiceV2(@QueryParam("region") String region,
      @QueryParam("connectorRef") String awsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    // In case of ServiceV2 Calls
    List<BucketResponseDTO> bucketResponse =
        artifactResourceUtils.getBucketsV2WithServiceV2S3(region, awsConnectorIdentifier, accountId, orgIdentifier,
            projectIdentifier, pipelineIdentifier, fqnPath, runtimeInputYaml, serviceRef, gitEntityBasicInfo);

    return ResponseDTO.newResponse(bucketResponse);
  }

  @GET
  @Path("getFilePaths")
  @ApiOperation(value = "Gets s3 file paths", nickname = "getFilePathsForS3")
  public ResponseDTO<List<FilePathDTO>> getFilePaths(@QueryParam("region") String region,
      @NotNull @QueryParam("connectorRef") String awsConnectorIdentifier,
      @NotNull @QueryParam("bucketName") String bucketName, @QueryParam("filePathRegex") String filePathRegex,
      @QueryParam("fileFilter") String fileFilter,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    List<FilePathDTO> artifactPathDTOS = artifactResourceUtils.getFilePathsS3(region, awsConnectorIdentifier,
        bucketName, filePathRegex, fileFilter, accountId, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(artifactPathDTOS);
  }

  @POST
  @Path("v2/getFilePaths")
  @ApiOperation(value = "Gets s3 file paths", nickname = "getFilePathsV2ForS3")
  public ResponseDTO<List<FilePathDTO>> getFilePathsForServiceV2(@QueryParam("region") String region,
      @QueryParam("connectorRef") String awsConnectorIdentifier, @QueryParam("bucketName") String bucketName,
      @QueryParam("filePathRegex") String filePathRegex, @QueryParam("fileFilter") String fileFilter,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    // In case of ServiceV2 Calls
    List<FilePathDTO> artifactPathDTOS = artifactResourceUtils.getFilePathsForServiceV2S3(region,
        awsConnectorIdentifier, bucketName, filePathRegex, fileFilter, accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, fqnPath, runtimeInputYaml, serviceRef, gitEntityBasicInfo);

    return ResponseDTO.newResponse(artifactPathDTOS);
  }
}
