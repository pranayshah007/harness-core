/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.NGResourceFilterConstants.IDENTIFIER;
import static io.harness.NGResourceFilterConstants.IDENTIFIERS;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.utils.PageUtils.getPageRequest;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.ApiKeyAggregateDTO;
import io.harness.ng.core.dto.ApiKeyDTO;
import io.harness.ng.core.dto.ApiKeyFilterDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entities.ApiKey.ApiKeyKeys;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.typesafe.config.Optional;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("apikey")
@Path("apikey")
@Produces({"application/json", "application/yaml", "text/plain"})
@Consumes({"application/json", "application/yaml", "text/plain"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Tag(name = "ApiKey", description = "This fetches API keys defined in Harness")
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
@Slf4j
@OwnedBy(PL)
public class ApiKeyResource {
  private final ApiKeyService apiKeyService;

  @POST
  @ApiOperation(value = "Create API key", nickname = "createApiKey")
  @Operation(operationId = "createApiKey", summary = "Creates an API key",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the created API key")
      })
  @FeatureRestrictionCheck(FeatureRestrictionName.MULTIPLE_API_KEYS)
  public ResponseDTO<ApiKeyDTO>
  createApiKey(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Valid ApiKeyDTO apiKeyDTO) {
    apiKeyDTO.setAccountIdentifier(accountIdentifier);
    apiKeyService.validateParentIdentifier(apiKeyDTO.getAccountIdentifier(), apiKeyDTO.getOrgIdentifier(),
        apiKeyDTO.getProjectIdentifier(), apiKeyDTO.getApiKeyType(), apiKeyDTO.getParentIdentifier());
    ApiKeyDTO apiKey = apiKeyService.createApiKey(apiKeyDTO);
    return ResponseDTO.newResponse(apiKey);
  }

  @PUT
  @Path("{identifier}")
  @ApiOperation(value = "Update API key", nickname = "updateApiKey")
  @Operation(operationId = "updateApiKey", summary = "Updates API Key for the provided ID",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated API key")
      })
  public ResponseDTO<ApiKeyDTO>
  updateApiKey(@Valid ApiKeyDTO apiKeyDTO,
      @Parameter(description = "This is the API key ID") @NotNull @PathParam("identifier") String identifier) {
    apiKeyService.validateParentIdentifier(apiKeyDTO.getAccountIdentifier(), apiKeyDTO.getOrgIdentifier(),
        apiKeyDTO.getProjectIdentifier(), apiKeyDTO.getApiKeyType(), apiKeyDTO.getParentIdentifier());
    ApiKeyDTO apiKey = apiKeyService.updateApiKey(apiKeyDTO);
    return ResponseDTO.newResponse(apiKey);
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete API key", nickname = "deleteApiKey")
  @Operation(operationId = "deleteApiKey", summary = "Deletes the API Key corresponding to the provided ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Returns a boolean value. The value is True if the API Key is successfully deleted, else it is False.")
      })
  public ResponseDTO<Boolean>
  deleteApiKey(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @Optional @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @Optional @QueryParam(PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "This is the API Key type like Personal Access Key or Service Account Key.") @NotNull
      @QueryParam("apiKeyType") ApiKeyType apiKeyType,
      @Parameter(description = "Id of API key's Parent Service Account") @NotNull @QueryParam(
          "parentIdentifier") String parentIdentifier,
      @Parameter(description = "This is the API key ID") @NotNull @PathParam(IDENTIFIER_KEY) String identifier) {
    apiKeyService.validateParentIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier);
    boolean deleted = apiKeyService.deleteApiKey(
        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, identifier);
    return ResponseDTO.newResponse(deleted);
  }

  @GET
  @ApiOperation(value = "List api keys", nickname = "listApiKeys")
  @Operation(operationId = "listApiKeys",
      summary = "Fetches the list of API Keys corresponding to the request's filter criteria.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of API keys.")
      })
  public ResponseDTO<List<ApiKeyDTO>>
  listApiKeys(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @Optional @QueryParam(ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @Optional @QueryParam(PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "This is the API Key type like Personal Access Key or Service Account Key.") @NotNull
      @QueryParam("apiKeyType") ApiKeyType apiKeyType,
      @Parameter(description = "ID of API key's Parent Service Account") @NotNull @QueryParam(
          "parentIdentifier") String parentIdentifier,
      @Parameter(description = "This is the list of API Key IDs. Details specific to these IDs would be fetched.")
      @Optional @QueryParam(IDENTIFIERS) List<String> identifiers) {
    apiKeyService.validateParentIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier);
    List<ApiKeyDTO> apiKeyDTOs = apiKeyService.listApiKeys(
        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, identifiers);
    return ResponseDTO.newResponse(apiKeyDTOs);
  }

  @GET
  @Path("aggregate")
  @ApiOperation(value = "List api key", nickname = "listAggregatedApiKeys")
  @Operation(operationId = "listApiKeys",
      summary = "Fetches the list of Aggregated API Keys corresponding to the request's filter criteria.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the Paginated list of Aggregated API keys.")
      })
  public ResponseDTO<PageResponse<ApiKeyAggregateDTO>>
  listAggregatedApiKeys(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                            ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @Optional @QueryParam(ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @Optional @QueryParam(
          PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This is the API Key type like Personal Access Key or Service Account Key.") @NotNull
      @QueryParam("apiKeyType") ApiKeyType apiKeyType,
      @Parameter(description = "ID of API key's Parent Service Account") @NotNull @QueryParam(
          "parentIdentifier") String parentIdentifier,
      @Parameter(description = "This is the list of API Key IDs. Details specific to these IDs would be fetched.")
      @Optional @QueryParam(IDENTIFIERS) List<String> identifiers, @BeanParam PageRequest pageRequest,
      @Parameter(
          description =
              "This would be used to filter API keys. Any API key having the specified string in its Name, ID and Tag would be filtered.")
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    apiKeyService.validateParentIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier);
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(ApiKeyKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    ApiKeyFilterDTO filterDTO = ApiKeyFilterDTO.builder()
                                    .accountIdentifier(accountIdentifier)
                                    .orgIdentifier(orgIdentifier)
                                    .projectIdentifier(projectIdentifier)
                                    .parentIdentifier(parentIdentifier)
                                    .apiKeyType(apiKeyType)
                                    .searchTerm(searchTerm)
                                    .identifiers(identifiers)
                                    .build();
    PageResponse<ApiKeyAggregateDTO> requestDTOS =
        apiKeyService.listAggregateApiKeys(accountIdentifier, getPageRequest(pageRequest), filterDTO);
    return ResponseDTO.newResponse(requestDTOS);
  }

  @GET
  @Path("aggregate/{identifier}")
  @ApiOperation(value = "Get API key", nickname = "getAggregatedApiKey")
  @Operation(operationId = "getAggregatedApiKey",
      summary = "Fetches the API Keys details corresponding to the provided ID and Scope.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the API key")
      })
  public ResponseDTO<ApiKeyAggregateDTO>
  getAggregatedApiKey(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                          ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @Optional @QueryParam(ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @Optional @QueryParam(
          PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This is the API Key type like Personal Access Key or Service Account Key.") @NotNull
      @QueryParam("apiKeyType") ApiKeyType apiKeyType,
      @Parameter(description = "ID of API key's Parent Service Account") @NotNull @QueryParam(
          "parentIdentifier") String parentIdentifier,
      @Parameter(description = "This is the API key ID") @PathParam(IDENTIFIER) @ResourceIdentifier String identifier) {
    apiKeyService.validateParentIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier);
    ApiKeyAggregateDTO aggregateDTO = apiKeyService.getApiKeyAggregateDTO(
        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier, identifier);
    return ResponseDTO.newResponse(aggregateDTO);
  }
}
