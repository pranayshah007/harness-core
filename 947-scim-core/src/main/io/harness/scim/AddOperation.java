/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.scim;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.ToString;
@ToString(callSuper = true, includeFieldNames = true)
public class AddOperation extends PatchOperation {
  @JsonProperty protected final JsonNode value;

  @JsonIgnore private ObjectMapper jsonObjectMapper = new ObjectMapper();

  public AddOperation(@JsonProperty(value = "path", required = true) String path,
      @JsonProperty(value = "value", required = true) final JsonNode value) {
    super(path);
    this.value = value;
  }

  @Override
  public String getOpType() {
    return "Add";
  }

  @Override
  public <T> T getValue(final Class<T> cls) throws JsonProcessingException, IllegalArgumentException {
    if (value.isArray()) {
      throw new IllegalArgumentException("Patch operation contains "
          + "multiple values");
    }
    return jsonObjectMapper.treeToValue(value, cls);
  }

  @Override
  public <T> List<T> getValues(final Class<T> cls) throws JsonProcessingException {
    ArrayList<T> objects = new ArrayList<>(value.size());
    for (JsonNode node : value) {
      objects.add(jsonObjectMapper.treeToValue(node, cls));
    }
    return objects;
  }
}
