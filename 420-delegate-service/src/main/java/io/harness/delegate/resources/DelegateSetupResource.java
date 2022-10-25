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
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateGroupDetails;
import io.harness.delegate.beans.DelegateGroupListing;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.rest.RestResponse;
import io.harness.service.intfc.DelegateSetupService;

import software.wings.security.annotations.AuthRule;

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
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

@Api("/setup/delegates/v2")
@Path("/setup/delegates/v2")
@Produces("application/json")
@AuthRule(permissionType = LOGGED_IN)
//@Scope(DELEGATE)
// This NG specific, switching to NG access control. AuthRule to be removed also when NG access control is fully
// enabled.
@Slf4j
@OwnedBy(HarnessTeam.DEL)
@Tag(name = "Delegate Management V2", description = "Contains APIs related to Delegate management")
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
public class DelegateSetupResource {
  private final DelegateSetupService delegateSetupService;
  private final AccessControlClient accessControlClient;

  @Inject
  public DelegateSetupResource(DelegateSetupService delegateSetupService, AccessControlClient accessControlClient) {
    this.delegateSetupService = delegateSetupService;
    this.accessControlClient = accessControlClient;
  }

  @GET
  @Timed
  @ExceptionMetered
  @Operation(operationId = "listDelegateGroupDetails",
      summary = "Lists Delegate groups details for the account, organization and project. "
          + "These include Delegate group identifier, Delegate type, group name, Delegate description, Delegate configuration id"
          + "Delegate size details, group implicit and custom selectors, last heartbeat time, is Delegate actively connected and other details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "A list of Delegate group details for the account.")
      })
  public RestResponse<DelegateGroupListing>
  list(@Parameter(description = "Account id") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateSetupService.listDelegateGroupDetails(accountId, orgId, projectId));
    }
  }

  @GET
  @Path("up-the-hierarchy")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "listDelegateGroupDetailsUpTheHierarchy",
      summary = "Lists Delegate groups details for the account, organization and project. "
          + "If no organization or project is submitted the result will ignore matching on them and will include cases where they are null. "
          + "These include Delegate group identifier, Delegate type, group name, Delegate description, Delegate configuration id"
          + "Delegate size details, group implicit and custom selectors, last heartbeat time, is Delegate actively connected.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "A list of Delegate group details for the account.")
      })
  public RestResponse<DelegateGroupListing>
  listUpTheHierarchy(@Parameter(description = "Account id") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          delegateSetupService.listDelegateGroupDetailsUpTheHierarchy(accountId, orgId, projectId));
    }
  }

  @GET
  @Path("{delegateGroupId}")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "getDelegateGroupDetails",
      summary = "Retrieves a Delegate group details object by Delegate group id.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Delegate group details object representation. "
                + "It includes Delegate group identifier, Delegate type, group name, Delegate description, Delegate configuration id"
                + "Delegate size details, group implicit and custom selectors, last heartbeat time, is Delegate actively connected.")
      })
  public RestResponse<DelegateGroupDetails>
  get(@Parameter(description = "Delegate Group Id") @PathParam("delegateGroupId") @NotEmpty String delegateGroupId,
      @Parameter(description = "Account Id") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, null), DELEGATE_VIEW_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(delegateSetupService.getDelegateGroupDetails(accountId, delegateGroupId));
    }
  }

  @PUT
  @Path("{delegateGroupId}")
  @Timed
  @ExceptionMetered
  @Operation(operationId = "updateDelegateGroupDetails", summary = "Updates Delegate group details.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Delegate group details object representation for the updated group. "
                + "It includes Delegate group identifier, Delegate type, group name, Delegate description, Delegate Configuration id"
                + "Delegate size details, group implicit and Custom Selectors, last heartbeat time, is Delegate actively connected.")
      })
  public RestResponse<DelegateGroupDetails>
  update(@Parameter(description = "Delegate group id to be updated") @PathParam(
             "delegateGroupId") @NotEmpty String delegateGroupId,
      @Parameter(description = "Account id") @QueryParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId,
      @RequestBody(required = true,
          description =
              "Delegate group details, including: groupId, delegateGroupIdentifier, delegateType, groupName, groupHostName, delegateDescription"
              + "delegateConfigurationId, sizeDetails, groupImplicitSelectors, groupCustomSelectors, delegateInsightsDetails, lastHeartBeat, activelyConnected")
      DelegateGroupDetails delegateGroupDetails) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_RESOURCE_TYPE, delegateGroupId), DELEGATE_EDIT_PERMISSION);

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          delegateSetupService.updateDelegateGroup(accountId, delegateGroupId, delegateGroupDetails));
    }
  }
}
