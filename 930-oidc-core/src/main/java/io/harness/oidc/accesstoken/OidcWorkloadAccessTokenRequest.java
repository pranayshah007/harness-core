/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.oidc.accesstoken;

import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.AUDIENCE;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.GRANT_TYPE;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.OPTIONS;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.REQUESTED_TOKEN_TYPE;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.SCOPE;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.SUBJECT_TOKEN;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.SUBJECT_TOKEN_TYPE;
import static io.harness.oidc.accesstoken.OidcAccessTokenConstants.USER_PROJECT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
public class OidcWorkloadAccessTokenRequest {
  @JsonProperty(AUDIENCE) private String audience;
  @JsonProperty(GRANT_TYPE) private String grantType;
  @JsonProperty(REQUESTED_TOKEN_TYPE) private String requestedTokenType;
  @JsonProperty(SCOPE) private String scope;
  @JsonProperty(SUBJECT_TOKEN_TYPE) private String subjectTokenType;
  @JsonProperty(SUBJECT_TOKEN) private String subjectToken;
  @JsonProperty(OPTIONS) private OidcAccessTokenOptions oidcAccessTokenOptions;

  public static class OidcAccessTokenOptions {
    // This class contains additional options
    @JsonProperty(USER_PROJECT) private String userProject;
  }
}
