/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.sto.variables;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("stoYamlImageType")
@RecasterAlias("io.harness.yaml.sto.variables.STOYamlImageType")
public enum STOYamlImageType {
  @JsonProperty("aws_ecr") AWS_ECR("aws_ecr"),
  @JsonProperty("docker_v2") DOCKER_V2("docker_v2"),
  @JsonProperty("jfrog_artifactory") JFROG_ARTIFACTORY("jfrog_artifactory"),
  @JsonProperty("local_image") LOCAL_IMAGE("local_image");
  private final String yamlName;

  STOYamlImageType(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static STOYamlImageType getValue(@JsonProperty("type") String yamlName) {
    for (STOYamlImageType value : STOYamlImageType.values()) {
      if (value.yamlName.equalsIgnoreCase(yamlName) || value.name().equalsIgnoreCase(yamlName)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Invalid value for image type: " + yamlName + ". Valid values are: "
        + Arrays.stream(STOYamlImageType.values()).map(Enum::toString).collect(Collectors.joining(", ")));
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }

  @Override
  public String toString() {
    return yamlName;
  }

  public static STOYamlImageType fromString(final String s) {
    return STOYamlImageType.getValue(s);
  }
}
