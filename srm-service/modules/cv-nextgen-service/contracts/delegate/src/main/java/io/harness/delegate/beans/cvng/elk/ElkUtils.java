/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.elk;

import io.harness.delegate.beans.connector.elkconnector.ELKAuthType;
import io.harness.delegate.beans.connector.elkconnector.ELKConnectorDTO;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.codec.binary.Base64;

@UtilityClass
public class ElkUtils {
  public static String getAuthorizationHeader(ELKConnectorDTO elkConnectorDTO) {
    if (elkConnectorDTO.getAuthType().equals(ELKAuthType.USERNAME_PASSWORD)) {
      return "Basic "
          + Base64.encodeBase64String(String
                                          .format("%s:%s", elkConnectorDTO.getUsername(),
                                              new String(elkConnectorDTO.getPasswordRef().getDecryptedValue()))
                                          .getBytes(StandardCharsets.UTF_8));
    } else if (elkConnectorDTO.getAuthType().equals(ELKAuthType.API_CLIENT_TOKEN)) {
      return "Apikey "
          + Base64.encodeBase64String(String
                                          .format("%s:%s", elkConnectorDTO.getApiKeyId(),
                                              new String(elkConnectorDTO.getApiKeyRef().getDecryptedValue()))
                                          .getBytes(StandardCharsets.UTF_8));
    } else {
      return "Bearer " + String.copyValueOf(elkConnectorDTO.getApiKeyRef().getDecryptedValue());
    }
  }

  public static Map<String, String> collectionHeaders(ELKConnectorDTO elkConnectorDTO) {
    Map<String, String> headers = new HashMap<>();
    headers.put("Connection", "keep-alive");
    if (elkConnectorDTO.getAuthType().equals(ELKAuthType.USERNAME_PASSWORD)
        || elkConnectorDTO.getAuthType().equals(ELKAuthType.API_CLIENT_TOKEN)
        || elkConnectorDTO.getAuthType().equals(ELKAuthType.BEARER_TOKEN)) {
      headers.put("Authorization", getAuthorizationHeader(elkConnectorDTO));
    }
    return headers;
  }
}
