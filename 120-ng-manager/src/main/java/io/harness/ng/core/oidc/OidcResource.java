/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.oidc;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.oidc.idtoken.OidcIdTokenConstants.ACCOUNT_ID;
import static io.harness.oidc.idtoken.OidcIdTokenUtility.capturePlaceholderContents;

import static org.apache.commons.net.util.Base64.encodeBase64URLSafeString;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.helpers.NgExpressionHelper;
import io.harness.ng.NextGenConfiguration;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.oidc.config.OidcConfigStructure.OidcConfiguration;
import io.harness.oidc.config.OidcConfigurationUtility;
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
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
  OidcConfigurationUtility oidcConfigurationUtility;

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
  public ResponseDTO<OidcConfiguration>
  getOpenIdConnectConfig(@Parameter(
      description = "This is the accountIdentifier for the account for which the JWKS public key needs to be exposed.")
      @PathParam(ACCOUNT_KEY) String accountId) {
    OidcConfiguration baseOidcConfig = oidcConfigurationUtility.getOidcConfiguration();
    String issuer = updateBaseClaims(baseOidcConfig.getIssuer(), accountId);
    String jwksUri = updateBaseClaims(baseOidcConfig.getJwksUri(), accountId);

    OidcConfiguration finalOidcConfig = OidcConfiguration.builder()
                                            .issuer(issuer)
                                            .jwksUri(jwksUri)
                                            .subTypesSupported(baseOidcConfig.getSubTypesSupported())
                                            .responseTypesSupported(baseOidcConfig.getResponseTypesSupported())
                                            .claimsSupported(baseOidcConfig.getClaimsSupported())
                                            .signingAlgsSupported(baseOidcConfig.getSigningAlgsSupported())
                                            .scopesSupported(baseOidcConfig.getScopesSupported())
                                            .build();

    return ResponseDTO.newResponse(finalOidcConfig);
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
  public ResponseDTO<JwksPublicKeyDTO>
  getJwksPublicKey(@Parameter(
      description = "This is the accountIdentifier for the account for which the JWKS public key needs to be exposed.")
      @PathParam(ACCOUNT_KEY) String accountId) {
    String finalStr = updateBaseClaims("{accountId}", accountId);
    OidcJwks oidcJwks = oidcJwksUtility.getJwksKeys(accountId);
    RSAPublicKey publicKey = (RSAPublicKey) rsaKeysUtils.readPemFile(oidcJwks.getRsaKeyPair().getPublicKey());
    JwksPublicKeyDTO jwksPublicKeyDTO =
        JwksPublicKeyDTO.builder()
            .algorithm("RSA256")
            .exponent(encodeBase64URLSafeString(publicKey.getPublicExponent().toByteArray()))
            .kid(oidcJwks.getKeyId())
            .keyType("RSA")
            .modulus(encodeBase64URLSafeString(publicKey.getModulus().toByteArray()))
            .use("sig")
            .build();

    return ResponseDTO.newResponse(jwksPublicKeyDTO);
  }

  private String updateBaseClaims(String claim, String accountId) {
    List<String> placeHolders = capturePlaceholderContents(claim);
    for (String placeholder : placeHolders) {
      switch (placeholder) {
        case ACCOUNT_ID:
          // Include {} in the captured placeholder while replacing values.
          claim = claim.replace("{" + placeholder + "}", accountId);
          break;
        default:
          break;
      }
    }
    return claim;
  }
}
