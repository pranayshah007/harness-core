/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.signalfx;

import io.harness.delegate.beans.connector.signalfxconnector.SignalFXConnectorDTO;

import java.util.HashMap;
import java.util.Map;

public class SignalFXUtils {
  public static final String HEADER_X_SF_TOKEN = "X-SF-TOKEN";

  public static Map<String, String> collectionHeaders(SignalFXConnectorDTO signalFXConnectorDTO) {
    String apiToken = new String(signalFXConnectorDTO.getApiTokenRef().getDecryptedValue());
    Map<String, String> headers = new HashMap<>();
    headers.put(HEADER_X_SF_TOKEN, String.format("%s", apiToken));
    return headers;
  }
}
