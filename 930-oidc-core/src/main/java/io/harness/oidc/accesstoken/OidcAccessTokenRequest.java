/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.accesstoken;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
public class OidcAccessTokenRequest {
  @JsonProperty(io.harness.oidc.accesstoken.OidcAccessTokenConstants.AUDIENCE) private String audience;
  @JsonProperty(io.harness.oidc.accesstoken.OidcAccessTokenConstants.GRANT_TYPE) private String grantType;
  @JsonProperty(io.harness.oidc.accesstoken.OidcAccessTokenConstants.REQUESTED_TOKEN_TYPE)
  private String requestedTokenType;
  @JsonProperty(io.harness.oidc.accesstoken.OidcAccessTokenConstants.SCOPE) private String scope;
  @JsonProperty(io.harness.oidc.accesstoken.OidcAccessTokenConstants.SUBJECT_TOKEN_TYPE)
  private String subjectTokenType;
  @JsonProperty(io.harness.oidc.accesstoken.OidcAccessTokenConstants.SUBJECT_TOKEN) private String subjectToken;
  @JsonProperty(io.harness.oidc.accesstoken.OidcAccessTokenConstants.OPTIONS)
  private OidcAccessTokenOptions oidcAccessTokenOptions;

  public static class OidcAccessTokenOptions {
    @JsonProperty(io.harness.oidc.accesstoken.OidcAccessTokenConstants.USER_PROJECT) private String userProject;
  }
}
