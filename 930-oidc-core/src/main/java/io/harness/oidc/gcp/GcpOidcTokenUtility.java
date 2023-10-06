/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.gcp;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.oidc.accesstoken.OidcAccessTokenUtility.getOidcAccessToken;
import static io.harness.oidc.gcp.GcpOidcIdTokenConstants.GCP_PROJECT_ID;
import static io.harness.oidc.gcp.GcpOidcIdTokenConstants.PROVIDER_ID;
import static io.harness.oidc.gcp.GcpOidcIdTokenConstants.WORKLOAD_POOL_ID;
import static io.harness.oidc.idtoken.OidcIdTokenConstants.ACCOUNT_ID;
import static io.harness.oidc.idtoken.OidcIdTokenUtility.capturePlaceholderContents;
import static io.harness.oidc.idtoken.OidcIdTokenUtility.generateOidcIdToken;

import static java.lang.System.currentTimeMillis;

import io.harness.oidc.accesstoken.OidcAccessTokenConfigStructure.OidcAccessTokenExchangeEndpoint;
import io.harness.oidc.accesstoken.OidcAccessTokenRequest;
import io.harness.oidc.accesstoken.OidcAccessTokenResponse;
import io.harness.oidc.idtoken.OidcIdTokenHeaderStructure;
import io.harness.oidc.idtoken.OidcIdTokenPayloadStructure;
import io.harness.oidc.jwks.OidcJwksUtility;

import com.google.inject.Inject;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class GcpOidcTokenUtility {
  @Inject private io.harness.oidc.config.OidcConfigurationUtility oidcConfigurationUtility;
  @Inject private OidcJwksUtility oidcJwksUtility;

  /**
   * Utility function to generate the OIDC ID Token for GCP.
   *
   * @param gcpOidcTokenRequestDTO GCP metadata needed to generate ID token
   * @return OIDC ID Token for GCP
   */
  public String generateGcpOidcIdToken(io.harness.oidc.gcp.GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO) {
    // Get the base OIDC ID Token Header and Payload structure.
    OidcIdTokenHeaderStructure baseOidcIdTokenHeaderStructure =
        oidcConfigurationUtility.getGcpOidcTokenStructure().getOidcIdTokenHeaderStructure();
    OidcIdTokenPayloadStructure baseOidcIdTokenPayloadStructure =
        oidcConfigurationUtility.getGcpOidcTokenStructure().getOidcIdTokenPayloadStructure();

    // Get the JWKS private key and kid
    io.harness.oidc.entities.OidcJwks oidcJwks = oidcJwksUtility.getJwksKeys(gcpOidcTokenRequestDTO.getAccountId());

    // parse the base token structure and generate appropriate values
    OidcIdTokenHeaderStructure finalOidcIdTokenHeader =
        parseOidcIdTokenHeader(baseOidcIdTokenHeaderStructure, oidcJwks.getKeyId());
    OidcIdTokenPayloadStructure finalOidcIdTokenPayload =
        parseOidcIdTokenPayload(baseOidcIdTokenPayloadStructure, gcpOidcTokenRequestDTO);

    // Generate the OIDC ID Token JWT
    return generateOidcIdToken(
        finalOidcIdTokenHeader, finalOidcIdTokenPayload, oidcJwks.getRsaKeyPair().getPrivateKeyRef());
  }

  /**
   * Utility function to exchange for the OIDC Access Token for GCP.
   *
   * @param gcpOidcAccessTokenRequestDTO GCP metadata needed to exchange for Access token
   * @return OIDC Access Token for GCP
   */
  public OidcAccessTokenResponse exchangeOidcAccessToken(
      io.harness.oidc.gcp.GcpOidcAccessTokenRequestDTO gcpOidcAccessTokenRequestDTO) {
    // Get the base OIDC Access Token structure.
    OidcAccessTokenExchangeEndpoint oidcAccessTokenExchangeEndpoint =
        oidcConfigurationUtility.getGcpOidcTokenStructure().getOidcAccessTokenExchangeEndpoint();
    OidcAccessTokenRequest baseOidcAccessTokenRequest =
        oidcConfigurationUtility.getGcpOidcTokenStructure().getOidcAccessTokenRequestStructure();

    // parse the base token structure and generate appropriate values
    OidcAccessTokenRequest finalOidcAccessTokenRequest = parseOidcAccessTokenRequestPayload(baseOidcAccessTokenRequest,
        gcpOidcAccessTokenRequestDTO.getOidcIdToken(), gcpOidcAccessTokenRequestDTO.getGcpOidcTokenRequestDTO());

    return getOidcAccessToken(oidcAccessTokenExchangeEndpoint, finalOidcAccessTokenRequest);
  }

  /**
   * This function is used to parse the base Oidc ID token header structure
   * and generate the appropriate values for GCP ID token header.
   *
   * @param baseOidcIdTokenHeaderStructure base header values for ID token
   * @param kid key identifier
   * @return OIDC ID Token Header
   */
  private OidcIdTokenHeaderStructure parseOidcIdTokenHeader(
      OidcIdTokenHeaderStructure baseOidcIdTokenHeaderStructure, String kid) {
    return OidcIdTokenHeaderStructure.builder()
        .typ(baseOidcIdTokenHeaderStructure.getTyp())
        .alg(baseOidcIdTokenHeaderStructure.getAlg())
        .kid(kid)
        .build();
  }

  /**
   * This function is used to parse the base Oidc ID token payload structure
   * and generate the appropriate values for GCP ID token payload.
   *
   * @param baseOidcIdTokenPayloadStructure base payload values for ID token
   * @param gcpOidcTokenRequestDTO GCP metadata needed for payload
   * @return OIDC ID Token Payload
   */
  private OidcIdTokenPayloadStructure parseOidcIdTokenPayload(
      OidcIdTokenPayloadStructure baseOidcIdTokenPayloadStructure,
      io.harness.oidc.gcp.GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO) {
    // First parse all the mandatory claims.
    String baseSub = baseOidcIdTokenPayloadStructure.getSub();
    String finalSub = updateBaseClaims(baseSub, gcpOidcTokenRequestDTO);

    String baseAud = baseOidcIdTokenPayloadStructure.getAud();
    String finalAud = updateBaseClaims(baseAud, gcpOidcTokenRequestDTO);

    String baseIss = baseOidcIdTokenPayloadStructure.getIss();
    String finalIss = updateBaseClaims(baseIss, gcpOidcTokenRequestDTO);

    Long base = currentTimeMillis();
    String iat = baseOidcIdTokenPayloadStructure.getIat();
    // Check if iat should be generated at runtime
    if (iat.contains(io.harness.oidc.config.OidcConfigurationUtility.GENERATE_AT_RUNTIME)) {
      iat = Long.toString(base);
    }

    Long exp = baseOidcIdTokenPayloadStructure.getExp();
    exp = base + exp;

    // Now parse the optional claims.
    String accountId = null;
    if (!StringUtils.isEmpty(baseOidcIdTokenPayloadStructure.getAccount_id())) {
      accountId = updateBaseClaims(baseOidcIdTokenPayloadStructure.getAccount_id(), gcpOidcTokenRequestDTO);
    }

    return OidcIdTokenPayloadStructure.builder()
        .sub(finalSub)
        .aud(finalAud)
        .iss(finalIss)
        .iat(iat)
        .exp(exp)
        .account_id(accountId)
        .build();
  }

  /**
   * This function is used to parse the base Oidc Access token payload structure
   * and generate the appropriate values for GCP Access token request payload.
   *
   * @param baseOidcAccessTokenRequest base payload values for Access Token
   * @param oidcIdToken optional OIDC ID Token
   * @param gcpOidcTokenRequestDTO GCP metadata needed for payload
   * @return OIDC Access Token Request payload
   */
  private OidcAccessTokenRequest parseOidcAccessTokenRequestPayload(OidcAccessTokenRequest baseOidcAccessTokenRequest,
      String oidcIdToken, io.harness.oidc.gcp.GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO) {
    String baseAud = baseOidcAccessTokenRequest.getAudience();
    String finalAud = updateBaseClaims(baseAud, gcpOidcTokenRequestDTO);

    String finalOidcIdToken = oidcIdToken;
    if (isEmpty(finalOidcIdToken)) {
      // ID Token not provided generate one now.
      finalOidcIdToken = generateGcpOidcIdToken(gcpOidcTokenRequestDTO);
    }

    // TODO : Ignoring options for now

    return OidcAccessTokenRequest.builder()
        .audience(finalAud)
        .grantType(baseOidcAccessTokenRequest.getGrantType())
        .requestedTokenType(baseOidcAccessTokenRequest.getRequestedTokenType())
        .scope(baseOidcAccessTokenRequest.getScope())
        .subjectTokenType(baseOidcAccessTokenRequest.getSubjectTokenType())
        .subjectToken(finalOidcIdToken)
        .build();
  }

  /**
   * Utility function to update the given base claim
   * by replacing the placeholders with the given values.
   *
   * @param claim base claim to be updated
   * @param gcpOidcTokenRequestDTO provides values for updating the base claims
   * @return fully resolved final claim
   */
  private String updateBaseClaims(String claim, io.harness.oidc.gcp.GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO) {
    List<String> placeHolders = capturePlaceholderContents(claim);
    for (String placeholder : placeHolders) {
      switch (placeholder) {
        case ACCOUNT_ID:
          claim = claim.replace(placeholder, gcpOidcTokenRequestDTO.getAccountId());
          break;
        case WORKLOAD_POOL_ID:
          claim = claim.replace(placeholder, gcpOidcTokenRequestDTO.getWorkloadPoolId());
          break;
        case PROVIDER_ID:
          claim = claim.replace(placeholder, gcpOidcTokenRequestDTO.getProviderId());
          break;
        case GCP_PROJECT_ID:
          claim = claim.replace(placeholder, gcpOidcTokenRequestDTO.getGcpProjectId());
          break;
      }
    }
    return claim;
  }
}
