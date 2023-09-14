/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGE_USERGROUP_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.USERGROUP;
import static io.harness.ng.core.utils.NGUtils.verifyValuesNotChanged;
import static io.harness.ng.core.utils.UserGroupMapper.toDTO;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.annotations.dev.OwnedBy;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupRequestV2DTO;
import io.harness.ng.core.dto.UserGroupResponseV2DTO;
import io.harness.ng.core.user.entities.UserGroup;
import io.harness.ng.core.utils.UserGroupMapper;
import io.harness.security.annotations.NextGenManagerAuth;

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
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
@Api("v2/user-groups")
@Path("v2/user-groups")
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
public class UserGroupResourceV2 {
  private final UserGroupService userGroupService;
  private final AccessControlClient accessControlClient;

  @POST
  @ApiOperation(value = "Create a User Group", nickname = "postUserGroupV2")
  @Operation(operationId = "postUserGroupV2", summary = "Create User Group",
      description = "Create a User Group in an account/org/project",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the successfully created User Group")
      })
  @FeatureRestrictionCheck(FeatureRestrictionName.MULTIPLE_USER_GROUPS)
  public ResponseDTO<UserGroupResponseV2DTO>
  create(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(description = "User Group entity to be created",
          required = true) @NotNull @Valid UserGroupRequestV2DTO userGroupDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, null), MANAGE_USERGROUP_PERMISSION);
    validateScopes(accountIdentifier, orgIdentifier, projectIdentifier, userGroupDTO);
    userGroupDTO.setAccountIdentifier(accountIdentifier);
    userGroupDTO.setOrgIdentifier(orgIdentifier);
    userGroupDTO.setProjectIdentifier(projectIdentifier);
    List<String> userIds = userGroupService.getUserIds(userGroupDTO.getUsers());

    UserGroup userGroup = userGroupService.create(UserGroupMapper.toV1(userGroupDTO, userIds));
    return ResponseDTO.newResponse(Long.toString(userGroup.getVersion()),
        UserGroupMapper.toV2Response(toDTO(userGroup), userGroupService.getUserMetaData(userIds)));
  }

  @PUT
  @ApiOperation(value = "Update a User Group", nickname = "putUserGroupV2")
  @Operation(operationId = "putUserGroupV2", description = "Update a User Group in an account/org/project",
      summary = "Update User Group",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Returns the successfully updated User Group")
      })
  public ResponseDTO<UserGroupResponseV2DTO>
  update(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(description = "User Group entity with the updates",
          required = true) @NotNull @Valid UserGroupRequestV2DTO userGroupDTO) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(USERGROUP, userGroupDTO.getIdentifier()), MANAGE_USERGROUP_PERMISSION);
    validateScopes(accountIdentifier, orgIdentifier, projectIdentifier, userGroupDTO);
    userGroupDTO.setAccountIdentifier(accountIdentifier);
    userGroupDTO.setOrgIdentifier(orgIdentifier);
    userGroupDTO.setProjectIdentifier(projectIdentifier);
    List<String> userIds = userGroupService.getUserIds(userGroupDTO.getUsers());
    UserGroup userGroup =
        userGroupService.updateWithCheckThatSCIMFieldsAreNotModified(UserGroupMapper.toV1(userGroupDTO, userIds));
    return ResponseDTO.newResponse(Long.toString(userGroup.getVersion()),
        UserGroupMapper.toV2Response(toDTO(userGroup), userGroupService.getUserMetaData(userIds)));
  }

  private static void validateScopes(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, UserGroupRequestV2DTO userGroupDTO) {
    verifyValuesNotChanged(Lists.newArrayList(Pair.of(accountIdentifier, userGroupDTO.getAccountIdentifier()),
                               Pair.of(orgIdentifier, userGroupDTO.getOrgIdentifier()),
                               Pair.of(projectIdentifier, userGroupDTO.getProjectIdentifier())),
        true);
  }
}
