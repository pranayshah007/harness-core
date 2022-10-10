/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", visible = true, defaultImpl = CloudRuntime.class)
@JsonSubTypes({
  @JsonSubTypes.Type(value = CloudRuntime.class, name = "Cloud")
  , @JsonSubTypes.Type(value = DockerRuntime.class, name = "Docker"),
})
public interface Runtime {
  @TypeAlias("runtime_type")
  enum Type {
    @JsonProperty("Docker") DOCKER("Docker"),
    @JsonProperty("Cloud") CLOUD("Cloud");

    private final String yamlName;

    Type(String yamlName) {
      this.yamlName = yamlName;
    }

    @JsonValue
    public String getYamlName() {
      return yamlName;
    }
  }
  Type getType();
}
