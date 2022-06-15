/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_MANAGEMENT;
import static software.wings.security.PermissionAttribute.PermissionType.USER_PERMISSION_READ;

import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.Operator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.rest.RestResponse;

import software.wings.beans.User;
import software.wings.beans.notification.NotificationSettings;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.beans.sso.LdapLinkGroupRequest;
import software.wings.beans.sso.SSOType;
import software.wings.beans.sso.SamlLinkGroupRequest;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.UserService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
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
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Users Resource class.
 *
 * @author Rishi
 */
@Api("userGroups")
@Path("/userGroups")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Scope(ResourceType.USER)
@AuthRule(permissionType = USER_PERMISSION_MANAGEMENT)
@Slf4j
public class UserGroupResource {
  private UserGroupService userGroupService;
  private UserService userService;

  /**
   * Instantiates a new User resource.
   *
   * @param userGroupService    the userGroupService
   */
  @Inject
  public UserGroupResource(UserGroupService userGroupService, UserService userService) {
    this.userGroupService = userGroupService;
    this.userService = userService;
  }

  /**
   * List.
   *
   * @param pageRequest the page request
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = USER_PERMISSION_READ)
  public RestResponse<PageResponse<UserGroup>> list(@BeanParam PageRequest<UserGroup> pageRequest,
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("searchTerm") String searchTerm,
      @QueryParam("details") @DefaultValue("true") boolean loadUsers) {
    if (!StringUtils.isEmpty(searchTerm)) {
      SearchFilter searchFilter = SearchFilter.builder()
                                      .fieldName(UserGroupKeys.name)
                                      .op(Operator.STARTS_WITH)
                                      .fieldValues(new String[] {searchTerm})
                                      .build();
      pageRequest.setFilters(Lists.newArrayList(searchFilter));
    }
    PageResponse<UserGroup> pageResponse = userGroupService.list(accountId, pageRequest, loadUsers);
    return getPublicUserGroups(pageResponse);
  }

  /**
   * Gets the.
   *
   * @param accountId   the account id
   * @param userGroupId  the userGroupId
   * @return the rest response
   */
  @GET
  @Path("{userGroupId}")
  @Timed
  @AuthRule(permissionType = USER_PERMISSION_READ)
  @ExceptionMetered
  public RestResponse<UserGroup> get(
      @QueryParam("accountId") String accountId, @PathParam("userGroupId") String userGroupId) {
    return new RestResponse<>(getPublicUserGroup(userGroupService.get(accountId, userGroupId)));
  }

  /**
   * Gets the count of user groups associated with an account ID
   *
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("count")
  @Timed
  @AuthRule(permissionType = USER_PERMISSION_READ)
  @ExceptionMetered
  public RestResponse<Long> getCountOfUserGroups(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(userGroupService.getCountOfUserGroups(accountId));
  }

  /**
   * Clone the user group with the given id and a new name
   * @param accountId The account it
   * @param userGroupId The user group id to clone
   * @param newName The name to be set of the new cloned user group
   * @return The rest response.
   */
  @POST
  @Path("{userGroupId}/clone")
  @Timed
  @ExceptionMetered
  public RestResponse<UserGroup> clone(@QueryParam("accountId") String accountId,
      @PathParam("userGroupId") String userGroupId, @QueryParam("newName") String newName,
      @QueryParam("newDescription") String newDescription) {
    return new RestResponse<>(
        getPublicUserGroup(userGroupService.cloneUserGroup(accountId, userGroupId, newName, newDescription)));
  }

  /**
   * Save.
   *
   * @param accountId   the account id
   * @param userGroup the userGroup
   * @return the rest response
   */
  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<UserGroup> save(@QueryParam("accountId") String accountId, UserGroup userGroup) {
    userGroup.setAccountId(accountId);
    return new RestResponse<>(getPublicUserGroup(userGroupService.save(userGroup)));
  }

  private UserGroup getPublicUserGroup(UserGroup userGroup) {
    if (userGroup == null) {
      return new UserGroup();
    }
    setUserSummary(userGroup);

    return userGroup;
  }

  private RestResponse<PageResponse<UserGroup>> getPublicUserGroups(PageResponse<UserGroup> pageResponse) {
    if (pageResponse == null) {
      return new RestResponse<>();
    }

    List<UserGroup> userGroups = pageResponse.getResponse();
    if (isEmpty(userGroups)) {
      return new RestResponse<>(pageResponse);
    }
    userGroups.forEach(this::setUserSummary);
    return new RestResponse<>(pageResponse);
  }

  private void setUserSummary(UserGroup userGroup) {
    if (userGroup == null) {
      return;
    }
    List<User> userSummaryList = userService.getUserSummary(userGroup.getMembers());
    userGroup.setMembers(userSummaryList);
  }
  /**
   * Update Overview.
   *
   * @param accountId   the account id
   * @param userGroupId  the userGroupId
   * @param userGroup the userGroup
   * @return the rest response
   */
  @PUT
  @Path("{userGroupId}/overview")
  @Timed
  @ExceptionMetered
  public RestResponse<UserGroup> updateOverview(
      @QueryParam("accountId") String accountId, @PathParam("userGroupId") String userGroupId, UserGroup userGroup) {
    userGroup.setUuid(userGroupId);
    userGroup.setAccountId(accountId);
    return new RestResponse<>(getPublicUserGroup(userGroupService.updateOverview(userGroup)));
  }

  /**
   * Update Members.
   *
   * @param accountId   the account id
   * @param userGroupId  the userGroupId
   * @param userGroup the userGroup
   * @return the rest response
   */
  @PUT
  @Path("{userGroupId}/members")
  @Timed
  @ExceptionMetered
  public RestResponse<UserGroup> updateMembers(
      @QueryParam("accountId") String accountId, @PathParam("userGroupId") String userGroupId, UserGroup userGroup) {
    UserGroup existingGroup = userGroupService.get(accountId, userGroupId);
    if (null == existingGroup) {
      throw new InvalidRequestException("Invalid UserGroup ID.");
    }
    if (existingGroup.isSsoLinked()) {
      throw new InvalidRequestException("Updating members of SSO linked user groups is not allowed.");
    }

    userGroup.setUuid(userGroupId);
    userGroup.setAccountId(accountId);
    userGroup.setName(existingGroup.getName());

    boolean sendMailToNewMembers = true;
    if (null != existingGroup.getNotificationSettings()) {
      sendMailToNewMembers = existingGroup.getNotificationSettings().isSendMailToNewMembers();
    }
    userGroup.setMemberIds(userGroup.getMembers().stream().map(User::getUuid).collect(Collectors.toList()));
    return new RestResponse<>(
        getPublicUserGroup(userGroupService.updateMembers(userGroup, sendMailToNewMembers, true)));
  }

  /**
   * Update Notification Settings.
   *
   * @param accountId   the account id
   * @param userGroupId  the userGroupId
   * @param settings notification settings to add to user group
   * @return the rest response
   */
  @PUT
  @Path("{userGroupId}/notification-settings")
  @Timed
  @ExceptionMetered
  public RestResponse<UserGroup> updateNotificationSettings(@QueryParam("accountId") String accountId,
      @PathParam("userGroupId") String userGroupId, NotificationSettings settings) {
    return new RestResponse<>(
        getPublicUserGroup(userGroupService.updateNotificationSettings(accountId, userGroupId, settings)));
  }

  /**
   * Update Permission.
   *
   * @param accountId   the account id
   * @param userGroupId  the userGroupId
   * @param userGroup the userGroup
   * @return the rest response
   */
  @PUT
  @Path("{userGroupId}/permissions")
  @Timed
  @ExceptionMetered
  public RestResponse<UserGroup> updatePermissions(
      @QueryParam("accountId") String accountId, @PathParam("userGroupId") String userGroupId, UserGroup userGroup) {
    userGroup.setUuid(userGroupId);
    userGroup.setAccountId(accountId);
    return new RestResponse<>(getPublicUserGroup(userGroupService.updatePermissions(userGroup)));
  }

  /**
   * Delete.
   *
   * @param accountId   the account id
   * @param userGroupId  the userGroupId
   * @return the rest response
   */
  @DELETE
  @Path("{userGroupId}")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> delete(
      @QueryParam("accountId") String accountId, @PathParam("userGroupId") String userGroupId) {
    UserGroup userGroup = userGroupService.get(accountId, userGroupId);
    if (userGroup == null) {
      throw new InvalidRequestException("UserGroup Doesn't Exists", WingsException.GROUP);
    } else if (userGroup.isImportedByScim()) {
      throw new InvalidRequestException("Cannot Delete Group Imported From SCIM", WingsException.GROUP);
    }
    return new RestResponse<>(userGroupService.delete(accountId, userGroupId, false));
  }

  @DELETE
  @Path("non-admin")
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> deleteNonAdminUserGroups(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(userGroupService.deleteNonAdminUserGroups(accountId));
  }

  @DELETE
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> delete(
      @QueryParam("accountId") @NotEmpty String accountId, @NotEmpty List<String> userGroupsToRetain) {
    return new RestResponse<>(userGroupService.deleteUserGroupsByName(accountId, userGroupsToRetain));
  }

  /**
   * List for approvals
   *
   * @param accountId   the account id
   * @return the rest response
   */
  @GET
  @Path("approvals")
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<PageResponse<UserGroup>> listForApprovals(@QueryParam("accountId") @NotEmpty String accountId) {
    PageRequest<UserGroup> pageRequest = PageRequestBuilder.aPageRequest()
                                             .withLimit(Long.toString(userGroupService.getCountOfUserGroups(accountId)))
                                             .addFilter("accountId", Operator.EQ, accountId)
                                             .addFieldsIncluded("_id", "name", "notificationSettings")
                                             .build();
    PageResponse<UserGroup> pageResponse = userGroupService.list(accountId, pageRequest, false);
    return getPublicUserGroups(pageResponse);
  }

  /**
   * Link to LDAP group
   *
   * @param userGroupId user group id
   * @param ldapId ldap provider id
   * @param accountId the account id
   * @param groupRequest group details
   * @return the rest response
   */
  @PUT
  @Path("{userGroupId}/link/ldap/{ldapId}")
  @Timed
  @ExceptionMetered
  public RestResponse<UserGroup> linkToLdapGroup(@PathParam("userGroupId") String userGroupId,
      @PathParam("ldapId") String ldapId, @QueryParam("accountId") @NotEmpty String accountId,
      @NotNull @Valid LdapLinkGroupRequest groupRequest) {
    return new RestResponse<>(getPublicUserGroup(userGroupService.linkToSsoGroup(
        accountId, userGroupId, SSOType.LDAP, ldapId, groupRequest.getLdapGroupDN(), groupRequest.getLdapGroupName())));
  }

  /**
   * API to unlink the harness user group from SSO group
   *
   * @param userGroupId
   * @param accountId
   * @param retainMembers
   * @return
   */
  @PUT
  @Path("{userGroupId}/unlink")
  @ApiOperation(value = "unlink Harness User Group from SSO Group", nickname = "unlinkUserGroupFromSSO")
  @Operation(operationId = "unlinkUserGroupFromSSO", summary = "Unlinks user group from an SSO group",
      description = "Unlinks user group from an SSO group.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Successfully unlinked Harness User Group from SSO group that was linked")
      })
  @Timed
  @ExceptionMetered
  public RestResponse<UserGroup>
  unlinkSsoGroup(@PathParam("userGroupId") String userGroupId, @QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("retainMembers") boolean retainMembers) {
    return new RestResponse<>(
        getPublicUserGroup(userGroupService.unlinkSsoGroup(accountId, userGroupId, retainMembers)));
  }

  /**
   * Link to SAML group
   *
   * @param userGroupId user group id
   * @param samlId    saml provider id
   * @param accountId the account id
   * @param groupRequest group details
   * @return the rest response
   */
  @PUT
  @Path("{userGroupId}/link/saml/{samlId}")
  @ApiOperation(value = "Link Harness User Group with SAML group", nickname = "linkUserGroupWithSAMLGroup")
  @Operation(operationId = "linkUserGroupWithSAMLGroup", summary = "Link a User Group to a SAML Group",
      description = "Links the given user group to a SAML group.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Successfully linked Harness user group with the SAML group of SSO Provider")
      })
  @Timed
  @ExceptionMetered
  public RestResponse<UserGroup>
  linkToSamlGroup(@PathParam("userGroupId") String userGroupId, @PathParam("samlId") String samlId,
      @QueryParam("accountId") @NotEmpty String accountId, @NotNull @Valid SamlLinkGroupRequest groupRequest) {
    return new RestResponse<>(userGroupService.linkToSsoGroup(accountId, userGroupId, SSOType.SAML, samlId,
        groupRequest.getSamlGroupName(), groupRequest.getSamlGroupName()));
  }
}
