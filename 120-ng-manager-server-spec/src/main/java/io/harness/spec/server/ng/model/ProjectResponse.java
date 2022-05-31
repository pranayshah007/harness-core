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
 * Project Response model
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
@Schema(description = "Project Response model")

public class ProjectResponse {
  private @Valid Long createdAt = null;

  private @Valid Long lastModifiedAt = null;

  private @Valid String orgIdentifier = null;

  private @Valid String slug = null;

  private @Valid String name = null;

  private @Valid String color = null;

  private @Valid List<ModuleType> modules = new ArrayList<>();

  private @Valid String description = null;

  private @Valid Map<String, String> tags = new HashMap<>();

  /**
   * Creation timestamp for the project
   **/
  public ProjectResponse createdAt(Long createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  @ApiModelProperty(value = "Creation timestamp for the project")
  @JsonProperty("created_at")

  public Long getCreatedAt() {
    return createdAt;
  }
  public void setCreatedAt(Long createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * Last modification timestamp for the project
   **/
  public ProjectResponse lastModifiedAt(Long lastModifiedAt) {
    this.lastModifiedAt = lastModifiedAt;
    return this;
  }

  @ApiModelProperty(value = "Last modification timestamp for the project")
  @JsonProperty("last_modified_at")

  public Long getLastModifiedAt() {
    return lastModifiedAt;
  }
  public void setLastModifiedAt(Long lastModifiedAt) {
    this.lastModifiedAt = lastModifiedAt;
  }

  /**
   * Organization identifier for the project
   **/
  public ProjectResponse orgIdentifier(String orgIdentifier) {
    this.orgIdentifier = orgIdentifier;
    return this;
  }

  @ApiModelProperty(value = "Organization identifier for the project")
  @JsonProperty("org_identifier")

  public String getOrgIdentifier() {
    return orgIdentifier;
  }
  public void setOrgIdentifier(String orgIdentifier) {
    this.orgIdentifier = orgIdentifier;
  }

  /**
   * Project identifier
   **/
  public ProjectResponse slug(String slug) {
    this.slug = slug;
    return this;
  }

  @ApiModelProperty(value = "Project identifier")
  @JsonProperty("slug")

  public String getSlug() {
    return slug;
  }
  public void setSlug(String slug) {
    this.slug = slug;
  }

  /**
   * Project name
   **/
  public ProjectResponse name(String name) {
    this.name = name;
    return this;
  }

  @ApiModelProperty(value = "Project name")
  @JsonProperty("name")

  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Project color
   **/
  public ProjectResponse color(String color) {
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
   * List of module for project
   **/
  public ProjectResponse modules(List<ModuleType> modules) {
    this.modules = modules;
    return this;
  }

  @ApiModelProperty(value = "List of module for project")
  @JsonProperty("modules")

  public List<ModuleType> getModules() {
    return modules;
  }
  public void setModules(List<ModuleType> modules) {
    this.modules = modules;
  }

  /**
   * Project description
   **/
  public ProjectResponse description(String description) {
    this.description = description;
    return this;
  }

  @ApiModelProperty(value = "Project description")
  @JsonProperty("description")

  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Project tags
   **/
  public ProjectResponse tags(Map<String, String> tags) {
    this.tags = tags;
    return this;
  }

  @ApiModelProperty(value = "Project tags")
  @JsonProperty("tags")

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
    ProjectResponse projectResponse = (ProjectResponse) o;
    return Objects.equals(createdAt, projectResponse.createdAt)
        && Objects.equals(lastModifiedAt, projectResponse.lastModifiedAt)
        && Objects.equals(orgIdentifier, projectResponse.orgIdentifier) && Objects.equals(slug, projectResponse.slug)
        && Objects.equals(name, projectResponse.name) && Objects.equals(color, projectResponse.color)
        && Objects.equals(modules, projectResponse.modules) && Objects.equals(description, projectResponse.description)
        && Objects.equals(tags, projectResponse.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(createdAt, lastModifiedAt, orgIdentifier, slug, name, color, modules, description, tags);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ProjectResponse {\n");

    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
    sb.append("    lastModifiedAt: ").append(toIndentedString(lastModifiedAt)).append("\n");
    sb.append("    orgIdentifier: ").append(toIndentedString(orgIdentifier)).append("\n");
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
