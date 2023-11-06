/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.accesstoken;

import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.ACCESS_TOKEN_IAM_SA_CREDENTIALS_ENDPOINT;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.ACCESS_TOKEN_STS_ENDPOINT;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.END_POINT;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.WORKLOAD_ACCESS_TOKEN_CONFIG;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OidcAccessTokenConfigStructure {
  @JsonProperty(ACCESS_TOKEN_STS_ENDPOINT) private String oidcAccessTokenStsEndpoint;
  @JsonProperty(ACCESS_TOKEN_IAM_SA_CREDENTIALS_ENDPOINT) private String oidcAccessTokenIamSaCredentialEndpoint;
  @JsonProperty(WORKLOAD_ACCESS_TOKEN_CONFIG) private OidcWorkloadAccessTokenRequest oidcWorkloadAccessTokenRequest;
}
