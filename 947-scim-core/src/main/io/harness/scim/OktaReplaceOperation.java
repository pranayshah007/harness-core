/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.scim;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString(callSuper = true, includeFieldNames = true)
@Slf4j
public class OktaReplaceOperation extends PatchOperation {
  @JsonProperty private final JsonNode value;

  @JsonIgnore private ObjectMapper jsonObjectMapper = new ObjectMapper();

  @JsonCreator
  public OktaReplaceOperation(
      @JsonProperty(value = "path") String path, @JsonProperty(value = "value") final JsonNode value) {
    super(path);
    this.value = value;
  }

  @Override
  public String getOpType() {
    return "replace";
  }

  @Override
  public <T> List<T> getValues(final Class<T> cls) throws JsonProcessingException {
    try {
      ArrayList<T> replaceObjects = new ArrayList<>(value.size());
      for (JsonNode node : value) {
        log.info("Value {}", node.toString());
        replaceObjects.add(jsonObjectMapper.treeToValue(node, cls));
      }
      return replaceObjects;
    } catch (Exception e) {
      throw new IllegalArgumentException("Replace Patch operation contains "
          + "multiple values");
    }
  }

  @Override
  public <T> T getValue(final Class<T> cls) throws JsonProcessingException {
    log.info("Fetching Values for " + cls.getName());
    log.info("Value logging {}", value);
    if (value.isArray()) {
      log.info("GetValues Logging" + getValues(cls).toString());
      if (getValues(cls).get(0) != null) {
        log.info("GetValues Inside " + getValues(cls).get(0).toString());
      }

      return getValues(cls).get(0);
    }
    return jsonObjectMapper.treeToValue(value, cls);
  }
}
