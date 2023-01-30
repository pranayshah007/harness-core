/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.adfs;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.ADFS_ERROR;
import static io.harness.exception.WingsException.USER;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.AdfsAuthException;
import io.harness.jackson.JsonNodeUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = AdfsAccessTokenResponseDeserializer.class)
public class AdfsAccessTokenResponse {
  private static final String BEARER = "Bearer";
  String authToken;
  Integer expiresIn;

  /**
   * As per docs, this method assumes to receive bearer type of token only
   * <a
   * href="https://learn.microsoft.com/en-us/windows-server/identity/ad-fs/overview/ad-fs-openid-connect-oauth-flows-scenarios#service-to-service-access-token-response">...</a>
   */
  public AdfsAccessTokenResponse(JsonNode node) {
    String accessToken = JsonNodeUtils.getString(node, "access_token");
    String tokenType = JsonNodeUtils.getString(node, "token_type");
    if (!StringUtils.isBlank(accessToken) && BEARER.equalsIgnoreCase(tokenType.trim())) {
      this.authToken = String.format("%s %s", BEARER, accessToken);
      this.expiresIn = JsonNodeUtils.getLong(node, "expires_in");
      if (isNull(this.expiresIn)) {
        throw new AdfsAuthException("ADFS response doesn't have \"expires_in\" for the access token", ADFS_ERROR, USER);
      }
      return;
    }
    throw new AdfsAuthException("ADFS response doesn't have bearer access token", ADFS_ERROR, USER);
  }
}
