/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.newrelic;

import io.harness.delegate.beans.connector.newrelic.NewRelicConnectorDTO;

import java.util.HashMap;
import java.util.Map;

public class NewRelicUtils {
  private static final String ACCOUNTS_BASE_URL = "v1/accounts/";
  private static final String X_API_KEY = "API-Key";
  private static final String X_QUERY_KEY = "X-Query-Key";
  private static final String NEW_API_BASE_URL = "applications.json";
  private static final String INSIGHTS_DSL_FILENAME = "newrelic-applications.datacollection";
  private static final String NEW_API_DSL_FILENAME = "newrelic-applications.datacollection";
  private static final String INSIGHTS = "insights";
  public static String getDSLFilename(NewRelicConnectorDTO newRelicConnectorDTO) {
    if (newRelicConnectorDTO.getUrl().contains(INSIGHTS)) {
      return INSIGHTS_DSL_FILENAME;
    }
    return NEW_API_DSL_FILENAME;
  }
  public static String getBaseUrl(NewRelicConnectorDTO newRelicConnectorDTO) {
    if (newRelicConnectorDTO.getUrl().contains(INSIGHTS)) {
      return newRelicConnectorDTO.getUrl() + ACCOUNTS_BASE_URL + newRelicConnectorDTO.getNewRelicAccountId() + "/";
    }
    return newRelicConnectorDTO.getUrl() + NEW_API_BASE_URL;
  }

  public static Map<String, String> collectionHeaders(NewRelicConnectorDTO newRelicConnectorDTO) {
    String apiKey = new String(newRelicConnectorDTO.getApiKeyRef().getDecryptedValue());
    Map<String, String> headers = new HashMap<>();
    if (newRelicConnectorDTO.getUrl().contains(INSIGHTS)) {
      headers.put(X_QUERY_KEY, apiKey);
    } else {
      headers.put(X_API_KEY, apiKey);
    }
    headers.put("Accept", "application/json");
    return headers;
  }
}
