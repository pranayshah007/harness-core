/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.datadog;

import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;

import java.util.HashMap;
import java.util.Map;

public class DatadogUtils {
  private static final String DD_API_KEY = "DD-API-KEY";
  private static final String DD_APPLICATION_KEY = "DD-APPLICATION-KEY";

  private DatadogUtils() {}

  public static Map<String, String> collectionHeaders(DatadogConnectorDTO datadogConnectorDTO) {
    String apiKey = new String(datadogConnectorDTO.getApiKeyRef().getDecryptedValue());
    String appKey = new String(datadogConnectorDTO.getApplicationKeyRef().getDecryptedValue());
    Map<String, String> headers = new HashMap<>();
    headers.put(DD_API_KEY, apiKey);
    headers.put(DD_APPLICATION_KEY, appKey);
    headers.put("Accept", "application/json");
    return headers;
  }
}
