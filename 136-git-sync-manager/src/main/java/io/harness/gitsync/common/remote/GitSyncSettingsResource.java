/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.remote;

import static io.harness.NGCommonEntityConstants.ACCOUNT_KEY;
import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.APPLICATION_JSON_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.APPLICATION_YAML_MEDIA_TYPE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_CODE;
import static io.harness.NGCommonEntityConstants.BAD_REQUEST_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_CODE;
import static io.harness.NGCommonEntityConstants.INTERNAL_SERVER_ERROR_MESSAGE;
import static io.harness.NGCommonEntityConstants.ORG_KEY;
import static io.harness.NGCommonEntityConstants.ORG_PARAM_MESSAGE;
import static io.harness.NGCommonEntityConstants.PROJECT_KEY;
import static io.harness.NGCommonEntityConstants.PROJECT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.ng.core.rbac.ProjectPermissions.EDIT_PROJECT_PERMISSION;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.OrgIdentifier;
import io.harness.accesscontrol.ProjectIdentifier;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.accesscontrol.ResourceTypes;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.common.dtos.GitSyncSettingsDTO;
import io.harness.gitsync.common.service.GitSyncSettingsService;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;

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
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

@Api("/git-sync-settings")
@Path("/git-sync-settings")
@Produces({"application/json", "text/yaml", "text/html"})
@Consumes({"application/json", "text/yaml", "text/html"})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Tag(name = "Git Sync Settings", description = "Contains APIs related to Git Sync Settings")
@io.swagger.v3.oas.annotations.responses.
ApiResponse(responseCode = BAD_REQUEST_CODE, description = BAD_REQUEST_PARAM_MESSAGE,
    content =
    {
      @Content(mediaType = APPLICATION_JSON_MEDIA_TYPE, schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = APPLICATION_YAML_MEDIA_TYPE, schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.
ApiResponse(responseCode = INTERNAL_SERVER_ERROR_CODE, description = INTERNAL_SERVER_ERROR_MESSAGE,
    content =
    {
      @Content(mediaType = APPLICATION_JSON_MEDIA_TYPE, schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = APPLICATION_YAML_MEDIA_TYPE, schema = @Schema(implementation = ErrorDTO.class))
    })
@OwnedBy(DX)
public class GitSyncSettingsResource {
  private final GitSyncSettingsService gitSyncSettingsService;
  private final AccessControlClient accessControlClient;

  @POST
  @ApiOperation(value = "Create a Git Sync Setting", nickname = "postGitSyncSetting")
  @Operation(operationId = "createGitSyncSetting", summary = "Creates Git Sync Setting in a scope",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Successfully created Git Sync Setting") })
  public ResponseDTO<GitSyncSettingsDTO>
  create(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
             ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @RequestBody(required = true,
          description = "This contains details of Git Sync settings like - (scope, executionOnDelegate)") @NotNull
      @Valid GitSyncSettingsDTO gitSyncSettings) {
    // todo(abhinav): when git sync comes at other level see for new permission
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, gitSyncSettings.getOrgIdentifier(), gitSyncSettings.getProjectIdentifier()),
        Resource.of(ResourceTypes.PROJECT, gitSyncSettings.getProjectIdentifier()), EDIT_PROJECT_PERMISSION);

    gitSyncSettings.setAccountIdentifier(accountIdentifier);
    return ResponseDTO.newResponse(gitSyncSettingsService.save(gitSyncSettings));
  }

  @GET
  @ApiOperation(value = "Get git sync settings", nickname = "getGitSyncSettings")
  @Operation(operationId = "getGitSyncSettings", summary = "Get Git Sync Setting for the given scope",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Git Sync Setting of the given scope") })
  public ResponseDTO<GitSyncSettingsDTO>
  get(@Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(ORG_KEY) String organizationIdentifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(ACCOUNT_KEY) @NotEmpty String accountIdentifier) {
    final Optional<GitSyncSettingsDTO> gitSyncSettingsDTO =
        gitSyncSettingsService.get(accountIdentifier, organizationIdentifier, projectIdentifier);
    return gitSyncSettingsDTO.map(ResponseDTO::newResponse)
        .orElseThrow(
            ()
                -> new InvalidRequestException(String.format(
                    "No Git Sync Setting found for accountIdentifier %s, orgIdentifier %s and projectIdentifier %s",
                    accountIdentifier, organizationIdentifier, projectIdentifier)));
  }

  @PUT
  @ApiOperation(value = "Update a Git Sync Setting", nickname = "updateGitSyncSetting")
  @Operation(operationId = "updateGitSyncSetting",
      summary =
          "This updates the existing Git Sync settings within the scope. Only changing Connectivity Mode is allowed",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "Updated Git Sync Setting") })
  public ResponseDTO<GitSyncSettingsDTO>
  update(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotEmpty @QueryParam(
             ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @RequestBody(required = true, description = "This contains details of Git Sync Settings") @NotNull
      @Valid GitSyncSettingsDTO gitSyncSettings) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, gitSyncSettings.getOrgIdentifier(), gitSyncSettings.getProjectIdentifier()),
        Resource.of(ResourceTypes.PROJECT, gitSyncSettings.getProjectIdentifier()), EDIT_PROJECT_PERMISSION);

    gitSyncSettings.setAccountIdentifier(accountIdentifier);
    return ResponseDTO.newResponse(gitSyncSettingsService.update(gitSyncSettings));
  }

  @POST
  @Hidden
  @Path("/git-simplification")
  @ApiOperation(value = "Enable Git Simplification for a project", nickname = "enableGitSimplification")
  public ResponseDTO<String> enableGitSimplification(
      @Parameter(required = true) @NotEmpty @QueryParam(ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true) @NotEmpty @QueryParam(ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(required = true) @NotEmpty @QueryParam(PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of(ResourceTypes.PROJECT, projectIdentifier), EDIT_PROJECT_PERMISSION);

    gitSyncSettingsService.enableGitSimplification(accountIdentifier, orgIdentifier, projectIdentifier);
    return ResponseDTO.newResponse(String.format(
        "New Git Simplification Experience is enabled for project [%s], please ensure that the project [%s] exists in org [%s] and account [%s]. ",
        projectIdentifier, projectIdentifier, orgIdentifier, accountIdentifier));
  }

  @GET
  @Hidden
  @Path("/git-simplification")
  @ApiOperation(value = "Get Git Simplification status for a project", nickname = "getGitSimplification")
  public ResponseDTO<Boolean> getGitSimplificationStatus(
      @Parameter(required = true) @NotEmpty @QueryParam(ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
      @Parameter(required = true) @NotEmpty @QueryParam(ORG_KEY) @OrgIdentifier String orgIdentifier,
      @Parameter(required = true) @NotEmpty @QueryParam(PROJECT_KEY) @ProjectIdentifier String projectIdentifier) {
    return ResponseDTO.newResponse(
        gitSyncSettingsService.getGitSimplificationStatus(accountIdentifier, orgIdentifier, projectIdentifier));
  }
}
