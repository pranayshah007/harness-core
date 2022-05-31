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
 * Create Project Request
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
@Schema(description = "Create Project Request")

public class CreateProjectRequest {
  private @Valid String slug = null;

  private @Valid String name = null;

  private @Valid String color = null;

  private @Valid List<ModuleType> modules = new ArrayList<>();

  private @Valid String description = null;

  private @Valid Map<String, String> tags = new HashMap<>();

  /**
   * Project identifier
   **/
  public CreateProjectRequest slug(String slug) {
    this.slug = slug;
    return this;
  }

  @ApiModelProperty(required = true, value = "Project identifier")
  @JsonProperty("slug")
  @NotNull
  @Pattern(regexp = "^[a-zA-Z_][0-9a-zA-Z_$]{0,63}$")
  @Size(min = 1, max = 64)
  public String getSlug() {
    return slug;
  }
  public void setSlug(String slug) {
    this.slug = slug;
  }

  /**
   * Project name
   **/
  public CreateProjectRequest name(String name) {
    this.name = name;
    return this;
  }

  @ApiModelProperty(required = true, value = "Project name")
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
   * Project color
   **/
  public CreateProjectRequest color(String color) {
    this.color = color;
    return this;
  }

  @ApiModelProperty(value = "Project color")
  @JsonProperty("color")

  public String getColor() {
    return color;
  }
  public void setColor(String color) {
    this.color = color;
  }

  /**
   * List of modules for project
   **/
  public CreateProjectRequest modules(List<ModuleType> modules) {
    this.modules = modules;
    return this;
  }

  @ApiModelProperty(value = "List of modules for project")
  @JsonProperty("modules")
  @Size(max = 1024)
  public List<ModuleType> getModules() {
    return modules;
  }
  public void setModules(List<ModuleType> modules) {
    this.modules = modules;
  }

  /**
   * Project description
   **/
  public CreateProjectRequest description(String description) {
    this.description = description;
    return this;
  }

  @ApiModelProperty(value = "Project description")
  @JsonProperty("description")
  @Size(max = 1024)
  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Project tags
   **/
  public CreateProjectRequest tags(Map<String, String> tags) {
    this.tags = tags;
    return this;
  }

  @ApiModelProperty(value = "Project tags")
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
    CreateProjectRequest createProjectRequest = (CreateProjectRequest) o;
    return Objects.equals(slug, createProjectRequest.slug) && Objects.equals(name, createProjectRequest.name)
        && Objects.equals(color, createProjectRequest.color) && Objects.equals(modules, createProjectRequest.modules)
        && Objects.equals(description, createProjectRequest.description)
        && Objects.equals(tags, createProjectRequest.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slug, name, color, modules, description, tags);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreateProjectRequest {\n");

    sb.append("    slug: ").append(toIndentedString(slug)).append("\n");
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
