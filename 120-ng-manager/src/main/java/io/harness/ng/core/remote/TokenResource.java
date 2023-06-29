/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.NGResourceFilterConstants.IDENTIFIERS;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.PageUtils.getPageRequest;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.OrgIdentifier;
import io.harness.ng.core.ProjectIdentifier;
import io.harness.ng.core.api.ApiKeyService;
import io.harness.ng.core.api.TokenService;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.TokenAggregateDTO;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.ng.core.dto.TokenFilterDTO;
import io.harness.ng.core.entities.Token.TokenKeys;
import io.harness.security.annotations.InternalApi;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.typesafe.config.Optional;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
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

@Api("token")
@Path("token")
@Produces({"application/json", "application/yaml", "text/plain"})
@Consumes({"application/json", "application/yaml", "text/plain"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Tag(name = "Token", description = "This contains APIs related to Token as defined in Harness")
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
public class TokenResource {
  private final TokenService tokenService;
  private final ApiKeyService apiKeyService;

  @POST
  @ApiOperation(value = "Create token", nickname = "createToken")
  @Operation(operationId = "createToken", summary = "Create a Token",
      description = "Creates a Token for the given API Key Type.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created Token details")
      })
  @FeatureRestrictionCheck(FeatureRestrictionName.MULTIPLE_API_TOKENS)
  public ResponseDTO<String>
  createToken(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
                  NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Valid TokenDTO tokenDTO) {
    tokenDTO.setAccountIdentifier(accountIdentifier);
    apiKeyService.validateParentIdentifier(tokenDTO.getAccountIdentifier(), tokenDTO.getOrgIdentifier(),
        tokenDTO.getProjectIdentifier(), tokenDTO.getApiKeyType(), tokenDTO.getParentIdentifier());
    return ResponseDTO.newResponse(tokenService.createToken(tokenDTO));
  }

  @PUT
  @Path("{identifier}")
  @ApiOperation(value = "Update token", nickname = "updateToken")
  @Operation(operationId = "updateToken", summary = "Update a Token",
      description = "Updates a Token for the given API Key Type.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns updated Token details")
      })
  public ResponseDTO<TokenDTO>
  updateToken(@Parameter(description = "Token ID") @NotNull @PathParam("identifier") String identifier,
      @Valid TokenDTO tokenDTO,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @AccountIdentifier @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    tokenDTO.setAccountIdentifier(accountIdentifier);
    apiKeyService.validateParentIdentifier(tokenDTO.getAccountIdentifier(), tokenDTO.getOrgIdentifier(),
        tokenDTO.getProjectIdentifier(), tokenDTO.getApiKeyType(), tokenDTO.getParentIdentifier());
    return ResponseDTO.newResponse(tokenService.updateToken(tokenDTO));
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete token", nickname = "deleteToken")
  @Operation(operationId = "deleteToken", summary = "Delete a Token",
      description = "Deletes a Token for the given API Key Type.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "It returns true if the Token is deleted successfully and false if the Token is not deleted.")
      })
  public ResponseDTO<Boolean>
  deleteToken(@Parameter(description = "Token ID") @NotNull @PathParam("identifier") String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @Optional @QueryParam(ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @Optional @QueryParam(
          PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This is the API Key type like Personal Access Key or Service Account Key.") @NotNull
      @QueryParam("apiKeyType") ApiKeyType apiKeyType,
      @Parameter(description = "ID of API key's Parent Service Account") @NotNull @QueryParam(
          "parentIdentifier") String parentIdentifier,
      @Parameter(description = "API key ID") @NotNull @QueryParam("apiKeyIdentifier") String apiKeyIdentifier) {
    apiKeyService.validateParentIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier);
    return ResponseDTO.newResponse(tokenService.revokeToken(accountIdentifier, orgIdentifier, projectIdentifier,
        apiKeyType, parentIdentifier, apiKeyIdentifier, identifier));
  }

  @GET
  @Hidden
  @InternalApi
  @ApiOperation(value = "Get token", nickname = "getToken")
  public ResponseDTO<TokenDTO> getToken(@QueryParam("tokenId") String tokenId) {
    return ResponseDTO.newResponse(tokenService.getToken(tokenId, true));
  }

  @POST
  @Path("rotate/{identifier}")
  @ApiOperation(value = "Rotate token", nickname = "rotateToken")
  @Operation(operationId = "rotateToken", summary = "Rotate a Token",
      description = "Rotates a Token for the given API Key Type.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the rotated Token")
      })
  public ResponseDTO<String>
  rotateToken(@Parameter(description = "Token Identifier") @NotNull @PathParam("identifier") String identifier,
      @Parameter(description = "Time stamp when the Token is to be rotated") @QueryParam(
          "rotateTimestamp") Long rotateTimestamp,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @Optional @QueryParam(ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @Optional @QueryParam(
          PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This is the API Key type like Personal Access Key or Service Account Key.") @NotNull
      @QueryParam("apiKeyType") ApiKeyType apiKeyType,
      @Parameter(description = "ID of API key's Parent Service Account") @NotNull @QueryParam(
          "parentIdentifier") String parentIdentifier,
      @Parameter(description = "API key ID") @NotNull @QueryParam("apiKeyIdentifier") String apiKeyIdentifier) {
    apiKeyService.validateParentIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, apiKeyType, parentIdentifier);
    return ResponseDTO.newResponse(tokenService.rotateToken(accountIdentifier, orgIdentifier, projectIdentifier,
        apiKeyType, parentIdentifier, apiKeyIdentifier, identifier, Instant.ofEpochMilli(rotateTimestamp)));
  }

  @GET
  @Path("aggregate")
  @ApiOperation(value = "List tokens", nickname = "listAggregatedTokens")
  @Operation(operationId = "listAggregatedTokens", summary = "List all Tokens",
      description = "Lists all the Tokens matching the given search criteria.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of Aggregated Tokens.")
      })
  public ResponseDTO<PageResponse<TokenAggregateDTO>>
  listAggregatedTokens(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                           ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @Optional @QueryParam(ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @Optional @QueryParam(
          PROJECT_KEY) @ProjectIdentifier String projectIdentifier,
      @Parameter(description = "This is the API Key type like Personal Access Key or Service Account Key.") @NotNull
      @QueryParam("apiKeyType") ApiKeyType apiKeyType,
      @Parameter(description = "ID of API key's Parent Service Account") @QueryParam(
          "parentIdentifier") String parentIdentifier,
      @Parameter(description = "API key ID") @QueryParam("apiKeyIdentifier") String apiKeyIdentifier,
      @Parameter(description = "This is the list of Token IDs. Details specific to these IDs would be fetched.")
      @Optional @QueryParam(IDENTIFIERS) List<String> identifiers, @BeanParam PageRequest pageRequest,
      @Parameter(
          description =
              "This would be used to filter Tokens. Any Token having the specified string in its Name, ID and Tag would be filtered.")
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Parameter(
          description =
              "Boolean value to indicate whether to list only active tokens or all tokens. By default, all tokens will be listed.")
      @QueryParam("includeOnlyActiveTokens") boolean includeOnlyActiveTokens) {
    if (isNotEmpty(apiKeyIdentifier) && isEmpty(parentIdentifier)) {
      throw new InvalidRequestException(
          "Need to pass parentIdentifier along with apikeyIdentifier to list the tokens.");
    }

    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order =
          SortOrder.Builder.aSortOrder().withField(TokenKeys.lastModifiedAt, SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    TokenFilterDTO filterDTO = TokenFilterDTO.builder()
                                   .accountIdentifier(accountIdentifier)
                                   .orgIdentifier(orgIdentifier)
                                   .projectIdentifier(projectIdentifier)
                                   .parentIdentifier(parentIdentifier)
                                   .apiKeyType(apiKeyType)
                                   .searchTerm(searchTerm)
                                   .apiKeyIdentifier(apiKeyIdentifier)
                                   .identifiers(identifiers)
                                   .includeOnlyActiveTokens(includeOnlyActiveTokens)
                                   .build();
    tokenService.validateTokenListPermissions(filterDTO);
    PageResponse<TokenAggregateDTO> requestDTOS =
        tokenService.listAggregateTokens(accountIdentifier, getPageRequest(pageRequest), filterDTO);
    return ResponseDTO.newResponse(requestDTOS);
  }

  @POST
  @Path("validate")
  @ApiOperation(value = "Validate token", nickname = "validateToken")
  @Operation(operationId = "validateToken", summary = "Validate a Token",
      description = "Validate a Token for the given account.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Validate a Token for the given account")
      })
  public ResponseDTO<TokenDTO>
  validateToken(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(ACCOUNT_KEY)
                @AccountIdentifier String accountIdentifier, @NotNull String apiKey) {
    log.info(String.format(
        "API_KEY_NG_VALIDATE: Validate token for API key or JWT token called for account id: %s", accountIdentifier));
    TokenDTO tokenDTO = tokenService.validateToken(accountIdentifier, apiKey);
    return ResponseDTO.newResponse(tokenDTO);
  }
}
