/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.splunk;

import static io.harness.data.encoding.EncodingUtils.encodeBase64;

import io.harness.delegate.beans.connector.splunkconnector.SplunkAuthType;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SplunkUtils {
  private static final String HEADER_PREFIX_BASIC_AUTH = "Basic ";
  private static final String HEADER_PREFIX_BEARER_TOKEN = "Bearer ";

  public static String getAuthorizationHeader(SplunkConnectorDTO splunkConnectorDTO) {
    String authHeader = null;
    SplunkAuthType authType =
        Optional.ofNullable(splunkConnectorDTO.getAuthType()).orElse(SplunkAuthType.USER_PASSWORD);
    if (SplunkAuthType.USER_PASSWORD.equals(authType)) {
      String pair = splunkConnectorDTO.getUsername() + ":"
          + String.copyValueOf(splunkConnectorDTO.getPasswordRef().getDecryptedValue());
      authHeader = HEADER_PREFIX_BASIC_AUTH + encodeBase64(pair);
    } else if (SplunkAuthType.BEARER_TOKEN.equals(authType)) {
      authHeader =
          HEADER_PREFIX_BEARER_TOKEN + String.copyValueOf(splunkConnectorDTO.getTokenRef().getDecryptedValue());
    }
    return authHeader;
  }

  public static Map<String, String> collectionHeaders(SplunkConnectorDTO splunkConnectorDTO) {
    Map<String, String> headers = new HashMap<>();
    if (splunkConnectorDTO.getAuthType() == SplunkAuthType.BEARER_TOKEN
        || splunkConnectorDTO.getAuthType() == SplunkAuthType.USER_PASSWORD) {
      headers.put("Authorization", SplunkUtils.getAuthorizationHeader(splunkConnectorDTO));
    }
    return headers;
  }
}
