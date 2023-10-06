/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.accesstoken;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OidcAccessTokenConfigStructure {
  @JsonProperty(io.harness.oidc.accesstoken.OidcAccessTokenConstants.ACCESS_TOKEN_EXCHANGE_ENDPOINT)
  private OidcAccessTokenExchangeEndpoint oidcAccessTokenExchangeEndpoint;

  @JsonProperty(io.harness.oidc.accesstoken.OidcAccessTokenConstants.ACCESS_TOKEN_CONFIG)
  private io.harness.oidc.accesstoken.OidcAccessTokenRequest oidcAccessTokenRequest;

  public static class OidcAccessTokenExchangeEndpoint {
    @JsonProperty(io.harness.oidc.accesstoken.OidcAccessTokenConstants.END_POINT) private String endPoint;
  }
}
