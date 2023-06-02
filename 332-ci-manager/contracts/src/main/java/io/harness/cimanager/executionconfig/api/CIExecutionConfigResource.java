/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cimanager.executionconfig.api;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.EDIT_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.AccountAccessControlPermissions.VIEW_ACCOUNT_PERMISSION;
import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.accesscontrol.commons.exceptions.AccessDeniedErrorDTO;
import io.harness.account.accesscontrol.ResourceTypes;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.StageInfraDetails.Type;
import io.harness.ci.beans.entities.CIExecutionImages;
import io.harness.ci.config.Operation;
import io.harness.ci.execution.DeprecatedImageInfo;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

@OwnedBy(CI)
@Api("/execution-config")
@Path("/execution-config")
@Produces({"application/json"})
@Consumes({"application/json"})
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
public interface CIExecutionConfigResource {
  @POST
  @Path("/update-config")
  @ApiOperation(value = "Update execution config", nickname = "updateExecutionConfig")
  @io.swagger.v3.oas.annotations.
  Operation(operationId = "updateExecutionConfig", summary = "Override execution Config for CI builds",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "True or False") })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  ResponseDTO<Boolean>
  updateExecutionConfig(@NotNull @QueryParam(NGCommonEntityConstants.INFRA) Type infra,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @RequestBody(required = true,
          description = "Details of the Update Operations") @NotNull @Valid List<Operation> operations);

  @POST
  @Path("/reset-config")
  @ApiOperation(value = "Reset execution config", nickname = "resetExecutionConfig")
  @io.swagger.v3.oas.annotations.
  Operation(operationId = "resetExecutionConfig", summary = "Reset execution Config for CI builds",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "True or False") })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  ResponseDTO<Boolean>
  resetExecutionConfig(@NotNull @QueryParam(NGCommonEntityConstants.INFRA) Type infra,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @RequestBody(
          required = true, description = "Details of the Reset Operations") @NotNull @Valid List<Operation> operations);

  @DELETE
  @Path("/")
  @ApiOperation(value = "Delete execution config", nickname = "deleteExecutionConfig")
  @io.swagger.v3.oas.annotations.Operation(operationId = "deleteExecutionConfig",
      summary = "Delete Execution Config for CI builds and use default configs",
      responses = { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "True or False") })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = EDIT_ACCOUNT_PERMISSION)
  ResponseDTO<Boolean>
  deleteExecutionConfig(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);

  @GET
  @Path("/")
  @ApiOperation(value = "Get execution config", nickname = "getExecutionConfig")
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  @io.swagger.v3.oas.annotations.Operation(operationId = "getExecutionConfig", summary = "Get deprecated tags for CI",
      responses =
      { @io.swagger.v3.oas.annotations.responses.ApiResponse(description = "List of deprecated tags for CI builds") })
  ResponseDTO<List<DeprecatedImageInfo>>
  getExecutionConfig(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);

  @GET
  @Path("/get-deprecated-config")
  @ApiOperation(value = "Get deprecated config", nickname = "getDeprecatedConfig")
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  @io.swagger.v3.oas.annotations.Operation(operationId = "getDeprecatedConfig", summary = "Get deprecated tags for CI",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "Get Customer's deprecated tags for CI builds")
      })
  ResponseDTO<CIExecutionImages>
  getDeprecatedConfig(@NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);

  @GET
  @Path("/get-customer-config")
  @ApiOperation(value = "Get customer's execution config", nickname = "getCustomerConfig")
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  @io.swagger.v3.oas.annotations.
  Operation(operationId = "getCustomerConfig", summary = "Get Customer's current Execution Config for CI builds",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "List of overridden images and versions used in CI Builds")
      })
  ResponseDTO<CIExecutionImages>
  getCustomerConfig(@NotNull @QueryParam(NGCommonEntityConstants.INFRA) Type infra,
      @NotNull @QueryParam(NGCommonEntityConstants.OVERRIDES_ONLY) @DefaultValue("true") boolean overridesOnly,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier);

  @GET
  @Path("/get-default-config")
  @ApiOperation(value = "Get default execution config", nickname = "getDefaultConfig")
  @io.swagger.v3.oas.annotations.
  Operation(operationId = "getDefaultConfig", summary = "Get default Execution Config for CI builds",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(description = "List of default images and versions used in CI Builds")
      })
  @NGAccessControlCheck(resourceType = ResourceTypes.ACCOUNT, permission = VIEW_ACCOUNT_PERMISSION)
  ResponseDTO<CIExecutionImages>
  getDefaultConfig(@NotNull @QueryParam(NGCommonEntityConstants.INFRA) Type infra);
}
