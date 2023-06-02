/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.buckets.resources.s3;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.cdng.buckets.resources.s3.S3ResourceService;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.buckets.resources.BucketsResourceUtils;
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
import org.apache.commons.lang3.StringUtils;

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
    if (isNotEmpty(serviceRef)) {
      S3StoreConfig storeConfig = (S3StoreConfig) bucketsResourceUtils.locateStoreConfigInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);
      if (isEmpty(region)) {
        region = storeConfig.getRegion().getValue();
      }
      if (isEmpty(awsConnectorIdentifier)) {
        awsConnectorIdentifier = storeConfig.getConnectorRef().getValue();
      }
    }
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);
    Map<String, String> s3Buckets =
        s3ResourceService.getBuckets(connectorRef, region, orgIdentifier, projectIdentifier);
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
    if (isNotEmpty(serviceRef)) {
      S3StoreConfig storeConfig = (S3StoreConfig) bucketsResourceUtils.locateStoreConfigInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      if (StringUtils.isBlank(region)) {
        region = storeConfig.getRegion().getValue();
      }

      if (StringUtils.isBlank(awsConnectorIdentifier)) {
        awsConnectorIdentifier = storeConfig.getConnectorRef().getValue();
      }
    }

    // Getting the resolved region in case of expressions
    String resolvedRegion = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, region, fqnPath, null, serviceRef);

    // Getting the resolved region in case of expressions
    String resolvedConnectorIdentifier = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier,
        projectIdentifier, pipelineIdentifier, runtimeInputYaml, awsConnectorIdentifier, fqnPath, null, serviceRef);

    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(resolvedConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    Map<String, String> s3Buckets =
        s3ResourceService.getBuckets(connectorRef, resolvedRegion, orgIdentifier, projectIdentifier);

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
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    Map<String, String> s3Buckets =
        s3ResourceService.getBuckets(connectorRef, region, orgIdentifier, projectIdentifier);

    List<String> bucketList = new ArrayList<>(s3Buckets.values());

    List<BucketResponseDTO> bucketResponse = new ArrayList<>();

    for (String s : bucketList) {
      BucketResponseDTO bucket = BucketResponseDTO.builder().bucketName(s).build();

      bucketResponse.add(bucket);
    }

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
    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      AmazonS3ArtifactConfig amazonS3ArtifactConfig = (AmazonS3ArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(region)) {
        region = (String) amazonS3ArtifactConfig.getRegion().fetchFinalValue();
      }

      if (StringUtils.isBlank(awsConnectorIdentifier)) {
        awsConnectorIdentifier = (String) amazonS3ArtifactConfig.getConnectorRef().fetchFinalValue();
      }
    }

    // Getting the resolved region in case of expressions
    String resolvedRegion = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved awsConnectorIdentifier in case of expressions
    String resolvedAwsConnectorIdentifier =
        artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            runtimeInputYaml, awsConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    // Common logic in case of ServiceV1 and ServiceV2
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        resolvedAwsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    Map<String, String> s3Buckets =
        s3ResourceService.getBuckets(connectorRef, resolvedRegion, orgIdentifier, projectIdentifier);

    List<String> bucketList = new ArrayList<>(s3Buckets.values());

    List<BucketResponseDTO> bucketResponse = new ArrayList<>();

    for (String s : bucketList) {
      BucketResponseDTO bucket = BucketResponseDTO.builder().bucketName(s).build();

      bucketResponse.add(bucket);
    }

    return ResponseDTO.newResponse(bucketResponse);
  }

  @GET
  @Path("getFilePaths")
  @ApiOperation(value = "Gets s3 file paths", nickname = "getFilePathsForS3")
  public ResponseDTO<List<FilePathDTO>> getFilePaths(@QueryParam("region") String region,
      @NotNull @QueryParam("connectorRef") String awsConnectorIdentifier,
      @NotNull @QueryParam("bucketName") String bucketName, @QueryParam("filePathRegex") String filePathRegex,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(awsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    List<BuildDetails> s3ArtifactPaths = s3ResourceService.getFilePaths(
        connectorRef, region, bucketName, filePathRegex, orgIdentifier, projectIdentifier);

    List<FilePathDTO> artifactPathDTOS = new ArrayList<>();

    for (BuildDetails s : s3ArtifactPaths) {
      FilePathDTO artifactPathDTO = FilePathDTO.builder().buildDetails(s).build();
      artifactPathDTOS.add(artifactPathDTO);
    }

    return ResponseDTO.newResponse(artifactPathDTOS);
  }

  @POST
  @Path("v2/getFilePaths")
  @ApiOperation(value = "Gets s3 file paths", nickname = "getFilePathsV2ForS3")
  public ResponseDTO<List<FilePathDTO>> getFilePathsForServiceV2(@QueryParam("region") String region,
      @QueryParam("connectorRef") String awsConnectorIdentifier, @QueryParam("bucketName") String bucketName,
      @QueryParam("filePathRegex") String filePathRegex,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @NotNull @QueryParam("fqnPath") String fqnPath, @NotNull String runtimeInputYaml,
      @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo) {
    // In case of ServiceV2 Calls
    if (StringUtils.isNotBlank(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(
          accountId, orgIdentifier, projectIdentifier, serviceRef, fqnPath);

      AmazonS3ArtifactConfig amazonS3ArtifactConfig = (AmazonS3ArtifactConfig) artifactSpecFromService;

      if (StringUtils.isBlank(region)) {
        region = (String) amazonS3ArtifactConfig.getRegion().fetchFinalValue();
      }

      if (StringUtils.isBlank(awsConnectorIdentifier)) {
        awsConnectorIdentifier = (String) amazonS3ArtifactConfig.getConnectorRef().fetchFinalValue();
      }

      if (StringUtils.isBlank(bucketName)) {
        bucketName = (String) amazonS3ArtifactConfig.getBucketName().fetchFinalValue();
      }
    }

    // Getting the resolved region in case of expressions
    String resolvedRegion = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, region, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved awsConnectorIdentifier in case of expressions
    String resolvedAwsConnectorIdentifier =
        artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier, pipelineIdentifier,
            runtimeInputYaml, awsConnectorIdentifier, fqnPath, gitEntityBasicInfo, serviceRef);

    // Getting the resolved bucketName in case of expressions
    String resolvedBucketName = artifactResourceUtils.getResolvedFieldValue(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, runtimeInputYaml, bucketName, fqnPath, gitEntityBasicInfo, serviceRef);

    // Common logic in case of ServiceV1 and ServiceV2
    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        resolvedAwsConnectorIdentifier, accountId, orgIdentifier, projectIdentifier);

    List<BuildDetails> s3ArtifactPaths = s3ResourceService.getFilePaths(
        connectorRef, resolvedRegion, resolvedBucketName, filePathRegex, orgIdentifier, projectIdentifier);

    List<FilePathDTO> artifactPathDTOS = new ArrayList<>();

    for (BuildDetails s : s3ArtifactPaths) {
      FilePathDTO artifactPathDTO = FilePathDTO.builder().buildDetails(s).build();
      artifactPathDTOS.add(artifactPathDTO);
    }

    return ResponseDTO.newResponse(artifactPathDTOS);
  }
}
