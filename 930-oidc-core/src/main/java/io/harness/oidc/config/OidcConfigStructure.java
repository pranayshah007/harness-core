/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.config;

import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.ACCESS_TOKEN_IAM_SA_CREDENTIALS_ENDPOINT;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.ACCESS_TOKEN_STS_ENDPOINT;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.WORKLOAD_ACCESS_TOKEN_CONFIG;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.oidc.accesstoken.OidcWorkloadAccessTokenRequest;
import io.harness.oidc.idtoken.OidcIdTokenConstants;
import io.harness.oidc.idtoken.OidcIdTokenHeaderStructure;
import io.harness.oidc.idtoken.OidcIdTokenPayloadStructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
public class OidcConfigStructure {
  @JsonProperty(OidcConfigConstants.OPENID_CONFIGURATION) private OidcConfiguration oidcConfiguration;
  @JsonProperty(OidcConfigConstants.GCP_OIDC) private OidcTokenStructure gcpOidcToken;

  @Data
  public static class OidcConfiguration {
    @JsonProperty(OidcConfigConstants.ISSUER) private String issuer;
    @JsonProperty(OidcConfigConstants.JWKS_URI) private String jwksUri;
    @JsonProperty(OidcConfigConstants.SUBJECT_TYPES_SUPPORTED) private List<String> subTypesSupported;
    @JsonProperty(OidcConfigConstants.RESPONSE_TYPES_SUPPORTED) private List<String> responseTypesSupported;
    @JsonProperty(OidcConfigConstants.CLAIMS_SUPPORTED) private List<String> claimsSupported;
    @JsonProperty(OidcConfigConstants.SIGNING_ALGS_SUPPORTED) List<String> signingAlgsSupported;
    @JsonProperty(OidcConfigConstants.SCOPES_SUPPORTED) List<String> scopesSupported;
  }

  @Data
  public static class OidcTokenStructure {
    @JsonProperty(OidcIdTokenConstants.HEADER) OidcIdTokenHeaderStructure oidcIdTokenHeaderStructure;
    @JsonProperty(OidcIdTokenConstants.PAYLOAD) OidcIdTokenPayloadStructure oidcIdTokenPayloadStructure;
    @JsonProperty(ACCESS_TOKEN_STS_ENDPOINT) private String oidcAccessTokenStsEndpoint;
    @JsonProperty(ACCESS_TOKEN_IAM_SA_CREDENTIALS_ENDPOINT) private String oidcAccessTokenIamSaEndpoint;
    @JsonProperty(WORKLOAD_ACCESS_TOKEN_CONFIG) OidcWorkloadAccessTokenRequest oidcWorkloadAccessTokenRequestStructure;
  }
}
