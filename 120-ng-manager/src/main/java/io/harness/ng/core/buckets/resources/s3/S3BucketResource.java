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
import io.harness.cdng.buckets.resources.s3.S3ResourceService;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.ng.core.buckets.resources.BucketsResourceUtils;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

  @GET
  @Path("getBuckets")
  @ApiOperation(value = "Gets s3 buckets", nickname = "getBucketListForS3")
  public ResponseDTO<Map<String, String>> getBuckets(@QueryParam("region") String region,
      @QueryParam("connectorRef") String awsConnectorIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @NotNull @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @NotNull @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("fqnPath") String fqnPath, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef) {
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
}
