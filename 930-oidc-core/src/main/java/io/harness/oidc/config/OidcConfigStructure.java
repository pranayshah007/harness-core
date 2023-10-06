/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.config;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.oidc.accesstoken.OidcAccessTokenConfigStructure;
import io.harness.oidc.accesstoken.OidcAccessTokenConstants;
import io.harness.oidc.idtoken.OidcIdTokenConstants;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
public class OidcConfigStructure {
  @JsonProperty(io.harness.oidc.config.OidcConfigConstants.OPENID_CONFIGURATION)
  private OidcConfiguration oidcConfiguration;
  @JsonProperty(io.harness.oidc.config.OidcConfigConstants.GCP_OIDC) private OidcTokenStructure gcpOidcToken;

  @Data
  public static class OidcConfiguration {
    @JsonProperty(io.harness.oidc.config.OidcConfigConstants.ISSUER) private String issuer;
    @JsonProperty(io.harness.oidc.config.OidcConfigConstants.JWKS_URI) private String jwksUri;
    @JsonProperty(io.harness.oidc.config.OidcConfigConstants.SUBJECT_TYPES_SUPPORTED)
    private List<String> subTypesSupported;
    @JsonProperty(io.harness.oidc.config.OidcConfigConstants.RESPONSE_TYPES_SUPPORTED)
    private List<String> responseTypesSupported;
    @JsonProperty(io.harness.oidc.config.OidcConfigConstants.CLAIMS_SUPPORTED) private List<String> claimsSupported;
    @JsonProperty(io.harness.oidc.config.OidcConfigConstants.SIGNING_ALGS_SUPPORTED) List<String> signingAlgsSupported;
    @JsonProperty(io.harness.oidc.config.OidcConfigConstants.SCOPES_SUPPORTED) List<String> scopesSupported;
  }

  @Data
  public static class OidcTokenStructure {
    @JsonProperty(OidcIdTokenConstants.HEADER)
    io.harness.oidc.idtoken.OidcIdTokenHeaderStructure oidcIdTokenHeaderStructure;
    @JsonProperty(OidcIdTokenConstants.PAYLOAD)
    io.harness.oidc.idtoken.OidcIdTokenPayloadStructure oidcIdTokenPayloadStructure;
    @JsonProperty(io.harness.oidc.accesstoken.OidcAccessTokenConstants.ACCESS_TOKEN_EXCHANGE_ENDPOINT)
    private OidcAccessTokenConfigStructure.OidcAccessTokenExchangeEndpoint oidcAccessTokenExchangeEndpoint;
    @JsonProperty(OidcAccessTokenConstants.ACCESS_TOKEN_CONFIG)
    io.harness.oidc.accesstoken.OidcAccessTokenRequest oidcAccessTokenRequestStructure;
  }
}
