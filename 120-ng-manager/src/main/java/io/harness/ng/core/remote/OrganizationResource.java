/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGConstants.DEFAULT_ORG_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.accesscontrol.PlatformPermissions.CREATE_ORGANIZATION_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.DELETE_ORGANIZATION_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.EDIT_ORGANIZATION_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_ORGANIZATION_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.ORGANIZATION;
import static io.harness.ng.core.remote.OrganizationMapper.toResponseWrapper;
import static io.harness.utils.PageUtils.getNGPageResponse;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.lang.Long.parseLong;
import static javax.ws.rs.core.HttpHeaders.IF_MATCH;
import static org.apache.commons.lang3.StringUtils.isNumeric;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.ScopeInfo;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.ResourceIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SortOrder;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.OrganizationFilterDTO;
import io.harness.ng.core.dto.OrganizationRequest;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Organization.OrganizationKeys;
import io.harness.ng.core.services.OrganizationService;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.utils.PageUtils;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
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
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;

@OwnedBy(PL)
@Api("organizations")
@Path("organizations")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Tag(name = "Organization", description = "This contains APIs related to Organization as defined in Harness")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Not Found",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
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
@NextGenManagerAuth
public class OrganizationResource {
  private final OrganizationService organizationService;

  @POST
  @ApiOperation(value = "Create an Organization", nickname = "postOrganization")
  @Operation(operationId = "postOrganization", summary = "Create an Organization",
      description = "Creates a new Organization.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns created Organization details")
      })
  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = CREATE_ORGANIZATION_PERMISSION)
  public ResponseDTO<OrganizationResponse>
  create(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @RequestBody(required = true, description = "Details of the Organization to create") @NotNull
      @Valid OrganizationRequest organizationDTO, @Context ScopeInfo scopeInfo) {
    if (DEFAULT_ORG_IDENTIFIER.equals(organizationDTO.getOrganization().getIdentifier())) {
      throw new InvalidRequestException(
          String.format("%s cannot be used as org identifier", DEFAULT_ORG_IDENTIFIER), USER);
    }
    Organization updatedOrganization = organizationService.create(accountIdentifier, organizationDTO.getOrganization());
    return ResponseDTO.newResponse(updatedOrganization.getVersion().toString(), toResponseWrapper(updatedOrganization));
  }

  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Get an Organization by identifier", nickname = "getOrganization")
  @Operation(operationId = "getOrganization", summary = "List Organization details",
      description = "Lists Organization details using an Account and Organization ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the Organization details with the passed Account Identifier and Org Identifier")
      })
  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = VIEW_ORGANIZATION_PERMISSION)
  public ResponseDTO<OrganizationResponse>
  get(@Parameter(description = ORG_PARAM_MESSAGE) @NotNull @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
    Optional<Organization> organizationOptional = organizationService.get(accountIdentifier, identifier);
    if (!organizationOptional.isPresent()) {
      throw new NotFoundException(String.format("Organization with identifier [%s] not found", identifier));
    }
    return ResponseDTO.newResponse(
        organizationOptional.get().getVersion().toString(), toResponseWrapper(organizationOptional.get()));
  }

  @GET
  @ApiOperation(value = "Get Organization list", nickname = "getOrganizationList")
  @Operation(operationId = "getOrganizationList", summary = "List Organizations by filter",
      description = "List all Organizations matching the given search criteria.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns list of Organizations")
      })
  public ResponseDTO<PageResponse<OrganizationResponse>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = "This is the list of Org Key IDs. Details specific to these IDs would be fetched.")
      @QueryParam(NGResourceFilterConstants.IDENTIFIERS) List<String> identifiers,
      @Parameter(
          description =
              "This would be used to filter Organizations. Any Organization having the specified string in its Name, ID and Tag would be filtered.")
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @BeanParam PageRequest pageRequest, @Context ScopeInfo scopeInfo) {
    OrganizationFilterDTO organizationFilterDTO =
        OrganizationFilterDTO.builder().searchTerm(searchTerm).identifiers(identifiers).build();
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder harnessManagedOrder =
          SortOrder.Builder.aSortOrder().withField(OrganizationKeys.harnessManaged, SortOrder.OrderType.DESC).build();
      SortOrder nameOrder =
          SortOrder.Builder.aSortOrder().withField(OrganizationKeys.name, SortOrder.OrderType.ASC).build();
      pageRequest.setSortOrders(ImmutableList.of(harnessManagedOrder, nameOrder));
      organizationFilterDTO.setIgnoreCase(true);
    }
    Page<Organization> orgsPage =
        organizationService.listPermittedOrgs(accountIdentifier, getPageRequest(pageRequest), organizationFilterDTO);
    return ResponseDTO.newResponse(getNGPageResponse(orgsPage.map(OrganizationMapper::toResponseWrapper)));
  }

  @POST
  @Hidden
  @Path("all-organizations")
  @ApiOperation(value = "Get All Organizations list", nickname = "getAllOrganizationList", hidden = true)
  @InternalApi
  public ResponseDTO<PageResponse<OrganizationResponse>> listAllOrganizations(
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = "Search term") @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @RequestBody(
          required = true, description = "list of ProjectIdentifiers to filter results by") List<String> identifiers) {
    OrganizationFilterDTO organizationFilterDTO =
        OrganizationFilterDTO.builder().searchTerm(searchTerm).identifiers(identifiers).build();
    Page<Organization> orgsPage = organizationService.listPermittedOrgs(accountIdentifier,
        PageUtils.getPageRequest(0, NGCommonEntityConstants.MAX_PAGE_SIZE, new ArrayList<>()), organizationFilterDTO);
    return ResponseDTO.newResponse(getNGPageResponse(orgsPage.map(OrganizationMapper::toResponseWrapper)));
  }

  @PUT
  @Path("{identifier}")
  @ApiOperation(value = "Update an Organization by ID", nickname = "putOrganization")
  @Operation(operationId = "putOrganization", summary = "Update an Organization",
      description = "Updates Organization settings.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated Organization details")
      })
  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = EDIT_ORGANIZATION_PERMISSION)
  public ResponseDTO<OrganizationResponse>
  update(@Parameter(description = "Version number of the Organization") @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = ORG_PARAM_MESSAGE) @NotNull @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @RequestBody(required = true,
          description =
              "This is the updated Organization. Please provide values for all fields, not just the fields you are updating")
      @NotNull @Valid OrganizationRequest organizationDTO,
      @Context ScopeInfo scopeInfo) {
    if (DEFAULT_ORG_IDENTIFIER.equals(identifier)) {
      throw new InvalidRequestException(
          String.format(
              "Update operation not supported for Default Organization (identifier: [%s])", DEFAULT_ORG_IDENTIFIER),
          USER);
    }
    organizationDTO.getOrganization().setVersion(isNumeric(ifMatch) ? parseLong(ifMatch) : null);
    Organization updatedOrganization =
        organizationService.update(accountIdentifier, identifier, organizationDTO.getOrganization());
    return ResponseDTO.newResponse(updatedOrganization.getVersion().toString(), toResponseWrapper(updatedOrganization));
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete an Organization by identifier", nickname = "deleteOrganization")
  @Operation(operationId = "deleteOrganization", summary = "Delete an Organization",
      description = "Deletes Organization for the given ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "It returns true if the Organization is deleted successfully and false if the Organization is not deleted.")
      })
  @NGAccessControlCheck(resourceType = ORGANIZATION, permission = DELETE_ORGANIZATION_PERMISSION)
  public ResponseDTO<Boolean>
  delete(@Parameter(description = "Version number of the Organization") @HeaderParam(IF_MATCH) String ifMatch,
      @Parameter(description = ORG_PARAM_MESSAGE) @NotNull @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) @ResourceIdentifier String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
    if (DEFAULT_ORG_IDENTIFIER.equals(identifier)) {
      throw new InvalidRequestException(
          String.format(
              "Delete operation not supported for Default Organization (identifier: [%s])", DEFAULT_ORG_IDENTIFIER),
          USER);
    }
    return ResponseDTO.newResponse(
        organizationService.delete(accountIdentifier, identifier, isNumeric(ifMatch) ? parseLong(ifMatch) : null));
  }
}
