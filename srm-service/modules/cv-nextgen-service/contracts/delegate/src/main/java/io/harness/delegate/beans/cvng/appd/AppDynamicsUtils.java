/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.appd;

import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsAuthType;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;

public class AppDynamicsUtils {
  private AppDynamicsUtils() {}

  public static String getAuthorizationHeader(AppDynamicsConnectorDTO appDynamicsConnectorDTO) {
    return "Basic "
        + Base64.encodeBase64String(
            String
                .format("%s@%s:%s", appDynamicsConnectorDTO.getUsername(), appDynamicsConnectorDTO.getAccountname(),
                    new String(appDynamicsConnectorDTO.getPasswordRef().getDecryptedValue()))
                .getBytes(StandardCharsets.UTF_8));
  }

  public static Map<String, String> collectionHeaders(AppDynamicsConnectorDTO appDynamicsConnectorDTO) {
    Map<String, String> headers = new HashMap<>();
    headers.put("Connection", "keep-alive");
    if (appDynamicsConnectorDTO.getAuthType().equals(AppDynamicsAuthType.USERNAME_PASSWORD)) {
      headers.put("Authorization", getAuthorizationHeader(appDynamicsConnectorDTO));
    }
    return headers;
  }

  public static Map<String, Object> getCommonEnvVariables(AppDynamicsConnectorDTO appDynamicsConnectorDTO) {
    Map<String, Object> envMap = new HashMap<>();
    if (appDynamicsConnectorDTO.getAuthType().equals(AppDynamicsAuthType.API_CLIENT_TOKEN)) {
      envMap.put("tokenBasedAuth", "true");
      envMap.put("clientId", appDynamicsConnectorDTO.getClientId() + "@" + appDynamicsConnectorDTO.getAccountname());
      envMap.put("clientSecret", new String(appDynamicsConnectorDTO.getClientSecretRef().getDecryptedValue()));
      return envMap;
    } else {
      envMap.put("tokenBasedAuth", "false");
    }
    return envMap;
  }
}
