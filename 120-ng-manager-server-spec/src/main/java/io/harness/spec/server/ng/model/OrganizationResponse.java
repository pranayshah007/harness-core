/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.spec.server.ng.model;

import com.fasterxml.jackson.annotation.JsonProperty;
/**
 * Organization Response Model
 **/
import io.swagger.annotations.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.validation.Valid;
import javax.validation.constraints.*;
@Schema(description = "Organization Response Model")

public class OrganizationResponse {
  private @Valid Long createdAt = null;

  private @Valid Long lastModifiedAt = null;

  private @Valid Boolean harnessManaged = null;

  private @Valid String slug = null;

  private @Valid String name = null;

  private @Valid String description = null;

  private @Valid Map<String, String> tags = new HashMap<>();

  /**
   * Creation timestamp for organization
   **/
  public OrganizationResponse createdAt(Long createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  @ApiModelProperty(value = "Creation timestamp for organization")
  @JsonProperty("created_at")

  public Long getCreatedAt() {
    return createdAt;
  }
  public void setCreatedAt(Long createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * Last modification timestamp for Organization
   **/
  public OrganizationResponse lastModifiedAt(Long lastModifiedAt) {
    this.lastModifiedAt = lastModifiedAt;
    return this;
  }

  @ApiModelProperty(value = "Last modification timestamp for Organization")
  @JsonProperty("last_modified_at")

  public Long getLastModifiedAt() {
    return lastModifiedAt;
  }
  public void setLastModifiedAt(Long lastModifiedAt) {
    this.lastModifiedAt = lastModifiedAt;
  }

  /**
   * This indicates if this Organization is managed by Harness or not. If True, Harness can manage and modify this
   *Organization.
   **/
  public OrganizationResponse harnessManaged(Boolean harnessManaged) {
    this.harnessManaged = harnessManaged;
    return this;
  }

  @ApiModelProperty(
      value =
          "This indicates if this Organization is managed by Harness or not. If True, Harness can manage and modify this Organization.")
  @JsonProperty("harness_managed")

  public Boolean
  isHarnessManaged() {
    return harnessManaged;
  }
  public void setHarnessManaged(Boolean harnessManaged) {
    this.harnessManaged = harnessManaged;
  }

  /**
   * Organization identifier
   **/
  public OrganizationResponse slug(String slug) {
    this.slug = slug;
    return this;
  }

  @ApiModelProperty(value = "Organization identifier")
  @JsonProperty("slug")

  public String getSlug() {
    return slug;
  }
  public void setSlug(String slug) {
    this.slug = slug;
  }

  /**
   * Organization name
   **/
  public OrganizationResponse name(String name) {
    this.name = name;
    return this;
  }

  @ApiModelProperty(value = "Organization name")
  @JsonProperty("name")

  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Organization description
   **/
  public OrganizationResponse description(String description) {
    this.description = description;
    return this;
  }

  @ApiModelProperty(value = "Organization description")
  @JsonProperty("description")

  public String getDescription() {
    return description;
  }
  public void setDescription(String description) {
    this.description = description;
  }

  /**
   * Organization tags
   **/
  public OrganizationResponse tags(Map<String, String> tags) {
    this.tags = tags;
    return this;
  }

  @ApiModelProperty(value = "Organization tags")
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
    OrganizationResponse organizationResponse = (OrganizationResponse) o;
    return Objects.equals(createdAt, organizationResponse.createdAt)
        && Objects.equals(lastModifiedAt, organizationResponse.lastModifiedAt)
        && Objects.equals(harnessManaged, organizationResponse.harnessManaged)
        && Objects.equals(slug, organizationResponse.slug) && Objects.equals(name, organizationResponse.name)
        && Objects.equals(description, organizationResponse.description)
        && Objects.equals(tags, organizationResponse.tags);
  }

  @Override
  public int hashCode() {
    return Objects.hash(createdAt, lastModifiedAt, harnessManaged, slug, name, description, tags);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class OrganizationResponse {\n");

    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
    sb.append("    lastModifiedAt: ").append(toIndentedString(lastModifiedAt)).append("\n");
    sb.append("    harnessManaged: ").append(toIndentedString(harnessManaged)).append("\n");
    sb.append("    slug: ").append(toIndentedString(slug)).append("\n");
    sb.append("    name: ").append(toIndentedString(name)).append("\n");
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
