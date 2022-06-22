package io.harness.ngsettings.remote;

import static io.harness.NGCommonEntityConstants.*;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.accountsetting.dto.AccountSettingResponseDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngsettings.beans.*;

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
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import retrofit2.http.Body;

@OwnedBy(PL)
@Api("settings")
@Path("settings")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "Setting", description = "This contains APIs related to Settings as defined in Harness")
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
public interface SettingsResource {
  @GET
  @Path("{identifier}")
  @ApiOperation(value = "Resolves and gets a setting value by Identifier", nickname = "getSettingValue")
  @Operation(operationId = "getSettingValue", summary = "Get a setting value by identifier",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "This returns a setting value by the Identifier")
      })
  ResponseDTO<SettingValueResponseDTO>
  get(@Parameter(description = "This is the Identifier of the Entity", required = true) @NotNull @PathParam(
          NGCommonEntityConstants.IDENTIFIER_KEY) String identifier,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @RequestBody(
          description = SettingConstants.FETCH_VALUE_DTO) @Body @NotNull SettingValueRequestDTO settingValueRequetDTO);

  @GET
  @ApiOperation(value = "Get list of settings", nickname = "getSettingsList")
  @Operation(operationId = "getSettingsList", summary = "Get list of settings",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "This contains a list of Settings")
      })
  ResponseDTO<PageResponse<AccountSettingResponseDTO>>
  list(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
           NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(
          description =
              "Details of all the resource groups having this string in their name or identifier will be returned.")
      @QueryParam(NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm,
      @BeanParam PageRequest pageRequest);

  @PUT
  @ApiOperation(value = "Updates the settings", nickname = "getSettingValue")
  @Operation(operationId = "updateSettingValue", summary = "Update settings",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "This updates the settings")
      })
  ResponseDTO<PageResponse<SettingResponseDTO>>
  update(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
             NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = ORG_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = PROJECT_PARAM_MESSAGE) @QueryParam(NGCommonEntityConstants.PROJECT_KEY)
      String projectIdentifier, @RequestBody @Body @NotNull List<SettingRequestDTO>);
}
