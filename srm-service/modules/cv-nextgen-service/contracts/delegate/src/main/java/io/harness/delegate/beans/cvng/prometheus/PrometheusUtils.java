/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.prometheus;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthKeyAndValue;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;

import com.google.api.client.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class PrometheusUtils {
  public static Map<String, String> getHeaders(PrometheusConnectorDTO prometheusConnectorDTO) {
    Map<String, String> headers = new HashMap<>();
    prometheusConnectorDTO.getHeaders().forEach(
        decryptableKeyValue -> headers.put(decryptableKeyValue.getKey(), getValue(decryptableKeyValue)));
    if (isNotEmpty(prometheusConnectorDTO.getUsername()) && prometheusConnectorDTO.getPasswordRef() != null) {
      headers.put("Authorization",
          "Basic "
              + Base64.encodeBase64String(
                  String
                      .format("%s:%s", prometheusConnectorDTO.getUsername(),
                          new String(prometheusConnectorDTO.getPasswordRef().getDecryptedValue()))
                      .getBytes(StandardCharsets.UTF_8)));
    }
    return headers;
  }

  private static String getValue(CustomHealthKeyAndValue customHealthKeyAndValue) {
    if (customHealthKeyAndValue.isValueEncrypted()) {
      return String.valueOf(customHealthKeyAndValue.getEncryptedValueRef().getDecryptedValue());
    } else {
      return customHealthKeyAndValue.getValue();
    }
  }
}
