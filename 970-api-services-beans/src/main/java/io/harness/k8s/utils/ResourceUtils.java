/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.utils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ResourceUtils {
  private ResourceUtils() {
    // Prevent instantiation
  }

  // this method is a workaround until we default to NON_EMPTY on the kubernetes model
  // see: https://github.com/fabric8io/kubernetes-model/issues/154

  public static String removeEmptyOrNullFields(String yaml) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    objectMapper.setSerializationInclusion(Include.NON_EMPTY);

    // We convert to YAML, parse as JsonNode
    // then remove empty nodes then write to YAML
    // again which is a hack around above issue:
    JsonNode jsonNode = objectMapper.readTree(yaml);
    removeNullOrEmptyValues(jsonNode);

    return objectMapper.writeValueAsString(jsonNode);
  }

  private static void removeNullOrEmptyValues(JsonNode jsonNode) {
    if (jsonNode instanceof ObjectNode) {
      List<String> removeFields = new ArrayList<>();
      ObjectNode object = (ObjectNode) jsonNode;
      for (Iterator<String> iter = object.fieldNames(); iter.hasNext();) {
        String field = iter.next();
        JsonNode value = object.get(field);
        if (isEmptyValue(value)) {
          removeFields.add(field);
        } else {
          removeNullOrEmptyValues(value);
        }
      }
      for (String field : removeFields) {
        object.remove(field);
      }
    } else if (jsonNode instanceof ArrayNode) {
      ArrayNode arrayNode = (ArrayNode) jsonNode;
      for (int i = 0, size = arrayNode.size(); i < size; i++) {
        JsonNode value = arrayNode.get(i);
        removeNullOrEmptyValues(value);
      }
    }
  }

  private static boolean isEmptyValue(JsonNode value) {
    if (value.isArray()) {
      int size = value.size();
      return size == 0;
    }
    if (value.isTextual()) {
      String text = value.textValue();
      return text == null;
    }
    if (value.isObject()) {
      removeNullOrEmptyValues(value);
      Iterator<String> iter = value.fieldNames();
      int count = 0;
      while (iter.hasNext()) {
        iter.next();
        count++;
      }
      return count == 0;
    }
    return false;
  }
}
