package io.harness.ng.oauth;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.rest.RestResponse;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;
import retrofit.http.Body;

@OwnedBy(CI)
@Api("oauth")
@Path("/oauth")
@Produces({"application/json"})
@Consumes({"application/json"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class OauthResource {
  @Inject OauthSecretService oauthSecretService;

  @POST
  @Path("create-access-token-secret")
  @ApiOperation(value = "Setup secret for oauth tokens", nickname = "configureOauth")
  public RestResponse<OauthAccessTokenResponseDTO> provisionCIResources(
      @Parameter(description = NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE, required = true) @NotBlank @QueryParam(
          NGCommonEntityConstants.ACCOUNT_KEY) String accountIdentifier,
      @Parameter(description = "scm provider", required = true) @NotBlank @QueryParam("provider") String scmProvider,
      @Parameter(description = "access token secret request", required = true) @Body OauthAccessTokenDTO accessToken) {
    return new RestResponse<>(oauthSecretService.createSecrets(accountIdentifier, scmProvider, accessToken));
  }
}
