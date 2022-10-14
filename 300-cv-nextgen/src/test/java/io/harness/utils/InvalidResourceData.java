/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import java.util.Map;
import java.util.Objects;
import lombok.Builder;
import lombok.Data;
import org.json.JSONObject;
import org.yaml.snakeyaml.Yaml;

@Data
@Builder
public class InvalidResourceData {
  String path;
  String property;
  Object replacementValue;
  int expectedResponseCode;
  String expectedErrorMessage;

  public static String replace(String text, InvalidResourceData invalidResourceData) {
    Yaml yaml = new Yaml();
    Map<String, Object> map = yaml.load(text);
    JSONObject jsonObject = new JSONObject(map);
    JSONObject iteratorObject = jsonObject;
    if (Objects.nonNull(invalidResourceData.getPath())) {
      String[] path = invalidResourceData.getPath().split("\\\\");
      for (String value : path) {
        JSONObject dataObject = iteratorObject.optJSONObject(value);
        if (Objects.nonNull(dataObject)) {
          iteratorObject = iteratorObject.getJSONObject(value);
        } else {
          iteratorObject = iteratorObject.getJSONArray(value).getJSONObject(0);
        }
      }
    }
    iteratorObject.remove(invalidResourceData.getProperty());
    iteratorObject.put(invalidResourceData.getProperty(), invalidResourceData.getReplacementValue());

    return jsonObject.toString();
  }
}
