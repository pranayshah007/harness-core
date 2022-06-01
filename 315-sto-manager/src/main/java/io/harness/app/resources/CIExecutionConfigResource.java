/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.resources;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.EDIT_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.account.accesscontrol.ResourceTypes;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.execution.CIExecutionConfigService;
import io.harness.execution.DeprecatedImageInfo;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AllArgsConstructor;
import retrofit2.http.Body;

@OwnedBy(CI)
@Api("/execution-config")
@Path("/execution-config")
@Produces({"application/json"})
@Consumes({"application/json"})
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@NextGenManagerAuth
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "CI Execution Config", description = "This contains APIs for CRUD on Execution Configs")
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
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Unauthorized",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = AccessDeniedErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = AccessDeniedErrorDTO.class))
    })
public class CIExecutionConfigResource {
  @Inject CIExecutionConfigService configService;
  @POST
  @Path("/")
  @ApiOperation(value = "Update execution config", nickname = "updateExecutionConfig")
  @Operation(operationId = "updateExecutionConfig", summary = "Override execution Config for CI builds",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "True or False") })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public ResponseDTO<Boolean>
  updateExecutionConfig(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @RequestBody(
          required = true, description = "Details of the filters applied") @Body CIExecutionImages cIExecutionImages) {
    return ResponseDTO.newResponse(configService.updateCIContainerTag(accountIdentifier, cIExecutionImages));
  }

  @DELETE
  @Path("/")
  @ApiOperation(value = "Delete execution config", nickname = "deleteExecutionConfig")
  @Operation(operationId = "deleteExecutionConfig",
      summary = "Delete Execution Config for CI builds and use default configs",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "True or False") })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  public ResponseDTO<Boolean>
  deleteExecutionConfig(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    return ResponseDTO.newResponse(configService.deleteCIExecutionConfig(accountIdentifier));
  }

  @GET
  @Path("/")
  @ApiOperation(value = "Get execution config", nickname = "getExecutionConfig")
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  @Operation(operationId = "getExecutionConfig", summary = "Get deprecated tags for CI",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "List of deprecated tags for CI builds") })
  public ResponseDTO<List<DeprecatedImageInfo>>
  getExecutionConfig(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    return ResponseDTO.newResponse(configService.getDeprecatedTags(accountIdentifier));
  }

  @GET
  @Path("/get-current-config")
  @ApiOperation(value = "Get account's execution config", nickname = "getCurrentConfig")
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  @Operation(operationId = "getCurrentConfig", summary = "Get current Execution Config for CI builds",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "List of overridden images and versions used in CI Builds")
      })
  public ResponseDTO<CIExecutionImages>
  getCurrentConfig(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier) {
    return ResponseDTO.newResponse(configService.getCurrentConfig(accountIdentifier));
  }

  @GET
  @Path("/get-default-config")

  @ApiOperation(value = "Get default execution config", nickname = "getDefaultConfig")
  @Operation(operationId = "getDefaultConfig", summary = "Get default Execution Config for CI builds",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "List of default images and versions used in CI Builds")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  public ResponseDTO<CIExecutionImages>
  getDefaultConfig() {
    return ResponseDTO.newResponse(configService.getDefaultConfig());
  }
}
