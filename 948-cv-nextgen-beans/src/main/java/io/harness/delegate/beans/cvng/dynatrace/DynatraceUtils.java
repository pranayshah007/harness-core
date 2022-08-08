/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.dynatrace;

import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO;

import java.util.HashMap;
import java.util.Map;

public class DynatraceUtils {
  private static final String DYNATRACE_API_TOKEN_LABEL = "Api-Token";

  public static Map<String, String> collectionHeaders(DynatraceConnectorDTO dynatraceConnectorDTO) {
    String apiToken = new String(dynatraceConnectorDTO.getApiTokenRef().getDecryptedValue());
    Map<String, String> headers = new HashMap<>();
    headers.put("Authorization", String.format("%s %s", DYNATRACE_API_TOKEN_LABEL, apiToken));
    headers.put("Accept", "application/json");
    return headers;
  }
}
