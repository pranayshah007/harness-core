/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.filter.utils;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;

@UtilityClass
public class ModuleInfoFilterUtils {
  public void processNode(JsonNode jsonNode, String parentPath, Criteria criteria) {
    if (jsonNode.isValueNode()) {
      if (jsonNode.isInt()) {
        criteria.and(parentPath).is(jsonNode.asInt());
      } else {
        criteria.and(parentPath).is(jsonNode.asText());
      }
    } else if (jsonNode.isArray()) {
      List<String> valueList = new ArrayList<>();
      for (JsonNode arrayItem : jsonNode) {
        valueList.add(arrayItem.textValue());
      }
      if (isNotEmpty(valueList)) {
        criteria.and(parentPath).in(valueList);
      }
    } else if (jsonNode.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> jsonField = fields.next();
        processNode(jsonField.getValue(), String.join(".", parentPath, jsonField.getKey()), criteria);
      }
    }
  }

  // This function is created to apply OR conditions on different modules in filterProperties.
  public void processNodeOROperator(JsonNode jsonNode, String parentPath, List<Criteria> criteriaList) {
    if (jsonNode.isObject()) {
      Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.fields();
      while (fields.hasNext()) {
        Criteria criteriaModules = new Criteria();
        Map.Entry<String, JsonNode> jsonField = fields.next();
        processNode(jsonField.getValue(), String.join(".", parentPath, jsonField.getKey()), criteriaModules);
        criteriaList.add(criteriaModules);
      }
    }
  }
}
