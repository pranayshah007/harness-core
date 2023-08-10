/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources.governance;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.CENextGenConfiguration;
import io.harness.ccm.bigQuery.BigQueryService;
import io.harness.ccm.commons.beans.recommendation.RecommendationState;
import io.harness.ccm.commons.beans.recommendation.ResourceType;
import io.harness.ccm.governance.dto.OverviewExecutionCostDetails;
import io.harness.ccm.graphql.dto.recommendation.K8sRecommendationFilterDTO;
import io.harness.ccm.graphql.dto.recommendation.RecommendationItemDTO;
import io.harness.ccm.graphql.query.recommendation.RecommendationsOverviewQueryV2;
import io.harness.ccm.graphql.utils.GraphQLToRESTHelper;
import io.harness.ccm.helper.RecommendationQueryHelper;
import io.harness.ccm.remote.beans.recommendation.CCMRecommendationFilterPropertiesDTO;
import io.harness.ccm.remote.beans.recommendation.K8sRecommendationFilterPropertiesDTO;
import io.harness.ccm.views.dto.CreateRuleExecutionDTO;
import io.harness.ccm.views.dto.CreateRuleExecutionFilterDTO;
import io.harness.ccm.views.entities.RuleExecution;
import io.harness.ccm.views.helper.FilterValues;
import io.harness.ccm.views.helper.OverviewExecutionDetails;
import io.harness.ccm.views.helper.RuleExecutionFilter;
import io.harness.ccm.views.helper.RuleExecutionList;
import io.harness.ccm.views.helper.RuleExecutionStatusType;
import io.harness.ccm.views.service.RuleExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.inject.Inject;
import io.fabric8.utils.Lists;
import io.leangen.graphql.execution.ResolutionEnvironment;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("governance")
@Path("governance")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Service
@OwnedBy(CE)
@Slf4j
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })

public class GovernanceRuleExecutionResource {
  private static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
  private static final String BINARY = "binary";
  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String ATTACHMENT_FILENAME = "attachment; filename=";
  private static final String EXTENSION = ".json";
  private static final String RESOURCESFILENAME = "resources";
  public static final String GCP_CREDENTIALS_PATH = "GOOGLE_APPLICATION_CREDENTIALS";
  public static final String MALFORMED_ERROR = "Request payload is malformed";
  private final RuleExecutionService ruleExecutionService;
  @Inject CENextGenConfiguration configuration;
  @Inject private BigQueryService bigQueryService;
  @Inject private RecommendationsOverviewQueryV2 recommendationsOverviewQueryV2;

  @Inject
  public GovernanceRuleExecutionResource(RuleExecutionService ruleExecutionService) {
    this.ruleExecutionService = ruleExecutionService;
  }

  @POST
  @Hidden
  @Path("execution")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Add a new rule execution api", nickname = "addRuleExecution")
  @Operation(operationId = "addRuleExecution", summary = "Add a new rule execution ",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns newly created rule Execution")
      })
  public ResponseDTO<RuleExecution>
  create(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing Rule Execution  object")
      @Valid CreateRuleExecutionDTO createRuleExecutionDTO) {
    if (createRuleExecutionDTO == null) {
      throw new InvalidRequestException(MALFORMED_ERROR);
    }
    RuleExecution ruleExecution = createRuleExecutionDTO.getRuleExecution();
    ruleExecution.setAccountId(accountId);
    ruleExecutionService.save(ruleExecution);
    return ResponseDTO.newResponse(ruleExecution.toDTO());
  }

  @POST
  @Path("execution/list")
  @ApiOperation(value = "Get execution for account", nickname = "getRuleExecutions")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getRuleExecutions", description = "Fetch RuleExecution ",
      summary = "Fetch RuleExecution for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns List of RuleExecution",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<RuleExecutionList>
  filterRuleExecution(
      @Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing CreateRuleExecutionFilterDTO object")
      @Valid CreateRuleExecutionFilterDTO createRuleExecutionFilterDTO) {
    RuleExecutionFilter ruleExecutionFilter = createRuleExecutionFilterDTO.getRuleExecutionFilter();
    ruleExecutionFilter.setAccountId(accountId);
    return ResponseDTO.newResponse(ruleExecutionService.filterExecution(ruleExecutionFilter));
  }

  @GET
  @Path("execution/filter-value")
  @ApiOperation(value = "get Rule Execution Filter Values", nickname = "getRuleExecutionsFilterValues")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(operationId = "getRuleExecutionsFilterValues", description = "Fetch RuleExecution ",
      summary = "Fetch RuleExecution for account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns List of RuleExecution",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })
  public ResponseDTO<FilterValues>
  filterValues(@Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId) {
    return ResponseDTO.newResponse(ruleExecutionService.filterValue(accountId));
  }

  @GET
  @Path("status/{ruleExecutionId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Return logs for a rule execution status", nickname = "getRuleExecutionStatus")
  @Operation(operationId = "getRuleExecutionStatus", summary = "Return logs for a rule execution status ",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Return logs for a rule status",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })

  public Response
  getRuleExecutionStatus(
      @Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @PathParam("ruleExecutionId") @NotNull @Valid String ruleExecutionId) {
    RuleExecution ruleExecution = ruleExecutionService.get(accountId, ruleExecutionId);
    if (ruleExecution.getExecutionStatus() == RuleExecutionStatusType.FAILED) {
      if (ruleExecution.getErrorMessage() != null) {
        throw new InvalidRequestException(ruleExecution.getErrorMessage());
      } else {
        throw new InvalidRequestException(
            "Rule Execution Failed, Validate the policy or check if the you have relevant permission");
      }
    } else if (ruleExecution.getExecutionStatus() == RuleExecutionStatusType.SUCCESS) {
      return getRuleExecutionDetails(accountId, ruleExecutionId);
    }
    return null;
  }

  @GET
  @Path("execution/{ruleExecutionId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Return logs for a rule execution", nickname = "getRuleExecutionDetails")
  @Operation(operationId = "getRuleExecutionDetails", summary = "Return logs for a rule execution ",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Return logs for a rule execution",
            content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })

  public Response
  getRuleExecutionDetails(
      @Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @PathParam("ruleExecutionId") @NotNull @Valid String ruleExecutionId) {
    // Only resources.json file is of interest to us.
    // TO DO: Remove some log lines post UI integration and testing
    log.info("ruleExecutionId {}", ruleExecutionId);
    RuleExecution ruleExecution = ruleExecutionService.get(accountId, ruleExecutionId);
    log.info("ruleExecution from mongo {}", ruleExecution);
    String executionLogPath = ruleExecution.getExecutionLogPath();
    String[] path = executionLogPath.split("/");
    String bucket = path[0];
    String objectName = executionLogPath.substring(executionLogPath.indexOf('/') + 1);
    objectName = objectName + "/resources.json";
    log.info("objectName: {}, bucket: {}", objectName, bucket);
    // Return response only when bucket type is GCS and execution has been completed by the worker.
    if (Objects.equals(ruleExecution.getExecutionLogBucketType(), "GCS")
        && ruleExecution.getExecutionCompletedAt() > 0) {
      try {
        // Other ways to return this file is by using a signed url concept. We can see this when adoption grows
        // https://cloud.google.com/storage/docs/access-control/signed-urls
        log.info("Fetching files from GCS");
        GoogleCredentials credentials = bigQueryService.getCredentials(GCP_CREDENTIALS_PATH);
        if (credentials == null) {
          try {
            log.info("WI: Using Google ADC");
            credentials = GoogleCredentials.getApplicationDefault();
          } catch (IOException e) {
            log.error("Exception in using Google ADC", e);
          }
        }

        log.info("configuration.getGcpConfig().getGcpProjectId(): {}", configuration.getGcpConfig().getGcpProjectId());
        Storage storage = StorageOptions.newBuilder()
                              .setProjectId(configuration.getGcpConfig().getGcpProjectId())
                              .setCredentials(credentials)
                              .build()
                              .getService();
        log.info("storage {}", storage);
        BlobId blobId = BlobId.of(bucket, objectName);
        log.info("blobId {}", blobId);
        Blob blob = storage.get(blobId);
        log.info("blob: {}", blob);
        return Response.ok(blob.getContent())
            .header(CONTENT_TRANSFER_ENCODING, BINARY)
            .type("text/plain; charset=UTF-8")
            .header(CONTENT_DISPOSITION, ATTACHMENT_FILENAME + RESOURCESFILENAME + EXTENSION)
            .build();
      } catch (Exception e) {
        log.error("{}", e);
        return null;
      }
    } else {
      // Non GCS Unsupported atm
      log.error("Bucket type is not GCS or execution is not yet completed");
      return null;
    }
  }

  @POST
  @Path("overview/executionDetails")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Return rule execution Details", nickname = "getExecutionDetails")
  @Operation(operationId = "getExecutionDetails", summary = "Return  rule execution Details ",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description = "Return  rule Details", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })

  public ResponseDTO<OverviewExecutionDetails>
  getExecutionDetails(
      @Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing CreateRuleExecutionFilterDTO object")
      @Valid CreateRuleExecutionFilterDTO createRuleExecutionFilterDTO) {
    RuleExecutionFilter ruleExecutionFilter = createRuleExecutionFilterDTO.getRuleExecutionFilter();
    ruleExecutionFilter.setAccountId(accountId);
    return ResponseDTO.newResponse(ruleExecutionService.getOverviewExecutionDetails(accountId, ruleExecutionFilter));
  }

  @POST
  @Path("overview/executionCostDetails")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Return rule execution Cost Details", nickname = "executionCostDetails")
  @Operation(operationId = "executionCostDetails", summary = "Return  rule execution Cost Details ",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description = "Return  rule Details", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })

  public ResponseDTO<OverviewExecutionCostDetails>
  getExecutionCostDetails(
      @Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @RequestBody(required = true, description = "Request body containing CreateRuleExecutionFilterDTO object")
      @Valid CreateRuleExecutionFilterDTO createRuleExecutionFilterDTO) {
    RuleExecutionFilter ruleExecutionFilter = createRuleExecutionFilterDTO.getRuleExecutionFilter();
    ruleExecutionFilter.setAccountId(accountId);

    // Getting recommendations data from timescale DB
    K8sRecommendationFilterDTO filter =
        RecommendationQueryHelper.buildK8sRecommendationFilterDTO(getDefaultGovernanceRecommendationOverviewFilter());
    GraphQLToRESTHelper.setDefaultPaginatedFilterValues(filter);
    final ResolutionEnvironment env = GraphQLToRESTHelper.createResolutionEnv(accountId);
    List<RecommendationItemDTO> recommendationsDTO =
        recommendationsOverviewQueryV2.recommendations(filter, env).getItems();
    if (Lists.isNullOrEmpty(recommendationsDTO)) {
      return ResponseDTO.newResponse(OverviewExecutionCostDetails.builder().build());
    }
    List<String> recommendationsIds = recommendationsDTO.stream()
                                          .map(recommendationItemDTO -> recommendationItemDTO.getId())
                                          .collect(Collectors.toList());

    return ResponseDTO.newResponse(
        ruleExecutionService.getExecutionCostDetails(accountId, ruleExecutionFilter, recommendationsIds));
  }

  @GET
  @Path("recommendation/{recommendationId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiOperation(value = "Return getRuleRecommendation details", nickname = "getRuleRecommendation")
  @Operation(operationId = "getRuleRecommendation", summary = "Return getRuleRecommendation details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            description = "getRuleRecommendation", content = { @Content(mediaType = MediaType.APPLICATION_JSON) })
      })

  public ResponseDTO<RuleExecutionList>
  getRuleRecommendation(
      @Parameter(required = true, description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier @NotNull @Valid String accountId,
      @PathParam("recommendationId") @NotNull @Valid String recommendationId) {
    return ResponseDTO.newResponse(ruleExecutionService.getRuleRecommendationDetails(recommendationId, accountId));
  }

  private CCMRecommendationFilterPropertiesDTO getDefaultGovernanceRecommendationOverviewFilter() {
    // Forming the same query passed through UI on Governance overview Page
    return CCMRecommendationFilterPropertiesDTO.builder()
        .k8sRecommendationFilterPropertiesDTO(K8sRecommendationFilterPropertiesDTO.builder()
                                                  .resourceTypes(List.of(ResourceType.GOVERNANCE))
                                                  .recommendationStates(List.of(RecommendationState.OPEN))
                                                  .build())
        .limit(5L)
        .offset(0L)
        .build();
  }
}
