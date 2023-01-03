/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.sumologic;

import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;

public class SumoLogicUtils {
  public static Map<String, String> collectionHeaders(SumoLogicConnectorDTO sumoLogicConnectorDTO) {
    String accessId = new String(sumoLogicConnectorDTO.getAccessIdRef().getDecryptedValue());
    String accessKey = new String(sumoLogicConnectorDTO.getAccessKeyRef().getDecryptedValue());
    String base64AccessIdAccessKey = accessId + ":" + accessKey;
    String authorizationHeader =
        "Basic " + Base64.encodeBase64String(base64AccessIdAccessKey.getBytes(StandardCharsets.UTF_8));

    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", authorizationHeader);
    headers.put("Accept", "*/*");
    headers.put("Content-Type", "application/json");
    return headers;
  }
}
