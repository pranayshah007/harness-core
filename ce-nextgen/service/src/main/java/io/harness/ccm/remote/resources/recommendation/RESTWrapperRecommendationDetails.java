/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.recommendation;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.commons.utils.TimeUtils;
import io.harness.ccm.graphql.dto.recommendation.AzureVmRecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.EC2RecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.ECSRecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.NodeRecommendationDTO;
import io.harness.ccm.graphql.dto.recommendation.WorkloadRecommendationDTO;
import io.harness.ccm.graphql.query.recommendation.RecommendationsDetailsQuery;
import io.harness.ccm.graphql.utils.GraphQLToRESTHelper;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.exception.InvalidArgumentsException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.leangen.graphql.execution.ResolutionEnvironment;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

@Api("recommendation/details")
@Path("recommendation/details")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
@Tag(name = "Cloud Cost Recommendations Details",
    description = "Cloud Cost Recommendations details for workloads and node pools.")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = FailureDTO.class)) })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content = { @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ErrorDTO.class)) })
public class RESTWrapperRecommendationDetails {
  @Inject private RecommendationsDetailsQuery detailsQuery;

  private static final String INVALID_DATETIME_INPUT = "datetime range invalid.\nProvided from:[%s] to:[%s]";
  private static final String DATETIME_DESCRIPTION =
      "Should use org.joda.time.DateTime parsable format. Example, '2022-01-31', '2022-01-31T07:54Z' or '2022-01-31T07:54:51.264Z'";

  @GET
  @Path("node-pool")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Node pool Recommendation Details", nickname = "nodeRecommendationDetail")
  @Operation(operationId = "nodeRecommendationDetail",
      description = "Returns node pool Recommendation details for the given identifier.",
      summary = "Return node pool Recommendation",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns node pool Recommendation details for the given identifier.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<NodeRecommendationDTO>
  nodeRecommendationDetail(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                               NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Node pool Recommendation identifier") @QueryParam(
          "id") @NotNull @Valid String id) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);

    NodeRecommendationDTO nodeRecommendation =
        (NodeRecommendationDTO) detailsQuery.recommendationDetails(id, ResourceType.NODE_POOL, null, null, 0L, env);
    return ResponseDTO.newResponse(nodeRecommendation);
  }

  @GET
  @Path("workload")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Workload Recommendation Details", nickname = "workloadRecommendationDetail")
  @Operation(operationId = "workloadRecommendationDetail",
      description = "Returns workload Recommendation details for the given Recommendation identifier.",
      summary = "Return workload Recommendation",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the workload Recommendation for the given identifier.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<WorkloadRecommendationDTO>
  workloadRecommendationDetail(
      @Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Workload Recommendation identifier.") @QueryParam(
          "id") @NotNull @Valid String id,
      @Parameter(required = false, description = DATETIME_DESCRIPTION + " Defaults to Today-7days") @QueryParam(
          "from") @Nullable @Valid String from,
      @Parameter(required = false, description = DATETIME_DESCRIPTION + " Defaults to Today") @QueryParam(
          "to") @Nullable @Valid String to) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);

    DateTime endTime = DateTime.now().withTimeAtStartOfDay();
    DateTime startTime = endTime.minusDays(7);

    if (from != null) {
      startTime = DateTime.parse(from);
    }
    if (to != null) {
      endTime = DateTime.parse(to);
    }

    if (startTime.isAfter(endTime)) {
      throw new InvalidArgumentsException(
          String.format(INVALID_DATETIME_INPUT, startTime.toString(), endTime.toString()));
    }

    WorkloadRecommendationDTO workloadRecommendation = (WorkloadRecommendationDTO) detailsQuery.recommendationDetails(
        id, ResourceType.WORKLOAD, TimeUtils.toOffsetDateTime(startTime.getMillis()),
        TimeUtils.toOffsetDateTime(endTime.getMillis()), 0L, env);
    return ResponseDTO.newResponse(workloadRecommendation);
  }

  @GET
  @Path("ecs-service")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "ECS Recommendation Details", nickname = "ecsRecommendationDetail")
  @Operation(operationId = "ecsRecommendationDetail",
      description = "Returns ECS Recommendation details for the given Recommendation identifier.",
      summary = "Return ECS Recommendation",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the ECS Recommendation for the given identifier.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<ECSRecommendationDTO>
  ecsRecommendationDetail(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                              NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "ECS Recommendation identifier.") @QueryParam(
          "id") @NotNull @Valid String id,
      @Parameter(required = false, description = DATETIME_DESCRIPTION + " Defaults to Today-7days") @QueryParam(
          "from") @Nullable @Valid String from,
      @Parameter(required = false, description = DATETIME_DESCRIPTION + " Defaults to Today") @QueryParam(
          "to") @Nullable @Valid String to,
      @Parameter(required = false, description = "Buffer Percentage defaults to zero") @QueryParam(
          "bufferPercentage") @Nullable @Valid Long bufferPercentage) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);

    DateTime endTime = DateTime.now().withTimeAtStartOfDay();
    DateTime startTime = endTime.minusDays(7);

    if (from != null) {
      startTime = DateTime.parse(from);
    }
    if (to != null) {
      endTime = DateTime.parse(to);
    }

    if (bufferPercentage == null) {
      bufferPercentage = 0L;
    }

    if (startTime.isAfter(endTime)) {
      throw new InvalidArgumentsException(
          String.format(INVALID_DATETIME_INPUT, startTime.toString(), endTime.toString()));
    }

    ECSRecommendationDTO ecsRecommendation = (ECSRecommendationDTO) detailsQuery.recommendationDetails(id,
        ResourceType.ECS_SERVICE, TimeUtils.toOffsetDateTime(startTime.getMillis()),
        TimeUtils.toOffsetDateTime(endTime.getMillis()), bufferPercentage, env);
    return ResponseDTO.newResponse(ecsRecommendation);
  }

  @GET
  @Path("ec2-instance")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "EC2 Recommendation Details", nickname = "ec2RecommendationDetail")
  @Operation(operationId = "ec2RecommendationDetail",
      description = "Returns EC2 Recommendation details for the given Recommendation identifier.",
      summary = "Return EC2 Recommendation",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the EC2 Recommendation for the given identifier.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<EC2RecommendationDTO>
  ec2RecommendationDetail(@Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                              NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "EC2 Recommendation identifier.") @QueryParam(
          "id") @NotNull @Valid String id) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);
    EC2RecommendationDTO ec2Recommendation =
        (EC2RecommendationDTO) detailsQuery.recommendationDetails(id, ResourceType.EC2_INSTANCE, null, null, null, env);
    return ResponseDTO.newResponse(ec2Recommendation);
  }

  @GET
  @Path("azure-vm")
  @Timed
  @LogAccountIdentifier
  @ExceptionMetered
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Azure VM Recommendation Details", nickname = "azureVmRecommendationDetail")
  @Operation(operationId = "azureVmRecommendationDetail",
      description = "Returns Azure VM Recommendation details for the given Recommendation identifier.",
      summary = "Return Azure VM Recommendation",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the Azure VM Recommendation for the given identifier.",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<AzureVmRecommendationDTO>
  azureVmRecommendationDetail(
      @Parameter(required = true, description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @Parameter(required = true, description = "Azure VM Recommendation identifier.") @QueryParam(
          "id") @NotNull @Valid String id) {
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);
    AzureVmRecommendationDTO azureVmRecommendationDTO = (AzureVmRecommendationDTO) detailsQuery.recommendationDetails(
        id, ResourceType.AZURE_INSTANCE, null, null, null, env);
    return ResponseDTO.newResponse(azureVmRecommendationDTO);
  }
}
