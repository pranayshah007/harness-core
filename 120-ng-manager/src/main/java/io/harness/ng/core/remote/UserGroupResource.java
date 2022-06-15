/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.GROUP_IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USERGROUP_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_USERGROUP_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USERGROUP;
import static io.harness.ng.core.utils.NGUtils.verifyValuesNotChanged;
import static io.harness.ng.core.utils.UserGroupMapper.toDTO;
import static io.harness.utils.PageUtils.getNGPageResponse;
import static io.harness.utils.PageUtils.getPageRequest;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.SortOrder;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.accesscontrol.scopes.ScopeNameDTO;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.user.remote.dto.UserFilter;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.usergroups.filter.UserGroupFilterType;
import io.harness.ng.core.utils.UserGroupMapper;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.NextGenManagerAuth;

import software.wings.beans.sso.SSOType;
import software.wings.beans.sso.SamlLinkGroupRequest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.domain.Page;
import retrofit2.http.Body;

@OwnedBy(PL)
@Api("user-groups")
@Path("user-groups")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error"),
          @ApiResponse(code = 403, response = AccessDeniedErrorDTO.class, message = "Unauthorized")
    })
@Tag(name = "User Group", description = "This contains APIs related to User Group as defined in Harness")
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
@NextGenManagerAuth
public class UserGroupResource {
  private final UserGroupService userGroupService;
  private final AccessControlClient accessControlClient;

  @POST
  @ApiOperation(value = "Create a User Group", nickname = "postUserGroup")
  @Operation(operationId = "postUserGroup", summary = "Create User Group",
      description = "Create a User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the successfully created User Group")
      })
  public ResponseDTO<UserGroupDTO>
  create(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(
          description = "User Group entity to be created", required = true) @NotNull @Valid UserGroupDTO userGroupDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, null), MANAGE_USERGROUP_PERMISSION);
    validateScopes(accountIdentifier, orgIdentifier, projectIdentifier, userGroupDTO);
    userGroupDTO.setAccountIdentifier(accountIdentifier);
    userGroupDTO.setOrgIdentifier(orgIdentifier);
    userGroupDTO.setProjectIdentifier(projectIdentifier);
    UserGroup userGroup = userGroupService.create(userGroupDTO);
    return ResponseDTO.newResponse(Long.toString(userGroup.getVersion()), toDTO(userGroup));
  }

  @PUT
  @ApiOperation(value = "Update a User Group", nickname = "putUserGroup")
  @Operation(operationId = "putUserGroup", description = "Update a User Group in an account/org/project",
      summary = "Update User Group",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the successfully updated User Group")
      })
  public ResponseDTO<UserGroupDTO>
  update(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(description = "User Group entity with the updates",
          required = true) @NotNull @Valid UserGroupDTO userGroupDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, userGroupDTO.getIdentifier()), MANAGE_USERGROUP_PERMISSION);
    validateScopes(accountIdentifier, orgIdentifier, projectIdentifier, userGroupDTO);
    checkExternallyManaged(accountIdentifier, orgIdentifier, projectIdentifier, userGroupDTO.getIdentifier());
    userGroupDTO.setAccountIdentifier(accountIdentifier);
    userGroupDTO.setOrgIdentifier(orgIdentifier);
    userGroupDTO.setProjectIdentifier(projectIdentifier);
    UserGroup userGroup = userGroupService.update(userGroupDTO);
    return ResponseDTO.newResponse(Long.toString(userGroup.getVersion()), toDTO(userGroup));
  }

  @PUT
  @Path("/copy")
  @ApiOperation(value = "Copy a User Group to several scopes", nickname = "copyUserGroup")
  @Operation(operationId = "copyUserGroup", summary = "Copy User Group",
      description = "Copy a User Group in an account/org/project",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Returns whether the copy was successful") })
  public ResponseDTO<Boolean>
  copy(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = GROUP_IDENTIFIER_KEY, required = true) @QueryParam(
          NGCommonEntityConstants.GROUP_IDENTIFIER_KEY) String userGroupIdentifier,
      @RequestBody(description = "List of scopes", required = true) List<ScopeDTO> scopes) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, null, null),
        Resource.of(USERGROUP, userGroupIdentifier), MANAGE_USERGROUP_PERMISSION);
    return ResponseDTO.newResponse(userGroupService.copy(accountIdentifier, userGroupIdentifier, scopes));
  }

  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Get a User Group", nickname = "getUserGroup")
  @Operation(operationId = "getUserGroup", summary = "Get User Group",
      description = "Get a User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the successfully fetched User Group")
      })
  public ResponseDTO<UserGroupDTO>
  get(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Identifier of the user group", required = true) @NotEmpty @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String identifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, identifier), VIEW_USERGROUP_PERMISSION);
    Optional<UserGroup> userGroupOptional =
        userGroupService.get(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    return userGroupOptional
        .map(userGroup -> ResponseDTO.newResponse(Long.toString(userGroup.getVersion()), toDTO(userGroup)))
        .orElseGet(() -> ResponseDTO.newResponse(null));
  }

  @DELETE
  @Path("{identifier}")
  @ApiOperation(value = "Delete a User Group", nickname = "deleteUserGroup")
  @Operation(operationId = "deleteUserGroup", description = "Delete User Group",
      summary = "Delete a User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the successfully deleted User Group")
      })
  public ResponseDTO<UserGroupDTO>
  delete(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Identifier of the user group", required = true) @NotEmpty @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String identifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, identifier), MANAGE_USERGROUP_PERMISSION);
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    checkExternallyManaged(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    UserGroup userGroup = userGroupService.delete(scope, identifier);
    return ResponseDTO.newResponse(Long.toString(userGroup.getVersion()), toDTO(userGroup));
  }

  @GET
  @ApiOperation(value = "Get User Group List", nickname = "getUserGroupList")
  @Operation(operationId = "getUserGroupList", description = "List User Groups",
      summary = "List the User Groups in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the paginated list of the User Groups.")
      })
  public ResponseDTO<PageResponse<UserGroupDTO>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Search filter which matches by user group name/identifier") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @QueryParam("filterType") @DefaultValue("EXCLUDE_INHERITED_GROUPS") UserGroupFilterType filterType,
      @BeanParam PageRequest pageRequest) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION);
    if (isEmpty(pageRequest.getSortOrders())) {
      SortOrder order = SortOrder.Builder.aSortOrder().withField("lastModifiedAt", SortOrder.OrderType.DESC).build();
      pageRequest.setSortOrders(ImmutableList.of(order));
    }
    Page<UserGroupDTO> page = userGroupService
                                  .list(accountIdentifier, orgIdentifier, projectIdentifier, searchTerm, filterType,
                                      getPageRequest(pageRequest))
                                  .map(UserGroupMapper::toDTO);
    return ResponseDTO.newResponse(getNGPageResponse(page));
  }

  @GET
  @Path("{identifier}/scopes")
  @ApiOperation(value = "Get Inheriting Child Scope List", nickname = "getInheritingChildScopeList")
  @Operation(operationId = "getInheritingChildScopeList", summary = "Get Inheriting Child Scopes",
      description = "List the Child Scopes inheriting this User Group",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of the child scopes inheriting this User Group.")
      })
  public ResponseDTO<List<ScopeNameDTO>>
  getInheritingChildScopeList(@Parameter(description = "Identifier of the user group",
                                  required = true) @NotNull @PathParam("identifier") String userGroupIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION);
    List<ScopeNameDTO> inheritingScopeNames = userGroupService.getInheritingChildScopeList(
        accountIdentifier, orgIdentifier, projectIdentifier, userGroupIdentifier);
    return ResponseDTO.newResponse(inheritingScopeNames);
  }

  @POST
  @Path("{identifier}/users")
  @ApiOperation(value = "List users in a user group", nickname = "getUsersInUserGroup")
  @Operation(operationId = "getUserListInUserGroup", summary = "List users in User Group",
      description = "List the users in a User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the paginated list of the users in a User Group.")
      })
  public ResponseDTO<PageResponse<UserMetadataDTO>>
  getUsersInUserGroup(@Parameter(description = "Identifier of the user group", required = true) @NotNull @PathParam(
                          "identifier") String userGroupIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @Valid @BeanParam PageRequest pageRequest,
      @RequestBody(description = "Filter users based on multiple parameters") UserFilter userFilter) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, userGroupIdentifier), VIEW_USERGROUP_PERMISSION);
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    return ResponseDTO.newResponse(
        userGroupService.listUsersInUserGroup(scope, userGroupIdentifier, userFilter, pageRequest));
  }

  @POST
  @Path("batch")
  @ApiOperation(value = "Get Batch User Group List", nickname = "getBatchUserGroupList")
  @Operation(operationId = "getBatchUsersGroupList", summary = "List User Groups by filter",
      description = "List the User Groups selected by a filter in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the list of the user groups selected by a filter in a User Group.")
      })
  public ResponseDTO<List<UserGroupDTO>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @RequestBody(
          description = "User Group Filter", required = true) @Body @NotNull UserGroupFilterDTO userGroupFilterDTO) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(userGroupFilterDTO.getAccountIdentifier(), userGroupFilterDTO.getOrgIdentifier(),
            userGroupFilterDTO.getProjectIdentifier()),
        Resource.of(USERGROUP, null), VIEW_USERGROUP_PERMISSION);
    List<UserGroupDTO> userGroups =
        userGroupService.list(userGroupFilterDTO).stream().map(UserGroupMapper::toDTO).collect(Collectors.toList());
    return ResponseDTO.newResponse(userGroups);
  }

  @GET
  @Path("{identifier}/member/{userIdentifier}")
  @ApiOperation(value = "Check if the user is part of the user group", nickname = "checkMember")
  @Operation(operationId = "getMember", summary = "Check user membership",
      description = "Check if the user is part of the user group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Return true/false based on whether the user is part of the user group")
      })
  public ResponseDTO<Boolean>
  checkMember(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
                  NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Identifier of the user group", required = true) @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Parameter(description = "Identifier of the user", required = true) @PathParam(
          "userIdentifier") String userIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, identifier), VIEW_USERGROUP_PERMISSION);
    boolean isMember =
        userGroupService.checkMember(accountIdentifier, orgIdentifier, projectIdentifier, identifier, userIdentifier);
    return ResponseDTO.newResponse(isMember);
  }

  @PUT
  @Path("{identifier}/member/{userIdentifier}")
  @ApiOperation(value = "Add a user to the user group", nickname = "addMember")
  @Operation(operationId = "putMember", summary = "Add user to User Group",
      description = "Add a user to the user group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the updated user group after user addition")
      })
  public ResponseDTO<UserGroupDTO>
  addMember(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
                NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Identifier of the user group", required = true) @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Parameter(description = "Identifier of the user", required = true) @PathParam(
          "userIdentifier") String userIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, identifier), MANAGE_USERGROUP_PERMISSION);
    checkExternallyManaged(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    UserGroup userGroup =
        userGroupService.addMember(accountIdentifier, orgIdentifier, projectIdentifier, identifier, userIdentifier);
    return ResponseDTO.newResponse(Long.toString(userGroup.getVersion()), toDTO(userGroup));
  }

  @DELETE
  @Path("{identifier}/member/{userIdentifier}")
  @ApiOperation(value = "Remove a user from the user group", nickname = "removeMember")
  @Operation(operationId = "deleteMember", summary = "Remove user from User Group",
      description = "Remove a user from the user group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the updated user group after user removal")
      })
  public ResponseDTO<UserGroupDTO>
  removeMember(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
                   NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Identifier of the user group", required = true) @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Parameter(description = "Identifier of the user", required = true) @PathParam(
          "userIdentifier") String userIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, identifier), MANAGE_USERGROUP_PERMISSION);
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    checkExternallyManaged(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    UserGroup userGroup = userGroupService.removeMember(scope, identifier, userIdentifier);
    return ResponseDTO.newResponse(Long.toString(userGroup.getVersion()), toDTO(userGroup));
  }

  private static void validateScopes(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, UserGroupDTO userGroupDTO) {
    verifyValuesNotChanged(Lists.newArrayList(Pair.of(accountIdentifier, userGroupDTO.getAccountIdentifier()),
                               Pair.of(orgIdentifier, userGroupDTO.getOrgIdentifier()),
                               Pair.of(projectIdentifier, userGroupDTO.getProjectIdentifier())),
        true);
  }

  @PUT
  @Path("{userGroupId}/unlink")
  @ApiOperation(value = "API to unlink the harness user group from SSO group", nickname = "unlinkSsoGroup")
  @Operation(operationId = "unlinkUserGroupfromSSO",
      summary = "Unlink SSO Group from the User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the updated User Group after unlinking SSO Group")
      })
  public RestResponse<UserGroup>
  unlinkSsoGroup(@Parameter(description = "Identifier of the user group", required = true) @PathParam(
                     "userGroupId") String userGroupId,
      @Parameter(description = "Retain currently synced members of the user group") @QueryParam(
          "retainMembers") boolean retainMembers,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, userGroupId), MANAGE_USERGROUP_PERMISSION);
    checkExternallyManaged(accountIdentifier, orgIdentifier, projectIdentifier, userGroupId);
    return new RestResponse<>(userGroupService.unlinkSsoGroup(
        accountIdentifier, orgIdentifier, projectIdentifier, userGroupId, retainMembers));
  }

  @PUT
  @Path("{userGroupId}/link/saml/{samlId}")
  @ApiOperation(value = "Link to SAML group", nickname = "linkToSamlGroup")
  @Operation(operationId = "linkUserGroupToSAML",
      summary = "Link SAML Group to the User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the updated User Group after linking SAML Group")
      })
  public RestResponse<UserGroup>
  linkToSamlGroup(@Parameter(description = "Identifier of the user group", required = true) @PathParam(
                      "userGroupId") String userGroupId,
      @Parameter(description = "Saml Group entity identifier", required = true) @PathParam("samlId") String samlId,
      @RequestBody(
          description = "Saml Link Group Request", required = true) @NotNull @Valid SamlLinkGroupRequest groupRequest,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, userGroupId), MANAGE_USERGROUP_PERMISSION);
    checkExternallyManaged(accountIdentifier, orgIdentifier, projectIdentifier, userGroupId);
    return new RestResponse<>(userGroupService.linkToSsoGroup(accountIdentifier, orgIdentifier, projectIdentifier,
        userGroupId, SSOType.SAML, samlId, groupRequest.getSamlGroupName(), groupRequest.getSamlGroupName()));
  }

  private void checkExternallyManaged(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    if (userGroupService.isExternallyManaged(accountIdentifier, orgIdentifier, projectIdentifier, identifier)) {
      throw new InvalidRequestException("This API call is not supported for externally managed group" + identifier);
    }
  }
}
