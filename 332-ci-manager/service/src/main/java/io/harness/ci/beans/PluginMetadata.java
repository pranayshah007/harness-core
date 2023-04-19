/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(CI)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginMetadata {
  @JsonProperty("name") String name;
  @JsonProperty("description") String description;
  @JsonProperty("kind") String kind;
  @JsonProperty("logo") String logo;
  @JsonProperty("repo") String repo;
  @JsonProperty("image") String image;
  @JsonProperty("uses") String uses;
  @JsonProperty("inputs") List<Input> inputs;
  @JsonProperty("outputs") List<Output> outputs;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Input {
    @JsonProperty("name") private String name;
    @JsonProperty("description") String description;
    @JsonProperty("type") String type;
    @JsonProperty("required") boolean required;
    @JsonProperty("secret") boolean secret;
    @JsonProperty("default") String defaultVal;
    @JsonProperty("allowed_values") List<String> allowedValues;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Output {
    @JsonProperty("name") String name;
    @JsonProperty("description") String description;
  }
}
