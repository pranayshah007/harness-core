/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static io.harness.delegate.utils.RbacConstants.DELEGATE_EDIT_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_RESOURCE_TYPE;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_VIEW_PERMISSION;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateGroupDetails;
import io.harness.delegate.beans.DelegateGroupListing;
import io.harness.delegate.filter.DelegateFilterPropertiesDTO;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.rest.RestResponse;
import io.harness.service.intfc.DelegateSetupService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

@Api("/setup/delegates/ng/v2")
@Path("/setup/delegates/ng/v2")
@Produces("application/json")
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@Tag(name = "Delegate Setup", description = "Contains APIs related to Delegate Setup")
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
public class DelegateSetupResourceV2 {
  private final DelegateSetupService delegateSetupService;
  private final AccessControlClient accessControlClient;

  @Inject
  public DelegateSetupResourceV2(DelegateSetupService delegateSetupService, AccessControlClient accessControlClient) {
    this.delegateSetupService = delegateSetupService;
    this.accessControlClient = accessControlClient;
  }

  @GET
  @Timed
  @ExceptionMetered
  @Operation(operationId = "getDelegateGroups", summary = "Lists Delegate Groups.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "A list of Delegate Groups.")
      })
  public RestResponse<DelegateGroupListing>
  list(@QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id. If left empty, all Delegate groups will be listed") @QueryParam(
          "orgId") String orgId,
      @Parameter(description = "Project Id. If lefty empty all Delegate Groups will be listed for organization")
      @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateSetupService.listDelegateGroupDetails(accountId, orgId, projectId));
    }
  }

  @POST
  @Timed
  @ExceptionMetered
  @Operation(operationId = "searchDelegateGroupsByFilter", summary = "Lists Delegate groups by applying a filter.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "List of Delegate group details which satisfy the filter.")
      })
  public RestResponse<DelegateGroupListing>
  listV2(@QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id. If left empty, Delegate groups won't be filtered by organisation")
      @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id. If left empty Delegate groups with no project will be listed") @QueryParam(
          "projectId") String projectId,
      @QueryParam(NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @Body @RequestBody(description = "Details of the Delegate filter properties to be applied")
      DelegateFilterPropertiesDTO delegateFilterPropertiesDTO, @BeanParam PageRequest pageRequest) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateSetupService.listDelegateGroupDetailsV2(
          accountId, orgId, projectId, filterIdentifier, searchTerm, delegateFilterPropertiesDTO, pageRequest));
    }
  }

  @GET
  @Path("up-the-hierarchy")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "getDelegateGroupHierarchy", summary = "Lists Delegate groups up the hierarchy.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "List of Delegate groups up the hierarchy.")
      })
  public RestResponse<DelegateGroupListing>
  listUpTheHierarchy(@QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id. If left empty, Delegate groups won't be filtered by organisation")
      @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id. If left empty Delegate groups with no project will be listed") @QueryParam(
          "projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          delegateSetupService.listDelegateGroupDetailsUpTheHierarchy(accountId, orgId, projectId));
    }
  }

  @GET
  @Path("{identifier}")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "getDelegateGroup", summary = "Get Delegate group details by identifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate group details.")
      })
  public RestResponse<DelegateGroupDetails>
  get(@Parameter(description = "Delegate Group Identifier") @PathParam("identifier") @NotEmpty String identifier,
      @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(
          description = "Organization Id. If left empty Delegate group with no organization specified will be returned")
      @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id. If left empty Delegate group with no project specified will be returned")
      @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          delegateSetupService.getDelegateGroupDetailsV2(accountId, orgId, projectId, identifier));
    }
  }

  @PUT
  @Path("{identifier}")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "updateDelegateGroup", summary = "Updates Delegate Group details by identifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate Group details for updated group.")
      })
  public RestResponse<DelegateGroupDetails>
  update(@Parameter(description = "Delegate Group Identifier") @PathParam("identifier") @NotEmpty String identifier,
      @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(
          description = "Organization Id. If left empty Delegate group with no organization specified will be updated")
      @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id. If left empty Delegate group with no project specified will be updated")
      @QueryParam("projectId") String projectId, DelegateGroupDetails delegateGroupDetails) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, identifier), DELEGATE_EDIT_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          delegateSetupService.updateDelegateGroup(accountId, orgId, projectId, identifier, delegateGroupDetails));
    }
  }
}
