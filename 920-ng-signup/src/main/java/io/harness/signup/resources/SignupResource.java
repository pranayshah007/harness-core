/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.signup.resources;

import static io.harness.annotations.dev.HarnessTeam.GTM;

import static java.lang.Boolean.TRUE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.licensing.NGLicensingEntityConstants;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.PublicApi;
import io.harness.signup.dto.OAuthSignupDTO;
import io.harness.signup.dto.SignupDTO;
import io.harness.signup.dto.VerifyTokenResponseDTO;
import io.harness.signup.services.SignupService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Api("signup")
@Path("signup")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Hidden
@OwnedBy(GTM)
@Slf4j
public class SignupResource {
  private SignupService signupService;

  /**
   * Follows the "free trial sign up" path
   * Module type can be optional but by default we will always redirect to NG
   *
   * @param dto
   * @return
   */
  @POST
  @PublicApi
  public RestResponse<Void> signup(SignupDTO dto, @QueryParam("captchaToken") @Nullable String captchaToken) {
    try {
      signupService.createSignupInvite(dto, captchaToken);
      return new RestResponse<>();
    } catch (Exception e) {
      log.error("Signup failed. {} at {}", e.getMessage(), e.getStackTrace());
      throw e;
    }
  }

  /**
   * Follows the "free trial sign up" path
   * Module type can be optional but by default we will always redirect to NG
   *
   * @param dto
   * @return
   */
  @POST
  @Path("/community")
  @PublicApi
  public RestResponse<UserInfo> communitySignup(SignupDTO dto) {
    try {
      return new RestResponse<>(signupService.communitySignup(dto));
    } catch (Exception e) {
      log.error("Signup completion failed. {} at {}", e.getMessage(), e.getStackTrace());
      throw e;
    }
  }

  @PUT
  @Path("/complete/{token}")
  @PublicApi
  public RestResponse<UserInfo> completeSignupInvite(@PathParam("token") String token,
      @QueryParam("referer") String referer, @QueryParam(NGLicensingEntityConstants.GA_CLIENT_ID) String gaClientId,
      @QueryParam(NGLicensingEntityConstants.VISITOR_TOKEN) String visitorToken) {
    try {
      return new RestResponse<>(signupService.completeSignupInvite(token, referer, gaClientId, visitorToken));
    } catch (Exception e) {
      log.error("Signup completion failed. {} at {}", e.getMessage(), e.getStackTrace());
      throw e;
    }
  }

  /**
   * Follows the "oauth" path
   *
   * @param dto
   * @return
   */
  @POST
  @Path("/oauth")
  @PublicApi
  public RestResponse<UserInfo> signupOAuth(OAuthSignupDTO dto) {
    try {
      return new RestResponse<>(signupService.oAuthSignup(dto));
    } catch (Exception e) {
      log.error("OAuth signup failed. {} at {}", e.getMessage(), e.getStackTrace());
      throw e;
    }
  }

  @POST
  @Path("/verify/{token}")
  @PublicApi
  public RestResponse<VerifyTokenResponseDTO> verifyToken(@PathParam("token") String token) {
    try {
      return new RestResponse<>(signupService.verifyToken(token));
    } catch (Exception e) {
      log.error("Signup token verification failed. {} at {}", e.getMessage(), e.getStackTrace());
      throw e;
    }
  }

  @POST
  @Path("verify-notification")
  @Produces("application/json")
  @Consumes("application/json")
  @ApiOperation(value = "Resend user verification email", nickname = "resendVerifyEmail")
  @PublicApi
  public ResponseDTO<Boolean> resendVerifyEmail(@NotNull @QueryParam("email") String email) {
    try {
      signupService.resendVerificationEmail(email);
      return ResponseDTO.newResponse(TRUE);
    } catch (Exception e) {
      log.error("Resending verification email failed. {} at {}", e.getMessage(), e.getStackTrace());
      throw e;
    }
  }
}
