/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.cvng.customhealth;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthKeyAndValue;
import io.harness.serializer.JsonUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomHealthConnectorValidationInfoUtils {
  public static Map<String, String> convertKeyAndValueListToMap(
      List<CustomHealthKeyAndValue> customHealthKeyAndValueList) {
    HashMap<String, String> map = new HashMap();
    customHealthKeyAndValueList.forEach(keyAndValue -> {
      if (keyAndValue.isValueEncrypted()) {
        map.put(keyAndValue.getKey(), new String(keyAndValue.getEncryptedValueRef().getDecryptedValue()));
      } else {
        map.put(keyAndValue.getKey(), keyAndValue.getValue());
      }
    });

    return map;
  }

  public static Map<String, Object> getCommonEnvVariables(CustomHealthConnectorDTO customHealthConnectorDTO) {
    Map<String, Object> commonEnvVariables = new HashMap<>();
    commonEnvVariables.put("method", customHealthConnectorDTO.getMethod());
    String validationPath = customHealthConnectorDTO.getValidationPath();
    String validationBody = customHealthConnectorDTO.getValidationBody();

    commonEnvVariables.put(
        "validationPath", isNotEmpty(validationPath) ? customHealthConnectorDTO.getValidationPath() : null);
    commonEnvVariables.put("validationBody",
        isNotEmpty(validationBody) ? JsonUtils.asMap(customHealthConnectorDTO.getValidationBody()) : null);
    return commonEnvVariables;
  }
}
