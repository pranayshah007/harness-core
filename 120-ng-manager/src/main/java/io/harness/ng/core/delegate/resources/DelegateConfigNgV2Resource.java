/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.delegate.resources;

import static io.harness.delegate.utils.RbacConstants.DELEGATE_CONFIG_DELETE_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_CONFIG_EDIT_PERMISSION;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_CONFIG_RESOURCE_TYPE;
import static io.harness.delegate.utils.RbacConstants.DELEGATE_CONFIG_VIEW_PERMISSION;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.delegate.beans.DelegateGroup;
import io.harness.delegate.beans.DelegateGroupTags;
import io.harness.delegate.beans.DelegateProfileDetailsNg;
import io.harness.delegate.beans.ScopingRuleDetailsNg;
import io.harness.delegate.filter.DelegateProfileFilterPropertiesDTO;
import io.harness.ng.core.api.DelegateProfileManagerNgService;
import io.harness.ng.core.delegate.client.DelegateNgManagerCgManagerClient;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.remote.client.RestClientUtils;
import io.harness.rest.RestResponse;

import software.wings.security.annotations.AuthRule;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Body;

@Path("/v2")
@Api("/v2")
@Produces("application/json")
@AuthRule(permissionType = LOGGED_IN)
@OwnedBy(HarnessTeam.DEL)
@Hidden
@Tag(name = "Delegate Configuration Resource",
    description = "Contains APIs related to Delegate Configuration management")
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
public class DelegateConfigNgV2Resource {
  private final DelegateProfileManagerNgService delegateProfileManagerNgService;
  private final AccessControlClient accessControlClient;
  private final DelegateNgManagerCgManagerClient delegateNgManagerCgManagerClient;

  @Inject
  public DelegateConfigNgV2Resource(DelegateProfileManagerNgService delegateProfileManagerNgService,
      AccessControlClient accessControlClient, DelegateNgManagerCgManagerClient delegateNgManagerCgManagerClient) {
    this.delegateProfileManagerNgService = delegateProfileManagerNgService;
    this.accessControlClient = accessControlClient;
    this.delegateNgManagerCgManagerClient = delegateNgManagerCgManagerClient;
  }

  @GET
  @Path("/accounts/{accountId}/delegate-configs/{delegateConfigIdentifier}")
  @ApiOperation(value = "Gets Delegate config by identifier", nickname = "getDelegateConfigNgV2")
  @Timed
  @ExceptionMetered
  @Hidden
  @Operation(operationId = "getDelegateConfigrationDetailsV2",
      summary = "Retrieves Delegate Configuration details for given Delegate Configuration identifier.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "A Delegate Configuration. "
                + "It includes uuid, accountId, name, description, startupScript, scopingRules, selectors, numberOfDelegates and other info.")
      })
  public RestResponse<DelegateProfileDetailsNg>
  get(@Parameter(description = "Delegate Configuration identifier") @PathParam(
          "delegateConfigIdentifier") @NotEmpty String delegateConfigIdentifier,
      @Parameter(description = "Account id") @PathParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_CONFIG_RESOURCE_TYPE, null), DELEGATE_CONFIG_VIEW_PERMISSION);

    return new RestResponse<>(
        delegateProfileManagerNgService.get(accountId, orgId, projectId, delegateConfigIdentifier));
  }

  @PUT
  @Path("/accounts/{accountId}/delegate-configs/{delegateConfigIdentifier}/scoping-rules")
  @Timed
  @ExceptionMetered
  @ApiOperation(value = "Updates the Scoping Rules inside the Delegate config", nickname = "updateScopingRulesNgV2")
  @Hidden
  @Operation(operationId = "updateScopingRulesV2",
      summary = "Updates Scoping Rules for the Delegate Configuration specified by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate Configuration which was updated.")
      })
  public RestResponse<DelegateProfileDetailsNg>
  updateScopingRules(@Parameter(description = "Delegate Configuration identifier") @PathParam(
                         "delegateConfigIdentifier") @NotEmpty String delegateConfigIdentifier,
      @Parameter(description = "Account id") @PathParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId,
      @RequestBody(required = true,
          description = "List of Delegate Scoping Rules to be updated") List<ScopingRuleDetailsNg> scopingRules) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_CONFIG_RESOURCE_TYPE, delegateConfigIdentifier), DELEGATE_CONFIG_EDIT_PERMISSION);

    return new RestResponse<>(delegateProfileManagerNgService.updateScopingRules(
        accountId, orgId, projectId, delegateConfigIdentifier, scopingRules));
  }

  @DELETE
  @Path("/accounts/{accountId}/delegate-configs/{delegateConfigIdentifier}")
  @ApiOperation(value = "Deletes a Delegate config by identifier", nickname = "deleteDelegateConfigNgV2")
  @Timed
  @ExceptionMetered
  @Hidden
  @Operation(operationId = "deleteDelegateConfigV2", summary = "Deletes Delegate Configuration specified by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Boolean value resulting true if deletion was successful.")
      })
  public ResponseDTO<Boolean>
  delete(@Parameter(description = "Delegate Configuration identifier") @PathParam(
             "delegateConfigIdentifier") @NotEmpty String delegateConfigIdentifier,
      @Parameter(description = "Account id") @PathParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_CONFIG_RESOURCE_TYPE, delegateConfigIdentifier), DELEGATE_CONFIG_DELETE_PERMISSION);
    return ResponseDTO.newResponse(
        delegateProfileManagerNgService.delete(accountId, orgId, projectId, delegateConfigIdentifier));
  }

  @PUT
  @Path("/accounts/{accountId}/delegate-configs/{delegateConfigIdentifier}/selectors")
  @ApiOperation(value = "Updates the selectors inside the Delegate config", nickname = "updateSelectorsNgV2")
  @Timed
  @ExceptionMetered
  @Hidden
  @Operation(operationId = "updateDelegateSelectorsV2",
      summary = "Updates Delegate selectors for Delegate Configuration specified by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate Configuration which was updated.")
      })
  public RestResponse<DelegateProfileDetailsNg>
  updateSelectors(@Parameter(description = "Delegate Configuration identifier") @PathParam(
                      "delegateConfigIdentifier") @NotEmpty String delegateConfigIdentifier,
      @Parameter(description = "Account id") @PathParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId,
      @RequestBody(description = "List of Delegate selectors to be updated") List<String> selectors) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_CONFIG_RESOURCE_TYPE, delegateConfigIdentifier), DELEGATE_CONFIG_EDIT_PERMISSION);

    return new RestResponse<>(delegateProfileManagerNgService.updateSelectors(
        accountId, orgId, projectId, delegateConfigIdentifier, selectors));
  }

  @PUT
  @Path("/accounts/{accountId}/delegate-configs/{delegateConfigIdentifier}")
  @ApiOperation(value = "Updates a Delegate Configuration", nickname = "updateDelegateConfigNgV2")
  @Timed
  @ExceptionMetered
  @Hidden
  @Operation(operationId = "updateDelegateConfigurationV2",
      summary = "Updates Delegate Configuration specified by Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate Configuration which was updated.")
      })
  public RestResponse<DelegateProfileDetailsNg>
  update(@Parameter(description = "Delegate Configuration identifier") @PathParam(
             "delegateConfigIdentifier") @NotEmpty String delegateConfigIdentifier,
      @Parameter(description = "Account id") @PathParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId,
      @RequestBody(required = true,
          description =
              "Delegate configuration details to be updated. These include name, startupScript, scopingRules, selectors")
      @NotNull DelegateProfileDetailsNg delegateConfig) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_CONFIG_RESOURCE_TYPE, delegateConfigIdentifier), DELEGATE_CONFIG_EDIT_PERMISSION);

    delegateConfig.setAccountId(accountId);
    delegateConfig.setIdentifier(delegateConfigIdentifier);
    delegateConfig.setOrgIdentifier(orgId);
    delegateConfig.setProjectIdentifier(projectId);
    return new RestResponse<>(delegateProfileManagerNgService.updateV2(
        accountId, orgId, projectId, delegateConfigIdentifier, delegateConfig));
  }

  @POST
  @Path("/delegate-configs")
  @ApiOperation(value = "Adds a Delegate profile", nickname = "addDelegateProfileNgV2noQueryParamsV2")
  @Hidden
  @Operation(operationId = "createDelegateConfigurationV2",
      summary = "Creates Delegate Configuration specified by config details",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate Configuration which was created.")
      })
  public RestResponse<DelegateProfileDetailsNg>
  add(@RequestBody(required = true,
      description =
          "Delegate Configuration to be created. These include uuid, identifier, accountId, orgId, projId, name, startupScript, scopingRules, selectors...")
      @NotNull DelegateProfileDetailsNg delegateProfile) {
    String accountId = delegateProfile.getAccountId();
    String orgId = delegateProfile.getOrgIdentifier();
    String projectId = delegateProfile.getProjectIdentifier();
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_CONFIG_RESOURCE_TYPE, null), DELEGATE_CONFIG_EDIT_PERMISSION);

    return new RestResponse<>(delegateProfileManagerNgService.add(delegateProfile));
  }

  @POST
  @Path("/accounts/{accountId}/delegate-configs")
  @ApiOperation(value = "Adds a Delegate profile", nickname = "addDelegateProfileNgV2")
  @Hidden
  @Operation(operationId = "addDelegateConfigurationForAccount",
      summary = "Creates Delegate Configuration specified by config details for specified account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate Configuration which was created.")
      })
  public RestResponse<DelegateProfileDetailsNg>
  add(@Parameter(description = "Account id") @PathParam("accountId") @NotEmpty String accountId,
      @RequestBody(required = true,
          description =
              "Delegate Configuration to be created. These include uuid, identifier, accountId, orgId, projId, name, startupScript, scopingRules, selectors...")
      @NotNull DelegateProfileDetailsNg delegateProfile) {
    delegateProfile.setAccountId(accountId);
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, delegateProfile.getOrgIdentifier(), delegateProfile.getProjectIdentifier()),
        Resource.of(DELEGATE_CONFIG_RESOURCE_TYPE, null), DELEGATE_CONFIG_EDIT_PERMISSION);
    return new RestResponse<>(delegateProfileManagerNgService.add(delegateProfile));
  }

  @GET
  @ApiOperation(value = "Lists the Delegate Configurations", nickname = "listDelegateConfigsNgV2")
  @Timed
  @Path("/accounts/{accountId}/delegate-configs")
  @ExceptionMetered
  @Hidden
  @Operation(operationId = "getDelegateConfigurationsForAccountV2",
      summary = "Lists Delegate Configuration for specified account, org and project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "A list of Delegate Configurations for the account, org and project")
      })
  public RestResponse<PageResponse<DelegateProfileDetailsNg>>
  list(@BeanParam PageRequest<DelegateProfileDetailsNg> pageRequest,
      @Parameter(description = "Account id") @PathParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_CONFIG_RESOURCE_TYPE, null), DELEGATE_CONFIG_VIEW_PERMISSION);

    return new RestResponse<>(delegateProfileManagerNgService.list(accountId, pageRequest, orgId, projectId));
  }

  @POST
  @ApiOperation(value = "Lists the Delegate configs with filter", nickname = "listDelegateConfigsNgV2WithFilter")
  @Timed
  @Path("/accounts/{accountId}/delegate-configs/listV2")
  @ExceptionMetered
  @Hidden
  @Operation(operationId = "getDelegateConfigurationsWithFiltering",
      summary = "Lists Delegate Configuration for specified account, org and project and filter applied",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "A list of Delegate Configurations for the account, org and project and filter applied")
      })
  public RestResponse<PageResponse<DelegateProfileDetailsNg>>
  listV2(@Parameter(description = "Account id") @PathParam("accountId") @NotEmpty String accountId,
      @Parameter(description = "Organization Id") @QueryParam("orgId") String orgId,
      @Parameter(description = "Project Id") @QueryParam("projectId") String projectId,
      @Parameter(description = "Filter identifier") @QueryParam(
          NGResourceFilterConstants.FILTER_KEY) String filterIdentifier,
      @Parameter(description = "Search term") @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @RequestBody(
          description =
              "Delegate Configuration filter properties: name, identifier, description, approvalRequired, list of selectors ")
      @Body DelegateProfileFilterPropertiesDTO delegateProfileFilterPropertiesDTO,
      @BeanParam PageRequest<DelegateProfileDetailsNg> pageRequest) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountId, orgId, projectId),
        Resource.of(DELEGATE_CONFIG_RESOURCE_TYPE, null), DELEGATE_CONFIG_VIEW_PERMISSION);

    return new RestResponse<>(delegateProfileManagerNgService.listV2(
        accountId, orgId, projectId, filterIdentifier, searchTerm, delegateProfileFilterPropertiesDTO, pageRequest));
  }

  @PUT
  @ApiOperation(value = "Update tags for the Delegate group", nickname = "updateTagsForDelegateGroup")
  @Timed
  @Path("{identifier}/tags")
  @ExceptionMetered
  @Operation(operationId = "updateTagsForDelegateGroup",
      summary =
          "Note: This api is deprecated. Please start using Delegate Group Tags Apis. This Api updates tags for the Delegate group",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Delegate Group details for updated group")
      })
  @Deprecated
  public RestResponse<DelegateGroup>
  updateTagsForDelegateGroup(@Parameter(description = "Delegate Group Name") @PathParam(
                                 NGCommonEntityConstants.IDENTIFIER_KEY) @NotEmpty String groupName,
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountId,
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgId,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectId,
      @RequestBody(required = true, description = "List of tags") DelegateGroupTags tags) {
    return new RestResponse<>(RestClientUtils.getResponse(
        delegateNgManagerCgManagerClient.updateDelegateGroupTags_old(groupName, accountId, orgId, projectId, tags)));
  }
}
