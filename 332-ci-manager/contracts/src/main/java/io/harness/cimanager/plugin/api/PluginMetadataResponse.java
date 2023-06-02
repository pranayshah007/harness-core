/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.plugin.api;

import static io.harness.annotations.dev.HarnessTeam.CI;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Plugin metadata api response format
 */

@OwnedBy(CI)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginMetadataResponse {
  String name;
  String description;
  String kind;
  String logo;
  String repo;
  String image;
  String uses;
  List<Input> inputs;
  List<Output> outputs;

  /**
   * Plugin metadata api input attribute response format
   */
  @Value
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Input {
    String name;
    String description;
    String type;
    boolean required;
    boolean secret;
    @JsonProperty("default") String defaultVal;
    @JsonProperty("allowed_values") List<String> allowedValues;
  }

  /**
   * Plugin metadata api output attribute response format
   */
  @Value
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Output {
    String name;
    String description;
  }
}