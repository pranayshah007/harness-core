/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.accesstoken;

import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.ACCESS_TOKEN;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.EXPIRES_IN;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.ISSUED_TOKEN_TYPE;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.TOKEN_TYPE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@OwnedBy(HarnessTeam.PL)
public class OidcWorkloadAccessTokenResponse {
  @JsonProperty(ACCESS_TOKEN) private String accessToken;
  @JsonProperty(ISSUED_TOKEN_TYPE) private String issuedTokenType;
  @JsonProperty(TOKEN_TYPE) private String tokenType;
  @JsonProperty(EXPIRES_IN) private int expiresIn;
}
