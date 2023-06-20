/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow.auth.refreshtoken;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenExceptionUtils.throwRefreshTokenException;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.jackson.JsonNodeUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = AccessTokenResponseDeserializer.class)
public class AccessTokenResponse {
  private static final String BEARER = "Bearer";
  @NotNull @ToString.Exclude String authToken;
  @NotNull Integer expiresIn;
  String scope;

  /**
   * We expect the response to have bearer type of token only
   *
   * If the response has both "id_token" and "access_token", then "id_token" is used in ServiceNow requests
   *
   * Only persistent refresh token is supported, hence the returned refresh token is not saved.
   **/
  public AccessTokenResponse(JsonNode node) {
    String idToken = JsonNodeUtils.getString(node, "id_token");
    String accessToken = JsonNodeUtils.getString(node, "access_token");
    String parsedAuthToken = StringUtils.isBlank(idToken) ? accessToken : idToken;
    String tokenType = JsonNodeUtils.getString(node, "token_type");
    String scope = JsonNodeUtils.getString(node, "scope");

    if (!StringUtils.isBlank(parsedAuthToken) && BEARER.equalsIgnoreCase(tokenType.trim())) {
      this.authToken = String.format("%s %s", BEARER, parsedAuthToken);
      this.expiresIn = JsonNodeUtils.getLong(node, "expires_in");
      if (isNull(this.expiresIn) || this.expiresIn <= 0) {
        throwRefreshTokenException("response doesn't have a positive \"expires_in\" for the access or id token");
      }
      this.scope = scope;
      return;
    }
    throwRefreshTokenException("response doesn't have bearer access token or id token");
  }
}
