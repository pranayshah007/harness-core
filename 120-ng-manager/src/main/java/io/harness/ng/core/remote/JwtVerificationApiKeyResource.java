package io.harness.ng.core.remote;


import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.annotations.dev.HarnessTeam.PL;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import com.google.inject.Inject;
import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ApiKeyDTO;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.JWTVerificationApiKeyDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("jwt-verification-apikey")
@Path("jwt-verification-apikey")
@Produces({"application/json", "application/yaml", "text/plain"})
@Consumes({"application/json", "application/yaml", "text/plain"})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject}))
@Tag(name = "JwtVerificationApiKey", description = "This provides management of JWT Verification API keys defined in Harness")
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
@ApiResponses(value =
    {
        @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
        , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Slf4j
@OwnedBy(PL)
public class JwtVerificationApiKeyResource {


  @POST
  @ApiOperation(value = "Create JWT Verification API key at service account level", nickname = "createJwtVerificationApiKey")
  @Operation(operationId = "createApiKey", summary = "Creates an JWT Verification API key",
      responses =
          {
              @io.swagger.v3.oas.annotations.responses.
                  ApiResponse(responseCode = "default", description = "Returns the created JWT Verification API key")
          })
  public ResponseDTO<JWTVerificationApiKeyDTO>
  createApiKey(@Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @NotNull @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @AccountIdentifier String accountIdentifier,
               @Valid ApiKeyDTO apiKeyDTO) {
    return ResponseDTO.newResponse(null);
  }

}
