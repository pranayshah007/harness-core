/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.oidc;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static org.apache.commons.net.util.Base64.encodeBase64URLSafeString;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.helpers.NgExpressionHelper;
import io.harness.exception.InternalServerErrorException;
import io.harness.ng.NextGenConfiguration;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.oidc.dto.JwksPublicKeyDTO;
import io.harness.oidc.entities.OidcJwks;
import io.harness.oidc.jwks.OidcJwksUtility;
import io.harness.rsa.RSAKeysUtils;
import io.harness.security.annotations.PublicApi;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@OwnedBy(PL)
@Api("/oidc/account")
@Path("/oidc/account")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@Tag(name = "OIDC", description = "This contains APIs related to the Harness OIDC config")
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
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class OidcResource {
  OidcJwksUtility oidcJwksUtility;
  RSAKeysUtils rsaKeysUtils;
  NgExpressionHelper ngExpressionHelper;
  NextGenConfiguration nextGenConfiguration;
  final String ACCOUNT_KEY = "accountId";

  @GET
  @Path("{accountId}/.wellknown/openid-configuration")
  @ApiOperation(value = "Gets the openid configuration for Harness", nickname = "getHarnessOpenIdConfig")
  @Operation(operationId = "getHarnessOpenIdConfig", summary = "Get the openid configuration for Harness",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "This gets the openid configuration for Harness")
      })
  @PublicApi
  public Map<String, Object>
  getOpenIdConnectConfig(@Parameter(
      description = "This is the accountIdentifier for the account for which the JWKS public key needs to be exposed.")
      @PathParam(ACCOUNT_KEY) String accountId) {
    String baseUrl = null;
    try {
      baseUrl =
          new URL("https", nextGenConfiguration.getHostname(), nextGenConfiguration.getBasePathPrefix()).toString();
    } catch (MalformedURLException e) {
      log.error("Failed to generate baseURL for openid-configuration endpoint", e);
      throw new InternalServerErrorException("Failed due to internal server error. Please try again after some time.");
    }
    String issuer = String.format("%s/account/%s", baseUrl, accountId);
    JSONObject openIdConfig = new JSONObject(); // TODO- This will be replaced by a DTO once all PRs are merged.
    openIdConfig.put("issuer", issuer);
    openIdConfig.put("jwks_uri", String.format("%s/.well-known/jwks", issuer));
    openIdConfig.put("subject_types_supported", List.of("public", "pairwise"));
    openIdConfig.put("response_types_supported", List.of("id_token"));
    openIdConfig.put("claims_supported", List.of("sub", "aud", "exp", "iat", "iss", "account_id"));
    openIdConfig.put("id_token_signing_alg_values_supported", List.of("RS256"));
    openIdConfig.put("scopes_supported", List.of("openid"));

    return openIdConfig.toMap();
  }

  @GET
  @Path("{accountId}/.well-known/jwks")
  @ApiOperation(value = "Gets the openid configuration for Harness", nickname = "getHarnessOpenIdJwks")
  @Operation(operationId = "getHarnessOpenIdConfig", summary = "Get the openid configuration for Harness",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "This gets the openid configuration for Harness")
      })
  @PublicApi
  public JwksPublicKeyDTO
  getJwksPublicKey(@Parameter(
      description = "This is the accountIdentifier for the account for which the JWKS public key needs to be exposed.")
      @PathParam(ACCOUNT_KEY) String accountId) {
    OidcJwks oidcJwks = oidcJwksUtility.getJwksKeys(accountId);
    RSAPublicKey publicKey = (RSAPublicKey) rsaKeysUtils.readPemFile(oidcJwks.getRsaKeyPair().getPublicKey());
    return JwksPublicKeyDTO.builder()
        .algorithm("RSA256")
        .exponent(encodeBase64URLSafeString(publicKey.getPublicExponent().toByteArray()))
        .kid(oidcJwks.getKeyId())
        .keyType("RSA")
        .modulus(encodeBase64URLSafeString(publicKey.getModulus().toByteArray()))
        .use("sig")
        .build();
  }
}
