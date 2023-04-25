/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

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
import javax.ws.rs.DefaultValue;
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
      @Parameter(description = NGCommonEntityConstants.ORG_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @Parameter(description = NGCommonEntityConstants.PROJECT_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @Parameter(description = "Secret Manager Identifier") @QueryParam("secretManagerIdentifier") @DefaultValue(
          "harnessSecretManager") String secretManagerIdentifier,
      @Parameter(description = "scm provider", required = true) @NotBlank @QueryParam("provider") String scmProvider,
      @Parameter(
          description = "This is a boolean value to specify if the Secret is Private. The default value is False.")
      @QueryParam("isPrivateSecret") @DefaultValue("false") boolean isPrivateSecret,
      @Parameter(description = "access token secret request", required = true) @Body OauthAccessTokenDTO accessToken) {
    return new RestResponse<>(oauthSecretService.createSecrets(accountIdentifier, orgIdentifier, projectIdentifier,
        scmProvider, accessToken, secretManagerIdentifier, isPrivateSecret, accessToken.getUserDetailsDTO()));
  }
}
