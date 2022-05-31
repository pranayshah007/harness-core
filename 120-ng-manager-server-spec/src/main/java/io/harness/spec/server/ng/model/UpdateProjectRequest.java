/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.spec.server.ng.model;

import io.harness.spec.server.ng.model.ModuleType;

import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * Update Project Request
 **/
import io.swagger.annotations.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.*;
@Schema(description = "Update Project Request")

public class UpdateProjectRequest {
  private @Valid String name = null;

  private @Valid String color = null;

  private @Valid List<ModuleType> modules = new ArrayList<>();

  private @Valid String description = null;

  private @Valid Map<String, String> tags = new HashMap<>();

  /**
   **/
  public UpdateProjectRequest name(String name) {
    this.name = name;
    return this;
  }

  @ApiModelProperty(required = true, value = "")
  @JsonProperty("name")
  @NotNull
  @Pattern(regexp = "^[a-zA-Z_][0-9a-zA-Z-_ ]{0,63}$")
  @Size(min = 1, max = 64)
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  /**
   **/
  public UpdateProjectRequest color(String color) {
    this.color = color;
    return this;
  }

  @ApiModelProperty(value = "")
  @JsonProperty("color")
  @Size(min = 1)
  public String getColor() {
    return color;
  }
  public void setColor(String color) {
    this.color = color;
  }

  /**
   **/
  public UpdateProjectRequest modules(List<ModuleType> modules) {
    this.modules = modules;
    return this;
  }

  @ApiModelProperty(value = "")
  @JsonProperty("modules")
  @Size(max = 1024)
  public List<ModuleType> getModules() {
    return modules;
  }
  public void setModules(List<ModuleType> modules) {
    this.modules = modules;
  }

  /**
   **/
  public UpdateProjectRequest description(String description) {
    this.description = description;
    return this;
  }

  @ApiModelProperty(value = "")
  @JsonProperty("description")
  @Size(min = 1, max = 1024)
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   **/
  public UpdateProjectRequest tags(Map<String, String> tags) {
    this.tags = tags;
    return this;
  }

  @ApiModelProperty(value = "")
  @JsonProperty("tags")
  @Size(max = 128)
  public Map<String, String> getTags() {
    return tags;
  }
  public void setTags(Map<String, String> tags) {
    this.tags = tags;
  }

  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UpdateProjectRequest updateProjectRequest = (UpdateProjectRequest) o;
    return Objects.equals(name, updateProjectRequest.name) && Objects.equals(color, updateProjectRequest.color)
        && Objects.equals(modules, updateProjectRequest.modules)
        && Objects.equals(description, updateProjectRequest.description)
        && Objects.equals(tags, updateProjectRequest.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, color, modules, description, tags);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class UpdateProjectRequest {\n");

    sb.append("    name: ").append(toIndentedString(name)).append("\n");
    sb.append("    color: ").append(toIndentedString(color)).append("\n");
    sb.append("    modules: ").append(toIndentedString(modules)).append("\n");
    sb.append("    description: ").append(toIndentedString(description)).append("\n");
    sb.append("    tags: ").append(toIndentedString(tags)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}
