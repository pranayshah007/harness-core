/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.user.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USER_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_USER_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USER;
import static io.harness.ng.core.invites.mapper.RoleBindingMapper.validateRoleBindings;
import static io.harness.utils.PageUtils.getPageRequest;

import static java.lang.Boolean.TRUE;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NoResultFoundException;
import io.harness.ng.accesscontrol.user.ACLAggregateFilter;
import io.harness.ng.accesscontrol.user.AggregateUserService;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ActiveProjectsCountDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UsersCountDTO;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.AddUsersDTO;
import io.harness.ng.core.user.AddUsersResponse;
import io.harness.ng.core.user.NGRemoveUserFilter;
import io.harness.ng.core.user.PasswordChangeDTO;
import io.harness.ng.core.user.PasswordChangeResponse;
import io.harness.ng.core.user.TwoFactorAuthMechanismInfo;
import io.harness.ng.core.user.TwoFactorAuthSettingsInfo;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserInfoUpdateDTO;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.ng.core.user.entities.UserMetadata;
import io.harness.ng.core.user.remote.dto.UserAggregateDTO;
import io.harness.ng.core.user.remote.dto.UserFilter;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.remote.mapper.UserMetadataMapper;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ng.userprofile.services.api.UserInfoService;
import io.harness.rest.RestResponse;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.security.dto.PrincipalType;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

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
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
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
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import retrofit2.http.Body;

@Api("user")
@Path("user")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor(access = AccessLevel.PUBLIC, onConstructor = @__({ @Inject }))
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "User", description = "This contains APIs related to User as defined in Harness")
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
@Slf4j
@OwnedBy(PL)
public class UserResource {
  AggregateUserService aggregateUserService;
  NgUserService ngUserService;
  ProjectService projectService;
  UserInfoService userInfoService;
  AccessControlClient accessControlClient;
  NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @GET
  @Path("currentUser")
  @ApiOperation(value = "get current user information", nickname = "getCurrentUserInfo")
  @Operation(operationId = "getCurrentUserInfo", summary = "Get Current User Info",
      description = "Gets current logged in User information",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns current logged in user info")
      })
  public ResponseDTO<UserInfo>
  getUserInfo(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    return ResponseDTO.newResponse(userInfoService.getCurrentUser());
  }

  @GET
  @Path("two-factor-auth/{authMechanism}")
  @ApiOperation(value = "get two factor auth settings", nickname = "getTwoFactorAuthSettings")
  @Operation(operationId = "getTwoFactorAuthSettings", summary = "Gets Two Factor Auth Settings",
      description = "Gets two factor authentication settings information of the current logged in user",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns current logged in user's two factor authentication info")
      })
  public ResponseDTO<TwoFactorAuthSettingsInfo>
  getTwoFactorAuthSettingsInfo(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
                                   NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(
          description =
              "This is the authentication mechanism for the logged-in User. Two-Factor Authentication settings will be fetched for this mechanism.")
      @PathParam("authMechanism") TwoFactorAuthMechanismInfo authMechanism) {
    return ResponseDTO.newResponse(userInfoService.getTwoFactorAuthSettingsInfo(authMechanism));
  }

  @GET
  @Hidden
  @Path("usermembership")
  @ApiOperation(value = "Check if user part of scope", nickname = "checkUserMembership", hidden = true)
  @InternalApi
  public ResponseDTO<Boolean> checkUserMembership(
      @Parameter(
          description =
              "This is the User Identifier. The membership details of the user corresponding to this identifier will be checked.",
          required = true) @QueryParam(NGCommonEntityConstants.USER_ID) String userId,
      @Parameter(
          description =
              "This is the Account Identifier. The membership details within the scope of this Account will be checked.",
          required = true) @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(
          description =
              "This is the Organization Identifier. The membership details within the scope of this Organization will be checked.")
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(
          description =
              "This is the Project Identifier. The membership details within the scope of this Project will be checked.")
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USER, null), VIEW_USER_PERMISSION);
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    return ResponseDTO.newResponse(ngUserService.isUserAtScope(userId, scope));
  }

  @GET
  @Hidden
  @Path("admin")
  @ApiOperation(value = "Check if user is account admin", nickname = "checkIfAccountAdmin", hidden = true)
  @InternalApi
  public ResponseDTO<Boolean> isUserAdmin(
      @Parameter(
          description =
              "This is the User Identifier. The membership details of the user corresponding to this identifier will be checked.",
          required = true) @NotNull @QueryParam(NGCommonEntityConstants.USER_ID) String userId,
      @Parameter(
          description =
              "This is the Account Identifier. The membership details within the scope of this Account will be checked.",
          required = true) @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    return ResponseDTO.newResponse(ngUserService.isAccountAdmin(userId, accountIdentifier));
  }

  @GET
  @Path("currentgen")
  @ApiOperation(value = "Get users from current gen for an account", nickname = "getCurrentGenUsers")
  @Operation(operationId = "getCurrentGenUsers", summary = "List Current Gen Users from Account",
      description = "List of current gen users with the given Account Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "This retrieves a list of Current Generation Users corresponding to the specified Account Identifier.")
      },
      hidden = true)
  public ResponseDTO<PageResponse<UserMetadataDTO>>
  getCurrentGenUsers(
      @Parameter(description = "This is the Account Identifier. Users corresponding to this Account will be retrieved.")
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(
          description =
              "This string will be used to filter the search results. Details of all the users having this string in their name or email address will be filtered.")
      @QueryParam("searchString") @DefaultValue("") String searchString,
      @BeanParam PageRequest pageRequest) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(USER, null), VIEW_USER_PERMISSION);
    Pageable pageable = getPageRequest(pageRequest);
    Page<UserInfo> users = ngUserService.listCurrentGenUsers(accountIdentifier, searchString, pageable);
    return ResponseDTO.newResponse(PageUtils.getNGPageResponse(users.map(UserMetadataMapper::toDTO)));
  }

  @GET
  @Path("projects")
  @ApiOperation(value = "get user project information", nickname = "getUserProjectInfo")
  @Operation(operationId = "getUserProjectInfo", summary = "Get User's Projects",
      description =
          "Retrieves the list of projects of the current user corresponding to the specified Account Identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "List of projects of the current user corresponding to the specified Account Identifier")
      },
      hidden = true)
  public ResponseDTO<PageResponse<ProjectDTO>>
  getUserProjectInfo(
      @Parameter(
          description =
              "This is the Account Identifier. Details of all the Projects within the scope of this Account will be fetched.")
      @QueryParam("accountId") String accountId,
      @BeanParam PageRequest pageRequest) {
    Optional<String> userId = getUserIdentifierFromSecurityContext();
    if (!userId.isPresent()) {
      return ResponseDTO.newResponse(PageResponse.getEmptyPageResponse(pageRequest));
    }
    return ResponseDTO.newResponse(projectService.listProjectsForUser(userId.get(), accountId, pageRequest));
  }

  @GET
  @Path("all-projects")
  @ApiOperation(value = "get user all projects information", nickname = "getUserAllProjectsInfo")
  @Operation(operationId = "getUserAllProjectsInfo",
      summary = "list of project(s) of current user in the passed account Id in form of List",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the list of project(s) of current user in the passed account Id in form of List")
      },
      hidden = true)
  public ResponseDTO<List<ProjectDTO>>
  getUserAllProjectsInfo(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam("accountId") String accountId,
      @Parameter(description = "User Identifier") @QueryParam("userId") String userId) {
    return ResponseDTO.newResponse(projectService.listProjectsForUser(userId, accountId));
  }

  @GET
  @Path("projects-count")
  @ApiOperation(value = "Get count of projects accessible to a user", nickname = "getAccessibleProjectsCount")
  @Operation(operationId = "getAccessibleProjectsCount",
      summary = "Count of projects that are accessible to a user filtered by CreatedAt time",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the count of projects that are accessible to a user filtered by CreatedAt time")
      },
      hidden = true)
  public ResponseDTO<ActiveProjectsCountDTO>
  getAccessibleProjectsCount(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                 NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = "user Identifier") @QueryParam(NGCommonEntityConstants.USER_ID) String userId,
      @Parameter(description = "Start time to Filter projects by CreatedAt time") @QueryParam(
          NGResourceFilterConstants.START_TIME) long startInterval,
      @Parameter(description = "End time to Filter projects by CreatedAt time") @QueryParam(
          NGResourceFilterConstants.END_TIME) long endInterval) {
    return ResponseDTO.newResponse(
        projectService.accessibleProjectsCount(userId, accountIdentifier, startInterval, endInterval));
  }

  @GET
  @Path("last-admin")
  @ApiOperation(value = "check if user is last admin at the scope", nickname = "checkIfLastAdmin")
  @Operation(operationId = "checkIfLastAdmin", summary = "Check if user is last admin",
      description = "Check whether the user is last admin at scope or not",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns Boolean status whether the user is last admin at scope or not")
      })
  public ResponseDTO<Boolean>
  checkIfLastAdmin(
      @Parameter(description = "User identifier") @QueryParam(NGCommonEntityConstants.USER_ID) String userId,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USER, null), VIEW_USER_PERMISSION);
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    return ResponseDTO.newResponse(ngUserService.isUserLastAdminAtScope(userId, scope));
  }

  @POST
  @Path("batch")
  @ApiOperation(value = "Get a list of users", nickname = "getUsers")
  @Operation(operationId = "getUsers", summary = "Get users list", description = "Get list of user's for a given scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the list of user's Metadata for a given scope")
      })
  public ResponseDTO<PageResponse<UserMetadataDTO>>
  getUsers(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @NotEmpty @QueryParam(
               NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @Valid @BeanParam PageRequest pageRequest, UserFilter userFilter) {
    if (userFilter == null || UserFilter.ParentFilter.NO_PARENT_SCOPES.equals(userFilter.getParentFilter())) {
      accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
          Resource.of(USER, null), VIEW_USER_PERMISSION);
    } else {
      accessControlClient.checkForAccessOrThrow(
          ResourceScope.of(accountIdentifier, null, null), Resource.of(USER, null), VIEW_USER_PERMISSION);
      if (isNotEmpty(orgIdentifier)) {
        accessControlClient.checkForAccessOrThrow(
            ResourceScope.of(accountIdentifier, orgIdentifier, null), Resource.of(USER, null), VIEW_USER_PERMISSION);
        if (isNotEmpty(projectIdentifier)) {
          accessControlClient.checkForAccessOrThrow(
              ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier), Resource.of(USER, null),
              VIEW_USER_PERMISSION);
        }
      }
    }
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    return ResponseDTO.newResponse(ngUserService.listUsers(scope, pageRequest, userFilter));
  }

  @GET
  @Path("/aggregate/{userId}")
  @ApiOperation(value = "Get a user by userId for access control", nickname = "getAggregatedUser")
  @Operation(operationId = "getAggregatedUser", summary = "Get detailed user information",
      description = "Returns the user metadata along with rolesAssignments by userId and scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns the user metadata along with rolesAssignments by userId and scope")
      })
  public ResponseDTO<UserAggregateDTO>
  getAggregatedUser(@Parameter(description = "user Identifier") @PathParam("userId") String userId,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USER, userId), VIEW_USER_PERMISSION);
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    Optional<UserAggregateDTO> aclUserAggregateDTO = aggregateUserService.getAggregatedUser(scope, userId);
    String errorMessage = String.format("User with id %s not found in the given scope", userId);
    return ResponseDTO.newResponse(aclUserAggregateDTO.orElseThrow(notFoundExceptionSupplier(errorMessage)));
  }

  @NotNull
  private Supplier<NoResultFoundException> notFoundExceptionSupplier(String message) {
    return () -> NoResultFoundException.newBuilder().code(ErrorCode.ENTITY_NOT_FOUND).message(message).build();
  }

  @POST
  @Path("aggregate")
  @ApiOperation(value = "Get a page of active users for access control", nickname = "getAggregatedUsers")
  @Operation(operationId = "getAggregatedUsers", summary = "Get list of users",
      description = "List of all the user's metadata along with rolesAssignments who have access to given scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description =
                "Returns list of all the user's metadata along with rolesAssignments who have access to given scope")
      })
  public ResponseDTO<PageResponse<UserAggregateDTO>>
  getAggregatedUsers(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @Parameter(description = "Search term") @QueryParam("searchTerm") String searchTerm,
      @BeanParam PageRequest pageRequest, ACLAggregateFilter aclAggregateFilter) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USER, null), VIEW_USER_PERMISSION);

    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    if (ACLAggregateFilter.isFilterApplied(aclAggregateFilter) && isNotBlank(searchTerm)) {
      throw new InvalidRequestException("Search and Filter are not supported together");
    }
    if (ACLAggregateFilter.isFilterApplied(aclAggregateFilter)) {
      return ResponseDTO.newResponse(aggregateUserService.getAggregatedUsers(scope, aclAggregateFilter, pageRequest));
    }
    return ResponseDTO.newResponse(aggregateUserService.getAggregatedUsers(scope, searchTerm, pageRequest));
  }

  @POST
  @Path("users")
  @ApiOperation(value = "Add users to a scope", nickname = "addUsers")
  @Operation(operationId = "addUsers", summary = "Add user(s) to scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns list of added users to a given scope")
      })
  public ResponseDTO<AddUsersResponse>
  addUsers(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
               NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @NotNull @Valid AddUsersDTO addUsersDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USER, null), MANAGE_USER_PERMISSION);
    validateRoleBindings(addUsersDTO.getRoleBindings(), orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(
        ngUserService.addUsers(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), addUsersDTO));
  }

  @PUT
  @ApiOperation(value = "update user information", nickname = "updateUserInfo")
  @Operation(operationId = "updateUserInfo", summary = "Update User", description = "Updates the User information",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the update User information")
      })
  public ResponseDTO<UserInfo>
  updateUserInfo(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true)
                 @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier, @Body UserInfo userInfo) {
    if (isUserExternallyManaged(userInfo.getUuid(), accountIdentifier)) {
      log.warn("User is externally managed, cannot update user - userId: {}", userInfo.getUuid());
      throw new InvalidRequestException(
          "User is externally managed by your Identity Provider and cannot be updated via UI/API. To update user information in Harness, update it from your Identity Provider");
    } else {
      return ResponseDTO.newResponse(userInfoService.update(userInfo, accountIdentifier));
    }
  }

  @PUT
  @Path("{userId}")
  @ApiOperation(value = "update user information", nickname = "updateUserName")
  @Operation(operationId = "updateUserInfo", summary = "Update User", description = "Updates the User information",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the update User information")
      })
  public ResponseDTO<UserInfo>
  updateUserInfoV2(@Parameter(description = "User Identifier") @PathParam("userId") String userId,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY)
      String accountIdentifier, @Body UserInfoUpdateDTO userInfo) {
    if (isUserExternallyManaged(userId, accountIdentifier)) {
      log.warn("User is externally managed, cannot update user - userId: {}", userId);
      throw new InvalidRequestException(
          "User is externally managed by your Identity Provider and cannot be updated via UI/API. To update user information in Harness, update it from your Identity Provider");
    } else {
      return ResponseDTO.newResponse(userInfoService.update(userInfo, userId, accountIdentifier));
    }
  }

  @PUT
  @Path("password")
  @ApiOperation(value = "Change user password", nickname = "changeUserPassword")
  @Operation(operationId = "changeUserPassword", summary = "Change user password",
      description = "Updates the User password",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Returns whether the operation is successful or not with readable response.")
      })
  public ResponseDTO<PasswordChangeResponse>
  changeUserPassword(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      PasswordChangeDTO passwordChangeDTO) {
    return ResponseDTO.newResponse(userInfoService.changeUserPassword(passwordChangeDTO));
  }

  @PUT
  @Path("enable-two-factor-auth")
  @ApiOperation(value = "enable two factor auth settings", nickname = "enableTwoFactorAuth")
  @Operation(operationId = "enableTwoFactorAuth", summary = "Enable two factor authentication",
      description = "Enables two-factor-auth for an user in an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns user information")
      })
  @FeatureRestrictionCheck(FeatureRestrictionName.TWO_FACTOR_AUTH_SUPPORT)
  public ResponseDTO<UserInfo>
  updateTwoFactorAuthInfo(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
                              NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Body TwoFactorAuthSettingsInfo authSettingsInfo) {
    return ResponseDTO.newResponse(userInfoService.updateTwoFactorAuthInfo(authSettingsInfo));
  }

  @PUT
  @Path("disable-two-factor-auth")
  @ApiOperation(value = "disable two factor auth settings", nickname = "disableTwoFactorAuth")
  @Operation(operationId = "disableTTwoFactorAuth", summary = "Disable two factor authentication",
      description = "Disables two-factor-auth for an user in an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns user information")
      })
  @FeatureRestrictionCheck(FeatureRestrictionName.TWO_FACTOR_AUTH_SUPPORT)
  public ResponseDTO<UserInfo>
  disableTFA(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier) {
    return ResponseDTO.newResponse(userInfoService.disableTFA());
  }

  @GET
  @Path("reset-two-factor-auth/{userId}")
  @ApiOperation(value = "resend email for two factor authorization", nickname = "resetTwoFactorAuth")
  @Operation(operationId = "reset2fa", summary = "Reset two factor authorization",
      description = "Reset Two-Factor authorization.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Successfully reset two factor authorization for an account")
      })
  @FeatureRestrictionCheck(FeatureRestrictionName.TWO_FACTOR_AUTH_SUPPORT)
  public RestResponse<Boolean>
  reset2fa(@PathParam("userId") @NotEmpty String userId,
      @AccountIdentifier @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) @NotEmpty String accountIdentifier) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(USER, userId), MANAGE_USER_PERMISSION);
    return new RestResponse<>(userInfoService.sendTwoFactorAuthenticationResetEmail(userId, accountIdentifier));
  }

  @DELETE
  @Path("{userId}")
  @Produces("application/json")
  @Consumes()
  @ApiOperation(value = "Remove user as the collaborator from the scope", nickname = "removeUser")
  @Operation(operationId = "removeUser", summary = "Remove user from scope",
      description = "Remove user as the collaborator from the scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Returns Boolean status whether request was successful or not")
      })
  public ResponseDTO<Boolean>
  removeUser(@Parameter(description = "user Identifier") @NotNull @PathParam("userId") String userId,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    if ((ScopeLevel.ACCOUNT.equals(ScopeLevel.of(accountIdentifier, orgIdentifier, projectIdentifier)))
        && isUserExternallyManaged(userId, accountIdentifier)) {
      // throw error when an externally managed user is being removed from account
      log.error("User is externally managed, cannot delete user - userId: {}", userId);
      throw new InvalidRequestException(
          "User is externally managed by your Identity Provider and cannot be deleted via UI / API. To delete the user from Harness, delete it from your Identity Provider.");
    } else {
      ResponseDTO<Boolean> userRemovalResponse = removeUserInternal(
          userId, accountIdentifier, orgIdentifier, projectIdentifier, NGRemoveUserFilter.ACCOUNT_LAST_ADMIN_CHECK);
      if (ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_USER_DELETION_V2)
          && !ngUserService.isUserAtScope(userId, Scope.builder().accountIdentifier(accountIdentifier).build())) {
        ngUserService.removeUser(userId, accountIdentifier);
      }

      return userRemovalResponse;
    }
  }

  @DELETE
  @Hidden
  @Path("internal/{userId}")
  @Produces("application/json")
  @Consumes()
  @InternalApi
  @ApiOperation(value = "Remove user from the scope", nickname = "removeUserInternal", hidden = true)
  public ResponseDTO<Boolean> removeUserInternal(@NotNull @PathParam("userId") String userId,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam("removeUserFilter") @DefaultValue("ACCOUNT_LAST_ADMIN_CHECK") NGRemoveUserFilter removeUserFilter) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USER, userId), MANAGE_USER_PERMISSION);
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    return ResponseDTO.newResponse(TRUE.equals(
        ngUserService.removeUserFromScope(userId, scope, UserMembershipUpdateSource.USER, removeUserFilter)));
  }

  @GET
  @Hidden
  @Path("internal/{userId}")
  @InternalApi
  @ApiOperation(value = "Get user", nickname = "getUserInternal", hidden = true)
  public ResponseDTO<UserMetadataDTO> getUser(@NotNull @PathParam("userId") String userId,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    return ResponseDTO.newResponse(ngUserService.getUserMetadata(userId).orElse(null));
  }

  Optional<String> getUserIdentifierFromSecurityContext() {
    Optional<String> userId = Optional.empty();
    if (SourcePrincipalContextBuilder.getSourcePrincipal() != null
        && SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.USER) {
      userId = Optional.of(SourcePrincipalContextBuilder.getSourcePrincipal().getName());
    }
    return userId;
  }

  @PUT
  @Path("unlock-user/{userId}")
  @Produces("application/json")
  @Consumes()
  @ApiOperation(value = "unlock user", nickname = "unlockUser")
  @Operation(operationId = "unlockUser", summary = "Unlock user", description = "unlock user in a given scope",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns user information")
      })
  public ResponseDTO<UserInfo>
  unlockUser(@Parameter(description = "user Identifier") @NotNull @PathParam("userId") String userId,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USER, userId), MANAGE_USER_PERMISSION);
    return ResponseDTO.newResponse(userInfoService.unlockUser(userId, accountIdentifier));
  }

  private boolean isUserExternallyManaged(String userId, String accountIdentifier) {
    Optional<UserInfo> optionalUserInfo = ngUserService.getUserByIdAndAccount(userId, accountIdentifier);
    return optionalUserInfo.map(UserInfo::isExternallyManaged).orElse(false);
  }

  @GET
  @Hidden
  @Path("is-email-in-account")
  @ApiOperation(value = "Check if email in account", nickname = "checkEmailAccount", hidden = true)
  @InternalApi
  public ResponseDTO<Boolean> checkIfEmailInAccount(
      @Parameter(description = "This is the Email Identifier.", required = true) @QueryParam(
          NGCommonEntityConstants.EMAIL_KEY) String emailIdentifier,
      @Parameter(
          description =
              "This is the Account Identifier. The membership details within the scope of this Account will be checked.",
          required = true) @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    Optional<UserMetadataDTO> optionalUser = ngUserService.getUserByEmail(emailIdentifier, false);
    boolean found = optionalUser.isPresent()
        && ngUserService.isUserAtScope(
            optionalUser.get().getUuid(), Scope.builder().accountIdentifier(accountIdentifier).build());
    return ResponseDTO.newResponse(found);
  }

  @GET
  @Hidden
  @Path("users-count")
  @ApiOperation(
      value = "Get total count of users present on Harness platform", nickname = "getUsersCount", hidden = true)
  @Operation(operationId = "getUsersCount", summary = "Get total count of users present on Harness platform",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns count of users present on Harness platform")
      },
      hidden = true)
  @InternalApi
  public ResponseDTO<UsersCountDTO>
  getUsersCount(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam(
                    NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @NotNull @QueryParam(NGResourceFilterConstants.START_TIME) long startInterval,
      @NotNull @QueryParam(NGResourceFilterConstants.END_TIME) long endInterval) {
    return ResponseDTO.newResponse(ngUserService.getUsersCount(
        Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), startInterval, endInterval));
  }

  @PUT
  @Hidden
  @Path("update-user-metadata/{userId}")
  @ApiOperation(value = "update user metadata", nickname = "updateUserMetadata")
  @Operation(operationId = "updateUserMetadata", summary = "Update User Metadata",
      description = "Update User Metadata for a user",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns user information")
      })
  @InternalApi
  public ResponseDTO<UserMetadata>
  updateUserMetadata(@Parameter(description = "user Identifier") @NotNull @PathParam("userId") String userId,
      @Body UserMetadataDTO userMetadataDTO) {
    if (!userId.equals(userMetadataDTO.getUuid())) {
      throw new InvalidRequestException(
          "UserMetadata passed in the request body does not belong to the userId passed in the path");
    }
    return ResponseDTO.newResponse(ngUserService.updateUserMetadataInternal(userMetadataDTO));
  }
}
