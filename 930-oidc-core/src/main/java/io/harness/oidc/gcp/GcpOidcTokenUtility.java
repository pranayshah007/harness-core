/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.gcp;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.oidc.accesstoken.OidcAccessTokenUtility.getOidcWorkloadAccessToken;
import static io.harness.oidc.gcp.GcpOidcIdTokenConstants.GCP_PROJECT_ID;
import static io.harness.oidc.gcp.GcpOidcIdTokenConstants.PROVIDER_ID;
import static io.harness.oidc.gcp.GcpOidcIdTokenConstants.WORKLOAD_POOL_ID;
import static io.harness.oidc.idtoken.OidcIdTokenConstants.ACCOUNT_ID;
import static io.harness.oidc.idtoken.OidcIdTokenUtility.capturePlaceholderContents;
import static io.harness.oidc.idtoken.OidcIdTokenUtility.generateOidcIdToken;

import static java.lang.System.currentTimeMillis;

import io.harness.network.Http;
import io.harness.oidc.accesstoken.OidcWorkloadAccessTokenRequest;
import io.harness.oidc.accesstoken.OidcWorkloadAccessTokenResponse;
import io.harness.oidc.config.OidcConfigurationUtility;
import io.harness.oidc.entities.OidcJwks;
import io.harness.oidc.idtoken.OidcIdTokenHeaderStructure;
import io.harness.oidc.idtoken.OidcIdTokenPayloadStructure;
import io.harness.oidc.jwks.OidcJwksUtility;
import io.harness.oidc.rsa.OidcRsaKeyService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@Slf4j
public class GcpOidcTokenUtility {
  @Inject private OidcConfigurationUtility oidcConfigurationUtility;
  @Inject private OidcJwksUtility oidcJwksUtility;
  @Inject private OidcRsaKeyService oidcRsaKeyService;

  /**
   * Utility function to generate the OIDC ID Token for GCP.
   *
   * @param gcpOidcTokenRequestDTO GCP metadata needed to generate ID token
   * @return OIDC ID Token for GCP
   */
  public String generateGcpOidcIdToken(GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO) {
    // Get the base OIDC ID Token Header and Payload structure.
    OidcIdTokenHeaderStructure baseOidcIdTokenHeaderStructure =
        oidcConfigurationUtility.getGcpOidcTokenStructure().getOidcIdTokenHeaderStructure();
    OidcIdTokenPayloadStructure baseOidcIdTokenPayloadStructure =
        oidcConfigurationUtility.getGcpOidcTokenStructure().getOidcIdTokenPayloadStructure();

    // Get the JWKS private key and kid
    OidcJwks oidcJwks = oidcJwksUtility.getJwksKeys(gcpOidcTokenRequestDTO.getAccountId());

    // parse the base token structure and generate appropriate values
    OidcIdTokenHeaderStructure finalOidcIdTokenHeader =
        parseOidcIdTokenHeader(baseOidcIdTokenHeaderStructure, oidcJwks.getKeyId());
    OidcIdTokenPayloadStructure finalOidcIdTokenPayload =
        parseOidcIdTokenPayload(baseOidcIdTokenPayloadStructure, gcpOidcTokenRequestDTO);

    // Generate the OIDC ID Token JWT
    return generateOidcIdToken(finalOidcIdTokenHeader, finalOidcIdTokenPayload,
        oidcRsaKeyService.getDecryptedJwksPrivateKeyPem(
            gcpOidcTokenRequestDTO.getAccountId(), oidcJwks.getRsaKeyPair()));
  }

  /**
   * Utility function to exchange for the OIDC GCP Workload Access Token.
   *
   * @param gcpOidcAccessTokenRequestDTO GCP metadata needed to exchange for Access token
   * @return OIDC Workload Access Token for GCP
   */
  public OidcWorkloadAccessTokenResponse exchangeOidcWorkloadAccessToken(
      GcpOidcAccessTokenRequestDTO gcpOidcAccessTokenRequestDTO) {
    // Get the base OIDC Access Token structure.
    String oidcAccessTokenExchangeEndpoint =
        oidcConfigurationUtility.getGcpOidcTokenStructure().getOidcAccessTokenStsEndpoint();
    OidcWorkloadAccessTokenRequest baseOidcAccessTokenRequest =
        oidcConfigurationUtility.getGcpOidcTokenStructure().getOidcWorkloadAccessTokenRequestStructure();

    // parse the base token structure and generate appropriate values
    OidcWorkloadAccessTokenRequest finalOidcAccessTokenRequest =
        parseOidcAccessTokenRequestPayload(baseOidcAccessTokenRequest, gcpOidcAccessTokenRequestDTO.getOidcIdToken(),
            gcpOidcAccessTokenRequestDTO.getGcpOidcTokenRequestDTO());

    return getOidcWorkloadAccessToken(oidcAccessTokenExchangeEndpoint, finalOidcAccessTokenRequest);
  }

  /**
   * Utility function to exchange for the OIDC GCP Service Account Access Token.
   *
   * @param gcpOidcAccessTokenRequestDTO GCP metadata needed to exchange for Access token
   * @return OIDC Service Account Access Token for GCP
   */
  public GcpOidcServiceAccountAccessTokenResponse exchangeOidcServiceAccountAccessToken(
      GcpOidcAccessTokenRequestDTO gcpOidcAccessTokenRequestDTO) {
    // Get the Workload Access Token to be used as bearer
    OidcWorkloadAccessTokenResponse oidcWorkloadAccessTokenResponse =
        exchangeOidcWorkloadAccessToken(gcpOidcAccessTokenRequestDTO);
    String oidcIamSaTokenExchangeEndpoint =
        oidcConfigurationUtility.getGcpOidcTokenStructure().getOidcAccessTokenIamSaEndpoint();
    GcpOidcServiceAccountAccessTokenRequest gcpOidcServiceAccountAccessTokenRequest =
        GcpOidcServiceAccountAccessTokenRequest.builder()
            .delegates(null)
            .scope(new ArrayList<>(Arrays.asList("https://www.googleapis.com/auth/cloud-platform")))
            .lifetime(null)
            .build();
    return getOidcServiceAccountAccessToken(oidcIamSaTokenExchangeEndpoint, gcpOidcServiceAccountAccessTokenRequest,
        oidcWorkloadAccessTokenResponse.getAccessToken());
  }

  /**
   * Utility function to exchange for the OIDC GCP Service Account Access Token.
   *
   * @param gcpOidcIamSaApiEndpoint The GCP IAM endpoint to make the token exchange with.
   * @param gcpOidcServiceAccountAccessTokenRequest The Token exchange request body.
   * @param workloadAccessToken The OIDC Workload Access Token which will be used as Authorization bearer.
   * @return Service Account Access Token
   */
  public GcpOidcServiceAccountAccessTokenResponse getOidcServiceAccountAccessToken(String gcpOidcIamSaApiEndpoint,
      GcpOidcServiceAccountAccessTokenRequest gcpOidcServiceAccountAccessTokenRequest, String workloadAccessToken) {
    // Create an OkHttpClient with any desired configurations (e.g., timeouts, interceptors)
    OkHttpClient httpClient = new OkHttpClient.Builder()
                                  .connectionPool(Http.connectionPool)
                                  .readTimeout(60, TimeUnit.SECONDS)
                                  .retryOnConnectionFailure(true)
                                  .addInterceptor(new GcpOidcAccessTokenIamSaApiInterceptor(workloadAccessToken))
                                  .build();

    // Create a Retrofit client with the base URL
    Retrofit retrofit = new Retrofit.Builder()
                            .baseUrl(String.valueOf(gcpOidcIamSaApiEndpoint))
                            .addConverterFactory(GsonConverterFactory.create())
                            .client(httpClient)
                            .build();

    // Create an instance of the API interface
    GcpOidcAccessTokenIamSaAPI gcpOidcAccessTokenIamSaAPI = retrofit.create(GcpOidcAccessTokenIamSaAPI.class);

    // Make the POST request and handle the response
    Call<GcpOidcServiceAccountAccessTokenResponse> call =
        gcpOidcAccessTokenIamSaAPI.exchangeServiceAccountAccessToken(gcpOidcServiceAccountAccessTokenRequest);

    try {
      Response<GcpOidcServiceAccountAccessTokenResponse> response = call.execute();
      if (response.isSuccessful()) {
        GcpOidcServiceAccountAccessTokenResponse gcpOidcServiceAccountAccessTokenResponse = response.body();
        return gcpOidcServiceAccountAccessTokenResponse;
      } else {
        log.error("Error encountered while obtaining OIDC Access Token from STS for {}",
            gcpOidcServiceAccountAccessTokenRequest);
      }
    } catch (IOException e) {
      log.error("Exception encountered while exchanging OIDC Service Account Access Token {} ", e);
    }

    return null;
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
      OidcIdTokenPayloadStructure baseOidcIdTokenPayloadStructure, GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO) {
    // First parse all the mandatory claims.
    String baseSub = baseOidcIdTokenPayloadStructure.getSub();
    String finalSub = updateBaseClaims(baseSub, gcpOidcTokenRequestDTO);

    String baseAud = baseOidcIdTokenPayloadStructure.getAud();
    String finalAud = updateBaseClaims(baseAud, gcpOidcTokenRequestDTO);

    String baseIss = baseOidcIdTokenPayloadStructure.getIss();
    String finalIss = updateBaseClaims(baseIss, gcpOidcTokenRequestDTO);

    Long iat = currentTimeMillis() / 1000;
    Long exp = baseOidcIdTokenPayloadStructure.getExp();
    exp = iat + exp;

    // Now parse the optional claims.
    String accountId = null;
    if (!StringUtils.isEmpty(baseOidcIdTokenPayloadStructure.getAccountId())) {
      accountId = updateBaseClaims(baseOidcIdTokenPayloadStructure.getAccountId(), gcpOidcTokenRequestDTO);
    }

    return OidcIdTokenPayloadStructure.builder()
        .sub(finalSub)
        .aud(finalAud)
        .iss(finalIss)
        .iat(iat)
        .exp(exp)
        .accountId(accountId)
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
  private OidcWorkloadAccessTokenRequest parseOidcAccessTokenRequestPayload(
      OidcWorkloadAccessTokenRequest baseOidcAccessTokenRequest, String oidcIdToken,
      GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO) {
    String baseAud = baseOidcAccessTokenRequest.getAudience();
    String finalAud = updateBaseClaims(baseAud, gcpOidcTokenRequestDTO);

    String finalOidcIdToken = oidcIdToken;
    if (isEmpty(finalOidcIdToken)) {
      // ID Token not provided generate one now.
      finalOidcIdToken = generateGcpOidcIdToken(gcpOidcTokenRequestDTO);
    }

    // TODO : Ignoring options for now

    return OidcWorkloadAccessTokenRequest.builder()
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
  private String updateBaseClaims(String claim, GcpOidcTokenRequestDTO gcpOidcTokenRequestDTO) {
    List<String> placeHolders = capturePlaceholderContents(claim);
    for (String placeholder : placeHolders) {
      String replaceValue = "";
      switch (placeholder) {
        case ACCOUNT_ID:
          replaceValue = gcpOidcTokenRequestDTO.getAccountId();
          break;
        case WORKLOAD_POOL_ID:
          replaceValue = gcpOidcTokenRequestDTO.getWorkloadPoolId();
          break;
        case PROVIDER_ID:
          replaceValue = gcpOidcTokenRequestDTO.getProviderId();
          break;
        case GCP_PROJECT_ID:
          replaceValue = gcpOidcTokenRequestDTO.getGcpProjectId();
          break;
      }
      // Include {} in the captured placeholder while replacing values.
      claim = claim.replace("{" + placeholder + "}", replaceValue);
    }
    return claim;
  }
}
